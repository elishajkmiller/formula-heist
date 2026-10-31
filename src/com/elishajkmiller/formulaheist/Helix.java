package com.elishajkmiller.formulaheist;

import android.graphics.Canvas;
import android.graphics.Color;

import java.util.Arrays;

import static com.elishajkmiller.formulaheist.Game.*;
import static com.elishajkmiller.formulaheist.Sprites.*;

/** DNA Helix: hold to rise, tap to fire base pairs. Its twisting body follows the path the head took. */
final class Helix extends Vehicle {
    final float[] yHist = new float[64];   // where the head has been, so the body can follow it
    int yh;
    float boltCd, tilt;                    // tilt: degrees, nose up is negative

    Helix(Game g) { super(g); }

    String name() { return "DNA HELIX"; }
    String hint() { return "HOLD TO RISE. TAP TO FIRE BASE PAIRS."; }
    String[] rows() { return HELIX_HEAD; }

    void start() { Arrays.fill(yHist, g.y); }

    void press() {
        if (boltCd > 0) return;
        Bolt b = new Bolt();
        b.x = g.playerX + 12; b.y = g.y; b.kind = g.bolts.size() & 1;
        g.bolts.add(b);
        boltCd = 0.3f;
        g.sfx(S_TELE);
    }

    void tick(float dt) {
        if (boltCd > 0) boltCd -= dt;
    }

    /** light gravity, and holding pushes up */
    float accel(float dt) {
        return 14f * g.gravDir - (g.thrusting && g.state == PLAY ? 30f : 0);
    }

    void update(float dt) {
        yh = (yh + 1) & 63; yHist[yh] = g.y;
        // nose up when rising, nose down when sinking, eased so it does not snap
        float target = Math.max(-34f, Math.min(44f, g.vy * 5f));
        tilt += (target - tilt) * Math.min(1f, 9f * dt);
    }

    /** a double helix that trails the head and follows its path, with base pairs for rungs */
    void draw(Canvas c) {
        float px = g.playerX, y = g.y;
        int left = Math.round(px) - 10, top = Math.round(y) - 8;
        int segs = 18;
        float phase = g.clock * 7f;
        float pax = 0, pay = 0, pbx = 0, pby = 0;
        for (int i = 0; i < segs; i++) {
            float sx = px - 12 - i * 5f;
            float sy = yHist[(yh - i * 2) & 63] + (float) Math.sin(phase + i * 0.55f) * 2f * Math.min(1f, i / 4f);
            float tw = (float) Math.sin(phase * 1.3f + i * 0.9f);             // the twist
            float off = tw * 6f * (1f - i / (segs * 1.4f));                    // narrows toward the tail
            float ay2 = sy - off, by2 = sy + off;
            int sz = i < 8 ? 3 : 2;
            if ((i & 1) == 0) {
                int[] pr = RUNG[(i / 2) & 3];
                g.line(c, sx, ay2, sx, by2, Color.argb(190, 220, 230, 245), 1);
                g.rectPx(c, sx - 1, ay2 - 1, 2, 2, 0xFF000000 | pr[0]);
                g.rectPx(c, sx - 1, by2 - 1, 2, 2, 0xFF000000 | pr[1]);
            }
            if (i > 0) {
                g.line(c, pax, pay, sx, ay2, Color.rgb(70, 200, 190), 1);
                g.line(c, pbx, pby, sx, by2, Color.rgb(210, 90, 190), 1);
            }
            // the strand nearer the viewer is drawn last
            if (tw > 0) {
                g.rectPx(c, sx - 1, by2 - 1, sz, sz, Color.rgb(230, 100, 205));
                g.rectPx(c, sx - 1, ay2 - 1, sz, sz, Color.rgb(90, 230, 210));
            } else {
                g.rectPx(c, sx - 1, ay2 - 1, sz, sz, Color.rgb(90, 230, 210));
                g.rectPx(c, sx - 1, by2 - 1, sz, sz, Color.rgb(230, 100, 205));
            }
            pax = sx; pay = ay2; pbx = sx; pby = by2;
        }
        c.save();
        c.rotate(tilt, px, y);
        g.outlined(c, HELIX_HEAD, left, top);
        g.outlined(c, HEAD_ONLY, left, top + 3);
        c.restore();
    }
}
