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

    /**
     * The jetpack roar: low-passed noise with a deep rumble under it and a fast random flutter,
     * like a flame. Made as a seamless loop (the tail is crossfaded into the head).
     */
    static short[] jet() {
        int n = RATE * 1200 / 1000, x = RATE * 80 / 1000;
        float[] f = new float[n + x];
        Random r = new Random(11);
        // two low-pass filters on the same noise: a body around 450 Hz and a deep rumble around 80 Hz
        double a1 = 1 - Math.exp(-2 * Math.PI * 450 / RATE), a2 = 1 - Math.exp(-2 * Math.PI * 80 / RATE);
        double l1 = 0, l2 = 0, flut = 1, flutT = 1, peak = 1e-9, cr = 0, lastW = 0;
        for (int i = 0; i < f.length; i++) {
            double w = r.nextDouble() * 2 - 1;
            l1 += (w - l1) * a1;
            l2 += (w - l2) * a2;
            if (i % (RATE / 30) == 0) flutT = 0.7 + 0.3 * r.nextDouble();   // a new flicker level 30 times a second
            flut += (flutT - flut) * 0.004;
            // quiet crackle: about 40 tiny pops a second, each a few ms of high-passed noise that dies away
            if (r.nextDouble() < 40.0 / RATE) cr = 0.06 + 0.1 * r.nextDouble();
            double crackle = (w - lastW) * cr;
            cr *= 0.93;
            lastW = w;
            double v = (l1 * 1.4 + l2 * 5.0) * flut + crackle;
            f[i] = (float) v;
            peak = Math.max(peak, Math.abs(v));
        }
        short[] d = new short[n];
        for (int i = 0; i < n; i++) {
            double v = f[i];
            if (i < x) { double k = i / (double) x; v = v * k + f[n + i] * (1 - k); }
            d[i] = (short) (v / peak * 32767 * 0.45);
        }
        return d;
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
            try {
                short[] d = name.equals("jet") ? jet() : shimmer();
                t = mk(d);
                t.setLoopPoints(0, d.length, -1);
                t.setVolume(0);
                while (alive) {
                    float target = wanted ? 1f : 0f;
                    if (target > 0 && !playing) { t.play(); playing = true; }
                    if (vol != target) {
                        vol = target > vol ? Math.min(target, vol + 0.25f) : Math.max(target, vol - 0.12f);   // in over ~40 ms, out over ~80 ms
                        t.setVolume(vol * gain);
                    }
                    float want = Math.max(0.5f, Math.min(2f, rate));
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

    /** turn the jetpack roar on or off */
    void setJet(boolean on) { jetLoop.set(on, 1f); }

    /** the rising scratch tone; rate 1 at the start of a card up to 2 (an octave) when it reveals */
    void setScratch(boolean on, float rate) { scratchLoop.set(on, rate); }

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
