package com.example.jetlab;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends Activity {

    private Game game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new Game(this);
        setContentView(game);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        game.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        game.stop();
    }

    static class Coin { float x, y; boolean taken; }

    /** a background scientist who watches the player and points */
    static class Npc { float x, phase, pointT, nextPoint, walk; int coat, hair, mode, face; }

    static class Crate { float x, y; int type; }

    static class Zapper {
        float x, y, len, angle, spin;
    }

    static class Missile {
        float x, y, warn;
        boolean flying;
    }

    /** Short synthesized sound effects, no audio files. */
    static class Sfx {
        static final int RATE = 22050;
        final AudioTrack[] tr = new AudioTrack[9];
        // stop/reload/play can block for a frame, so every call runs off the game thread
        final ExecutorService io = Executors.newSingleThreadExecutor();

        Sfx() {
            try {
                tr[Game.S_JUMP] = mk(tone(200, 560, 120, 0.5f, 0f, true));
                tr[Game.S_COIN] = mk(tone(900, 1500, 70, 0.4f, 0f, false));
                tr[Game.S_HIT] = mk(tone(160, 30, 380, 0.9f, 0.6f, false));
                tr[Game.S_STOMP] = mk(tone(140, 40, 170, 0.9f, 0.35f, false));
                tr[Game.S_FLIP] = mk(tone(700, 250, 130, 0.5f, 0f, true));
                tr[Game.S_WARN] = mk(tone(820, 820, 90, 0.4f, 0f, true));
                tr[Game.S_DONE] = mk(tone(500, 1400, 260, 0.6f, 0f, true));
                tr[Game.S_TELE] = mk(tone(1800, 250, 150, 0.5f, 0.15f, true));
                tr[Game.S_BUILD] = mk(build());
            } catch (Throwable t) { }
        }

        static short[] tone(float f0, float f1, int ms, float vol, float noise, boolean square) {
            int n = RATE * ms / 1000;
            short[] d = new short[n];
            Random r = new Random(7);
            double ph = 0;
            for (int i = 0; i < n; i++) {
                float t = i / (float) n;
                ph += 2 * Math.PI * (f0 + (f1 - f0) * t) / RATE;
                double v = square ? (Math.sin(ph) >= 0 ? 1 : -1) : Math.sin(ph);
                v = v * (1 - noise) + (r.nextFloat() * 2 - 1) * noise;
                float env = (t < 0.05f ? t / 0.05f : 1f) * (1 - t);
                d[i] = (short) (v * env * vol * 32767 * 0.5);
            }
            return d;
        }

        /** a rising whirr with a wobble, about as long as the build animation */
        static short[] build() {
            int n = RATE * 1400 / 1000;
            short[] d = new short[n];
            double ph = 0;
            for (int i = 0; i < n; i++) {
                float t = i / (float) n;
                double f = 140 + 800 * t * t + 40 * Math.sin(t * 70);
                ph += 2 * Math.PI * f / RATE;
                double v = Math.sin(ph) >= 0 ? 1 : -1;
                float env = (t < 0.05f ? t / 0.05f : 1f) * (t > 0.9f ? (1 - t) * 10 : 1f);
                d[i] = (short) (v * env * 32767 * 0.18);
            }
            return d;
        }

        static AudioTrack mk(short[] d) {
            AudioTrack a = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, d.length * 2, AudioTrack.MODE_STATIC);
            a.write(d, 0, d.length);
            return a;
        }

        void play(final int id) {
            try {
                io.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AudioTrack a = tr[id];
                            if (a == null) return;
                            a.stop();
                            a.reloadStaticData();
                            a.play();
                        } catch (Throwable t) { }
                    }
                });
            } catch (Throwable t) { }
        }

        void release() {
            try {
                io.execute(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < tr.length; i++) {
                            try { if (tr[i] != null) tr[i].release(); } catch (Throwable t) { }
                            tr[i] = null;
                        }
                    }
                });
                io.shutdown();
            } catch (Throwable t) { }
        }
    }

    /** One game view: physics, drawing and input in one place. */
    static class Game extends View implements Choreographer.FrameCallback {

        static final int START = 0, PLAY = 1, DEAD = 2, BUILD = 3, INTRO = 4, FLIP = 5, LAUNCH = 6;
        static final int TOWER_TOP = 58;        // y of the slingshot platform
        static final float MAXPULL = 34f;       // longest pull back, in pixels
        static final int STOMPER = 1, INVERTER = 2, QUANTUM = 3;
        static final float BUILD_TIME = 1.5f;
        static final int S_JUMP = 0, S_COIN = 1, S_HIT = 2, S_STOMP = 3, S_BUILD = 4, S_FLIP = 5, S_WARN = 6, S_DONE = 7, S_TELE = 8;
        static final String[] VNAME = {"", "SPRING STOMPER", "GRAVITY INVERTER", "QUANTUM ENTANGLER"};
        static final String[] VHINT = {"", "BOUNCES BY ITSELF. TAP IN THE AIR TO STOMP.", "TAP TO FLIP GRAVITY.", "TAP TO TELEPORT TO YOUR TWIN ON THE OTHER SIDE."};

        static final float GRAVITY = 22f;       // m/s^2, game gravity (Earth is 9.8)
        static final float THRUST = 38f;        // m/s^2 from the pack, net 16 up
        static final float VFALL = 14f;         // m/s, falling top speed
        static final float JUMP = 8.5f;          // m/s, jump off the floor
        static final float VMAX = 7f;           // m/s, "terminal" speed up or down

        static final String[] SYM = {"H", "He", "Li", "C", "N", "O", "Ne", "Na", "Si", "Fe", "Cu", "Ag", "Au", "U"};
        static final String[] FACTS = {
            "Newton's 2nd law: a = F / m. More thrust, more acceleration.",
            "Newton's 3rd law: the exhaust goes down, so you go up.",
            "Gravity pulls everything at 9.8 m/s², whatever its mass.",
            "Light takes about 1.3 seconds to reach us from the Moon.",
            "An atom is almost entirely empty space.",
            "Sound moves at about 343 m/s in air. You are slower, for now.",
            "Lightning heats the air around it hotter than the surface of the Sun.",
            "Drag grows with speed until it balances gravity. That is terminal velocity.",
            "Energy is never destroyed, only changed from one form to another.",
            "Electrons repel each other, which is why static makes hair stand up."
        };

        final Random rnd = new Random();
        final Paint p = new Paint();
        final Paint blit = new Paint();
        final Path path = new Path();
        final RectF dst = new RectF();
        Bitmap buf;
        Canvas bc;
        final SharedPreferences prefs;

        final ArrayList<Coin> coins = new ArrayList<>();
        final ArrayList<Zapper> zappers = new ArrayList<>();
        final ArrayList<Missile> missiles = new ArrayList<>();
        final ArrayList<Crate> crates = new ArrayList<>();
        final ArrayList<Npc> npcs = new ArrayList<>();
        static final int[] COATS = {Color.rgb(235, 240, 248), Color.rgb(190, 215, 240), Color.rgb(205, 205, 215)};
        static final int[] HAIRS = {Color.rgb(110, 70, 40), Color.rgb(30, 30, 40), Color.rgb(170, 170, 180), Color.rgb(190, 90, 50)};
        float lastBg, npcLeft;

        static final int NB = 64;
        final int[] brow = new int[NB], bcol = new int[NB];
        final float[] bdelay = new float[NB], bsx = new float[NB], bsy = new float[NB];
        final boolean[] bland = new boolean[NB];
        int nb, buildType;
        float buildT;

        static final int NP = 140;
        final float[] px = new float[NP], py = new float[NP], pvx = new float[NP], pvy = new float[NP], pl = new float[NP];
        int pNext;

        int w, h;
        float ppm, floorY, ceilY, playerX;

        int state = START;
        boolean thrusting, onFloor, running;
        int vehicle, gravDir = 1;
        boolean diving;
        float teleFromY, telePulse, invuln, squash, flash, shake, hitstop, hintT, nextCrate, lastCoinSfx, unit = 1f;
        Sfx sfx;
        boolean ghost, outline;
        float leanPx, landT, ang, angV, settleT, finalDist;
        boolean seenIntro, dragging, moved;
        float tx0, ty0, pdx, pdy, lastDx = -22f, lastDy = 14f, introT, flipT, bonus, launchT, tipT, towerX;
        int bounces;
        boolean settled;
        Vibrator vib;
        float jumpDelay, y, vy, ay, dist, speed, bgOff, spawnLeft, stateTime, clock, runPhase;
        int electrons, best;
        String fact = FACTS[0];
        long lastNanos;

        Game(Context c) {
            super(c);
            prefs = c.getSharedPreferences("jetlab", Context.MODE_PRIVATE);
            best = prefs.getInt("best", 0);
            p.setAntiAlias(false);
            blit.setFilterBitmap(false);
        }

        void start() {
            if (running) return;
            running = true;
            lastNanos = 0;
            if (sfx == null) sfx = new Sfx();
            if (vib == null) vib = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            Choreographer.getInstance().postFrameCallback(this);
        }

        void stop() {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
            thrusting = false;
            if (sfx != null) { sfx.release(); sfx = null; }
        }

        @Override
        protected void onSizeChanged(int nw, int nh, int ow, int oh) {
            // the game is drawn on a 144 pixel tall canvas and scaled up with no smoothing
            h = 144;
            w = Math.max(160, Math.round(nw * 144f / nh));
            ppm = 16f;
            buf = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bc = new Canvas(buf);
            dst.set(0, 0, nw, nh);
            unit = nh / 144f;
            floorY = h - 11;
            ceilY = 11;
            playerX = w * 0.22f;
            towerX = playerX;
            if (state == START) y = TOWER_TOP - SH_BOT;
            npcs.clear();
            for (float nx = 30; nx < w + 20; nx += 70 + rnd.nextInt(80)) addSpawn(nx);
        }

        /** mode: 0 stands alone, 1 walks left, 2 walks right, 3 stands in a group */
        Npc addNpc(float nx, int mode, int face) {
            Npc n = new Npc();
            n.x = nx; n.phase = rnd.nextFloat() * 6f;
            n.mode = mode; n.face = face;
            n.walk = mode == 1 ? -28f : mode == 2 ? 38f : 0f;
            n.nextPoint = mode == 1 || mode == 2 ? 1e9f : 0.5f + rnd.nextFloat() * 7f;
            n.coat = rnd.nextInt(COATS.length); n.hair = rnd.nextInt(HAIRS.length);
            npcs.add(n);
            return n;
        }

        void addSpawn(float nx) {
            int r = rnd.nextInt(10);
            if (r < 3) addNpc(nx, 0, 1);
            else if (r < 5) addNpc(nx, 1, -1);
            else if (r < 7) addNpc(nx, 2, 1);
            else {
                int cnt = 2 + rnd.nextInt(3);
                float mid = (cnt - 1) / 2f;
                for (int i = 0; i < cnt; i++) addNpc(nx + i * 10, 3, i < mid ? 1 : i > mid ? -1 : (rnd.nextBoolean() ? 1 : -1));
            }
        }

        void reset() {
            coins.clear(); zappers.clear(); missiles.clear(); crates.clear();
            for (int i = 0; i < NP; i++) pl[i] = 0;
            vehicle = 0; gravDir = 1; invuln = 0; flash = 0; shake = 0; hitstop = 0; squash = 0; hintT = 0;
            diving = false; nextCrate = 80;
            y = TOWER_TOP - SH_BOT;
            vy = 0; dist = 0; electrons = 0; spawnLeft = w * 0.6f;
            towerX = playerX; dragging = false; pdx = pdy = 0; bonus = 0; launchT = 0; tipT = 0;
            state = LAUNCH; stateTime = 0;
        }

        static final int SH_TOP = 11, SH_BOT = 12;     // scientist sprite rows above and below his centre

        float hTop() { return vehicle == 0 ? SH_TOP : hhVeh(vehicle); }

        float hBot() { return vehicle == 0 ? SH_BOT : hhVeh(vehicle); }

        static int hhVeh(int v) { return v == STOMPER ? 12 : v == QUANTUM ? 14 : 8; }

        /** vehicles that stick to a surface and switch which one gravity points at */
        boolean flips() { return vehicle == INVERTER || vehicle == QUANTUM; }

        void sfx(int id) { if (sfx != null) sfx.play(id); }

        void buzz(int ms) {
            try {
                if (vib != null) vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } catch (Throwable t) { }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float gx = e.getX() / unit, gy = e.getY() / unit;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (state == START) {
                        if (!seenIntro) { seenIntro = true; state = INTRO; introT = 0; }
                        else state = LAUNCH;
                    } else if (state == INTRO) {
                        if (introT > 0.4f) { state = FLIP; flipT = 0; sfx(S_FLIP); }
                    } else if (state == DEAD) {
                        if (stateTime > 0.7f) reset();
                    } else if (state == LAUNCH) {
                        dragging = true; moved = false; tx0 = gx; ty0 = gy; pdx = pdy = 0;
                    } else if (state == PLAY) {
                        press();
                        thrusting = true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (state == LAUNCH && dragging) {
                        float dx = gx - tx0, dy = gy - ty0;
                        float len = (float) Math.sqrt(dx * dx + dy * dy);
                        if (len > MAXPULL) { dx *= MAXPULL / len; dy *= MAXPULL / len; len = MAXPULL; }
                        pdx = dx; pdy = dy;
                        if (len > 3f) moved = true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    thrusting = false;
                    if (state == LAUNCH && dragging) {
                        dragging = false;
                        if (moved && pdx * pdx + pdy * pdy > 36f) { lastDx = pdx; lastDy = pdy; }
                        launch();
                    }
                    break;
            }
            return true;
        }

        /** let go of the sling: the pull sets the launch speed and angle */
        void launch() {
            float lvx = -lastDx / MAXPULL, lvy = -lastDy / MAXPULL;
            y = TOWER_TOP - SH_BOT + lastDy;
            vy = lvy * 13f;
            bonus = Math.max(0f, lvx) * 7f;
            launchT = 0.9f; tipT = 3f;
            onFloor = false; jumpDelay = 0; thrusting = false;
            state = PLAY; stateTime = 0;
            shake = 3f;
            sfx(S_FLIP);
            buzz(25);
            for (int i = 0; i < 8; i++) burst(playerX, y);
        }

        /** waiting at the sling: title, intro picture, the flip to side view, and aiming */
        void updateLaunch(float dt) {
            if (state == INTRO) {
                introT += dt;
                if (introT > 3.6f) { state = FLIP; flipT = 0; sfx(S_FLIP); }
            } else if (state == FLIP) {
                flipT += dt;
                if (flipT >= 0.8f) state = LAUNCH;
            }
            y = TOWER_TOP - SH_BOT; vy = -1f; onFloor = !(state == LAUNCH && dragging);
            speed = 0;
            for (int i = 0; i < NP; i++) {
                if (pl[i] > 0) {
                    pl[i] -= dt;
                    px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
                }
            }
        }

        /** jump to the mirror position on the opposite surface, gravity follows */
        void teleport() {
            teleFromY = y;
            for (int i = 0; i < 12; i++) burst(playerX, y);
            y = ceilY + floorY - y;
            gravDir = -gravDir;
            vy = 0;
            telePulse = 0.25f;
            shake = Math.max(shake, 1.5f);
            flash = 0.05f;
            sfx(S_TELE);
            buzz(15);
            for (int i = 0; i < 12; i++) burst(playerX, y);
        }

        void press() {
            if (vehicle == INVERTER) {
                gravDir = -gravDir;
                vy *= 0.3f;
                shake = Math.max(shake, 2f);
                sfx(S_FLIP);
                buzz(15);
                for (int i = 0; i < 10; i++) burst(playerX, y);
            } else if (vehicle == QUANTUM) {
                teleport();
            } else if (vehicle == STOMPER) {
                if (!onFloor && squash <= 0) { vy = VFALL; diving = true; sfx(S_FLIP); }
            } else if (onFloor) {
                vy = -JUMP; jumpDelay = 0.22f; onFloor = false;
                sfx(S_JUMP);
                for (int i = 0; i < 4; i++) burst(playerX - 3, y + 10);
            }
        }

        @Override
        public void doFrame(long nanos) {
            if (!running) return;
            float dt = lastNanos == 0 ? 0.016f : (nanos - lastNanos) / 1e9f;
            lastNanos = nanos;
            if (dt > 0.033f) dt = 0.033f;
            if (w > 0) update(dt);
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        // ---------- physics ----------

        void update(float rdt) {
            clock += rdt;
            stateTime += rdt;
            if (flash > 0) flash -= rdt;
            if (shake > 0) shake = Math.max(0f, shake - 30f * rdt);
            if (hitstop > 0) { hitstop -= rdt; return; }   // freeze for a moment on a big hit

            boolean waiting = state == START || state == INTRO || state == FLIP || state == LAUNCH;   // the scene holds still until he runs
            float nsc = (bgOff - lastBg) * 2.4f;       // background scientists move at 60% of the floor
            lastBg = bgOff;
            npcLeft -= nsc;
            if (npcLeft <= 0) { addSpawn(w + 10); npcLeft = 60 + rnd.nextInt(110); }
            for (int i = npcs.size() - 1; i >= 0; i--) {
                Npc n = npcs.get(i);
                n.x += (waiting ? 0f : n.walk * rdt) - nsc;
                if (n.pointT > 0) n.pointT -= rdt;
                else if ((n.nextPoint -= rdt) <= 0) { n.pointT = 0.8f + rnd.nextFloat() * 1.2f; n.nextPoint = 2f + rnd.nextFloat() * 7f; }
                if (npcs.get(i).x < -10 || npcs.get(i).x > w + 60) npcs.remove(i);
            }

            float dt = rdt;
            if (state == DEAD) { updateDead(dt); return; }
            if (state == START || state == INTRO || state == FLIP || state == LAUNCH) { updateLaunch(dt); return; }
            if (state == BUILD) {
                buildT += rdt;
                dt = rdt * 0.15f;                           // time slows while the vehicle assembles
                if (buildT >= BUILD_TIME) finishBuild();
            }
            if (invuln > 0) invuln -= rdt;
            if (hintT > 0) hintT -= rdt;
            if (squash > 0) squash -= rdt;
            if (landT > 0) landT -= rdt;
            if (telePulse > 0) telePulse -= rdt;
            boolean playing = state == PLAY || state == BUILD;
            float r = vehicle == 0 ? 0.3f * ppm : (vehicle == STOMPER ? 8f : 7f);

            // vertical motion: gravity, thrust (on foot only), speed limited
            if (jumpDelay > 0) jumpDelay -= dt;
            boolean fire = thrusting && state == PLAY && vehicle == 0 && jumpDelay <= 0;
            ay = GRAVITY * gravDir - (fire ? THRUST : 0);
            vy += ay * dt;
            float vmin = flips() ? -VFALL : -VMAX - (launchT > 0 ? 7f * launchT / 0.9f : 0f);
            if (vy > VFALL) vy = VFALL;
            if (vy < vmin) vy = vmin;
            y += vy * ppm * dt;
            onFloor = false;
            float lo = ceilY + hTop(), hi = floorY - hBot();
            if (y < lo) {
                y = lo;
                if (vy < 0) {
                    if (vehicle == INVERTER && vy < -6f) { shake = Math.max(shake, 2f); sfx(S_STOMP); for (int i = 0; i < 6; i++) burst(playerX, y - 6); }
                    vy = 0;
                }
                if (flips()) onFloor = true;
            }
            if (y > hi) {
                y = hi;
                if (vy > 0) {
                    if (vehicle == STOMPER && playing) {
                        float impact = vy;
                        squash = 0.14f;
                        if (diving) {
                            diving = false; vy = -11f; shake = 5f;
                            sfx(S_STOMP); buzz(20);
                            for (int i = 0; i < 14; i++) burst(playerX, y + 8);
                        } else {
                            vy = -(thrusting ? 12.5f : 9.5f);
                            if (impact > 6f) { shake = Math.max(shake, 1.5f); sfx(S_STOMP); }
                        }
                    } else {
                        if (vehicle == 0 && vy > 9f) {
                            shake = Math.max(shake, 1.5f);
                            landT = 0.12f;
                            for (int i = 0; i < 5; i++) burst(playerX - 3, y + 10);
                        }
                        vy = 0;
                    }
                }
                if (vehicle != STOMPER) onFloor = true;
            }
            if (vehicle == STOMPER && y >= hi - 0.5f) onFloor = true;

            if (fire) emitExhaust();
            for (int i = 0; i < NP; i++) {
                if (pl[i] > 0) {
                    pl[i] -= dt;
                    px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
                }
            }

            if (launchT > 0) launchT -= dt;
            if (tipT > 0) tipT -= rdt;
            bonus = Math.max(0f, bonus - 4.5f * dt);
            speed = 5f + Math.min(dist / 60f, 7f) + bonus;
            float scroll = speed * ppm * dt;
            towerX -= scroll;
            dist += speed * dt;
            bgOff += scroll * 0.25f;
            if (onFloor && (vehicle == 0 || vehicle == QUANTUM)) runPhase += dt * 11f;     // steps at a fixed rate, they do not speed up with the world

            spawnLeft -= scroll;
            if (spawnLeft <= 0) spawn();

            for (int i = crates.size() - 1; i >= 0; i--) {
                Crate k = crates.get(i);
                k.x -= scroll;
                float dx = k.x - playerX, dy = k.y - y;
                if (state == PLAY && vehicle == 0 && dx * dx + dy * dy < (r + 8) * (r + 8)) {
                    startBuild(k.type);
                    crates.remove(i);
                    continue;
                }
                if (k.x < -20) crates.remove(i);
            }

            for (int i = coins.size() - 1; i >= 0; i--) {
                Coin c = coins.get(i);
                c.x -= scroll;
                float dx = c.x - playerX, dy = c.y - y;
                if (!c.taken && dx * dx + dy * dy < (r + 0.22f * ppm) * (r + 0.22f * ppm)) {
                    c.taken = true; electrons++;
                    if (clock - lastCoinSfx > 0.06f) { sfx(S_COIN); lastCoinSfx = clock; }
                }
                if (c.x < -ppm || c.taken) coins.remove(i);
            }

            for (int i = zappers.size() - 1; i >= 0; i--) {
                Zapper z = zappers.get(i);
                z.x -= scroll;
                z.angle += z.spin * dt;
                if (z.x < -2.5f * ppm) { zappers.remove(i); continue; }
                float hx = (float) Math.cos(z.angle) * z.len / 2, hy = (float) Math.sin(z.angle) * z.len / 2;
                if (state == PLAY && invuln <= 0 && segDist(playerX, y, z.x - hx, z.y - hy, z.x + hx, z.y + hy) < r + 0.06f * ppm) hit();
            }

            for (int i = missiles.size() - 1; i >= 0; i--) {
                Missile m = missiles.get(i);
                if (!m.flying) {
                    m.warn -= dt;
                    if (m.warn > 0.4f) m.y += (y - m.y) * Math.min(1f, 6f * dt);
                    if (m.warn <= 0) { m.flying = true; m.x = w + 0.5f * ppm; }
                } else {
                    m.x -= (speed + 9f) * ppm * dt;
                    float dx = m.x - playerX, dy = m.y - y;
                    if (state == PLAY && invuln <= 0 && dx * dx + dy * dy < (r + 0.22f * ppm) * (r + 0.22f * ppm)) {
                        hit();
                        missiles.remove(i);
                        continue;
                    }
                    if (m.x < -ppm) missiles.remove(i);
                }
            }
        }

        // ---------- vehicles ----------

        void startBuild(int type) {
            buildType = type;
            state = BUILD;
            buildT = 0;
            invuln = BUILD_TIME + 1f;
            flash = 0.12f;
            sfx(S_BUILD);
            String[] rows = vehRows(type, false);
            nb = 0;
            for (int r0 = 0; r0 < rows.length; r0 += 3) {
                for (int c0 = 0; c0 < rows[0].length(); c0 += 3) {
                    boolean any = false;
                    for (int rr = 0; rr < 3 && r0 + rr < rows.length; rr++)
                        for (int cc = 0; cc < 3 && c0 + cc < rows[0].length(); cc++)
                            if (rows[r0 + rr].charAt(c0 + cc) != '.') any = true;
                    if (!any || nb >= NB) continue;
                    brow[nb] = r0; bcol[nb] = c0;
                    bdelay[nb] = 0.1f + rnd.nextFloat() * 0.7f;
                    bsx[nb] = rnd.nextBoolean() ? -20 - rnd.nextFloat() * 60 : w + 20 + rnd.nextFloat() * 60;
                    bsy[nb] = rnd.nextFloat() * h;
                    bland[nb] = false;
                    nb++;
                }
            }
        }

        void finishBuild() {
            vehicle = buildType;
            state = PLAY;
            gravDir = 1;
            vy = 0;
            diving = false;
            flash = 0.3f;
            shake = 4f;
            hintT = 2.8f;
            sfx(S_DONE);
            buzz(40);
            for (int i = 0; i < 24; i++) burst(playerX, y);
        }

        void hit() {
            if (state != PLAY || invuln > 0) return;
            if (vehicle != 0) breakVehicle(); else die();
        }

        void breakVehicle() {
            for (int i = 0; i < 40; i++) burst(playerX, y);
            vehicle = 0; gravDir = 1; diving = false;
            invuln = 1.6f; hitstop = 0.08f; shake = 7f; flash = 0.12f;
            vy = -5f;
            sfx(S_HIT);
            buzz(60);
        }

        void die() {
            if (state != PLAY) return;
            state = DEAD; stateTime = 0;
            hitstop = 0.1f; shake = 8f; flash = 0.15f;
            sfx(S_HIT);
            buzz(120);
            fact = FACTS[rnd.nextInt(FACTS.length)];
            finalDist = dist;
            int score = (int) dist;
            if (score > best) {
                best = score;
                prefs.edit().putInt("best", best).apply();
            }
            for (int i = 0; i < 30; i++) burst(playerX, y);
            // ragdoll: popped up, tumbling forward
            vehicle = 0; gravDir = 1; thrusting = false;
            vy = -6f; ang = 0; angV = 640f; bounces = 0; settled = false; settleT = 0;
        }

        /** a spark kicked up where he drags along the floor */
        void dust(float dx, float dy) {
            int k = pNext++ % NP;
            px[k] = dx; py[k] = dy;
            pvx[k] = (-speed * 0.5f + (rnd.nextFloat() - 0.3f) * 3f) * ppm;
            pvy[k] = -rnd.nextFloat() * 4f * ppm;
            pl[k] = 0.3f + rnd.nextFloat() * 0.3f;
        }

        /** after a zap: bounce a few times, faceplant, then get dragged along the floor */
        void updateDead(float dt) {
            vy += GRAVITY * dt;
            if (vy > VFALL) vy = VFALL;
            y += vy * ppm * dt;
            float hb = SH_BOT - 4f * settleT;               // lying flat he sits lower than standing
            float hi = floorY - hb;
            if (y > hi) {
                y = hi;
                if (vy > 3f && bounces < 3) {
                    vy = -vy * 0.55f;
                    bounces++;
                    angV *= 0.65f;
                    shake = Math.max(shake, 2.5f);
                    sfx(S_STOMP);
                    for (int i = 0; i < 6; i++) dust(playerX, floorY - 2);
                } else {
                    vy = 0;
                    if (!settled) {
                        settled = true;
                        shake = Math.max(shake, 3f);
                        sfx(S_STOMP);
                        for (int i = 0; i < 10; i++) dust(playerX, floorY - 2);
                    }
                }
            }
            if (!settled) {
                ang += angV * dt;
            } else {
                float target = 90f + 360f * Math.round((ang - 90f) / 360f);   // face down
                ang += (target - ang) * Math.min(1f, 10f * dt);
                settleT = Math.min(1f, settleT + dt * 5f);
            }
            // the world slows down while he is dragged along the floor
            speed = Math.max(0f, speed - (settled ? 6f : 2f) * dt);
            float sc = speed * ppm * dt;
            bgOff += sc * 0.25f;
            dist += speed * dt;
            if (settled && speed > 0.3f && rnd.nextInt(3) == 0) dust(playerX - 4, floorY - 1);
            towerX -= sc;
            for (Coin k : coins) k.x -= sc;
            for (Zapper z : zappers) z.x -= sc;
            for (Crate k : crates) k.x -= sc;
            for (int i = missiles.size() - 1; i >= 0; i--) {
                Missile m = missiles.get(i);
                if (m.flying) { m.x -= (speed + 9f) * ppm * dt; if (m.x < -ppm) missiles.remove(i); }
            }
            for (int i = 0; i < NP; i++) {
                if (pl[i] > 0) {
                    pl[i] -= dt;
                    px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
                }
            }
        }

        void burst(float bx, float by) {
            int k = pNext++ % NP;
            px[k] = bx; py[k] = by;
            pvx[k] = (rnd.nextFloat() - 0.5f) * 8f * ppm;
            pvy[k] = (rnd.nextFloat() - 0.7f) * 8f * ppm;
            pl[k] = 0.9f;
        }

        void emitExhaust() {
            for (int n = 0; n < 2; n++) {
                int k = pNext++ % NP;
                px[k] = playerX - 5; py[k] = y + 5;
                pvx[k] = (-speed * 0.6f + (rnd.nextFloat() - 0.5f) * 2f) * ppm;
                pvy[k] = (4f + rnd.nextFloat() * 4f) * ppm;
                pl[k] = 0.35f + rnd.nextFloat() * 0.3f;
            }
        }

        static float segDist(float cx, float cy, float ax, float ay, float bx, float by) {
            float dx = bx - ax, dy = by - ay;
            float t = ((cx - ax) * dx + (cy - ay) * dy) / (dx * dx + dy * dy);
            t = Math.max(0f, Math.min(1f, t));
            float qx = ax + t * dx - cx, qy = ay + t * dy - cy;
            return (float) Math.sqrt(qx * qx + qy * qy);
        }

        // ---------- spawning ----------

        float randY(float loM, float hiM) {
            return (loM + rnd.nextFloat() * (hiM - loM)) * ppm;
        }

        void spawn() {
            float x0 = w + 0.6f * ppm;
            if (dist >= nextCrate) {
                if (vehicle == 0) {
                    Crate k = new Crate();
                    k.x = x0; k.y = randY(3f, 6f); k.type = 1 + rnd.nextInt(3);
                    crates.add(k);
                    spawnLeft = 4f * ppm;
                    nextCrate = dist + 220 + rnd.nextInt(150);
                } else {
                    nextCrate = dist + 100;
                }
                return;
            }
            int roll = rnd.nextInt(10);
            float gap = (3.5f - Math.min(dist / 400f, 1.5f) + rnd.nextFloat() * 2f) * ppm;
            if (roll < 4) {
                int kind = rnd.nextInt(3);
                float cy = randY(2.4f, 6.6f);
                if (kind == 0) {            // sine wave
                    for (int i = 0; i < 14; i++) addCoin(x0 + i * 0.5f * ppm, cy + (float) Math.sin(i * 0.6) * 1.3f * ppm);
                    spawnLeft = 14 * 0.5f * ppm + gap;
                } else if (kind == 1) {     // block
                    for (int i = 0; i < 6; i++) for (int j = 0; j < 3; j++) addCoin(x0 + i * 0.5f * ppm, cy + (j - 1) * 0.5f * ppm);
                    spawnLeft = 6 * 0.5f * ppm + gap;
                } else {                    // line
                    for (int i = 0; i < 9; i++) addCoin(x0 + i * 0.5f * ppm, cy);
                    spawnLeft = 9 * 0.5f * ppm + gap;
                }
            } else if (roll < 9 || dist < 100) {
                int n = (dist > 400 && rnd.nextFloat() < 0.4f) ? 2 : 1;
                for (int i = 0; i < n; i++) {
                    Zapper z = new Zapper();
                    z.x = x0 + i * 3.2f * ppm;
                    z.len = (2f + rnd.nextFloat() * 1.5f + Math.min(dist / 500f, 1f)) * ppm;
                    z.y = randY(2.6f + z.len / ppm / 2 - 1f, 6.4f - z.len / ppm / 2 + 1f);
                    z.angle = (float) (rnd.nextFloat() < 0.6f ? Math.PI / 2 + (rnd.nextFloat() - 0.5f) * 0.8f : rnd.nextFloat() * Math.PI);
                    z.spin = dist > 250 && rnd.nextFloat() < 0.5f ? (rnd.nextBoolean() ? 1f : -1f) * (0.8f + rnd.nextFloat()) : 0f;
                    zappers.add(z);
                }
                spawnLeft = (n * 3.2f + 1.5f) * ppm + gap;
            } else {
                Missile m = new Missile();
                m.warn = 1.4f; m.y = y;
                sfx(S_WARN);
                missiles.add(m);
                spawnLeft = 4f * ppm;
            }
        }

        void addCoin(float x, float yy) {
            Coin c = new Coin();
            c.x = x;
            c.y = Math.max(ceilY + 0.3f * ppm, Math.min(floorY - 0.3f * ppm, yy));
            coins.add(c);
        }

        // ---------- drawing (pixel art, drawn small then scaled up) ----------

        static final String CH = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,!-+/:'^ ";
        static final String[] GL = {
            "010101111101101", "110101110101110", "011100100100011", "110101101101110", "111100110100111",
            "111100110100100", "011100101101011", "101101111101101", "111010010010111", "001001001101010",
            "101101110101101", "100100100100111", "101111111101101", "110101101101101", "010101101101010",
            "110101110100100", "010101101111011", "110101110101101", "011100010001110", "111010010010010",
            "101101101101111", "101101101101010", "101101111111101", "101101010101101", "101101010010010",
            "111001010100111",
            "111101101101111", "010110010010111", "110001010100111", "110001010001110", "101101111001001",
            "111100110001110", "011100111101111", "111001010010010", "111101111101111", "111101111001110",
            "000000000000010", "000000000010100", "010010010000010", "000000111000000", "000010111010000",
            "001001010100100", "000010000010000", "010010000000000", "010101000000000", "000000000000000"
        };

        static final String[] SPR_HEAD = {
            "......b..bb.....",
            ".....bBbBBBb....",
            "....bBBBBBBBBb..",
            "....BBBSSSSSSS..",
            "....BsSSBBSBBS..",
            "....BsSSEeSEeSS.",
            "....BsSSEESEESs.",
            "..P.BsSaSSSSsSS.",
            "..P.Bsaaeeeeeaa.",
            "..P..saaaaaaaa..",
            ".hOOowWWVtW.....",
            ".hOOowxxVtW.....",
            ".OOOowxxxSS.....",
            ".OOoowxxxSN.....",
            ".NNNNwWWWWW.....",
            "..NN.wwwwww....."
        };
        static final String[] SPR_HEAD_B = {
            ".......b..bb....",
            ".....bBbBBBb....",
            "....bBBBBBBBBb..",
            "....BBBSSSSSSS..",
            "....BsSSBBSBBS..",
            "....BsSSEeSEeSS.",
            "....BsSSEESEESs.",
            "..P.BsSaSSSSsSS.",
            "..P.Bsaaeeeeeaa.",
            "..P..saaannaaa..",
            ".hOOowwWWVt.....",
            ".hOOowxxxSS.....",
            ".OOOowxxxSN.....",
            ".OOoowxxWVt.....",
            ".NNNNwwWWWW.....",
            "..NN.wwwwww....."
        };
        static final String[] SPR_RUN2 = {
            "......b..bb.....",
            ".....bBbBBBb....",
            "....bBBBBBBBBb..",
            "....BBBSSSSSSS..",
            "....BsSSBBSBBS..",
            "....BsSSEeSEeSS.",
            "....BsSSEESEESs.",
            "..P.BsSaSSSSsSS.",
            "..P.Bsaaeeeeeaa.",
            "..P..saaaaaaaa..",
            ".hOOoxwwVtw.....",
            ".hOOoxxxVtw.....",
            ".OOOoxxxxSS.....",
            ".OOooxxxxSN.....",
            ".NNNNxwwWWw.....",
            "..NN.wwwwww....."
        };
        static final String[] SPR_RUN3 = {
            ".......b..bb....",
            ".....bBbBBBb....",
            "....bBBBBBBBBb..",
            "....BBBSSSSSSS..",
            "....BsSSBBSBBS..",
            "....BsSSEeSEeSS.",
            "....BsSSEESEESs.",
            "..P.BsSaSSSSsSS.",
            "..P.Bsaaeeeeeaa.",
            "..P..saaannaaa..",
            ".hOOowwVtWW.....",
            ".hOOowxxxSS.....",
            ".OOOowxxxSN.....",
            ".OOoowxxtWW.....",
            ".NNNNwwWWWW.....",
            "..NN.wwwwww....."
        };
        /** skin over the top row of the eyes, drawn on top of the head for a blink */
        static final String[] SPR_BLINK = {
            "................",
            "................",
            "................",
            "................",
            "................",
            "........SSSSS...",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................"
        };
        /** upper body per run phase: same outline, shading and tie shift so the torso seems to turn */
        static final String[][] SPR_RUN = {SPR_HEAD, SPR_HEAD_B, SPR_RUN2, SPR_RUN3};
        static final String[] LEGS_A = {
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....KKKK.KKKK..",
            "....LKKKK.LKKKK."
        };
        static final String[] LEGS_B = {
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            "....DDd..DDd....",
            "....KKKK.KKKK...",
            "........LKKKKK.."
        };
        static final String[] LEGS_M = {
            ".....DDDDDd.....",
            ".....DDDDDd.....",
            ".....DDDDDd.....",
            ".....DDDDDd.....",
            ".....DDDDDd.....",
            ".....KKKKKKK....",
            "....LKKKKKKKK..."
        };
        static final String[] LEGS_C = {
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd..DDd...",
            ".....KKKK.KKKK..",
            "....LKKKK......."
        };
        static final String[] LEGS_AIR = {
            "....DDDd.DDDd...",
            "...DDDd..DDDd...",
            "..DDDd...DDDd...",
            ".DDDd....DDDd...",
            ".DDd.....DDd....",
            ".KKK.....KKKK...",
            "................"
        };

        static final String[] LEGS_AIR2 = {
            "....DDDd.DDDd...",
            "...DDDd...DDd...",
            "..DDDd....DDDd..",
            ".DDDd.....DDDd..",
            ".DDd......DDd...",
            ".KKK......KKK...",
            "................"
        };
        static final String[] LEGS_FALL1 = {
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd.DDDd...",
            ".....DDd..DDd...",
            ".....KKK..KKK...",
            ".....KKK..KKK..."
        };
        static final String[] LEGS_FALL2 = {
            ".....DDd.DDd....",
            ".....DDd.DDd....",
            ".....DDd..DDDd..",
            ".....DDd...DDd..",
            ".....DDd...DDd..",
            ".....KKK...KKKK.",
            "................"
        };

        /** face down on the floor after a zap, facing right (28 wide, 11 tall) */
        static final String[] SPR_FLAT = {
            "............................",
            "...........ooO..bb.b........",
            "..........hOOOoBBbBBb.......",
            "..........hOOOoBBBBBBBb.....",
            "....DDd...hOOOoWwBBBBBBBB...",
            "...DDLd...NNNNwWWWBsSSSSSB..",
            "..KKd.dDDDDDWWWWWWWBsSEXSs..",
            "..KK...DDDDDwWWWWWWWxsSSSSSa",
            "KKKDDDDDDDDDwwwwwwwwxsaannra",
            "LKKDDDDDDDDDwwwwwwwwxaaaaaa.",
            "......................xxxSSS"
        };

        static final int HEAD_ROWS = 10;
        static final String[] HEAD_ONLY = headOnly();

        /** just the head, without the hose, for the vehicle cockpits */
        static String[] headOnly() {
            String[] h = Arrays.copyOfRange(SPR_HEAD, 0, HEAD_ROWS);
            for (int i = 0; i < h.length; i++) h[i] = h[i].replace('P', '.');
            return h;
        }

        static final String[] BODY_STOMP = {
            "....mmmmmmmmmmmm....",
            "...mMMMMMMMMMMMMm...",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMMMMMMMMMMMMMMm..",
            "..mRRRRRRRRRRRRRRm..",
            "..mRYRRRRRRRRRRYRm..",
            "..mRRRRRRRRRRRRRRm..",
            "...mmmmmmmmmmmmmm..."
        };
        static final String[] LEGS_STOMP = {
            "......MM......MM....", ".....MM......MM.....", "......MM......MM....", ".....MM......MM.....",
            "......MM......MM....", "....KKKK....KKKK....", "...KKKKK....KKKKK..."
        };
        static final String[] LEGS_STOMP_C = {
            "....MMMM....MMMM....", "...KKKKK....KKKKK...", "...KKKKK....KKKKK...", "...KKKKKK..KKKKKK..."
        };
        static final String[] POD = {
            "......RRRRRRRR......",
            ".....RRRRRRRRRR.....",
            "....mmmmmmmmmmmm....",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "..mMggggggggggggMm..",
            "....mmmmmmmmmmmm....",
            ".....UUUUUUUUUU.....",
            "......UUUUUUUU......"
        };
        static final String[] BOARD = {
            "..mMMMMMMMMMMMMMMm..",
            "...mmmmmmmmmmmmmm...",
            "..UU............UU..",
            "...UUUUUUUUUUUUUU..."
        };
        /** the scientist standing on a board with two linked orbs, padded to the vehicle width */
        static final String[] QUANT = quant();

        static String[] quant() {
            String[] out = new String[SPR_HEAD.length + LEGS_A.length + BOARD.length];
            int n = 0;
            for (String r : SPR_HEAD) out[n++] = ".." + r + "..";
            for (String r : LEGS_A) out[n++] = ".." + r + "..";
            for (String r : BOARD) out[n++] = r;
            return out;
        }

        static String[] vehRows(int type, boolean compressed) {
            if (type == QUANTUM) return QUANT;
            if (type == INVERTER) return POD;
            String[] legs = compressed ? LEGS_STOMP_C : LEGS_STOMP;
            String[] out = Arrays.copyOf(BODY_STOMP, BODY_STOMP.length + legs.length);
            System.arraycopy(legs, 0, out, BODY_STOMP.length, legs.length);
            return out;
        }

        void rectPx(Canvas c, float x, float yy, float rw, float rh, int col) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            int ix = Math.round(x), iy = Math.round(yy);
            c.drawRect(ix, iy, ix + Math.round(rw), iy + Math.round(rh), p);
        }

        void glyphs(Canvas c, String s, int ix, int iy, int sc, int col) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            for (int i = 0; i < s.length(); i++) {
                int g = CH.indexOf(s.charAt(i));
                if (g < 0) continue;
                String bits = GL[g];
                for (int r = 0; r < 5; r++) {
                    for (int k = 0; k < 3; k++) {
                        if (bits.charAt(r * 3 + k) == '1') {
                            int gx = ix + (i * 4 + k) * sc, gy = iy + r * sc;
                            c.drawRect(gx, gy, gx + sc, gy + sc, p);
                        }
                    }
                }
            }
        }

        /** align: 0 left, 1 centre, 2 right. y is the top of the text. */
        void text(Canvas c, String s, float x, float yy, int sc, int col, int align) {
            s = s.toUpperCase();
            int tw = s.length() * 4 * sc - sc;
            int ix = Math.round(x);
            if (align == 1) ix -= tw / 2;
            else if (align == 2) ix -= tw;
            int iy = Math.round(yy);
            glyphs(c, s, ix + sc, iy + sc, sc, Color.argb(160, 0, 0, 20));
            glyphs(c, s, ix, iy, sc, col);
        }

        void wrapText(Canvas c, String s, float cx, float yy, int sc, int col, int maxPx) {
            int per = Math.max(8, maxPx / (4 * sc));
            String[] words = s.split(" ");
            StringBuilder line = new StringBuilder();
            float ly = yy;
            for (String wd : words) {
                if (line.length() > 0 && line.length() + 1 + wd.length() > per) {
                    text(c, line.toString(), cx, ly, sc, col, 1);
                    ly += 7 * sc;
                    line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(wd);
            }
            if (line.length() > 0) text(c, line.toString(), cx, ly, sc, col, 1);
        }

        static int pal(char ch) {
            switch (ch) {
                case 'B': return Color.rgb(80, 50, 30);
                case 'b': return Color.rgb(135, 90, 55);
                case 'E': return Color.rgb(30, 30, 45);
                case 'n': return Color.rgb(150, 70, 70);
                case 'h': return Color.rgb(255, 135, 85);
                case 'x': return Color.rgb(150, 160, 185);
                case 'd': return Color.rgb(78, 92, 140);
                case 'L': return Color.rgb(95, 95, 108);
                case 'y': return Color.rgb(150, 110, 70);
                case 't': return Color.rgb(40, 45, 70);
                case 'S': return Color.rgb(240, 200, 160);
                case 'G': return Color.rgb(50, 60, 80);
                case 'C': return Color.rgb(120, 235, 255);
                case 'O': return Color.rgb(225, 80, 40);
                case 'a': return Color.rgb(170, 140, 115);
                case 'r': return Color.rgb(230, 120, 140);
                case 'X': return Color.rgb(60, 40, 40);
                case 'e': return Color.rgb(255, 255, 255);
                case 'P': return Color.rgb(210, 170, 60);
                case 'o': return Color.rgb(150, 45, 25);
                case 'N': return Color.rgb(120, 125, 140);
                case 'W': return Color.rgb(240, 244, 250);
                case 'w': return Color.rgb(195, 203, 220);
                case 'D': return Color.rgb(50, 60, 100);
                case 's': return Color.rgb(205, 160, 120);
                case 'T': return Color.rgb(60, 45, 40);
                case 'V': return Color.rgb(70, 100, 160);
                case 'R': return Color.rgb(220, 70, 70);
                case 'U': return Color.rgb(70, 110, 230);
                case 'Y': return Color.rgb(255, 200, 80);
                case 'M': return Color.rgb(170, 178, 195);
                case 'm': return Color.rgb(100, 108, 128);
                case 'g': return Color.rgb(140, 215, 235);
                case 'K': return Color.rgb(30, 30, 40);
                default: return 0;
            }
        }

        /** a sprite with a dark one pixel outline so it stands off the backdrop */
        void outlined(Canvas c, String[] rows, int left, int top) {
            if (!ghost) {
                outline = true;
                sprite(c, rows, left - 1, top);
                sprite(c, rows, left + 1, top);
                sprite(c, rows, left, top - 1);
                sprite(c, rows, left, top + 1);
                outline = false;
            }
            sprite(c, rows, left, top);
        }

        void sprite(Canvas c, String[] rows, int left, int top) {
            p.setStyle(Paint.Style.FILL);
            for (int r = 0; r < rows.length; r++) {
                String row = rows[r];
                for (int k = 0; k < row.length(); k++) {
                    int col = pal(row.charAt(k));
                    if (col == 0) continue;
                    p.setColor(ghost ? Color.argb(110, 120, 235, 255) : outline ? Color.rgb(8, 10, 20) : col);
                    int dxr = leanPx == 0 ? 0 : Math.round(leanPx * (rows.length - 1 - r) / (rows.length - 1f));
                    c.drawRect(left + k + dxr, top + r, left + k + dxr + 1, top + r + 1, p);
                }
            }
        }

        @Override
        protected void onDraw(Canvas out) {
            if (w == 0 || bc == null) return;
            Canvas c = bc;
            if (state == INTRO) {
                drawTopDown(c);
            } else if (state == FLIP) {
                rectPx(c, 0, 0, w, h, Color.rgb(6, 8, 14));
                float t = flipT / 0.8f;
                boolean first = t < 0.5f;
                float sy = first ? 1f - t / 0.5f : (t - 0.5f) / 0.5f;     // the picture tips over like a card
                if (sy > 0.04f) {
                    c.save();
                    c.scale(1f, sy, w / 2f, h / 2f);
                    if (first) drawTopDown(c); else drawScene(c);
                    c.restore();
                }
            } else {
                drawScene(c);
            }
            if (flash > 0) rectPx(c, 0, 0, w, h, Color.argb((int) Math.min(200, flash * 520), 255, 255, 255));
            if (state == START) drawTitle(c);
            if (state == DEAD) drawDead(c);
            out.save();
            if (shake > 0) out.translate((rnd.nextFloat() - 0.5f) * 2f * shake * unit, (rnd.nextFloat() - 0.5f) * 2f * shake * unit);
            out.drawBitmap(buf, null, dst, blit);
            out.restore();
        }

        void drawScene(Canvas c) {
            drawBackground(c);
            drawFloor(c);
            drawNpcs(c);
            rectPx(c, 0, ceilY, w, floorY - ceilY, Color.argb(95, 6, 10, 24));   // push the whole backdrop back
            drawTower(c);
            drawCoins(c);
            drawCrates(c);
            drawZappers(c);
            drawMissiles(c);
            drawParticles(c);
            drawPlayer(c);
            if (telePulse > 0) {
                ring(c, playerX, teleFromY, telePulse / 0.25f);
                ring(c, playerX, y, telePulse / 0.25f);
            }
            if (state == BUILD) drawBuild(c);
            if (state == LAUNCH) drawLaunchUi(c);
            drawHud(c);
        }

        // ---------- launch tower, sling and intro ----------

        void line(Canvas c, float x0, float y0, float x1, float y1, int col, float wd) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(wd);
            p.setColor(col);
            c.drawLine(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), p);
            p.setStyle(Paint.Style.FILL);
        }

        void drawTower(Canvas c) {
            if (towerX < -60 || towerX > w + 60) return;
            int tx = Math.round(towerX), top = TOWER_TOP, fl = Math.round(floorY);
            // the enemy lab's launch tower
            rectPx(c, tx - 14, top + 4, 28, fl - top - 4, Color.rgb(88, 100, 122));
            rectPx(c, tx - 14, top + 4, 3, fl - top - 4, Color.rgb(62, 72, 92));
            rectPx(c, tx + 11, top + 4, 3, fl - top - 4, Color.rgb(112, 126, 150));
            for (int yy = top + 16; yy < fl - 4; yy += 14) rectPx(c, tx - 14, yy, 28, 1, Color.rgb(60, 70, 90));
            for (int yy = top + 20; yy < fl - 12; yy += 14) {
                for (int k = 0; k < 2; k++) {
                    boolean lit = (((int) (clock * 1.5f) + yy / 14 + k) & 3) != 0;
                    rectPx(c, tx - 8 + k * 10, yy, 5, 6, lit ? Color.rgb(255, 215, 100) : Color.rgb(40, 45, 60));
                }
            }
            rectPx(c, tx - 20, top, 40, 4, Color.rgb(125, 140, 168));
            rectPx(c, tx - 20, top + 4, 40, 1, Color.rgb(50, 58, 76));
            for (int k = 0; k < 10; k++) rectPx(c, tx - 20 + k * 4, top + 5, 2, 3, Color.rgb(230, 180, 40));
            // antenna with a blinking light
            rectPx(c, tx + 17, top - 16, 1, 16, Color.rgb(150, 160, 180));
            if (((int) (clock * 2) & 1) == 0) rectPx(c, tx + 16, top - 18, 3, 3, Color.rgb(255, 70, 60));
            // slingshot
            int wood = Color.rgb(120, 78, 44), wood2 = Color.rgb(160, 108, 64);
            rectPx(c, tx - 2, top - 7, 4, 7, wood);
            rectPx(c, tx - 7, top - 9, 14, 3, wood);
            rectPx(c, tx - 7, top - 18, 3, 10, wood2);
            rectPx(c, tx + 4, top - 18, 3, 10, wood2);
            float pcx = playerX + ((state == LAUNCH && dragging) ? pdx : 0f);
            float pcy = y + ((state == LAUNCH && dragging) ? pdy : 0f);
            int band = Color.rgb(200, 70, 50);
            if (state == START || state == LAUNCH || state == FLIP) {
                line(c, tx - 6, top - 17, pcx - 2, pcy - 2, band, 2);
                line(c, tx + 5, top - 17, pcx + 2, pcy - 2, band, 2);
            } else {
                line(c, tx - 6, top - 17, tx, top - 12, band, 2);     // slack after the launch
                line(c, tx + 5, top - 17, tx, top - 12, band, 2);
            }
        }

        void drawLaunchUi(Canvas c) {
            float dx = dragging ? pdx : lastDx, dy = dragging ? pdy : lastDy;
            float lvx = -dx / MAXPULL, lvy = -dy / MAXPULL;
            float vx = (5f + Math.max(0f, lvx) * 7f) * ppm, vy0 = lvy * 13f * ppm;
            // predicted path, in the frame of the ground
            for (int i = 1; i <= 20; i++) {
                float t = i * 0.09f;
                float ax = playerX + vx * t, ay = (TOWER_TOP - SH_BOT) + vy0 * t + 0.5f * GRAVITY * ppm * t * t;
                if (ay > floorY - 2 || ax > w) break;
                rectPx(c, ax - 1, ay - 1, 2, 2, Color.argb(Math.max(60, 230 - i * 9), 255, 255, 255));
            }
            float ang = (float) Math.toDegrees(Math.atan2(-lvy, Math.max(0.05f, 1f + lvx)));
            float spd = (float) Math.sqrt(lvx * lvx + lvy * lvy) * 13f;
            int top = Math.round(ceilY) + 5;
            if (dragging) {
                text(c, "ANGLE " + Math.round(ang) + "   SPEED " + String.format("%.1f", spd) + " M/S", w / 2f, top, 1, Color.WHITE, 1);
            } else {
                text(c, "DRAG BACK AND RELEASE TO LAUNCH", w / 2f, top, 1, Color.rgb(255, 200, 80), 1);
                text(c, "A QUICK TAP REPEATS YOUR LAST LAUNCH", w / 2f, top + 9, 1, Color.rgb(150, 190, 215), 1);
            }
        }

        void drawTopDown(Canvas c) {
            rectPx(c, 0, 0, w, h, Color.rgb(20, 32, 28));
            for (int gx = 0; gx < w; gx += 16) rectPx(c, gx, 0, 1, h, Color.rgb(26, 42, 36));
            for (int gy = 0; gy < h; gy += 16) rectPx(c, 0, gy, w, 1, Color.rgb(26, 42, 36));
            int bx = Math.round(w * 0.46f), by = 16, bw = w - bx - 14, bh = h - 32;
            // fence
            int fence = Color.rgb(150, 150, 165);
            for (int x = bx - 8; x < bx + bw + 8; x += 4) { rectPx(c, x, by - 8, 2, 2, fence); rectPx(c, x, by + bh + 6, 2, 2, fence); }
            for (int yy = by - 8; yy < by + bh + 8; yy += 4) { rectPx(c, bx - 8, yy, 2, 2, fence); rectPx(c, bx + bw + 6, yy, 2, 2, fence); }
            // roof of the enemy lab
            rectPx(c, bx, by, bw, bh, Color.rgb(40, 50, 72));
            rectPx(c, bx + 2, by + 2, bw - 4, bh - 4, Color.rgb(68, 80, 106));
            int sw = (bw - 28) / 3;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 2; j++) {
                    int sx = bx + 8 + i * (sw + 6), sy = by + 8 + j * (bh - 44);
                    rectPx(c, sx, sy, sw, 14, Color.rgb(30, 70, 100));
                    rectPx(c, sx + 1, sy + 1, sw - 2, 12, Color.rgb(60, 140, 180));
                    rectPx(c, sx + 2 + ((int) (clock * 20) + i * 9 + j * 5) % Math.max(1, sw - 6), sy + 2, 3, 10, Color.argb(90, 255, 255, 255));
                }
            }
            text(c, "ENEMY LAB", bx + bw / 2f, by + bh / 2f - 4, 2, Color.rgb(205, 70, 70), 1);
            // the hatch you are breaking in through
            int hx = bx + 6, hy = by + bh / 2 + 10;
            rectPx(c, hx, hy, 14, 10, Color.rgb(30, 34, 46));
            rectPx(c, hx + 1, hy + 1, 12, 8, Color.rgb(90, 96, 110));
            if (((int) (clock * 3) & 1) == 0) rectPx(c, hx + 5, hy + 4, 4, 2, Color.rgb(255, 70, 60));
            // searchlights sweeping from two corners
            for (int k = 0; k < 2; k++) {
                float cx = k == 0 ? bx : bx + bw, cy = k == 0 ? by : by + bh;
                float a = clock * (k == 0 ? 0.9f : -0.8f) + k * 2.1f;
                path.reset();
                path.moveTo(cx, cy);
                path.lineTo(cx + (float) Math.cos(a - 0.18) * 80, cy + (float) Math.sin(a - 0.18) * 80);
                path.lineTo(cx + (float) Math.cos(a + 0.18) * 80, cy + (float) Math.sin(a + 0.18) * 80);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(46, 255, 245, 160));
                c.drawPath(path, p);
            }
            // patrolling scientists in white coats
            for (int i = 0; i < 5; i++) {
                float t = (clock * 0.07f + i * 0.2f) % 1f;
                float px0, py0;
                float per = t * 4f;
                int rx0 = bx + 6, ry0 = by + 6, rw = bw - 12, rh = bh - 12;
                if (per < 1f) { px0 = rx0 + rw * per; py0 = ry0; }
                else if (per < 2f) { px0 = rx0 + rw; py0 = ry0 + rh * (per - 1f); }
                else if (per < 3f) { px0 = rx0 + rw * (3f - per); py0 = ry0 + rh; }
                else { px0 = rx0; py0 = ry0 + rh * (4f - per); }
                rectPx(c, px0 - 2, py0 - 2, 5, 5, Color.rgb(8, 10, 20));
                rectPx(c, px0 - 1, py0 - 1, 3, 3, Color.rgb(245, 247, 252));
                rectPx(c, px0, py0, 1, 1, Color.rgb(110, 70, 40));
            }
            // your launch tower, seen from above, and the way in
            int tx = Math.round(w * 0.13f), ty = Math.round(h * 0.66f);
            rectPx(c, tx - 11, ty - 11, 22, 22, Color.rgb(46, 54, 70));
            rectPx(c, tx - 9, ty - 9, 18, 18, Color.rgb(112, 126, 150));
            rectPx(c, tx - 1, ty - 1, 3, 8, Color.rgb(120, 78, 44));
            rectPx(c, tx - 6, ty - 8, 3, 8, Color.rgb(160, 108, 64));
            rectPx(c, tx + 4, ty - 8, 3, 8, Color.rgb(160, 108, 64));
            rectPx(c, tx - 6, ty + 1, 13, 2, Color.rgb(120, 78, 44));
            int shown = 3 + ((int) (clock * 9) % 14);
            for (int i = 0; i < shown; i++) {
                float f = i / 16f;
                float ax = tx + 14 + (hx - tx - 18) * f, ay = ty + (hy + 5 - ty) * f - (float) Math.sin(f * Math.PI) * 8;
                rectPx(c, ax, ay, 2, 2, Color.argb(220, 255, 255, 255));
            }
            // briefing
            text(c, "THE ENEMY LAB STOLE", 10, 22, 1, Color.WHITE, 0);
            text(c, "THE FORMULA.", 10, 31, 1, Color.WHITE, 0);
            text(c, "BREAK IN.", 10, 44, 2, Color.rgb(255, 200, 80), 0);
            if (((int) (clock * 2) & 1) == 0) text(c, "TAP TO CONTINUE", 10, h - 16, 1, Color.rgb(150, 190, 215), 0);
        }

        void drawBackground(Canvas c) {
            int bands = 12;
            for (int i = 0; i < bands; i++) {
                float t = i / (float) (bands - 1);
                int col = Color.rgb((int) (14 + 10 * t), (int) (22 + 18 * t), (int) (40 + 22 * t));
                rectPx(c, 0, i * h / (float) bands, w, h / (float) bands + 1, col);
            }
            drawWall(c);
            // ceiling pipe with rivets
            rectPx(c, 0, 0, w, ceilY, Color.rgb(36, 50, 72));
            rectPx(c, 0, ceilY - 3, w, 3, Color.rgb(110, 240, 250));
            rectPx(c, 0, ceilY - 4, w, 1, Color.rgb(30, 100, 120));
            float off = (dist * ppm) % 24;
            for (int x = -24; x < w + 24; x += 24) rectPx(c, x - off, 3, 2, 2, Color.rgb(90, 110, 140));
        }

        // ---------- wall with rooms ----------

        static final int RW = 170, PERIOD = 230;
        static final String[] ROOM_NAMES = {"MISSION CONTROL", "CHEMISTRY LAB", "COMPUTER ROOM", "OBSERVATORY"};
        static final String[] DIG = new String[24];
        static {
            Random r = new Random(3);
            for (int i = 0; i < DIG.length; i++) {
                StringBuilder b = new StringBuilder();
                for (int k = 0; k < 6; k++) b.append((char) ('0' + r.nextInt(10)));
                DIG[i] = b.toString();
            }
        }

        void drawWall(Canvas c) {
            int wallTop = Math.round(ceilY), wallH = Math.round(floorY) - wallTop;
            rectPx(c, 0, wallTop, w, wallH, Color.rgb(30, 44, 66));
            float so = bgOff % 40;
            for (float x = -so; x < w; x += 40) rectPx(c, x, wallTop, 1, wallH, Color.rgb(24, 36, 56));
            // baseboard with hazard dashes
            rectPx(c, 0, floorY - 5, w, 5, Color.rgb(22, 30, 46));
            float ho = bgOff % 16;
            for (float x = -ho; x < w; x += 16) rectPx(c, x, floorY - 4, 8, 2, Color.rgb(200, 160, 40));

            int k0 = (int) Math.floor((bgOff - 20) / PERIOD) - 1;
            for (int k = k0; k < k0 + w / PERIOD + 3; k++) {
                float ox = k * PERIOD - bgOff + 20;
                if (ox > w || ox + RW < 0) continue;
                drawRoom(c, Math.round(ox), k);
            }
        }

        void drawRoom(Canvas c, int ox, int k) {
            int kind = Math.floorMod(k, 4);
            int oy = Math.round(ceilY) + 10, oh = Math.round(floorY) - oy;      // down to the floor line
            // name plate
            rectPx(c, ox + 25, ceilY + 2, RW - 50, 7, Color.rgb(16, 24, 38));
            text(c, ROOM_NAMES[kind], ox + RW / 2f, ceilY + 3, 1, Color.rgb(255, 200, 80), 1);
            // frame
            rectPx(c, ox - 3, oy - 3, RW + 6, oh + 6, Color.rgb(16, 24, 38));
            rectPx(c, ox - 2, oy - 2, RW + 4, oh + 4, Color.rgb(70, 90, 120));

            c.save();
            c.clipRect(ox, oy, ox + RW, oy + oh);
            switch (kind) {
                case 0: interiorControl(c, ox, oy, RW, oh, k); break;
                case 1: interiorLab(c, ox, oy, RW, oh); break;
                case 2: interiorComputer(c, ox, oy, RW, oh); break;
                default: interiorObservatory(c, ox, oy, RW, oh); break;
            }
            // roll-up shutter: opens as the player gets close
            float d = Math.abs(ox + RW / 2f - playerX);
            float open = Math.max(0f, Math.min(1f, 1f - (d - 50f) / 130f));
            open = open * open * (3f - 2f * open);
            int closed = Math.round(oh * (1f - open));
            if (closed > 0) {
                rectPx(c, ox, oy, RW, closed, Color.rgb(78, 92, 116));
                for (int yy = oy + 3; yy < oy + closed; yy += 4) rectPx(c, ox, yy, RW, 1, Color.rgb(52, 64, 86));
                rectPx(c, ox, oy + closed - 3, RW, 3, Color.rgb(200, 160, 40));
                for (int xx = ox; xx < ox + RW; xx += 8) rectPx(c, xx, oy + closed - 3, 4, 3, Color.rgb(30, 30, 40));
            }
            c.restore();
        }

        void interiorControl(Canvas c, int ox, int oy, int ow, int oh, int k) {
            int[] skin = {Color.rgb(240, 200, 160), Color.rgb(205, 160, 120), Color.rgb(150, 105, 75)};
            rectPx(c, ox, oy, ow, oh, Color.rgb(44, 62, 60));
            rectPx(c, ox, oy + oh - 6, ow, 6, Color.rgb(70, 78, 70));
            // big world map with a ground track
            int sx = ox + 52, sy = oy + 6;
            rectPx(c, sx - 2, sy - 2, 70, 40, Color.rgb(12, 16, 22));
            rectPx(c, sx, sy, 66, 36, Color.rgb(10, 30, 70));
            for (int gx = 0; gx < 66; gx += 11) rectPx(c, sx + gx, sy, 1, 36, Color.rgb(20, 50, 100));
            for (int gy = 0; gy < 36; gy += 9) rectPx(c, sx, sy + gy, 66, 1, Color.rgb(20, 50, 100));
            int land = Color.rgb(30, 100, 70);
            rectPx(c, sx + 6, sy + 8, 12, 10, land);
            rectPx(c, sx + 10, sy + 18, 6, 10, land);
            rectPx(c, sx + 28, sy + 6, 10, 7, land);
            rectPx(c, sx + 30, sy + 13, 10, 14, land);
            rectPx(c, sx + 46, sy + 8, 14, 8, land);
            for (int i = 0; i < 66; i += 2) rectPx(c, sx + i, sy + 18 + (float) Math.sin(i * 0.18) * 12, 1, 1, Color.rgb(255, 220, 90));
            int mi = (int) ((clock * 10) % 66);
            rectPx(c, sx + mi - 1, sy + 17 + (float) Math.sin(mi * 0.18) * 12, 3, 3, Color.WHITE);
            // flanking data screens
            rectPx(c, ox + 10, oy + 6, 34, 28, Color.rgb(10, 22, 18));
            int di = ((int) (clock * 3)) % 18;
            for (int i = 0; i < 4; i++) text(c, DIG[di + i % 3], ox + 12, oy + 8 + i * 6, 1, Color.rgb(90, 230, 120), 0);
            rectPx(c, ox + ow - 44, oy + 6, 34, 28, Color.rgb(10, 22, 18));
            for (int b = 0; b < 6; b++) {
                int bh = (int) (Math.abs(Math.sin(clock * 2 + b * 1.7)) * 18) + 2;
                rectPx(c, ox + ow - 42 + b * 5, oy + 32 - bh, 3, bh, Color.rgb(80, 230, 120));
            }
            // tiered rows of two-tone green consoles with controllers in white shirts and headsets
            for (int row = 0; row < 3; row++) {
                int ry = oy + 56 + row * 18;
                for (int i = 0; i < 5; i++) {
                    int cx = ox + 6 + i * 33 + (row == 1 ? 8 : 0);
                    if (cx + 24 > ox + ow) continue;
                    int hsh = k * 31 + row * 7 + i * 13;
                    if ((hsh & 3) != 0) {
                        int bob = (((int) (clock * 3) + hsh) & 7) == 0 ? 1 : 0;
                        rectPx(c, cx + 7, ry - 8 + bob, 10, 9, Color.rgb(240, 244, 250));
                        rectPx(c, cx + 9, ry - 14 + bob, 6, 6, skin[(hsh >> 2) % 3 < 0 ? 0 : (hsh >> 2) % 3]);
                        rectPx(c, cx + 9, ry - 14 + bob, 6, 2, HAIRS[(hsh >> 1) & 3]);
                        rectPx(c, cx + 8, ry - 13 + bob, 1, 4, Color.rgb(40, 40, 50));
                        rectPx(c, cx + 15, ry - 13 + bob, 1, 4, Color.rgb(40, 40, 50));
                    }
                    rectPx(c, cx, ry, 24, 4, Color.rgb(60, 120, 85));
                    rectPx(c, cx, ry + 4, 24, 9, Color.rgb(120, 170, 135));
                    rectPx(c, cx + 3, ry + 5, 7, 5, Color.rgb(15, 40, 25));
                    rectPx(c, cx + 3, ry + 5 + ((int) (clock * 8) + hsh) % 5, 7, 1, Color.rgb(90, 220, 120));
                    for (int b = 0; b < 5; b++) {
                        boolean on = (((int) (clock * 2) + hsh + b) & 3) == 0;
                        rectPx(c, cx + 12 + b * 2, ry + 6, 1, 3, on ? Color.rgb(255, 190, 60) : Color.rgb(70, 70, 55));
                    }
                }
            }
            // rocket-shaped coffee pot in the corner
            rectPx(c, ox + 4, oy + oh - 18, 3, 10, Color.rgb(235, 235, 240));
            rectPx(c, ox + 4, oy + oh - 20, 3, 2, Color.rgb(220, 70, 70));
            rectPx(c, ox + 3, oy + oh - 10, 5, 2, Color.rgb(220, 70, 70));
            // cigarette haze drifting up
            for (int i = 0; i < 6; i++) {
                float hy = oy + oh - ((clock * 6 + i * 17) % 80);
                float hx = ox + 20 + i * 26 + (float) Math.sin(clock + i) * 4;
                rectPx(c, hx, hy, 14, 2, Color.argb(28, 200, 200, 190));
            }
        }

        void interiorLab(Canvas c, int ox, int oy, int ow, int oh) {
            int[] liq = {Color.rgb(90, 230, 120), Color.rgb(255, 120, 200), Color.rgb(120, 200, 255), Color.rgb(255, 210, 80), Color.rgb(170, 120, 255)};
            rectPx(c, ox, oy, ow, oh, Color.rgb(60, 95, 105));
            for (int gx = 0; gx < ow; gx += 12) rectPx(c, ox + gx, oy, 1, oh, Color.rgb(50, 82, 92));
            // chart on the wall
            for (int r = 0; r < 4; r++) for (int q = 0; q < 8; q++)
                rectPx(c, ox + 12 + q * 6, oy + 8 + r * 6, 5, 5, ((q + r) & 1) == 0 ? Color.rgb(90, 150, 200) : Color.rgb(200, 150, 90));
            // shelf with jars
            rectPx(c, ox + 100, oy + 24, 60, 2, Color.rgb(110, 80, 50));
            for (int i = 0; i < 5; i++) rectPx(c, ox + 104 + i * 11, oy + 14, 7, 10, liq[(i + 2) % 5]);
            // bench
            int by = oy + oh - 24;
            rectPx(c, ox, by, ow, oh - 24, Color.rgb(110, 80, 50));
            rectPx(c, ox, by, ow, 3, Color.rgb(150, 115, 75));
            for (int i = 0; i < 6; i++) {
                int fx = ox + 10 + i * 26;
                int col = liq[i % 5];
                rectPx(c, fx + 3, by - 22, 4, 8, Color.rgb(200, 225, 235));
                rectPx(c, fx + 2, by - 14, 6, 3, Color.rgb(200, 225, 235));
                rectPx(c, fx, by - 11, 10, 11, Color.rgb(200, 225, 235));
                rectPx(c, fx + 1, by - 8, 8, 8, col);
                for (int b = 0; b < 2; b++) {
                    float yy = by - 4 - ((clock * 8 + i * 5 + b * 7) % 14);
                    rectPx(c, fx + 3 + b * 3, yy, 1, 1, Color.WHITE);
                }
            }
            // burner flame
            int fx2 = ox + ow - 14;
            rectPx(c, fx2, by - 4, 6, 4, Color.rgb(90, 90, 100));
            int fl = 4 + (int) (clock * 20) % 3;
            rectPx(c, fx2 + 1, by - 4 - fl, 4, fl, Color.rgb(90, 140, 255));
            rectPx(c, fx2 + 2, by - 4 - fl + 1, 2, fl - 1, Color.rgb(255, 220, 120));
        }

        void interiorComputer(Canvas c, int ox, int oy, int ow, int oh) {
            rectPx(c, ox, oy, ow, oh, Color.rgb(30, 36, 52));
            rectPx(c, ox, oy + oh - 6, ow, 6, Color.rgb(60, 62, 70));
            for (int i = 0; i < 4; i++) {
                int cx = ox + 4 + i * 41, cy = oy + 6, ch = oh - 14;
                rectPx(c, cx, cy, 38, ch, Color.rgb(150, 155, 168));
                rectPx(c, cx + 2, cy + 2, 34, ch - 4, Color.rgb(100, 106, 120));
                // blinking lamp rows
                for (int r = 0; r < 4; r++) for (int q = 0; q < 8; q++) {
                    boolean on = (((int) (clock * 5) + r * 3 + q * 5 + i) % 4) == 0;
                    rectPx(c, cx + 5 + q * 4, cy + ch - 24 + r * 5, 2, 2, on ? Color.rgb(255, 190, 60) : Color.rgb(60, 56, 50));
                }
                // two tape reels turning against each other
                for (int rl = 0; rl < 2; rl++) {
                    float rx = cx + 10 + rl * 18, ry = cy + 18;
                    float ang = clock * ((rl == 0) ? 4f : -3.2f) + i;
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.rgb(25, 25, 32));
                    c.drawCircle(rx, ry, 8, p);
                    p.setColor(Color.rgb(150, 100, 60));
                    c.drawCircle(rx, ry, 6, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1);
                    p.setColor(Color.rgb(25, 25, 32));
                    for (int s = 0; s < 3; s++) {
                        double a = ang + s * 2.0944;
                        c.drawLine(rx, ry, rx + (float) Math.cos(a) * 6, ry + (float) Math.sin(a) * 6, p);
                    }
                    p.setStyle(Paint.Style.FILL);
                }
                rectPx(c, cx + 10, cy + 27, 18, 1, Color.rgb(60, 40, 30));
            }
        }

        void interiorObservatory(Canvas c, int ox, int oy, int ow, int oh) {
            rectPx(c, ox, oy, ow, oh, Color.rgb(8, 10, 28));
            for (int i = 0; i < 34; i++) {
                int sx = (i * 37) % ow, sy = (i * 53) % (oh - 16);
                boolean bright = (((int) (clock * 3) + i) & 3) == 0;
                rectPx(c, ox + sx, oy + sy, 1, 1, bright ? Color.WHITE : Color.rgb(110, 120, 160));
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(225, 225, 205));
            c.drawCircle(ox + ow - 30, oy + 24, 10, p);
            p.setColor(Color.rgb(190, 190, 170));
            c.drawCircle(ox + ow - 33, oy + 21, 3, p);
            c.drawCircle(ox + ow - 26, oy + 28, 2, p);
            // dome walls either side of the slit
            rectPx(c, ox, oy, 22, oh, Color.rgb(52, 56, 70));
            rectPx(c, ox + ow - 22, oy, 22, oh, Color.rgb(52, 56, 70));
            rectPx(c, ox + 22, oy, 2, oh, Color.rgb(90, 96, 115));
            rectPx(c, ox + ow - 24, oy, 2, oh, Color.rgb(90, 96, 115));
            rectPx(c, ox, oy + oh - 10, ow, 10, Color.rgb(44, 46, 58));
            // telescope tube and mount
            float bx = ox + ow / 2f - 10, by = oy + oh - 10;
            path.reset();
            path.moveTo(bx - 6, by);
            path.lineTo(bx + 6, by);
            path.lineTo(bx + 36, oy + 22);
            path.lineTo(bx + 22, oy + 18);
            path.close();
            p.setColor(Color.rgb(170, 176, 192));
            c.drawPath(path, p);
            rectPx(c, bx + 20, oy + 16, 18, 6, Color.rgb(60, 66, 80));
            rectPx(c, bx - 3, by - 8, 6, 8, Color.rgb(110, 114, 130));
        }

        void drawFloor(Canvas c) {
            rectPx(c, 0, floorY, w, h - floorY, Color.rgb(30, 40, 58));
            rectPx(c, 0, floorY, w, 3, Color.rgb(110, 240, 250));
            rectPx(c, 0, floorY + 3, w, 1, Color.rgb(30, 100, 120));
            int m0 = (int) Math.floor(dist - playerX / ppm);
            for (int m = Math.max(0, m0); m < dist + (w - playerX) / ppm + 1; m++) {
                float x = playerX + (m - dist) * ppm;
                boolean ten = m % 10 == 0;
                rectPx(c, x, floorY + 4, 1, ten ? 5 : 3, Color.rgb(90, 150, 175));
                if (ten) text(c, m + " M", x, floorY + 11, 1, Color.rgb(140, 190, 210), 1);
            }
        }

        void drawNpcs(Canvas c) {
            int[] swing = {0, 1, 0, -1};
            for (Npc n : npcs) {
                int nx = Math.round(n.x);
                boolean walking = n.mode == 1 || n.mode == 2;
                boolean pointing = n.pointT > 0 && !walking;
                boolean lookUp = !walking && (pointing || n.mode == 0);
                boolean chatting = n.mode == 3 && !pointing;
                int wp = ((int) (clock * 6 + n.phase)) & 3;
                int sw = walking ? swing[wp] : 0;
                int f = walking ? (n.mode == 2 ? 1 : -1) : (lookUp ? (playerX >= n.x ? 1 : -1) : n.face);
                boolean hop = n.mode == 0 && ((int) (clock * 5 + n.phase) & 3) == 0;
                int base = Math.round(floorY) - (hop || (walking && (wp & 1) == 1) ? 1 : 0);
                int coat = COATS[n.coat];
                int skin = Color.rgb(240, 200, 160), shade = Color.rgb(170, 180, 200);
                rectPx(c, nx - 4, floorY - 1, 8, 1, Color.argb(70, 0, 0, 0));
                // legs and shoes (they scissor when walking)
                rectPx(c, nx - 2 + sw, base - 4, 2, 4, Color.rgb(50, 60, 100));
                rectPx(c, nx - sw, base - 4, 2, 4, Color.rgb(50, 60, 100));
                rectPx(c, nx - 2 + sw, base - 1, 2, 1, Color.rgb(30, 30, 40));
                rectPx(c, nx - sw, base - 1, 2, 1, Color.rgb(30, 30, 40));
                // coat
                rectPx(c, nx - 3, base - 10, 6, 6, coat);
                rectPx(c, nx - 3, base - 10, 1, 6, shade);
                // far arm swings with the walk
                rectPx(c, nx - f * 3 - sw * f, base - 9, 1, 4, shade);
                // head: looking up puts the lenses high; otherwise they sit level
                rectPx(c, nx - 2, base - 15, 5, 5, skin);
                rectPx(c, nx - 2, base - 15, 5, 2, HAIRS[n.hair]);
                int ey = lookUp ? base - 14 : base - 13;
                rectPx(c, nx + (f > 0 ? 0 : -1), ey, 1, 1, Color.rgb(120, 235, 255));
                rectPx(c, nx + (f > 0 ? 1 : 0), ey, 1, 1, Color.rgb(120, 235, 255));
                boolean talk = pointing || (chatting && ((int) (clock * 4 + n.phase) & 1) == 0);
                if (talk) rectPx(c, nx + (f > 0 ? 1 : 0), base - 12, 1, 1, Color.rgb(90, 50, 50));
                float sx = nx + f * 2, sy = base - 9;
                if (pointing) {
                    float ang = (float) Math.atan2(y - sy, playerX - sx);
                    float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                    for (int i = 0; i <= 4; i++) rectPx(c, sx + cs * i, sy + sn * i, 2, 2, coat);
                    rectPx(c, sx + cs * 5, sy + sn * 5, 2, 2, skin);
                } else if (chatting && ((int) (clock * 2 + n.phase) & 3) == 0) {
                    rectPx(c, sx + f, sy - 2, 1, 3, coat);      // gesturing mid conversation
                    rectPx(c, sx + f, sy - 3, 2, 2, skin);
                } else {
                    rectPx(c, sx + sw * f, sy, 1, 4, shade);
                }
            }
        }

        void drawCoins(Canvas c) {
            int[][] orbit = {{4, 0}, {0, 4}, {-4, 0}, {0, -4}};
            for (Coin k : coins) {
                int cx = Math.round(k.x), cy = Math.round(k.y);
                rectPx(c, cx - 3, cy - 1, 7, 3, Color.argb(90, 80, 220, 255));
                rectPx(c, cx - 1, cy - 3, 3, 7, Color.argb(90, 80, 220, 255));
                rectPx(c, cx - 1, cy - 1, 3, 3, Color.rgb(120, 235, 255));
                int[] o = orbit[((int) (clock * 8 + k.x * 0.05f) & 3)];
                rectPx(c, cx + o[0], cy + o[1], 1, 1, Color.WHITE);
            }
        }

        void drawZappers(Canvas c) {
            for (Zapper z : zappers) {
                float hx = (float) Math.cos(z.angle) * z.len / 2, hy = (float) Math.sin(z.angle) * z.len / 2;
                float ax = z.x - hx, ay2 = z.y - hy, bx = z.x + hx, by = z.y + hy;
                float nx = -hy / (z.len / 2), ny = hx / (z.len / 2);
                path.reset();
                path.moveTo(Math.round(ax), Math.round(ay2));
                int segs = 6;
                for (int i = 1; i < segs; i++) {
                    float t = i / (float) segs, j = (rnd.nextInt(5) - 2) * 1.5f;
                    path.lineTo(Math.round(ax + (bx - ax) * t + nx * j), Math.round(ay2 + (by - ay2) * t + ny * j));
                }
                path.lineTo(Math.round(bx), Math.round(by));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeJoin(Paint.Join.MITER);
                p.setStrokeWidth(4);
                p.setColor(Color.argb(70, 120, 200, 255));
                c.drawPath(path, p);
                p.setStrokeWidth(1);
                p.setColor(Color.rgb(230, 250, 255));
                c.drawPath(path, p);
                for (int e = 0; e < 2; e++) {
                    float nxp = e == 0 ? ax : bx, nyp = e == 0 ? ay2 : by;
                    rectPx(c, nxp - 4, nyp - 4, 8, 8, Color.rgb(90, 100, 120));
                    rectPx(c, nxp - 3, nyp - 3, 6, 6, Color.rgb(60, 70, 90));
                    rectPx(c, nxp - 2, nyp - 2, 4, 4, Color.rgb(255, 200, 80));
                }
            }
        }

        void drawMissiles(Canvas c) {
            for (Missile m : missiles) {
                if (!m.flying) {
                    if (((int) (m.warn * 8)) % 2 == 0) {
                        float wx = w - 12;
                        rectPx(c, wx - 6, m.y - 6, 12, 12, Color.rgb(255, 90, 60));
                        rectPx(c, wx - 5, m.y - 5, 10, 10, Color.rgb(160, 40, 30));
                        text(c, "!", wx - 1, m.y - 3, 1, Color.WHITE, 1);
                    }
                } else {
                    // alpha particle: two protons and two neutrons
                    int mx = Math.round(m.x), my = Math.round(m.y);
                    rectPx(c, mx + 4, my - 3, 10, 6, Color.argb(70, 255, 120, 70));
                    rectPx(c, mx + 14, my - 2, 8, 4, Color.argb(40, 255, 120, 70));
                    rectPx(c, mx - 4, my - 4, 4, 4, Color.rgb(255, 80, 70));
                    rectPx(c, mx, my, 4, 4, Color.rgb(255, 80, 70));
                    rectPx(c, mx, my - 4, 4, 4, Color.rgb(200, 205, 215));
                    rectPx(c, mx - 4, my, 4, 4, Color.rgb(200, 205, 215));
                }
            }
        }

        void drawParticles(Canvas c) {
            for (int i = 0; i < NP; i++) {
                if (pl[i] <= 0) continue;
                float a = Math.min(1f, pl[i] * 2f);
                int sz = a > 0.6f ? 3 : 2;
                rectPx(c, px[i], py[i], sz, sz, Color.argb((int) (a * 255), 255, state == DEAD ? 120 : (a > 0.6f ? 230 : 150), 60));
            }
        }

        static final int[] BOB = {0, 0, 0, 0};     // upper body holds still while running so the pixels stay put
        static final int[] LEAN = {0, 0, 0, 0};

        /**
         * Legs first, then the upper body. On the ground he shuffles (body dips, arm pumps).
         * Firing the pack he leans into it with his legs trailing and a small recoil buzz;
         * rising he tucks; falling he leans back and his legs dangle and kick.
         */
        void scientist(Canvas c, int left, int top, int ph, boolean run) {
            boolean thr = thrusting && state == PLAY && jumpDelay <= 0 && vehicle == 0;
            String[] legs, body = SPR_HEAD;
            float lean;
            int dy = 0, dx = 0;
            if (onFloor) {
                legs = ph == 0 ? LEGS_A : ph == 1 ? LEGS_B : ph == 2 ? LEGS_M : LEGS_C;
                lean = 0;
                if (run) {
                    dy = BOB[ph]; dx = LEAN[ph];
                    body = SPR_RUN[ph];
                    if (landT > 0) { dy = 2; legs = LEGS_M; }
                }
            } else if (thr) {
                legs = ((int) (clock * 10) & 1) == 0 ? LEGS_AIR : LEGS_AIR2;
                lean = 1;
                dy = ((int) (clock * 30)) & 1;
            } else if (vy * gravDir < 0) {
                legs = LEGS_AIR;
                lean = 0;
            } else {
                legs = ((int) (clock * 6) & 1) == 0 ? LEGS_FALL1 : LEGS_FALL2;
                lean = 0;
            }
            leanPx = 0;
            outlined(c, legs, left, top + SPR_HEAD.length);
            leanPx = lean;
            outlined(c, body, left + dx, top + dy);
            if ((clock % 2.8f) < 0.12f) sprite(c, SPR_BLINK, left + dx, top + dy);
            leanPx = 0;
        }

        void drawRagdoll(Canvas c) {
            if (settled && settleT >= 1f) {
                // face down on the floor: his own sprite, bobbing a pixel as he is dragged
                boolean sliding = speed > 0.3f;
                int bump = sliding && ((int) (clock * 14) & 1) == 0 ? 1 : 0;
                int left = Math.round(playerX) - 14, top = Math.round(floorY) - SPR_FLAT.length - bump;
                outlined(c, SPR_FLAT, left, top);
                if (!sliding) {
                    // dizzy stars circling his head
                    for (int i = 0; i < 3; i++) {
                        double a = clock * 5 + i * 2.094;
                        rectPx(c, left + 22 + (float) Math.cos(a) * 7, top - 2 + (float) Math.sin(a) * 3, 2, 2, Color.rgb(255, 230, 90));
                    }
                }
                return;
            }
            int left = Math.round(playerX) - 8, top = Math.round(y) - SH_TOP;
            boolean flop = ((int) (clock * 12) & 1) == 0;
            String[] legs = flop ? LEGS_AIR : LEGS_AIR2;
            c.save();
            c.rotate(ang, playerX, y);
            leanPx = 0;
            outlined(c, legs, left, top + SPR_HEAD.length);
            outlined(c, flop ? SPR_HEAD_B : SPR_HEAD, left, top);
            c.restore();
        }

        void drawPlayer(Canvas c) {
            if (state == DEAD) { drawRagdoll(c); return; }
            if (state == PLAY && invuln > 0 && ((int) (clock * 14) & 1) == 0) return;
            if (vehicle != 0 && state == PLAY) { drawVehicle(c); return; }
            if (state == BUILD && buildT > BUILD_TIME * 0.9f) return;

            float cyc = runPhase * 0.2f;
            int ph = ((int) (cyc * 4)) & 3;
            boolean thr = thrusting && state == PLAY && jumpDelay <= 0;
            float aimX = (state == LAUNCH && dragging) ? pdx : 0f, aimY = (state == LAUNCH && dragging) ? pdy : 0f;
            int left = Math.round(playerX + aimX) - 8, top = Math.round(y + aimY) - SH_TOP;

            if (thr) {
                int fl = 7 + rnd.nextInt(6);
                rectPx(c, left, top + 16, 6, fl, Color.rgb(255, 150, 50));
                rectPx(c, left + 1, top + 16, 4, Math.max(1, fl - 2), Color.rgb(255, 215, 90));
                rectPx(c, left + 2, top + 16, 2, Math.max(1, fl - 5), Color.rgb(255, 250, 220));
                rectPx(c, left + 1 + rnd.nextInt(4), top + 16 + fl, 1, 2, Color.rgb(255, 150, 50));
            }
            scientist(c, left, top, ph, onFloor && state == PLAY);
        }

        void quantumBody(Canvas c, int cy, boolean flip) {
            int left = Math.round(playerX) - 10, top = cy - 14;
            if (flip) { c.save(); c.scale(1, -1, playerX, cy); }
            int ph = ((int) (runPhase * 0.2f * 4)) & 3;
            scientist(c, left + 2, top, ph, true);
            outlined(c, BOARD, left, top + SPR_HEAD.length + LEGS_A.length);
            if (flip) c.restore();
        }

        void ring(Canvas c, float cx, float cy, float t) {
            int rr = (int) ((1f - t) * 18) + 2;
            int col = Color.argb((int) (t * 4 * 255 > 255 ? 255 : t * 4 * 255), 120, 235, 255);
            rectPx(c, cx - rr, cy - rr, rr * 2, 1, col);
            rectPx(c, cx - rr, cy + rr, rr * 2, 1, col);
            rectPx(c, cx - rr, cy - rr, 1, rr * 2, col);
            rectPx(c, cx + rr, cy - rr, 1, rr * 2 + 1, col);
        }

        void drawQuantum(Canvas c) {
            int cy = Math.round(y), gy = Math.round(ceilY + floorY - y);
            for (int yy = Math.min(cy, gy) + 14; yy < Math.max(cy, gy) - 14; yy += 4) {
                if (rnd.nextInt(5) > 0) rectPx(c, playerX, yy, 1, 2, Color.argb(120, 120, 235, 255));
            }
            ghost = true;
            quantumBody(c, gy, gravDir > 0);     // the entangled twin on the other side
            ghost = false;
            quantumBody(c, cy, gravDir < 0);
        }

        void drawVehicle(Canvas c) {
            if (vehicle == QUANTUM) { drawQuantum(c); return; }
            int left = Math.round(playerX) - 10;
            int hh = hhVeh(vehicle);
            int cy = Math.round(y);
            int top = cy - hh;
            boolean flip = gravDir < 0;
            if (flip) { c.save(); c.scale(1, -1, playerX, cy); }
            if (vehicle == STOMPER) {
                boolean comp = squash > 0;
                int off = comp ? 3 : 0;
                outlined(c, BODY_STOMP, left, top + off);
                outlined(c, comp ? LEGS_STOMP_C : LEGS_STOMP, left, top + BODY_STOMP.length + off);
                outlined(c, HEAD_ONLY, left, top + 2 + off);
            } else {
                outlined(c, POD, left, top);
                outlined(c, HEAD_ONLY, left, top + 3);
                // field sparks at both magnet caps
                rectPx(c, left + 6 + rnd.nextInt(8), top - 1 - rnd.nextInt(2), 1, 1, Color.rgb(255, 150, 150));
                rectPx(c, left + 6 + rnd.nextInt(8), top + 16 + rnd.nextInt(2), 1, 1, Color.rgb(150, 190, 255));
            }
            if (flip) c.restore();
        }

        void drawBuild(Canvas c) {
            String[] rows = vehRows(buildType, false);
            int hh = hhVeh(buildType);
            int left = Math.round(playerX) - 10, top = Math.round(y) - hh;
            for (int i = 0; i < nb; i++) {
                float t = Math.max(0f, Math.min(1f, (buildT - bdelay[i]) / 0.5f));
                float e = 1f - (1f - t) * (1f - t) * (1f - t);
                float tx = left + bcol[i], ty = top + brow[i];
                float cx = bsx[i] + (tx - bsx[i]) * e, cy = bsy[i] + (ty - bsy[i]) * e;
                if (t >= 1f && !bland[i]) {
                    bland[i] = true;
                    burst(tx + 1, ty + 1);
                }
                if (t < 1f) rectPx(c, cx - 1, cy - 1, 5, 5, Color.argb(70, 120, 220, 255));
                for (int rr = 0; rr < 3 && brow[i] + rr < rows.length; rr++) {
                    for (int cc = 0; cc < 3 && bcol[i] + cc < rows[0].length(); cc++) {
                        int col = pal(rows[brow[i] + rr].charAt(bcol[i] + cc));
                        if (col != 0) rectPx(c, cx + cc, cy + rr, 1, 1, col);
                    }
                }
            }
            if (buildT > BUILD_TIME * 0.9f && buildType != QUANTUM) {
                int headTop = top + (buildType == STOMPER ? 2 : 3);
                sprite(c, HEAD_ONLY, left, headTop);
            }
            text(c, "BUILDING", w / 2f, ceilY + 28, 1, Color.rgb(255, 200, 80), 1);
            text(c, VNAME[buildType], w / 2f, ceilY + 38, 1, Color.WHITE, 1);
            int bw = 60;
            rectPx(c, w / 2f - bw / 2f - 1, ceilY + 48, bw + 2, 5, Color.rgb(20, 30, 50));
            rectPx(c, w / 2f - bw / 2f, ceilY + 49, bw * Math.min(1f, buildT / BUILD_TIME), 3, Color.rgb(120, 235, 255));
        }

        void drawCrates(Canvas c) {
            for (Crate k : crates) {
                int cx = Math.round(k.x), cy = Math.round(k.y + (float) Math.sin(clock * 4 + k.x * 0.05f) * 2);
                int g = 30 + (int) (30 * Math.sin(clock * 6));
                rectPx(c, cx - 10, cy - 10, 20, 20, Color.argb(g, 120, 220, 255));
                rectPx(c, cx - 7, cy - 7, 14, 14, Color.rgb(30, 70, 140));
                rectPx(c, cx - 6, cy - 6, 12, 12, Color.rgb(60, 130, 210));
                rectPx(c, cx - 6, cy, 12, 1, Color.argb(120, 255, 255, 255));
                rectPx(c, cx, cy - 6, 1, 12, Color.argb(120, 255, 255, 255));
                text(c, "B", cx - 1, cy - 2, 1, Color.WHITE, 1);
            }
        }

        void drawHud(Canvas c) {
            int top = Math.round(ceilY) + 4;
            text(c, (int) (state == DEAD ? finalDist : dist) + " M", 6, top, 2, Color.WHITE, 0);
            text(c, "E- " + electrons, 6, top + 14, 1, Color.rgb(120, 235, 255), 0);
            int rx = w - 6;
            Color_ = Color.rgb(170, 200, 220);
            text(c, String.format("V %+.1f M/S", -vy), rx, top, 1, Color_, 2);
            text(c, String.format("A %+.1f M/S^2", -ay), rx, top + 8, 1, Color_, 2);
            text(c, "BEST " + best + " M", rx, top + 16, 1, Color_, 2);
            drawHint(c);
        }
        int Color_;

        void drawHint(Canvas c) {
            if (tipT > 0 && state == PLAY && vehicle == 0) {
                text(c, "HOLD TO FIRE THE JETPACK", w / 2f, ceilY + 28, 1, Color.WHITE, 1);
            }
            if (hintT > 0 && state == PLAY && vehicle != 0) {
                text(c, VNAME[vehicle], w / 2f, ceilY + 28, 1, Color.WHITE, 1);
                text(c, VHINT[vehicle], w / 2f, ceilY + 38, 1, Color.rgb(120, 235, 255), 1);
            }
        }

        void drawTitle(Canvas c) {
            text(c, "THRUST LAB", w / 2f, h * 0.26f, 4, Color.WHITE, 1);
            text(c, "BREAK INTO THE ENEMY LAB", w / 2f, h * 0.26f + 30, 1, Color.rgb(120, 235, 255), 1);
            if (((int) (clock * 2) & 1) == 0) text(c, "TAP TO START", w / 2f, h * 0.26f + 44, 1, Color.rgb(255, 200, 80), 1);
        }

        void drawDead(Canvas c) {
            float fade = Math.max(0f, Math.min(1f, (stateTime - 1.2f) / 0.4f));
            if (fade <= 0f) return;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb((int) (170 * fade), 8, 12, 24));
            c.drawRect(0, 0, w, h, p);
            if (stateTime < 1.5f) return;
            text(c, (int) finalDist + " M", w / 2f, h * 0.18f, 4, Color.WHITE, 1);
            text(c, electrons + " ELECTRONS   BEST " + best + " M", w / 2f, h * 0.18f + 30, 1, Color.rgb(120, 235, 255), 1);
            wrapText(c, fact, w / 2f, h * 0.18f + 50, 1, Color.rgb(210, 220, 235), (int) (w * 0.7f));
            if (((int) (clock * 2) & 1) == 0) {
                text(c, "TAP TO TRY AGAIN", w / 2f, h * 0.84f, 1, Color.rgb(255, 200, 80), 1);
            }
        }
    }
}
