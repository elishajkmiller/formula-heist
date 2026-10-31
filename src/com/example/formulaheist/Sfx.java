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
            tr[Game.S_COIN] = mk(tone(900, 1500, 70, 0.4f, 0f, false));
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
     * A short looping chiptune, made here so there is no audio file: A minor, 128 bpm, eight bars
     * of Am F C G twice. A square-wave arpeggio and a noise hat, no bass: the jetpack tone plays the roots.
     */
    static final double BEAT = 60.0 / 128.0;
    static final float[] ROOTS = {110.00f, 87.31f, 130.81f, 98.00f};   // A2 F2 C3 G2, one per bar

    static short[] song() {
        double beat = BEAT, step = beat / 4;
        int bars = 8, steps = bars * 16;
        int n = (int) (steps * step * RATE);
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
            // hat: a tiny noise tick on the off 8ths
            double hat = (st % 2 == 1 ? (noise.nextDouble() * 2 - 1) * Math.exp(-inStep * 12) * 0.05 : 0);
            double v = lead + hat;
            d[i] = (short) (Math.max(-1, Math.min(1, v)) * 32767 * 0.6);
        }
        return d;
    }

    /**
     * The sound of jetpack type 0..7 (the JETPACKS tab order) or vehicle 8..13 (Vehicle.KINDS order
     * plus 8), a 1.2 s seamless loop.
     * Each is a noise part and a tone part, normalised apart and then mixed. Every tone is a
     * whole number of cycles in 1.2 s (110 Hz = 132 cycles), so only the noise needs the tail
     * crossfaded into the head. The tones are tuned to A, so the playback rate can put them
     * on the music's chord root.
     */
    static short[] jet(int type) {
        int n = RATE * 1200 / 1000, x = RATE * 80 / 1000, m = n + x;
        float[] nz = new float[m], tn = new float[m];
        Random r = new Random(11 + type);
        double a80 = lp(80), a300 = lp(300), a450 = lp(450), a1200 = lp(1200), a1500 = lp(1500);
        double l80 = 0, l300 = 0, l450 = 0, l1200 = 0, l1500 = 0, lastW = 0, flut = 1, flutT = 1, cr = 0;
        double[] arp = {440, 660, 880, 1320, 880, 660};
        for (int i = 0; i < m; i++) {
            double t = i / (double) RATE, w = r.nextDouble() * 2 - 1, P = 2 * Math.PI * t;
            l80 += (w - l80) * a80; l300 += (w - l300) * a300; l450 += (w - l450) * a450;
            l1200 += (w - l1200) * a1200; l1500 += (w - l1500) * a1500;
            if (i % (RATE / 30) == 0) flutT = 0.7 + 0.3 * r.nextDouble();   // a new flicker level 30 times a second
            flut += (flutT - flut) * 0.004;
            double hi = w - l1500;                                          // noise above 1.5 kHz
            double a = 0, b = 0;
            switch (type) {
                default:
                case 0: {   // ROCKET: a flame roar, rumble and crackle over a buzzy A2
                    if (r.nextDouble() < 40.0 / RATE) cr = 0.06 + 0.1 * r.nextDouble();
                    a = (l450 * 1.4 + l80 * 5.0) * flut + (w - lastW) * cr;
                    cr *= 0.93;
                    b = saw(P * 110, 5);
                    break;
                }
                case 1: {   // PULSE JET: chugs 15 times a second, a hollow square on A1
                    double pulse = Math.exp(-((t * 15) % 1.0) * 6);
                    a = (l300 * 2 + l80 * 4) * pulse;
                    b = (Math.sin(P * 55) + Math.sin(P * 165) / 3 + Math.sin(P * 275) / 5 + Math.sin(P * 385) / 7) * (0.4 + 0.6 * pulse);
                    break;
                }
                case 2: {   // STEAM: a hiss with a kettle whistle on A5
                    a = hi * flut * (0.85 + 0.15 * Math.sin(P * 5));
                    double ph = P * 880 + 0.6 * Math.sin(P * 5);
                    b = Math.sin(ph) + 0.3 * Math.sin(ph * 2);
                    break;
                }
                case 3: {   // ION: a clean electric whine, A4 with its octave and a soft A2 under it
                    a = hi * 0.5;
                    b = (Math.sin(P * 440) + 0.5 * Math.sin(P * 880) + 0.25 * Math.sin(P * 1320) + 0.6 * Math.sin(P * 110))
                            * (0.85 + 0.15 * Math.sin(P * 10));
                    break;
                }
                case 4: {   // DARK MATTER: a deep A1 whose brightness wobbles 2.5 times a second
                    a = l80;
                    double bright = 1 + 5 * (0.5 + 0.5 * Math.sin(P * 2.5)), s = 0;
                    for (int k = 1; k <= 12; k++) s += Math.sin(P * 55 * k) / k * Math.exp(-(k - 1) / bright);
                    b = s;
                    break;
                }
                case 5: {   // FUSION: a big bright roar on A2 and A3 with the fifth on top
                    a = (l1200 + l450 * 1.4 + l80 * 4) * flut;
                    b = saw(P * 110, 10) + 0.5 * saw(P * 220, 6) + 0.3 * Math.sin(P * 330);
                    break;
                }
                case 6: {   // PLASMA: a harsh narrow-pulse buzz on A2 with electric zaps
                    if (r.nextDouble() < 25.0 / RATE) cr = 0.5 + 0.5 * r.nextDouble();
                    a = hi * (0.25 + cr);
                    cr *= 0.997;                                            // each zap lasts about 15 ms
                    double ph = (t * 110) % 1.0;
                    b = (ph < 0.15 ? 1 : -0.18) + 0.4 * saw(P * 220, 8);
                    break;
                }
                case 7: {   // PHOTON: a sparkling A arpeggio, root fifth octave, over a soft A3
                    int note = (int) (t / 0.1) % arp.length;
                    double u = (t % 0.1) / 0.1;
                    double env = Math.min(1, u * 30) * Math.exp(-u * 4);
                    b = (Math.sin(P * arp[note]) + 0.3 * Math.sin(P * arp[note] * 2)) * env + 0.3 * Math.sin(P * 220);
                    a = hi * 0.4 * (0.6 + 0.4 * Math.sin(P * 10));
                    break;
                }
                // the vehicles, in Vehicle.KINDS order, quieter than the jetpacks so they sit under the music
                case 8: {   // SPRING STOMPER: a boinging A2 that bends 2.5 times a second, with clanks
                    double clank = Math.exp(-((t * 5) % 1.0) * 25);
                    a = hi * clank + l300 * 0.5;
                    double ph = P * 110 + 4 * Math.sin(P * 2.5);
                    b = Math.sin(ph) + 0.4 * Math.sin(ph * 2) + 0.2 * Math.sin(ph * 3);
                    break;
                }
                case 9: {   // GRAVITY INVERTER: a hum that swaps between A2 and A3 2.5 times a second
                    double sw = 0.5 + 0.5 * Math.sin(P * 2.5);
                    a = l80 * 2 + l300;
                    b = saw(P * 110, 6) * sw + saw(P * 220, 4) * (1 - sw);
                    break;
                }
                case 10: {  // QUANTUM ENTANGLER: A4 and E5 trading places, eerie and airy
                    double tr = 0.5 + 0.5 * Math.sin(P * 5);
                    a = hi * 0.6;
                    b = Math.sin(P * 440) * tr + Math.sin(P * 660) * (1 - tr) + 0.3 * Math.sin(P * 110);
                    break;
                }
                case 11: {  // DNA HELIX: two soft triangles, A3 and E4, with a gentle vibrato
                    double v1 = P * 220 + 0.5 * Math.sin(P * 5), v2 = P * 330 + 0.5 * Math.sin(P * 5 + 1.5);
                    a = l1200 * 0.6;
                    b = tri(v1) + 0.6 * tri(v2);
                    break;
                }
                case 12: {  // BERNOULLI BIRD: wind over the wings and a breathy A5 flute
                    a = l1200 * flut * 2;
                    double breath = 0.8 + 0.2 * Math.sin(P * 2.5);
                    b = (Math.sin(P * 880) + 0.15 * Math.sin(P * 1760)) * breath + 0.08 * w;
                    break;
                }
                case 13: {  // AERIAL SCREW: a propeller whirr, A2 chopped by the blades 15 times a second
                    double blade = 0.55 + 0.45 * Math.cos(P * 15);
                    a = l450 * 2 * blade;
                    b = saw(P * 110, 8) * blade;
                    break;
                }
            }
            lastW = w;
            nz[i] = (float) a;
            tn[i] = (float) b;
        }
        //                   ROCKET PULSE  STEAM  ION    DARK   FUSION PLASMA PHOTON STOMP  INVERT QUANT  HELIX  BIRD   SCREW
        float[] noiseMix = {0.36f, 0.40f, 0.30f, 0.08f, 0.18f, 0.40f, 0.33f, 0.08f, 0.10f, 0.12f, 0.06f, 0.06f, 0.16f, 0.16f};
        float[] toneMix  = {0.20f, 0.20f, 0.16f, 0.38f, 0.28f, 0.26f, 0.30f, 0.42f, 0.24f, 0.22f, 0.24f, 0.26f, 0.20f, 0.20f};
        int k = Math.max(0, Math.min(noiseMix.length - 1, type));
        double pn = 1e-9, pt = 1e-9;
        for (int i = 0; i < m; i++) { pn = Math.max(pn, Math.abs(nz[i])); pt = Math.max(pt, Math.abs(tn[i])); }
        short[] d = new short[n];
        for (int i = 0; i < n; i++) {
            double v = nz[i];
            if (i < x) { double c = i / (double) x; v = v * c + nz[n + i] * (1 - c); }
            d[i] = (short) ((v / pn * noiseMix[k] + tn[i] / pt * toneMix[k]) * 32767);
        }
        return d;
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

        @Override
        public void run() {
            float vol = 0, cur = 1f;
            boolean playing = false;
            int built = -1;
            try {
                while (alive) {
                    // the jet rebuilds its loop when the worn jetpack changes, once it has faded out
                    int type = this == jetLoop ? jetType : 0;
                    if (t == null || (built != type && vol == 0)) {
                        if (t != null) { t.release(); t = null; }
                        short[] d = this == jetLoop ? jet(type) : shimmer();
                        t = mk(d);
                        t.setLoopPoints(0, d.length, -1);
                        t.setVolume(0);
                        built = type; playing = false; cur = 1f;
                    }
                    float target = wanted && built == type ? 1f : 0f;
                    if (target > 0 && !playing) { t.play(); playing = true; }
                    if (vol != target) {
                        vol = target > vol ? Math.min(target, vol + 0.25f) : Math.max(target, vol - 0.12f);   // in over ~40 ms, out over ~80 ms
                        t.setVolume(vol * gain);
                    }
                    float want = Math.max(0.5f, Math.min(2f, this == jetLoop ? musicPitch() : rate));
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
    final Loop jetLoop = new Loop("jet", 0.5f), scratchLoop = new Loop("scratch", 0.35f);

    /**
     * The roar's playback rate that puts its A2 hum on the root of the chord the music is playing now,
     * read from the music track's play head. 1 when the music is off.
     */
    float musicPitch() {
        AudioTrack m = music;
        if (m == null || !musicWanted || songLen == 0) return 1f;
        try {
            int pos = m.getPlaybackHeadPosition() % songLen;
            int bar = (int) (pos / (double) RATE / (BEAT * 4));
            return ROOTS[bar % 4] / ROOTS[0];
        } catch (Throwable e) { return 1f; }
    }

    volatile int jetType;

    /** turn the jetpack sound on or off; type is the worn jetpack, which picks the sound */
    void setJet(boolean on, int type) { jetType = type; jetLoop.set(on, 1f); }

    /** the rising scratch tone; rate 1 at the start of a card up to 2 (an octave) when it reveals */
    void setScratch(boolean on, float rate) { scratchLoop.set(on, rate); }

    volatile AudioTrack music;
    volatile boolean musicWanted;
    volatile int songLen;

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
                                songLen = d.length;
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
                        // the jump and vehicle sounds are tuned to A, so they play on the music's chord
                        if (id == Game.S_JUMP || id == Game.S_STOMP || id == Game.S_FLIP || id == Game.S_TELE)
                            a.setPlaybackRate((int) (RATE * musicPitch()));
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
                    try { if (music != null) music.release(); } catch (Throwable t) { }
                    music = null;
                }
            });
            io.shutdown();
        } catch (Throwable t) { }
    }
}
