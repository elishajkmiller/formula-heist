package com.example.jetlab;

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
