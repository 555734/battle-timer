extends Node2D

const W := 1280.0
const H := 720.0
const GOAL_X := 4840.0

var game
var state: String: get: return game.state
var player_pos: Vector2: get: return game.player_pos
var player_vel: Vector2: get: return game.player_vel
var player_grounded: bool: get: return game.player_grounded
var camera_x: float: get: return game.camera_x
var shake_offset: Vector2: get: return game.shake_offset
var energy: float: get: return game.energy
var max_energy: float: get: return game.max_energy
var elapsed: float: get: return game.elapsed
var title_t: float: get: return game.title_t
var finish_t: float: get: return game.finish_t
var runner_anim: float: get: return game.runner_anim
var message: String: get: return game.message
var message_t: float: get: return game.message_t
var deaths: int: get: return game.deaths
var god_drawing: bool: get: return game.god_drawing
var god_draw_start: Vector2: get: return game.god_draw_start
var god_draw_end: Vector2: get: return game.god_draw_end
var solids: Array: get: return game.solids
var spikes: Array: get: return game.spikes
var crystals: Array: get: return game.crystals
var collected: Dictionary: get: return game.collected
var magic_platforms: Array: get: return game.magic_platforms
var enemies: Array: get: return game.enemies

func _ready() -> void:
    game = get_parent()

func _world_to_screen(p: Vector2) -> Vector2:
    return game._world_to_screen(p)

func _draw() -> void:
    _draw_background()
    if state == "title":
        _draw_title()
        return
    _draw_world()
    _draw_hud()
    if state == "finished":
        _draw_finish()

func _draw_background() -> void:
    var bands := [Color("091226"), Color("10264a"), Color("1a4162"), Color("3a6b7a"), Color("8aa08f")]
    for i in range(5):
        draw_rect(Rect2(0, i*144, W, 146), bands[i])
    draw_circle(Vector2(1020 - fmod(camera_x*0.03, 120.0), 115), 72, Color(0.75,0.96,1,0.10))
    draw_circle(Vector2(1020 - fmod(camera_x*0.03, 120.0), 115), 51, Color(0.88,0.98,1,0.18))
    for i in range(8):
        var x := fmod(i*243.0 - camera_x*0.08, W+360.0) - 120.0
        var y := 120.0 + (i%3)*63.0
        draw_circle(Vector2(x,y), 55, Color(0.5,0.9,1,0.045))
        draw_circle(Vector2(x+55,y+12), 42, Color(0.5,0.9,1,0.035))
    _draw_mountain_layer(0.12, 420, Color("17334a"), 115)
    _draw_mountain_layer(0.22, 490, Color("142c3c"), 82)
    _draw_ruins_back()

func _draw_mountain_layer(parallax: float, base_y: float, col: Color, amp: float) -> void:
    var pts := PackedVector2Array()
    pts.append(Vector2(-100, H))
    for i in range(14):
        var x := i*115.0 - fmod(camera_x*parallax,115.0) - 80.0
        var y := base_y - (sin(i*1.7 + camera_x*0.001)*0.5+0.5)*amp
        pts.append(Vector2(x,y))
    pts.append(Vector2(W+100,H))
    draw_colored_polygon(pts,col)

func _draw_ruins_back() -> void:
    for i in range(9):
        var world_x := i*720.0 + 340.0
        var x := world_x - camera_x*0.35
        if x < -180 or x > W+180: continue
        var h := 105.0 + (i%4)*28.0
        draw_rect(Rect2(x, 515-h, 66, h), Color(0.05,0.12,0.18,0.62))
        draw_rect(Rect2(x-12, 515-h, 90, 13), Color(0.07,0.16,0.22,0.7))
        draw_circle(Vector2(x+33, 515-h+38), 15, Color(0.25,0.55,0.6,0.14))

func _draw_world() -> void:
    for r in solids:
        var sr := Rect2(_world_to_screen(r.position), r.size)
        if sr.end.x < -100 or sr.position.x > W+100: continue
        draw_rect(sr, Color("192f35"))
        draw_rect(Rect2(sr.position, Vector2(sr.size.x,8)), Color("7fa26f"))
        draw_rect(Rect2(sr.position+Vector2(0,8), Vector2(sr.size.x,5)), Color("425e4c"))
        for x in range(int(sr.position.x)+16, int(sr.end.x), 42):
            draw_line(Vector2(x,sr.position.y+22),Vector2(x+12,sr.position.y+37),Color(0.2,0.35,0.36,.5),2)
    for s in spikes:
        var p := _world_to_screen(s.position)
        var n := max(1, int(s.size.x/24.0))
        for j in range(n):
            var x := p.x + j*(s.size.x/n)
            draw_colored_polygon(PackedVector2Array([Vector2(x,p.y+s.size.y),Vector2(x+s.size.x/n/2,p.y),Vector2(x+s.size.x/n,p.y+s.size.y)]), Color("c95766"))
    for m in magic_platforms:
        var sr := Rect2(_world_to_screen(m.rect.position),m.rect.size)
        var life_alpha: float = clamp(m.ttl/1.2,0.0,1.0)
        draw_rect(sr.grow(8), Color(0.2,0.9,1,0.055*life_alpha), true)
        draw_rect(sr, Color(0.18,0.76,0.86,0.58*life_alpha), true)
        draw_line(sr.position+Vector2(4,3), Vector2(sr.end.x-4,sr.position.y+3), Color(0.7,1,1,0.85*life_alpha), 3)
        for k in range(4):
            var px := sr.position.x + 20 + fmod(k*73.0 + m.pulse*45.0, max(30.0,sr.size.x-30.0))
            draw_circle(Vector2(px,sr.position.y+11), 3, Color(0.75,1,1,0.8))
    for i in range(crystals.size()):
        if collected.has(i): continue
        var p := _world_to_screen(crystals[i])
        if p.x < -50 or p.x > W+50: continue
        var bob := sin(elapsed*3.2 + i)*7.0
        p.y += bob
        draw_circle(p,22,Color(1,0.82,0.35,0.08))
        var pts := PackedVector2Array([p+Vector2(0,-18),p+Vector2(12,0),p+Vector2(0,18),p+Vector2(-12,0)])
        draw_colored_polygon(pts, Color("ffd56a"))
        draw_polyline(PackedVector2Array([pts[0],pts[1],pts[2],pts[3],pts[0]]),Color("fff0ad"),2)
    for e in enemies:
        _draw_enemy(e)
    _draw_goal()
    _draw_runner()
    if god_drawing:
        var a := _world_to_screen(god_draw_start)
        var b := _world_to_screen(god_draw_end)
        var ok := abs(b.x-a.x) > 55 and energy >= 12.0 + min(abs(b.x-a.x),360.0)*0.095
        var c := Color("7df7ff") if ok else Color("ff7d8d")
        draw_line(a,b,Color(c.r,c.g,c.b,.25),16,true)
        draw_line(a,b,Color(c.r,c.g,c.b,.9),4,true)
        draw_circle(a,10,Color(c.r,c.g,c.b,.35))
        draw_circle(b,10,Color(c.r,c.g,c.b,.35))
    for p in game.particles:
        var sp := _world_to_screen(p.pos)
        var a := clamp(p.life / p.max, 0.0,1.0)
        draw_circle(sp,p.size,Color(p.color.r,p.color.g,p.color.b,a))
    for r in game.rings:
        var sp := _world_to_screen(r.pos)
        var a := clamp(r.life/r.max,0.0,1.0)
        draw_arc(sp,r.radius,0,TAU,36,Color(r.color.r,r.color.g,r.color.b,a),3)

func _draw_enemy(e) -> void:
    var p := _world_to_screen(e.pos)
    if p.x < -70 or p.x > W+70: return
    var frozen: bool = e.stasis > 0.0
    var col := Color("70e3f0") if frozen else Color("d35d70")
    draw_circle(p+Vector2(0,8),25,Color(0,0,0,.18))
    draw_circle(p,24,col)
    draw_circle(p+Vector2(-9,-5),4,Color("10202b"))
    draw_circle(p+Vector2(9,-5),4,Color("10202b"))
    draw_arc(p+Vector2(0,1),10,0.25,PI-0.25,12,Color("10202b"),2)
    if frozen:
        draw_arc(p,34,0,TAU,32,Color(0.6,1,1,.7),3)
        draw_line(p+Vector2(-31,-24),p+Vector2(-22,-32),Color(0.8,1,1,.8),2)
        draw_line(p+Vector2(29,20),p+Vector2(38,26),Color(0.8,1,1,.8),2)

func _draw_runner() -> void:
    var p := _world_to_screen(player_pos)
    var moving := abs(player_vel.x) > 40 and player_grounded
    var stride := sin(runner_anim)*7.0 if moving else 0.0
    var lean := clamp(player_vel.x / game.RUN_SPEED, -1.0,1.0)*5.0
    draw_ellipse(p+Vector2(0,31),Vector2(26,8),Color(0,0,0,.28))
    draw_circle(p+Vector2(0,-4),38,Color(0.95,0.55,0.34,.035))
    var cape_pts := PackedVector2Array([p+Vector2(-10+lean,-12),p+Vector2(-28-lean,10),p+Vector2(-18-lean,29),p+Vector2(6,18)])
    draw_colored_polygon(cape_pts,Color("a84255"))
    draw_line(p+Vector2(-8,18), p+Vector2(-10+stride,31), Color("d7dde2"), 8, true)
    draw_line(p+Vector2(8,18), p+Vector2(10-stride,31), Color("d7dde2"), 8, true)
    draw_colored_polygon(PackedVector2Array([p+Vector2(-15,-10),p+Vector2(14,-10),p+Vector2(17,20),p+Vector2(-17,20)]),Color("e26c5f"))
    draw_rect(Rect2(p+Vector2(-16,9),Vector2(33,6)),Color("6a394b"))
    draw_circle(p+Vector2(0,-25),14,Color("f2c382"))
    draw_line(p+Vector2(-13,-14),p+Vector2(13,-15),Color("f0a64f"),7,true)
    draw_circle(p+Vector2(5,-27),2.8,Color("152234"))
    draw_colored_polygon(PackedVector2Array([p+Vector2(-13,-32),p+Vector2(0,-43),p+Vector2(13,-33),p+Vector2(8,-20),p+Vector2(-12,-21)]),Color("273248"))

func draw_ellipse(center: Vector2, radius: Vector2, color: Color) -> void:
    var pts := PackedVector2Array()
    for i in range(32):
        var a := TAU*i/32.0
        pts.append(center+Vector2(cos(a)*radius.x,sin(a)*radius.y))
    draw_colored_polygon(pts,color)

func _draw_goal() -> void:
    var x := GOAL_X - camera_x
    if x < -100 or x > W+200: return
    draw_circle(Vector2(x,455),70,Color(0.4,1,1,.05))
    draw_arc(Vector2(x,455),54,-PI,0,32,Color("7defff"),7)
    draw_line(Vector2(x-54,455),Vector2(x-54,560),Color("446c70"),14)
    draw_line(Vector2(x+54,455),Vector2(x+54,560),Color("446c70"),14)
    draw_circle(Vector2(x,448),13,Color("ffe07a"))
    draw_string(ThemeDB.fallback_font,Vector2(x-48,390),"SANCTUARY",HORIZONTAL_ALIGNMENT_LEFT,100,18,Color(0.8,1,1,.75))

func _draw_hud() -> void:
    draw_style_box(Rect2(34,28,360,58),Color(0.02,0.05,0.10,.78),Color(0.35,0.8,0.9,.22),22)
    draw_string(ThemeDB.fallback_font,Vector2(58,52),"GOD POWER",HORIZONTAL_ALIGNMENT_LEFT,-1,17,Color("b9f8ff"))
    draw_rect(Rect2(58,63,306,9),Color(1,1,1,.08))
    draw_rect(Rect2(58,63,306*(energy/max_energy),9),Color("66eaff"))
    var progress := clamp(player_pos.x/GOAL_X,0.0,1.0)
    draw_style_box(Rect2(860,28,386,58),Color(0.02,0.05,0.10,.72),Color(1,1,1,.08),22)
    draw_string(ThemeDB.fallback_font,Vector2(888,53),"SANCTUARY  %d%%" % int(progress*100),HORIZONTAL_ALIGNMENT_LEFT,-1,17,Color("f4e8c0"))
    draw_rect(Rect2(888,64,325,6),Color(1,1,1,.08))
    draw_rect(Rect2(888,64,325*progress,6),Color("ffd56a"))
    draw_circle(Vector2(105,642),61,Color(0.02,0.06,0.11,.46))
    draw_circle(Vector2(105,642),60,Color(0.5,0.9,1,.06),false,3)
    draw_colored_polygon(PackedVector2Array([Vector2(78,642),Vector2(103,623),Vector2(103,661)]),Color(0.8,0.95,1,.55))
    draw_circle(Vector2(282,642),61,Color(0.02,0.06,0.11,.46))
    draw_circle(Vector2(282,642),60,Color(0.5,0.9,1,.06),false,3)
    draw_colored_polygon(PackedVector2Array([Vector2(309,642),Vector2(284,623),Vector2(284,661)]),Color(0.8,0.95,1,.55))
    draw_circle(Vector2(1165,642),62,Color(0.08,0.05,0.09,.54))
    draw_circle(Vector2(1165,642),60,Color(1,0.7,0.5,.1),false,3)
    draw_string(ThemeDB.fallback_font,Vector2(1134,650),"JUMP",HORIZONTAL_ALIGNMENT_CENTER,62,16,Color("ffd3a8"))
    draw_string(ThemeDB.fallback_font,Vector2(530,699),"GOD: DRAW • DRAG • FREEZE",HORIZONTAL_ALIGNMENT_CENTER,240,15,Color(0.72,0.95,1,.35))
    if message_t > 0.0:
        var a := min(1.0,message_t*2.5)
        draw_style_box(Rect2(320,104,640,54),Color(0.02,0.045,0.085,.78*a),Color(0.45,0.95,1,.18*a),18)
        draw_string(ThemeDB.fallback_font,Vector2(345,139),message,HORIZONTAL_ALIGNMENT_CENTER,590,18,Color(0.88,0.98,1,a))

func draw_style_box(rect: Rect2, fill: Color, border: Color, radius: float) -> void:
    draw_rect(rect,fill,true)
    draw_rect(rect,border,false,2)

func _draw_title() -> void:
    draw_rect(Rect2(0,0,W,H),Color(0.01,0.02,0.05,.18))
    var pulse := .5 + .5*sin(title_t*2.0)
    draw_circle(Vector2(640,332),124,Color(0.2,0.95,1,.025+pulse*.012))
    draw_string(ThemeDB.fallback_font,Vector2(0,214),"RUNNER + GOD",HORIZONTAL_ALIGNMENT_CENTER,1280,64,Color("f2f6ec"))
    draw_string(ThemeDB.fallback_font,Vector2(0,260),"TWO HANDS. ONE PATH.",HORIZONTAL_ALIGNMENT_CENTER,1280,20,Color(0.55,0.94,1,.8))
    draw_line(Vector2(380,420),Vector2(830,420),Color(0.3,0.95,1,.12),24,true)
    draw_line(Vector2(380,420),Vector2(830,420),Color(0.55,0.98,1,.72),4,true)
    draw_circle(Vector2(485,389),24,Color("e36d61"))
    draw_circle(Vector2(485,354),13,Color("f1c481"))
    draw_circle(Vector2(825,350),42,Color(0.75,1,1,.08))
    draw_arc(Vector2(825,350),31,-2.4,.7,22,Color(0.78,1,1,.8),7)
    var alpha := .48 + .42*(.5+.5*sin(title_t*3.0))
    draw_string(ThemeDB.fallback_font,Vector2(0,560),"TOUCH TO BEGIN",HORIZONTAL_ALIGNMENT_CENTER,1280,20,Color(0.88,0.98,1,alpha))
    draw_string(ThemeDB.fallback_font,Vector2(0,604),"RUNNER MOVES BELOW  •  GOD TOUCHES THE WORLD ABOVE",HORIZONTAL_ALIGNMENT_CENTER,1280,15,Color(0.74,0.83,0.86,.55))

func _draw_finish() -> void:
    var a := clamp(finish_t*2.0,0.0,1.0)
    draw_rect(Rect2(0,0,W,H),Color(0.01,0.02,0.05,.65*a))
    draw_string(ThemeDB.fallback_font,Vector2(0,240),"SANCTUARY REACHED",HORIZONTAL_ALIGNMENT_CENTER,1280,48,Color(0.9,1,1,a))
    draw_string(ThemeDB.fallback_font,Vector2(0,302),"RUNNER %d  •  GOD 1" % (deaths+1),HORIZONTAL_ALIGNMENT_CENTER,1280,18,Color(1,0.86,0.55,.8*a))
    draw_string(ThemeDB.fallback_font,Vector2(0,372),"This path only exists while both players cooperate.",HORIZONTAL_ALIGNMENT_CENTER,1280,19,Color(0.82,0.9,0.92,.75*a))
    draw_string(ThemeDB.fallback_font,Vector2(0,486),"TOUCH TO PLAY AGAIN",HORIZONTAL_ALIGNMENT_CENTER,1280,18,Color(0.65,0.95,1,.8*a))
