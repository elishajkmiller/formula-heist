package com.example.formulaheist;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")   // the AudioTrack constructors still work on every phone we target
/** Short synthesized sound effects, no audio files. */
final class Sfx {
    static final int RATE = 22050;
    final AudioTrack[] tr = new AudioTrack[11];
    // stop/reload/play can block for a frame, so every call runs off the game thread
    final ExecutorService io = Executors.newSingleThreadExecutor();

    Sfx() {
        try {
            tr[Game.S_JUMP] = mk(tone(220, 440, 120, 0.5f, 0f, true));        // A3 up to A4
            tr[Game.S_COIN] = mk(coin());                                      // A5 then E6, a fifth
            tr[Game.S_HIT] = mk(tone(160, 30, 380, 0.9f, 0.6f, false));
            tr[Game.S_STOMP] = mk(tone(110, 27.5f, 170, 0.9f, 0.35f, false));  // A2 down to A0
            tr[Game.S_FLIP] = mk(tone(880, 220, 130, 0.5f, 0f, true));         // A5 down to A3
            tr[Game.S_WARN] = mk(tone(820, 820, 90, 0.4f, 0f, true));
            tr[Game.S_DONE] = mk(tone(500, 1400, 260, 0.6f, 0f, true));
            tr[Game.S_TELE] = mk(tone(1760, 220, 150, 0.5f, 0.15f, true));     // A6 down to A3
            tr[Game.S_SCRATCH] = mk(tone(2600, 1400, 35, 0.22f, 0.85f, false));
            tr[Game.S_BUILD] = mk(build());
            tr[Game.S_FANFARE] = mk(fanfare());
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

    /** the electron pickup: two quick notes, A5 then E6 (a fifth up), so it can sit on the chord root */
    static short[] coin() {
        int half = RATE * 40 / 1000;
        short[] d = new short[half * 2];
        double[] f = {880.0, 1318.51};
        for (int k = 0; k < 2; k++) {
            double ph = 0;
            for (int i = 0; i < half; i++) {
                float t = i / (float) half;
                ph += 2 * Math.PI * f[k] / RATE;
                float env = (t < 0.08f ? t / 0.08f : 1f) * (1 - t * 0.7f);
                d[k * half + i] = (short) (Math.sin(ph) * env * 0.4 * 32767 * 0.5 * (k == 1 ? 1.1 : 1));
            }
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
     * A trumpet fanfare for a new formula: "dun dun dun DUNNN", three quick G4s then a held C5
     * with a third and fifth joining it. Each note is a brass tone: harmonics that get brighter
     * as the note swells (a real horn sounds brighter the harder it is blown), plus vibrato on the long one.
     */
    static short[] fanfare() {
        // start (s), length (s), pitches
        float[][] notes = {
            {0.00f, 0.12f, 392.00f},
            {0.15f, 0.12f, 392.00f},
            {0.30f, 0.12f, 392.00f},
            {0.45f, 0.85f, 523.25f, 659.25f, 783.99f},
        };
        int n = (int) (RATE * 1.4f);
        float[] f = new float[n];
        for (float[] nt : notes) {
            int i0 = (int) (nt[0] * RATE), len = (int) (nt[1] * RATE);
            boolean held = nt[1] > 0.5f;
            for (int v = 2; v < nt.length; v++) {
                double ph = 0, gain = v == 2 ? 1.0 : 0.45;           // the harmony sits under the melody
                for (int i = 0; i < len && i0 + i < n; i++) {
                    double t = i / (double) RATE, u = i / (double) len;
                    // swell in over 25 ms with a small bump, settle, then let go over the last 15 percent
                    double env = t < 0.025 ? t / 0.025 * 1.15 : Math.max(0.85, 1.15 - (t - 0.025) * 3);
                    if (u > 0.85) env *= (1 - u) / 0.15;
                    double vib = held && t > 0.15 ? 1 + 0.006 * Math.sin(2 * Math.PI * 5.5 * t) : 1;
                    ph += 2 * Math.PI * nt[v] * vib / RATE;
                    double bright = 1 + 6 * Math.min(1, env), s = 0;
                    for (int k = 1; k <= 10; k++) s += Math.sin(ph * k) / k * Math.exp(-(k - 1) / bright);
                    f[i0 + i] += (float) (s * env * gain);
                }
            }
        }
        double peak = 1e-9;
        for (float x : f) peak = Math.max(peak, Math.abs(x));
        short[] d = new short[n];
        for (int i = 0; i < n; i++) d[i] = (short) (f[i] / peak * 32767 * 0.55);
        return d;
    }

    /**
     * The run music is made up as it plays, bar by bar, so it never loops. A minor, 128 bpm: Am F C G,
     * a square-wave arpeggio and a noise hat, no bass (the jetpack tone plays the roots). Each phrase of
     * four bars picks one of a few arpeggio patterns. Every two to four phrases the key changes through a
     * bar of fully diminished seventh chord on the new key's leading tone: that chord is the same four
     * notes for tonics a minor third apart, so it resolves to the old key's minor third up, tritone or
     * sixth up, and the song lands in a new key without a seam.
     */
    static final double BEAT = 60.0 / 128.0;
    static final int STEP = (int) (RATE * BEAT / 4), BAR = STEP * 16;       // frames in a 16th note and in a bar
    static final int[] CH_ROOT = {0, -4, 3, -2}, CH_THIRD = {3, 4, 4, 4};   // Am F C G as semitones from A4
    static final int[][] PATS = {
        {0, 1, 2, 1, 0, 1, 2, 1, 2, 1, 0, 1, 2, 1, 0, 2},
        {0, 2, 1, 2, 0, 2, 1, 2, 2, 1, 0, 1, 2, 0, 1, 2},
        {0, 1, 2, 0, 1, 2, 1, 0, 2, 1, 0, 2, 1, 0, 1, 2},
    };
    static final int[] DIM_PAT = {0, 1, 2, 3, 2, 1, 2, 3, 0, 1, 2, 3, 3, 2, 1, 0};
    static final int[] DIM_STEPS = {-1, 2, 5, 8};                           // leading tone, then up in minor thirds
    final float[] barRatio = new float[64];                                  // jet pitch ratio per bar, ring indexed by bar number
    { java.util.Arrays.fill(barRatio, 1f); }
    final int[] barKey = new int[64];                                        // key (semitones from A) per bar, same ring
    volatile int curTrack;                                                   // 0 none, 1 run music, 2 menu music

    static float hz(int semis) { return (float) (440.0 * Math.pow(2, semis / 12.0)); }

    /** the root folded into a tritone either side of A, as a rate for the A-tuned jet tone */
    static float ratioOf(int semis) {
        int r = ((semis + 6) % 12 + 12) % 12 - 6;
        return (float) Math.pow(2, r / 12.0);
    }

    /** render one bar. tones are semitones from A4 for the three arp notes (or four for the dim chord). */
    static short[] renderBar(int[] tones, int[] pat, long first, Random noise, boolean hatLoud) {
        short[] d = new short[BAR];
        for (int j = 0; j < BAR; j++) {
            double t = (first + j) / (double) RATE;
            int st = j / STEP;
            double inStep = (j - st * STEP) / (double) STEP;
            double lf = hz(tones[pat[st] % tones.length]);
            double lead = (((t * lf) % 1.0) < 0.25 ? 1 : -1) * Math.exp(-inStep * 3.2) * 0.12;
            double hat = (st % 2 == 1 ? (noise.nextDouble() * 2 - 1) * Math.exp(-inStep * 12) * (hatLoud ? 0.08 : 0.05) : 0);
            d[j] = (short) (Math.max(-1, Math.min(1, lead + hat)) * 32767 * 0.6);
        }
        return d;
    }

    /** the music thread: writes one generated bar after another into the streaming track until it is released */
    void musicLoop(AudioTrack tk) {
        try { runMusic(tk); } catch (Throwable t) { }
        try { tk.release(); } catch (Throwable t) { }
    }

    void runMusic(AudioTrack tk) {
        Random rnd = new Random(), noise = new Random(5);
        int key = 0, bar = 0, inPhrase = 0, phrasesLeft = 2 + rnd.nextInt(3);
        int[] pat = PATS[0];
        boolean dim = false;
        java.util.Arrays.fill(barRatio, 1f);
        java.util.Arrays.fill(barKey, 0);
        while (alive && music == tk) {
            int[] tones;
            int rootSemis;
            if (dim) {
                tones = new int[4];
                for (int i = 0; i < 4; i++) tones[i] = key + DIM_STEPS[i];
                rootSemis = key - 1 - 12;
            } else {
                int r = key + CH_ROOT[inPhrase];
                tones = new int[]{r, r + CH_THIRD[inPhrase], r + 7};
                rootSemis = r - 12;
            }
            barRatio[bar & 63] = ratioOf(rootSemis + 12);
            barKey[bar & 63] = key;
            short[] d = renderBar(tones, dim ? DIM_PAT : pat, (long) bar * BAR, noise, dim);
            try {
                // non-blocking so this thread can notice it was replaced; it alone releases its track
                int off = 0;
                while (off < d.length) {
                    if (!alive || music != tk) return;
                    int wr = tk.write(d, off, d.length - off, AudioTrack.WRITE_NON_BLOCKING);
                    if (wr < 0) return;
                    off += wr;
                    if (wr == 0) Thread.sleep(8);
                }
            } catch (Throwable t) { return; }
            bar++;
            if (dim) {
                dim = false;
            } else if (++inPhrase == 4) {
                inPhrase = 0;
                pat = PATS[rnd.nextInt(PATS.length)];
                if (--phrasesLeft == 0) {
                    int[] moves = {3, 3, 9, 9, 6};
                    key += moves[rnd.nextInt(moves.length)];
                    while (key > 6) key -= 12;
                    while (key < -5) key += 12;
                    phrasesLeft = 2 + rnd.nextInt(3);
                    dim = true;
                }
            }
        }
    }

    /**
     * The menu and pre-run tune: slower and softer than the run music. C major, 92 bpm, eight bars of
     * C Am F G twice, a triangle melody over a slow broken chord with a faint bell on the downbeats.
     */
    static short[] menuSong() {
        double beat = 60.0 / 92.0, step = beat / 2;
        int bars = 8, steps = bars * 8;
        int n = (int) (steps * step * RATE);
        float[][] ch = {{261.63f, 329.63f, 392.00f}, {220.00f, 261.63f, 329.63f}, {174.61f, 220.00f, 261.63f}, {196.00f, 246.94f, 293.66f}};
        int[] mel = {2, -1, 1, -1, 0, 1, 2, -1, 1, -1, 0, -1, 1, -1, 2, -1};
        short[] d = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            int st = Math.min(steps - 1, (int) (t / step));
            int chord = (st / 8) % 4;
            double inStep = (t - st * step) / step;
            int m = mel[st % 16];
            double v = 0;
            if (m >= 0) {   // triangle melody an octave up
                double ph = (t * ch[chord][m] * 2) % 1.0;
                v += (Math.abs(ph * 4 - 2) - 1) * Math.exp(-inStep * 1.6) * 0.16;
            }
            // broken chord underneath, sine
            double bf = ch[chord][st % 3] * 0.5;
            v += Math.sin(2 * Math.PI * t * bf) * Math.exp(-inStep * 2.2) * 0.12;
            // bell on each bar's first step
            if (st % 8 == 0) v += Math.sin(2 * Math.PI * t * ch[chord][2] * 2) * Math.exp(-inStep * 5) * 0.05;
            d[i] = (short) (Math.max(-1, Math.min(1, v)) * 32767 * 0.6);
        }
        return d;
    }

    /**
     * The jetpack (type 0..7, the JETPACKS tab order), vehicle (8..13, Vehicle.KINDS order plus 8) and
     * missile (-1) sounds, made live. Each is a noise part and a tone part, normalised apart and then
     * mixed. The tone's pitch comes from a pitched clock q that runs fm times faster than real time,
     * while every wobble, chug, flutter and arpeggio step runs on the real clock, so moving into
     * another key changes the notes but never the speed of the sound. The tones are tuned to A.
     */
    static final class JetSynth {
        //                   ROCKET PULSE  STEAM  ION    DARK   FUSION PLASMA PHOTON STOMP  INVERT QUANT  HELIX  BIRD   SCREW
        static final float[] NOISE_MIX = {0.36f, 0.40f, 0.30f, 0.08f, 0.18f, 0.40f, 0.33f, 0.08f, 0.10f, 0.12f, 0.06f, 0.06f, 0.16f, 0.16f};
        static final float[] TONE_MIX  = {0.20f, 0.20f, 0.16f, 0.38f, 0.28f, 0.26f, 0.30f, 0.42f, 0.24f, 0.22f, 0.24f, 0.26f, 0.20f, 0.20f};
        static final double[] ARP = {440, 660, 880, 1320, 880, 660};
        final int type;
        final Random r;
        final double a80 = lp(80), a300 = lp(300), a450 = lp(450), a1200 = lp(1200), a1500 = lp(1500);
        double l80, l300, l450, l1200, l1500, lastW, flut = 1, flutT = 1, cr, q;
        long n;
        double na, nb, pn = 1e-9, pt = 1e-9;     // last noise and tone sample, and their peaks at fm 1

        JetSynth(int type) {
            this.type = type;
            r = new Random(11 + type);
            JetSynth probe = new JetSynth(type, true);   // run 1.2 s once to find how loud each part gets
            for (int i = 0; i < RATE * 12 / 10; i++) { probe.step(1.0); pn = Math.max(pn, Math.abs(probe.na)); pt = Math.max(pt, Math.abs(probe.nb)); }
        }

        private JetSynth(int type, boolean probe) { this.type = type; r = new Random(11 + type); }

        /** one sample of noise (na) and tone (nb); fm scales the pitch of the tone */
        void step(double fm) {
            double t = n++ / (double) RATE, w = r.nextDouble() * 2 - 1, P = 2 * Math.PI * t;
            q += 2 * Math.PI * fm / RATE;
            double Q = q;
            l80 += (w - l80) * a80; l300 += (w - l300) * a300; l450 += (w - l450) * a450;
            l1200 += (w - l1200) * a1200; l1500 += (w - l1500) * a1500;
            if (n % (RATE / 30) == 1) flutT = 0.7 + 0.3 * r.nextDouble();   // a new flicker level 30 times a second
            flut += (flutT - flut) * 0.004;
            double hi = w - l1500;                                          // noise above 1.5 kHz
            double a = 0, b = 0;
            switch (type) {
                case -1: {  // the incoming missile: two sawtooths on A3 a hair apart, so they beat, with a flutter
                    b = (saw(Q * 220, 6) + saw(Q * 222.5, 6)) * (0.8 + 0.2 * Math.sin(P * 5));
                    break;
                }
                default:
                case 0: {   // ROCKET: a flame roar, rumble and crackle over a buzzy A2
                    if (r.nextDouble() < 40.0 / RATE) cr = 0.06 + 0.1 * r.nextDouble();
                    a = (l450 * 1.4 + l80 * 5.0) * flut + (w - lastW) * cr;
                    cr *= 0.93;
                    b = saw(Q * 110, 5);
                    break;
                }
                case 1: {   // PULSE JET: chugs 15 times a second, a hollow square on A1
                    double pulse = Math.exp(-((t * 15) % 1.0) * 6);
                    a = (l300 * 2 + l80 * 4) * pulse;
                    b = (Math.sin(Q * 55) + Math.sin(Q * 165) / 3 + Math.sin(Q * 275) / 5 + Math.sin(Q * 385) / 7) * (0.4 + 0.6 * pulse);
                    break;
                }
                case 2: {   // STEAM: a hiss with a kettle whistle on A5
                    a = hi * flut * (0.85 + 0.15 * Math.sin(P * 5));
                    double ph = Q * 880 + 0.6 * Math.sin(P * 5);
                    b = Math.sin(ph) + 0.3 * Math.sin(ph * 2);
                    break;
                }
                case 3: {   // ION: a clean electric whine, A4 with its octave and a soft A2 under it
                    a = hi * 0.5;
                    b = (Math.sin(Q * 440) + 0.5 * Math.sin(Q * 880) + 0.25 * Math.sin(Q * 1320) + 0.6 * Math.sin(Q * 110))
                            * (0.85 + 0.15 * Math.sin(P * 10));
                    break;
                }
                case 4: {   // DARK MATTER: a deep A1 whose brightness wobbles 2.5 times a second
                    a = l80;
                    double bright = 1 + 5 * (0.5 + 0.5 * Math.sin(P * 2.5)), s = 0;
                    for (int k = 1; k <= 12; k++) s += Math.sin(Q * 55 * k) / k * Math.exp(-(k - 1) / bright);
                    b = s;
                    break;
                }
                case 5: {   // FUSION: a big bright roar on A2 and A3 with the fifth on top
                    a = (l1200 + l450 * 1.4 + l80 * 4) * flut;
                    b = saw(Q * 110, 10) + 0.5 * saw(Q * 220, 6) + 0.3 * Math.sin(Q * 330);
                    break;
                }
                case 6: {   // PLASMA: a harsh narrow-pulse buzz on A2 with electric zaps
                    if (r.nextDouble() < 25.0 / RATE) cr = 0.5 + 0.5 * r.nextDouble();
                    a = hi * (0.25 + cr);
                    cr *= 0.997;                                            // each zap lasts about 15 ms
                    double ph = (Q / (2 * Math.PI) * 110) % 1.0;
                    b = (ph < 0.15 ? 1 : -0.18) + 0.4 * saw(Q * 220, 8);
                    break;
                }
                case 7: {   // PHOTON: a sparkling A arpeggio, root fifth octave, over a soft A3
                    int note = (int) (t / 0.1) % ARP.length;
                    double u = (t % 0.1) / 0.1;
                    double env = Math.min(1, u * 30) * Math.exp(-u * 4);
                    b = (Math.sin(Q * ARP[note]) + 0.3 * Math.sin(Q * ARP[note] * 2)) * env + 0.3 * Math.sin(Q * 220);
                    a = hi * 0.4 * (0.6 + 0.4 * Math.sin(P * 10));
                    break;
                }
                // the vehicles, in Vehicle.KINDS order, quieter than the jetpacks so they sit under the music
                case 8: {   // SPRING STOMPER: a boinging A2 that bends 2.5 times a second, with clanks
                    double clank = Math.exp(-((t * 5) % 1.0) * 25);
                    a = hi * clank + l300 * 0.5;
                    double ph = Q * 110 + 4 * Math.sin(P * 2.5);
                    b = Math.sin(ph) + 0.4 * Math.sin(ph * 2) + 0.2 * Math.sin(ph * 3);
                    break;
                }
                case 9: {   // GRAVITY INVERTER: a hum that swaps between A2 and A3 2.5 times a second
                    double sw = 0.5 + 0.5 * Math.sin(P * 2.5);
                    a = l80 * 2 + l300;
                    b = saw(Q * 110, 6) * sw + saw(Q * 220, 4) * (1 - sw);
                    break;
                }
                case 10: {  // QUANTUM ENTANGLER: A4 and E5 trading places, eerie and airy
                    double tr = 0.5 + 0.5 * Math.sin(P * 5);
                    a = hi * 0.6;
                    b = Math.sin(Q * 440) * tr + Math.sin(Q * 660) * (1 - tr) + 0.3 * Math.sin(Q * 110);
                    break;
                }
                case 11: {  // DNA HELIX: two soft triangles, A3 and E4, with a gentle vibrato
                    double v1 = Q * 220 + 0.5 * Math.sin(P * 5), v2 = Q * 330 + 0.5 * Math.sin(P * 5 + 1.5);
                    a = l1200 * 0.6;
                    b = tri(v1) + 0.6 * tri(v2);
                    break;
                }
                case 12: {  // BERNOULLI BIRD: wind over the wings and a breathy A5 flute
                    a = l1200 * flut * 2;
                    double breath = 0.8 + 0.2 * Math.sin(P * 2.5);
                    b = (Math.sin(Q * 880) + 0.15 * Math.sin(Q * 1760)) * breath + 0.08 * w;
                    break;
                }
                case 13: {  // AERIAL SCREW: a propeller whirr, A2 chopped by the blades 15 times a second
                    double blade = 0.55 + 0.45 * Math.cos(P * 15);
                    a = l450 * 2 * blade;
                    b = saw(Q * 110, 8) * blade;
                    break;
                }
            }
            lastW = w;
            na = a;
            nb = b;
        }

        /** fill out with len mixed samples at pitch multiplier fm */
        void fill(short[] out, int len, double fm) {
            int k = type < 0 ? -1 : Math.min(NOISE_MIX.length - 1, type);
            float nm = k < 0 ? 0f : NOISE_MIX[k], tm = k < 0 ? 0.5f : TONE_MIX[k];
            for (int i = 0; i < len; i++) {
                step(fm);
                out[i] = (short) Math.max(-32767, Math.min(32767, (na / pn * nm + nb / pt * tm) * 32767));
            }
        }
    }

    /** one-pole low-pass coefficient for a cutoff in Hz */
    static double lp(double hz) { return 1 - Math.exp(-2 * Math.PI * hz / RATE); }

    /** a triangle wave, ph in radians */
    static double tri(double ph) {
        double p = (ph / (2 * Math.PI)) % 1.0;
        return p < 0.5 ? p * 4 - 1 : 3 - p * 4;
    }

    /** a sawtooth-ish tone from its first h harmonics, ph in radians */
    static double saw(double ph, int h) {
        double s = 0;
        for (int k = 1; k <= h; k++) s += Math.sin(ph * k) / k;
        return s;
    }

    /**
     * The scratch tone: a soft shimmer (a triangle at 330 Hz with an octave and a 12 Hz tremolo).
     * Every part fits a whole number of cycles in 0.5 s, so it loops with no seam.
     * Its pitch is raised by playback rate as the foil comes off.
     */
    static short[] shimmer() {
        int n = RATE / 2;
        short[] d = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double p1 = (t * 330) % 1.0, p2 = (t * 660) % 1.0;
            double tri1 = p1 < 0.5 ? p1 * 4 - 1 : 3 - p1 * 4, tri2 = p2 < 0.5 ? p2 * 4 - 1 : 3 - p2 * 4;
            double trem = 0.75 + 0.25 * Math.sin(2 * Math.PI * 12 * t);
            d[i] = (short) ((tri1 * 0.6 + tri2 * 0.3) * trem * 32767 * 0.4);
        }
        return d;
    }

    /**
     * A looping sound that is switched on and off from the game thread. A small thread of its own
     * fades it in and out so it never clicks, and glides its pitch toward the wanted rate.
     */
    final class Loop implements Runnable {
        final String name;
        final float gain;
        volatile boolean wanted;
        volatile float level = 1f;         // extra volume 0..1, set by the game while it plays
        volatile float rate = 1f;           // playback speed, 1 = as made, 2 = an octave up
        Thread th;
        AudioTrack t;

        Loop(String name, float gain) { this.name = name; this.gain = gain; }

        void set(boolean on, float r) {
            rate = r;
            if (on == wanted) return;
            wanted = on;
            if (on && th == null) {
                th = new Thread(this, name);
                th.setDaemon(true);
                th.start();
            }
        }

        /** the jetpack, vehicle and missile sounds are made live (JetSynth) so pitch never changes their speed */
        void runStream() {
            final int CH = 256;
            float vol = 0, cur = 1f, lastV = -1f;
            boolean playing = false;
            int built = -2;
            JetSynth sy = null;
            short[] out = new short[CH];
            try {
                while (alive) {
                    int type = this == jetLoop ? jetType : -1;
                    if (sy == null || (built != type && vol == 0)) {
                        if (t != null) { t.release(); t = null; }
                        int buf = Math.max(AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), CH * 2 * 4);
                        t = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                                AudioFormat.ENCODING_PCM_16BIT, buf, AudioTrack.MODE_STREAM);
                        t.setVolume(0);
                        sy = new JetSynth(type);
                        built = type; playing = false; cur = 1f; lastV = 0;
                    }
                    float target = wanted && built == type ? 1f : 0f;
                    if (target > 0 && !playing) { t.play(); playing = true; }
                    if (vol != target) vol = target > vol ? Math.min(target, vol + 0.25f) : Math.max(target, vol - 0.12f);
                    float v = vol * gain * level;
                    if (Math.abs(v - lastV) > 0.002f) { t.setVolume(v); lastV = v; }
                    float want = Math.max(0.5f, Math.min(2f, musicPitch()));
                    cur += (want - cur) * 0.3f;
                    if (vol == 0 && playing) { t.pause(); t.flush(); playing = false; }
                    if (playing) {
                        sy.fill(out, CH, cur);
                        if (t.write(out, 0, CH) < 0) { t.release(); t = null; sy = null; }
                    } else {
                        Thread.sleep(10);
                    }
                }
            } catch (Throwable e) { }
            try { if (t != null) t.release(); } catch (Throwable e) { }
            t = null;
        }

        @Override
        public void run() {
            if (this != scratchLoop) { runStream(); return; }
            float vol = 0, cur = 1f, lastV = -1f;
            boolean playing = false;
            int built = -1;
            try {
                while (alive) {
                    // the jet rebuilds its loop when the worn jetpack changes, once it has faded out
                    int type = this == jetLoop ? jetType : 0;
                    if (t == null || (built != type && vol == 0)) {
                        if (t != null) { t.release(); t = null; }
                        short[] d = shimmer();
                        t = mk(d);
                        t.setLoopPoints(0, d.length, -1);
                        t.setVolume(0); lastV = 0;
                        built = type; playing = false; cur = 1f;
                    }
                    float target = wanted && built == type ? 1f : 0f;
                    if (target > 0 && !playing) { t.play(); playing = true; }
                    if (vol != target) {
                        vol = target > vol ? Math.min(target, vol + 0.25f) : Math.max(target, vol - 0.12f);   // in over ~40 ms, out over ~80 ms
                    }
                    float v = vol * gain * level;
                    if (Math.abs(v - lastV) > 0.002f) { t.setVolume(v); lastV = v; }
                    float want = Math.max(0.5f, Math.min(2f, this == jetLoop || this == missileLoop ? musicPitch() : rate * keyRatio()));
                    if (Math.abs(want - cur) > 0.002f) {
                        cur += (want - cur) * 0.3f;
                        t.setPlaybackRate((int) (RATE * cur));
                    }
                    if (vol == 0 && playing) { t.pause(); playing = false; }
                    Thread.sleep(10);
                }
            } catch (Throwable e) { }
            try { if (t != null) t.release(); } catch (Throwable e) { }
            t = null;
        }
    }

    volatile boolean alive = true;
    final Loop jetLoop = new Loop("jet", 0.5f), scratchLoop = new Loop("scratch", 0.35f), missileLoop = new Loop("missile", 0.4f);

    /**
     * The roar's playback rate that puts its A2 hum on the root of the chord the music is playing now,
     * read from the music track's play head. 1 when the music is off.
     */
    long playedFrames;
    int lastHead;

    /**
     * Frames played is counted from the play head's steps, not read from it directly: some devices
     * restart the head at 0 after a pause, which would point at the wrong bar for the rest of the run.
     */
    /** the ring index of the bar being heard now, or -1 when there is no run music track */
    synchronized int curBar() {
        AudioTrack m = music;
        if (m == null) return -1;
        try {
            int h = m.getPlaybackHeadPosition();
            int d = h - lastHead;
            if (d < 0) d = h;           // the head restarted
            playedFrames += d;
            lastHead = h;
            return (int) ((playedFrames / BAR) & 63);
        } catch (Throwable e) { return -1; }
    }

    /** rate that puts an A-tuned sound on the root of the chord now playing; 1 outside a run */
    float musicPitch() {
        if (curTrack != 1) return 1f;
        int i = curBar();
        return i < 0 ? 1f : barRatio[i];
    }

    /** rate that moves an A-tuned sound into the music's current key; 1 on the menu tune (A minor / C major) */
    float keyRatio() {
        if (curTrack == 2) return 1f;
        int i = curBar();
        return i < 0 ? 1f : ratioOf(barKey[i]);
    }

    volatile int jetType;

    /** turn the jetpack sound on or off; type is the worn jetpack, which picks the sound */
    /** the incoming-missile whine; level 0..1 is how near it is, and its pitch follows the music's chord */
    void setMissile(boolean on, float level) { missileLoop.level = level; missileLoop.set(on, 1f); }

    void setJet(boolean on, int type) { jetType = type; jetLoop.set(on, 1f); }

    /** the rising scratch tone; rate 1 at the start of a card up to 2 (an octave) when it reveals */
    void setScratch(boolean on, float rate) { scratchLoop.set(on, rate); }

    volatile AudioTrack music;
    volatile boolean musicWanted;

    volatile AudioTrack menuTrack;

    /** which tune plays: 0 none, 1 the run music, 2 the menu / pre-run music; tracks are built on the audio thread the first time */
    void setTrack(final int which) {
        musicWanted = which == 1;
        curTrack = which;
        try {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (which == 1) {
                            // every run starts the music over in A minor: drop the old track and its thread
                            music = null;       // the old music thread sees this, stops and releases its own track
                            synchronized (Sfx.this) { playedFrames = 0; lastHead = 0; }
                            java.util.Arrays.fill(barRatio, 1f);
                            final AudioTrack tk = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                                    AudioFormat.ENCODING_PCM_16BIT, BAR * 2 * 3, AudioTrack.MODE_STREAM);
                            tk.setVolume(0.55f);
                            music = tk;
                            Thread th = new Thread(new Runnable() { public void run() { musicLoop(tk); } }, "music");
                            th.setDaemon(true);
                            th.start();
                        }
                        if (which == 2 && menuTrack == null) {
                            short[] d = menuSong();
                            menuTrack = mk(d);
                            menuTrack.setLoopPoints(0, d.length, -1);
                            menuTrack.setVolume(0.5f);
                        }
                        if (which != 1 && music != null) music.pause();
                        if (which != 2 && menuTrack != null) menuTrack.pause();
                        if (which == 1 && musicWanted) music.play();
                        if (which == 2) menuTrack.play();
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
                        // the jump and vehicle sounds are tuned to A, so they play on the music's chord
                        float r = 1f;
                        if (id == Game.S_JUMP || id == Game.S_STOMP || id == Game.S_FLIP || id == Game.S_TELE || id == Game.S_COIN) r = musicPitch();
                        else if (id == Game.S_WARN || id == Game.S_DONE || id == Game.S_BUILD || id == Game.S_FANFARE) r = keyRatio();
                        a.setPlaybackRate((int) (RATE * r));
                        a.play();
                    } catch (Throwable t) { }
                }
            });
        } catch (Throwable t) { }
    }

    void release() {
        alive = false;   // the jet thread releases its own track
        try {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < tr.length; i++) {
                        try { if (tr[i] != null) tr[i].release(); } catch (Throwable t) { }
                        tr[i] = null;
                    }
                    music = null;       // the music thread releases its own track
                    try { if (menuTrack != null) menuTrack.release(); } catch (Throwable t) { }
                    menuTrack = null;
                }
            });
            io.shutdown();
        } catch (Throwable t) { }
    }
}
