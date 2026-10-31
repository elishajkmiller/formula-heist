package com.example.jetlab;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import static com.example.jetlab.Geom.*;

/**
 * Floor and ceiling spikes, a sliding saw blade and a laser gate. Zappers and missiles live in their own lists.
 * To add a hazard, write a subclass here and a spawn method in Game.
 */
abstract class Hazard {
    float x, y, w, phase, amp;

    Hazard(float x, float y, float w, float phase) { this.x = x; this.y = y; this.w = w; this.phase = phase; }

    abstract boolean hits(Game g, float r);
    abstract void draw(Game g, Canvas c);
    void update(Game g, float dt) { }
    /** a helix base pair at bx, by cuts through it */
    boolean cutBy(Game g, float bx, float by) { return false; }
}

/** a metal strip with a row of steel spikes pointing into the room */
final class Spikes extends Hazard {
    final boolean floor;

    Spikes(boolean floor, float x, float y, float w) { super(x, y, w, 0); this.floor = floor; }

    boolean hits(Game g, float r) {
        return circleHitsRect(g.playerX, g.y, r, x, floor ? g.floorY - 8 : g.ceilY, w, 8);
    }

    void draw(Game g, Canvas c) {
        int x0 = Math.round(x), wd = Math.round(w);
        int base = Math.round(floor ? g.floorY : g.ceilY);
        g.rectPx(c, x0, floor ? base - 2 : base, wd, 2, Color.rgb(70, 76, 94));
        for (int sx = x0; sx + 6 <= x0 + wd; sx += 6) {
            for (int row = 0; row < 8; row++) {
                int half = 3 - row * 3 / 8;                         // the spike narrows to a point
                int ry = floor ? base - 3 - row : base + 2 + row;
                g.rectPx(c, sx + 3 - half, ry, half * 2, 1, row < 3 ? Color.rgb(205, 212, 228) : Color.rgb(160, 170, 190));
                g.rectPx(c, sx + 3 - half, ry, 1, 1, Color.rgb(8, 10, 22));
                g.rectPx(c, sx + 2 + half, ry, 1, 1, Color.rgb(8, 10, 22));
            }
            g.rectPx(c, sx + 2, floor ? base - 11 : base + 10, 2, 1, Color.rgb(8, 10, 22));
        }
    }
}

/** a saw blade sliding up and down a rail; amp is how far it slides each way */
final class Saw extends Hazard {
    Saw(float x, float y, float amp, float phase) { super(x, y, 0, phase); this.amp = amp; }

    float bladeY(Game g) { return y + (float) Math.sin(g.clock * 1.6f + phase) * amp; }

    boolean hits(Game g, float r) {
        float dx = x - g.playerX, dy = bladeY(g) - g.y, rr = r + 7;
        return dx * dx + dy * dy < rr * rr;
    }

    boolean cutBy(Game g, float bx, float by) {
        float dx = x - bx, dy = bladeY(g) - by;
        return dx * dx + dy * dy < 100f;
    }

    void draw(Game g, Canvas c) {
        float cy = bladeY(g);
        int cx = Math.round(x);
        // the rail it slides on
        g.rectPx(c, cx - 1, y - amp - 6, 2, amp * 2 + 12, Color.rgb(50, 56, 76));
        g.rectPx(c, cx - 3, y - amp - 7, 6, 2, Color.rgb(90, 98, 124));
        g.rectPx(c, cx - 3, y + amp + 5, 6, 2, Color.rgb(90, 98, 124));
        // the blade: sixteen teeth turning
        float a0 = g.clock * 9f;
        g.path.reset();
        for (int i = 0; i < 16; i++) {
            float a = a0 + i * (float) Math.PI / 8f, rr = (i & 1) == 0 ? 9f : 6f;
            float px0 = cx + (float) Math.cos(a) * rr, py0 = cy + (float) Math.sin(a) * rr;
            if (i == 0) g.path.moveTo(Math.round(px0), Math.round(py0)); else g.path.lineTo(Math.round(px0), Math.round(py0));
        }
        g.path.close();
        Paint p = g.p;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(8, 10, 22));
        c.save(); c.scale(1.18f, 1.18f, cx, cy); c.drawPath(g.path, p); c.restore();
        p.setColor(Color.rgb(196, 204, 222));
        c.drawPath(g.path, p);
        p.setColor(Color.rgb(120, 130, 156));
        c.drawCircle(cx, cy, 4, p);
        p.setColor(Color.rgb(60, 66, 88));
        c.drawCircle(cx, cy, 2, p);
    }
}

/** a laser gate: a red warning, then a full beam top to bottom with one gap to fly through; y is the gap's middle */
final class Laser extends Hazard {
    static final float GAP = 26f;          // half the opening, in pixels

    Laser(float x, float y) { super(x, y, 0, 0); }

    boolean on() { float t = phase % 3f; return t >= 1f && t < 1.9f; }
    boolean warning() { float t = phase % 3f; return t > 0f && t < 1f; }

    void update(Game g, float dt) {
        if (x < g.w + 10) phase += dt;       // the cycle starts when it comes on screen
    }

    boolean hits(Game g, float r) {
        return on() && Math.abs(x - g.playerX) < r + 2 && Math.abs(g.y - y) > GAP - r;
    }

    void draw(Game g, Canvas c) {
        int lx = Math.round(x), ty = Math.round(g.ceilY), by = Math.round(g.floorY);
        int g0 = Math.round(y - GAP), g1 = Math.round(y + GAP);
        boolean on = on(), warn = warning();
        // the emitters at both ends of the gate and around the gap
        for (int yy : new int[] {ty, g0 - 2, g1, by - 5}) {
            g.rectPx(c, lx - 4, yy, 8, 5, Color.rgb(8, 10, 22));
            g.rectPx(c, lx - 3, yy + 1, 6, 3, Color.rgb(96, 104, 130));
            g.rectPx(c, lx - 1, yy + 2, 2, 1, on ? Color.rgb(255, 80, 70) : Color.rgb(80, 40, 50));
        }
        if (on) {
            int[][] spans = {{ty + 5, g0 - 2}, {g1 + 5, by - 5}};
            for (int[] sp : spans) {
                g.rectPx(c, lx - 3, sp[0], 7, sp[1] - sp[0], Color.argb(70, 255, 70, 60));
                g.rectPx(c, lx - 1, sp[0], 3, sp[1] - sp[0], Color.rgb(255, 90, 80));
                g.rectPx(c, lx, sp[0], 1, sp[1] - sp[0], Color.WHITE);
            }
        } else if (warn && ((int) (g.clock * 10) & 1) == 0) {
            for (int yy = ty + 6; yy < by - 5; yy += 4) {
                if (yy > g0 - 2 && yy < g1 + 5) continue;
                g.rectPx(c, lx, yy, 1, 2, Color.rgb(255, 120, 110));
            }
        }
    }
}
