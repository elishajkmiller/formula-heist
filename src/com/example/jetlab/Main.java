package com.example.jetlab;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Random;

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

    static class Zapper {
        float x, y, len, angle, spin;
    }

    static class Missile {
        float x, y, warn;
        boolean flying;
    }

    /** One game view: physics, drawing and input in one place. */
    static class Game extends View implements Choreographer.FrameCallback {

        static final int START = 0, PLAY = 1, DEAD = 2;

        static final float GRAVITY = 9.8f;      // m/s^2, the real one
        static final float THRUST = 26f;        // m/s^2 from the pack
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
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final RectF rect = new RectF();
        final SharedPreferences prefs;

        final ArrayList<Coin> coins = new ArrayList<>();
        final ArrayList<Zapper> zappers = new ArrayList<>();
        final ArrayList<Missile> missiles = new ArrayList<>();

        static final int NP = 140;
        final float[] px = new float[NP], py = new float[NP], pvx = new float[NP], pvy = new float[NP], pl = new float[NP];
        int pNext;

        int w, h;
        float ppm, floorY, ceilY, playerX;

        int state = START;
        boolean thrusting, onFloor, running;
        float y, vy, ay, dist, speed, bgOff, spawnLeft, stateTime, clock, runPhase;
        int electrons, best;
        String fact = FACTS[0];
        long lastNanos;

        Game(Context c) {
            super(c);
            prefs = c.getSharedPreferences("jetlab", Context.MODE_PRIVATE);
            best = prefs.getInt("best", 0);
            tp.setTypeface(Typeface.MONOSPACE);
        }

        void start() {
            if (running) return;
            running = true;
            lastNanos = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }

        void stop() {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
            thrusting = false;
        }

        @Override
        protected void onSizeChanged(int nw, int nh, int ow, int oh) {
            w = nw; h = nh;
            ppm = h / 9f;
            floorY = h - 0.7f * ppm;
            ceilY = 0.7f * ppm;
            playerX = w * 0.22f;
            if (state == START) y = floorY - 0.62f * ppm;
        }

        void reset() {
            coins.clear(); zappers.clear(); missiles.clear();
            for (int i = 0; i < NP; i++) pl[i] = 0;
            y = floorY - 0.62f * ppm;
            vy = 0; dist = 0; electrons = 0; spawnLeft = w * 0.6f;
            state = PLAY; stateTime = 0;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (state == START || (state == DEAD && stateTime > 0.7f)) reset();
                    thrusting = true;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    thrusting = false;
                    break;
            }
            return true;
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

        void update(float dt) {
            clock += dt;
            stateTime += dt;
            float r = 0.3f * ppm;

            // vertical motion: gravity down, thrust up, speed limited
            boolean fire = thrusting && state == PLAY;
            ay = GRAVITY - (fire ? THRUST : 0);
            vy += ay * dt;
            if (vy > VMAX) vy = VMAX;
            if (vy < -VMAX) vy = -VMAX;
            y += vy * ppm * dt;
            onFloor = false;
            float lo = ceilY + 0.72f * ppm, hi = floorY - 0.62f * ppm;
            if (y < lo) { y = lo; if (vy < 0) vy = 0; }
            if (y > hi) { y = hi; if (vy > 0) vy = 0; onFloor = true; }

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
            if (onFloor) runPhase += speed * dt * 2.2f;

            spawnLeft -= scroll;
            if (spawnLeft <= 0) spawn();

            for (int i = coins.size() - 1; i >= 0; i--) {
                Coin c = coins.get(i);
                c.x -= scroll;
                float dx = c.x - playerX, dy = c.y - y;
                if (!c.taken && dx * dx + dy * dy < (r + 0.22f * ppm) * (r + 0.22f * ppm)) {
                    c.taken = true; electrons++;
                }
                if (c.x < -ppm || c.taken) coins.remove(i);
            }

            for (int i = zappers.size() - 1; i >= 0; i--) {
                Zapper z = zappers.get(i);
                z.x -= scroll;
                z.angle += z.spin * dt;
                if (z.x < -2.5f * ppm) { zappers.remove(i); continue; }
                float hx = (float) Math.cos(z.angle) * z.len / 2, hy = (float) Math.sin(z.angle) * z.len / 2;
                if (segDist(playerX, y, z.x - hx, z.y - hy, z.x + hx, z.y + hy) < r + 0.06f * ppm) die();
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
                    if (dx * dx + dy * dy < (r + 0.22f * ppm) * (r + 0.22f * ppm)) die();
                    if (m.x < -ppm) missiles.remove(i);
                }
            }
        }

        void die() {
            if (state != PLAY) return;
            state = DEAD; stateTime = 0;
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

        // ---------- drawing ----------

        @Override
        protected void onDraw(Canvas c) {
            if (w == 0) return;
            drawBackground(c);
            drawFloor(c);
            drawCoins(c);
            drawZappers(c);
            drawMissiles(c);
            drawParticles(c);
            drawPlayer(c);
            drawHud(c);
            if (state == START) drawTitle(c);
            if (state == DEAD) drawDead(c);
        }

        void drawBackground(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(14, 22, 40), Color.rgb(24, 40, 62), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);

            // slow periodic-table tiles
            float step = 2.2f * ppm;
            int first = (int) Math.floor(bgOff / step);
            float frac = bgOff - first * step;
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(0.55f * ppm);
            for (int k = -1; k < w / step + 2; k++) {
                int idx = first + k;
                int m = Math.floorMod(idx * 7 + 3, 11);
                float tx = k * step - frac + step / 2, ty = (1.4f + (m % 5) * 1.2f) * ppm;
                p.setColor(Color.argb(34, 120, 200, 255));
                rect.set(tx - 0.8f * ppm, ty - 0.8f * ppm, tx + 0.8f * ppm, ty + 0.8f * ppm);
                c.drawRoundRect(rect, 8, 8, p);
                tp.setColor(Color.argb(80, 170, 220, 255));
                c.drawText(SYM[Math.floorMod(idx, SYM.length)], tx, ty + 0.2f * ppm, tp);
            }
            // ceiling pipe
            p.setColor(Color.rgb(36, 50, 72));
            c.drawRect(0, 0, w, ceilY, p);
            p.setColor(Color.rgb(70, 190, 200));
            c.drawRect(0, ceilY - 3, w, ceilY, p);
        }

        void drawFloor(Canvas c) {
            p.setColor(Color.rgb(30, 40, 58));
            c.drawRect(0, floorY, w, h, p);
            p.setColor(Color.rgb(70, 190, 200));
            c.drawRect(0, floorY, w, floorY + 3, p);
            // meter ruler, aligned with the player's distance
            p.setStrokeWidth(2);
            tp.setTextSize(0.32f * ppm);
            tp.setColor(Color.rgb(140, 190, 210));
            int m0 = (int) Math.floor(dist - playerX / ppm);
            for (int m = m0; m < dist + (w - playerX) / ppm + 1; m++) {
                if (m < 0) continue;
                float x = playerX + (m - dist) * ppm;
                boolean ten = m % 10 == 0;
                p.setColor(Color.rgb(90, 150, 175));
                c.drawLine(x, floorY + 3, x, floorY + (ten ? 0.28f : 0.14f) * ppm, p);
                if (ten) c.drawText(m + " m", x, floorY + 0.55f * ppm, tp);
            }
        }

        void drawCoins(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            for (Coin k : coins) {
                float a = clock * 4f + k.x * 0.01f;
                p.setColor(Color.argb(60, 80, 220, 255));
                c.drawCircle(k.x, k.y, 0.26f * ppm, p);
                p.setColor(Color.rgb(120, 235, 255));
                c.drawCircle(k.x, k.y, 0.09f * ppm, p);
                p.setColor(Color.rgb(255, 255, 255));
                c.drawCircle(k.x + (float) Math.cos(a) * 0.2f * ppm, k.y + (float) Math.sin(a) * 0.2f * ppm, 0.045f * ppm, p);
            }
        }

        void drawZappers(Canvas c) {
            for (Zapper z : zappers) {
                float hx = (float) Math.cos(z.angle) * z.len / 2, hy = (float) Math.sin(z.angle) * z.len / 2;
                float ax = z.x - hx, ay2 = z.y - hy, bx = z.x + hx, by = z.y + hy;
                float nx = -hy / (z.len / 2), ny = hx / (z.len / 2);
                path.reset();
                path.moveTo(ax, ay2);
                int segs = 7;
                for (int i = 1; i < segs; i++) {
                    float t = i / (float) segs, j = (rnd.nextFloat() - 0.5f) * 0.4f * ppm;
                    path.lineTo(ax + (bx - ax) * t + nx * j, ay2 + (by - ay2) * t + ny * j);
                }
                path.lineTo(bx, by);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setStrokeWidth(0.22f * ppm);
                p.setColor(Color.argb(60, 120, 200, 255));
                c.drawPath(path, p);
                p.setStrokeWidth(0.07f * ppm);
                p.setColor(Color.rgb(230, 250, 255));
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.rgb(90, 100, 120));
                c.drawCircle(ax, ay2, 0.2f * ppm, p);
                c.drawCircle(bx, by, 0.2f * ppm, p);
                p.setColor(Color.rgb(255, 200, 80));
                c.drawCircle(ax, ay2, 0.09f * ppm, p);
                c.drawCircle(bx, by, 0.09f * ppm, p);
            }
        }

        void drawMissiles(Canvas c) {
            for (Missile m : missiles) {
                if (!m.flying) {
                    boolean on = ((int) (m.warn * 8)) % 2 == 0;
                    if (on) {
                        float wx = w - 0.7f * ppm;
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(Color.rgb(255, 90, 60));
                        path.reset();
                        path.moveTo(wx, m.y - 0.35f * ppm);
                        path.lineTo(wx + 0.5f * ppm, m.y + 0.3f * ppm);
                        path.lineTo(wx - 0.5f * ppm, m.y + 0.3f * ppm);
                        path.close();
                        c.drawPath(path, p);
                        tp.setColor(Color.WHITE);
                        tp.setTextSize(0.4f * ppm);
                        tp.setTextAlign(Paint.Align.CENTER);
                        c.drawText("!", wx, m.y + 0.26f * ppm, tp);
                    }
                } else {
                    // alpha particle: a helium nucleus, two protons and two neutrons
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb(70, 255, 120, 70));
                    c.drawCircle(m.x + 0.6f * ppm, m.y, 0.32f * ppm, p);
                    c.drawCircle(m.x + 1.1f * ppm, m.y, 0.22f * ppm, p);
                    float rr = 0.12f * ppm;
                    p.setColor(Color.rgb(255, 80, 70));
                    c.drawCircle(m.x - rr, m.y - rr, rr * 1.2f, p);
                    c.drawCircle(m.x + rr, m.y + rr, rr * 1.2f, p);
                    p.setColor(Color.rgb(200, 205, 215));
                    c.drawCircle(m.x + rr, m.y - rr, rr * 1.2f, p);
                    c.drawCircle(m.x - rr, m.y + rr, rr * 1.2f, p);
                }
            }
        }

        void drawParticles(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < NP; i++) {
                if (pl[i] <= 0) continue;
                float a = Math.min(1f, pl[i] * 2f);
                p.setColor(Color.argb((int) (a * 255), 255, state == DEAD ? 120 : 190, 60));
                c.drawCircle(px[i], py[i], 0.09f * ppm * (0.4f + a), p);
            }
        }

        void drawPlayer(Canvas c) {
            float u = ppm, cx = playerX, cy = y;
            p.setStyle(Paint.Style.FILL);
            if (state == DEAD && stateTime < 0.2f) return;
            // flame
            if (thrusting && state == PLAY) {
                float fl = (0.45f + rnd.nextFloat() * 0.3f) * u;
                path.reset();
                path.moveTo(cx - 0.42f * u, cy + 0.22f * u);
                path.lineTo(cx - 0.22f * u, cy + 0.22f * u);
                path.lineTo(cx - 0.32f * u, cy + 0.22f * u + fl);
                path.close();
                p.setColor(Color.rgb(255, 170, 40));
                c.drawPath(path, p);
            }
            // legs
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.13f * u);
            p.setColor(Color.rgb(50, 60, 90));
            float sw = onFloor ? (float) Math.sin(runPhase * 6.28f) * 0.18f * u : -0.1f * u;
            c.drawLine(cx - 0.08f * u, cy + 0.22f * u, cx - 0.1f * u + sw, cy + 0.6f * u, p);
            c.drawLine(cx + 0.08f * u, cy + 0.22f * u, cx + 0.1f * u - sw, cy + 0.6f * u, p);
            p.setStyle(Paint.Style.FILL);
            // jetpack tank
            p.setColor(Color.rgb(240, 120, 50));
            rect.set(cx - 0.44f * u, cy - 0.3f * u, cx - 0.2f * u, cy + 0.22f * u);
            c.drawRoundRect(rect, 6, 6, p);
            // lab coat
            p.setColor(Color.rgb(240, 244, 250));
            rect.set(cx - 0.24f * u, cy - 0.35f * u, cx + 0.22f * u, cy + 0.26f * u);
            c.drawRoundRect(rect, 8, 8, p);
            // head and goggles
            p.setColor(Color.rgb(240, 200, 160));
            c.drawCircle(cx + 0.02f * u, cy - 0.52f * u, 0.2f * u, p);
            p.setColor(Color.rgb(60, 70, 90));
            c.drawRect(cx - 0.12f * u, cy - 0.6f * u, cx + 0.24f * u, cy - 0.5f * u, p);
            p.setColor(Color.rgb(120, 235, 255));
            c.drawCircle(cx + 0.14f * u, cy - 0.55f * u, 0.07f * u, p);
        }

        void drawHud(Canvas c) {
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setTextSize(0.42f * ppm);
            tp.setColor(Color.WHITE);
            float x = 0.4f * ppm, yy = ceilY + 0.6f * ppm;
            c.drawText(String.format("%d m", (int) dist), x, yy, tp);
            tp.setColor(Color.rgb(120, 235, 255));
            c.drawText("e- " + electrons, x, yy + 0.55f * ppm, tp);
            tp.setTextAlign(Paint.Align.RIGHT);
            tp.setColor(Color.rgb(170, 200, 220));
            tp.setTextSize(0.34f * ppm);
            float rx = w - 0.4f * ppm;
            c.drawText(String.format("v  %+.1f m/s", -vy), rx, yy - 0.08f * ppm, tp);
            c.drawText(String.format("a  %+.1f m/s²", -ay), rx, yy + 0.32f * ppm, tp);
            c.drawText(String.format("best %d m", best), rx, yy + 0.72f * ppm, tp);
        }

        void drawTitle(Canvas c) {
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(Color.WHITE);
            tp.setTextSize(1.1f * ppm);
            tp.setFakeBoldText(true);
            c.drawText("THRUST LAB", w / 2f, h * 0.38f, tp);
            tp.setFakeBoldText(false);
            tp.setTextSize(0.4f * ppm);
            tp.setColor(Color.rgb(120, 235, 255));
            c.drawText("hold to fire the jetpack, let go to fall", w / 2f, h * 0.38f + 0.9f * ppm, tp);
            tp.setColor(Color.rgb(200, 215, 230));
            c.drawText("tap to start", w / 2f, h * 0.38f + 1.55f * ppm, tp);
        }

        void drawDead(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(170, 8, 12, 24));
            c.drawRect(0, 0, w, h, p);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(Color.WHITE);
            tp.setTextSize(0.9f * ppm);
            c.drawText((int) dist + " m", w / 2f, h * 0.28f, tp);
            tp.setTextSize(0.4f * ppm);
            tp.setColor(Color.rgb(120, 235, 255));
            c.drawText(electrons + " electrons   best " + best + " m", w / 2f, h * 0.28f + 0.8f * ppm, tp);
            tp.setColor(Color.rgb(210, 220, 235));
            tp.setTextSize(0.36f * ppm);
            int bw = (int) (w * 0.6f);
            StaticLayout sl = StaticLayout.Builder.obtain(fact, 0, fact.length(), tp, bw)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).build();
            c.save();
            c.translate((w - bw) / 2f, h * 0.28f + 1.5f * ppm);
            sl.draw(c);
            c.restore();
            if (stateTime > 0.7f) {
                tp.setColor(Color.rgb(255, 200, 80));
                tp.setTextSize(0.4f * ppm);
                c.drawText("tap to try again", w / 2f, h * 0.85f, tp);
            }
        }
    }
}
