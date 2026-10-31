package com.example.formulaheist;

import android.graphics.Canvas;
import android.graphics.Color;

import static com.example.formulaheist.Game.*;
import static com.example.formulaheist.Sprites.*;

/** Gravity Inverter: a magnet pod; a tap flips gravity so you fall to the ceiling or back to the floor. */
final class Inverter extends Vehicle {
    Inverter(Game g) { super(g); }

    String name() { return "GRAVITY INVERTER"; }
    String hint() { return "TAP TO FLIP GRAVITY."; }
    String[] rows() { return POD; }
    boolean flips() { return true; }

    void press() {
        g.gravDir = -g.gravDir;
        g.vy *= 0.3f;
        g.shake = Math.max(g.shake, 2f);
        g.sfx(S_FLIP);
        g.buzz(15);
        for (int i = 0; i < 10; i++) g.burst(g.playerX, g.y);
    }

    void bumpCeiling(float vy) {
        if (vy < -6f) { g.shake = Math.max(g.shake, 2f); g.sfx(S_STOMP); for (int i = 0; i < 6; i++) g.burst(g.playerX, g.y - 6); }
    }

    void draw(Canvas c) {
        int left = Math.round(g.playerX) - 10, cy = Math.round(g.y), top = cy - hTop();
        boolean flip = g.gravDir < 0;
        if (flip) { c.save(); c.scale(1, -1, g.playerX, cy); }
        g.outlined(c, POD, left, top);
        g.outlined(c, HEAD_ONLY, left, top + 3);
        // field sparks at both magnet caps
        g.rectPx(c, left + 6 + g.rnd.nextInt(8), top - 1 - g.rnd.nextInt(2), 1, 1, Color.rgb(255, 150, 150));
        g.rectPx(c, left + 6 + g.rnd.nextInt(8), top + 16 + g.rnd.nextInt(2), 1, 1, Color.rgb(150, 190, 255));
        if (flip) c.restore();
    }
}
