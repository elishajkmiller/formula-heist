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

        static final int START = 0, PLAY = 1, DEAD = 2, BUILD = 3;
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
        boolean ghost;
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
            if (state == START) y = floorY - 0.62f * ppm;
        }

        void reset() {
            coins.clear(); zappers.clear(); missiles.clear(); crates.clear();
            for (int i = 0; i < NP; i++) pl[i] = 0;
            vehicle = 0; gravDir = 1; invuln = 0; flash = 0; shake = 0; hitstop = 0; squash = 0; hintT = 0;
            diving = false; nextCrate = 80;
            y = floorY - 0.62f * ppm;
            vy = 0; dist = 0; electrons = 0; spawnLeft = w * 0.6f;
            state = PLAY; stateTime = 0;
        }

        float hTop() { return vehicle == 0 ? 0.72f * ppm : hhVeh(vehicle); }

        float hBot() { return vehicle == 0 ? 0.62f * ppm : hhVeh(vehicle); }

        static int hhVeh(int v) { return v == STOMPER ? 10 : v == QUANTUM ? 12 : 6; }

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
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (state == START || (state == DEAD && stateTime > 0.7f)) reset();
                    if (state == PLAY) press();
                    thrusting = true;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    thrusting = false;
                    break;
            }
            return true;
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

            float dt = rdt;
            if (state == BUILD) {
                buildT += rdt;
                dt = rdt * 0.15f;                           // time slows while the vehicle assembles
                if (buildT >= BUILD_TIME) finishBuild();
            }
            if (invuln > 0) invuln -= rdt;
            if (hintT > 0) hintT -= rdt;
            if (squash > 0) squash -= rdt;
            if (telePulse > 0) telePulse -= rdt;
            boolean playing = state == PLAY || state == BUILD;
            float r = vehicle == 0 ? 0.3f * ppm : (vehicle == STOMPER ? 8f : 7f);

            // vertical motion: gravity, thrust (on foot only), speed limited
            if (jumpDelay > 0) jumpDelay -= dt;
            boolean fire = thrusting && state == PLAY && vehicle == 0 && jumpDelay <= 0;
            ay = GRAVITY * gravDir - (fire ? THRUST : 0);
            vy += ay * dt;
            float vmin = flips() ? -VFALL : -VMAX;
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

            if (state == START) {
                speed = 3f;
                bgOff += speed * ppm * dt * 0.25f;
                return;
            }
            if (state == DEAD) {
                speed = Math.max(0f, speed - 14f * dt);
                bgOff += speed * ppm * dt * 0.25f;
                return;
            }

            speed = 5f + Math.min(dist / 60f, 7f);
            float scroll = speed * ppm * dt;
            dist += speed * dt;
            bgOff += scroll * 0.25f;
            if (onFloor && (vehicle == 0 || vehicle == QUANTUM)) runPhase += speed * dt * 2.2f;

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
            int score = (int) dist;
            if (score > best) {
                best = score;
                prefs.edit().putInt("best", best).apply();
            }
            for (int i = 0; i < 30; i++) burst(playerX, y);
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
                px[k] = playerX - 0.33f * ppm; py[k] = y + 0.25f * ppm;
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
            ".......BBBBB....",
            "......BBBBBBBS..",
            "......BSSGCCSS..",
            "......SSSSSSSSs.",
            ".......SSSSSs...",
            "........SSss....",
            ".OOOOwwWWWVVW...",
            ".OooOwwWWWVTW...",
            ".OooOwwwwSSTW...",
            ".OooOwwwwwWTW...",
            ".OOOOWWWWWWTW...",
            ".OooOWWWWWWWW...",
            ".NNNNWWWWWWWW...",
            "..NN.wwwwwwww..."
        };
        static final String[] LEGS_A = {
            "....DDDD.DDDD...", "....DDDD.DDDD...", "....DDDD.DDDD...", "....DDDD.DDDD...", "....DDDD.DDDD...",
            "....KKKK.KKKKK..", "...KKKKK.KKKKKK."
        };
        static final String[] LEGS_M = {
            "....DDDDDDDD....", "....DDDDDDDD....", "....DDDDDDDD....", "....DDDDDDDD....", "....DDDDDDDD....",
            "....KKKKKKKK....", "...KKKKKKKKKK..."
        };
        static final String[] LEGS_B = {
            "....DDDDDDDD....", "...DDD....DDD...", "..DDD......DDD..", "..DDD......DDD..", "..DDD......DDD..",
            "..KKKK.....KKKK.", ".KKKKK.....KKKKK"
        };
        static final String[] LEGS_AIR = {
            "....DDDD.DDDD...", "...DDD...DDDD...", "..DDD....DDDD...", ".DDD.....DDDD...", ".KKK.....KKKK...",
            "................", "................"
        };

        static final String[] HEAD_ONLY = Arrays.copyOfRange(SPR_HEAD, 0, 6);

        static final String[] BODY_STOMP = {
            "......mmmmmmmm......",
            ".....mMMMMMMMMm.....",
            "....mMggggggggMm....",
            "....mMggggggggMm....",
            "....mMggggggggMm....",
            "....mMggggggggMm....",
            "....mMggggggggMm....",
            "....mMggggggggMm....",
            "....mMMMMMMMMMMm....",
            "...mRRRRRRRRRRRRm...",
            "...mRYRRRRRRRRYRm...",
            "...mRRRRRRRRRRRRm...",
            "....mmmmmmmmmmmm...."
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
            "...mMggggggggggMm...",
            "...mMggggggggggMm...",
            "...mMggggggggggMm...",
            "...mMggggggggggMm...",
            "...mMggggggggggMm...",
            "...mMggggggggggMm...",
            "....mmmmmmmmmmmm....",
            ".....UUUUUUUUUU.....",
            "......UUUUUUUU......"
        };

        static final String[] BOARD = {
            "..mMMMMMMMMMMMMMMm..",
            "...mmmmmmmmmmmmmm...",
            "..UU............UU.."
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
                case 'B': return Color.rgb(110, 70, 40);
                case 'S': return Color.rgb(240, 200, 160);
                case 'G': return Color.rgb(50, 60, 80);
                case 'C': return Color.rgb(120, 235, 255);
                case 'O': return Color.rgb(240, 120, 50);
                case 'o': return Color.rgb(170, 80, 40);
                case 'N': return Color.rgb(120, 125, 140);
                case 'W': return Color.rgb(240, 244, 250);
                case 'w': return Color.rgb(180, 190, 210);
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

        void sprite(Canvas c, String[] rows, int left, int top) {
            p.setStyle(Paint.Style.FILL);
            for (int r = 0; r < rows.length; r++) {
                String row = rows[r];
                for (int k = 0; k < row.length(); k++) {
                    int col = pal(row.charAt(k));
                    if (col == 0) continue;
                    p.setColor(ghost ? Color.argb(110, 120, 235, 255) : col);
                    c.drawRect(left + k, top + r, left + k + 1, top + r + 1, p);
                }
            }
        }

        @Override
        protected void onDraw(Canvas out) {
            if (w == 0 || bc == null) return;
            Canvas c = bc;
            drawBackground(c);
            drawFloor(c);
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
            drawHud(c);
            if (flash > 0) rectPx(c, 0, 0, w, h, Color.argb((int) Math.min(200, flash * 520), 255, 255, 255));
            if (state == START) drawTitle(c);
            if (state == DEAD) drawDead(c);
            out.save();
            if (shake > 0) out.translate((rnd.nextFloat() - 0.5f) * 2f * shake * unit, (rnd.nextFloat() - 0.5f) * 2f * shake * unit);
            out.drawBitmap(buf, null, dst, blit);
            out.restore();
        }

        void drawBackground(Canvas c) {
            int bands = 12;
            for (int i = 0; i < bands; i++) {
                float t = i / (float) (bands - 1);
                int col = Color.rgb((int) (14 + 10 * t), (int) (22 + 18 * t), (int) (40 + 22 * t));
                rectPx(c, 0, i * h / (float) bands, w, h / (float) bands + 1, col);
            }
            float step = 2.2f * ppm;
            int first = (int) Math.floor(bgOff / step);
            float frac = bgOff - first * step;
            for (int k = -1; k < w / step + 2; k++) {
                int idx = first + k;
                int m = Math.floorMod(idx * 7 + 3, 11);
                int tx = Math.round(k * step - frac + step / 2), ty = Math.round((1.4f + (m % 5) * 1.2f) * ppm);
                int half = 12;
                rectPx(c, tx - half, ty - half, half * 2, half * 2, Color.rgb(36, 62, 96));
                rectPx(c, tx - half + 1, ty - half + 1, half * 2 - 2, half * 2 - 2, Color.rgb(24, 42, 70));
                text(c, SYM[Math.floorMod(idx, SYM.length)], tx, ty - 2, 1, Color.rgb(90, 150, 200), 1);
            }
            // ceiling pipe with rivets
            rectPx(c, 0, 0, w, ceilY, Color.rgb(36, 50, 72));
            rectPx(c, 0, ceilY - 3, w, 3, Color.rgb(70, 190, 200));
            rectPx(c, 0, ceilY - 4, w, 1, Color.rgb(30, 100, 120));
            float off = (dist * ppm) % 24;
            for (int x = -24; x < w + 24; x += 24) rectPx(c, x - off, 3, 2, 2, Color.rgb(90, 110, 140));
        }

        void drawFloor(Canvas c) {
            rectPx(c, 0, floorY, w, h - floorY, Color.rgb(30, 40, 58));
            rectPx(c, 0, floorY, w, 3, Color.rgb(70, 190, 200));
            rectPx(c, 0, floorY + 3, w, 1, Color.rgb(30, 100, 120));
            int m0 = (int) Math.floor(dist - playerX / ppm);
            for (int m = Math.max(0, m0); m < dist + (w - playerX) / ppm + 1; m++) {
                float x = playerX + (m - dist) * ppm;
                boolean ten = m % 10 == 0;
                rectPx(c, x, floorY + 4, 1, ten ? 5 : 3, Color.rgb(90, 150, 175));
                if (ten) text(c, m + " M", x, floorY + 11, 1, Color.rgb(140, 190, 210), 1);
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

        void drawPlayer(Canvas c) {
            if (state == DEAD && stateTime < 0.2f) return;
            if (state == PLAY && invuln > 0 && ((int) (clock * 14) & 1) == 0) return;
            if (vehicle != 0 && state == PLAY) { drawVehicle(c); return; }
            if (state == BUILD && buildT > BUILD_TIME * 0.9f) return;

            float cyc = runPhase * 0.28f;
            int ph = ((int) (cyc * 4)) & 3;
            boolean thr = thrusting && state == PLAY && jumpDelay <= 0;
            int bob = (onFloor && state == PLAY && (ph & 1) == 1) ? -1 : 0;
            int left = Math.round(playerX) - 8, top = Math.round(y) - 11 + bob;

            if (thr) {
                int fl = 4 + rnd.nextInt(5);
                rectPx(c, left + 1, top + 14, 4, fl, Color.rgb(255, 170, 40));
                rectPx(c, left + 2, top + 14, 2, Math.max(1, fl - 3), Color.rgb(255, 240, 150));
            }
            sprite(c, SPR_HEAD, left, top);
            String[] legs = !onFloor ? LEGS_AIR : (ph == 0 ? LEGS_A : ph == 2 ? LEGS_B : LEGS_M);
            sprite(c, legs, left, top + SPR_HEAD.length);
        }

        void quantumBody(Canvas c, int cy, boolean flip) {
            int left = Math.round(playerX) - 10, top = cy - 12;
            if (flip) { c.save(); c.scale(1, -1, playerX, cy); }
            int ph = ((int) (runPhase * 0.28f * 4)) & 3;
            String[] legs = ph == 0 ? LEGS_A : ph == 2 ? LEGS_B : LEGS_M;
            sprite(c, SPR_HEAD, left + 2, top);
            sprite(c, legs, left + 2, top + SPR_HEAD.length);
            sprite(c, BOARD, left, top + SPR_HEAD.length + LEGS_A.length);
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
                sprite(c, BODY_STOMP, left, top + off);
                sprite(c, comp ? LEGS_STOMP_C : LEGS_STOMP, left, top + BODY_STOMP.length + off);
                sprite(c, HEAD_ONLY, left, top + 2 + off);
            } else {
                sprite(c, POD, left, top);
                sprite(c, HEAD_ONLY, left, top + 3);
                // field sparks at both magnet caps
                rectPx(c, left + 6 + rnd.nextInt(8), top - 1 - rnd.nextInt(2), 1, 1, Color.rgb(255, 150, 150));
                rectPx(c, left + 6 + rnd.nextInt(8), top + 12 + rnd.nextInt(2), 1, 1, Color.rgb(150, 190, 255));
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
            text(c, (int) dist + " M", 6, top, 2, Color.WHITE, 0);
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
            if (hintT > 0 && state == PLAY && vehicle != 0) {
                text(c, VNAME[vehicle], w / 2f, ceilY + 28, 1, Color.WHITE, 1);
                text(c, VHINT[vehicle], w / 2f, ceilY + 38, 1, Color.rgb(120, 235, 255), 1);
            }
        }

        void drawTitle(Canvas c) {
            text(c, "THRUST LAB", w / 2f, h * 0.26f, 4, Color.WHITE, 1);
            text(c, "TAP TO JUMP, THEN HOLD IN THE AIR TO FIRE THE JETPACK", w / 2f, h * 0.26f + 30, 1, Color.rgb(120, 235, 255), 1);
            if (((int) (clock * 2) & 1) == 0) text(c, "TAP TO START", w / 2f, h * 0.26f + 44, 1, Color.rgb(255, 200, 80), 1);
        }

        void drawDead(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(170, 8, 12, 24));
            c.drawRect(0, 0, w, h, p);
            text(c, (int) dist + " M", w / 2f, h * 0.18f, 4, Color.WHITE, 1);
            text(c, electrons + " ELECTRONS   BEST " + best + " M", w / 2f, h * 0.18f + 30, 1, Color.rgb(120, 235, 255), 1);
            wrapText(c, fact, w / 2f, h * 0.18f + 50, 1, Color.rgb(210, 220, 235), (int) (w * 0.7f));
            if (stateTime > 0.7f && ((int) (clock * 2) & 1) == 0) {
                text(c, "TAP TO TRY AGAIN", w / 2f, h * 0.84f, 1, Color.rgb(255, 200, 80), 1);
            }
        }
    }
}
