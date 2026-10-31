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
import android.graphics.Typeface;
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

@SuppressWarnings("deprecation")   // immersive flags, Vibrator and AudioTrack constructors still work on every phone we target
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

    /** a stolen formula, rolled up */
    static class Scroll { float x, y; }

    /** a base pair shot forward by the DNA helix */
    static class Bolt { float x, y; int kind; }

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
        final AudioTrack[] tr = new AudioTrack[10];
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
                tr[Game.S_SCRATCH] = mk(tone(2600, 1400, 35, 0.22f, 0.85f, false));
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

        /**
         * A short looping chiptune, made here so there is no audio file: A minor, 128 bpm, eight bars
         * of Am F C G twice. A square-wave arpeggio over a triangle bass and a noise hat.
         */
        static short[] song() {
            double beat = 60.0 / 128.0, step = beat / 4;
            int bars = 8, steps = bars * 16;
            int n = (int) (steps * step * RATE);
            float[] roots = {110.00f, 87.31f, 130.81f, 98.00f};
            float[][] arp = {{440.00f, 523.25f, 659.25f}, {349.23f, 440.00f, 523.25f}, {523.25f, 659.25f, 783.99f}, {392.00f, 493.88f, 587.33f}};
            int[] pattern = {0, 1, 2, 1, 0, 1, 2, 1, 2, 1, 0, 1, 2, 1, 0, 2};
            short[] d = new short[n];
            Random noise = new Random(5);
            for (int i = 0; i < n; i++) {
                double t = i / (double) RATE;
                int st = Math.min(steps - 1, (int) (t / step));
                int chord = (st / 16) % 4;
                double inStep = (t - st * step) / step;                 // 0..1 inside this 16th note
                // lead: the arpeggio
                double lf = arp[chord][pattern[st % 16] % 3];
                double lead = (((t * lf) % 1.0) < 0.25 ? 1 : -1) * Math.exp(-inStep * 3.2) * 0.12;
                // bass: a triangle on the root, plucked on every beat
                double bt = (t % beat) / beat;
                double bf = roots[chord];
                double ph = (t * bf) % 1.0;
                double bass = (ph < 0.5 ? ph * 4 - 1 : 3 - ph * 4) * Math.exp(-bt * 2.2) * 0.3;
                // hat: a tiny noise tick on the off 8ths
                double hat = (st % 2 == 1 ? (noise.nextDouble() * 2 - 1) * Math.exp(-inStep * 12) * 0.05 : 0);
                double v = lead + bass + hat;
                d[i] = (short) (Math.max(-1, Math.min(1, v)) * 32767 * 0.6);
            }
            return d;
        }

        AudioTrack music;
        boolean musicWanted;

        /** start or pause the background music; the track is built the first time on the audio thread */
        void setMusic(final boolean on) {
            musicWanted = on;
            try {
                io.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (on) {
                                if (music == null) {
                                    short[] d = song();
                                    music = mk(d);
                                    music.setLoopPoints(0, d.length, -1);
                                    music.setVolume(0.55f);
                                }
                                if (musicWanted) music.play();
                            } else if (music != null) {
                                music.pause();
                            }
                        } catch (Throwable t) { }
                    }
                });
            } catch (Throwable t) { }
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
                        try { if (music != null) music.release(); } catch (Throwable t) { }
                        music = null;
                    }
                });
                io.shutdown();
            } catch (Throwable t) { }
        }
    }

    /**
     * The whole game in one View: physics, drawing and input.
     *
     * Layout of this class, top to bottom: constants and text data, game state, setup and touch input,
     * the formula archive and prism helpers, the update loop (physics, vehicles, spawning), then all
     * drawing (world, the yard and wall, rooms, player, HUD and screens) and the pixel font.
     * Everything is drawn at 144 pixels tall into a small bitmap that is scaled up with no smoothing.
     */
    static class Game extends View implements Choreographer.FrameCallback {

        // ---------- constants ----------

        static final int START = 0, PLAY = 1, DEAD = 2, BUILD = 3, ARCHIVE = 4, SCRATCH = 5, STORE = 6, SETTINGS = 7;
        static final int STOMPER = 1, INVERTER = 2, QUANTUM = 3, HELIX = 4;
        static final int NF = 20;                // formulas to recover
        static final float BUILD_TIME = 1.5f;    // seconds a vehicle takes to assemble
        static final int ENTANGLER_USES = 5;     // teleports before the Quantum Entangler burns out
        static final int S_JUMP = 0, S_COIN = 1, S_HIT = 2, S_STOMP = 3, S_BUILD = 4, S_FLIP = 5, S_WARN = 6, S_DONE = 7, S_TELE = 8, S_SCRATCH = 9;

        static final float GRAVITY = 22f;        // m/s^2, game gravity (Earth is 9.8)
        static final float THRUST = 38f;         // m/s^2 from the pack, net 16 up
        static final float VFALL = 14f;          // m/s, falling top speed
        static final float VMAX = 7f;            // m/s, "terminal" speed going up
        static final float JUMP = 8.5f;          // m/s, jump off the floor

        static final String[] VNAME = {"", "SPRING STOMPER", "GRAVITY INVERTER", "QUANTUM ENTANGLER", "DNA HELIX"};
        static final String[] VHINT = {"", "BOUNCES BY ITSELF. TAP IN THE AIR TO STOMP.", "TAP TO FLIP GRAVITY.", "TAP TO TELEPORT TO YOUR TWIN. FIVE USES.", "HOLD TO RISE. TAP TO FIRE BASE PAIRS."};

        static final int[] COATS = {Color.rgb(235, 240, 248), Color.rgb(190, 215, 240), Color.rgb(205, 205, 215)};
        static final int[] HAIRS = {Color.rgb(110, 70, 40), Color.rgb(30, 30, 40), Color.rgb(170, 170, 180), Color.rgb(190, 90, 50)};

        // ---------- clothes store: shirts, pants and jetpacks, each bought and worn on its own ----------

        static final String[] TABS = {"SHIRTS", "PANTS", "JETPACKS"};
        static final String[][] INAME = {
            {"LAB COAT", "FIRE RED", "OCEAN BLUE", "NIGHT SHIFT", "GOLD RUSH", "PINK STORM", "HAZMAT", "STEALTH"},
            {"DEFAULT", "BLACK", "KHAKI", "LIGHT JEANS", "RED", "GREEN", "PURPLE", "GOLD"},
            {"RED ORANGE", "OLIVE", "SILVER", "CYAN GLOW", "BLACK", "GOLD", "PINK", "RAINBOW"}
        };
        static final int[][] ICOST = {      // electrons
            {0, 25, 25, 40, 40, 60, 60, 100},
            {0, 15, 15, 25, 25, 40, 40, 60},
            {0, 20, 30, 40, 50, 70, 90, 150}
        };
        /** the sprite letters each kind of item recolours, in the order of its colours */
        static final char[][] IKEYS = {{'W', 'w', 'x', 't', 'V'}, {'D', 'd'}, {'O', 'o', 'h'}};
        static final int[][][] IPAL = {     // null means the plain colours drawn in pal()
            {
                null,
                new int[] {Color.rgb(220, 70, 60), Color.rgb(170, 45, 45), Color.rgb(130, 35, 40), Color.rgb(255, 220, 80), Color.rgb(235, 235, 240)},
                new int[] {Color.rgb(90, 160, 240), Color.rgb(60, 120, 200), Color.rgb(45, 90, 160), Color.rgb(255, 255, 255), Color.rgb(200, 230, 255)},
                new int[] {Color.rgb(70, 72, 86), Color.rgb(50, 52, 64), Color.rgb(38, 40, 52), Color.rgb(120, 235, 255), Color.rgb(160, 170, 190)},
                new int[] {Color.rgb(245, 205, 90), Color.rgb(205, 160, 55), Color.rgb(165, 120, 40), Color.rgb(120, 40, 40), Color.rgb(255, 245, 220)},
                new int[] {Color.rgb(255, 140, 190), Color.rgb(215, 100, 150), Color.rgb(170, 75, 120), Color.rgb(255, 255, 255), Color.rgb(255, 230, 245)},
                new int[] {Color.rgb(235, 225, 70), Color.rgb(195, 185, 45), Color.rgb(150, 140, 35), Color.rgb(40, 40, 40), Color.rgb(235, 225, 70)},
                new int[] {Color.rgb(40, 40, 46), Color.rgb(28, 28, 34), Color.rgb(20, 20, 26), Color.rgb(200, 50, 50), Color.rgb(60, 62, 70)}
            },
            {
                null,
                new int[] {Color.rgb(28, 28, 34), Color.rgb(46, 46, 56)},
                new int[] {Color.rgb(150, 130, 90), Color.rgb(185, 165, 120)},
                new int[] {Color.rgb(70, 110, 170), Color.rgb(100, 140, 200)},
                new int[] {Color.rgb(150, 40, 50), Color.rgb(190, 70, 80)},
                new int[] {Color.rgb(40, 100, 60), Color.rgb(70, 140, 90)},
                new int[] {Color.rgb(90, 50, 120), Color.rgb(125, 80, 155)},
                new int[] {Color.rgb(190, 150, 50), Color.rgb(225, 185, 80)}
            },
            {
                null,
                new int[] {Color.rgb(120, 130, 100), Color.rgb(80, 90, 70), Color.rgb(165, 175, 140)},
                new int[] {Color.rgb(175, 180, 195), Color.rgb(120, 125, 145), Color.rgb(225, 230, 245)},
                new int[] {Color.rgb(80, 210, 230), Color.rgb(40, 140, 170), Color.rgb(180, 245, 255)},
                new int[] {Color.rgb(60, 60, 70), Color.rgb(32, 32, 40), Color.rgb(100, 100, 118)},
                new int[] {Color.rgb(235, 190, 70), Color.rgb(180, 135, 40), Color.rgb(255, 230, 130)},
                new int[] {Color.rgb(240, 120, 180), Color.rgb(185, 80, 135), Color.rgb(255, 175, 215)},
                null
            }
        };
        static final int RAINBOW_PACK = 7;
        static final int[] OVR = new int[128];     // the worn items' colours, looked up first by pal()

        // ---------- text data: death-card facts and the 20 formulas ----------

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

        /** the formulas as they are written in a textbook, drawn with a real font so they are easy to read */
        static final String[] FORMD = {
            "F = ma", "E = mc\u00b2", "V = IR", "PV = nRT", "v = u + at", "F = G m1 m2 / r\u00b2", "E = hf", "S = k ln W",
            "\u0394x \u0394p \u2265 \u0127/2", "F = \u2212kx", "KE = \u00bd m v\u00b2", "PE = m g h", "v = f \u03bb",
            "n1 sin \u03b81 = n2 sin \u03b82", "F = k q1 q2 / r\u00b2", "p = m v", "W = F d", "P = E / t",
            "T = 2\u03c0 \u221a(L/g)", "a = v\u00b2 / r"
        };
        static final String[] FNAME = {
            "NEWTON'S SECOND LAW",
            "MASS-ENERGY",
            "OHM'S LAW",
            "IDEAL GAS LAW",
            "CONSTANT ACCELERATION",
            "UNIVERSAL GRAVITATION",
            "PLANCK RELATION",
            "ENTROPY",
            "UNCERTAINTY",
            "HOOKE'S LAW",
            "KINETIC ENERGY",
            "POTENTIAL ENERGY",
            "WAVE SPEED",
            "SNELL'S LAW",
            "COULOMB'S LAW",
            "MOMENTUM",
            "WORK",
            "POWER",
            "PENDULUM",
            "CENTRIPETAL ACCELERATION"
        };
        static final String[] FDESC = {
            "PUSH HARDER AND A THING SPEEDS UP MORE. HEAVIER THINGS SPEED UP LESS.",
            "A TINY BIT OF MASS HOLDS A HUGE AMOUNT OF ENERGY.",
            "VOLTAGE EQUALS CURRENT TIMES RESISTANCE. IT IS WHY WIRES GET WARM.",
            "SQUEEZE A GAS AND ITS PRESSURE OR ITS TEMPERATURE GOES UP.",
            "YOUR SPEED AFTER ACCELERATING FOR A WHILE IS YOUR START SPEED PLUS A TIMES T.",
            "EVERYTHING PULLS ON EVERYTHING ELSE, WEAKER WITH DISTANCE SQUARED.",
            "LIGHT COMES IN PACKETS. HIGHER FREQUENCY MEANS MORE ENERGY PER PACKET.",
            "DISORDER GROWS BECAUSE THERE ARE MORE MESSY WAYS TO BE THAN TIDY ONES.",
            "YOU CANNOT KNOW EXACTLY WHERE A PARTICLE IS AND HOW FAST IT IS GOING.",
            "A SPRING PUSHES BACK IN PROPORTION TO HOW FAR YOU STRETCH IT.",
            "DOUBLE THE SPEED AND YOU QUADRUPLE THE ENERGY.",
            "LIFT SOMETHING HIGHER AND YOU STORE MORE ENERGY IN IT.",
            "THE SPEED OF A WAVE IS ITS FREQUENCY TIMES ITS WAVELENGTH.",
            "THIS IS HOW LIGHT BENDS WHEN IT GOES INTO WATER OR GLASS.",
            "LIKE CHARGES PUSH APART AND OPPOSITE CHARGES PULL TOGETHER.",
            "MASS TIMES VELOCITY. IT IS CONSERVED IN EVERY COLLISION.",
            "WORK IS THE FORCE TIMES THE DISTANCE IT MOVES SOMETHING.",
            "POWER IS HOW FAST ENERGY IS USED: ENERGY DIVIDED BY TIME.",
            "A LONGER PENDULUM SWINGS MORE SLOWLY.",
            "TO GO IN A CIRCLE YOU MUST ACCELERATE TOWARD THE CENTER."
        };

        // ---------- text drawn at screen resolution (the formulas) ----------

        static class Ov { String t; float x, y, size; int col; boolean clip; }
        final ArrayList<Ov> ovs = new ArrayList<>();
        final Paint ovp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path clipPath = new Path();

        void ov(String t, float gx, float gy, float gsize, int col, boolean clip) {
            Ov o = new Ov();
            o.t = t; o.x = gx; o.y = gy; o.size = gsize; o.col = col; o.clip = clip;
            ovs.add(o);
        }

        /** the biggest text size, in game pixels, that fits the width */
        float fitSize(String t, float maxW, float maxSize) {
            ovp.setTextSize(maxSize * unit);
            float wpx = ovp.measureText(t);
            return wpx > maxW * unit ? maxSize * maxW * unit / wpx : maxSize;
        }

        // ---------- game state ----------

        // drawing
        final Random rnd = new Random();
        final Paint p = new Paint();                 // pixel paint, no smoothing
        final Paint blit = new Paint();              // scales the small bitmap up
        final Path path = new Path();
        final RectF dst = new RectF();
        Bitmap buf;
        Canvas bc;
        int w, h;                                    // game size in pixels, h is always 144
        float ppm, floorY, ceilY, playerX, unit = 1f;   // pixels per metre, edges, screen pixels per game pixel
        float clock, flash, shake, hitstop;          // time, screen flash, screen shake, brief freeze on a big hit
        final SharedPreferences prefs;

        // world
        final ArrayList<Coin> coins = new ArrayList<>();
        final ArrayList<Zapper> zappers = new ArrayList<>();
        final ArrayList<Missile> missiles = new ArrayList<>();
        final ArrayList<Crate> crates = new ArrayList<>();
        final ArrayList<Scroll> scrolls = new ArrayList<>();
        final ArrayList<Bolt> bolts = new ArrayList<>();
        final ArrayList<Npc> npcs = new ArrayList<>();
        float bgOff, lastBg, npcLeft, spawnLeft, nextCrate, nextParch;

        // particles
        static final int NP = 140;
        final float[] px = new float[NP], py = new float[NP], pvx = new float[NP], pvy = new float[NP], pl = new float[NP];
        final int[] pcol = new int[NP];              // 0 means the default spark colour
        int pNext;

        // the run
        int state = START;
        boolean running, thrusting, onFloor;
        float y, vy, ay, speed, dist, runPhase, stateTime;
        float jumpDelay, landT, leanPx;
        boolean ghost, outline;                      // sprite draw modes: translucent twin, dark outline
        int electrons, best;
        String fact = FACTS[0];
        long lastNanos;

        // outside, the wall and the break-in
        boolean breaking, holeOpen;                  // breaking: still outside, running at the wall
        float outX, holeY, bonus, tipT;

        // vehicles
        int vehicle, gravDir = 1, charges;       // charges: teleports left on the Entangler
        boolean diving;
        float invuln, squash, hintT, teleFromY, telePulse;
        static final int NB = 64;                    // pixel blocks that fly in to build a vehicle
        final int[] brow = new int[NB], bcol = new int[NB];
        final float[] bdelay = new float[NB], bsx = new float[NB], bsy = new float[NB];
        final boolean[] bland = new boolean[NB];
        int nb, buildType;
        float buildT;

        // DNA helix: where the head has been, so the body can follow it
        final float[] yHist = new float[64];
        int yh;
        float boltCd;
        static final int[][] RUNG = {{0xDC4646, 0x4678E6}, {0x50DC78, 0xF0C83C}, {0x4678E6, 0xDC4646}, {0xF0C83C, 0x50DC78}};   // A-T, C-G pairs

        // death
        boolean settled;
        int bounces;
        float ang, angV, settleT, finalDist;

        // clothes store
        int bank, storeFrom, storeTab, sbx, sby, sbw, sbh;       // bank: electrons kept across runs
        final int[] inv = {1, 1, 1}, worn = {0, 0, 0};           // per tab: owned items (bit mask) and the item being worn
        float storeMsgT;
        String storeMsg = "";

        // formula archive
        int ownedMask;                               // bit i set: formula i is yours
        int tickets, pending = -1;                   // scratch tickets in hand, and the formula under a ticket you opened but did not finish
        int revealKind;                              // 0 viewing, 1 a new formula, 2 a duplicate (paid in electrons)
        int tbx, tby, tbw, tbh;                      // the scratch-a-ticket button in the archive
        static final int DUPLICATE_PAY = 10;
        int archFrom, selCard, mcols, mrows, cleared;
        boolean selScratched, dragging;
        final boolean[] mask = new boolean[2000];    // scratched-off cells of the open card
        float bannerT;
        int abx, aby, abw, abh, cbx, cby, cbw, cbh, scx, scy, scw, sch;   // buttons and the big card

        // settings
        boolean hapticsOn = true, soundOn = true, musicOn = true, confirmReset;
        int settingsFrom, gbx, gby, gbw, gbh;        // the settings button on the title and death screens

        // sound and haptics
        Sfx sfx;
        Vibrator vib;
        float lastCoinSfx, lastScratchSfx;

        void saveStore() {
            prefs.edit().putInt("bank", bank).putInt("inv0", inv[0]).putInt("inv1", inv[1]).putInt("inv2", inv[2])
                    .putInt("worn0", worn[0]).putInt("worn1", worn[1]).putInt("worn2", worn[2]).apply();
        }

        /** the jetpack colours of pack i; the rainbow one cycles through the hues */
        int[] packColors(int i) {
            if (i != RAINBOW_PACK) return IPAL[2][i];
            float hue = (clock * 90f) % 360f;
            return new int[] {Color.HSVToColor(new float[] {hue, 0.85f, 1f}), Color.HSVToColor(new float[] {hue, 0.9f, 0.6f}), Color.HSVToColor(new float[] {hue, 0.45f, 1f})};
        }

        /** make the sprites use what is worn (item 0 of each tab is the plain colours) */
        void applyOutfit() {
            java.util.Arrays.fill(OVR, 0);
            for (int t = 0; t < 3; t++) {
                int[] cols = t == 2 && worn[2] == RAINBOW_PACK ? packColors(worn[2]) : IPAL[t][worn[t]];
                if (cols == null) continue;
                for (int i = 0; i < IKEYS[t].length; i++) OVR[IKEYS[t][i]] = cols[i];
            }
        }

        Game(Context c) {
            super(c);
            prefs = c.getSharedPreferences("jetlab", Context.MODE_PRIVATE);
            best = prefs.getInt("best", 0);
            if (prefs.contains("formulas")) {
                ownedMask = prefs.getInt("formulas", 0);
                tickets = prefs.getInt("tickets", 0);
                pending = prefs.getInt("pending", -1);
            } else {                                    // older saves: recovered cards that were not scratched yet become tickets
                int revealed = prefs.getInt("scratched", 0);
                ownedMask = revealed;
                tickets = Integer.bitCount(prefs.getInt("owned", 0) & ~revealed);
            }
            bank = prefs.getInt("bank", 0);
            hapticsOn = prefs.getBoolean("haptics", true);
            soundOn = prefs.getBoolean("sound", true);
            musicOn = prefs.getBoolean("music", true);
            if (prefs.contains("inv0")) {
                for (int t = 0; t < 3; t++) { inv[t] = prefs.getInt("inv" + t, 1) | 1; worn[t] = prefs.getInt("worn" + t, 0); }
            } else {                                    // earlier whole outfits become shirts
                inv[0] = prefs.getInt("outfits", 1) | 1;
                worn[0] = prefs.getInt("outfit", 0);
            }
            applyOutfit();
            p.setAntiAlias(false);
            blit.setFilterBitmap(false);
            ovp.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            ovp.setTextAlign(Paint.Align.CENTER);

        }

        void start() {
            if (running) return;
            running = true;
            lastNanos = 0;
            if (sfx == null) sfx = new Sfx();
            sfx.setMusic(musicOn);
            if (vib == null) vib = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            Choreographer.getInstance().postFrameCallback(this);
        }

        void stop() {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
            thrusting = false;
            saveStore();
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
            abw = 76; abh = 11; abx = w - abw - 4; aby = h - 12;
            sbw = 62; sbh = 11; sbx = 4; sby = h - 12;
            tbw = 124; tbh = 12; tbx = 8; tby = h - 14;
            gbw = 52; gbh = 11; gbx = (w - gbw) / 2; gby = h - 12;
            cbw = 36; cbh = 11; cbx = w - cbw - 4; cby = 3;
            scw = Math.min(200, w - 24); sch = 104; scx = (w - scw) / 2; scy = 24;
            if (state == START) toStart(true);
            populateNpcs();
        }

        void populateNpcs() {
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
            electrons = 0;
            toStart(true);
            state = START;      // stand outside the wall again and wait for a tap
            stateTime = 0;
        }

        /** back outside on the grass, in front of NULLCORP's wall */
        void toStart(boolean keepState) {
            coins.clear(); zappers.clear(); missiles.clear(); crates.clear(); scrolls.clear(); bolts.clear();
            nextParch = 70; bannerT = 0;
            for (int i = 0; i < NP; i++) pl[i] = 0;
            vehicle = 0; gravDir = 1; invuln = 0; flash = 0; shake = 0; hitstop = 0; squash = 0; hintT = 0;
            diving = false; nextCrate = 80; thrusting = false;
            settled = false; settleT = 0; ang = 0; angV = 0;
            outX = Math.min(playerX + 110f, w - 40f); holeOpen = false;
            bgOff = 20f - (outX + 10f); lastBg = bgOff;     // mission control sits just inside the wall
            y = floorY - SH_BOT; vy = 0; dist = 0; spawnLeft = w * 0.6f; speed = 0;
            dragging = false; bonus = 0; tipT = 0; onFloor = true;
            populateNpcs();
            breaking = false;
            if (!keepState) { state = PLAY; breaking = true; tipT = 4f; }
            stateTime = 0;
        }

        static final int SH_TOP = 11, SH_BOT = 12;     // scientist sprite rows above and below his centre

        float hTop() { return vehicle == 0 ? SH_TOP : hhVeh(vehicle); }

        float hBot() { return vehicle == 0 ? SH_BOT : hhVeh(vehicle); }

        static int hhVeh(int v) { return v == STOMPER ? 12 : v == QUANTUM ? 14 : 8; }

        /** vehicles that stick to a surface and switch which one gravity points at */
        boolean flips() { return vehicle == INVERTER || vehicle == QUANTUM; }

        void sfx(int id) { if (soundOn && sfx != null) sfx.play(id); }

        void buzz(int ms) {
            try {
                if (hapticsOn && vib != null) vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } catch (Throwable t) { }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float gx = e.getX() / unit, gy = e.getY() / unit;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (settingsButtonHit(gx, gy)) {
                        settingsFrom = state; state = SETTINGS; confirmReset = false; thrusting = false;
                    } else if (state == SETTINGS) {
                        settingsTap(gx, gy);
                    } else if (storeButtonHit(gx, gy)) {
                        storeFrom = state; state = STORE; thrusting = false;
                    } else if (state == STORE) {
                        storeTap(gx, gy);
                    } else if (archiveButtonHit(gx, gy)) {
                        archFrom = state; state = ARCHIVE; dragging = false; thrusting = false;
                    } else if (state == START) {
                        state = PLAY; breaking = true; stateTime = 0; tipT = 4f;
                    } else if (state == ARCHIVE) {
                        archiveTap(gx, gy);
                    } else if (state == SCRATCH) {
                        scratchDown(gx, gy);
                    } else if (state == DEAD) {
                        if (stateTime > 0.7f) reset();
                    } else if (state == PLAY) {
                        press();
                        thrusting = true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (state == SCRATCH && dragging) scratchAt(gx, gy);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    thrusting = false;
                    if (state == SCRATCH) dragging = false;
                    break;
            }
            return true;
        }

        /** he hit the wall at whatever height he was: bricks fly, a hole opens and the run goes on inside */
        void breach() {
            holeOpen = true; holeY = y;
            breaking = false; stateTime = 0; spawnLeft = w * 0.8f;
            bonus = Math.max(0f, speed - 5f);
            tipT = 3f; invuln = 0.8f;
            hitstop = 0.08f; shake = 7f; flash = 0.15f;
            sfx(S_HIT);
            buzz(50);
            for (int i = 0; i < 40; i++) debris(playerX + 8, y);
        }

        /** a random formula; ones you do not have yet are three times as likely, so repeats are possible but rarer */
        int rollFormula() {
            int total = 0;
            for (int i = 0; i < NF; i++) total += (ownedMask >> i & 1) == 0 ? 3 : 1;
            int r = rnd.nextInt(total);
            for (int i = 0; i < NF; i++) {
                r -= (ownedMask >> i & 1) == 0 ? 3 : 1;
                if (r < 0) return i;
            }
            return 0;
        }

        boolean ticketReady() { return tickets > 0 || pending >= 0; }

        void saveFormulas() {
            prefs.edit().putInt("formulas", ownedMask).putInt("tickets", tickets).putInt("pending", pending).putInt("bank", bank).apply();
        }

        /** a scroll on the track is a scratch ticket: you find out what is under it when you scratch it */
        void collectScroll() {
            tickets++;
            saveFormulas();
            bannerT = 1.2f;
            flash = 0.12f;
            sfx(S_DONE);
            buzz(30);
            for (int i = 0; i < 18; i++) confetti(playerX, y);
        }

        void confetti(float bx, float by) {
            int k = pNext++ % NP;
            px[k] = bx; py[k] = by;
            pvx[k] = (rnd.nextFloat() - 0.5f) * 10f * ppm;
            pvy[k] = (rnd.nextFloat() - 0.8f) * 9f * ppm;
            pl[k] = 0.9f;
            int[] cols = {Color.rgb(255, 215, 90), Color.rgb(255, 120, 170), Color.rgb(120, 235, 255), Color.rgb(160, 255, 150)};
            pcol[k] = cols[rnd.nextInt(cols.length)];
        }

        void flake(float bx, float by) {
            int k = pNext++ % NP;
            px[k] = bx; py[k] = by;
            pvx[k] = (rnd.nextFloat() - 0.5f) * 5f * ppm;
            pvy[k] = (rnd.nextFloat() - 0.4f) * 5f * ppm;
            pl[k] = 0.45f;
            pcol[k] = Color.rgb(200 + rnd.nextInt(40), 205 + rnd.nextInt(40), 220);
        }

        // ---------- outfit store: spend electrons on new looks ----------

        // ---------- settings: haptics, sounds, music and a guarded reset ----------

        boolean settingsButtonHit(float gx, float gy) {
            if (!(state == START || (state == DEAD && stateTime > 0.3f))) return false;
            return gx >= gbx && gx < gbx + gbw && gy >= gby && gy < gby + gbh;
        }

        void drawSettingsButton(Canvas c) {
            rectPx(c, gbx, gby, gbw, gbh, Color.rgb(8, 10, 20));
            rectPx(c, gbx + 1, gby + 1, gbw - 2, gbh - 2, Color.rgb(44, 54, 82));
            text(c, "SETTINGS", gbx + gbw / 2f, gby + 3, 1, Color.WHITE, 1);
        }

        /** rows of the settings screen: x, y, width, height */
        int[] settingsRow(int i) { return new int[] {w / 2 - 90, 28 + i * 24, 180, 20}; }

        int[] confirmButton(int i) { return new int[] {w / 2 - 78 + i * 82, 92, 74, 16}; }   // 0 yes, 1 no

        void settingsTap(float gx, float gy) {
            if (confirmReset) {
                for (int i = 0; i < 2; i++) {
                    int[] b = confirmButton(i);
                    if (gx >= b[0] && gx < b[0] + b[2] && gy >= b[1] && gy < b[1] + b[3]) {
                        if (i == 0) { resetProgress(); sfx(S_HIT); shake = 4f; buzz(60); }
                        confirmReset = false;
                        return;
                    }
                }
                return;
            }
            if (closeHit(gx, gy)) { state = settingsFrom; return; }
            for (int i = 0; i < 4; i++) {
                int[] r = settingsRow(i);
                if (gx < r[0] || gx >= r[0] + r[2] || gy < r[1] || gy >= r[1] + r[3]) continue;
                if (i == 0) { hapticsOn = !hapticsOn; buzz(30); }
                else if (i == 1) { soundOn = !soundOn; sfx(S_COIN); }
                else if (i == 2) { musicOn = !musicOn; if (sfx != null) sfx.setMusic(musicOn); }
                else { confirmReset = true; return; }
                prefs.edit().putBoolean("haptics", hapticsOn).putBoolean("sound", soundOn).putBoolean("music", musicOn).apply();
                return;
            }
        }

        /** erase formulas, tickets, clothes, electrons and the best distance; the settings stay */
        void resetProgress() {
            SharedPreferences.Editor e = prefs.edit();
            for (String k : new String[] {"best", "bank", "inv0", "inv1", "inv2", "worn0", "worn1", "worn2", "formulas", "tickets", "pending", "owned", "scratched", "outfits", "outfit"}) e.remove(k);
            e.apply();
            best = 0; bank = 0; ownedMask = 0; tickets = 0; pending = -1;
            for (int t = 0; t < 3; t++) { inv[t] = 1; worn[t] = 0; }
            applyOutfit();
        }

        void drawSettings(Canvas c) {
            rectPx(c, 0, 0, w, h, Color.rgb(16, 20, 34));
            for (int gx = 0; gx < w; gx += 16) rectPx(c, gx, 0, 1, h, Color.rgb(20, 26, 42));
            text(c, "SETTINGS", 8, 5, 1, Color.rgb(255, 215, 120), 0);
            closeButton(c);
            String[] names = {"HAPTICS", "SOUNDS", "MUSIC", "RESET PROGRESS"};
            boolean[] on = {hapticsOn, soundOn, musicOn, false};
            for (int i = 0; i < 4; i++) {
                int[] r = settingsRow(i);
                boolean reset = i == 3;
                rectPx(c, r[0], r[1], r[2], r[3], Color.rgb(8, 10, 20));
                rectPx(c, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, reset ? Color.rgb(90, 36, 40) : Color.rgb(34, 42, 66));
                text(c, names[i], r[0] + 8, r[1] + 7, 1, Color.WHITE, 0);
                if (!reset) {
                    rectPx(c, r[0] + r[2] - 38, r[1] + 3, 32, r[3] - 6, Color.rgb(8, 10, 20));
                    rectPx(c, r[0] + r[2] - 37, r[1] + 4, 30, r[3] - 8, on[i] ? Color.rgb(50, 150, 90) : Color.rgb(120, 50, 50));
                    text(c, on[i] ? "ON" : "OFF", r[0] + r[2] - 22, r[1] + 7, 1, Color.WHITE, 1);
                }
            }
            if (confirmReset) {
                rectPx(c, 0, 0, w, h, Color.argb(200, 6, 8, 16));
                rectPx(c, w / 2 - 90, 36, 180, 78, Color.rgb(8, 10, 20));
                rectPx(c, w / 2 - 89, 37, 178, 76, Color.rgb(40, 30, 40));
                text(c, "ARE YOU SURE?", w / 2f, 42, 2, Color.rgb(255, 120, 100), 1);
                wrapText(c, "THIS ERASES YOUR FORMULAS, TICKETS, CLOTHES, ELECTRONS AND BEST DISTANCE. IT CANNOT BE UNDONE.", w / 2f, 60, 1, Color.WHITE, 164);
                for (int i = 0; i < 2; i++) {
                    int[] b = confirmButton(i);
                    rectPx(c, b[0], b[1], b[2], b[3], Color.rgb(8, 10, 20));
                    rectPx(c, b[0] + 1, b[1] + 1, b[2] - 2, b[3] - 2, i == 0 ? Color.rgb(150, 45, 45) : Color.rgb(45, 120, 80));
                    text(c, i == 0 ? "YES, RESET" : "NO, KEEP IT", b[0] + b[2] / 2f, b[1] + 5, 1, Color.WHITE, 1);
                }
            }
            drawParticles(c);
        }

        boolean storeButtonHit(float gx, float gy) {
            if (!(state == START || (state == DEAD && stateTime > 0.3f))) return false;
            return gx >= sbx && gx < sbx + sbw && gy >= sby && gy < sby + sbh;
        }

        void drawStoreButton(Canvas c) {
            rectPx(c, sbx, sby, sbw, sbh, Color.rgb(8, 10, 20));
            rectPx(c, sbx + 1, sby + 1, sbw - 2, sbh - 2, Color.rgb(44, 54, 82));
            text(c, "STORE E- " + bank, sbx + sbw / 2f, sby + 3, 1, Color.WHITE, 1);
        }

        /** x, y, width, height of item card i: four across, two down, under the tabs */
        int[] storeRect(int i) {
            int cw = (w - 12) / 4;
            return new int[] {6 + (i % 4) * cw, 36 + (i / 4) * 54, cw - 3, 52};
        }

        int[] tabRect(int t) { return new int[] {8 + t * 66, 21, 62, 11}; }

        void storeTap(float gx, float gy) {
            if (closeHit(gx, gy)) { state = storeFrom; saveStore(); return; }
            for (int t = 0; t < 3; t++) {
                int[] r = tabRect(t);
                if (gx >= r[0] && gx < r[0] + r[2] && gy >= r[1] && gy < r[1] + r[3]) { storeTab = t; sfx(S_COIN); return; }
            }
            int tab = storeTab;
            for (int i = 0; i < INAME[tab].length; i++) {
                int[] r = storeRect(i);
                if (gx < r[0] || gx >= r[0] + r[2] || gy < r[1] || gy >= r[1] + r[3]) continue;
                if ((inv[tab] >> i & 1) != 0) {
                    worn[tab] = i; applyOutfit(); saveStore();
                    sfx(S_COIN);
                } else if (bank >= ICOST[tab][i]) {
                    bank -= ICOST[tab][i]; inv[tab] |= 1 << i; worn[tab] = i; applyOutfit(); saveStore();
                    flash = 0.15f; sfx(S_DONE); buzz(30);
                    for (int k = 0; k < 30; k++) confetti(r[0] + r[2] / 2f, r[1] + 20);
                } else {
                    storeMsg = "NEED " + (ICOST[tab][i] - bank) + " MORE E-"; storeMsgT = 1.6f;
                    shake = 2f; sfx(S_WARN);
                }
                return;
            }
        }

        void drawStore(Canvas c) {
            rectPx(c, 0, 0, w, h, Color.rgb(16, 20, 34));
            for (int gx = 0; gx < w; gx += 16) rectPx(c, gx, 0, 1, h, Color.rgb(20, 26, 42));
            text(c, "CLOTHES STORE", 8, 5, 1, Color.rgb(255, 215, 120), 0);
            text(c, "E- " + bank, 110, 5, 1, Color.rgb(120, 235, 255), 0);
            if (storeMsgT > 0) text(c, storeMsg, w / 2f + 40, 5, 1, Color.rgb(255, 120, 100), 1);
            closeButton(c);
            for (int t = 0; t < 3; t++) {
                int[] r = tabRect(t);
                boolean on = storeTab == t;
                rectPx(c, r[0], r[1], r[2], r[3], on ? Color.rgb(255, 200, 80) : Color.rgb(8, 10, 20));
                rectPx(c, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, on ? Color.rgb(70, 60, 30) : Color.rgb(44, 54, 82));
                text(c, TABS[t], r[0] + r[2] / 2f, r[1] + 3, 1, on ? Color.rgb(255, 230, 150) : Color.WHITE, 1);
            }
            int tab = storeTab, keep = worn[tab];
            for (int i = 0; i < INAME[tab].length; i++) {
                int[] r = storeRect(i);
                boolean own = (inv[tab] >> i & 1) != 0, on = keep == i;
                rectPx(c, r[0], r[1], r[2], r[3], on ? Color.rgb(255, 200, 80) : own ? Color.rgb(90, 100, 130) : Color.rgb(8, 10, 20));
                rectPx(c, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, Color.rgb(28, 34, 52));
                // the scientist with this item on, drawn twice as big
                worn[tab] = i;
                applyOutfit();
                c.save();
                c.translate(r[0] + 6, r[1] + 3);
                c.scale(2f, 2f);
                outlined(c, LEGS_A, 0, SPR_HEAD.length);
                outlined(c, SPR_HEAD, 0, 0);
                c.restore();
                worn[tab] = keep;
                applyOutfit();
                int tx = r[0] + 42, tw = r[2] - 44;
                wrapOut(c, INAME[tab][i], tx + tw / 2f, r[1] + 6, 1, Color.WHITE, tw, false);
                String st = on ? "WORN" : own ? "OWNED" : ICOST[tab][i] + " E-";
                int sc = on ? Color.rgb(255, 215, 120) : own ? Color.rgb(150, 190, 215) : bank >= ICOST[tab][i] ? Color.rgb(120, 255, 160) : Color.rgb(255, 130, 110);
                textOut(c, st, tx + tw / 2f, r[1] + r[3] - 12, 1, sc, 1, false);
            }
            drawParticles(c);
        }

        // ---------- archive: the recovered formulas, scratch off to reveal ----------

        boolean archiveButtonHit(float gx, float gy) {
            if (!(state == START || (state == DEAD && stateTime > 0.3f))) return false;
            return gx >= abx && gx < abx + abw && gy >= aby && gy < aby + abh;
        }

        boolean closeHit(float gx, float gy) {
            return gx >= cbx - 6 && gx < cbx + cbw + 6 && gy >= cby - 3 && gy < cby + cbh + 6;
        }

        int archCols() { return Math.max(5, Math.min(10, (w - 16) / 30)); }

        /** x, y, width, height of card i in the grid */
        int[] cardRect(int i) {
            int cols = archCols(), rows = (NF + cols - 1) / cols;
            int cw = (w - 16) / cols, chh = Math.min(40, (h - 34) / rows);
            return new int[] {8 + (i % cols) * cw + 2, 22 + (i / cols) * chh + 2, cw - 4, chh - 4};
        }

        void archiveTap(float gx, float gy) {
            if (closeHit(gx, gy)) { state = archFrom; return; }
            if (ticketReady() && gx >= tbx && gx < tbx + tbw && gy >= tby && gy < tby + tbh) { openTicket(); return; }
            for (int i = 0; i < NF; i++) {
                int[] r = cardRect(i);
                if (gx >= r[0] && gx < r[0] + r[2] && gy >= r[1] && gy < r[1] + r[3] && (ownedMask >> i & 1) != 0) {
                    selCard = i; selScratched = true; revealKind = 0;
                    mcols = scw / 4; mrows = sch / 4;
                    java.util.Arrays.fill(mask, true);
                    cleared = 0; dragging = false;
                    state = SCRATCH;
                    return;
                }
            }
        }

        /** take a ticket from your pile (or pick up the one you left half scratched) and start scratching */
        void openTicket() {
            if (pending < 0) {
                if (tickets <= 0) return;
                tickets--;
                pending = rollFormula();      // decided now, so closing the screen cannot re-roll it
                saveFormulas();
            }
            selCard = pending; selScratched = false; revealKind = 0;
            mcols = scw / 4; mrows = sch / 4;
            java.util.Arrays.fill(mask, false);
            cleared = 0; dragging = false;
            state = SCRATCH;
        }

        void scratchDown(float gx, float gy) {
            if (closeHit(gx, gy)) { state = ARCHIVE; dragging = false; return; }
            if (!selScratched && gx >= scx && gx < scx + scw && gy >= scy && gy < scy + sch) {
                dragging = true;
                scratchAt(gx, gy);
            }
        }

        void scratchAt(float gx, float gy) {
            if (selScratched) return;
            int rad = 7;
            int c0 = (int) ((gx - rad - scx) / 4), c1 = (int) ((gx + rad - scx) / 4);
            int r0 = (int) ((gy - rad - scy) / 4), r1 = (int) ((gy + rad - scy) / 4);
            for (int cy = Math.max(0, r0); cy <= Math.min(mrows - 1, r1); cy++) {
                for (int cx = Math.max(0, c0); cx <= Math.min(mcols - 1, c1); cx++) {
                    float ccx = scx + cx * 4 + 2, ccy = scy + cy * 4 + 2;
                    float ddx = ccx - gx, ddy = ccy - gy;
                    if (ddx * ddx + ddy * ddy <= rad * rad && !mask[cy * mcols + cx]) {
                        mask[cy * mcols + cx] = true;
                        cleared++;
                        if (rnd.nextInt(5) == 0) flake(ccx, ccy);
                    }
                }
            }
            if (clock - lastScratchSfx > 0.07f) { sfx(S_SCRATCH); lastScratchSfx = clock; }
            if (cleared >= mcols * mrows * 0.6f) revealCard();
        }

        void revealCard() {
            java.util.Arrays.fill(mask, true);
            selScratched = true; dragging = false;
            if ((ownedMask >> selCard & 1) != 0) {
                revealKind = 2;
                bank += DUPLICATE_PAY;        // a repeat pays out in electrons
            } else {
                revealKind = 1;
                ownedMask |= 1 << selCard;
            }
            pending = -1;
            saveFormulas();
            flash = 0.2f; shake = 2f;
            sfx(S_DONE);
            buzz(25);
            for (int i = 0; i < (revealKind == 1 ? 40 : 16); i++) confetti(scx + scw / 2f, scy + sch / 2f);
        }

        void closeButton(Canvas c) {
            rectPx(c, cbx, cby, cbw, cbh, Color.rgb(8, 10, 20));
            rectPx(c, cbx + 1, cby + 1, cbw - 2, cbh - 2, Color.rgb(60, 70, 100));
            text(c, "CLOSE", cbx + cbw / 2f, cby + 3, 1, Color.WHITE, 1);
        }

        void drawArchiveButton(Canvas c) {
            boolean fresh = ticketReady();
            rectPx(c, abx, aby, abw, abh, Color.rgb(8, 10, 20));
            rectPx(c, abx + 1, aby + 1, abw - 2, abh - 2, fresh && ((int) (clock * 3) & 1) == 0 ? Color.rgb(130, 100, 30) : Color.rgb(44, 54, 82));
            String label = "FORMULAS " + Integer.bitCount(ownedMask) + "/" + NF;
            if (fresh && ((int) (clock * 1.6f) & 1) == 0) label = "SCRATCH A TICKET";
            text(c, label, abx + abw / 2f, aby + 3, 1, Color.WHITE, 1);
        }

        // ---------- the prism on every scratch card ----------
        // An equilateral glass prism (apex 60 degrees). A white beam hits the left face at 50 degrees to
        // the normal. Each colour is bent by Snell's law, n1 sin(a1) = n2 sin(a2), at both faces, using a
        // Cauchy fit for BK7 glass, n = 1.5046 + 0.0042 / L^2 (L in micrometres). Violet is slowed most,
        // so it bends most. Real glass spreads red to violet by only about 1.6 degrees, so the spread is
        // drawn 6 times wider to make the rainbow visible; every angle is otherwise exact.
        static final int NW = 26;
        static final float DISP = 6f;
        static final float PCX = 84f, PCY = 52f, PHALF = 40f;      // prism centre and half base, in a 200 x 104 card
        static final float[] LAM = new float[NW];
        static final int[] LCOL = new int[NW];
        static final float[] E1 = new float[2], D0 = new float[2];
        static final float[] E2X = new float[NW], E2Y = new float[NW], D2X = new float[NW], D2Y = new float[NW];
        static final float[] P0 = new float[2], P1 = new float[2], P2 = new float[2];
        static {
            float hgt = PHALF * (float) Math.sqrt(3);
            P0[0] = PCX; P0[1] = PCY - hgt / 2; P1[0] = PCX - PHALF; P1[1] = PCY + hgt / 2; P2[0] = PCX + PHALF; P2[1] = PCY + hgt / 2;
            double phi = Math.toRadians(20);                        // the beam travels up and to the right at 20 degrees
            D0[0] = (float) Math.cos(phi); D0[1] = (float) -Math.sin(phi);
            E1[0] = (P1[0] + P0[0]) / 2; E1[1] = (P1[1] + P0[1]) / 2;  // it hits the middle of the left face
            double nl0 = -0.8660254, nl1 = -0.5;                    // outward normal of the left face (y points down)
            double nr0 = 0.8660254, nr1 = -0.5;                     // outward normal of the right face
            double nMean = 1.5046 + 0.0042 / (0.55 * 0.55);
            for (int i = 0; i < NW; i++) {
                double lam = 400 + i * 300.0 / (NW - 1);
                LAM[i] = (float) lam;
                LCOL[i] = wavelengthColor(lam);
                double um = lam / 1000.0;
                double n = nMean + DISP * (1.5046 + 0.0042 / (um * um) - nMean);
                double[] d1 = refract(D0[0], D0[1], nl0, nl1, 1.0 / n);
                double[] hit = d1 == null ? null : hitFace(E1[0], E1[1], d1[0], d1[1], P0, P2);
                double[] d2 = (d1 == null || hit == null) ? null : refract(d1[0], d1[1], -nr0, -nr1, n);
                if (d2 == null) { E2X[i] = Float.NaN; continue; }
                E2X[i] = (float) hit[0]; E2Y[i] = (float) hit[1];
                D2X[i] = (float) d2[0]; D2Y[i] = (float) d2[1];
            }
        }

        /** Snell refraction. d is the unit direction, nrm the unit normal facing against d, eta = n1 / n2. */
        static double[] refract(double dx, double dy, double nx, double ny, double eta) {
            double cos1 = -(dx * nx + dy * ny);
            double k = 1 - eta * eta * (1 - cos1 * cos1);
            if (k < 0) return null;                                 // total internal reflection
            double f = eta * cos1 - Math.sqrt(k);
            return new double[] {eta * dx + f * nx, eta * dy + f * ny};
        }

        /** where the ray from (px, py) along (dx, dy) crosses the segment a-b, or null */
        static double[] hitFace(double px, double py, double dx, double dy, float[] a, float[] b) {
            double ex = b[0] - a[0], ey = b[1] - a[1];
            double den = dx * ey - dy * ex;
            if (Math.abs(den) < 1e-9) return null;
            double t = ((a[0] - px) * ey - (a[1] - py) * ex) / den;
            double u = ((a[0] - px) * dy - (a[1] - py) * dx) / den;
            if (t <= 0 || u < 0 || u > 1) return null;
            return new double[] {px + dx * t, py + dy * t};
        }

        static int wavelengthColor(double w) {
            double r, g, b;
            if (w < 440) { r = -(w - 440) / 60; g = 0; b = 1; }
            else if (w < 490) { r = 0; g = (w - 440) / 50; b = 1; }
            else if (w < 510) { r = 0; g = 1; b = -(w - 510) / 20; }
            else if (w < 580) { r = (w - 510) / 70; g = 1; b = 0; }
            else if (w < 645) { r = 1; g = -(w - 645) / 65; b = 0; }
            else { r = 1; g = 0; b = 0; }
            double f = w < 420 ? 0.3 + 0.7 * (w - 380) / 40 : w > 700 ? 0.3 + 0.7 * (750 - w) / 50 : 1;
            return Color.rgb((int) (255 * Math.pow(Math.max(0, Math.min(1, r * f)), 0.8)),
                    (int) (255 * Math.pow(Math.max(0, Math.min(1, g * f)), 0.8)),
                    (int) (255 * Math.pow(Math.max(0, Math.min(1, b * f)), 0.8)));
        }

        /** the prism picture inside the rectangle: a white beam in, a rainbow out. sc scales the 200 x 104 card. */
        void prismScene(Canvas c, int x, int y, int cw, int chh, float sc, float ox, float oy) {
            c.save();
            c.clipRect(x, y, x + cw, y + chh);
            for (int i = 0; i < 8; i++) rectPx(c, x, y + i * chh / 8f, cw, chh / 8f + 1, Color.rgb(14 + i, 16 + i, 40 + i * 3));
            float wd = Math.max(1f, sc * 2f);
            float bx = x + ox, by = y + oy;
            // the beam arriving from the left
            float sx = E1[0] - D0[0] * 90, sy = E1[1] - D0[1] * 90;
            line(c, bx + sx * sc, by + sy * sc, bx + E1[0] * sc, by + E1[1] * sc, Color.argb(70, 255, 255, 255), wd * 2.4f);
            line(c, bx + sx * sc, by + sy * sc, bx + E1[0] * sc, by + E1[1] * sc, Color.WHITE, wd);
            // the glass
            path.reset();
            path.moveTo(bx + P0[0] * sc, by + P0[1] * sc);
            path.lineTo(bx + P1[0] * sc, by + P1[1] * sc);
            path.lineTo(bx + P2[0] * sc, by + P2[1] * sc);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(150, 90, 125, 190));
            c.drawPath(path, p);
            // each colour inside the glass, then out the other side
            for (int i = 0; i < NW; i++) {
                if (Float.isNaN(E2X[i])) continue;
                int col = LCOL[i];
                line(c, bx + E1[0] * sc, by + E1[1] * sc, bx + E2X[i] * sc, by + E2Y[i] * sc, (col & 0xFFFFFF) | 0xA0000000, Math.max(1f, sc));
                float fx = E2X[i] + D2X[i] * 220, fy = E2Y[i] + D2Y[i] * 220;
                line(c, bx + E2X[i] * sc, by + E2Y[i] * sc, bx + fx * sc, by + fy * sc, col, wd);
            }
            // bright edges and a glint
            line(c, bx + P0[0] * sc, by + P0[1] * sc, bx + P1[0] * sc, by + P1[1] * sc, Color.rgb(215, 235, 255), Math.max(1f, sc));
            line(c, bx + P0[0] * sc, by + P0[1] * sc, bx + P2[0] * sc, by + P2[1] * sc, Color.rgb(170, 200, 240), Math.max(1f, sc));
            line(c, bx + P1[0] * sc, by + P1[1] * sc, bx + P2[0] * sc, by + P2[1] * sc, Color.rgb(140, 170, 220), Math.max(1f, sc));
            if (((int) (clock * 4) & 3) == 0) rectPx(c, bx + E1[0] * sc - 1, by + E1[1] * sc - 1, 3, 3, Color.WHITE);
            c.restore();
        }

        void drawArchive(Canvas c) {
            rectPx(c, 0, 0, w, h, Color.rgb(16, 20, 34));
            for (int gx = 0; gx < w; gx += 16) rectPx(c, gx, 0, 1, h, Color.rgb(20, 26, 42));
            text(c, "FORMULA ARCHIVE", 8, 5, 1, Color.rgb(255, 215, 120), 0);
            int owned = Integer.bitCount(ownedMask);
            text(c, owned == NF ? "EVERY FORMULA IS BACK. SHARE THEM WITH THE WORLD!" : owned + "/" + NF + " RECOVERED", 8, 13, 1, owned == NF ? Color.rgb(120, 255, 160) : Color.rgb(150, 190, 215), 0);
            closeButton(c);
            for (int i = 0; i < NF; i++) {
                int[] r = cardRect(i);
                boolean have = (ownedMask >> i & 1) != 0;
                rectPx(c, r[0], r[1], r[2], r[3], Color.rgb(8, 10, 20));
                if (have) {
                    rectPx(c, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, Color.rgb(236, 222, 180));
                    rectPx(c, r[0] + 1, r[1] + 1, r[2] - 2, 1, Color.rgb(196, 160, 100));
                    float fs = fitSize(FORMD[i], r[2] - 3, 8f);
                    if (fs >= 4.6f) ov(FORMD[i], r[0] + r[2] / 2f, r[1] + r[3] / 2f, fs, Color.rgb(70, 42, 28), false);
                    else text(c, "" + (i + 1), r[0] + r[2] / 2f, r[1] + r[3] / 2f - 2, 1, Color.rgb(90, 60, 40), 1);
                } else {
                    rectPx(c, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, Color.rgb(28, 34, 52));
                    text(c, "" + (i + 1), r[0] + r[2] / 2f, r[1] + r[3] / 2f - 2, 1, Color.rgb(60, 70, 96), 1);
                }
            }
            // the ticket pile
            if (ticketReady()) {
                boolean blink = ((int) (clock * 3) & 1) == 0;
                rectPx(c, tbx, tby, tbw, tbh, Color.rgb(8, 10, 20));
                rectPx(c, tbx + 1, tby + 1, tbw - 2, tbh - 2, blink ? Color.rgb(130, 100, 30) : Color.rgb(44, 54, 82));
                prismScene(c, tbx + 3, tby + 2, 14, 8, 0.07f, 0f, 3f);
                String label = tickets == 0 ? "FINISH YOUR TICKET" : "SCRATCH A TICKET (" + tickets + ")";
                text(c, label, tbx + tbw / 2f + 8, tby + 4, 1, Color.WHITE, 1);
            } else {
                text(c, "FIND SCROLLS ON YOUR RUNS FOR SCRATCH TICKETS", w / 2f, h - 11, 1, Color.rgb(150, 190, 215), 1);
            }
            drawParticles(c);
        }

        void drawScratch(Canvas c) {
            rectPx(c, 0, 0, w, h, Color.rgb(16, 20, 34));
            closeButton(c);
            String head = !selScratched ? "SCRATCH TO REVEAL" : revealKind == 1 ? "NEW FORMULA RECOVERED!" : revealKind == 2 ? "YOU HAD THIS ONE. +" + DUPLICATE_PAY + " E-" : "FORMULA";
            text(c, head, 8, 8, 1, revealKind == 2 ? Color.rgb(120, 235, 255) : Color.rgb(255, 215, 120), 0);
            rectPx(c, scx - 2, scy - 2, scw + 4, sch + 4, Color.rgb(8, 10, 20));
            int paper = Color.rgb(236, 222, 180);
            if (selScratched) {
                rectPx(c, scx, scy, scw, sch, paper);
            } else {
                prismScene(c, scx, scy, scw, sch, 1f, 0f, 0f);
                for (int cy = 0; cy < mrows; cy++) {
                    for (int cx = 0; cx < mcols; cx++) {
                        if (mask[cy * mcols + cx]) rectPx(c, scx + cx * 4, scy + cy * 4, 4, 4, paper);
                    }
                }
                if (cleared < mcols * mrows * 0.12f) text(c, "SCRATCH HERE", scx + scw / 2f, scy + sch - 14, 1, Color.WHITE, 1);
            }
            int ink = Color.rgb(70, 42, 28);
            boolean clip = !selScratched;
            if (clip) {
                // only the scratched-off cells show the card's writing
                clipPath.reset();
                for (int cy = 0; cy < mrows; cy++) {
                    int run = -1;
                    for (int cx = 0; cx <= mcols; cx++) {
                        boolean on = cx < mcols && mask[cy * mcols + cx];
                        if (on && run < 0) run = cx;
                        if (!on && run >= 0) {
                            clipPath.addRect(scx + run * 4, scy + cy * 4, scx + cx * 4, scy + cy * 4 + 4, Path.Direction.CW);
                            run = -1;
                        }
                    }
                }
                c.save();
                c.clipPath(clipPath);
            }
            textOut(c, FNAME[selCard], scx + scw / 2f, scy + 8, 1, Color.rgb(140, 96, 56), 1, false);
            wrapOut(c, FDESC[selCard], scx + scw / 2f, scy + 58, 1, ink, scw - 16, false);
            if (clip) c.restore();
            ov(FORMD[selCard], scx + scw / 2f, scy + 34, fitSize(FORMD[selCard], scw - 16, 17f), ink, clip);
            drawParticles(c);
        }

        void debris(float bx, float by) {
            int k = pNext++ % NP;
            px[k] = bx; py[k] = by;
            pvx[k] = (rnd.nextFloat() * 1.4f - 0.2f) * 7f * ppm;
            pvy[k] = (rnd.nextFloat() - 0.6f) * 8f * ppm;
            pl[k] = 0.8f;
            int[] cols = {Color.rgb(160, 84, 62), Color.rgb(200, 120, 90), Color.rgb(86, 52, 44), Color.rgb(120, 125, 140)};
            pcol[k] = cols[rnd.nextInt(cols.length)];
        }

        /** title screen: he stands on the grass facing the wall */
        void updateStart(float dt) {
            y = floorY - SH_BOT; vy = 0; onFloor = true;
            speed = 0;
            for (int i = 0; i < NP; i++) {
                if (pl[i] > 0) {
                    pl[i] -= dt;
                    px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
                }
            }
        }

        void fireBolt() {
            if (boltCd > 0) return;
            Bolt b = new Bolt();
            b.x = playerX + 12; b.y = y; b.kind = bolts.size() & 1;
            bolts.add(b);
            boltCd = 0.3f;
            sfx(S_TELE);
        }

        /** base pairs fly forward and wipe out a zapper or a missile they touch */
        void updateBolts(float dt) {
            if (boltCd > 0) boltCd -= dt;
            for (int i = bolts.size() - 1; i >= 0; i--) {
                Bolt b = bolts.get(i);
                b.x += 220f * dt;
                boolean gone = b.x > w + 10;
                for (int j = zappers.size() - 1; j >= 0 && !gone; j--) {
                    Zapper z = zappers.get(j);
                    float hx = (float) Math.cos(z.angle) * z.len / 2, hy = (float) Math.sin(z.angle) * z.len / 2;
                    if (segDist(b.x, b.y, z.x - hx, z.y - hy, z.x + hx, z.y + hy) < 7f) {
                        zappers.remove(j);
                        for (int k = 0; k < 14; k++) burst(b.x, b.y);
                        sfx(S_HIT);
                        gone = true;
                    }
                }
                for (int j = missiles.size() - 1; j >= 0 && !gone; j--) {
                    Missile m = missiles.get(j);
                    if (!m.flying) continue;
                    float dx = m.x - b.x, dy = m.y - b.y;
                    if (dx * dx + dy * dy < 100f) {
                        missiles.remove(j);
                        for (int k = 0; k < 10; k++) burst(b.x, b.y);
                        sfx(S_HIT);
                        gone = true;
                    }
                }
                if (gone) bolts.remove(i);
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
            if (--charges <= 0) breakVehicle();      // the fifth jump burns it out
        }

        void press() {
            if (vehicle == INVERTER) {
                gravDir = -gravDir;
                vy *= 0.3f;
                shake = Math.max(shake, 2f);
                sfx(S_FLIP);
                buzz(15);
                for (int i = 0; i < 10; i++) burst(playerX, y);
            } else if (vehicle == HELIX) {
                fireBolt();
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
            if (worn[2] == RAINBOW_PACK) applyOutfit();      // the rainbow jetpack cycles colours
            if (flash > 0) flash -= rdt;
            if (shake > 0) shake = Math.max(0f, shake - 30f * rdt);
            if (hitstop > 0) { hitstop -= rdt; return; }   // freeze for a moment on a big hit

            boolean waiting = state == START || state == ARCHIVE || state == SCRATCH || state == STORE || state == SETTINGS;   // the scene holds still until he runs
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
            if (state == ARCHIVE || state == SCRATCH || state == STORE || state == SETTINGS) {
                if (storeMsgT > 0) storeMsgT -= dt;
                for (int i = 0; i < NP; i++) {
                    if (pl[i] > 0) {
                        pl[i] -= dt;
                        px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
                    }
                }
                return;
            }
            if (state == START) { updateStart(dt); return; }
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
            boolean helix = vehicle == HELIX;
            boolean fire = thrusting && state == PLAY && ((vehicle == 0 && jumpDelay <= 0) || helix);
            ay = (helix ? 14f : GRAVITY) * gravDir - (fire ? (helix ? 30f : THRUST) : 0);
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
                            landT = 0.12f;
                            for (int i = 0; i < 5; i++) burst(playerX - 3, y + 10);
                        }
                        vy = 0;
                    }
                }
                if (vehicle != STOMPER) onFloor = true;
            }
            if (vehicle == STOMPER && y >= hi - 0.5f) onFloor = true;

            if (fire && !helix) emitExhaust();
            if (helix) { yh = (yh + 1) & 63; yHist[yh] = y; }
            for (int i = 0; i < NP; i++) {
                if (pl[i] > 0) {
                    pl[i] -= dt;
                    px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
                }
            }

            if (tipT > 0) tipT -= rdt;
            bonus = Math.max(0f, bonus - 4.5f * dt);
            speed = breaking ? Math.min(5f, 2f + stateTime * 8f) : 5f + Math.min(dist / 60f, 7f) + bonus;   // outside he tops out at the speed the run starts at
            float scroll = speed * ppm * dt;
            outX -= scroll;
            if (breaking && outX <= playerX + 8f) breach();
            if (!breaking) dist += speed * dt;
            bgOff += scroll * 0.25f;
            if (onFloor && (vehicle == 0 || vehicle == QUANTUM)) runPhase += dt * 11f;     // steps at a fixed rate, they do not speed up with the world

            if (!breaking) {
                spawnLeft -= scroll;
                if (spawnLeft <= 0) spawn();
            }

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

            if (bannerT > 0) bannerT -= rdt;
            updateBolts(dt);
            for (int i = scrolls.size() - 1; i >= 0; i--) {
                Scroll k = scrolls.get(i);
                k.x -= scroll;
                float dx = k.x - playerX, dy = k.y - y;
                if (state == PLAY && dx * dx + dy * dy < (r + 9) * (r + 9)) {
                    collectScroll();
                    scrolls.remove(i);
                    continue;
                }
                if (k.x < -20) scrolls.remove(i);
            }

            for (int i = coins.size() - 1; i >= 0; i--) {
                Coin c = coins.get(i);
                c.x -= scroll;
                float dx = c.x - playerX, dy = c.y - y;
                if (!c.taken && dx * dx + dy * dy < (r + 0.22f * ppm) * (r + 0.22f * ppm)) {
                    c.taken = true; electrons++; bank++;
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
                if (state == PLAY && invuln <= 0 && (segDist(playerX, y, z.x - hx, z.y - hy, z.x + hx, z.y + hy) < r + 0.06f * ppm
                        || circleHitsSquare(playerX, y, r, z.x - hx, z.y - hy, 4f)
                        || circleHitsSquare(playerX, y, r, z.x + hx, z.y + hy, 4f))) hit();
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
            Arrays.fill(yHist, y);
            boltCd = 0;
            charges = ENTANGLER_USES;
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
            saveStore();
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
            pcol[k] = 0;
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
            outX -= sc;
            for (Coin k : coins) k.x -= sc;
            for (Zapper z : zappers) z.x -= sc;
            for (Crate k : crates) k.x -= sc;
            for (Scroll k : scrolls) k.x -= sc;
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
            pcol[k] = 0;
        }

        void emitExhaust() {
            for (int n = 0; n < 2; n++) {
                int k = pNext++ % NP;
                px[k] = playerX - 5; py[k] = y + 5;
                pvx[k] = (-speed * 0.6f + (rnd.nextFloat() - 0.5f) * 2f) * ppm;
                pvy[k] = (4f + rnd.nextFloat() * 4f) * ppm;
                pl[k] = 0.35f + rnd.nextFloat() * 0.3f;
                pcol[k] = 0;
            }
        }

        /** does the circle touch the square of the given half size (the zapper's end nodes)? */
        static boolean circleHitsSquare(float cx, float cy, float r, float sx, float sy, float half) {
            float nx = Math.max(sx - half, Math.min(cx, sx + half)), ny = Math.max(sy - half, Math.min(cy, sy + half));
            float dx = cx - nx, dy = cy - ny;
            return dx * dx + dy * dy <= r * r;
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
                    k.x = x0; k.y = randY(3f, 6f); k.type = 1 + rnd.nextInt(4);
                    crates.add(k);
                    spawnLeft = 4f * ppm;
                    nextCrate = dist + 220 + rnd.nextInt(150);
                } else {
                    nextCrate = dist + 100;
                }
                return;
            }
            if (dist >= nextParch) {
                Scroll k = new Scroll();
                k.x = x0; k.y = randY(3f, 6f);
                scrolls.add(k);
                spawnLeft = 3f * ppm;
                nextParch = dist + 140 + rnd.nextInt(120);
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
        /** the helix's head: the pod with green and magenta caps, the two ends of a base pair */
        static final String[] HELIX_HEAD = recolor(POD, 'R', 'Q', 'U', 'q');

        static String[] recolor(String[] rows, char a, char a2, char b, char b2) {
            String[] out = new String[rows.length];
            for (int i = 0; i < rows.length; i++) out[i] = rows[i].replace(a, a2).replace(b, b2);
            return out;
        }

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
            if (type == HELIX) return HELIX_HEAD;
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

        // 3 x 5 pixel font: A-Z, 0-9 and . , ! - + / : ' ^ = ?. Each glyph is 5 rows of 3 bits.
        static final String CH = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,!-+/:'^ =?";
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
            "001001010100100", "000010000010000", "010010000000000", "010101000000000", "000000000000000", "000111000111000", "110001010000010"
        };
        static final int TXT_OUTLINE = Color.rgb(8, 10, 22);

        void glyphs(Canvas c, String s, int ix, int iy, int sc, int col) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            for (int i = 0; i < s.length(); i++) {
                int gi = CH.indexOf(s.charAt(i));
                if (gi < 0) continue;
                String bits = GL[gi];
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

        static int textWidth(String s, int sc) { return s.length() * 4 * sc - sc; }

        /**
         * Pixel text. align: 0 left, 1 centre, 2 right. y is the top of the text, which is 5 * sc tall.
         * outline draws a 1 pixel dark edge (not an offset shadow) so it stays readable over the scenery.
         * Centred text that is too wide for the screen splits onto two lines.
         */
        void text(Canvas c, String s, float x, float yy, int sc, int col, int align) {
            textOut(c, s, x, yy, sc, col, align, true);
        }

        void textOut(Canvas c, String s, float x, float yy, int sc, int col, int align, boolean outline) {
            s = s.toUpperCase();
            float maxW = align == 1 ? 2f * Math.min(x, w - x) - 2f : align == 0 ? w - x - 1f : x - 1f;
            if (textWidth(s, sc) > maxW && align == 1 && s.indexOf(' ') > 0) {
                int mid = s.length() / 2, cut = s.lastIndexOf(' ', mid);
                if (cut <= 0) cut = s.indexOf(' ', mid);
                textOut(c, s.substring(0, cut), x, yy, sc, col, align, outline);
                textOut(c, s.substring(cut + 1), x, yy + 7 * sc, sc, col, align, outline);
                return;
            }
            int tw = textWidth(s, sc);
            int ix = Math.round(x);
            if (align == 1) ix -= tw / 2;
            else if (align == 2) ix -= tw;
            int iy = Math.round(yy);
            if (outline) {
                glyphs(c, s, ix - sc, iy, sc, TXT_OUTLINE);
                glyphs(c, s, ix + sc, iy, sc, TXT_OUTLINE);
                glyphs(c, s, ix, iy - sc, sc, TXT_OUTLINE);
                glyphs(c, s, ix, iy + sc, sc, TXT_OUTLINE);
            }
            glyphs(c, s, ix, iy, sc, col);
        }

        void wrapText(Canvas c, String s, float cx, float yy, int sc, int col, int maxPx) {
            wrapOut(c, s, cx, yy, sc, col, maxPx, true);
        }

        void wrapOut(Canvas c, String s, float cx, float yy, int sc, int col, int maxPx, boolean outline) {
            int per = Math.max(8, (maxPx + sc) / (4 * sc));
            String[] words = s.split(" ");
            StringBuilder line = new StringBuilder();
            float ly = yy;
            for (String wd : words) {
                if (line.length() > 0 && line.length() + 1 + wd.length() > per) {
                    textOut(c, line.toString(), cx, ly, sc, col, 1, outline);
                    ly += 7 * sc;
                    line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(wd);
            }
            if (line.length() > 0) textOut(c, line.toString(), cx, ly, sc, col, 1, outline);
        }

        static int pal(char ch) {
            int ov = ch < 128 ? OVR[ch] : 0;
            if (ov != 0) return ov;
            switch (ch) {
                case 'Q': return Color.rgb(80, 220, 130);
                case 'q': return Color.rgb(230, 90, 200);
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
            ovs.clear();
            if (state == SETTINGS) drawSettings(c);
            else if (state == STORE) drawStore(c);
            else if (state == ARCHIVE) drawArchive(c);
            else if (state == SCRATCH) drawScratch(c);
            else drawScene(c);
            if (flash > 0) rectPx(c, 0, 0, w, h, Color.argb((int) Math.min(200, flash * 520), 255, 255, 255));
            if (state == START) drawTitle(c);
            if (state == DEAD) drawDead(c);
            if (state == START || state == DEAD) { drawArchiveButton(c); drawStoreButton(c); drawSettingsButton(c); }
            out.save();
            if (shake > 0) out.translate((rnd.nextFloat() - 0.5f) * 2f * shake * unit, (rnd.nextFloat() - 0.5f) * 2f * shake * unit);
            out.drawBitmap(buf, null, dst, blit);
            for (Ov o : ovs) {
                boolean clipped = false;
                if (o.clip) {
                    // only the scratched-off part of the card shows the text
                    clipPath.reset();
                    for (int cy = 0; cy < mrows; cy++) {
                        int run = -1;
                        for (int cx = 0; cx <= mcols; cx++) {
                            boolean on = cx < mcols && mask[cy * mcols + cx];
                            if (on && run < 0) run = cx;
                            if (!on && run >= 0) {
                                clipPath.addRect((scx + run * 4) * unit, (scy + cy * 4) * unit, (scx + cx * 4) * unit, (scy + cy * 4 + 4) * unit, Path.Direction.CW);
                                run = -1;
                            }
                        }
                    }
                    out.save();
                    out.clipPath(clipPath);
                    clipped = true;
                }
                    ovp.setTextSize(o.size * unit);
                    ovp.setColor(o.col);
                    out.drawText(o.t, o.x * unit, o.y * unit + o.size * unit * 0.34f, ovp);
                if (clipped) out.restore();
            }
            out.restore();
        }

        void drawScene(Canvas c) {
            drawBackground(c);
            drawFloor(c);
            drawNpcs(c);
            rectPx(c, 0, ceilY, w, floorY - ceilY, Color.argb(95, 6, 10, 24));   // push the whole backdrop back
            drawExterior(c);
            drawCoins(c);
            drawCrates(c);
            drawScrolls(c);
            drawZappers(c);
            drawMissiles(c);
            drawBolts(c);
            drawParticles(c);
            drawPlayer(c);
            if (telePulse > 0) {
                ring(c, playerX, teleFromY, telePulse / 0.25f);
                ring(c, playerX, y, telePulse / 0.25f);
            }
            if (state == BUILD) drawBuild(c);
            drawHud(c);
        }

        // ---------- outside: the yard and NULLCORP's wall ----------

        void line(Canvas c, float x0, float y0, float x1, float y1, int col, float wd) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(wd);
            p.setColor(col);
            c.drawLine(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), p);
            p.setStyle(Paint.Style.FILL);
        }

        void drawExterior(Canvas c) {
            int ox = Math.round(outX), th = 10, fl = Math.round(floorY);
            if (ox + th < 0) return;
            int cw = Math.min(w, ox);
            if (cw > 0) {
                c.save();
                c.clipRect(0, 0, cw, h);
                // night sky over the yard
                for (int i = 0; i < 12; i++) rectPx(c, 0, i * fl / 12f, w, fl / 12f + 1, Color.rgb(8 + i, 12 + i * 2, 36 + i * 4));
                for (int i = 0; i < 40; i++) {
                    int sx = (i * 53) % w, sy = (i * 29) % (fl - 40);
                    boolean bright = (((int) (clock * 2) + i) & 3) == 0;
                    rectPx(c, sx, sy, 1, 1, bright ? Color.WHITE : Color.rgb(120, 130, 170));
                }
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.rgb(225, 225, 205));
                c.drawCircle(w * 0.16f, 30, 9, p);
                p.setColor(Color.rgb(190, 190, 170));
                c.drawCircle(w * 0.16f - 3, 27, 3, p);
                c.drawCircle(w * 0.16f + 3, 33, 2, p);
                // distant treeline
                float tl = (outX * 0.3f) % 14;
                for (float k = -tl - 14; k < w; k += 14) rectPx(c, k, fl - 24 - ((int) (k + 140) * 7) % 9, 12, 40, Color.rgb(12, 24, 28));
                // a searchlight sweeping the sky from the top of the wall
                float a = (float) Math.sin(clock * 0.8) * 0.5f + 2.2f;
                path.reset();
                path.moveTo(ox, 0);
                path.lineTo(ox + (float) Math.cos(a - 0.12) * 160, (float) Math.sin(a - 0.12) * 160);
                path.lineTo(ox + (float) Math.cos(a + 0.12) * 160, (float) Math.sin(a + 0.12) * 160);
                path.close();
                p.setColor(Color.argb(40, 255, 245, 160));
                c.drawPath(path, p);
                // ground and grass
                rectPx(c, 0, fl, w, h - fl, Color.rgb(52, 44, 34));
                rectPx(c, 0, fl, w, 4, Color.rgb(34, 78, 46));
                float go = outX % 12;
                for (float x = ox - go; x > -12; x -= 12) rectPx(c, x, fl - 2, 1, 2, Color.rgb(40, 96, 56));
                // fence and the company sign
                for (float x = ox - 14; x > -10; x -= 22) {
                    rectPx(c, x, fl - 14, 2, 14, Color.rgb(120, 120, 135));
                    rectPx(c, x - 20, fl - 11, 20, 1, Color.rgb(90, 90, 105));
                }
                int sgx = ox - 76;
                rectPx(c, sgx + 28, fl - 24, 3, 24, Color.rgb(90, 70, 50));
                rectPx(c, sgx, fl - 46, 60, 22, Color.rgb(24, 26, 36));
                rectPx(c, sgx + 1, fl - 45, 58, 20, Color.rgb(160, 40, 40));
                textOut(c, "NULLCORP", sgx + 30, fl - 42, 1, Color.WHITE, 1, false);
                textOut(c, "KEEP OUT", sgx + 30, fl - 34, 1, Color.rgb(255, 210, 120), 1, false);
                c.restore();
            }
            // the wall: a brick slab with a hole once he has gone through
            for (int yy = 0; yy < fl; yy++) {
                if (holeOpen && Math.abs(yy - holeY) < 15 + ((yy * 13) % 4)) continue;
                if (yy % 5 == 4) {
                    rectPx(c, ox, yy, th, 1, Color.rgb(86, 52, 44));
                } else {
                    rectPx(c, ox, yy, th, 1, Color.rgb(160, 84, 62));
                    rectPx(c, ox + (((yy / 5) & 1) == 0 ? 4 : 8), yy, 1, 1, Color.rgb(86, 52, 44));
                }
            }
            rectPx(c, ox, 0, 1, fl, Color.rgb(205, 125, 95));
            rectPx(c, ox + th - 1, 0, 1, fl, Color.rgb(110, 60, 48));
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
            int oy = Math.round(ceilY) + 14, oh = Math.round(floorY) - oy;      // down to the floor line
            // name plate
            rectPx(c, ox + 25, ceilY + 2, RW - 50, 9, Color.rgb(16, 24, 38));
            textOut(c, ROOM_NAMES[kind], ox + RW / 2f, ceilY + 4, 1, Color.rgb(255, 200, 80), 1, false);
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
            for (int i = 0; i < 4; i++) textOut(c, DIG[di + i % 3], ox + 12, oy + 8 + i * 6, 1, Color.rgb(90, 230, 120), 0, false);
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
                if (ten) textOut(c, m + " M", x, floorY + 10, 1, Color.rgb(140, 190, 210), 1, false);
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
                        textOut(c, "!", wx - 1, m.y - 3, 1, Color.WHITE, 1, false);
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
                if (pcol[i] != 0) rectPx(c, px[i], py[i], sz, sz, (pcol[i] & 0xFFFFFF) | ((int) (a * 255) << 24));
                else rectPx(c, px[i], py[i], sz, sz, Color.argb((int) (a * 255), 255, state == DEAD ? 120 : (a > 0.6f ? 230 : 150), 60));
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
            int left = Math.round(playerX) - 8, top = Math.round(y) - SH_TOP;

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
            // one pip per teleport left
            int py0 = gravDir > 0 ? cy - 20 : cy + 17;
            for (int i = 0; i < ENTANGLER_USES; i++) {
                int pxl = Math.round(playerX) - 14 + i * 6;
                rectPx(c, pxl - 1, py0 - 1, 5, 5, Color.rgb(8, 10, 22));
                rectPx(c, pxl, py0, 3, 3, i < charges ? Color.rgb(120, 235, 255) : Color.rgb(50, 60, 80));
            }
        }

        /** a double helix that trails the head and follows its path, with base pairs for rungs */
        void drawHelix(Canvas c) {
            int left = Math.round(playerX) - 10, top = Math.round(y) - 8;
            int segs = 18;
            float phase = clock * 7f;
            float pax = 0, pay = 0, pbx = 0, pby = 0;
            for (int i = 0; i < segs; i++) {
                float sx = playerX - 12 - i * 5f;
                float sy = yHist[(yh - i * 2) & 63] + (float) Math.sin(phase + i * 0.55f) * 2f * Math.min(1f, i / 4f);
                float tw = (float) Math.sin(phase * 1.3f + i * 0.9f);             // the twist
                float off = tw * 6f * (1f - i / (segs * 1.4f));                    // narrows toward the tail
                float ay2 = sy - off, by2 = sy + off;
                int sz = i < 8 ? 3 : 2;
                if ((i & 1) == 0) {
                    int[] pr = RUNG[(i / 2) & 3];
                    line(c, sx, ay2, sx, by2, Color.argb(190, 220, 230, 245), 1);
                    rectPx(c, sx - 1, ay2 - 1, 2, 2, 0xFF000000 | pr[0]);
                    rectPx(c, sx - 1, by2 - 1, 2, 2, 0xFF000000 | pr[1]);
                }
                if (i > 0) {
                    line(c, pax, pay, sx, ay2, Color.rgb(70, 200, 190), 1);
                    line(c, pbx, pby, sx, by2, Color.rgb(210, 90, 190), 1);
                }
                // the strand nearer the viewer is drawn last
                if (tw > 0) {
                    rectPx(c, sx - 1, by2 - 1, sz, sz, Color.rgb(230, 100, 205));
                    rectPx(c, sx - 1, ay2 - 1, sz, sz, Color.rgb(90, 230, 210));
                } else {
                    rectPx(c, sx - 1, ay2 - 1, sz, sz, Color.rgb(90, 230, 210));
                    rectPx(c, sx - 1, by2 - 1, sz, sz, Color.rgb(230, 100, 205));
                }
                pax = sx; pay = ay2; pbx = sx; pby = by2;
            }
            outlined(c, HELIX_HEAD, left, top);
            outlined(c, HEAD_ONLY, left, top + 3);
        }

        void drawBolts(Canvas c) {
            for (Bolt b : bolts) {
                int[] pr = RUNG[b.kind * 1];
                float len = 1f + 6f * Math.abs((float) Math.cos(clock * 12 + b.x * 0.15f));
                for (int t = 1; t <= 3; t++) rectPx(c, b.x - t * 5, b.y, 3, 1, Color.argb(120 - t * 30, 220, 240, 255));
                line(c, b.x, b.y - len / 2, b.x, b.y + len / 2, Color.WHITE, 1);
                rectPx(c, b.x - 1, b.y - len / 2 - 1, 3, 3, 0xFF000000 | pr[0]);
                rectPx(c, b.x - 1, b.y + len / 2 - 1, 3, 3, 0xFF000000 | pr[1]);
            }
        }

        void drawVehicle(Canvas c) {
            if (vehicle == QUANTUM) { drawQuantum(c); return; }
            if (vehicle == HELIX) { drawHelix(c); return; }
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

        void drawScrolls(Canvas c) {
            for (Scroll k : scrolls) {
                int cx = Math.round(k.x), cy = Math.round(k.y + (float) Math.sin(clock * 3 + k.x * 0.04f) * 2);
                int g = 34 + (int) (30 * Math.sin(clock * 5));
                rectPx(c, cx - 11, cy - 10, 22, 20, Color.argb(g, 255, 230, 140));
                rectPx(c, cx - 9, cy - 7, 18, 14, Color.rgb(30, 22, 16));
                rectPx(c, cx - 6, cy - 5, 12, 10, Color.rgb(236, 222, 180));
                for (int i = 0; i < 3; i++) rectPx(c, cx - 4, cy - 3 + i * 3, 8, 1, Color.rgb(120, 90, 60));
                rectPx(c, cx - 8, cy - 6, 3, 12, Color.rgb(196, 160, 100));
                rectPx(c, cx - 8, cy - 6, 1, 12, Color.rgb(230, 200, 140));
                rectPx(c, cx + 5, cy - 6, 3, 12, Color.rgb(196, 160, 100));
                rectPx(c, cx + 7, cy - 6, 1, 12, Color.rgb(150, 110, 60));
                rectPx(c, cx - 1, cy - 6, 2, 12, Color.rgb(200, 60, 60));
            }
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
                textOut(c, "B", cx - 1, cy - 2, 1, Color.WHITE, 1, false);
            }
        }

        void drawHud(Canvas c) {
            int top = Math.round(ceilY) + 4;
            text(c, (int) (state == DEAD ? finalDist : dist) + " M", 6, top, 2, Color.WHITE, 0);
            text(c, "E- " + electrons, 6, top + 14, 1, Color.rgb(120, 235, 255), 0);
            text(c, "FORMULAS " + Integer.bitCount(ownedMask) + "/" + NF, 6, top + 22, 1, Color.rgb(255, 215, 120), 0);
            int rx = w - 6;
            int dim = Color.rgb(170, 200, 220);
            text(c, String.format("V %+.1f M/S", -vy), rx, top, 1, dim, 2);
            text(c, String.format("A %+.1f M/S^2", -ay), rx, top + 8, 1, dim, 2);
            text(c, "BEST " + best + " M", rx, top + 16, 1, dim, 2);
            drawHint(c);
        }

        void drawHint(Canvas c) {
            if (bannerT > 0 && state == PLAY) {
                // a prism scratch card pops up and rises, with no words so nothing is given away
                float t = 1f - bannerT / 1.2f;
                int cx = Math.round(playerX + 14), cy = Math.round(y - 22 - t * 14);
                rectPx(c, cx - 7, cy - 9, 14, 18, Color.rgb(8, 10, 20));
                prismScene(c, cx - 6, cy - 8, 12, 16, 0.16f, -4f, 3f);
                if (((int) (clock * 12) & 1) == 0) {
                    rectPx(c, cx - 1, cy - 12, 2, 6, Color.WHITE);
                    rectPx(c, cx - 4, cy - 10, 8, 2, Color.WHITE);
                }
            }
            if (tipT > 0 && state == PLAY && vehicle == 0) {
                text(c, "TAP TO JUMP, THEN HOLD TO FIRE THE JETPACK", w / 2f, ceilY + 28, 1, Color.WHITE, 1);
            }
            if (hintT > 0 && state == PLAY && vehicle != 0) {
                text(c, VNAME[vehicle], w / 2f, ceilY + 28, 1, Color.WHITE, 1);
                text(c, VHINT[vehicle], w / 2f, ceilY + 38, 1, Color.rgb(120, 235, 255), 1);
            }
        }

        void drawTitle(Canvas c) {
            text(c, "THRUST LAB", w / 2f, h * 0.14f, 4, Color.WHITE, 1);
            text(c, "NULLCORP STOLE EVERY THEORY AND FORMULA.", w / 2f, h * 0.14f + 30, 1, Color.rgb(255, 200, 80), 1);
            text(c, "STEAL THEM BACK AND SHARE THEM WITH THE WORLD.", w / 2f, h * 0.14f + 39, 1, Color.rgb(120, 235, 255), 1);
            if (((int) (clock * 2) & 1) == 0) text(c, "TAP TO START", w / 2f, h * 0.14f + 54, 1, Color.rgb(255, 220, 140), 1);
        }

        void drawDead(Canvas c) {
            float fade = Math.max(0f, Math.min(1f, (stateTime - 1.2f) / 0.4f));
            if (fade <= 0f) return;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb((int) (170 * fade), 8, 12, 24));
            c.drawRect(0, 0, w, h, p);
            if (stateTime < 1.5f) return;
            text(c, (int) finalDist + " M", w / 2f, h * 0.18f, 4, Color.WHITE, 1);
            text(c, electrons + " ELECTRONS   " + Integer.bitCount(ownedMask) + "/" + NF + " FORMULAS   BEST " + best + " M", w / 2f, h * 0.18f + 30, 1, Color.rgb(120, 235, 255), 1);
            wrapText(c, fact, w / 2f, h * 0.18f + 50, 1, Color.rgb(210, 220, 235), (int) (w * 0.7f));
            if (((int) (clock * 2) & 1) == 0) {
                text(c, "TAP TO CONTINUE", w / 2f, 106, 2, Color.rgb(255, 200, 80), 1);
            }
        }
    }
}
