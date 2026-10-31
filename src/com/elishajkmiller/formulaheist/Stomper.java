package com.elishajkmiller.formulaheist;

import android.graphics.Canvas;
import android.graphics.Color;

import java.util.Arrays;

import static com.elishajkmiller.formulaheist.Game.*;
import static com.elishajkmiller.formulaheist.Sprites.*;

/** The bouncy lunar module: bounces by itself on its sprung legs; hold to fire the RCS thrusters and climb like a jetpack. */
final class Stomper extends Vehicle {
    static final String[] ROWS = rowsOf();

    float squash;          // seconds left squashed flat after a landing

    Stomper(Game g) { super(g); }

    static String[] rowsOf() {
        String[] out = Arrays.copyOf(BODY_STOMP, BODY_STOMP.length + LEGS_STOMP.length);
        System.arraycopy(LEGS_STOMP, 0, out, BODY_STOMP.length, LEGS_STOMP.length);
        return out;
    }

    String name() { return "BOUNCY LEM"; }
    String hint() { return "BOUNCES BY ITSELF. HOLD TO FIRE THE RCS THRUSTERS UP."; }
    String[] rows() { return ROWS; }
    int hTop() { return 12; }
    float radius() { return 8f; }
    int cockpit() { return 2; }
    boolean bounces() { return true; }

    static final float RCS_THRUST = 33f;       // m/s^2 up while held; gravity is 22, so it climbs

    boolean firing() { return g.thrusting && g.state == PLAY; }

    float accel(float dt) {
        return GRAVITY * g.gravDir - (firing() ? RCS_THRUST : 0f);
    }

    void tick(float dt) {
        if (squash > 0) squash -= dt;
        if (firing() && ((int) (g.clock * 40) & 1) == 0) rcsPuffs();
    }

    void bounce(float impact) {
        squash = 0.14f;
        g.vy = -(firing() ? 12.5f : 9.5f);
        if (impact > 6f) { g.shake = Math.max(g.shake, 1.5f); g.sfx(S_STOMP); }
    }

    /** cold gas out of the four thruster quads, pushed down so the craft goes up */
    void rcsPuffs() {
        float left = g.playerX - 10, top = g.y - hTop();
        float[] ys = {top + 5, top + 12};
        float[] xs = {left + 1, left + 19};
        for (float yy : ys)
            for (float xx : xs)
                g.puff(xx, yy, (g.rnd.nextFloat() - 0.5f) * 1.5f, 3f + g.rnd.nextFloat() * 2f, Color.rgb(225, 238, 255));
    }

    void draw(Canvas c) {
        int left = Math.round(g.playerX) - 10, top = Math.round(g.y) - hTop();
        boolean comp = squash > 0;
        int off = comp ? 3 : 0;
        g.outlined(c, BODY_STOMP, left, top + off);
        g.outlined(c, comp ? LEGS_STOMP_C : LEGS_STOMP, left, top + BODY_STOMP.length + off);
        g.outlined(c, HEAD_ONLY, left, top + 2 + off);
        if (firing()) {      // short plumes shooting down from the four thruster quads
            for (int k = 0; k < 4; k++) {
                int px = left + (k < 2 ? 1 : 19), py = top + (k % 2 == 0 ? 5 : 12) + off;
                int len = 4 + (int) ((g.clock * 60 + k * 7) % 4);
                g.rectPx(c, px, py, 1, len, Color.argb(200, 235, 245, 255));
                g.rectPx(c, px, py, 1, Math.max(1, len / 2), Color.WHITE);
            }
        }
    }
}
