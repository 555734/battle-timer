package com.projectbreakline;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * OpenGL ES 2.0 implementation of PROJECT BREAKLINE.
 *
 * The playfield is rendered with a real perspective camera, depth buffer and
 * low-poly meshes. Android Canvas is used only for touch-friendly menu and HUD
 * text that would otherwise need a bitmap font atlas.
 */
public final class Breakline3DView extends FrameLayout {
    private static final int MENU = 0;
    private static final int STAGE_SELECT = 1;
    private static final int PLAYING = 2;
    private static final int RESULT = 3;

    private final BattleModel model;
    private final BattleSurface surface;
    private final Overlay overlay;
    private volatile UiSnapshot snapshot;
    private volatile float desiredLane;

    public Breakline3DView(Context context) {
        super(context);
        setWillNotDraw(false);
        SharedPreferences prefs = context.getSharedPreferences("project_breakline_3d_v1", Context.MODE_PRIVATE);
        model = new BattleModel(prefs);
        desiredLane = 0f;
        snapshot = model.snapshot();
        surface = new BattleSurface(context, this);
        overlay = new Overlay(context, this);
        addView(surface, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(overlay, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void publish() {
        snapshot = model.snapshot();
        overlay.postInvalidateOnAnimation();
    }

    private void setDesiredLane(float lane) {
        desiredLane = clamp(lane, -3.05f, 3.05f);
    }

    private void selectStage(final int stage) {
        surface.queueEvent(new Runnable() {
            @Override public void run() {
                model.startStage(stage);
                desiredLane = 0f;
                publish();
            }
        });
    }

    private void showStages() {
        surface.queueEvent(new Runnable() {
            @Override public void run() {
                model.showStages();
                publish();
            }
        });
    }

    private void showMenu() {
        surface.queueEvent(new Runnable() {
            @Override public void run() {
                model.showMenu();
                publish();
            }
        });
    }

    private void togglePause() {
        surface.queueEvent(new Runnable() {
            @Override public void run() {
                model.paused = !model.paused;
                publish();
            }
        });
    }

    void tickFromRenderer(float dt) {
        model.tick(dt, desiredLane);
        publish();
    }

    BattleModel model() { return model; }

    public void onHostResume() { surface.onResume(); }

    public void onHostPause() {
        surface.queueEvent(new Runnable() {
            @Override public void run() { model.paused = model.mode == PLAYING; publish(); }
        });
        surface.onPause();
    }

    public void release() { }

    public boolean handleBack() {
        UiSnapshot current = snapshot;
        if (current.mode == MENU) return false;
        if (current.mode == PLAYING && !current.paused) {
            togglePause();
        } else {
            showMenu();
        }
        return true;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class BattleSurface extends GLSurfaceView {
        BattleSurface(Context context, Breakline3DView host) {
            super(context);
            setEGLContextClientVersion(2);
            setPreserveEGLContextOnPause(true);
            setRenderer(new WorldRenderer(host));
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }
    }

    private static final class Overlay extends View {
        private final Breakline3DView host;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float downX;
        private float downY;

        Overlay(Context context, Breakline3DView host) {
            super(context);
            this.host = host;
            stroke.setStyle(Paint.Style.STROKE);
            setClickable(true);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            UiSnapshot s = host.snapshot;
            if (s.mode == MENU) drawMenu(canvas, s);
            else if (s.mode == STAGE_SELECT) drawStageSelect(canvas, s);
            else if (s.mode == PLAYING) drawHud(canvas, s);
            else drawResult(canvas, s);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            UiSnapshot s = host.snapshot;
            float x = event.getX();
            float y = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = x;
                downY = y;
                if (s.mode == PLAYING && !s.paused && !pauseRect().contains(x, y)) {
                    host.setDesiredLane(mapLane(x));
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (s.mode == PLAYING && !s.paused) host.setDesiredLane(mapLane(x));
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                handleTap(x, y, s);
                return true;
            }
            return true;
        }

        private void handleTap(float x, float y, UiSnapshot s) {
            if (s.mode == MENU) {
                if (startRect().contains(x, y)) host.showStages();
            } else if (s.mode == STAGE_SELECT) {
                if (backRect().contains(x, y)) {
                    host.showMenu();
                    return;
                }
                for (int i = 0; i < 12; i++) {
                    if (stageRect(i).contains(x, y) && i + 1 <= s.unlocked) {
                        host.selectStage(i + 1);
                        return;
                    }
                }
            } else if (s.mode == PLAYING) {
                if (pauseRect().contains(x, y)) {
                    host.togglePause();
                } else if (s.paused) {
                    if (resumeRect().contains(x, y)) host.togglePause();
                    else if (exitRect().contains(x, y)) host.showStages();
                }
            } else if (s.mode == RESULT) {
                if (mainResultRect().contains(x, y)) host.selectStage(s.stage);
                else if (stageResultRect().contains(x, y)) host.showStages();
            }
        }

        private void drawMenu(Canvas c, UiSnapshot s) {
            float w = getWidth();
            float h = getHeight();
            paint.setShader(new LinearGradient(0f, 0f, 0f, h, 0x10000000, 0xb0091020, Shader.TileMode.CLAMP));
            c.drawRect(0f, 0f, w, h, paint);
            paint.setShader(null);
            drawText(c, "PROJECT", w * .10f, h * .22f, 42f, Color.WHITE, true, false);
            drawText(c, "BREAKLINE", w * .10f, h * .30f, 42f, 0xff61d8ff, true, false);
            drawText(c, "3D NATIVE EDITION", w * .11f, h * .345f, 11f, 0xffa5f3fc, true, false);
            drawText(c, "奥行きのある戦場を横移動し、", w * .10f, h * .43f, 14f, 0xffe0edf8, false, false);
            drawText(c, "樽の報酬を選んで群れを突破する。", w * .10f, h * .46f, 14f, 0xffe0edf8, false, false);
            drawButton(c, startRect(), "3D ランを開始", true);
            drawText(c, "OPENGL ES 2.0 / 完全オフライン / 12 STAGES", w * .10f, h * .84f, 9f, 0xff9fb5c9, true, false);
            drawText(c, "コイン " + s.coins + "   クリア " + Math.max(0, s.unlocked - 1) + "/12", w * .10f, h * .88f, 11f, 0xffffd166, true, false);
        }

        private void drawStageSelect(Canvas c, UiSnapshot s) {
            float w = getWidth();
            float h = getHeight();
            drawVeil(c, .77f);
            drawText(c, "←", w * .08f, h * .105f, 26f, Color.WHITE, false, false);
            drawText(c, "3D STAGE SELECT", w * .18f, h * .10f, 12f, 0xff7dd3fc, true, false);
            drawText(c, "区画を選ぶ", w * .10f, h * .18f, 28f, Color.WHITE, true, false);
            drawText(c, "突破した区画が順番に開放されます。", w * .10f, h * .225f, 11f, 0xffbed0e1, false, false);
            for (int i = 0; i < 12; i++) {
                boolean open = i + 1 <= s.unlocked;
                RectF r = stageRect(i);
                drawCard(c, r, open ? 0xe70a2038 : 0xd9131c2b, open ? 0xff55d8ff : 0xff33485b);
                drawText(c, open ? String.format(Locale.JAPAN, "%02d", i + 1) : "LOCK", r.centerX(), r.top + 31f * density(), 15f, open ? Color.WHITE : 0xff72849a, true, true);
                drawText(c, stageName(i), r.centerX(), r.bottom - 13f * density(), 8.5f, open ? 0xff9be8ff : 0xff71849a, false, true);
            }
            drawText(c, "樽：仲間 / 威力 / 連射 / 武器", w * .10f, h * .84f, 10f, 0xffb8cee2, false, false);
            drawText(c, "敵：ウォーカー / ランナー / ヘビー / ボス", w * .10f, h * .875f, 10f, 0xffb8cee2, false, false);
        }

        private void drawHud(Canvas c, UiSnapshot s) {
            float w = getWidth();
            float h = getHeight();
            paint.setColor(0x8d03101e);
            c.drawRoundRect(new RectF(10f * density(), 10f * density(), w - 10f * density(), 76f * density()), 15f * density(), 15f * density(), paint);
            drawText(c, "STAGE " + s.stage + "  " + stageName(s.stage - 1), 20f * density(), 32f * density(), 11f, 0xff81e6ff, true, false);
            drawText(c, formatTime(s.time), 20f * density(), 56f * density(), 18f, Color.WHITE, true, false);
            RectF progress = new RectF(20f * density(), 64f * density(), w - 72f * density(), 68f * density());
            paint.setColor(0x663a5064);
            c.drawRoundRect(progress, 3f * density(), 3f * density(), paint);
            paint.setColor(0xff52d9ff);
            c.drawRoundRect(new RectF(progress.left, progress.top, progress.left + progress.width() * s.progress, progress.bottom), 3f * density(), 3f * density(), paint);
            drawCard(c, pauseRect(), 0xdd0d2944, 0xff7dd3fc);
            drawText(c, "Ⅱ", pauseRect().centerX(), pauseRect().centerY() + 6f * density(), 18f, Color.WHITE, true, true);

            float bottom = h - 58f * density();
            drawMini(c, new RectF(14f * density(), bottom, 102f * density(), bottom + 43f * density()), "仲間", String.valueOf(s.squad), 0xff75f0b6);
            drawMini(c, new RectF(w * .5f - 58f * density(), bottom, w * .5f + 58f * density(), bottom + 43f * density()), "武器", s.weapon, 0xffffd166);
            drawMini(c, new RectF(w - 102f * density(), bottom, w - 14f * density(), bottom + 43f * density()), "コイン", String.valueOf(s.coins), 0xffa5f3fc);

            if (s.status.length() > 0 && !s.paused) {
                float boxW = Math.min(w * .80f, 308f * density());
                RectF box = new RectF((w - boxW) / 2f, h * .70f, (w + boxW) / 2f, h * .70f + 38f * density());
                drawCard(c, box, 0xdd061628, s.danger ? 0xffff697c : 0xff63e9ff);
                drawText(c, s.status, box.centerX(), box.centerY() + 4f * density(), 11f, Color.WHITE, true, true);
            }
            if (s.bossHealth >= 0f) {
                RectF hp = new RectF(w * .20f, 91f * density(), w * .80f, 98f * density());
                paint.setColor(0xaa301b2a);
                c.drawRoundRect(hp, 4f * density(), 4f * density(), paint);
                paint.setColor(0xffff6278);
                c.drawRoundRect(new RectF(hp.left, hp.top, hp.left + hp.width() * s.bossHealth, hp.bottom), 4f * density(), 4f * density(), paint);
                drawText(c, "MUTANT", hp.centerX(), hp.top - 5f * density(), 9f, 0xffffd2da, true, true);
            }
            if (s.paused) drawPause(c);
        }

        private void drawPause(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            paint.setColor(0xb9000710);
            c.drawRect(0f, 0f, w, h, paint);
            drawText(c, "一時停止", w / 2f, h * .40f, 30f, Color.WHITE, true, true);
            drawText(c, "3Dワールドは停止しています", w / 2f, h * .45f, 12f, 0xffb9cbdb, false, true);
            drawButton(c, resumeRect(), "再開", true);
            drawButton(c, exitRect(), "ステージ選択へ", false);
        }

        private void drawResult(Canvas c, UiSnapshot s) {
            float w = getWidth();
            float h = getHeight();
            drawVeil(c, .78f);
            drawText(c, s.win ? "RUN COMPLETE" : "RUN FAILED", w * .10f, h * .20f, 12f, s.win ? 0xff75e9ff : 0xffff8d9b, true, false);
            drawText(c, s.win ? "ステージクリア" : "全滅", w * .10f, h * .29f, 34f, Color.WHITE, true, false);
            drawText(c, s.win ? "3D戦場を突破しました。" : "樽の選択と横移動を見直して再挑戦。", w * .10f, h * .36f, 13f, 0xffd7e5f0, false, false);
            drawCard(c, new RectF(w * .10f, h * .44f, w * .90f, h * .54f), 0xdd0b233b, 0xff416b8a);
            drawText(c, "獲得コイン  +" + s.resultCoins, w * .14f, h * .48f, 13f, 0xffffd166, true, false);
            drawText(c, "破壊した樽 " + s.barrels + "     撃破した敵 " + s.enemies, w * .14f, h * .515f, 11f, 0xffc8d9e6, false, false);
            drawButton(c, mainResultRect(), "もう一度", true);
            drawButton(c, stageResultRect(), "ステージ選択", false);
        }

        private void drawVeil(Canvas c, float alpha) {
            paint.setColor(Color.argb((int) (alpha * 255f), 3, 14, 28));
            c.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        }

        private void drawMini(Canvas c, RectF r, String label, String value, int accent) {
            drawCard(c, r, 0xc7081a2d, 0x665b7894);
            drawText(c, label, r.left + 8f * density(), r.top + 15f * density(), 8f, 0xffaabfd0, false, false);
            drawText(c, value, r.left + 8f * density(), r.top + 34f * density(), 12f, accent, true, false);
        }

        private void drawButton(Canvas c, RectF r, String label, boolean primary) {
            drawCard(c, r, primary ? 0xff4dbbf0 : 0xdf0b2742, primary ? 0xffc1f4ff : 0xff466d8d);
            drawText(c, label, r.centerX(), r.centerY() + 5f * density(), primary ? 14f : 12f, primary ? 0xff05233b : Color.WHITE, true, true);
        }

        private void drawCard(Canvas c, RectF r, int fill, int border) {
            paint.setColor(fill);
            c.drawRoundRect(r, 12f * density(), 12f * density(), paint);
            stroke.setColor(border);
            stroke.setStrokeWidth(Math.max(1f, density()));
            c.drawRoundRect(r, 12f * density(), 12f * density(), stroke);
        }

        private void drawText(Canvas c, String text, float x, float y, float sp, int color, boolean bold, boolean center) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(sp * density());
            paint.setTypeface(android.graphics.Typeface.create("sans", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            paint.setTextAlign(center ? Paint.Align.CENTER : Paint.Align.LEFT);
            c.drawText(text, x, y, paint);
        }

        private float density() { return getResources().getDisplayMetrics().density; }
        private float mapLane(float x) { return ((x / Math.max(1f, getWidth())) - .5f) * 6.1f; }
        private RectF startRect() { float w = getWidth(), h = getHeight(); return new RectF(w * .10f, h * .56f, w * .69f, h * .56f + 54f * density()); }
        private RectF backRect() { return new RectF(getWidth() * .05f, getHeight() * .055f, getWidth() * .18f, getHeight() * .13f); }
        private RectF pauseRect() { return new RectF(getWidth() - 62f * density(), 14f * density(), getWidth() - 15f * density(), 61f * density()); }
        private RectF resumeRect() { float w = getWidth(), h = getHeight(); return new RectF(w * .20f, h * .53f, w * .80f, h * .53f + 52f * density()); }
        private RectF exitRect() { float w = getWidth(), h = getHeight(); return new RectF(w * .20f, h * .53f + 66f * density(), w * .80f, h * .53f + 118f * density()); }
        private RectF mainResultRect() { float w = getWidth(), h = getHeight(); return new RectF(w * .10f, h * .62f, w * .58f, h * .62f + 54f * density()); }
        private RectF stageResultRect() { float w = getWidth(), h = getHeight(); return new RectF(w * .10f, h * .62f + 68f * density(), w * .58f, h * .62f + 122f * density()); }

        private RectF stageRect(int index) {
            float w = getWidth(), h = getHeight(), d = density();
            float left = w * .10f;
            float gap = 8f * d;
            float cardW = (w - left * 2f - gap * 2f) / 3f;
            float cardH = Math.min(77f * d, (h * .48f - gap * 3f) / 4f);
            int col = index % 3;
            int row = index / 3;
            float top = h * .285f + row * (cardH + gap);
            float x = left + col * (cardW + gap);
            return new RectF(x, top, x + cardW, top + cardH);
        }
    }

    private static String stageName(int index) {
        String[] names = {"橋の入口", "検問線", "崩落区画", "走者の橋", "湾岸道路", "封鎖トンネル", "装甲前線", "高架交差点", "夜間都市", "赤色街区", "最終封鎖線", "BREAKLINE"};
        return names[Math.max(0, Math.min(names.length - 1, index))];
    }

    private static String formatTime(float time) {
        return String.format(Locale.JAPAN, "%02d:%02d", (int) (time / 60f), (int) time % 60);
    }

    private static final class UiSnapshot {
        int mode = MENU;
        int stage = 1;
        int unlocked = 1;
        int coins;
        int squad = 1;
        int barrels;
        int enemies;
        int resultCoins;
        boolean paused;
        boolean win;
        float time;
        float progress;
        float bossHealth = -1f;
        String weapon = "PISTOL";
        String status = "";
        boolean danger;
    }

    private static final class BattleModel {
        private static final int BARREL = 0;
        private static final int ENEMY = 1;
        private static final int BOSS = 2;
        private static final int WALKER = 0;
        private static final int RUNNER = 1;
        private static final int HEAVY = 2;

        private final SharedPreferences prefs;
        private final Random random = new Random(901L);
        private final ArrayList<Entity> entities = new ArrayList<>();
        private final ArrayList<Shot> shots = new ArrayList<>();
        private final ArrayList<Burst> bursts = new ArrayList<>();
        private int mode = MENU;
        private int stage = 1;
        private int unlocked;
        private int coins;
        private int squad;
        private int weapon;
        private float damage;
        private float playerX;
        private float time;
        private float duration;
        private float spawnEnemy;
        private float spawnBarrel;
        private float fireCooldown;
        private int barrelGroup;
        private int barrels;
        private int enemies;
        private int score;
        private int resultCoins;
        private boolean paused;
        private boolean bossSpawned;
        private boolean bossDefeated;
        private boolean win;
        private float statusUntil;
        private String status = "";
        private boolean danger;

        BattleModel(SharedPreferences prefs) {
            this.prefs = prefs;
            unlocked = Math.max(1, Math.min(12, prefs.getInt("unlocked", 1)));
            coins = prefs.getInt("coins", 0);
        }

        void showMenu() {
            mode = MENU;
            paused = false;
            entities.clear();
            shots.clear();
            bursts.clear();
        }

        void showStages() {
            mode = STAGE_SELECT;
            paused = false;
            entities.clear();
            shots.clear();
            bursts.clear();
        }

        void startStage(int requestedStage) {
            stage = Math.max(1, Math.min(unlocked, requestedStage));
            mode = PLAYING;
            paused = false;
            entities.clear();
            shots.clear();
            bursts.clear();
            time = 0f;
            duration = 31f + stage * 2.2f;
            spawnEnemy = .8f;
            spawnBarrel = 2.1f;
            fireCooldown = .3f;
            playerX = 0f;
            squad = 1;
            damage = 1f;
            weapon = 0;
            barrelGroup = 0;
            barrels = 0;
            enemies = 0;
            score = 0;
            resultCoins = 0;
            bossSpawned = false;
            bossDefeated = false;
            win = false;
            showStatus("左右にドラッグして隊列を動かす", false, 3f);
        }

        void tick(float dt, float wantedX) {
            if (mode != PLAYING || paused) return;
            time += dt;
            playerX += (wantedX - playerX) * Math.min(1f, dt * 7.5f);
            fireCooldown -= dt;
            spawnEnemy -= dt;
            spawnBarrel -= dt;
            statusUntil -= dt;
            if (statusUntil <= 0f) { status = ""; danger = false; }

            if (!bossSpawned && time >= duration - 7f) spawnBoss();
            if (!bossSpawned && spawnBarrel <= 0f) {
                spawnBarrelChoices();
                spawnBarrel = Math.max(3.6f, 5.7f - stage * .09f);
            }
            if (!bossSpawned && spawnEnemy <= 0f) {
                spawnEnemies();
                spawnEnemy = Math.max(.82f, 2.25f - stage * .065f);
            }
            if (fireCooldown <= 0f) {
                fire();
                fireCooldown = Math.max(.12f, .31f - weapon * .035f);
            }

            for (Entity entity : entities) {
                if (!entity.alive) continue;
                entity.phase += dt;
                if (entity.kind == BOSS) updateBoss(entity, dt);
                else entity.z += entity.speed * dt;
                if (entity.kind != BOSS && entity.z > 2.7f) {
                    if (Math.abs(entity.x - playerX) < (entity.subtype == HEAVY ? 1.45f : 1.12f)) {
                        hitPlayer(entity.subtype == HEAVY ? 2 : 1, entity.x, entity.z);
                    }
                    entity.alive = false;
                }
            }

            Iterator<Shot> shotIterator = shots.iterator();
            while (shotIterator.hasNext()) {
                Shot shot = shotIterator.next();
                shot.progress += dt * 5.1f;
                if (shot.target == null || !shot.target.alive || shot.progress >= 1f) {
                    if (shot.target != null && shot.target.alive && shot.progress >= 1f) {
                        shot.target.hp -= shot.damage;
                        bursts.add(new Burst(shot.target.x, shot.target.z, shot.color, .34f));
                        if (shot.target.hp <= 0f) destroy(shot.target);
                    }
                    shotIterator.remove();
                }
            }

            Iterator<Burst> burstIterator = bursts.iterator();
            while (burstIterator.hasNext()) {
                Burst burst = burstIterator.next();
                burst.life -= dt;
                if (burst.life <= 0f) burstIterator.remove();
            }
            removeDeadFarEntities();
            if (bossDefeated) finish(true);
            else if (time > duration + 16f) finish(false);
        }

        private void spawnBarrelChoices() {
            int group = ++barrelGroup;
            int count = (group + stage) % 3 == 0 ? 3 : 2;
            float[] lanes = count == 3 ? new float[] {-2.55f, 0f, 2.55f} : new float[] {-1.8f, 1.8f};
            int baseReward = (group + stage) % 4;
            for (int i = 0; i < count; i++) {
                Entity e = new Entity(BARREL);
                e.x = lanes[i];
                e.z = -34f;
                e.speed = 2.25f + stage * .08f;
                e.hp = 3f + stage * .55f + (i == 2 ? 1f : 0f);
                e.maxHp = e.hp;
                e.reward = (baseReward + i) % 4;
                e.group = group;
                entities.add(e);
            }
            showStatus("樽を1つ選んで壊す", false, 2.0f);
        }

        private void spawnEnemies() {
            int count = stage >= 9 ? 3 : stage >= 3 ? 2 : 1;
            for (int i = 0; i < count; i++) {
                Entity e = new Entity(ENEMY);
                float roll = random.nextFloat();
                e.subtype = stage >= 7 && roll < .20f ? HEAVY : stage >= 4 && roll < .54f ? RUNNER : WALKER;
                e.x = new float[] {-2.55f, 0f, 2.55f}[random.nextInt(3)];
                e.z = -43f - i * 3.0f - random.nextFloat() * 4f;
                e.speed = 3.1f + stage * .10f;
                if (e.subtype == RUNNER) e.speed *= 1.45f;
                if (e.subtype == HEAVY) e.speed *= .62f;
                e.hp = 1.1f + stage * .25f;
                if (e.subtype == RUNNER) e.hp *= .7f;
                if (e.subtype == HEAVY) e.hp *= 3.2f;
                e.maxHp = e.hp;
                entities.add(e);
            }
        }

        private void spawnBoss() {
            bossSpawned = true;
            Entity boss = new Entity(BOSS);
            boss.x = 0f;
            boss.z = -19f;
            boss.hp = 30f + stage * 7f;
            boss.maxHp = boss.hp;
            boss.attackTimer = 2.3f;
            entities.add(boss);
            showStatus("ボス出現 — 予告レーンを避ける", true, 2.6f);
        }

        private void updateBoss(Entity boss, float dt) {
            boss.phase += dt;
            if (boss.attackState == 0) {
                boss.attackTimer -= dt;
                boss.x += (0f - boss.x) * Math.min(1f, dt * 1.5f);
                if (boss.attackTimer <= 0f) {
                    boss.attackState = 1;
                    boss.attackType = ((int) time + stage) % 2;
                    boss.attackTimer = 1.25f;
                    boss.attackX = Math.round(playerX / 2.55f) * 2.55f;
                    boss.safeX = new float[] {-2.55f, 0f, 2.55f}[random.nextInt(3)];
                    showStatus(boss.attackType == 0 ? "赤いレーンから離れる" : "青いレーンへ移動", true, 1.3f);
                }
            } else if (boss.attackState == 1) {
                boss.attackTimer -= dt;
                if (boss.attackTimer <= 0f) {
                    boss.attackState = 2;
                    boss.attackTimer = boss.attackType == 0 ? .95f : .68f;
                }
            } else {
                boss.attackTimer -= dt;
                if (!boss.struck && boss.attackTimer < (boss.attackType == 0 ? .53f : .40f)) {
                    boss.struck = true;
                    boolean hit = boss.attackType == 0 ? Math.abs(playerX - boss.attackX) < 1.0f : Math.abs(playerX - boss.safeX) > 1.0f;
                    if (hit) hitPlayer(boss.attackType == 0 ? 2 : 1, playerX, 2.3f);
                    else showStatus("DODGE", false, .65f);
                }
                if (boss.attackTimer <= 0f) {
                    boss.attackState = 0;
                    boss.attackTimer = 1.8f + random.nextFloat() * .8f;
                    boss.struck = false;
                }
            }
        }

        private void fire() {
            Entity target = targetForPlayer();
            if (target == null) return;
            float[] weaponDamage = {1f, .75f, 1.2f, 1.7f};
            int[] colors = {0xffffe48a, 0xffffbd48, 0xfffb7185, 0xffa5f3fc};
            int shotsPerVolley = Math.min(5, squad);
            for (int i = 0; i < shotsPerVolley; i++) shots.add(new Shot(target, damage * weaponDamage[weapon], colors[weapon], i));
        }

        private Entity targetForPlayer() {
            Entity best = null;
            float bestScore = -999f;
            for (Entity e : entities) {
                if (!e.alive) continue;
                float score = e.z - Math.abs(e.x - playerX) * 1.35f;
                if (e.kind == BOSS) score += 4f;
                if (score > bestScore) { best = e; bestScore = score; }
            }
            return best;
        }

        private void destroy(Entity target) {
            target.alive = false;
            if (target.kind == BARREL) {
                for (Entity e : entities) if (e.alive && e.kind == BARREL && e.group == target.group) e.alive = false;
                barrels++;
                score += 5;
                switch (target.reward) {
                    case 0: squad = Math.min(9, squad + 1); showStatus("+1 仲間", false, 1f); break;
                    case 1: damage += .28f; showStatus("DAMAGE UP", false, 1f); break;
                    case 2: weapon = Math.min(3, weapon + 1); showStatus("WEAPON UP", false, 1f); break;
                    default: damage += .12f; squad = Math.min(9, squad + 1); showStatus("SQUAD BOOST", false, 1f); break;
                }
                bursts.add(new Burst(target.x, target.z, 0xffffc263, .7f));
            } else if (target.kind == ENEMY) {
                enemies++;
                score += target.subtype == HEAVY ? 24 : target.subtype == RUNNER ? 14 : 10;
                bursts.add(new Burst(target.x, target.z, target.subtype == HEAVY ? 0xffffa2b3 : 0xffff687d, .48f));
            } else {
                bossDefeated = true;
                bursts.add(new Burst(target.x, target.z, 0xffffd76a, 1.1f));
                showStatus("BOSS BREAK!", false, 1.2f);
            }
        }

        private void hitPlayer(int amount, float x, float z) {
            squad = Math.max(0, squad - amount);
            bursts.add(new Burst(x, z, 0xffff506a, .55f));
            showStatus("-" + amount + " 仲間", true, .8f);
            if (squad <= 0) finish(false);
        }

        private void removeDeadFarEntities() {
            Iterator<Entity> iterator = entities.iterator();
            while (iterator.hasNext()) {
                Entity e = iterator.next();
                if (!e.alive && e.kind != BOSS) iterator.remove();
            }
        }

        private void finish(boolean stageWin) {
            if (mode != PLAYING) return;
            mode = RESULT;
            win = stageWin;
            resultCoins = stageWin ? 7 + stage * 4 + score / 12 : Math.max(1, score / 24);
            coins += resultCoins;
            if (stageWin) unlocked = Math.max(unlocked, Math.min(12, stage + 1));
            prefs.edit().putInt("coins", coins).putInt("unlocked", unlocked).apply();
        }

        private void showStatus(String text, boolean important, float seconds) {
            status = text;
            danger = important;
            statusUntil = seconds;
        }

        UiSnapshot snapshot() {
            UiSnapshot s = new UiSnapshot();
            s.mode = mode;
            s.stage = stage;
            s.unlocked = unlocked;
            s.coins = coins;
            s.squad = squad;
            s.weapon = new String[] {"PISTOL", "SMG", "RIFLE", "PIERCE"}[weapon];
            s.time = time;
            s.progress = duration <= 0f ? 0f : Math.min(1f, time / duration);
            s.barrels = barrels;
            s.enemies = enemies;
            s.resultCoins = resultCoins;
            s.paused = paused;
            s.win = win;
            s.status = status;
            s.danger = danger;
            for (Entity e : entities) if (e.alive && e.kind == BOSS) { s.bossHealth = Math.max(0f, e.hp / e.maxHp); break; }
            return s;
        }

        static final class Entity {
            final int kind;
            int subtype;
            int reward;
            int group = -1;
            float x;
            float z;
            float speed;
            float hp;
            float maxHp;
            float phase;
            boolean alive = true;
            int attackState;
            int attackType;
            float attackTimer;
            float attackX;
            float safeX;
            boolean struck;
            Entity(int kind) { this.kind = kind; }
        }

        static final class Shot {
            final Entity target;
            final float damage;
            final int color;
            final int index;
            float progress;
            Shot(Entity target, float damage, int color, int index) {
                this.target = target;
                this.damage = damage;
                this.color = color;
                this.index = index;
            }
        }

        static final class Burst {
            final float x;
            final float z;
            final int color;
            final float maxLife;
            float life;
            Burst(float x, float z, int color, float life) {
                this.x = x;
                this.z = z;
                this.color = color;
                this.life = life;
                this.maxLife = life;
            }
        }
    }

    private static final class WorldRenderer implements GLSurfaceView.Renderer {
        private final Breakline3DView host;
        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] vp = new float[16];
        private final float[] model = new float[16];
        private final float[] mvp = new float[16];
        private Mesh box;
        private Mesh cylinder;
        private Mesh sphere;
        private int program;
        private int posLoc;
        private int normalLoc;
        private int mvpLoc;
        private int modelLoc;
        private int colorLoc;
        private int lightLoc;
        private long lastFrame;
        private int width;
        private int height;

        WorldRenderer(Breakline3DView host) { this.host = host; }

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 unused, javax.microedition.khronos.egl.EGLConfig config) {
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            posLoc = GLES20.glGetAttribLocation(program, "aPosition");
            normalLoc = GLES20.glGetAttribLocation(program, "aNormal");
            mvpLoc = GLES20.glGetUniformLocation(program, "uMvp");
            modelLoc = GLES20.glGetUniformLocation(program, "uModel");
            colorLoc = GLES20.glGetUniformLocation(program, "uColor");
            lightLoc = GLES20.glGetUniformLocation(program, "uLight");
            box = Mesh.box();
            cylinder = Mesh.cylinder(12);
            sphere = Mesh.sphere(10, 8);
            lastFrame = SystemClock.uptimeMillis();
        }

        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 unused, int w, int h) {
            width = w;
            height = h;
            GLES20.glViewport(0, 0, w, h);
            Matrix.perspectiveM(projection, 0, 53f, w / (float) Math.max(1, h), .1f, 100f);
        }

        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 unused) {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(.05f, Math.max(0f, (now - lastFrame) / 1000f));
            lastFrame = now;
            host.tickFromRenderer(dt);
            BattleModel state = host.model();
            drawScene(state);
        }

        private void drawScene(BattleModel state) {
            int environment = Math.min(3, Math.max(0, (state.stage - 1) / 3));
            int[] skies = {0xff6294bd, 0xffb47f61, 0xff263e62, 0xff261935};
            int sky = skies[environment];
            GLES20.glClearColor(Color.red(sky) / 255f, Color.green(sky) / 255f, Color.blue(sky) / 255f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (width == 0 || height == 0) return;
            float eyeX = state.playerX * .17f;
            Matrix.setLookAtM(view, 0, eyeX, 6.1f, 11.4f, state.playerX * .06f, .35f, -17f, 0f, 1f, 0f);
            Matrix.multiplyMM(vp, 0, projection, 0, view, 0);
            GLES20.glUseProgram(program);
            GLES20.glUniform3f(lightLoc, -.35f, .88f, .55f);

            drawEnvironment(environment, state.time);
            if (state.mode == PLAYING || state.mode == RESULT) {
                drawTelegraphs(state);
                for (BattleModel.Entity e : state.entities) if (e.alive) drawEntity(e);
                drawPlayer(state);
                for (BattleModel.Shot shot : state.shots) drawShot(state, shot);
                for (BattleModel.Burst burst : state.bursts) drawBurst(burst);
            } else {
                drawAttractScene(environment);
            }
        }

        private void drawEnvironment(int environment, float time) {
            int water = environment >= 2 ? 0x88366192 : 0x8849a7ca;
            drawBox(-14f, -.6f, -25f, 20f, .08f, 68f, water);
            drawBox(14f, -.6f, -25f, 20f, .08f, 68f, water);
            for (int i = 0; i < 18; i++) {
                float z = -52f + i * 4.2f + (time * 1.3f % 4.2f);
                drawBox(-12f, -.49f, z, 5f, .012f, .07f, 0x88d7f6ff);
                drawBox(12f, -.49f, z, 5f, .012f, .07f, 0x88d7f6ff);
            }
            drawBox(0f, -.22f, -25f, 8.2f, .35f, 66f, environment == 3 ? 0xff323844 : 0xff5f676e);
            for (int i = 0; i < 19; i++) {
                float z = -53f + i * 3.4f;
                drawBox(0f, -.01f, z, .18f, .025f, 1.0f, 0xffe7eceb);
            }
            for (float x : new float[] {-4.35f, 4.35f}) {
                drawBox(x, .34f, -25f, .16f, .16f, 66f, 0xffb9c5c8);
                for (int i = 0; i < 14; i++) {
                    float z = -52f + i * 4.8f;
                    drawBox(x, .60f, z, .16f, .60f, .16f, 0xffcbd5d8);
                    drawBox(x, 1.1f, z, .35f, .08f, .16f, 0xffffcf59);
                }
            }
            for (int i = 0; i < 14; i++) {
                float z = -54f + i * 5.8f;
                float leftX = -8.2f - (i % 3) * 1.2f;
                float rightX = 8.2f + (i % 4) * 1.1f;
                float h = 4f + (i % 5) * 1.7f;
                int city = environment == 3 ? 0xff1f1b37 : environment >= 2 ? 0xff263a55 : 0xff54768c;
                drawBox(leftX, h / 2f - .45f, z, 2.3f, h, 3.5f, city);
                drawBox(rightX, (h * .78f) / 2f - .45f, z + 1.1f, 2.0f, h * .78f, 3.2f, city);
                if (environment >= 2) {
                    drawBox(leftX, h * .42f, z - 1.78f, 1.5f, .06f, .05f, 0xffffd166);
                    drawBox(rightX, h * .30f, z - .55f, 1.3f, .06f, .05f, 0xffffd166);
                }
            }
            for (int i = 0; i < 7; i++) {
                float z = -44f + i * 8f;
                drawBox(-5.6f, 1.45f, z, .12f, 2.7f, .12f, 0xff314b60);
                drawBox(-5.15f, 2.65f, z, .9f, .08f, .08f, 0xff314b60);
                drawSphere(-4.7f, 2.58f, z, .16f, 0xffffd36a);
                drawBox(5.6f, 1.45f, z + 3f, .12f, 2.7f, .12f, 0xff314b60);
                drawBox(5.15f, 2.65f, z + 3f, .9f, .08f, .08f, 0xff314b60);
                drawSphere(4.7f, 2.58f, z + 3f, .16f, 0xffffd36a);
            }
        }

        private void drawAttractScene(int environment) {
            drawHumanoid(-1.1f, 0f, -4.5f, 1.35f, 0xff24669b, 0xffffbb66, true);
            drawHumanoid(1.1f, 0f, -5.2f, 1.2f, 0xff24669b, 0xffffbb66, true);
            drawBarrel(0f, -10f, 1.2f, 0xffffd166, 8);
            drawEnemy(0f, -16f, BattleModel.HEAVY, 1.25f);
        }

        private void drawTelegraphs(BattleModel state) {
            for (BattleModel.Entity e : state.entities) {
                if (!e.alive || e.kind != BattleModel.BOSS || e.attackState == 0) continue;
                if (e.attackType == 0) {
                    drawBox(e.attackX, -.03f, -7.5f, 1.85f, .018f, 24f, 0x66ff4056);
                } else {
                    drawBox(0f, -.025f, -7.5f, 7.9f, .016f, 24f, 0x44ff4056);
                    drawBox(e.safeX, -.005f, -7.5f, 1.65f, .020f, 24f, 0x8842e9d5);
                }
            }
        }

        private void drawEntity(BattleModel.Entity e) {
            if (e.kind == BattleModel.BARREL) {
                int[] colors = {0xff75f0b6, 0xffffd166, 0xfffb7185, 0xffa5f3fc};
                drawBarrel(e.x, e.z, 1f, colors[e.reward], Math.max(1, (int) Math.ceil(e.hp)));
            } else if (e.kind == BattleModel.ENEMY) {
                drawEnemy(e.x, e.z, e.subtype, 1f + (float) Math.sin(e.phase * 5f) * .025f);
            } else {
                drawBoss(e);
            }
        }

        private void drawBarrel(float x, float z, float scale, int rewardColor, int hp) {
            drawCylinder(x, .55f * scale, z, .78f * scale, 1.1f * scale, 0xffa46634);
            drawCylinder(x, .19f * scale, z, .84f * scale, .12f * scale, 0xff3f271c);
            drawCylinder(x, .91f * scale, z, .84f * scale, .12f * scale, 0xff3f271c);
            drawCylinder(x, 1.19f * scale, z, .20f * scale, .10f * scale, rewardColor);
            drawRewardShape(x, 1.74f * scale, z + .05f, rewardColor);
            drawNumber(hp, x, 1.18f * scale, z + .79f * scale, .22f * scale, Color.WHITE);
        }

        private void drawRewardShape(float x, float y, float z, int color) {
            drawBox(x, y, z, .80f, .14f, .10f, color);
            drawBox(x + .30f, y + .09f, z, .22f, .22f, .10f, color);
            drawSphere(x - .28f, y - .11f, z, .16f, color);
            drawSphere(x + .28f, y - .11f, z, .16f, color);
        }

        private void drawEnemy(float x, float z, int type, float scale) {
            float body = type == BattleModel.HEAVY ? 1.46f : type == BattleModel.RUNNER ? .78f : 1f;
            int torso = type == BattleModel.HEAVY ? 0xff7a3e4b : type == BattleModel.RUNNER ? 0xff9a7145 : 0xff63727a;
            int head = type == BattleModel.HEAVY ? 0xffa96d67 : 0xffc29c73;
            drawHumanoid(x, 0f, z, body * scale, torso, head, false);
            if (type == BattleModel.HEAVY) {
                drawBox(x, 1.30f * body * scale, z + .05f, 1.72f * body * scale, .26f * body * scale, .34f * body * scale, 0xff432f3b);
            } else if (type == BattleModel.RUNNER) {
                drawBox(x, .65f * body * scale, z - .28f, 1.65f * body * scale, .12f * body * scale, .52f * body * scale, 0xffffb84d);
            }
        }

        private void drawBoss(BattleModel.Entity e) {
            float sway = (float) Math.sin(e.phase * 2.5f) * .18f;
            drawBox(e.x, 1.25f, e.z, 3.7f, 2.5f, 1.8f, 0xffa96f69);
            drawSphere(e.x, 3.25f + sway, e.z + .15f, 1.28f, 0xffc88a7e);
            drawBox(e.x - 2.35f, 1.45f, e.z, .75f, 1.0f, 1.0f, 0xff8b5858);
            drawBox(e.x + 2.35f, 1.45f, e.z, .75f, 1.0f, 1.0f, 0xff8b5858);
            drawBox(e.x - 1.15f, .20f, e.z, .80f, .45f, .9f, 0xff784552);
            drawBox(e.x + 1.15f, .20f, e.z, .80f, .45f, .9f, 0xff784552);
            drawSphere(e.x - .45f, 3.45f + sway, e.z - 1.08f, .15f, 0xffffe5a5);
            drawSphere(e.x + .45f, 3.45f + sway, e.z - 1.08f, .15f, 0xffffe5a5);
        }

        private void drawPlayer(BattleModel state) {
            int count = Math.min(5, state.squad);
            for (int i = 0; i < count; i++) {
                float offset = (i - (count - 1) / 2f) * .82f;
                drawHumanoid(state.playerX + offset, 0f, 3.8f - Math.abs(offset) * .18f, .82f, 0xff1f679f, 0xffffbb66, true);
            }
        }

        private void drawHumanoid(float x, float y, float z, float scale, int suit, int skin, boolean armed) {
            drawBox(x, y + .82f * scale, z, .72f * scale, 1.28f * scale, .42f * scale, suit);
            drawSphere(x, y + 1.76f * scale, z, .37f * scale, skin);
            drawBox(x, y + 2.02f * scale, z, .76f * scale, .16f * scale, .44f * scale, 0xff153245);
            drawBox(x - .25f * scale, y + .08f * scale, z, .22f * scale, .55f * scale, .25f * scale, 0xff1a3349);
            drawBox(x + .25f * scale, y + .08f * scale, z, .22f * scale, .55f * scale, .25f * scale, 0xff1a3349);
            drawBox(x - .57f * scale, y + 1.02f * scale, z - .05f, .18f * scale, .70f * scale, .20f * scale, suit);
            drawBox(x + .57f * scale, y + 1.02f * scale, z - .05f, .18f * scale, .70f * scale, .20f * scale, suit);
            if (armed) {
                drawBox(x + .62f * scale, y + 1.23f * scale, z - .33f * scale, .15f * scale, .15f * scale, .85f * scale, 0xff2b3540);
                drawSphere(x + .62f * scale, y + 1.23f * scale, z - .79f * scale, .08f * scale, 0xffffe28a);
            }
        }

        private void drawShot(BattleModel state, BattleModel.Shot shot) {
            if (shot.target == null || !shot.target.alive) return;
            float p = Math.min(1f, shot.progress);
            float startX = state.playerX + (shot.index - 2) * .14f;
            float x = lerp(startX, shot.target.x, p);
            float z = lerp(3.1f, shot.target.z, p);
            float y = lerp(1.25f, 1.15f, p);
            drawBox(x, y, z, .07f, .07f, .55f, shot.color);
            drawSphere(x, y, z - .30f, .10f, shot.color);
        }

        private void drawBurst(BattleModel.Burst burst) {
            float progress = 1f - burst.life / Math.max(.01f, burst.maxLife);
            float radius = .2f + progress * 1.8f;
            for (int i = 0; i < 9; i++) {
                double a = i * Math.PI * 2d / 9d;
                float x = burst.x + (float) Math.cos(a) * radius;
                float z = burst.z + (float) Math.sin(a) * radius;
                drawBox(x, .45f + progress * .7f, z, .11f, .11f, .11f, burst.color);
            }
        }

        private void drawNumber(int value, float x, float y, float z, float scale, int color) {
            String text = String.valueOf(Math.max(0, value));
            float total = text.length() * scale * 1.45f;
            for (int i = 0; i < text.length(); i++) {
                drawDigit(text.charAt(i) - '0', x - total / 2f + scale * .72f + i * scale * 1.45f, y, z, scale, color);
            }
        }

        private void drawDigit(int digit, float x, float y, float z, float s, int color) {
            boolean[][] segments = {
                    {true,true,true,true,true,true,false}, {false,true,true,false,false,false,false}, {true,true,false,true,true,false,true},
                    {true,true,true,true,false,false,true}, {false,true,true,false,false,true,true}, {true,false,true,true,false,true,true},
                    {true,false,true,true,true,true,true}, {true,true,true,false,false,false,false}, {true,true,true,true,true,true,true}, {true,true,true,true,false,true,true}
            };
            boolean[] on = segments[Math.max(0, Math.min(9, digit))];
            float[][] pos = {{0f,s,0f},{s*.55f,s*.5f,0f},{s*.55f,-s*.5f,0f},{0f,-s,0f},{-s*.55f,-s*.5f,0f},{-s*.55f,s*.5f,0f},{0f,0f,0f}};
            float[][] size = {{s*.95f,s*.14f},{s*.14f,s*.85f},{s*.14f,s*.85f},{s*.95f,s*.14f},{s*.14f,s*.85f},{s*.14f,s*.85f},{s*.95f,s*.14f}};
            for (int i = 0; i < 7; i++) if (on[i]) drawBox(x + pos[i][0], y + pos[i][1], z, size[i][0], size[i][1], .035f, color);
        }

        private void drawBox(float x, float y, float z, float sx, float sy, float sz, int color) {
            drawMesh(box, x, y, z, sx, sy, sz, 0f, color);
        }

        private void drawCylinder(float x, float y, float z, float radius, float h, int color) {
            drawMesh(cylinder, x, y, z, radius * 2f, h, radius * 2f, 0f, color);
        }

        private void drawSphere(float x, float y, float z, float radius, int color) {
            drawMesh(sphere, x, y, z, radius * 2f, radius * 2f, radius * 2f, 0f, color);
        }

        private void drawMesh(Mesh mesh, float x, float y, float z, float sx, float sy, float sz, float rotY, int color) {
            Matrix.setIdentityM(model, 0);
            Matrix.translateM(model, 0, x, y, z);
            if (rotY != 0f) Matrix.rotateM(model, 0, rotY, 0f, 1f, 0f);
            Matrix.scaleM(model, 0, sx, sy, sz);
            Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
            GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0);
            GLES20.glUniformMatrix4fv(modelLoc, 1, false, model, 0);
            GLES20.glUniform4f(colorLoc, Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f, Color.alpha(color) / 255f);
            mesh.draw(posLoc, normalLoc);
        }

        private static int createProgram(String vertex, String fragment) {
            int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex);
            int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment);
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, vs);
            GLES20.glAttachShader(p, fs);
            GLES20.glLinkProgram(p);
            int[] status = new int[1];
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(p));
            return p;
        }

        private static int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader));
            return shader;
        }

        private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

        private static final String VERTEX_SHADER =
                "attribute vec3 aPosition;\n" +
                "attribute vec3 aNormal;\n" +
                "uniform mat4 uMvp;\n" +
                "uniform mat4 uModel;\n" +
                "uniform vec4 uColor;\n" +
                "uniform vec3 uLight;\n" +
                "varying vec4 vColor;\n" +
                "void main() {\n" +
                "  vec3 n = normalize((uModel * vec4(aNormal, 0.0)).xyz);\n" +
                "  float light = max(dot(n, normalize(uLight)), 0.0);\n" +
                "  vColor = vec4(uColor.rgb * (0.30 + light * 0.70), uColor.a);\n" +
                "  gl_Position = uMvp * vec4(aPosition, 1.0);\n" +
                "}";

        private static final String FRAGMENT_SHADER =
                "precision mediump float;\n" +
                "varying vec4 vColor;\n" +
                "void main() { gl_FragColor = vColor; }";
    }

    private static final class Mesh {
        private final FloatBuffer data;
        private final int count;

        private Mesh(float[] vertices) {
            data = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            data.put(vertices).position(0);
            count = vertices.length / 6;
        }

        void draw(int posLoc, int normalLoc) {
            data.position(0);
            GLES20.glEnableVertexAttribArray(posLoc);
            GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 24, data);
            data.position(3);
            GLES20.glEnableVertexAttribArray(normalLoc);
            GLES20.glVertexAttribPointer(normalLoc, 3, GLES20.GL_FLOAT, false, 24, data);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
            GLES20.glDisableVertexAttribArray(posLoc);
            GLES20.glDisableVertexAttribArray(normalLoc);
        }

        static Mesh box() {
            float[] v = {
                    -0.5f,-0.5f, 0.5f, 0,0,1, 0.5f,-0.5f, 0.5f, 0,0,1, 0.5f,0.5f,0.5f,0,0,1,
                    -0.5f,-0.5f, 0.5f, 0,0,1, 0.5f,0.5f,0.5f,0,0,1, -0.5f,0.5f,0.5f,0,0,1,
                    0.5f,-0.5f,-0.5f, 0,0,-1, -0.5f,-0.5f,-0.5f,0,0,-1, -0.5f,0.5f,-0.5f,0,0,-1,
                    0.5f,-0.5f,-0.5f,0,0,-1, -0.5f,0.5f,-0.5f,0,0,-1, 0.5f,0.5f,-0.5f,0,0,-1,
                    -0.5f,-0.5f,-0.5f,-1,0,0, -0.5f,-0.5f,0.5f,-1,0,0, -0.5f,0.5f,0.5f,-1,0,0,
                    -0.5f,-0.5f,-0.5f,-1,0,0, -0.5f,0.5f,0.5f,-1,0,0, -0.5f,0.5f,-0.5f,-1,0,0,
                    0.5f,-0.5f,0.5f,1,0,0, 0.5f,-0.5f,-0.5f,1,0,0, 0.5f,0.5f,-0.5f,1,0,0,
                    0.5f,-0.5f,0.5f,1,0,0, 0.5f,0.5f,-0.5f,1,0,0, 0.5f,0.5f,0.5f,1,0,0,
                    -0.5f,0.5f,0.5f,0,1,0, 0.5f,0.5f,0.5f,0,1,0, 0.5f,0.5f,-0.5f,0,1,0,
                    -0.5f,0.5f,0.5f,0,1,0, 0.5f,0.5f,-0.5f,0,1,0, -0.5f,0.5f,-0.5f,0,1,0,
                    -0.5f,-0.5f,-0.5f,0,-1,0, 0.5f,-0.5f,-0.5f,0,-1,0, 0.5f,-0.5f,0.5f,0,-1,0,
                    -0.5f,-0.5f,-0.5f,0,-1,0, 0.5f,-0.5f,0.5f,0,-1,0, -0.5f,-0.5f,0.5f,0,-1,0
            };
            return new Mesh(v);
        }

        static Mesh cylinder(int segments) {
            ArrayList<Float> values = new ArrayList<>();
            for (int i = 0; i < segments; i++) {
                float a0 = (float) (i * Math.PI * 2d / segments);
                float a1 = (float) ((i + 1) * Math.PI * 2d / segments);
                float x0 = (float) Math.cos(a0) * .5f, z0 = (float) Math.sin(a0) * .5f;
                float x1 = (float) Math.cos(a1) * .5f, z1 = (float) Math.sin(a1) * .5f;
                add(values, x0,-.5f,z0,x0,0,z0); add(values, x1,-.5f,z1,x1,0,z1); add(values, x1,.5f,z1,x1,0,z1);
                add(values, x0,-.5f,z0,x0,0,z0); add(values, x1,.5f,z1,x1,0,z1); add(values, x0,.5f,z0,x0,0,z0);
                add(values, 0,.5f,0,0,1,0); add(values, x1,.5f,z1,0,1,0); add(values, x0,.5f,z0,0,1,0);
                add(values, 0,-.5f,0,0,-1,0); add(values, x0,-.5f,z0,0,-1,0); add(values, x1,-.5f,z1,0,-1,0);
            }
            return new Mesh(toArray(values));
        }

        static Mesh sphere(int rings, int sectors) {
            ArrayList<Float> values = new ArrayList<>();
            for (int r = 0; r < rings; r++) {
                float v0 = r / (float) rings;
                float v1 = (r + 1) / (float) rings;
                for (int s = 0; s < sectors; s++) {
                    float u0 = s / (float) sectors;
                    float u1 = (s + 1) / (float) sectors;
                    float[] p0 = spherePoint(u0, v0), p1 = spherePoint(u1, v0), p2 = spherePoint(u1, v1), p3 = spherePoint(u0, v1);
                    add(values,p0,p0); add(values,p1,p1); add(values,p2,p2);
                    add(values,p0,p0); add(values,p2,p2); add(values,p3,p3);
                }
            }
            return new Mesh(toArray(values));
        }

        private static float[] spherePoint(float u, float v) {
            float phi = (float) (u * Math.PI * 2d);
            float theta = (float) (v * Math.PI);
            float x = (float) (Math.sin(theta) * Math.cos(phi)) * .5f;
            float y = (float) Math.cos(theta) * .5f;
            float z = (float) (Math.sin(theta) * Math.sin(phi)) * .5f;
            return new float[] {x, y, z};
        }

        private static void add(List<Float> out, float x, float y, float z, float nx, float ny, float nz) {
            out.add(x); out.add(y); out.add(z); out.add(nx); out.add(ny); out.add(nz);
        }

        private static void add(List<Float> out, float[] p, float[] n) { add(out, p[0],p[1],p[2],n[0],n[1],n[2]); }

        private static float[] toArray(List<Float> in) {
            float[] out = new float[in.size()];
            for (int i = 0; i < out.length; i++) out[i] = in.get(i);
            return out;
        }
    }
}
