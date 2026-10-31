package com.elishajkmiller.formulaheist;

import android.graphics.Canvas;
import android.graphics.Color;

import static com.elishajkmiller.formulaheist.Game.*;
import static com.elishajkmiller.formulaheist.Sprites.*;

/** Quantum Entangler: you and a twin on the opposite surface; a tap swaps you over. Burns out after USES jumps. */
final class Quantum extends Vehicle {
    static final int USES = 5;

    int charges = USES;    // teleports left

    Quantum(Game g) { super(g); }

    String name() { return "QUANTUM ENTANGLER"; }
    String hint() { return "TAP TO TELEPORT TO YOUR TWIN. FIVE USES."; }
    String[] rows() { return QUANT; }
    int hTop() { return 14; }
    int cockpit() { return -1; }
    boolean flips() { return true; }
    boolean walks() { return true; }

    /** jump to the mirror position on the opposite surface, gravity follows */
    void press() {
        g.teleFromY = g.y;
        for (int i = 0; i < 12; i++) g.burst(g.playerX, g.y);
        g.y = g.ceilY + g.floorY - g.y;
        g.gravDir = -g.gravDir;
        g.vy = 0;
        g.telePulse = 0.25f;
        g.shake = Math.max(g.shake, 1.5f);
        g.flash = 0.05f;
        g.sfx(S_TELE);
        g.buzz(15);
        for (int i = 0; i < 12; i++) g.burst(g.playerX, g.y);
        if (--charges <= 0) g.breakVehicle();      // the last jump burns it out
    }

    void draw(Canvas c) {
        int cy = Math.round(g.y), gy = Math.round(g.ceilY + g.floorY - g.y);
        for (int yy = Math.min(cy, gy) + 14; yy < Math.max(cy, gy) - 14; yy += 4) {
            if (g.rnd.nextInt(5) > 0) g.rectPx(c, g.playerX, yy, 1, 2, Color.argb(120, 120, 235, 255));
        }
        g.ghost = true;
        body(c, gy, g.gravDir > 0);     // the entangled twin on the other side
        g.ghost = false;
        body(c, cy, g.gravDir < 0);
        // one pip per teleport left
        int py0 = g.gravDir > 0 ? cy - 20 : cy + 17;
        for (int i = 0; i < USES; i++) {
            int pxl = Math.round(g.playerX) - 14 + i * 6;
            g.rectPx(c, pxl - 1, py0 - 1, 5, 5, Color.rgb(8, 10, 22));
            g.rectPx(c, pxl, py0, 3, 3, i < charges ? Color.rgb(120, 235, 255) : Color.rgb(50, 60, 80));
        }
    }

    /** the scientist running on the board, upside down on the ceiling */
    void body(Canvas c, int cy, boolean flip) {
        int left = Math.round(g.playerX) - 10, top = cy - 14;
        if (flip) { c.save(); c.scale(1, -1, g.playerX, cy); }
        int ph = ((int) (g.runPhase * 0.2f * 4)) & 3;
        g.scientist(c, left + 2, top, ph, true);
        g.outlined(c, BOARD, left, top + SPR_HEAD.length + LEGS_A.length);
        if (flip) c.restore();
    }
}
