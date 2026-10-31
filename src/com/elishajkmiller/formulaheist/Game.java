package com.elishajkmiller.formulaheist;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Random;

import static com.elishajkmiller.formulaheist.Content.*;
import static com.elishajkmiller.formulaheist.Geom.*;
import static com.elishajkmiller.formulaheist.Prism.*;
import static com.elishajkmiller.formulaheist.Sprites.*;

/**
 * The game View: state, input, the update loop and drawing. Vehicles, hazards and rooms are their own classes
 * (Vehicle, Hazard, Room); fixed data is in Content, pixel art in Sprites, prism optics in Prism.
 *
 * Layout of this class, top to bottom: constants, game state, setup and touch input, settings, store,
 * archive and the prism card, the update loop (physics, vehicles, spawning), then all drawing (world,
 * the yard and wall, player, HUD and screens) and the pixel font.
 * Everything is drawn at 144 pixels tall into a small bitmap that is scaled up with no smoothing.
 */
@SuppressWarnings("deprecation")   // immersive flags and the Vibrator calls still work on every phone we target
final class Game extends View implements Choreographer.FrameCallback {

    // ---------- constants ----------

    static final int START = 0, PLAY = 1, DEAD = 2, BUILD = 3, ARCHIVE = 4, SCRATCH = 5, STORE = 6, SETTINGS = 7;
    static final float GUARD_RUN = 100f;     // how fast the guards run away, in pixels per second
    static final int GUARD = 4;              // Npc mode of the guards
    static final float CARD_DELAY = 1.5f;    // seconds after a crash before the score card and its buttons appear
    static final float BUILD_TIME = 1.5f;    // seconds a vehicle takes to assemble
    static final int S_JUMP = 0, S_COIN = 1, S_HIT = 2, S_STOMP = 3, S_BUILD = 4, S_FLIP = 5, S_WARN = 6, S_DONE = 7, S_TELE = 8, S_SCRATCH = 9, S_FANFARE = 10, S_LAND = 11;

    static final float GRAVITY = 22f;        // m/s^2, game gravity (Earth is 9.8)
    static final float THRUST = 38f;         // m/s^2 from the pack, net 16 up
    static final float VFALL = 14f;          // m/s, falling top speed
    static final float VMAX = 7f;            // m/s, "terminal" speed going up
    static final float JUMP = 8.5f;          // m/s, jump off the floor


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
    final RectF dst = new RectF(), zdst = new RectF();
    final Rect zsrc = new Rect();
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
    final ArrayList<Hazard> hazards = new ArrayList<>();
    final ArrayList<Npc> npcs = new ArrayList<>();
    float bgOff, lastBg, npcLeft, spawnLeft, nextCrate, nextParch, nextSection;

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
    Vehicle veh, building;                       // the vehicle you ride (null on foot) and the one being assembled
    int gravDir = 1;
    float invuln, hintT, teleFromY, telePulse;
    static final int NB = 64;                    // pixel blocks that fly in to build a vehicle
    final int[] brow = new int[NB], bcol = new int[NB];
    final float[] bdelay = new float[NB], bsx = new float[NB], bsy = new float[NB];
    final boolean[] bland = new boolean[NB];
    int nb;
    float buildT;

    float bustOff, bustV, lastVy;                // the chest spring: it only reacts to jumps and landings, not to running
    static final int[][] RUNG = {{0xDC4646, 0x4678E6}, {0x50DC78, 0xF0C83C}, {0x4678E6, 0xDC4646}, {0xF0C83C, 0x50DC78}};   // A-T, C-G pairs

    // death
    boolean settled;
    int bounces;
    float ang, angV, settleT, finalDist;

    // clothes store
    int bank, storeFrom, storeTab, sbx, sby, sbw, sbh;       // bank: electrons kept across runs
    final int[] inv = {1, 1, 1}, worn = {0, 0, 0};           // per tab: owned items (bit mask) and the item being worn
    float storeMsgT, jetPreview;
    String storeMsg = "";

    // formula archive
    long ownedMask;                              // bit i set: formula i is yours (up to 64 formulas)
    int tickets, pending = -1;                   // scratch tickets in hand, and the formula under a ticket you opened but did not finish
    long plainMask, holoMask, goldMask;                     // bit i set: you have the holo / gold finish of formula i
    int pendingFin, selFin, lastPay;             // finish under the open ticket, finish of the card on screen, what a duplicate paid
    int revealKind;                              // 0 viewing, 1 a new formula, 2 a duplicate (paid in electrons), 3 a new finish of one you had
    int tbx, tby, tbw, tbh;                      // the scratch-a-ticket button in the archive
    static final int DUPLICATE_PAY = 10;
    int archFrom, selCard, mcols, mrows, cleared;
    boolean selScratched, dragging;
    float cardZoom = 1f, revealAt;                          // the card leans in a little while you scratch it
    static final float SCRATCH_ZOOM = 1.08f;
    final boolean[] mask = new boolean[2000];    // scratched-off cells of the open card
    float bannerT;
    int abx, aby, abw, abh, cbx, cby, cbw, cbh, scx, scy, scw, sch;   // buttons and the big card

    // settings
    boolean hapticsOn = true, soundOn = true, musicOn = true, confirmReset, female;
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
        if (female) {                               // copper hair
            OVR['B'] = Color.rgb(165, 78, 40);
            OVR['b'] = Color.rgb(215, 125, 70);
        }
    }

    Game(Context c) {
        super(c);
        prefs = c.getSharedPreferences("formulaheist", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        if (prefs.contains("formulas")) {
            Object saved = prefs.getAll().get("formulas");      // older saves stored this as an int
            ownedMask = saved instanceof Number ? ((Number) saved).longValue() : 0L;
            tickets = prefs.getInt("tickets", 0);
            pending = prefs.getInt("pending", -1);
            holoMask = prefs.getLong("holo", 0L);
            goldMask = prefs.getLong("gold", 0L);
            pendingFin = prefs.getInt("pendingFin", 0);
        } else {                                    // older saves: recovered cards that were not scratched yet become tickets
            int revealed = prefs.getInt("scratched", 0);
            ownedMask = revealed;
            tickets = Integer.bitCount(prefs.getInt("owned", 0) & ~revealed);
        }
        // the normal copy is its own card now; older saves count every owned card that is not holo or gold as normal
        plainMask = prefs.contains("plain") ? prefs.getLong("plain", 0L) : ownedMask & ~(holoMask | goldMask);
        bank = prefs.getInt("bank", 0);
        hapticsOn = prefs.getBoolean("haptics", true);
        soundOn = prefs.getBoolean("sound", true);
        musicOn = prefs.getBoolean("music", true);
        female = prefs.getBoolean("female", false);
        applyCharacter(female);
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

    int lastTrack = -1;

    /** run music while playing or building, menu music on the title and menu screens, silence on the death card */
    void syncMusic() {
        if (sfx == null) return;
        int t = !musicOn ? 0 : (state == PLAY || state == BUILD) ? 1 : state == DEAD ? 0 : 2;
        if (t != lastTrack) { lastTrack = t; sfx.setTrack(t); }
    }

    void start() {
        if (running) return;
        running = true;
        lastNanos = 0;
        if (sfx == null) sfx = new Sfx();
        lastTrack = -1;
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
        float from = Math.max(outX + 110, 100);
        for (float nx = from; nx < w + 20; nx += 70 + rnd.nextInt(80)) addSpawn(nx);
        for (int i = 0; i < 4; i++) addNpc(outX + 26 + i * 13, GUARD, -1);   // the guards: inside, just past the wall, watching you
    }

    /** mode: 0 stands alone, 1 walks left, 2 walks right, 3 stands in a group */
    /** guards watch you through the wall, run away from you when you run, and are thrown over when you break through */
    void stepGuard(Npc n, float dt) {
        if (n.pointT > 0) n.pointT -= dt;
        else if ((n.nextPoint -= dt) <= 0) { n.pointT = 0.8f + rnd.nextFloat() * 1.2f; n.nextPoint = 1.5f + rnd.nextFloat() * 4f; }
        if (state == PLAY && breaking && !n.falling) { n.running = true; n.vw = GUARD_RUN; }
        if (!n.falling) {
            // inside the building they ride the world's scroll, and run away to the right when you run
            n.x += (n.running ? GUARD_RUN - speed * ppm : -speed * ppm) * dt;
            if (n.x > w + 30) n.x = w + 1000;      // out of sight, removed by the caller below
            return;
        }
        n.fvy -= GRAVITY * ppm * dt;
        n.fy += n.fvy * dt;
        if (n.fy <= 0) {
            n.fy = 0;
            if (n.fvy < -70f && n.bounces < 2) { n.fvy = -n.fvy * 0.5f; n.bounces++; n.angV *= 0.6f; }
            else { n.fvy = 0; n.settled = true; }
        }
        if (!n.settled) n.ang += n.angV * dt;
        else n.ang += (90f + 360f * Math.round((n.ang - 90f) / 360f) - n.ang) * Math.min(1f, 10f * dt);   // face down
        // they were running with the world; friction slows them while the world keeps going, so they get dragged back
        n.vw *= Math.max(0f, 1f - (n.settled ? 4f : 0.3f) * dt);
        n.x += (n.vw - speed * ppm) * dt;
    }

    Npc addNpc(float nx, int mode, int face) {
        Npc n = new Npc();
        n.x = nx; n.phase = rnd.nextFloat() * 6f;
        n.mode = mode; n.face = face;
        n.walk = mode == 1 ? -28f : mode == 2 ? 38f : 0f;
        n.nextPoint = mode == 1 || mode == 2 ? 1e9f : 0.5f + rnd.nextFloat() * 7f;
        n.coat = rnd.nextInt(COATS.length); n.hair = rnd.nextInt(HAIRS.length);
        n.female = mode != GUARD && rnd.nextInt(5) < 2;     // about two in five of the scientists in the background are women
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
        coins.clear(); zappers.clear(); missiles.clear(); crates.clear(); scrolls.clear(); bolts.clear(); hazards.clear();
        nextParch = 70; bannerT = 0;
        for (int i = 0; i < NP; i++) pl[i] = 0;
        veh = null; gravDir = 1; invuln = 0; flash = 0; shake = 0; hitstop = 0; hintT = 0;
        nextCrate = 80; nextSection = 70; thrusting = false;
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

    float hTop() { return veh == null ? SH_TOP : veh.hTop(); }

    float hBot() { return veh == null ? SH_BOT : veh.hBot(); }


    /** vehicles that stick to a surface and switch which one gravity points at */
    boolean flips() { return veh != null && veh.flips(); }

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
        for (Npc n : npcs) {                          // the guards behind you are thrown off their feet
            if (n.mode != GUARD || n.falling) continue;
            n.falling = true; n.running = false;
            n.fvy = 60f + rnd.nextFloat() * 70f;
            n.angV = 420f + rnd.nextFloat() * 260f;
            n.vw = n.vw > 0 ? n.vw : GUARD_RUN * 0.6f;      // thrown on, in the direction they were running
        }
    }

    /** how likely formula i is to be pulled: ones you do not have yet are three times as likely */
    int pullWeight(int i) { return (ownedMask >> i & 1) == 0 ? 3 : 1; }

    /** a random formula, weighted by pullWeight, so repeats are possible but rarer */
    int rollFormula() {
        int total = 0;
        for (int i = 0; i < NF; i++) total += pullWeight(i);
        int r = rnd.nextInt(total);
        for (int i = 0; i < NF; i++) {
            r -= pullWeight(i);
            if (r < 0) return i;
        }
        return 0;
    }

    /** the finish of a pull: 2 secret gold, 1 holo, else 0 normal */
    int rollFinish(int i) {
        if (rnd.nextInt(GOLD_ODDS) == 0) return 2;
        return rnd.nextInt(HOLO_ODDS) == 0 ? 1 : 0;
    }

    /** whether you own the given finish (0 normal, 1 holo, 2 gold) of formula i */
    boolean hasFin(int i, int fin) { return ((fin == 0 ? plainMask : fin == 1 ? holoMask : goldMask) >> i & 1) != 0; }

    /** the first finish you own of formula i at or after `from`, wrapping round; -1 if you own none */
    int nextFin(int i, int from) {
        for (int k = 0; k < 3; k++) { int f = (from + k) % 3; if (hasFin(i, f)) return f; }
        return 0;
    }

    boolean ticketReady() { return tickets > 0 || pending >= 0; }

    void saveFormulas() {
        prefs.edit().putLong("formulas", ownedMask).putInt("tickets", tickets).putInt("pending", pending).putInt("bank", bank)
                .putLong("plain", plainMask).putLong("holo", holoMask).putLong("gold", goldMask).putInt("pendingFin", pendingFin).apply();
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

    // ---------- settings: haptics, sounds, music and a guarded reset ----------

    boolean settingsButtonHit(float gx, float gy) {
        if (!menuButtonsShown()) return false;
        return gx >= gbx && gx < gbx + gbw && gy >= gby && gy < gby + gbh;
    }

    void drawSettingsButton(Canvas c) {
        rectPx(c, gbx, gby, gbw, gbh, Color.rgb(8, 10, 20));
        rectPx(c, gbx + 1, gby + 1, gbw - 2, gbh - 2, Color.rgb(44, 54, 82));
        text(c, "SETTINGS", gbx + gbw / 2f, gby + 3, 1, Color.WHITE, 1);
    }

    /** rows of the settings screen: x, y, width, height */
    int[] settingsRow(int i) { return new int[] {w / 2 - 90, 22 + i * 21, 180, 18}; }

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
        for (int i = 0; i < 5; i++) {
            int[] r = settingsRow(i);
            if (gx < r[0] || gx >= r[0] + r[2] || gy < r[1] || gy >= r[1] + r[3]) continue;
            if (i == 0) { female = !female; applyCharacter(female); applyOutfit(); sfx(S_COIN); }
            else if (i == 1) { hapticsOn = !hapticsOn; buzz(30); }
            else if (i == 2) { soundOn = !soundOn; sfx(S_COIN); }
            else if (i == 3) { musicOn = !musicOn; lastTrack = -1; }
            else { confirmReset = true; return; }
            prefs.edit().putBoolean("haptics", hapticsOn).putBoolean("sound", soundOn).putBoolean("music", musicOn).putBoolean("female", female).apply();
            return;
        }
    }

    /** erase formulas, tickets, clothes, electrons and the best distance; the settings stay */
    void resetProgress() {
        SharedPreferences.Editor e = prefs.edit();
        for (String k : new String[] {"best", "bank", "inv0", "inv1", "inv2", "worn0", "worn1", "worn2", "formulas", "tickets", "pending", "owned", "scratched", "outfits", "outfit", "holo", "gold", "plain", "pendingFin"}) e.remove(k);
        e.apply();
        best = 0; bank = 0; ownedMask = 0; tickets = 0; pending = -1; plainMask = 0; holoMask = 0; goldMask = 0; pendingFin = 0;
        for (int t = 0; t < 3; t++) { inv[t] = 1; worn[t] = 0; }
        applyOutfit();
    }

    /** dark grid backdrop shared by the settings, store and archive screens */
    void menuBackdrop(Canvas c) {
        rectPx(c, 0, 0, w, h, Color.rgb(16, 20, 34));
        for (int gx = 0; gx < w; gx += 16) rectPx(c, gx, 0, 1, h, Color.rgb(20, 26, 42));
    }

    /** the Archive, Store and Settings buttons only show on the title screen and the death card */
    boolean menuButtonsShown() {
        return state == START || (state == DEAD && stateTime > CARD_DELAY);
    }

    void drawSettings(Canvas c) {
        menuBackdrop(c);
        text(c, "SETTINGS", 8, 5, 1, Color.rgb(255, 215, 120), 0);
        closeButton(c);
        String[] names = {"CHARACTER", "HAPTICS", "SOUNDS", "MUSIC", "RESET PROGRESS"};
        boolean[] on = {true, hapticsOn, soundOn, musicOn, false};
        for (int i = 0; i < 5; i++) {
            int[] r = settingsRow(i);
            boolean reset = i == 4;
            rectPx(c, r[0], r[1], r[2], r[3], Color.rgb(8, 10, 20));
            rectPx(c, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, reset ? Color.rgb(90, 36, 40) : Color.rgb(34, 42, 66));
            text(c, names[i], r[0] + 8, r[1] + 6, 1, Color.WHITE, 0);
            if (i == 0) {
                rectPx(c, r[0] + r[2] - 50, r[1] + 3, 44, r[3] - 6, Color.rgb(8, 10, 20));
                rectPx(c, r[0] + r[2] - 49, r[1] + 4, 42, r[3] - 8, female ? Color.rgb(160, 60, 70) : Color.rgb(60, 90, 160));
                text(c, female ? "FEMALE" : "MALE", r[0] + r[2] - 28, r[1] + 6, 1, Color.WHITE, 1);
            } else if (!reset) {
                rectPx(c, r[0] + r[2] - 38, r[1] + 3, 32, r[3] - 6, Color.rgb(8, 10, 20));
                rectPx(c, r[0] + r[2] - 37, r[1] + 4, 30, r[3] - 8, on[i] ? Color.rgb(50, 150, 90) : Color.rgb(120, 50, 50));
                text(c, on[i] ? "ON" : "OFF", r[0] + r[2] - 22, r[1] + 6, 1, Color.WHITE, 1);
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

    // ---------- clothes store: spend electrons on shirts, pants and jetpacks ----------

    boolean storeButtonHit(float gx, float gy) {
        if (!menuButtonsShown()) return false;
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
                if (tab == 2) jetPreview = 1.0f;
            } else if (bank >= ICOST[tab][i]) {
                bank -= ICOST[tab][i]; inv[tab] |= 1 << i; worn[tab] = i; applyOutfit(); saveStore();
                flash = 0.15f; sfx(S_DONE); buzz(30);
                for (int k = 0; k < 30; k++) confetti(r[0] + r[2] / 2f, r[1] + 20);
                if (tab == 2) jetPreview = 1.0f;
            } else {
                storeMsg = "NEED " + (ICOST[tab][i] - bank) + " MORE E-"; storeMsgT = 1.6f;
                shake = 2f; sfx(S_WARN);
            }
            return;
        }
    }

    void drawStore(Canvas c) {
        menuBackdrop(c);
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
            drawHairTail(c, 0, 0);
            drawHairLock(c, 0, 0);
            outlined(c, SPR_HEAD, 0, 0);
            drawBust(c, 0, 0);
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
        if (!menuButtonsShown()) return false;
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
                selCard = i; selFin = nextFin(i, 0); selScratched = true; revealKind = 0;
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
            pendingFin = rollFinish(pending);
            saveFormulas();
        }
        selCard = pending; selFin = pendingFin; selScratched = false; revealKind = 0;
        mcols = scw / 4; mrows = sch / 4;
        java.util.Arrays.fill(mask, false);
        cleared = 0; dragging = false;
        state = SCRATCH;
    }

    boolean anotherReady() { return selScratched && revealKind != 0 && tickets > 0; }

    void scratchDown(float gx, float gy) {
        if (closeHit(gx, gy)) { state = ARCHIVE; dragging = false; return; }
        if (anotherReady() && gx >= w / 2f - 64 && gx < w / 2f + 64 && gy >= h - 15 && gy < h - 2) { openTicket(); return; }
        if (!selScratched && gx >= scx && gx < scx + scw && gy >= scy && gy < scy + sch) {
            dragging = true;
            scratchAt(gx, gy);
        } else if (selScratched && gx >= scx && gx < scx + scw + 12 && gy >= scy - 12 && gy < scy + sch) {
            int nf = nextFin(selCard, selFin + 1);     // tap the card to bring the next finish you own to the front
            if (nf >= 0) selFin = nf;
        }
    }

    void scratchAt(float gx, float gy) {
        if (selScratched) return;
        // the card is drawn zoomed around its centre, so undo that to find the spot under the finger
        float zcx = scx + scw / 2f, zcy = scy + sch / 2f;
        gx = zcx + (gx - zcx) / cardZoom;
        gy = zcy + (gy - zcy) / cardZoom;
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
        selScratched = true; dragging = false; revealAt = clock;
        long bit = 1L << selCard;
        boolean newFin = !hasFin(selCard, selFin);
        if ((ownedMask & bit) == 0) {
            revealKind = 1;
        } else if (newFin) {
            revealKind = 3;               // a holo or gold version of one you already had
        } else {
            revealKind = 2;
            lastPay = DUP_PAY * (selFin == 2 ? 5 : selFin == 1 ? 2 : 1);
            bank += lastPay;              // a repeat pays out in electrons
        }
        ownedMask |= bit;
        if (selFin == 0) plainMask |= bit;
        if (selFin == 1) holoMask |= bit;
        if (selFin == 2) goldMask |= bit;
        pending = -1; pendingFin = 0;
        saveFormulas();
        boolean big = revealKind != 2;
        flash = 0.2f + (big ? selFin * 0.1f : 0f); shake = 2f + (big ? selFin * 1.5f : 0);
        sfx(big ? S_FANFARE : S_DONE);
        buzz(big && selFin > 0 ? 45 : 25);
        int bits = big ? 40 + selFin * 30 : 16;
        for (int i = 0; i < bits; i++) confetti(scx + scw / 2f, scy + sch / 2f);
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
        String label = "FORMULAS " + Long.bitCount(ownedMask) + "/" + NF;
        if (fresh && ((int) (clock * 1.6f) & 1) == 0) label = "SCRATCH A TICKET";
        text(c, label, abx + abw / 2f, aby + 3, 1, Color.WHITE, 1);
    }

    // ---------- the prism on every scratch card ----------
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
        c.restore();
    }

    // ---------- card rarity and finishes ----------
    static final int PAPER = Color.rgb(236, 222, 180);
    static final int[] HOLO_BANDS = {Color.argb(0, 255, 255, 255), Color.argb(110, 255, 90, 120), Color.argb(110, 255, 220, 90),
            Color.argb(110, 110, 255, 150), Color.argb(110, 90, 200, 255), Color.argb(110, 200, 120, 255), Color.argb(0, 255, 255, 255)};
    static final int[] GOLD_BANDS = {Color.rgb(196, 146, 46), Color.rgb(255, 226, 130), Color.rgb(214, 168, 62), Color.rgb(255, 240, 175), Color.rgb(196, 146, 46)};

    /** the colour that marks a finish: parchment, cycling rainbow, gold */
    int finCol(int fin) {
        if (fin == 0) return Color.rgb(176, 150, 112);
        if (fin == 2) return Color.rgb(255, 205, 90);
        return Color.HSVToColor(new float[] {(clock * 120f) % 360f, 0.5f, 1f});
    }

    /** card paper for a finish: plain parchment, parchment under sliding rainbow foil bands, or shining gold. Sparkles on foil. */
    void cardPaper(Canvas c, float x, float y, float cw, float chh, int fin, int seed) {
        float slide = (clock * 40f) % 80f;
        if (fin == 2) {
            p.setShader(new LinearGradient(x - slide, y, x - slide + 40, y + 40, GOLD_BANDS, null, Shader.TileMode.MIRROR));
            rectPx(c, x, y, cw, chh, Color.WHITE);
            p.setShader(null);
        } else {
            rectPx(c, x, y, cw, chh, PAPER);
            if (fin == 1) {
                p.setShader(new LinearGradient(x + slide, y, x + slide + 50, y + 30, HOLO_BANDS, null, Shader.TileMode.MIRROR));
                rectPx(c, x, y, cw, chh, Color.WHITE);
                p.setShader(null);
            }
        }
        if (fin == 0) return;
        // twinkles: a few little crosses that move every half second
        int tick = (int) (clock * 2f);
        int n = Math.max(1, (int) (cw * chh / 900f));
        for (int k = 0; k < n; k++) {
            int hsh = (seed * 73856093) ^ ((tick + k * 7) * 19349663) ^ (k * 83492791);
            hsh ^= hsh >>> 13; hsh *= 0x5bd1e995; hsh ^= hsh >>> 15;
            float sx = x + 1 + ((hsh & 0xFFFF) % Math.max(1, (int) cw - 2));
            float sy = y + 1 + (((hsh >>> 16) & 0xFFFF) % Math.max(1, (int) chh - 2));
            float life = (clock * 2f) % 1f;
            int a = (int) (255 * Math.sin(life * Math.PI));
            int col = Color.argb(a, 255, 255, 255);
            rectPx(c, sx, sy, 1, 1, col);
            if (cw > 40) { rectPx(c, sx - 1, sy, 1, 1, col); rectPx(c, sx + 1, sy, 1, 1, col); rectPx(c, sx, sy - 1, 1, 1, col); rectPx(c, sx, sy + 1, 1, 1, col); }
        }
    }

    void drawArchive(Canvas c) {
        menuBackdrop(c);
        text(c, "FORMULA ARCHIVE", 8, 5, 1, Color.rgb(255, 215, 120), 0);
        int owned = Long.bitCount(ownedMask);
        String tally = owned + "/" + NF + " RECOVERED   HOLO " + Long.bitCount(holoMask) + "   GOLD " + Long.bitCount(goldMask);
        text(c, owned == NF ? "EVERY FORMULA IS BACK. SHARE THEM WITH THE WORLD!" : tally, 8, 13, 1, owned == NF ? Color.rgb(120, 255, 160) : Color.rgb(150, 190, 215), 0);
        closeButton(c);
        for (int i = 0; i < NF; i++) {
            int[] r = cardRect(i);
            boolean have = (ownedMask >> i & 1) != 0;
            rectPx(c, r[0], r[1], r[2], r[3], Color.rgb(8, 10, 20));
            if (have) {
                // the finishes you own are stacked: the first in front, the others peeking out up and to the right behind it
                int[] fins = new int[3]; int nf = 0;
                for (int f = 0; f < 3; f++) if (hasFin(i, f)) fins[nf++] = f;
                int off = 2 * (nf - 1);
                for (int k = nf - 1; k >= 0; k--) {
                    int lx = r[0] + 2 * k, ly = r[1] + off - 2 * k, lw = r[2] - off, lh = r[3] - off;
                    if (k > 0) rectPx(c, lx, ly, lw, lh, Color.rgb(8, 10, 20));
                    cardPaper(c, lx + 1, ly + 1, lw - 2, lh - 2, fins[k], i);
                    rectPx(c, lx + 1, ly + 1, lw - 2, 1, finCol(fins[k]));
                }
                int fx = r[0], fy = r[1] + off, fw = r[2] - off, fh = r[3] - off;
                float fs = fitSize(FORMD[i], fw - 3, 8f);
                if (fs >= 4.6f) ov(FORMD[i], fx + fw / 2f, fy + fh / 2f, fs, Color.rgb(70, 42, 28), false);
                else text(c, "" + (i + 1), fx + fw / 2f, fy + fh / 2f - 2, 1, Color.rgb(90, 60, 40), 1);
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

    /** spinning spotlight rays behind a newly found card: 12 wedges turning slowly around (x, y), faded in by k.
     *  fin 0 warm, 1 rainbow (holo), 2 bright gold and faster */
    void spotlight(Canvas c, float x, float y, float k, int fin) {
        float r = w + h;                                   // long enough to reach every corner
        float spin = clock * (fin == 2 ? 1.1f : 0.6f);     // radians per second
        float half = (float) Math.PI / 24;                 // each wedge is 15 degrees wide, with 15 degree gaps
        p.setStyle(Paint.Style.FILL);
        p.setColor(fin == 2 ? Color.argb((int) (80 * k), 255, 220, 90) : Color.argb((int) (55 * k), 255, 205, 110));
        for (int i = 0; i < 12; i++) {
            if (fin == 1) p.setColor(Color.HSVToColor((int) (60 * k), new float[] {(i * 30f + clock * 60f) % 360f, 0.6f, 1f}));
            float a = spin + i * (float) Math.PI / 6;
            path.reset();
            path.moveTo(x, y);
            path.lineTo(x + (float) Math.cos(a - half) * r, y + (float) Math.sin(a - half) * r);
            path.lineTo(x + (float) Math.cos(a + half) * r, y + (float) Math.sin(a + half) * r);
            path.close();
            c.drawPath(path, p);
        }
    }

    void drawScratch(Canvas c) {
        rectPx(c, 0, 0, w, h, Color.rgb(16, 20, 34));
        if (revealKind == 1 || revealKind == 3) spotlight(c, scx + scw / 2f, scy + sch / 2f, Math.min(1f, (clock - revealAt) * 3f), selFin);
        closeButton(c);
        if (revealKind == 1 || revealKind == 3) {
            String big = revealKind == 1 ? "NEW!" : "NEW " + FINNAME[selFin] + "!";
            text(c, big, w / 2f, 8, 2, selFin == 0 ? Color.rgb(255, 215, 120) : selFin == 2 ? Color.rgb(255, 230, 120) : finCol(1), 1);
        } else {
            String head = !selScratched ? "SCRATCH TO REVEAL" : revealKind == 2 ? "DUPLICATE! +" + lastPay + " E-" : "FORMULA";
            text(c, head, 8, 8, 1, revealKind == 2 ? Color.rgb(120, 235, 255) : Color.rgb(255, 215, 120), 0);
        }
        if (selScratched) {
            // rarity and finish, top right next to the close button
            String tag = FINNAME[selFin];
            if (revealKind == 1 || revealKind == 3) text(c, tag, 8, 8, 1, finCol(selFin), 0);   // the big header is centred
            else text(c, tag, cbx - 6, 8, 1, finCol(selFin), 2);
        }
        // the other finishes you own of this formula are stacked behind it, peeking out up and to the right
        if (selScratched) {
            int k = 0;
            for (int f = 0; f < 3; f++) {
                if (f == selFin || !hasFin(selCard, f)) continue;
                k++;
            }
            int j = k;
            for (int f = 2; f >= 0; f--) {
                if (f == selFin || !hasFin(selCard, f)) continue;
                float ox = 4f * j, oy = -4f * j;
                rectPx(c, scx - 2 + ox, scy - 2 + oy, scw + 4, sch + 4, Color.rgb(8, 10, 20));
                cardPaper(c, scx + ox, scy + oy, scw, sch, f, selCard);
                rectPx(c, scx + ox, scy + oy, scw, 1, finCol(f));
                j--;
            }
        }
        // the frame: dark until it is revealed, then the finish colour shows on the inner edge
        rectPx(c, scx - 2, scy - 2, scw + 4, sch + 4, Color.rgb(8, 10, 20));
        if (selScratched) {
            int fc = finCol(selFin);
            rectPx(c, scx - 1, scy - 1, scw + 2, 1, fc); rectPx(c, scx - 1, scy + sch, scw + 2, 1, fc);
            rectPx(c, scx - 1, scy - 1, 1, sch + 2, fc); rectPx(c, scx + scw, scy - 1, 1, sch + 2, fc);
        }
        int ink = selFin == 2 ? Color.rgb(84, 50, 12) : Color.rgb(70, 42, 28);
        boolean clip = !selScratched;
        if (!clip) {
            cardPaper(c, scx, scy, scw, sch, selFin, selCard);
        } else {
            prismScene(c, scx, scy, scw, sch, 1f, 0f, 0f);
            // only the scratched-off cells show the card (its foil shows through as you scratch) and its writing
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
            cardPaper(c, scx, scy, scw, sch, selFin, selCard);
        }
        textOut(c, FNAME[selCard], scx + scw / 2f, scy + 8, 1, selFin == 2 ? Color.rgb(110, 66, 20) : Color.rgb(140, 96, 56), 1, false);
        wrapOut(c, FDESC[selCard], scx + scw / 2f, scy + 58, 1, ink, scw - 16, false);
        if (clip) c.restore();
        ov(FORMD[selCard], scx + scw / 2f, scy + 34, fitSize(FORMD[selCard], scw - 16, 17f), ink, clip);
        if (anotherReady()) {
            boolean blink = ((int) (clock * 3) & 1) == 0;
            rectPx(c, w / 2 - 64, h - 15, 128, 13, Color.rgb(8, 10, 20));
            rectPx(c, w / 2 - 63, h - 14, 126, 11, blink ? Color.rgb(130, 100, 30) : Color.rgb(44, 54, 82));
            text(c, "SCRATCH ANOTHER (" + tickets + ")", w / 2f, h - 12, 1, Color.WHITE, 1);
        }
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

    /** base pairs fly forward and wipe out a zapper or a missile they touch */
    void updateBolts(float dt) {
        for (int i = bolts.size() - 1; i >= 0; i--) {
            Bolt b = bolts.get(i);
            b.x += 220f * dt;
            boolean gone = b.x > w + 10;
            for (int j = zappers.size() - 1; j >= 0 && !gone; j--) {
                Zapper z = zappers.get(j);
                float hx = z.hx(), hy = z.hy();
                if (segDist(b.x, b.y, z.x - hx, z.y - hy, z.x + hx, z.y + hy) < 7f) {
                    zappers.remove(j);
                    for (int k = 0; k < 14; k++) burst(b.x, b.y);
                    sfx(S_HIT);
                    gone = true;
                }
            }
            for (int j = hazards.size() - 1; j >= 0 && !gone; j--) {
                Hazard hz = hazards.get(j);
                if (hz.cutBy(this, b.x, b.y)) {
                    hazards.remove(j);
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

    void press() {
        if (veh != null) {
            veh.press();
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
        syncMusic();
        // the jetpack sound plays exactly while the flame is drawn, a vehicle hums the whole ride,
        // and a jetpack picked in the store plays a short sample
        if (jetPreview > 0) jetPreview -= dt;
        boolean flame = hitstop <= 0 && thrusting && state == PLAY && jumpDelay <= 0 && veh == null;
        boolean riding = hitstop <= 0 && state == PLAY && veh != null;
        boolean sample = state == STORE && jetPreview > 0;
        if (sfx != null) {
            // incoming missile: loudest as it passes the player, silent at the spawn edge and off the left edge
            float near = 0;
            for (Missile m : missiles) {
                if (!m.flying) continue;
                float f = m.x >= playerX ? (w - m.x) / (w - playerX) : (m.x + ppm) / (playerX + ppm);
                near = Math.max(near, Math.max(0f, Math.min(1f, f)));
            }
            sfx.setMissile(soundOn && near > 0 && (state == PLAY || state == BUILD || state == DEAD), near);
        }
        if (sfx != null) sfx.setJet(soundOn && (flame || riding || sample), riding ? 8 + veh.kind : worn[2]);
        // the scratch tone climbs an octave as the foil comes off (the card reveals at 60 percent)
        if (sfx != null) sfx.setScratch(soundOn && state == SCRATCH && dragging && !selScratched,
                1f + Math.min(1f, cleared / Math.max(1f, mcols * mrows * 0.6f)));
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    // ---------- physics ----------

    void update(float rdt) {
        clock += rdt;
        stateTime += rdt;
        if (worn[2] == RAINBOW_PACK) applyOutfit();      // the rainbow jetpack cycles colours
        if (female) stepBust(Math.min(rdt, 0.033f));
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
            if (n.mode == GUARD) {
                stepGuard(n, rdt);
                if (n.x < -30 || n.x > w + 500) npcs.remove(i);
                continue;
            }
            n.x += (waiting ? 0f : n.walk * rdt) - nsc;
            if (n.pointT > 0) n.pointT -= rdt;
            else if ((n.nextPoint -= rdt) <= 0) { n.pointT = 0.8f + rnd.nextFloat() * 1.2f; n.nextPoint = 2f + rnd.nextFloat() * 7f; }
            if (npcs.get(i).x < -10 || npcs.get(i).x > w + 60) npcs.remove(i);
        }

        float dt = rdt;
        if (state == DEAD) { updateDead(dt); return; }
        if (state == ARCHIVE || state == SCRATCH || state == STORE || state == SETTINGS) {
            if (storeMsgT > 0) storeMsgT -= dt;
            float zt = state == SCRATCH && dragging && !selScratched ? SCRATCH_ZOOM : 1f;
            cardZoom += (zt - cardZoom) * Math.min(1f, dt * 12f);     // eases in and back out in about a quarter second
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
        if (veh != null) veh.tick(rdt);
        if (landT > 0) landT -= rdt;
        if (telePulse > 0) telePulse -= rdt;
        boolean playing = state == PLAY || state == BUILD;
        float r = veh == null ? 0.3f * ppm : veh.radius();

        // vertical motion: gravity, thrust (on foot only), speed limited
        if (jumpDelay > 0) jumpDelay -= dt;
        boolean fire = thrusting && state == PLAY && veh == null && jumpDelay <= 0;
        ay = veh != null ? veh.accel(dt) : GRAVITY * gravDir - (fire ? THRUST : 0);
        vy += ay * dt;
        float vmin = flips() ? -VFALL : -VMAX;
        if (vy > VFALL) vy = VFALL;
        if (vy < vmin) vy = vmin;
        y += vy * ppm * dt;
        onFloor = false;
        float lo = ceilY + hTop(), hi = floorY - hBot();
        boolean bouncy = veh != null && veh.bounces();
        if (y < lo) {
            y = lo;
            if (vy < 0) {
                if (veh != null) veh.bumpCeiling(vy);
                vy = 0;
            }
            if (flips()) onFloor = true;
        }
        if (y > hi) {
            y = hi;
            if (vy > 0) {
                if (bouncy && playing) {
                    veh.bounce(vy);
                } else {
                    if (veh == null && vy > 2.5f) sfx(S_LAND);     // a faint thud on every landing
                    if (veh == null && vy > 9f) {
                        shake = Math.max(shake, 1.5f);
                        landT = 0.12f;
                        for (int i = 0; i < 6; i++)       // dust kicked out along the floor, not embers
                            puff(playerX - 3, y + 10, (i % 2 == 0 ? -1f : 1f) * (1.5f + rnd.nextFloat() * 2f) - speed * 0.3f, -0.4f - rnd.nextFloat(), Color.rgb(160, 150, 135));
                    }
                    vy = 0;
                }
            }
            if (!bouncy) onFloor = true;
        }
        if (bouncy && y >= hi - 0.5f) onFloor = true;

        if (fire) emitExhaust();
        if (veh != null) veh.update(dt);
        for (int i = 0; i < NP; i++) {
            if (pl[i] > 0) {
                pl[i] -= dt;
                px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
            }
        }

        if (tipT > 0) tipT -= rdt;
        bonus = Math.max(0f, bonus - 4.5f * dt);
        speed = breaking ? Math.min(5f, 2f + stateTime * 8f) : 5f + Math.min(dist / 80f, 7f) + bonus;   // outside he tops out at the speed the run starts at
        float scroll = speed * ppm * dt;
        outX -= scroll;
        if (breaking && outX <= playerX + 8f) breach();
        if (!breaking) dist += speed * dt;
        bgOff += scroll * 0.25f;
        if (onFloor && (veh == null || veh.walks())) runPhase += dt * 11f;     // steps at a fixed rate, they do not speed up with the world

        if (!breaking) {
            spawnLeft -= scroll;
            if (spawnLeft <= 0) spawn();
        }

        for (int i = crates.size() - 1; i >= 0; i--) {
            Crate k = crates.get(i);
            k.x -= scroll;
            float dx = k.x - playerX, dy = k.y - y;
            if (state == PLAY && veh == null && dx * dx + dy * dy < (r + 8) * (r + 8)) {
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
            float hx = z.hx(), hy = z.hy();
            if (state == PLAY && invuln <= 0 && (segDist(playerX, y, z.x - hx, z.y - hy, z.x + hx, z.y + hy) < r + 0.06f * ppm
                    || circleHitsSquare(playerX, y, r, z.x - hx, z.y - hy, 4f)
                    || circleHitsSquare(playerX, y, r, z.x + hx, z.y + hy, 4f))) hit();
        }

        for (int i = hazards.size() - 1; i >= 0; i--) {
            Hazard hz = hazards.get(i);
            hz.x -= scroll;
            if (hz.x + hz.w < -40) { hazards.remove(i); continue; }
            hz.update(this, dt);
            if (state == PLAY && invuln <= 0 && hz.hits(this, r)) hit();
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

    /** the widest row of a vehicle sprite; rows can differ in length (the screw's mast rows are wider than the pod) */
    static int rowsWidth(String[] rows) {
        int m = 0;
        for (String s : rows) m = Math.max(m, s.length());
        return m;
    }

    /** the sprite letter at (r, c), or '.' past the end of a short row */
    static char rowCh(String[] rows, int r, int c) { return c < rows[r].length() ? rows[r].charAt(c) : '.'; }

    void startBuild(int type) {
        building = Vehicle.KINDS[type].make(this);
        building.kind = type;
        state = BUILD;
        buildT = 0;
        invuln = BUILD_TIME + 1f;
        flash = 0.12f;
        sfx(S_BUILD);
        String[] rows = building.rows();
        nb = 0;
        for (int r0 = 0; r0 < rows.length; r0 += 3) {
            int wide = rowsWidth(rows);
            for (int c0 = 0; c0 < wide; c0 += 3) {
                boolean any = false;
                for (int rr = 0; rr < 3 && r0 + rr < rows.length; rr++)
                    for (int cc = 0; cc < 3 && c0 + cc < wide; cc++)
                        if (rowCh(rows, r0 + rr, c0 + cc) != '.') any = true;
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
        veh = building;
        state = PLAY;
        gravDir = 1;
        vy = 0;
        veh.start();
        flash = 0.3f;
        shake = 4f;
        hintT = 2.8f;
        sfx(S_DONE);
        buzz(40);
        for (int i = 0; i < 24; i++) burst(playerX, y);
    }

    void hit() {
        if (state != PLAY || invuln > 0) return;
        if (veh != null) breakVehicle(); else die();
    }

    void breakVehicle() {
        for (int i = 0; i < 40; i++) burst(playerX, y);
        veh = null; gravDir = 1;
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
        veh = null; gravDir = 1; thrusting = false;
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
        for (Hazard hz : hazards) hz.x -= sc;
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

    /** a soft puff of dust or air in the given colour: it spreads along (vx, vy) in metres per second and fades */
    void puff(float bx, float by, float vx, float vy, int col) {
        int k = pNext++ % NP;
        px[k] = bx; py[k] = by;
        pvx[k] = (vx + (rnd.nextFloat() - 0.5f) * 1.2f) * ppm;
        pvy[k] = (vy + (rnd.nextFloat() - 0.5f) * 1.2f) * ppm;
        pl[k] = 0.35f + rnd.nextFloat() * 0.25f;
        pcol[k] = col | 0xFF000000;
    }

    /** the colour of an ember: it takes the worn jetpack's exhaust colours (red after a crash) */
    int emberCol(float a, int i) {
        int al = (int) (a * 255) << 24;
        if (state == DEAD) return Color.argb((int) (a * 255), 255, 120, 60);
        int t = Math.max(0, Math.min(7, worn[2]));
        if (t == 7) return (Color.HSVToColor(new float[] {(clock * 360f + i * 20f) % 360f, 0.9f, 1f}) & 0xFFFFFF) | al;
        if (t == 4) return (a > 0.6f ? Color.rgb(170, 90, 230) : Color.rgb(120, 50, 180)) & 0xFFFFFF | al;   // dark matter, kept bright enough to see
        return (FLAME[t][a > 0.6f ? 1 : 0] & 0xFFFFFF) | al;
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


    float randY(float loM, float hiM) {
        return (loM + rnd.nextFloat() * (hiM - loM)) * ppm;
    }

    void spawn() {
        float x0 = w + 0.6f * ppm;
        if (dist >= nextCrate) {
            if (veh == null) {
                Crate k = new Crate();
                k.x = x0; k.y = randY(3f, 6f); k.type = rnd.nextInt(Vehicle.KINDS.length);
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
        // every so often a whole section of one thing: coins only, or laser rails along the floor and ceiling
        if (dist >= nextSection) {
            nextSection = dist + 90 + rnd.nextInt(70);
            if (dist >= 120 && rnd.nextBoolean()) spawnRails(x0); else spawnCoinSection(x0);
            return;
        }
        int roll = rnd.nextInt(100);
        float gap = (4f - Math.min(dist / 450f, 1.4f) + rnd.nextFloat() * 2.2f) * ppm;
        if (rnd.nextInt(6) == 0) gap += 3f * ppm;       // now and then a breather with nothing in it
        // what can appear grows with distance: zappers first, then spikes, missiles, saws and laser gates
        if (roll < 36) {
            spawnCoins(x0, gap);
        } else if (roll < 60 || dist < 50) {
            spawnZappers(x0, gap);
        } else if (roll < 74) {
            spawnSpikes(x0, gap);
        } else if (roll < 83) {
            if (dist < 160) spawnZappers(x0, gap); else spawnSaw(x0, gap);
        } else if (roll < 91) {
            if (dist < 270) spawnSpikes(x0, gap); else spawnLaser(x0, gap);
        } else if (dist < 170) {
            spawnZappers(x0, gap);
        } else {
            Missile m = new Missile();
            m.warn = 1.4f; m.y = y;
            sfx(S_WARN);
            missiles.add(m);
            spawnLeft = 5f * ppm;
        }
    }

    /** one coin pattern starting at x0: a sine wave, a block, a line or an arch. Returns its length in pixels. */
    float coinPattern(float x0) {
        if (rnd.nextInt(14) == 0) {         // rare: a wall of coins from floor to ceiling, four columns thick
            for (int i = 0; i < 4; i++)
                for (float yy = ceilY + 0.5f * ppm; yy <= floorY - 0.5f * ppm; yy += 0.5f * ppm) addCoin(x0 + i * 0.5f * ppm, yy);
            return 4 * 0.5f * ppm;
        }
        int kind = rnd.nextInt(4);
        float cy = randY(3.0f, 6.0f);
        if (kind == 0) {            // sine wave, two coins thick
            for (int i = 0; i < 14; i++) for (int j = 0; j < 2; j++) addCoin(x0 + i * 0.5f * ppm, cy + (j - 0.5f) * 0.5f * ppm + (float) Math.sin(i * 0.6) * 1.3f * ppm);
            return 14 * 0.5f * ppm;
        } else if (kind == 1) {     // block, six rows
            for (int i = 0; i < 6; i++) for (int j = 0; j < 6; j++) addCoin(x0 + i * 0.5f * ppm, cy + (j - 2.5f) * 0.5f * ppm);
            return 6 * 0.5f * ppm;
        } else if (kind == 2) {     // line, three coins thick
            for (int i = 0; i < 9; i++) for (int j = 0; j < 3; j++) addCoin(x0 + i * 0.5f * ppm, cy + (j - 1) * 0.5f * ppm);
            return 9 * 0.5f * ppm;
        }
        for (int i = 0; i < 12; i++) for (int j = 0; j < 2; j++)    // arch, two coins thick
            addCoin(x0 + i * 0.5f * ppm, cy + (j - 0.5f) * 0.5f * ppm + 1.6f * ppm - (float) Math.sin(i / 11.0 * Math.PI) * 3.2f * ppm);
        return 12 * 0.5f * ppm;
    }

    void spawnCoins(float x0, float gap) {
        spawnLeft = coinPattern(x0) + gap;
    }

    /** a section of nothing but coins: four patterns in a row, then room to breathe */
    void spawnCoinSection(float x0) {
        float xc = x0;
        for (int i = 0; i < 4; i++) xc += coinPattern(xc) + 1.5f * ppm;
        spawnLeft = xc - x0 + 3f * ppm;
    }

    /** a section of laser rails: the floor and ceiling beams trade places, with coins down the middle */
    void spawnRails(float x0) {
        float len = 28f * ppm;
        hazards.add(new LaserRails(x0, len, 2.6f));
        float mid = (floorY + ceilY) / 2f;
        for (int i = 0; i < 40; i++) addCoin(x0 + 2f * ppm + i * 0.6f * ppm, mid + (float) Math.sin(i * 0.4) * 1.6f * ppm);
        spawnLeft = len + 5f * ppm;
    }

    void spawnZappers(float x0, float gap) {
        int n = (dist > 500 && rnd.nextFloat() < 0.4f) ? 2 : 1;
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
    }

    /** a row of spikes on the floor or the ceiling; later a pair that leaves a corridor to fly through */
    void spawnSpikes(float x0, float gap) {
        float wd = (3f + rnd.nextFloat() * 3f) * ppm;
        int mode = rnd.nextInt(dist > 200 ? 3 : 2);          // 0 floor, 1 ceiling, 2 both
        if (mode != 1) hazards.add(new Spikes(true, x0, floorY, wd));
        if (mode != 0) hazards.add(new Spikes(false, x0, ceilY, wd));
        spawnLeft = wd + 1.5f * ppm + gap;
    }

    /** a saw blade sliding up and down a rail */
    void spawnSaw(float x0, float gap) {
        hazards.add(new Saw(x0 + 24, randY(3.6f, 5.6f), (1.4f + rnd.nextFloat() * 1.1f) * ppm, rnd.nextFloat() * 6f));
        spawnLeft = 4f * ppm + gap;
    }

    /** a laser gate: red warning, then a full beam top to bottom with one gap to fly through */
    void spawnLaser(float x0, float gap) {
        hazards.add(new Laser(x0 + 20, randY(3.2f, 6.0f)));
        spawnLeft = 4.5f * ppm + gap;
    }

    void addCoin(float x, float yy) {
        Coin c = new Coin();
        c.x = x;
        c.y = Math.max(ceilY + 0.3f * ppm, Math.min(floorY - 0.3f * ppm, yy));
        coins.add(c);
    }

    // ---------- drawing (pixel art, drawn small then scaled up) ----------

    // ---------- the scientist's hair and chest, and the pixel font (the art itself is in Sprites) ----------

    int hairFrame() { return onFloor && state == PLAY ? ((int) (runPhase * 0.2f * 4)) & 3 : ((int) (clock * 6)) & 3; }

    /** drawn first, behind the body and the pack, so the pack stays visible */
    void drawHairTail(Canvas c, int left, int top) {
        if (female) outlined(c, TAIL_FRAMES[hairFrame()], left, top);
    }

    void drawHairLock(Canvas c, int left, int top) {
        if (!female) return;
        outlined(c, LOCK_FRAMES[hairFrame()], left, top);
    }

    /** the chest sits a little lower or higher than its rest place as it springs after the body */
    int bustPx() { return Math.max(-1, Math.min(1, Math.round(bustOff))); }

    /** draw the chest at the sprite's top left; only the free edges get an outline so it does not cut into the coat */
    void drawBust(Canvas c, int left, int top) {
        if (!female) return;
        int x = left + 11, y = top + 10 + bustPx();
        float lp = leanPx;
        leanPx = 0;
        outline = true;
        sprite(c, BUST, x + 1, y);
        sprite(c, BUST, x, y - 1);
        sprite(c, BUST, x, y + 1);
        outline = false;
        sprite(c, BUST, x, y);
        leanPx = lp;
    }

    /** a small spring: it lags the body when you jump or land, then settles; running does not move it */
    void stepBust(float dt) {
        float dv = vy - lastVy;
        lastVy = vy;
        bustV += -dv * 3.3f;
        bustV += (-400f * bustOff - 16f * bustV) * dt;
        bustOff = Math.max(-2f, Math.min(2f, bustOff + bustV * dt));
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
        if (menuButtonsShown()) { drawArchiveButton(c); drawStoreButton(c); drawSettingsButton(c); }
        out.save();
        if (shake > 0) out.translate((rnd.nextFloat() - 0.5f) * 2f * shake * unit, (rnd.nextFloat() - 0.5f) * 2f * shake * unit);
        out.drawBitmap(buf, null, dst, blit);
        boolean zoomed = state == SCRATCH && cardZoom > 1.001f;
        if (zoomed) {
            // draw the card part of the picture again, a little bigger, over itself; the formula text below gets the same scale
            float zcx = (scx + scw / 2f) * unit, zcy = (scy + sch / 2f) * unit;
            zsrc.set(scx - 2, scy - 2, scx + scw + 2, scy + sch + 2);
            zdst.set(zcx + ((scx - 2) * unit - zcx) * cardZoom, zcy + ((scy - 2) * unit - zcy) * cardZoom,
                    zcx + ((scx + scw + 2) * unit - zcx) * cardZoom, zcy + ((scy + sch + 2) * unit - zcy) * cardZoom);
            out.drawBitmap(buf, zsrc, zdst, blit);
            out.save();
            out.scale(cardZoom, cardZoom, zcx, zcy);
        }
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
        if (zoomed) out.restore();
        out.restore();
    }

    void drawScene(Canvas c) {
        drawBackground(c);
        drawFloor(c);
        drawNpcs(c);
        rectPx(c, 0, ceilY, w, floorY - ceilY, Color.argb(95, 6, 10, 24));   // push the whole backdrop back
        drawExterior(c);
        drawGuards(c);
        drawCoins(c);
        drawCrates(c);
        drawScrolls(c);
        drawZappers(c);
        drawHazards(c);
        drawMissiles(c);
        drawBolts(c);
        drawParticles(c);
        drawPlayer(c);
        if (telePulse > 0) {
            ring(c, playerX, teleFromY, telePulse / 0.25f);
            ring(c, playerX, y, telePulse / 0.25f);
        }
        if (state == BUILD) drawBuild(c);
        if ((state == PLAY && !breaking) || state == BUILD) drawHud(c);   // the readouts only show during a run, not on the title, outside or after dying
        drawHint(c);
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
            // each tree keeps its own height and slides left as he runs at the wall, a third as fast as the wall itself
            float shift = Math.round(outX * 0.3f);
            for (int J = (int) Math.floor((-14 - shift) / 14f); J * 14 + shift < w; J++)
                rectPx(c, J * 14 + shift, fl - 24 - ((J * 7) % 9 + 9) % 9, 12, 40, Color.rgb(12, 24, 28));
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
            for (float x = ox - 12; x > -12; x -= 12) rectPx(c, x, fl - 2, 1, 2, Color.rgb(40, 96, 56));
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
        // cracks in the bricks
        for (int k = 0; k < 4; k++) {
            int cx = ox + 2 + (k * 3) % 6, cy = 14 + k * 30;
            if (holeOpen && Math.abs(cy - holeY) < 24) continue;
            for (int j = 0; j < 16; j++) { rectPx(c, cx + (j % 3 == 1 ? 1 : 0), cy + j, 1, 1, Color.rgb(40, 24, 22)); }
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

    final Room[] rooms = Room.all(this);
    static final int RW = 170, PERIOD = 230;
    static final String[] DIG = new String[24];
    static {
        Random r = new Random(3);
        for (int i = 0; i < DIG.length; i++) {
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < 6; k++) b.append((char) ('0' + r.nextInt(10)));
            DIG[i] = b.toString();
        }
    }

    /** cracks, stains, soot, scratches and a mismatched repair panel, placed by panel number so they stay put as it scrolls */
    void wallWear(Canvas c, float px0, int pi, int wallTop) {
        Random r = new Random(pi * 7919L + 13);
        int kind = r.nextInt(8);
        int x = Math.round(px0) + 6 + r.nextInt(26), y = wallTop + 6 + r.nextInt(50);
        int dark = Color.rgb(14, 22, 36);
        switch (kind) {
            case 1: case 2: {                                    // a crack that runs down and branches
                int cx = x, cy = y;
                for (int k = 0; k < 22; k++) {
                    rectPx(c, cx, cy, 1, 1, dark);
                    if (k % 5 == 3) rectPx(c, cx + 1, cy, 1, 1, Color.argb(120, 130, 150, 190));
                    cy++; cx += r.nextInt(3) - 1;
                    if (k == 8 || k == 14) {
                        int bx = cx, by = cy;
                        for (int j = 0; j < 7; j++) { rectPx(c, bx, by, 1, 1, dark); bx += k == 8 ? 1 : -1; by += r.nextInt(2); }
                    }
                }
                break;
            }
            case 3: {                                            // a damp stain
                for (int k = 0; k < 6; k++)
                    rectPx(c, x - 4 + r.nextInt(8), y + k * 4, 6 + r.nextInt(8), 5, Color.argb(34, 10, 30, 30));
                rectPx(c, x, y + 6, 2, 36, Color.argb(30, 120, 90, 60));
                break;
            }
            case 4: {                                            // scorched by something that went off
                for (int k = 0; k < 5; k++)
                    rectPx(c, x - k * 2, y + 2 * k - 8, 12 + k * 4, 18 - k * 2, Color.argb(30 + k * 6, 6, 6, 10));
                rectPx(c, x + 2, y + 2, 3, 3, Color.argb(160, 255, 140, 40));
                break;
            }
            case 5: {                                            // scratches
                for (int k = 0; k < 3; k++) {
                    int sx = x + k * 3, sy = y + k * 2;
                    for (int j = 0; j < 12; j++) rectPx(c, sx + j, sy + j / 2, 1, 1, Color.argb(110, 170, 190, 220));
                }
                break;
            }
            case 6: {                                            // a repair panel that does not match
                rectPx(c, x, y, 18, 12, Color.rgb(54, 72, 104));
                rectPx(c, x + 1, y + 1, 16, 10, Color.rgb(46, 62, 92));
                for (int k = 0; k < 4; k++) rectPx(c, x + (k & 1) * 16, y + (k >> 1) * 10 + 1, 1, 1, Color.rgb(150, 160, 190));
                rectPx(c, x + 2, y + 12, 1, 8, Color.argb(60, 150, 90, 50));      // rust running down from it
                break;
            }
            default: break;                                      // 0 and 7: this panel is fine
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

        int pi0 = (int) Math.floor(bgOff / 40f) - 1;
        for (int pi = pi0; pi < pi0 + w / 40 + 3; pi++) wallWear(c, pi * 40 - bgOff, pi, wallTop);

        int k0 = (int) Math.floor((bgOff - 20) / PERIOD) - 1;
        for (int k = k0; k < k0 + w / PERIOD + 3; k++) {
            float ox = k * PERIOD - bgOff + 20;
            if (ox > w || ox + RW < 0) continue;
            drawRoom(c, Math.round(ox), k);
        }
    }

    void drawRoom(Canvas c, int ox, int k) {
        int kind = Math.floorMod(k, rooms.length);
        int oy = Math.round(ceilY) + 14, oh = Math.round(floorY) - oy;      // down to the floor line
        // name plate
        rectPx(c, ox + 25, ceilY + 2, RW - 50, 9, Color.rgb(16, 24, 38));
        boolean err = rooms[kind].alarm();
        int plateCol = err ? (((int) (clock * 4) & 1) == 0 ? Color.rgb(255, 80, 70) : Color.rgb(150, 40, 40)) : Color.rgb(255, 200, 80);
        textOut(c, rooms[kind].name(), ox + RW / 2f, ceilY + 4, 1, plateCol, 1, false);
        // frame
        rectPx(c, ox - 3, oy - 3, RW + 6, oh + 6, Color.rgb(16, 24, 38));
        rectPx(c, ox - 2, oy - 2, RW + 4, oh + 4, Color.rgb(70, 90, 120));

        c.save();
        c.clipRect(ox, oy, ox + RW, oy + oh);
        rooms[kind].draw(c, ox, oy, RW, oh, k);
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

    /** a NULLCORP guard in a red coat: shouts and points at the start, runs in step with you, then tumbles and slides */
    void drawGuard(Canvas c, Npc n) {
        int nx = Math.round(n.x), base = Math.round(floorY - n.fy);
        // lying on the floor the body is thinner than it is tall, so lower it until it touches the ground
        if (n.falling && n.settled) base += Math.round(5f * Math.min(1f, Math.abs((float) Math.sin(Math.toRadians(n.ang)))));
        boolean run = n.running && !n.falling;
        boolean point = n.pointT > 0 && !run && !n.falling;
        int f = run || n.falling ? 1 : -1;                                // facing you, then facing away
        int wp = ((int) (clock * 12 + n.phase)) & 3;
        int[] swing = {0, 2, 0, -2};
        int sw = run ? swing[wp] : 0;
        int coat = Color.rgb(200, 60, 64), shade = Color.rgb(140, 36, 44), skin = Color.rgb(240, 200, 160);
        if (n.fy < 1f && !n.falling) rectPx(c, nx - 4, floorY - 1, 8, 1, Color.argb(70, 0, 0, 0));
        c.save();
        if (n.falling) c.rotate(n.ang, nx, base - 8);
        if (run) base -= (wp & 1);                                     // a bounce in the stride
        rectPx(c, nx - 2 + sw, base - 4, 2, 4, Color.rgb(40, 40, 52));
        rectPx(c, nx - sw, base - 4, 2, 4, Color.rgb(40, 40, 52));
        rectPx(c, nx - 2 + sw, base - 1, 2, 1, Color.rgb(20, 20, 28));
        rectPx(c, nx - sw, base - 1, 2, 1, Color.rgb(20, 20, 28));
        rectPx(c, nx - 3, base - 10, 6, 6, coat);
        rectPx(c, nx - 3, base - 10, 1, 6, shade);
        rectPx(c, nx - 2, base - 15, 5, 5, skin);
        rectPx(c, nx - 2, base - 15, 5, 2, HAIRS[n.hair]);
        rectPx(c, nx + (f > 0 ? 0 : -1), base - 13, 1, 1, Color.rgb(20, 20, 30));          // wide eyes and brows
        rectPx(c, nx + (f > 0 ? 2 : -3), base - 13, 1, 1, Color.rgb(20, 20, 30));
        rectPx(c, nx + (f > 0 ? 0 : -2), base - 14, 2, 1, Color.rgb(60, 36, 30));
        if (point || run || n.falling) rectPx(c, nx + (f > 0 ? 1 : -2), base - 12, 2, 1, Color.rgb(90, 40, 40));   // shouting
        if (point) {
            rectPx(c, nx + (f > 0 ? 2 : -6), base - 9, 4, 2, coat);   // an arm pointing at the runner
            rectPx(c, nx + (f > 0 ? 6 : -8), base - 9, 2, 2, skin);
        } else if (run) {
            rectPx(c, nx + 2 - sw, base - 9, 1, 4, shade);              // swinging arms
            rectPx(c, nx - 3 + sw, base - 9, 1, 4, shade);
        } else {
            rectPx(c, nx - 3, base - 9, 1, 4, shade);
        }
        c.restore();
    }

    void drawGuards(Canvas c) {
        for (Npc n : npcs) if (n.mode == GUARD) drawGuard(c, n);
    }

    void drawNpcs(Canvas c) {
        int[] swing = {0, 1, 0, -1};
        for (Npc n : npcs) {
            if (n.mode == GUARD) continue;               // drawn in front of the yard, see drawGuards
            int nx = Math.round(n.x);
            boolean walking = (n.mode == 1 || n.mode == 2) && state != START;      // before you start, everyone stands still
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
            if (n.female) {                               // the lab coat flares out like a dress
                rectPx(c, nx - 4, base - 6, 8, 2, coat);
                rectPx(c, nx - 4, base - 6, 1, 2, shade);
            }
            // far arm swings with the walk
            rectPx(c, nx - f * 3 - sw * f, base - 9, 1, 4, shade);
            // head: looking up puts the lenses high; otherwise they sit level
            rectPx(c, nx - 2, base - 15, 5, 5, skin);
            rectPx(c, nx - 2, base - 15, 5, 2, HAIRS[n.hair]);
            if (n.female) {                               // long hair: fuller on top and falling past the shoulders on both sides
                rectPx(c, nx - 3, base - 16, 7, 2, HAIRS[n.hair]);
                rectPx(c, nx - 3, base - 14, 1, 6, HAIRS[n.hair]);
                rectPx(c, nx + 3, base - 14, 1, 6, HAIRS[n.hair]);
            }
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
            float hx = z.hx(), hy = z.hy();
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

    void drawHazards(Canvas c) {
        for (Hazard hz : hazards) {
            if (hz.x > w + 40 || hz.x + hz.w < -40) continue;
            hz.draw(this, c);
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
            float py1 = Math.min(py[i], floorY - sz);           // sparks land on the ground, not under it
            if (pcol[i] != 0) rectPx(c, px[i], py1, sz, sz, (pcol[i] & 0xFFFFFF) | ((int) (a * 255) << 24));
            else rectPx(c, px[i], py1, sz, sz, emberCol(a, i));
        }
    }


    /**
     * Legs first, then the upper body. On the ground he shuffles (body dips, arm pumps).
     * Firing the pack he leans into it with his legs trailing and a small recoil buzz;
     * rising he tucks; falling he leans back and his legs dangle and kick.
     */
    void scientist(Canvas c, int left, int top, int ph, boolean run) {
        boolean thr = thrusting && state == PLAY && jumpDelay <= 0 && veh == null;
        String[] legs, body = SPR_HEAD;
        float lean;
        int dy = 0, dx = 0;
        if (onFloor) {
            legs = ph == 0 ? LEGS_A : ph == 1 ? LEGS_B : ph == 2 ? LEGS_M : LEGS_C;
            lean = 0;
            if (run) {
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
        drawHairTail(c, left + dx, top + dy);
        drawHairLock(c, left + dx, top + dy);
        outlined(c, body, left + dx, top + dy);
        drawBust(c, left + dx, top + dy);
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
        drawHairTail(c, left, top);
        drawHairLock(c, left, top);
        outlined(c, flop ? SPR_HEAD_B : SPR_HEAD, left, top);
        drawBust(c, left, top);
        c.restore();
    }

    void drawPlayer(Canvas c) {
        if (state == DEAD) { drawRagdoll(c); return; }
        if (state == PLAY && invuln > 0 && ((int) (clock * 14) & 1) == 0) return;
        if (veh != null && state == PLAY) { veh.draw(c); return; }
        if (state == BUILD && buildT > BUILD_TIME * 0.9f) return;

        float cyc = runPhase * 0.2f;
        int ph = ((int) (cyc * 4)) & 3;
        boolean thr = thrusting && state == PLAY && jumpDelay <= 0;
        int left = Math.round(playerX) - 8, top = Math.round(y) - SH_TOP;

        if (thr) drawFlame(c, left, top + 16, Math.round(floorY), worn[2]);
        scientist(c, left, top, ph, onFloor && state == PLAY);
    }

    /** outer, middle and core colours of each jetpack's exhaust (PHOTON, 7, is a rainbow made each frame) */
    static final int[][] FLAME = {
        {Color.rgb(255, 150, 50), Color.rgb(255, 215, 90), Color.rgb(255, 250, 220)},     // ROCKET
        {Color.rgb(230, 110, 40), Color.rgb(255, 180, 70), Color.rgb(255, 235, 170)},     // PULSE JET
        {Color.rgb(200, 205, 215), Color.rgb(235, 240, 245), Color.rgb(255, 255, 255)},   // STEAM
        {Color.rgb(60, 140, 255), Color.rgb(140, 220, 255), Color.rgb(235, 250, 255)},    // ION
        {Color.rgb(120, 50, 180), Color.rgb(50, 15, 80), Color.rgb(0, 0, 0)},             // DARK MATTER
        {Color.rgb(255, 220, 90), Color.rgb(255, 250, 200), Color.rgb(255, 255, 255)},    // FUSION
        {Color.rgb(230, 70, 200), Color.rgb(255, 140, 235), Color.rgb(255, 245, 255)},    // PLASMA
        null                                                                              // PHOTON
    };

    /** the exhaust of jetpack type under the pack at (left, fy); it stops at the ground instead of running through it */
    void drawFlame(Canvas c, int left, int fy, int ground, int type) {
        int room = ground - fy;
        if (room <= 0) return;
        int[] col = type == 7 ? null : FLAME[Math.max(0, Math.min(6, type))];
        if (type == 7) {
            float hue = (clock * 360f) % 360f;
            col = new int[] {Color.HSVToColor(new float[] {hue, 0.9f, 1f}), Color.HSVToColor(new float[] {(hue + 60) % 360, 0.7f, 1f}), Color.WHITE};
        }
        int fl;
        switch (type) {
            case 1:     // PULSE JET: puffs 15 times a second, a small ember between them
                if (((int) (clock * 15) & 1) == 0) fl = Math.min(room, 5 + rnd.nextInt(4));
                else { rectPx(c, left + 2, fy, 2, Math.min(room, 2), col[0]); if (rnd.nextInt(4) == 0) dust(left + 3, fy + 3); return; }
                break;
            case 2:     // STEAM: a wide white cloud that leaves puffs behind
                fl = Math.min(room, 8 + rnd.nextInt(6));
                rectPx(c, left - 1, fy + 2, 8, Math.max(1, fl - 2), col[0]);
                if (rnd.nextInt(2) == 0) dust(left + 3, fy + fl);
                break;
            case 3: {   // ION: a thin steady beam with a faint glow
                fl = Math.min(room, 12 + rnd.nextInt(2));
                rectPx(c, left, fy, 6, fl, Color.argb(60, 60, 140, 255));
                rectPx(c, left + 1, fy, 4, fl, col[0]);
                rectPx(c, left + 2, fy, 2, fl, col[1]);
                rectPx(c, left + 2, fy, 2, Math.max(1, fl - 3), col[2]);
                break;
            }
            case 5:     // FUSION: long and bright with a glow round it
                fl = Math.min(room, 11 + rnd.nextInt(6));
                rectPx(c, left - 1, fy, 8, fl + 1, Color.argb(70, 255, 230, 120));
                break;
            default:
                fl = Math.min(room, 7 + rnd.nextInt(6));
        }
        if (type != 3) {
            rectPx(c, left, fy, 6, fl, col[0]);
            rectPx(c, left + 1, fy, 4, Math.max(1, fl - 2), col[1]);
            rectPx(c, left + 2, fy, 2, Math.max(1, fl - 5), col[2]);
        }
        if (type == 6) {        // PLASMA: little arcs crackle off the sides
            for (int k = 0; k < 2; k++) {
                int ay = fy + rnd.nextInt(Math.max(1, fl)), side = rnd.nextBoolean() ? -1 - rnd.nextInt(3) : 6 + rnd.nextInt(3);
                rectPx(c, left + side, ay, 1, 1, Color.rgb(255, 200, 255));
                rectPx(c, left + side + (side < 0 ? 1 : -1), ay + 1, 1, 1, Color.rgb(255, 200, 255));
            }
        }
        if (fl == room) {
            // it splashes out along the ground and kicks up sparks
            rectPx(c, left - 4, ground - 1, 14, 1, col[0]);
            rectPx(c, left - 2, ground - 2, 10, 1, col[1]);
            if (rnd.nextInt(2) == 0) dust(left + 3, ground - 2);
        } else {
            rectPx(c, left + 1 + rnd.nextInt(4), fy + fl, 1, 2, col[0]);
        }
    }

    void ring(Canvas c, float cx, float cy, float t) {
        int rr = (int) ((1f - t) * 18) + 2;
        int col = Color.argb((int) (t * 4 * 255 > 255 ? 255 : t * 4 * 255), 120, 235, 255);
        rectPx(c, cx - rr, cy - rr, rr * 2, 1, col);
        rectPx(c, cx - rr, cy + rr, rr * 2, 1, col);
        rectPx(c, cx - rr, cy - rr, 1, rr * 2, col);
        rectPx(c, cx + rr, cy - rr, 1, rr * 2 + 1, col);
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

    void drawBuild(Canvas c) {
        String[] rows = building.rows();
        int hh = building.hTop();
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
                for (int cc = 0; cc < 3 && bcol[i] + cc < rowsWidth(rows); cc++) {
                    int col = pal(rowCh(rows, brow[i] + rr, bcol[i] + cc));
                    if (col != 0) rectPx(c, cx + cc, cy + rr, 1, 1, col);
                }
            }
        }
        if (buildT > BUILD_TIME * 0.9f && building.cockpit() >= 0) {
            int headTop = top + building.cockpit();
            sprite(c, HEAD_ONLY, left, headTop);
        }
        text(c, "BUILDING", w / 2f, ceilY + 28, 1, Color.rgb(255, 200, 80), 1);
        text(c, building.name(), w / 2f, ceilY + 38, 1, Color.WHITE, 1);
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

    // ---------- HUD and screens ----------

    void drawHud(Canvas c) {
        int top = Math.round(ceilY) + 4;
        text(c, (int) (state == DEAD ? finalDist : dist) + " M", 6, top, 2, Color.WHITE, 0);
        text(c, "E- " + electrons, 6, top + 14, 1, Color.rgb(120, 235, 255), 0);
        text(c, "FORMULAS " + Long.bitCount(ownedMask) + "/" + NF, 6, top + 22, 1, Color.rgb(255, 215, 120), 0);
        int rx = w - 6;
        int dim = Color.rgb(170, 200, 220);
        text(c, String.format("V %+.1f M/S", -vy), rx, top, 1, dim, 2);
        text(c, String.format("A %+.1f M/S^2", -ay), rx, top + 8, 1, dim, 2);
        text(c, "BEST " + best + " M", rx, top + 16, 1, dim, 2);
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
        if (tipT > 0 && state == PLAY && veh == null) {
            text(c, "TAP TO JUMP, THEN HOLD TO FIRE THE JETPACK", w / 2f, ceilY + 28, 1, Color.WHITE, 1);
        }
        if (hintT > 0 && state == PLAY && veh != null) {
            text(c, veh.name(), w / 2f, ceilY + 28, 1, Color.WHITE, 1);
            text(c, veh.hint(), w / 2f, ceilY + 38, 1, Color.rgb(120, 235, 255), 1);
        }
    }

    void drawTitle(Canvas c) {
        text(c, "FORMULA HEIST", w / 2f, h * 0.14f, 4, Color.WHITE, 1);
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
        if (stateTime < CARD_DELAY) return;
        text(c, (int) finalDist + " M", w / 2f, h * 0.18f, 4, Color.WHITE, 1);
        text(c, electrons + " ELECTRONS   " + Long.bitCount(ownedMask) + "/" + NF + " FORMULAS   BEST " + best + " M", w / 2f, h * 0.18f + 30, 1, Color.rgb(120, 235, 255), 1);
        wrapText(c, fact, w / 2f, h * 0.18f + 50, 1, Color.rgb(210, 220, 235), (int) (w * 0.7f));
        if (((int) (clock * 2) & 1) == 0) {
            text(c, "TAP TO CONTINUE", w / 2f, 106, 2, Color.rgb(255, 200, 80), 1);
        }
    }
}
