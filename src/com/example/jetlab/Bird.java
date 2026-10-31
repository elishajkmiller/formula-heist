package com.example.jetlab;

import android.graphics.Canvas;
import android.graphics.Color;

import static com.example.jetlab.Game.*;
import static com.example.jetlab.Sprites.*;

/**
 * Bernoulli Bird: a tap is a wingbeat, holding spreads the wing to glide. While gliding it draws the Bernoulli
 * picture: air races over the curved top (short gaps, fast, low pressure) and drifts under the flat bottom
 * (slow, high pressure), so the wing is pushed up.
 */
final class Bird extends Vehicle {
    float flapT, tilt;     // flapT: 1 at the start of a wingbeat, down to 0; tilt in degrees, nose up negative

    Bird(Game g) { super(g); }

    String name() { return "BERNOULLI BIRD"; }
    String hint() { return "TAP TO FLAP. HOLD TO GLIDE ON LIFT."; }
    String[] rows() { return BIRD_ROWS; }

    boolean gliding() { return g.thrusting && g.state == PLAY; }

    void press() {
        g.vy = -7f; flapT = 1f;                       // a wingbeat
        g.sfx(S_JUMP);
        for (int i = 0; i < 3; i++) g.burst(g.playerX - 6, g.y + 4);
    }

    void tick(float dt) {
        if (flapT > 0) flapT -= dt * 4f;
    }

    /** spread wings: lift cancels most of gravity */
    float accel(float dt) {
        return GRAVITY * g.gravDir * (gliding() ? 0.3f : 1f);
    }

    void update(float dt) {
        tilt += (Math.max(-25f, Math.min(30f, g.vy * 3f)) - tilt) * Math.min(1f, 9f * dt);
    }

    void draw(Canvas c) {
        float px = g.playerX, y = g.y;
        int left = Math.round(px) - 10, top = Math.round(y) - 8;
        boolean glide = gliding();
        float lift = flapT > 0 ? (float) Math.cos(Math.PI * (1 - flapT)) : glide ? 0.35f : -0.3f;   // wing up is positive
        float sx = px - 2, sy = y + 3;
        c.save();
        c.rotate(tilt, px, y);
        g.outlined(c, BIRD_ROWS, left, top);
        g.outlined(c, HEAD_ONLY, left, top + 3);
        for (int k = 0; k < 5; k++) {
            float len = 15 - k * 2f, ex = sx - 4 - k * 2.5f, ey = sy - lift * len;
            g.line(c, sx, sy, ex, ey, k == 0 ? Color.rgb(120, 235, 255) : Color.WHITE, 2);
        }
        c.restore();
        if (glide) {
            for (int i = 0; i < 3; i++) {
                float fast = (g.clock * 90f + i * 13f) % 30f, slow = (g.clock * 25f + i * 11f) % 30f;
                float x1 = px + 6 - fast;
                g.line(c, x1, y - 15 - (i & 1) * 2, x1 - 7, y - 15 - (i & 1) * 2, Color.rgb(160, 240, 255), 1);
                float x2 = px + 6 - slow;
                g.line(c, x2, y + 12 + i * 3, x2 - 3, y + 12 + i * 3, Color.argb(150, 255, 190, 120), 1);
            }
            if (y - 24 > g.ceilY) g.text(c, "P-", px - 18, y - 24, 1, Color.rgb(160, 240, 255), 1);
            g.text(c, "P+", px - 18, y + 25, 1, Color.rgb(255, 190, 120), 1);
        }
    }
}
