extends Node2D

const W := 1280.0
const H := 720.0
const FLOOR_Y := 610.0
const PLAYER_SIZE := Vector2(42, 62)
const GRAVITY := 1850.0
const MAX_FALL := 1100.0
const RUN_SPEED := 390.0
const RUN_ACCEL := 2500.0
const AIR_ACCEL := 1550.0
const FRICTION := 3000.0
const JUMP_SPEED := 740.0
const COYOTE := 0.11
const JUMP_BUFFER := 0.13
const GOAL_X := 4840.0

var state := "title"
var player_pos := Vector2(150, 470)
var player_vel := Vector2.ZERO
var player_grounded := false
var coyote_timer := 0.0
var jump_buffer := 0.0
var camera_x := 0.0
var target_camera_x := 0.0
var energy := 100.0
var max_energy := 100.0
var checkpoint := Vector2(150, 470)
var deaths := 0
var elapsed := 0.0
var title_t := 0.0
var finish_t := 0.0
var shake := 0.0
var shake_offset := Vector2.ZERO
var runner_anim := 0.0
var message := ""
var message_t := 0.0
var hint_stage := 0

var left_down := false
var right_down := false
var jump_down := false
var touch_roles := {}
var god_draw_start := Vector2.ZERO
var god_draw_end := Vector2.ZERO
var god_drawing := false
var dragging_magic := -1
var drag_offset := Vector2.ZERO
var magic_serial := 0

var solids: Array[Rect2] = []
var spikes: Array[Rect2] = []
var crystals: Array[Vector2] = []
var collected := {}
var magic_platforms: Array = []
var enemies: Array = []
var particles: Array = []
var rings: Array = []

func _ready() -> void:
    get_viewport().size = Vector2i(1280, 720)
    _build_level()
    $Renderer.queue_redraw()

func _sfx(kind: String) -> void:
    var rate := 22050
    var dur := 0.18
    if kind == "cast": dur = 0.34
    elif kind == "orb": dur = 0.28
    elif kind == "goal": dur = 0.72
    elif kind == "hit": dur = 0.22
    var frames := int(rate * dur)
    var bytes := PackedByteArray()
    bytes.resize(frames * 2)
    for i in range(frames):
        var t := float(i) / rate
        var env := pow(max(0.0, 1.0 - t / dur), 2.0)
        var v := 0.0
        if kind == "jump":
            v = sin(TAU * (500.0 + 850.0*t) * t) * env * 0.22
        elif kind == "cast":
            v = (sin(TAU*(260.0+720.0*t)*t) + 0.38*sin(TAU*760.0*t)) * env * 0.14
        elif kind == "orb":
            v = (sin(TAU*880.0*t) + 0.55*sin(TAU*1320.0*t)) * env * 0.15
        elif kind == "hit":
            v = (sin(TAU*95.0*t) + sin(TAU*47.0*t)*0.5) * env * 0.19
        elif kind == "goal":
            var f := 440.0 if t < 0.24 else (660.0 if t < 0.48 else 880.0)
            v = sin(TAU*f*t) * env * 0.16
        bytes.encode_s16(i * 2, int(clamp(v, -1.0, 1.0) * 32767.0))
    var wav := AudioStreamWAV.new()
    wav.format = AudioStreamWAV.FORMAT_16_BITS
    wav.mix_rate = rate
    wav.stereo = false
    wav.data = bytes
    var player := AudioStreamPlayer.new()
    player.stream = wav
    player.volume_db = -4.0
    add_child(player)
    player.finished.connect(player.queue_free)
    player.play()

func _build_level() -> void:
    solids = [
        Rect2(-200, 560, 920, 200),
        Rect2(900, 560, 500, 200),
        Rect2(1510, 560, 520, 200),
        Rect2(2170, 560, 420, 200),
        Rect2(2760, 560, 570, 200),
        Rect2(3470, 560, 450, 200),
        Rect2(4070, 560, 1050, 200),
        Rect2(510, 430, 170, 28),
        Rect2(1015, 412, 160, 28),
        Rect2(1250, 335, 145, 28),
        Rect2(1740, 410, 150, 28),
        Rect2(2250, 410, 160, 28),
        Rect2(2880, 390, 150, 28),
        Rect2(3130, 315, 140, 28),
        Rect2(3560, 400, 150, 28),
        Rect2(4260, 395, 170, 28),
    ]
    spikes = [
        Rect2(1130, 536, 120, 24),
        Rect2(1840, 536, 110, 24),
        Rect2(2940, 536, 100, 24),
        Rect2(3690, 536, 110, 24),
        Rect2(4450, 536, 125, 24),
    ]
    crystals = [Vector2(610,385), Vector2(1085,365), Vector2(1320,290), Vector2(1795,365), Vector2(2320,365), Vector2(2960,345), Vector2(3200,270), Vector2(3625,355), Vector2(4340,350), Vector2(4700,505)]
    enemies = [
        {"pos":Vector2(1030,518), "home":1030.0, "range":150.0, "dir":1.0, "stasis":0.0},
        {"pos":Vector2(1750,518), "home":1750.0, "range":120.0, "dir":-1.0, "stasis":0.0},
        {"pos":Vector2(2830,518), "home":2830.0, "range":160.0, "dir":1.0, "stasis":0.0},
        {"pos":Vector2(4200,518), "home":4200.0, "range":170.0, "dir":-1.0, "stasis":0.0},
    ]

func _physics_process(delta: float) -> void:
    elapsed += delta
    title_t += delta
    if state == "title":
        $Renderer.queue_redraw()
        return
    if state == "finished":
        finish_t += delta
        _update_particles(delta)
        $Renderer.queue_redraw()
        return

    energy = minf(max_energy, energy + 5.5 * delta)
    if message_t > 0.0:
        message_t -= delta
    if jump_buffer > 0.0:
        jump_buffer -= delta
    if coyote_timer > 0.0:
        coyote_timer -= delta

    var axis := 0.0
    if left_down: axis -= 1.0
    if right_down: axis += 1.0
    var accel := RUN_ACCEL if player_grounded else AIR_ACCEL
    if abs(axis) > 0.01:
        player_vel.x = move_toward(player_vel.x, axis * RUN_SPEED, accel * delta)
    else:
        player_vel.x = move_toward(player_vel.x, 0.0, FRICTION * delta)

    if player_grounded:
        coyote_timer = COYOTE
    if jump_buffer > 0.0 and coyote_timer > 0.0:
        player_vel.y = -JUMP_SPEED
        player_grounded = false
        coyote_timer = 0.0
        jump_buffer = 0.0
        _sfx("jump")
        _burst(player_pos + Vector2(0, 28), Color("85f8ff"), 7, 120.0)

    player_vel.y = minf(MAX_FALL, player_vel.y + GRAVITY * delta)
    _move_player(delta)
    _update_enemies(delta)
    _update_magic(delta)
    _update_particles(delta)
    _collect_crystals()
    _update_hints()

    if player_pos.y > 780 or _player_hits_spike() or _player_hits_enemy():
        _respawn()

    if player_pos.x > GOAL_X:
        _finish()

    target_camera_x = clampf(player_pos.x - 350.0, 0.0, GOAL_X - 750.0)
    camera_x = lerp(camera_x, target_camera_x, 1.0 - pow(0.0008, delta))
    if shake > 0.0:
        shake = maxf(0.0, shake - delta)
        shake_offset = Vector2(randf_range(-1,1), randf_range(-1,1)) * 10.0 * (shake / 0.18)
    else:
        shake_offset = Vector2.ZERO
    runner_anim += delta * (2.0 + abs(player_vel.x) / 70.0)
    $Renderer.queue_redraw()

func _move_player(delta: float) -> void:
    var was_grounded := player_grounded
    player_grounded = false
    player_pos.x += player_vel.x * delta
    var body := _player_rect()
    for r in _all_platform_rects():
        if body.intersects(r):
            if player_vel.x > 0:
                player_pos.x = r.position.x - PLAYER_SIZE.x * 0.5
            elif player_vel.x < 0:
                player_pos.x = r.end.x + PLAYER_SIZE.x * 0.5
            player_vel.x = 0
            body = _player_rect()

    var old_bottom := player_pos.y + PLAYER_SIZE.y * 0.5
    player_pos.y += player_vel.y * delta
    body = _player_rect()
    for r in _all_platform_rects():
        if body.intersects(r):
            if player_vel.y >= 0.0 and old_bottom <= r.position.y + 12.0:
                player_pos.y = r.position.y - PLAYER_SIZE.y * 0.5
                if player_vel.y > 470 and not was_grounded:
                    shake = 0.12
                    _burst(player_pos + Vector2(0,30), Color("b9f9ff"), 8, 90.0)
                player_vel.y = 0
                player_grounded = true
                body = _player_rect()
            elif player_vel.y < 0.0:
                player_pos.y = r.end.y + PLAYER_SIZE.y * 0.5
                player_vel.y = 0
                body = _player_rect()

func _all_platform_rects() -> Array[Rect2]:
    var arr: Array[Rect2] = []
    arr.append_array(solids)
    for m in magic_platforms:
        arr.append(m.rect)
    return arr

func _player_rect() -> Rect2:
    return Rect2(player_pos - PLAYER_SIZE * 0.5, PLAYER_SIZE)

func _update_enemies(delta: float) -> void:
    for e in enemies:
        if e.stasis > 0.0:
            e.stasis = maxf(0.0, float(e.stasis) - delta)
            continue
        e.pos.x += e.dir * 82.0 * delta
        if abs(e.pos.x - e.home) > e.range:
            e.dir *= -1.0
            e.pos.x = e.home + sign(e.pos.x - e.home) * e.range

func _update_magic(delta: float) -> void:
    for i in range(magic_platforms.size() - 1, -1, -1):
        var m = magic_platforms[i]
        m.ttl -= delta
        m.pulse += delta
        if m.ttl <= 0.0:
            _burst(m.rect.get_center(), Color("66e9ff"), 10, 150.0)
            magic_platforms.remove_at(i)

func _update_particles(delta: float) -> void:
    for i in range(particles.size() - 1, -1, -1):
        var p = particles[i]
        p.life -= delta
        p.pos += p.vel * delta
        p.vel.y += 150.0 * delta
        p.vel *= pow(0.4, delta)
        if p.life <= 0.0:
            particles.remove_at(i)
    for i in range(rings.size() - 1, -1, -1):
        var r = rings[i]
        r.life -= delta
        r.radius += r.speed * delta
        if r.life <= 0.0:
            rings.remove_at(i)

func _collect_crystals() -> void:
    for i in range(crystals.size()):
        if collected.has(i): continue
        if player_pos.distance_to(crystals[i]) < 58.0:
            collected[i] = true
            energy = minf(max_energy, energy + 26.0)
            _sfx("orb")
            _burst(crystals[i], Color("ffd56a"), 15, 190.0)
            rings.append({"pos":crystals[i], "radius":15.0, "speed":170.0, "life":0.38, "max":0.38, "color":Color("ffe69a")})
            _toast("GOD POWER +26")
            if crystals[i].x > checkpoint.x + 650:
                checkpoint = Vector2(crystals[i].x - 60, crystals[i].y - 90)

func _player_hits_spike() -> bool:
    var r := _player_rect().grow(-9)
    for s in spikes:
        if r.intersects(s): return true
    return false

func _player_hits_enemy() -> bool:
    var r := _player_rect().grow(-7)
    for e in enemies:
        if r.intersects(Rect2(e.pos - Vector2(25,23), Vector2(50,46))):
            return true
    return false

func _respawn() -> void:
    deaths += 1
    _sfx("hit")
    _burst(player_pos, Color("ff6b77"), 20, 260.0)
    player_pos = checkpoint
    player_vel = Vector2.ZERO
    energy = maxf(45.0, energy)
    camera_x = maxf(0.0, checkpoint.x - 350.0)
    shake = 0.18
    _toast("THE GOD PULLS YOU BACK")
    _vibrate(55)

func _finish() -> void:
    state = "finished"
    finish_t = 0.0
    _sfx("goal")
    for j in range(5):
        _burst(Vector2(GOAL_X + j*20, 430-j*25), [Color("7df7ff"), Color("ffd56a"), Color("ff7d9f")][j%3], 18, 300.0)
    _vibrate(90)

func _update_hints() -> void:
    if hint_stage == 0 and player_pos.x > 360:
        hint_stage = 1
        _toast("GOD: DRAG ACROSS THE GAP TO DRAW A BRIDGE", 4.0)
    elif hint_stage == 1 and player_pos.x > 830:
        hint_stage = 2
        _toast("GOD: TAP THE CREATURE TO FREEZE TIME", 4.0)
    elif hint_stage == 2 and player_pos.x > 1450:
        hint_stage = 3
        _toast("GOD: DRAG YOUR BLUE PLATFORM TO MOVE IT", 4.0)
    elif hint_stage == 3 and player_pos.x > 3250:
        hint_stage = 4
        _toast("RUNNER: COLLECT GOLD ORBS TO REFILL GOD POWER", 4.0)

func _toast(text: String, duration := 2.4) -> void:
    message = text
    message_t = duration

func _burst(pos: Vector2, color: Color, count: int, speed: float) -> void:
    for i in range(count):
        var a := randf_range(-PI, PI)
        var s := randf_range(speed * 0.35, speed)
        particles.append({"pos":pos, "vel":Vector2(cos(a),sin(a))*s, "life":randf_range(.28,.65), "max":.65, "color":color, "size":randf_range(2.0,6.0)})

func _vibrate(ms: int) -> void:
    if OS.get_name() == "Android":
        Input.vibrate_handheld(ms)

func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventScreenTouch:
        _touch(event.index, event.position, event.pressed)
    elif event is InputEventScreenDrag:
        _drag(event.index, event.position, event.relative)
    elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
        _touch(-1, event.position, event.pressed)
    elif event is InputEventMouseMotion and Input.is_mouse_button_pressed(MOUSE_BUTTON_LEFT):
        _drag(-1, event.position, event.relative)

func _touch(id: int, pos: Vector2, pressed: bool) -> void:
    if state == "title" and pressed:
        state = "play"
        _toast("RUNNER: LEFT SIDE  •  GOD: TOUCH THE WORLD", 3.2)
        _burst(Vector2(640,360), Color("7df7ff"), 24, 320)
        return
    if state == "finished" and pressed:
        _restart()
        return
    if state != "play": return

    if pressed:
        if pos.y > 565.0:
            if pos.x < 430.0:
                var role := "left" if pos.x < 215.0 else "right"
                touch_roles[id] = role
                if role == "left": left_down = true
                else: right_down = true
            elif pos.x > 1010.0:
                touch_roles[id] = "jump"
                jump_down = true
                jump_buffer = JUMP_BUFFER
            else:
                touch_roles[id] = "none"
        else:
            var world := _screen_to_world(pos)
            var enemy_idx := _enemy_at(world)
            if enemy_idx >= 0 and energy >= 22.0:
                enemies[enemy_idx].stasis = 4.0
                energy -= 22.0
                _sfx("cast")
                rings.append({"pos":enemies[enemy_idx].pos, "radius":18.0, "speed":130.0, "life":.6, "max":.6, "color":Color("8cf8ff")})
                _burst(enemies[enemy_idx].pos, Color("9dfcff"), 12, 150.0)
                _toast("TIME LOCKED • 4 SEC")
                _vibrate(24)
                touch_roles[id] = "stasis"
                return
            var magic_idx := _magic_at(world)
            if magic_idx >= 0:
                dragging_magic = magic_idx
                drag_offset = magic_platforms[magic_idx].rect.get_center() - world
                touch_roles[id] = "drag_magic"
                return
            god_drawing = true
            god_draw_start = world
            god_draw_end = world
            touch_roles[id] = "god_draw"
    else:
        var role = touch_roles.get(id, "")
        if role == "left": left_down = false
        elif role == "right": right_down = false
        elif role == "jump": jump_down = false
        elif role == "god_draw": _commit_magic_platform()
        elif role == "drag_magic": dragging_magic = -1
        touch_roles.erase(id)

func _drag(id: int, pos: Vector2, relative: Vector2) -> void:
    if state != "play": return
    var role = touch_roles.get(id, "")
    if role == "god_draw":
        god_draw_end = _screen_to_world(pos)
    elif role == "drag_magic" and dragging_magic >= 0 and dragging_magic < magic_platforms.size():
        var world: Vector2 = _screen_to_world(pos) + drag_offset
        var m: Dictionary = magic_platforms[dragging_magic] as Dictionary
        if energy > 0.0:
            var rect: Rect2 = m["rect"] as Rect2
            var old: Vector2 = rect.get_center()
            var next_pos: Vector2 = old.lerp(world, 0.33)
            var d: float = old.distance_to(next_pos)
            energy = maxf(0.0, energy - d * 0.045)
            rect.position = next_pos - rect.size * 0.5
            m["rect"] = rect
            m["ttl"] = minf(10.0, float(m["ttl"]) + 0.02)
            _spark_line(old, next_pos, Color("77ecff"))

func _commit_magic_platform() -> void:
    god_drawing = false
    var dx: float = god_draw_end.x - god_draw_start.x
    var length: float = absf(dx)
    if length < 55.0: return
    length = minf(length, 360.0)
    var cost: float = 12.0 + length * 0.095
    if energy < cost:
        _toast("NOT ENOUGH GOD POWER")
        return
    energy -= cost
    var center_x: float = (god_draw_start.x + god_draw_end.x) * 0.5
    var y: float = (god_draw_start.y + god_draw_end.y) * 0.5
    var rect: Rect2 = Rect2(center_x - length * 0.5, y - 11.0, length, 22.0)
    magic_serial += 1
    magic_platforms.append({"rect":rect, "ttl":8.5, "pulse":0.0, "id":magic_serial})
    _sfx("cast")
    _spark_line(Vector2(rect.position.x,y), Vector2(rect.end.x,y), Color("78efff"))
    rings.append({"pos":rect.get_center(), "radius":10.0, "speed":110.0, "life":.45, "max":.45, "color":Color("8df8ff")})
    _vibrate(20)

func _spark_line(a: Vector2, b: Vector2, color: Color) -> void:
    var count: int = int(clampf(a.distance_to(b) / 28.0, 2.0, 12.0))
    for i in range(count):
        var p := a.lerp(b, randf())
        particles.append({"pos":p, "vel":Vector2(randf_range(-30,30),randf_range(-65,-15)), "life":randf_range(.18,.4), "max":.4, "color":color, "size":randf_range(2.0,5.0)})

func _enemy_at(world: Vector2) -> int:
    for i in range(enemies.size()):
        if world.distance_to(enemies[i].pos) < 58.0: return i
    return -1

func _magic_at(world: Vector2) -> int:
    for i in range(magic_platforms.size()-1, -1, -1):
        if magic_platforms[i].rect.grow(18).has_point(world): return i
    return -1

func _screen_to_world(p: Vector2) -> Vector2:
    return p + Vector2(camera_x,0) - shake_offset

func _world_to_screen(p: Vector2) -> Vector2:
    return p - Vector2(camera_x,0) + shake_offset

func _restart() -> void:
    state = "play"
    player_pos = Vector2(150,470)
    player_vel = Vector2.ZERO
    camera_x = 0
    target_camera_x = 0
    energy = 100
    checkpoint = Vector2(150,470)
    deaths = 0
    collected.clear()
    magic_platforms.clear()
    _build_level()
    hint_stage = 0
    finish_t = 0
    _toast("RUNNER + GOD • AGAIN", 2.0)
