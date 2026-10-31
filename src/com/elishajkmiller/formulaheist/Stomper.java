package com.elishajkmiller.formulaheist;

import android.graphics.Canvas;
import android.graphics.Color;

import java.util.Arrays;

import static com.elishajkmiller.formulaheist.Game.*;
import static com.elishajkmiller.formulaheist.Sprites.*;

/** The bouncy lunar module: bounces by itself on its sprung legs; a tap in the air fires the RCS thrusters and dives for a big stomp bounce. */
final class Stomper extends Vehicle {
    static final String[] ROWS = rowsOf();

    float squash;          // seconds left squashed flat after a landing
    boolean diving;
    float rcsT;            // seconds the RCS thrusters keep firing

    Stomper(Game g) { super(g); }

    static String[] rowsOf() {
        String[] out = Arrays.copyOf(BODY_STOMP, BODY_STOMP.length + LEGS_STOMP.length);
        System.arraycopy(LEGS_STOMP, 0, out, BODY_STOMP.length, LEGS_STOMP.length);
        return out;
    }

    String name() { return "BOUNCY LEM"; }
    String hint() { return "BOUNCES BY ITSELF. TAP IN THE AIR FOR RCS DIVE."; }
    String[] rows() { return ROWS; }
    int hTop() { return 12; }
    float radius() { return 8f; }
    int cockpit() { return 2; }
    boolean bounces() { return true; }

    void press() {
        if (!g.onFloor && squash <= 0) { g.vy = VFALL; diving = true; g.sfx(S_FLIP); rcsT = 0.3f; rcsPuffs(); }
    }

    void tick(float dt) {
        if (squash > 0) squash -= dt;
        if (rcsT > 0) { rcsT -= dt; if (((int) (rcsT * 40) & 1) == 0) rcsPuffs(); }
    }

    void bounce(float impact) {
        squash = 0.14f;
        rcsT = 0.12f;
        if (diving) {
            diving = false; g.vy = -11f; g.shake = 5f;
            g.sfx(S_STOMP); g.buzz(20);
            for (int i = 0; i < 14; i++) g.burst(g.playerX, g.y + 8);
        } else {
            g.vy = -(g.thrusting ? 12.5f : 9.5f);
            if (impact > 6f) { g.shake = Math.max(g.shake, 1.5f); g.sfx(S_STOMP); }
        }
    }

    /** a puff of cold gas out of each of the four thruster quads, sideways like the real ones */
    void rcsPuffs() {
        float left = g.playerX - 10, top = g.y - hTop();
        int[] rows = {2, 3, 10, 11};
        for (int i = 0; i < rows.length; i++) {
            g.puff(left - 1, top + rows[i] + 0.5f, -3f - g.rnd.nextFloat() * 2f, (rows[i] < 6 ? -0.8f : 0.8f), Color.rgb(225, 238, 255));
            g.puff(left + 21, top + rows[i] + 0.5f, 3f + g.rnd.nextFloat() * 2f, (rows[i] < 6 ? -0.8f : 0.8f), Color.rgb(225, 238, 255));
        }
    }

    void draw(Canvas c) {
        int left = Math.round(g.playerX) - 10, top = Math.round(g.y) - hTop();
        boolean comp = squash > 0;
        int off = comp ? 3 : 0;
        g.outlined(c, BODY_STOMP, left, top + off);
        g.outlined(c, comp ? LEGS_STOMP_C : LEGS_STOMP, left, top + BODY_STOMP.length + off);
        g.outlined(c, HEAD_ONLY, left, top + 2 + off);
    }
}
