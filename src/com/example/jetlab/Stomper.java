package com.example.jetlab;

import android.graphics.Canvas;

import java.util.Arrays;

import static com.example.jetlab.Game.*;
import static com.example.jetlab.Sprites.*;

/** Spring Stomper: bounces by itself; a tap in the air dives down for a big stomp bounce. */
final class Stomper extends Vehicle {
    static final String[] ROWS = rowsOf();

    float squash;          // seconds left squashed flat after a landing
    boolean diving;

    Stomper(Game g) { super(g); }

    static String[] rowsOf() {
        String[] out = Arrays.copyOf(BODY_STOMP, BODY_STOMP.length + LEGS_STOMP.length);
        System.arraycopy(LEGS_STOMP, 0, out, BODY_STOMP.length, LEGS_STOMP.length);
        return out;
    }

    String name() { return "SPRING STOMPER"; }
    String hint() { return "BOUNCES BY ITSELF. TAP IN THE AIR TO STOMP."; }
    String[] rows() { return ROWS; }
    int hTop() { return 12; }
    float radius() { return 8f; }
    int cockpit() { return 2; }
    boolean bounces() { return true; }

    void press() {
        if (!g.onFloor && squash <= 0) { g.vy = VFALL; diving = true; g.sfx(S_FLIP); }
    }

    void tick(float dt) {
        if (squash > 0) squash -= dt;
    }

    void bounce(float impact) {
        squash = 0.14f;
        if (diving) {
            diving = false; g.vy = -11f; g.shake = 5f;
            g.sfx(S_STOMP); g.buzz(20);
            for (int i = 0; i < 14; i++) g.burst(g.playerX, g.y + 8);
        } else {
            g.vy = -(g.thrusting ? 12.5f : 9.5f);
            if (impact > 6f) { g.shake = Math.max(g.shake, 1.5f); g.sfx(S_STOMP); }
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
