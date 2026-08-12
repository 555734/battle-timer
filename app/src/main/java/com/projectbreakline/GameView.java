package com.projectbreakline;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

/**
 * Native Android vertical slice for PROJECT BREAKLINE.
 * The game is intentionally self-contained: primitive rendering, local save,
 * no network permissions, and a fixed portrait touch loop.
 */
public final class GameView extends View {
    private static final int MENU = 0;
    private static final int STAGE_SELECT = 1;
    private static final int UPGRADES = 2;
    private static final int PLAYING = 3;
    private static final int RESULT = 4;
    private static final int BARREL = 0;
    private static final int ENEMY = 1;
    private static final int BOSS = 2;
    private static final String PREFS = "project_breakline_offline_v1";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random(11L);
    private final SharedPreferences prefs;
    private final StageConfig[] stages = new StageConfig[] {
            new StageConfig("橋の入口", 34f, 2.05f, 1.28f, 40f, 28),
            new StageConfig("崩落区画", 39f, 1.72f, 1.08f, 47f, 38),
            new StageConfig("都市道路", 45f, 1.45f, .94f, 54f, 50)
    };
    private final UpgradeConfig[] upgrades = new UpgradeConfig[] {
            new UpgradeConfig("開始ダメージ", "弾1発の威力を上げる", "damage", 5),
            new UpgradeConfig("開始連射", "自動射撃の間隔を短くする", "fireRate", 5),
            new UpgradeConfig("開始人数", "開始時の仲間を1人増やす", "squad", 5)
    };

    private int screen = MENU;
    private int currentStage = 1;
    private boolean paused;
    private boolean dragging;
    private boolean resultWin;
    private int resultCoins;
    private long lastFrame;
    private long messageUntil;
    private String message = "";
    private float width;
    private float height;
    private float ui;
    private GameState game;

    public GameView(Context context) {
        super(context);
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setFocusable(true);
        setKeepScreenOn(true);
        lastFrame = SystemClock.uptimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        width = w;
        height = h;
        ui = Math.max(.75f, Math.min(w, 420f) / 360f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(.05f, Math.max(0f, (now - lastFrame) / 1000f));
        lastFrame = now;
        update(dt);
        drawWorld(canvas);
        if (screen == MENU) drawMenu(canvas);
        else if (screen == STAGE_SELECT) drawStageSelect(canvas);
        else if (screen == UPGRADES) drawUpgrades(canvas);
        else if (screen == RESULT) drawResult(canvas);
        if (screen == PLAYING) drawHud(canvas);
        postInvalidateOnAnimation();
    }

    private void update(float dt) {
        if (screen != PLAYING || game == null || paused || game.ended) return;
        game.time += dt;
        game.hint = Math.max(0f, game.hint - dt);
        game.player.playerFlash = Math.max(0f, game.player.playerFlash - dt);
        game.player.invulnerable = Math.max(0f, game.player.invulnerable - dt);
        game.spawnBarrel -= dt;
        game.spawnEnemy -= dt;
        game.player.fireCooldown -= dt;
        game.finishTimer = Math.max(0f, game.finishTimer - dt);

        if (!game.bossSpawned && game.time >= game.config.duration - 7f) spawnBoss();
        if (!game.bossSpawned && game.spawnBarrel <= 0f) {
            spawnBarrel();
            game.spawnBarrel = game.config.barrelRate;
        }
        int activeEnemies = 0;
        for (Target target : game.targets) if (target.alive && target.kind == ENEMY) activeEnemies++;
        if (!game.bossSpawned && game.spawnEnemy <= 0f && activeEnemies < 60) {
            spawnEnemy();
            game.spawnEnemy = game.config.enemyRate;
        }
        if (game.player.fireCooldown <= 0f) {
            fireBullet();
            game.player.fireCooldown = game.player.fireInterval;
        }

        for (Target target : game.targets) {
            if (!target.alive) continue;
            if (target.kind == BOSS) {
                target.phase += dt;
                target.lane = .5f + (float) Math.sin(target.phase * .9f) * .26f;
                target.y = height * .2f + (float) Math.sin(target.phase * 1.3f) * 13f;
            } else {
                target.y += target.speed * dt;
            }
            if (target.kind == ENEMY && target.y > playerY() - 75f) {
                if (Math.abs(target.lane - game.player.playerX) < .23f) hitPlayer(target);
                else target.alive = false;
            }
            if (target.kind != BOSS && target.y > height * 1.04f) target.alive = false;
        }

        Iterator<Bullet> bullets = game.bullets.iterator();
        while (bullets.hasNext()) {
            Bullet bullet = bullets.next();
            bullet.life -= dt;
            if (bullet.target != null && bullet.target.alive) {
                float tx = roadX(bullet.target.y, bullet.target.lane);
                float dx = tx - bullet.x;
                float dy = bullet.target.y - bullet.y;
                float distance = (float) Math.hypot(dx, dy);
                float step = bullet.speed * dt;
                if (distance <= step + 12f) {
                    bullet.target.hp -= bullet.damage;
                    burst(tx, bullet.target.y, bullet.color, 4);
                    if (bullet.target.hp <= 0f) destroyTarget(bullet.target);
                    bullet.life = 0f;
                } else {
                    float ratio = step / distance;
                    bullet.x += dx * ratio;
                    bullet.y += dy * ratio;
                }
            } else {
                bullet.y -= bullet.speed * dt;
            }
            if (bullet.life <= 0f || bullet.y < -40f) bullets.remove();
        }

        Iterator<Particle> particles = game.particles.iterator();
        while (particles.hasNext()) {
            Particle p = particles.next();
            p.life -= dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += 34f * dt;
            if (p.life <= 0f) particles.remove();
        }
        Iterator<FloatingText> texts = game.texts.iterator();
        while (texts.hasNext()) {
            FloatingText t = texts.next();
            t.life -= dt;
            t.y -= 24f * dt;
            if (t.life <= 0f) texts.remove();
        }
        if (game.finishTimer <= 0f && game.bossDefeated) endStage(true);
        if (game.time > game.config.duration + 10f && !game.bossDefeated) endStage(false);
    }

    private void drawWorld(Canvas canvas) {
        drawBackground(canvas);
        drawRoad(canvas);
        if (game != null) {
            for (Target target : game.targets) if (target.alive) drawTarget(canvas, target);
            for (Bullet bullet : game.bullets) drawBullet(canvas, bullet);
            if (screen == PLAYING || screen == RESULT) drawPlayer(canvas);
            for (Particle particle : game.particles) drawParticle(canvas, particle);
            for (FloatingText text : game.texts) drawFloatingText(canvas, text);
        }
    }

    private void drawBackground(Canvas canvas) {
        paint.setShader(new android.graphics.LinearGradient(0f, 0f, 0f, height,
                Color.rgb(137, 199, 232), Color.rgb(9, 35, 66), android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(28, 255, 255, 255));
        float shift = game == null ? 0f : (game.time * 9f) % height;
        for (int i = 0; i < 14; i++) {
            float y = (i * 53f + shift) % height;
            canvas.drawRect(0f, y, width * .28f, 1f, paint);
            canvas.drawRect(width * .72f, y + 24f, width, y + 25f, paint);
        }
    }

    private void drawRoad(Canvas canvas) {
        float topY = height * .12f;
        float topLeft = roadBounds(topY, true);
        float topRight = roadBounds(topY, false);
        float bottomLeft = roadBounds(height, true);
        float bottomRight = roadBounds(height, false);
        path.reset();
        path.moveTo(topLeft, topY);
        path.lineTo(topRight, topY);
        path.lineTo(bottomRight, height);
        path.lineTo(bottomLeft, height);
        path.close();
        paint.setColor(Color.rgb(115, 122, 129));
        canvas.drawPath(path, paint);
        stroke.setColor(Color.argb(120, 238, 246, 248));
        stroke.setStrokeWidth(2f * ui);
        canvas.drawLine(topLeft, topY, bottomLeft, height, stroke);
        canvas.drawLine(topRight, topY, bottomRight, height, stroke);
        float offset = game == null ? 0f : (game.time * 90f) % 120f;
        for (float lane : new float[] {.333f, .666f}) {
            for (float y = topY + 4f - offset; y < height; y += 120f) {
                float y2 = y + 52f;
                stroke.setColor(Color.argb(185, 238, 246, 248));
                stroke.setStrokeWidth(Math.max(1f, y / height * 3f));
                canvas.drawLine(roadX(y, lane), y, roadX(y2, lane), y2, stroke);
            }
        }
    }

    private void drawMenu(Canvas canvas) {
        drawPanel(canvas);
        float x = width * .12f;
        drawText(canvas, "OFFLINE SHOOTING RUNNER / NATIVE", x, height * .18f, 12f, 0xff67e8f9, true, false);
        drawText(canvas, "PROJECT", x, height * .29f, 48f, Color.WHITE, true, false);
        drawText(canvas, "BREAKLINE", x, height * .39f, 48f, 0xff3e9cff, true, false);
        drawText(canvas, "左右に動き、樽を壊し、仲間と武器を増やして群れを突破する。", x, height * .47f, 14f, 0xffd6e4f4, false, false);
        drawButton(canvas, new RectF(x, height * .58f, x + width * .45f, height * .58f + 54f * ui), "開始", true);
        drawButton(canvas, new RectF(x, height * .58f + 68f * ui, x + width * .45f, height * .58f + 122f * ui), "永続強化", false);
        drawText(canvas, "通信なし・アカウントなし・保存はこの端末のみ", x, height * .82f, 11f, 0xffa7b7ca, false, false);
        drawText(canvas, "Android native / MVP vertical slice", x, height * .87f, 10f, 0xff71849b, false, false);
    }

    private void drawStageSelect(Canvas canvas) {
        drawPanel(canvas);
        drawBack(canvas, "STAGE SELECT");
        float x = width * .1f;
        drawText(canvas, "ステージを選ぶ", x, height * .2f, 30f, Color.WHITE, true, false);
        drawText(canvas, "1プレイ約1〜2分。固定配置の縦スライスです。", x, height * .25f, 12f, 0xffa7b7ca, false, false);
        float y = height * .31f;
        for (int i = 0; i < stages.length; i++) {
            int number = i + 1;
            boolean unlocked = number <= unlockedStage();
            RectF card = new RectF(x, y, width - x, y + 70f * ui);
            drawCard(canvas, card, unlocked ? 0xff09203a : 0xff122235, unlocked ? 0xff67e8f9 : 0xff384b61);
            drawText(canvas, String.format(Locale.JAPAN, "STAGE %d  %s", number, stages[i].name), x + 16f * ui, y + 27f * ui, 14f, unlocked ? Color.WHITE : 0xff8091a5, true, false);
            drawText(canvas, String.format(Locale.JAPAN, "%d秒 / ボス戦あり / 固定配置", (int) stages[i].duration), x + 16f * ui, y + 49f * ui, 10f, 0xffa7b7ca, false, false);
            drawButton(canvas, new RectF(width - x - 74f * ui, y + 15f * ui, width - x - 8f * ui, y + 55f * ui), unlocked ? "開始" : "LOCK", unlocked);
            y += 82f * ui;
        }
        drawText(canvas, "永続強化は端末内だけに保存されます。", x, y + 20f * ui, 11f, 0xffa7b7ca, false, false);
        drawButton(canvas, new RectF(x, y + 40f * ui, x + 145f * ui, y + 82f * ui), "強化を見る", false);
    }

    private void drawUpgrades(Canvas canvas) {
        drawPanel(canvas);
        drawBack(canvas, "LOCAL PROGRESSION");
        float x = width * .1f;
        drawText(canvas, "永続強化", x, height * .2f, 30f, Color.WHITE, true, false);
        drawText(canvas, "所持コイン  " + coins(), x, height * .25f, 15f, 0xffffb45c, true, false);
        float y = height * .31f;
        for (UpgradeConfig upgrade : upgrades) {
            int level = upgradeLevel(upgrade.key);
            boolean maxed = level >= upgrade.max;
            int cost = upgradeCost(level);
            RectF card = new RectF(x, y, width - x, y + 76f * ui);
            drawCard(canvas, card, 0xff09203a, 0xff3e5d7c);
            drawText(canvas, upgrade.name + "  Lv." + level + "/" + upgrade.max, x + 14f * ui, y + 28f * ui, 13f, Color.WHITE, true, false);
            drawText(canvas, upgrade.description, x + 14f * ui, y + 51f * ui, 10f, 0xffa7b7ca, false, false);
            String label = maxed ? "MAX" : cost + " コイン";
            drawButton(canvas, new RectF(width - x - 94f * ui, y + 18f * ui, width - x - 8f * ui, y + 58f * ui), label, !maxed && coins() >= cost);
            y += 88f * ui;
        }
        drawText(canvas, "基地建設・サーバー・課金はMVP対象外", x, y + 16f * ui, 11f, 0xffa7b7ca, false, false);
    }

    private void drawResult(Canvas canvas) {
        drawPanel(canvas);
        float x = width * .1f;
        drawText(canvas, resultWin ? "RUN COMPLETE" : "RUN FAILED", x, height * .2f, 12f, resultWin ? 0xff67e8f9 : 0xffff8798, true, false);
        drawText(canvas, resultWin ? "ステージクリア" : "全滅", x, height * .3f, 34f, Color.WHITE, true, false);
        String copy = resultWin ? "隊列と武器の成長で突破しました。" : "武器の選択と横移動を見直して再挑戦しましょう。";
        drawText(canvas, copy, x, height * .37f, 13f, 0xffd6e4f4, false, false);
        float top = height * .46f;
        drawStatCard(canvas, new RectF(x, top, x + width * .25f, top + 70f * ui), "コイン", "+" + resultCoins);
        drawStatCard(canvas, new RectF(x + width * .28f, top, x + width * .53f, top + 70f * ui), "破壊した樽", String.valueOf(game == null ? 0 : game.barrelsBroken));
        drawStatCard(canvas, new RectF(x + width * .56f, top, width - x, top + 70f * ui), "撃破した敵", String.valueOf(game == null ? 0 : game.enemiesDefeated));
        boolean next = resultWin && currentStage < stages.length && currentStage < unlockedStage();
        drawButton(canvas, new RectF(x, height * .64f, x + width * .46f, height * .64f + 54f * ui), next ? "次のステージ" : "もう一度", true);
        drawButton(canvas, new RectF(x, height * .64f + 68f * ui, x + width * .46f, height * .64f + 122f * ui), "ステージ選択", false);
    }

    private void drawHud(Canvas canvas) {
        if (game == null) return;
        float pad = 16f * ui;
        drawText(canvas, String.format(Locale.JAPAN, "STAGE %d / %s", game.stage, game.config.name), pad, 28f * ui, 11f, 0xff67e8f9, true, false);
        drawText(canvas, formatTime(game.time), pad, 50f * ui, 18f, Color.WHITE, true, false);
        RectF pause = pauseRect();
        drawCard(canvas, pause, 0x88051123, 0xff67e8f9);
        drawTextCentered(canvas, "Ⅱ", pause.centerX(), pause.centerY() + 6f * ui, 18f, Color.WHITE, true);
        paint.setColor(0x553b536a);
        canvas.drawRoundRect(new RectF(pad, 66f * ui, width - pad, 70f * ui), 2f * ui, 2f * ui, paint);
        paint.setColor(0xff67e8f9);
        float progress = Math.min(1f, game.time / game.config.duration);
        canvas.drawRoundRect(new RectF(pad, 66f * ui, pad + (width - pad * 2f) * progress, 70f * ui), 2f * ui, 2f * ui, paint);
        float bottom = height - 64f * ui;
        drawMiniStat(canvas, new RectF(pad, bottom, pad + 82f * ui, bottom + 46f * ui), "仲間", String.valueOf(game.player.squad));
        drawMiniStat(canvas, new RectF(width * .5f - 55f * ui, bottom, width * .5f + 55f * ui, bottom + 46f * ui), "武器", weaponName(game.player.weapon));
        drawMiniStat(canvas, new RectF(width - pad - 82f * ui, bottom, width - pad, bottom + 46f * ui), "コイン", String.valueOf(coins()));
        if (game.hint > 0f && !paused) {
            float alpha = Math.min(.8f, game.hint / 1.5f);
            paint.setColor(Color.argb((int) (255f * alpha), 1, 14, 28));
            RectF hint = new RectF(width * .16f, height * .68f, width * .84f, height * .68f + 44f * ui);
            canvas.drawRoundRect(hint, 22f * ui, 22f * ui, paint);
            drawTextCentered(canvas, "左右にドラッグして隊列を動かす", width / 2f, hint.centerY() + 5f * ui, 12f, Color.WHITE, true);
        }
        if (paused) drawPauseOverlay(canvas);
        if (messageUntil > SystemClock.uptimeMillis()) drawMessage(canvas);
    }

    private void drawPauseOverlay(Canvas canvas) {
        paint.setColor(0xcc010914);
        canvas.drawRect(0f, 0f, width, height, paint);
        drawTextCentered(canvas, "一時停止", width / 2f, height * .4f, 30f, Color.WHITE, true);
        drawTextCentered(canvas, "進行は止まっています", width / 2f, height * .46f, 13f, 0xffa7b7ca, false);
        drawButton(canvas, new RectF(width * .2f, height * .54f, width * .8f, height * .54f + 52f * ui), "再開", true);
        drawButton(canvas, new RectF(width * .2f, height * .54f + 66f * ui, width * .8f, height * .54f + 118f * ui), "ステージ選択へ", false);
    }

    private void drawMessage(Canvas canvas) {
        float w = Math.min(width * .86f, 330f * ui);
        RectF box = new RectF((width - w) / 2f, height * .69f, (width + w) / 2f, height * .69f + 42f * ui);
        drawCard(canvas, box, 0xdd020f1e, 0x8867e8f9);
        drawTextCentered(canvas, message, width / 2f, box.centerY() + 4f * ui, 11f, Color.WHITE, true);
    }

    private void drawTarget(Canvas canvas, Target target) {
        float x = roadX(target.y, target.lane);
        float scale = Math.max(.55f, Math.min(1.45f, .55f + target.y / height * .75f));
        canvas.save();
        canvas.translate(x, target.y);
        canvas.scale(scale, scale);
        if (target.kind == BARREL) drawBarrel(canvas, target);
        else if (target.kind == ENEMY) drawEnemy(canvas);
        else drawBoss(canvas, target);
        canvas.restore();
    }

    private void drawBarrel(Canvas canvas, Target target) {
        RectF body = new RectF(-26f, -23f, 26f, 23f);
        paint.setColor(0xff8d592f);
        canvas.drawRoundRect(body, 8f, 8f, paint);
        paint.setColor(0xffba7b43);
        canvas.drawRoundRect(new RectF(-22f, -19f, 22f, 19f), 6f, 6f, paint);
        stroke.setColor(0xff3d2418);
        stroke.setStrokeWidth(4f);
        canvas.drawLine(-21f, -15f, 21f, -15f, stroke);
        canvas.drawLine(-21f, 15f, 21f, 15f, stroke);
        drawTextCentered(canvas, String.valueOf(Math.max(0, (int) Math.ceil(target.hp))), 0f, 6f, 18f, Color.WHITE, true);
        int rewardColor = target.reward == 0 ? 0xff75f0b6 : target.reward == 1 ? 0xffffd166 : target.reward == 2 ? 0xfffb7185 : 0xffa5f3fc;
        paint.setColor(rewardColor);
        canvas.drawCircle(0f, -32f, 5f, paint);
    }

    private void drawEnemy(Canvas canvas) {
        paint.setColor(0xff283541);
        canvas.drawCircle(0f, -18f, 10f, paint);
        paint.setColor(0xff9ba9ad);
        canvas.drawRoundRect(new RectF(-13f, -9f, 13f, 25f), 8f, 8f, paint);
        stroke.setColor(0xff1b2530);
        stroke.setStrokeWidth(5f);
        canvas.drawLine(-9f, 7f, -18f, 27f, stroke);
        canvas.drawLine(9f, 7f, 18f, 27f, stroke);
        canvas.drawLine(-8f, 2f, -21f, 13f, stroke);
        canvas.drawLine(8f, 2f, 21f, 13f, stroke);
        paint.setColor(0xffff647d);
        canvas.drawCircle(0f, -18f, 3f, paint);
    }

    private void drawBoss(Canvas canvas, Target target) {
        paint.setColor(0x44ff546a);
        canvas.drawCircle(0f, 0f, 76f, paint);
        paint.setColor(0xffd99c8c);
        canvas.drawOval(new RectF(-55f, -38f, 55f, 46f), paint);
        paint.setColor(0xff9f635f);
        canvas.drawCircle(0f, -25f, 31f, paint);
        stroke.setColor(0xff5a3437);
        stroke.setStrokeWidth(10f);
        canvas.drawLine(-35f, 15f, -62f, 43f, stroke);
        canvas.drawLine(35f, 15f, 62f, 43f, stroke);
        paint.setColor(0xffffe4a8);
        canvas.drawCircle(-11f, -28f, 4f, paint);
        canvas.drawCircle(11f, -28f, 4f, paint);
        drawTextCentered(canvas, String.format(Locale.JAPAN, "%d / %d", (int) Math.ceil(target.hp), target.maxHp), 0f, -70f, 12f, Color.WHITE, true);
    }

    private void drawBullet(Canvas canvas, Bullet bullet) {
        stroke.setColor(bullet.color);
        stroke.setStrokeWidth(3f * ui);
        canvas.drawLine(bullet.x, bullet.y + 12f * ui, bullet.x, bullet.y - 14f * ui, stroke);
    }

    private void drawPlayer(Canvas canvas) {
        if (game == null) return;
        float x = roadX(playerY(), game.player.playerX);
        float y = playerY();
        canvas.save();
        canvas.translate(x, y);
        if (game.player.invulnerable > 0f && ((int) (game.player.invulnerable * 20f) % 2 == 0)) canvas.scale(.92f, .92f);
        int count = Math.min(game.player.squad, 5);
        for (int i = 0; i < count; i++) {
            float offset = (i - (count - 1) / 2f) * 24f * ui;
            drawSoldier(canvas, offset, Math.abs(offset) * .05f);
        }
        if (game.player.playerFlash > 0f) {
            paint.setColor(0xfffff4a3);
            canvas.drawCircle(0f, -27f * ui, 9f * ui + game.player.playerFlash * 30f * ui, paint);
        }
        canvas.restore();
    }

    private void drawSoldier(Canvas canvas, float x, float y) {
        paint.setColor(0xff1e4e80);
        canvas.drawRoundRect(new RectF(x - 10f * ui, y - 3f * ui, x + 10f * ui, y + 30f * ui), 6f * ui, 6f * ui, paint);
        paint.setColor(0xffffc36b);
        canvas.drawCircle(x, y - 13f * ui, 10f * ui, paint);
        paint.setColor(0xff193146);
        canvas.drawRoundRect(new RectF(x - 12f * ui, y - 21f * ui, x + 12f * ui, y - 14f * ui), 4f * ui, 4f * ui, paint);
        stroke.setColor(0xff1c2c3b);
        stroke.setStrokeWidth(4f * ui);
        canvas.drawLine(x - 5f * ui, y + 27f * ui, x - 10f * ui, y + 38f * ui, stroke);
        canvas.drawLine(x + 5f * ui, y + 27f * ui, x + 10f * ui, y + 38f * ui, stroke);
        canvas.drawLine(x + 10f * ui, y + 5f * ui, x + 24f * ui, y - 9f * ui, stroke);
    }

    private void drawParticle(Canvas canvas, Particle p) {
        paint.setAlpha((int) (255f * Math.max(0f, p.life / p.maxLife)));
        paint.setColor(p.color);
        canvas.drawCircle(p.x, p.y, p.size * ui, paint);
        paint.setAlpha(255);
    }

    private void drawFloatingText(Canvas canvas, FloatingText text) {
        paint.setAlpha((int) (255f * Math.max(0f, text.life / text.maxLife)));
        drawText(canvas, text.value, text.x, text.y, 15f, text.color, true, true);
        paint.setAlpha(255);
    }

    private void hitPlayer(Target target) {
        target.alive = false;
        if (game.player.invulnerable > 0f) return;
        game.player.invulnerable = .25f;
        game.player.squad -= 1;
        burst(roadX(target.y, target.lane), playerY() - 15f, 0xffff647d, 16);
        addFloatingText(roadX(target.y, target.lane), playerY() - 60f, "-1 仲間", 0xffff9aa9);
        if (game.player.squad <= 0) endStage(false);
    }

    private void destroyTarget(Target target) {
        if (!target.alive) return;
        target.alive = false;
        float x = roadX(target.y, target.lane);
        if (target.kind == BARREL) {
            game.barrelsBroken++;
            game.score += 5;
            String label = target.reward == 0 ? "+1 仲間" : target.reward == 1 ? "DAMAGE UP" : target.reward == 2 ? "RAPID FIRE" : "WEAPON UP";
            addFloatingText(x, target.y - 25f, label, target.reward == 0 ? 0xff9ff6ce : 0xffffd36a);
            burst(x, target.y, 0xffcaa16d, 20);
            if (target.reward == 0) game.player.squad++;
            else if (target.reward == 1) game.player.damage += .22f;
            else if (target.reward == 2) game.player.fireInterval = Math.max(.095f, game.player.fireInterval * .86f);
            else game.player.weapon = Math.min(3, game.player.weapon + 1);
        } else if (target.kind == ENEMY) {
            game.enemiesDefeated++;
            game.score += 10;
            burst(x, target.y, 0xffff7b8e, 11);
        } else {
            game.bossDefeated = true;
            game.finishTimer = .5f;
            game.score += 60;
            burst(x, target.y, 0xffffd166, 45);
            addFloatingText(x, target.y - 45f, "BOSS BREAK!", 0xfffff0a8);
        }
    }

    private void spawnBarrel() {
        int reward = (game.barrelsBroken + (int) (game.time * 2f)) % 4;
        float lane = random.nextInt(3) / 2f;
        float hp = 3f + game.stage * 2f + (int) (game.time / 11f);
        game.targets.add(new Target(BARREL, lane, height * .11f, 26f + game.stage * 3f, hp, reward));
    }

    private void spawnEnemy() {
        float lane = random.nextInt(3) / 2f;
        float hp = 1f + game.stage / 2;
        game.targets.add(new Target(ENEMY, lane, height * .09f - random.nextFloat() * 26f, game.config.enemySpeed + random.nextFloat() * 12f, hp, 0));
    }

    private void spawnBoss() {
        game.bossSpawned = true;
        game.targets.add(new Target(BOSS, .5f, height * .18f, 10f, game.config.bossHp, 0));
        toast("ボス出現 — 中央の巨体を狙う");
    }

    private void fireBullet() {
        Target target = nearestTarget();
        if (target == null) return;
        float[] damage = {1f, .58f, .75f, 2.4f};
        int[] colors = {0xfffff08a, 0xfffbbf24, 0xfffb7185, 0xffa5f3fc};
        game.bullets.add(new Bullet(roadX(playerY(), game.player.playerX), playerY() - 18f, target,
                game.player.damage * damage[game.player.weapon], colors[game.player.weapon]));
        game.player.playerFlash = .08f;
    }

    private Target nearestTarget() {
        Target candidate = null;
        float best = Float.MAX_VALUE;
        for (Target target : game.targets) {
            if (!target.alive) continue;
            float distance = target.y + (target.kind == BOSS ? 50f : 0f);
            if (distance < best && distance > height * .06f) {
                best = distance;
                candidate = target;
            }
        }
        return candidate;
    }

    private void burst(float x, float y, int color, int count) {
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2d;
            float speed = 30f + random.nextFloat() * 150f;
            game.particles.add(new Particle(x, y, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed,
                    .3f + random.nextFloat() * .45f, color, 1.5f + random.nextFloat() * 4f));
        }
    }

    private void addFloatingText(float x, float y, String text, int color) {
        game.texts.add(new FloatingText(x, y, text, color));
    }

    private void startStage(int stageNumber) {
        currentStage = stageNumber;
        game = new GameState(stageNumber, stages[stageNumber - 1], this);
        paused = false;
        screen = PLAYING;
        toast("左右にドラッグして照準を動かす");
    }

    private void endStage(boolean win) {
        if (game == null || game.ended) return;
        game.ended = true;
        resultWin = win;
        resultCoins = win ? 8 + currentStage * 4 + game.score / 10 : Math.max(1, game.score / 20);
        if (win) {
            prefs.edit().putInt("unlocked", Math.max(unlockedStage(), Math.min(stages.length, currentStage + 1)))
                    .putInt("totalWins", prefs.getInt("totalWins", 0) + 1).apply();
        }
        prefs.edit().putInt("coins", coins() + resultCoins).apply();
        screen = RESULT;
    }

    private void showStageSelect() {
        game = null;
        paused = false;
        screen = STAGE_SELECT;
    }

    private void showMenu() {
        game = null;
        paused = false;
        screen = MENU;
    }

    private void togglePause() {
        if (screen != PLAYING || game == null || game.ended) return;
        paused = !paused;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (screen == PLAYING) {
                if (pauseRect().contains(x, y)) {
                    togglePause();
                    return true;
                }
                if (paused) {
                    handlePausedTap(x, y);
                    return true;
                }
                dragging = true;
                setPlayerX(x);
                return true;
            }
            handleUiTap(x, y);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && screen == PLAYING && dragging && !paused) {
            setPlayerX(x);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            return true;
        }
        return true;
    }

    private void setPlayerX(float x) {
        if (game == null) return;
        game.player.playerX = clamp((x - roadBounds(playerY(), true)) / Math.max(1f, roadBounds(playerY(), false) - roadBounds(playerY(), true)), .08f, .92f);
    }

    private void handlePausedTap(float x, float y) {
        RectF resume = new RectF(width * .2f, height * .54f, width * .8f, height * .54f + 52f * ui);
        RectF stagesButton = new RectF(width * .2f, height * .54f + 66f * ui, width * .8f, height * .54f + 118f * ui);
        if (resume.contains(x, y)) paused = false;
        else if (stagesButton.contains(x, y)) showStageSelect();
    }

    private void handleUiTap(float x, float y) {
        if (screen == MENU) {
            float left = width * .12f;
            if (new RectF(left, height * .58f, left + width * .45f, height * .58f + 54f * ui).contains(x, y)) showStageSelect();
            else if (new RectF(left, height * .58f + 68f * ui, left + width * .45f, height * .58f + 122f * ui).contains(x, y)) screen = UPGRADES;
        } else if (screen == STAGE_SELECT) {
            if (backRect().contains(x, y)) showMenu();
            else {
                float left = width * .1f;
                float rowY = height * .31f;
                for (int i = 0; i < stages.length; i++) {
                    RectF row = new RectF(left, rowY, width - left, rowY + 70f * ui);
                    if (row.contains(x, y) && i + 1 <= unlockedStage()) {
                        startStage(i + 1);
                        return;
                    }
                    rowY += 82f * ui;
                }
                float upgradeY = height * .31f + stages.length * 82f * ui + 40f * ui;
                if (new RectF(left, upgradeY, left + 160f * ui, upgradeY + 50f * ui).contains(x, y)) screen = UPGRADES;
            }
        } else if (screen == UPGRADES) {
            if (backRect().contains(x, y)) showMenu();
            else {
                float left = width * .1f;
                float rowY = height * .31f;
                for (UpgradeConfig upgrade : upgrades) {
                    RectF row = new RectF(left, rowY, width - left, rowY + 76f * ui);
                    if (row.contains(x, y)) buyUpgrade(upgrade);
                    rowY += 88f * ui;
                }
            }
        } else if (screen == RESULT) {
            float left = width * .1f;
            RectF main = new RectF(left, height * .64f, left + width * .46f, height * .64f + 54f * ui);
            RectF stagesButton = new RectF(left, height * .64f + 68f * ui, left + width * .46f, height * .64f + 122f * ui);
            if (main.contains(x, y)) {
                if (resultWin && currentStage < stages.length && currentStage < unlockedStage()) startStage(currentStage + 1);
                else startStage(currentStage);
            } else if (stagesButton.contains(x, y)) showStageSelect();
        }
    }

    private void buyUpgrade(UpgradeConfig upgrade) {
        int level = upgradeLevel(upgrade.key);
        if (level >= upgrade.max) return;
        int cost = upgradeCost(level);
        if (coins() < cost) {
            toast("コインが足りません");
            return;
        }
        prefs.edit().putInt("coins", coins() - cost).putInt("upgrade_" + upgrade.key, level + 1).apply();
        toast(upgrade.name + " を強化しました");
    }

    private int upgradeCost(int level) { return 8 + level * 8; }
    private int coins() { return prefs.getInt("coins", 0); }
    private int unlockedStage() { return Math.max(1, Math.min(stages.length, prefs.getInt("unlocked", 1))); }
    private int upgradeLevel(String key) { return prefs.getInt("upgrade_" + key, 0); }
    private String weaponName(int weapon) { return new String[] {"PISTOL", "SMG", "SHOTGUN", "PIERCE"}[Math.min(3, weapon)]; }
    private String formatTime(float seconds) { return String.format(Locale.JAPAN, "%02d:%02d", (int) (seconds / 60f), (int) seconds % 60); }
    private void toast(String text) { message = text; messageUntil = SystemClock.uptimeMillis() + 2100L; }

    private float playerY() { return height * .82f; }
    private float roadX(float y, float lane) { return roadBounds(y, true) + (roadBounds(y, false) - roadBounds(y, true)) * lane; }
    private float roadBounds(float y, boolean left) {
        float top = height * .12f;
        float t = clamp((y - top) / Math.max(1f, height - top), 0f, 1f);
        return left ? lerp(width * .34f, width * .04f, t) : lerp(width * .66f, width * .96f, t);
    }
    private float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private void drawPanel(Canvas canvas) {
        paint.setColor(0xdd061225);
        canvas.drawRoundRect(new RectF(width * .045f, height * .055f, width * .955f, height * .94f), 28f * ui, 28f * ui, paint);
        stroke.setColor(0x335fc5ff);
        stroke.setStrokeWidth(1f * ui);
        canvas.drawRoundRect(new RectF(width * .045f, height * .055f, width * .955f, height * .94f), 28f * ui, 28f * ui, stroke);
    }

    private RectF backRect() { return new RectF(width * .075f, height * .1f, width * .2f, height * .16f); }
    private void drawBack(Canvas canvas, String label) {
        drawText(canvas, "←", width * .09f, height * .14f, 24f, Color.WHITE, false, false);
        drawText(canvas, label, width * .2f, height * .137f, 10f, 0xff67e8f9, true, false);
    }
    private RectF pauseRect() { return new RectF(width - 62f * ui, 14f * ui, width - 16f * ui, 60f * ui); }

    private void drawButton(Canvas canvas, RectF rect, String label, boolean primary) {
        paint.setColor(primary ? 0xff3e9cff : 0xcc09203a);
        canvas.drawRoundRect(rect, 12f * ui, 12f * ui, paint);
        stroke.setColor(primary ? 0xff7dd3fc : 0xff3b5b78);
        stroke.setStrokeWidth(1f * ui);
        canvas.drawRoundRect(rect, 12f * ui, 12f * ui, stroke);
        drawTextCentered(canvas, label, rect.centerX(), rect.centerY() + 5f * ui, primary ? 14f : 12f, primary ? 0xff03152b : Color.WHITE, true);
    }

    private void drawCard(Canvas canvas, RectF rect, int fill, int borderColor) {
        paint.setColor(fill);
        canvas.drawRoundRect(rect, 12f * ui, 12f * ui, paint);
        stroke.setColor(borderColor);
        stroke.setStrokeWidth(1f * ui);
        canvas.drawRoundRect(rect, 12f * ui, 12f * ui, stroke);
    }

    private void drawStatCard(Canvas canvas, RectF rect, String label, String value) {
        drawCard(canvas, rect, 0xcc09203a, 0xff3b5b78);
        drawTextCentered(canvas, label, rect.centerX(), rect.top + 22f * ui, 9f, 0xffa7b7ca, false);
        drawTextCentered(canvas, value, rect.centerX(), rect.top + 49f * ui, 15f, Color.WHITE, true);
    }

    private void drawMiniStat(Canvas canvas, RectF rect, String label, String value) {
        drawCard(canvas, rect, 0x99051123, 0x335b7896);
        drawText(canvas, label, rect.left + 8f * ui, rect.top + 16f * ui, 8f, 0xffa7b7ca, false, false);
        drawText(canvas, value, rect.left + 8f * ui, rect.top + 35f * ui, 12f, label.equals("武器") ? 0xffffb45c : Color.WHITE, true, false);
    }

    private void drawText(Canvas canvas, String text, float x, float y, float size, int color, boolean bold, boolean centered) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size * ui);
        paint.setTypeface(android.graphics.Typeface.create("sans", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        paint.setTextAlign(centered ? Paint.Align.CENTER : Paint.Align.LEFT);
        canvas.drawText(text, x, y, paint);
    }

    private void drawTextCentered(Canvas canvas, String text, float x, float y, float size, int color, boolean bold) {
        drawText(canvas, text, x, y, size, color, bold, true);
    }

    private void drawTextCentered(Canvas canvas, String text, float x, float y, float size, int color, boolean bold, boolean unused) {
        drawText(canvas, text, x, y, size, color, bold, true);
    }

    private static final class StageConfig {
        final String name;
        final float duration;
        final float enemyRate;
        final float barrelRate;
        final float enemySpeed;
        final int bossHp;

        StageConfig(String name, float duration, float enemyRate, float barrelRate, float enemySpeed, int bossHp) {
            this.name = name;
            this.duration = duration;
            this.enemyRate = enemyRate;
            this.barrelRate = barrelRate;
            this.enemySpeed = enemySpeed;
            this.bossHp = bossHp;
        }
    }

    private static final class UpgradeConfig {
        final String name;
        final String description;
        final String key;
        final int max;

        UpgradeConfig(String name, String description, String key, int max) {
            this.name = name;
            this.description = description;
            this.key = key;
            this.max = max;
        }
    }

    private static final class Target {
        final int kind;
        float lane;
        float y;
        final float speed;
        float hp;
        final int maxHp;
        final int reward;
        boolean alive = true;
        float phase;

        Target(int kind, float lane, float y, float speed, float hp, int reward) {
            this.kind = kind;
            this.lane = lane;
            this.y = y;
            this.speed = speed;
            this.hp = hp;
            this.maxHp = (int) hp;
            this.reward = reward;
        }
    }

    private static final class Bullet {
        float x;
        float y;
        final Target target;
        final float speed = 880f;
        final float damage;
        final int color;
        float life = 1.4f;

        Bullet(float x, float y, Target target, float damage, int color) {
            this.x = x;
            this.y = y;
            this.target = target;
            this.damage = damage;
            this.color = color;
        }
    }

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float life;
        final float maxLife;
        final int color;
        final float size;

        Particle(float x, float y, float vx, float vy, float life, int color, float size) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.maxLife = life;
            this.color = color;
            this.size = size;
        }
    }

    private static final class FloatingText {
        float x;
        float y;
        final String value;
        final int color;
        float life = 1f;
        final float maxLife = 1f;

        FloatingText(float x, float y, String value, int color) {
            this.x = x;
            this.y = y;
            this.value = value;
            this.color = color;
        }
    }

    private static final class PlayerState {
        float playerX = .5f;
        int squad;
        float damage;
        float fireInterval;
        float fireCooldown = .25f;
        int weapon;
        float playerFlash;
        float invulnerable;
    }

    private static final class GameState {
        final int stage;
        final StageConfig config;
        final PlayerState player = new PlayerState();
        final ArrayList<Target> targets = new ArrayList<>();
        final ArrayList<Bullet> bullets = new ArrayList<>();
        final ArrayList<Particle> particles = new ArrayList<>();
        final ArrayList<FloatingText> texts = new ArrayList<>();
        float time;
        float spawnBarrel = .5f;
        float spawnEnemy = 1f;
        float hint = 5f;
        float finishTimer;
        int score;
        int barrelsBroken;
        int enemiesDefeated;
        boolean bossSpawned;
        boolean bossDefeated;
        boolean ended;

        GameState(int stage, StageConfig config, GameView view) {
            this.stage = stage;
            this.config = config;
            player.squad = 1 + view.upgradeLevel("squad");
            player.damage = 1f + view.upgradeLevel("damage") * .16f;
            player.fireInterval = Math.max(.12f, .36f - view.upgradeLevel("fireRate") * .035f);
        }
    }
}
