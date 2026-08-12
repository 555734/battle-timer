package com.projectbreakline;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
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
    private static final int SETTINGS = 5;
    private static final int BARREL = 0;
    private static final int ENEMY = 1;
    private static final int BOSS = 2;
    private static final int WALKER = 0;
    private static final int RUNNER = 1;
    private static final int HEAVY = 2;
    private static final String PREFS = "project_breakline_offline_v1";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random(11L);
    private final SharedPreferences prefs;
    private final ToneGenerator tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 35);
    private final StageConfig[] stages = new StageConfig[] {
            new StageConfig("橋の入口", 38f, 2.15f, 5.8f, 38f, 30, 0),
            new StageConfig("検問線", 40f, 2.02f, 5.6f, 40f, 34, 0),
            new StageConfig("崩落区画", 42f, 1.90f, 5.4f, 42f, 38, 0),
            new StageConfig("走者の橋", 43f, 1.78f, 5.2f, 44f, 42, 1),
            new StageConfig("湾岸道路", 45f, 1.68f, 5.0f, 46f, 46, 1),
            new StageConfig("封鎖トンネル", 46f, 1.58f, 4.9f, 48f, 50, 1),
            new StageConfig("装甲前線", 48f, 1.50f, 4.8f, 50f, 56, 2),
            new StageConfig("高架交差点", 50f, 1.44f, 4.7f, 52f, 62, 2),
            new StageConfig("夜間都市", 51f, 1.38f, 4.6f, 54f, 68, 2),
            new StageConfig("赤色街区", 53f, 1.32f, 4.5f, 56f, 76, 3),
            new StageConfig("最終封鎖線", 55f, 1.26f, 4.4f, 58f, 84, 3),
            new StageConfig("BREAKLINE", 58f, 1.20f, 4.3f, 60f, 96, 3)
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
    private boolean resetArmed;
    private long resetArmedUntil;
    private boolean tonesReleased;
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
        canvas.save();
        if (game != null && game.shake > 0f && !reduceMotion()) {
            float amount = game.shake * 9f * ui;
            canvas.translate((random.nextFloat() - .5f) * amount, (random.nextFloat() - .5f) * amount);
        }
        drawWorld(canvas);
        canvas.restore();
        if (screen == MENU) drawMenu(canvas);
        else if (screen == STAGE_SELECT) drawStageSelect(canvas);
        else if (screen == UPGRADES) drawUpgrades(canvas);
        else if (screen == RESULT) drawResult(canvas);
        else if (screen == SETTINGS) drawSettings(canvas);
        if (screen == PLAYING) drawHud(canvas);
        postInvalidateOnAnimation();
    }

    private void update(float dt) {
        if (screen != PLAYING || game == null || paused || game.ended) return;
        game.time += dt;
        game.shake = Math.max(0f, game.shake - dt * 3.5f);
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
                updateBoss(target, dt);
            } else {
                target.y += target.speed * dt;
            }
            if (target.kind == ENEMY && target.y > playerY() - 75f) {
                float hitWidth = target.enemyType == HEAVY ? .28f : .22f;
                if (Math.abs(target.lane - game.player.playerX) < hitWidth) hitPlayer(target);
                else target.alive = false;
            }
            if (target.kind == BARREL && target.y > playerY() - 68f) {
                if (Math.abs(target.lane - game.player.playerX) < .24f) hitPlayer(target);
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
        if (game.time > game.config.duration + 18f && !game.bossDefeated) endStage(false);
    }

    private void drawWorld(Canvas canvas) {
        drawBackground(canvas);
        drawRoad(canvas);
        if (game != null) {
            drawBossWarnings(canvas);
            for (Target target : game.targets) if (target.alive) drawTarget(canvas, target);
            for (Bullet bullet : game.bullets) drawBullet(canvas, bullet);
            if (screen == PLAYING || screen == RESULT) drawPlayer(canvas);
            for (Particle particle : game.particles) drawParticle(canvas, particle);
            for (FloatingText text : game.texts) drawFloatingText(canvas, text);
        }
    }

    private void drawBackground(Canvas canvas) {
        int environment = game == null ? 0 : game.config.environment;
        int[] sky = {0xff89c7e8, 0xffdaa77a, 0xff606f91, 0xff402c59};
        int[] horizon = {0xff092342, 0xff39263a, 0xff101d35, 0xff150d29};
        paint.setShader(new android.graphics.LinearGradient(0f, 0f, 0f, height,
                sky[Math.min(3, environment)], horizon[Math.min(3, environment)], android.graphics.Shader.TileMode.CLAMP));
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
        float menuTop = height * .56f;
        drawButton(canvas, new RectF(x, menuTop, x + width * .48f, menuTop + 50f * ui), "開始", true);
        drawButton(canvas, new RectF(x, menuTop + 60f * ui, x + width * .48f, menuTop + 110f * ui), "永続強化", false);
        drawButton(canvas, new RectF(x, menuTop + 120f * ui, x + width * .48f, menuTop + 170f * ui), "設定", false);
        drawText(canvas, "通信なし・アカウントなし・保存はこの端末のみ", x, height * .84f, 11f, 0xffa7b7ca, false, false);
        drawText(canvas, "Android native / COMPLETE MVP", x, height * .89f, 10f, 0xff71849b, false, false);
    }

    private void drawStageSelect(Canvas canvas) {
        drawPanel(canvas);
        drawBack(canvas, "STAGE SELECT");
        float x = width * .1f;
        drawText(canvas, "ステージを選ぶ", x, height * .2f, 30f, Color.WHITE, true, false);
        drawText(canvas, "全12区画。突破すると次の区画が開放されます。", x, height * .25f, 12f, 0xffa7b7ca, false, false);
        float gridTop = height * .30f;
        float gap = 8f * ui;
        float cardWidth = (width - x * 2f - gap * 2f) / 3f;
        float cardHeight = Math.min(78f * ui, (height * .48f - gap * 3f) / 4f);
        for (int i = 0; i < stages.length; i++) {
            int number = i + 1;
            boolean unlocked = number <= unlockedStage();
            int col = i % 3;
            int row = i / 3;
            float left = x + col * (cardWidth + gap);
            float top = gridTop + row * (cardHeight + gap);
            RectF card = new RectF(left, top, left + cardWidth, top + cardHeight);
            drawCard(canvas, card, unlocked ? 0xff09203a : 0xff122235, unlocked ? 0xff67e8f9 : 0xff384b61);
            drawTextCentered(canvas, unlocked ? String.format(Locale.JAPAN, "%02d", number) : "LOCK", card.centerX(), top + 29f * ui, 17f, unlocked ? Color.WHITE : 0xff71849b, true);
            String shortName = stages[i].name.length() > 6 ? stages[i].name.substring(0, 6) : stages[i].name;
            drawTextCentered(canvas, shortName, card.centerX(), top + 51f * ui, 9f, unlocked ? 0xffa5f3fc : 0xff71849b, false);
            if (number < unlockedStage()) {
                drawTextCentered(canvas, "CLEAR", card.centerX(), card.bottom - 7f * ui, 7f, 0xff75f0b6, true);
            }
        }
        float buttonY = gridTop + 4f * (cardHeight + gap) + 14f * ui;
        drawButton(canvas, new RectF(x, buttonY, width * .49f, buttonY + 45f * ui), "永続強化", false);
        drawButton(canvas, new RectF(width * .51f, buttonY, width - x, buttonY + 45f * ui), "設定", false);
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

    private void drawSettings(Canvas canvas) {
        drawPanel(canvas);
        drawBack(canvas, "SETTINGS");
        float x = width * .1f;
        drawText(canvas, "設定", x, height * .2f, 30f, Color.WHITE, true, false);
        drawText(canvas, "すべての設定と進行はこの端末だけに保存されます。", x, height * .25f, 11f, 0xffa7b7ca, false, false);
        float y = height * .31f;
        drawSettingRow(canvas, settingRect(0), "効果音", "射撃以外の重要な効果音", soundEnabled());
        drawSettingRow(canvas, settingRect(1), "振動", "被弾・報酬・ボス攻撃のフィードバック", hapticsEnabled());
        drawSettingRow(canvas, settingRect(2), "画面揺れを抑える", "強い揺れ演出を無効化", reduceMotion());
        y += 3f * 72f * ui;
        RectF reset = new RectF(x, y + 18f * ui, width - x, y + 70f * ui);
        drawButton(canvas, reset, resetArmed && resetArmedUntil > SystemClock.uptimeMillis() ? "もう一度押して全データ削除" : "ローカル進行を初期化", false);
        drawText(canvas, "※ステージ開放・コイン・永続強化が消去されます", x, y + 93f * ui, 9f, 0xffff8798, false, false);
        drawText(canvas, "PROJECT BREAKLINE  1.0.0  /  完全オフライン", x, height * .84f, 10f, 0xff71849b, false, false);
    }

    private RectF settingRect(int index) {
        float x = width * .1f;
        float top = height * .31f + index * 72f * ui;
        return new RectF(x, top, width - x, top + 62f * ui);
    }

    private void drawSettingRow(Canvas canvas, RectF rect, String title, String detail, boolean enabled) {
        drawCard(canvas, rect, 0xff09203a, enabled ? 0xff3e9cff : 0xff3b5b78);
        drawText(canvas, title, rect.left + 14f * ui, rect.top + 25f * ui, 13f, Color.WHITE, true, false);
        drawText(canvas, detail, rect.left + 14f * ui, rect.top + 45f * ui, 8.5f, 0xffa7b7ca, false, false);
        RectF toggle = new RectF(rect.right - 55f * ui, rect.centerY() - 14f * ui, rect.right - 13f * ui, rect.centerY() + 14f * ui);
        paint.setColor(enabled ? 0xff3e9cff : 0xff25384b);
        canvas.drawRoundRect(toggle, 14f * ui, 14f * ui, paint);
        paint.setColor(Color.WHITE);
        float knobX = enabled ? toggle.right - 14f * ui : toggle.left + 14f * ui;
        canvas.drawCircle(knobX, toggle.centerY(), 10f * ui, paint);
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
        else if (target.kind == ENEMY) drawEnemy(canvas, target);
        else drawBoss(canvas, target);
        canvas.restore();
    }

    private void drawBossWarnings(Canvas canvas) {
        if (game == null) return;
        for (Target target : game.targets) {
            if (!target.alive || target.kind != BOSS || target.attackState == 0) continue;
            float pulse = .45f + .25f * (float) Math.sin(target.phase * 14f);
            if (target.attackType == 0) {
                drawLaneZone(canvas, target.attackLane, .19f,
                        Color.argb((int) (255f * pulse), 255, 49, 78));
                if (target.attackState == 1) {
                    drawTextCentered(canvas, "突進予告 — 横へ回避", width / 2f, height * .26f, 13f, 0xffffd2d9, true);
                }
            } else {
                paint.setColor(Color.argb(target.attackState == 1 ? 82 : 125, 255, 35, 67));
                canvas.drawRect(roadBounds(height * .16f, true), height * .16f,
                        roadBounds(height * .86f, false), height * .86f, paint);
                drawLaneZone(canvas, target.safeLane, .17f, Color.argb(175, 70, 230, 213));
                if (target.attackState == 1) {
                    drawTextCentered(canvas, "地面強打 — 青いレーンへ", width / 2f, height * .26f, 13f, 0xffbdfcf4, true);
                }
            }
        }
    }

    private void drawLaneZone(Canvas canvas, float lane, float halfWidth, int color) {
        float top = height * .16f;
        float bottom = height * .86f;
        path.reset();
        path.moveTo(roadX(top, clamp(lane - halfWidth, 0f, 1f)), top);
        path.lineTo(roadX(top, clamp(lane + halfWidth, 0f, 1f)), top);
        path.lineTo(roadX(bottom, clamp(lane + halfWidth, 0f, 1f)), bottom);
        path.lineTo(roadX(bottom, clamp(lane - halfWidth, 0f, 1f)), bottom);
        path.close();
        paint.setColor(color);
        canvas.drawPath(path, paint);
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

    private void drawEnemy(Canvas canvas, Target target) {
        float bodyWidth = target.enemyType == HEAVY ? 19f : target.enemyType == RUNNER ? 10f : 13f;
        float bodyHeight = target.enemyType == HEAVY ? 31f : 25f;
        int skin = target.enemyType == HEAVY ? 0xff8e5360 : target.enemyType == RUNNER ? 0xffd8b56f : 0xff9ba9ad;
        paint.setColor(target.enemyType == HEAVY ? 0xff4c2332 : 0xff283541);
        canvas.drawCircle(0f, -18f, target.enemyType == HEAVY ? 13f : 10f, paint);
        paint.setColor(skin);
        canvas.drawRoundRect(new RectF(-bodyWidth, -9f, bodyWidth, bodyHeight), 8f, 8f, paint);
        if (target.enemyType == HEAVY) {
            paint.setColor(0xff563440);
            canvas.drawRoundRect(new RectF(-23f, -5f, 23f, 12f), 6f, 6f, paint);
            drawTextCentered(canvas, "H", 0f, 8f, 9f, 0xffffd6dc, true);
        } else if (target.enemyType == RUNNER) {
            paint.setColor(0xffffc857);
            path.reset();
            path.moveTo(-16f, 3f);
            path.lineTo(0f, -7f);
            path.lineTo(16f, 3f);
            path.close();
            canvas.drawPath(path, paint);
        }
        stroke.setColor(0xff1b2530);
        stroke.setStrokeWidth(5f);
        float stride = target.enemyType == RUNNER ? 25f : 18f;
        canvas.drawLine(-9f, 7f, -stride, 29f, stroke);
        canvas.drawLine(9f, 7f, stride, 29f, stroke);
        canvas.drawLine(-8f, 2f, -21f, 13f, stroke);
        canvas.drawLine(8f, 2f, 21f, 13f, stroke);
        paint.setColor(0xffff647d);
        canvas.drawCircle(0f, -18f, 3f, paint);
        if (target.enemyType == HEAVY) {
            paint.setColor(0x88ff8798);
            canvas.drawArc(new RectF(-30f, -35f, 30f, 31f), 190f, 160f, false, paint);
        }
    }

    private void drawBoss(Canvas canvas, Target target) {
        int aura = target.attackState == 1 ? 0x88ff4057 : 0x44ff546a;
        paint.setColor(aura);
        canvas.drawCircle(0f, 0f, 76f + (float) Math.sin(target.phase * 7f) * 5f, paint);
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
        paint.setColor(0x99203953);
        canvas.drawRoundRect(new RectF(-54f, -60f, 54f, -55f), 3f, 3f, paint);
        paint.setColor(0xffff647d);
        canvas.drawRoundRect(new RectF(-54f, -60f, -54f + 108f * Math.max(0f, target.hp / target.maxHp), -55f), 3f, 3f, paint);
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

    private void updateBoss(Target boss, float dt) {
        boss.phase += dt;
        if (boss.attackState == 0) {
            boss.y = height * (.18f + .006f * (float) Math.sin(boss.phase * 3.2f));
            boss.lane = lerp(boss.lane, .5f, Math.min(1f, dt * 1.8f));
            boss.attackTimer -= dt;
            if (boss.attackTimer <= 0f) {
                boss.attackState = 1;
                boss.attackType = ((int) (game.time / 3f) + game.stage) % 2;
                boss.attackTimer = 1.15f;
                boss.attackProgress = 0f;
                boss.strikeDone = false;
                if (boss.attackType == 0) {
                    boss.attackLane = clamp(Math.round(game.player.playerX * 2f) / 2f, .08f, .92f);
                    toast("赤いレーンから離れる");
                } else {
                    float[] lanes = {.17f, .5f, .83f};
                    int current = game.player.playerX < .335f ? 0 : game.player.playerX > .665f ? 2 : 1;
                    boss.safeLane = lanes[(current + 1 + random.nextInt(2)) % 3];
                    toast("青いレーンへ移動");
                }
                playTone(ToneGenerator.TONE_PROP_BEEP, 90);
                feedback(false);
            }
            return;
        }

        if (boss.attackState == 1) {
            boss.attackTimer -= dt;
            if (boss.attackType == 0) boss.lane = lerp(boss.lane, boss.attackLane, Math.min(1f, dt * 4f));
            if (boss.attackTimer <= 0f) {
                boss.attackState = 2;
                boss.attackProgress = 0f;
                boss.strikeDone = false;
                game.shake = Math.max(game.shake, reduceMotion() ? .12f : .38f);
                playTone(ToneGenerator.TONE_PROP_NACK, 150);
            }
            return;
        }

        boss.attackProgress += dt;
        if (boss.attackType == 0) {
            float duration = 1.05f;
            float t = Math.min(1f, boss.attackProgress / duration);
            boss.lane = boss.attackLane;
            boss.y = lerp(height * .18f, height * .77f, (float) Math.sin(Math.PI * t));
            if (!boss.strikeDone && boss.attackProgress >= .43f) {
                boss.strikeDone = true;
                if (Math.abs(game.player.playerX - boss.attackLane) < .23f) {
                    damageSquad(2, roadX(playerY(), boss.attackLane));
                } else {
                    addFloatingText(roadX(playerY(), game.player.playerX), playerY() - 65f, "DODGE", 0xff75f0b6);
                }
                game.shake = Math.max(game.shake, reduceMotion() ? .18f : .75f);
                feedback(true);
            }
            if (boss.attackProgress >= duration) finishBossAttack(boss);
        } else {
            float duration = .82f;
            float t = Math.min(1f, boss.attackProgress / duration);
            boss.y = height * (.18f - .045f * (float) Math.sin(Math.PI * t));
            if (!boss.strikeDone && boss.attackProgress >= .31f) {
                boss.strikeDone = true;
                if (Math.abs(game.player.playerX - boss.safeLane) > .18f) {
                    damageSquad(1, roadX(playerY(), game.player.playerX));
                } else {
                    addFloatingText(roadX(playerY(), game.player.playerX), playerY() - 65f, "SAFE", 0xff75f0b6);
                }
                game.shake = Math.max(game.shake, reduceMotion() ? .18f : .9f);
                burst(roadX(playerY(), game.player.playerX), playerY() - 24f, 0xffffb45c, 24);
                feedback(true);
            }
            if (boss.attackProgress >= duration) finishBossAttack(boss);
        }
    }

    private void finishBossAttack(Target boss) {
        boss.attackState = 0;
        boss.attackTimer = 1.8f + random.nextFloat() * .8f;
        boss.attackProgress = 0f;
        boss.y = height * .18f;
        boss.lane = .5f;
    }

    private void hitPlayer(Target target) {
        target.alive = false;
        int damage = target.kind == ENEMY && target.enemyType == HEAVY ? 2 : 1;
        damageSquad(damage, roadX(target.y, target.lane));
    }

    private void damageSquad(int amount, float sourceX) {
        if (game.player.invulnerable > 0f) return;
        game.player.invulnerable = .42f;
        game.player.squad = Math.max(0, game.player.squad - amount);
        game.shake = Math.max(game.shake, reduceMotion() ? .12f : .65f);
        burst(sourceX, playerY() - 15f, 0xffff647d, 16 + amount * 4);
        addFloatingText(sourceX, playerY() - 60f, "-" + amount + " 仲間", 0xffff9aa9);
        playTone(ToneGenerator.TONE_PROP_NACK, 120);
        feedback(true);
        if (game.player.squad <= 0) endStage(false);
    }

    private void destroyTarget(Target target) {
        if (!target.alive) return;
        target.alive = false;
        float x = roadX(target.y, target.lane);
        if (target.kind == BARREL) {
            for (Target sibling : game.targets) {
                if (sibling.alive && sibling.kind == BARREL && sibling.groupId == target.groupId) sibling.alive = false;
            }
            game.barrelsBroken++;
            game.score += 5;
            String label = target.reward == 0 ? "+1 仲間" : target.reward == 1 ? "DAMAGE UP" : target.reward == 2 ? "RAPID FIRE" : "WEAPON UP";
            addFloatingText(x, target.y - 25f, label, target.reward == 0 ? 0xff9ff6ce : 0xffffd36a);
            burst(x, target.y, 0xffcaa16d, 20);
            if (target.reward == 0) game.player.squad++;
            else if (target.reward == 1) game.player.damage += .22f;
            else if (target.reward == 2) game.player.fireInterval = Math.max(.095f, game.player.fireInterval * .86f);
            else game.player.weapon = Math.min(3, game.player.weapon + 1);
            game.shake = Math.max(game.shake, .22f);
            playTone(ToneGenerator.TONE_PROP_ACK, 90);
            feedback(false);
        } else if (target.kind == ENEMY) {
            game.enemiesDefeated++;
            game.score += target.enemyType == HEAVY ? 24 : target.enemyType == RUNNER ? 14 : 10;
            burst(x, target.y, target.enemyType == HEAVY ? 0xffffa0b2 : 0xffff7b8e, target.enemyType == HEAVY ? 20 : 11);
        } else {
            game.bossDefeated = true;
            game.finishTimer = .8f;
            game.score += 60;
            game.shake = reduceMotion() ? .25f : 1f;
            burst(x, target.y, 0xffffd166, 45);
            addFloatingText(x, target.y - 45f, "BOSS BREAK!", 0xfffff0a8);
            playTone(ToneGenerator.TONE_PROP_ACK, 260);
            feedback(true);
        }
    }

    private void spawnBarrel() {
        int group = ++game.barrelGroup;
        int count = (group + game.stage) % 3 == 0 ? 3 : 2;
        float[] lanes = count == 3 ? new float[] {.16f, .5f, .84f} : new float[] {.28f, .72f};
        float hp = 3.5f + game.stage * 1.15f + (int) (game.time / 15f);
        int baseReward = (group + game.stage) % 4;
        for (int i = 0; i < count; i++) {
            int reward = (baseReward + i) % 4;
            game.targets.add(new Target(BARREL, lanes[i], height * .11f, 26f + game.stage * 2.5f,
                    hp + (reward == 3 ? 1f : 0f), reward, WALKER, group));
        }
    }

    private void spawnEnemy() {
        float lane = random.nextInt(3) / 2f;
        float roll = random.nextFloat();
        int type = WALKER;
        if (game.stage >= 7 && roll < .22f) type = HEAVY;
        else if (game.stage >= 4 && roll < .55f) type = RUNNER;
        float baseHp = 1f + game.stage * .34f;
        float hp = type == HEAVY ? baseHp * 3.1f : type == RUNNER ? Math.max(1f, baseHp * .72f) : baseHp;
        float speed = game.config.enemySpeed + random.nextFloat() * 10f;
        if (type == RUNNER) speed *= 1.52f;
        else if (type == HEAVY) speed *= .64f;
        game.targets.add(new Target(ENEMY, lane, height * .09f - random.nextFloat() * 26f,
                speed, hp, 0, type, -1));
    }

    private void spawnBoss() {
        game.bossSpawned = true;
        Target boss = new Target(BOSS, .5f, height * .18f, 10f, game.config.bossHp, 0);
        boss.attackTimer = 2.1f;
        game.targets.add(boss);
        game.shake = reduceMotion() ? .18f : .7f;
        playTone(ToneGenerator.TONE_PROP_BEEP, 180);
        feedback(true);
        toast("ボス出現 — 攻撃予告を見て回避");
    }

    private void fireBullet() {
        Target target = nearestTarget();
        if (target == null) return;
        float[] damage = {1f, .58f, .75f, 2.4f};
        int[] colors = {0xfffff08a, 0xfffbbf24, 0xfffb7185, 0xffa5f3fc};
        int shooters = Math.min(5, game.player.squad);
        float centerX = roadX(playerY(), game.player.playerX);
        for (int i = 0; i < shooters; i++) {
            float offset = (i - (shooters - 1) / 2f) * 8f * ui;
            game.bullets.add(new Bullet(centerX + offset, playerY() - 18f + Math.abs(offset) * .1f, target,
                    game.player.damage * damage[game.player.weapon], colors[game.player.weapon]));
        }
        game.player.playerFlash = .08f;
    }

    private Target nearestTarget() {
        Target candidate = null;
        float best = -Float.MAX_VALUE;
        for (Target target : game.targets) {
            if (!target.alive) continue;
            float priority = target.y - Math.abs(target.lane - game.player.playerX) * height * .28f;
            if (target.kind == BOSS) priority += 24f;
            if (priority > best && target.y > height * .06f && target.y < playerY() + 10f) {
                best = priority;
                candidate = target;
            }
        }
        return candidate;
    }

    private void burst(float x, float y, int color, int count) {
        int available = Math.max(0, 180 - game.particles.size());
        count = Math.min(count, available);
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
        playTone(win ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_NACK, win ? 280 : 180);
        feedback(true);
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
            float menuTop = height * .56f;
            if (new RectF(left, menuTop, left + width * .48f, menuTop + 50f * ui).contains(x, y)) showStageSelect();
            else if (new RectF(left, menuTop + 60f * ui, left + width * .48f, menuTop + 110f * ui).contains(x, y)) screen = UPGRADES;
            else if (new RectF(left, menuTop + 120f * ui, left + width * .48f, menuTop + 170f * ui).contains(x, y)) screen = SETTINGS;
        } else if (screen == STAGE_SELECT) {
            if (backRect().contains(x, y)) showMenu();
            else {
                float left = width * .1f;
                float gridTop = height * .30f;
                float gap = 8f * ui;
                float cardWidth = (width - left * 2f - gap * 2f) / 3f;
                float cardHeight = Math.min(78f * ui, (height * .48f - gap * 3f) / 4f);
                for (int i = 0; i < stages.length; i++) {
                    int col = i % 3;
                    int row = i / 3;
                    float cardLeft = left + col * (cardWidth + gap);
                    float cardTop = gridTop + row * (cardHeight + gap);
                    RectF card = new RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
                    if (card.contains(x, y) && i + 1 <= unlockedStage()) {
                        startStage(i + 1);
                        return;
                    }
                }
                float buttonY = gridTop + 4f * (cardHeight + gap) + 14f * ui;
                if (new RectF(left, buttonY, width * .49f, buttonY + 45f * ui).contains(x, y)) screen = UPGRADES;
                else if (new RectF(width * .51f, buttonY, width - left, buttonY + 45f * ui).contains(x, y)) screen = SETTINGS;
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
        } else if (screen == SETTINGS) {
            if (backRect().contains(x, y)) showMenu();
            else if (settingRect(0).contains(x, y)) {
                prefs.edit().putBoolean("sound", !soundEnabled()).apply();
                playTone(ToneGenerator.TONE_PROP_ACK, 80);
            } else if (settingRect(1).contains(x, y)) {
                prefs.edit().putBoolean("haptics", !hapticsEnabled()).apply();
                feedback(false);
            } else if (settingRect(2).contains(x, y)) {
                prefs.edit().putBoolean("reduceMotion", !reduceMotion()).apply();
                toast(reduceMotion() ? "画面揺れを抑えます" : "画面揺れを有効にしました");
            } else {
                float top = height * .31f + 3f * 72f * ui + 18f * ui;
                RectF reset = new RectF(width * .1f, top, width * .9f, top + 52f * ui);
                if (reset.contains(x, y)) resetLocalProgress();
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
        playTone(ToneGenerator.TONE_PROP_ACK, 90);
        feedback(false);
    }

    private void resetLocalProgress() {
        long now = SystemClock.uptimeMillis();
        if (!resetArmed || now > resetArmedUntil) {
            resetArmed = true;
            resetArmedUntil = now + 3500L;
            toast("確認のため、もう一度押してください");
            feedback(false);
            return;
        }
        prefs.edit().clear().apply();
        resetArmed = false;
        resetArmedUntil = 0L;
        toast("ローカル進行を初期化しました");
        playTone(ToneGenerator.TONE_PROP_ACK, 110);
        feedback(true);
    }

    private int upgradeCost(int level) { return 8 + level * 8; }
    private int coins() { return prefs.getInt("coins", 0); }
    private int unlockedStage() { return Math.max(1, Math.min(stages.length, prefs.getInt("unlocked", 1))); }
    private int upgradeLevel(String key) { return prefs.getInt("upgrade_" + key, 0); }
    private boolean soundEnabled() { return prefs.getBoolean("sound", true); }
    private boolean hapticsEnabled() { return prefs.getBoolean("haptics", true); }
    private boolean reduceMotion() { return prefs.getBoolean("reduceMotion", false); }
    private String weaponName(int weapon) { return new String[] {"PISTOL", "SMG", "SHOTGUN", "PIERCE"}[Math.min(3, weapon)]; }
    private String formatTime(float seconds) { return String.format(Locale.JAPAN, "%02d:%02d", (int) (seconds / 60f), (int) seconds % 60); }
    private void toast(String text) { message = text; messageUntil = SystemClock.uptimeMillis() + 2100L; }

    private void playTone(int tone, int durationMs) {
        if (!soundEnabled() || tonesReleased) return;
        try {
            tones.startTone(tone, durationMs);
        } catch (RuntimeException ignored) {
            // Audio hardware is optional; gameplay remains functional without it.
        }
    }

    private void feedback(boolean strong) {
        if (!hapticsEnabled()) return;
        performHapticFeedback(strong ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP);
    }

    public void pauseFromSystem() {
        if (screen == PLAYING && game != null && !game.ended) paused = true;
        dragging = false;
    }

    public void resumeFromSystem() {
        lastFrame = SystemClock.uptimeMillis();
    }

    public void release() {
        if (!tonesReleased) {
            tones.release();
            tonesReleased = true;
        }
    }

    public boolean handleBack() {
        if (screen == PLAYING) {
            if (paused) showStageSelect();
            else paused = true;
            return true;
        }
        if (screen == MENU) return false;
        showMenu();
        return true;
    }

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
        final int environment;

        StageConfig(String name, float duration, float enemyRate, float barrelRate, float enemySpeed, int bossHp, int environment) {
            this.name = name;
            this.duration = duration;
            this.enemyRate = enemyRate;
            this.barrelRate = barrelRate;
            this.enemySpeed = enemySpeed;
            this.bossHp = bossHp;
            this.environment = environment;
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
        final int enemyType;
        final int groupId;
        boolean alive = true;
        float phase;
        int attackState;
        int attackType;
        float attackTimer;
        float attackProgress;
        float attackLane = .5f;
        float safeLane = .5f;
        boolean strikeDone;

        Target(int kind, float lane, float y, float speed, float hp, int reward) {
            this(kind, lane, y, speed, hp, reward, WALKER, -1);
        }

        Target(int kind, float lane, float y, float speed, float hp, int reward, int enemyType, int groupId) {
            this.kind = kind;
            this.lane = lane;
            this.y = y;
            this.speed = speed;
            this.hp = hp;
            this.maxHp = (int) hp;
            this.reward = reward;
            this.enemyType = enemyType;
            this.groupId = groupId;
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
        float shake;
        int score;
        int barrelsBroken;
        int enemiesDefeated;
        int barrelGroup;
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
