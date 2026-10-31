package com.elishajkmiller.formulaheist;

import android.graphics.Canvas;
import android.graphics.Color;

import static com.elishajkmiller.formulaheist.Game.*;
import static com.elishajkmiller.formulaheist.Sprites.*;

/**
 * Da Vinci's aerial screw. Holding spins the rotor up; the spiral blade pushes air down and Newton's third law
 * pushes the craft up. The rotor takes time to spin up and keeps turning a while after you let go.
 */
final class Screw extends Vehicle {
    float rotor = 0.65f, rotAng;    // rotor: speed 0 to 1 (starts near a hover); rotAng: its spin in radians

    Screw(Game g) { super(g); }

    String name() { return "AERIAL SCREW"; }
    String hint() { return "HOLD TO SPIN THE SCREW. IT TAKES A MOMENT TO SPIN UP."; }
    String[] rows() { return SCREW_ROWS; }
    int hTop() { return 12; }
    int hBot() { return 8; }
    int cockpit() { return 7; }

    float accel(float dt) {
        rotor = Math.max(0f, Math.min(1f, rotor + (g.thrusting && g.state == PLAY ? 1.7f : -0.7f) * dt));
        rotAng += rotor * 30f * dt;
        return GRAVITY * g.gravDir - rotor * 34f;
    }

    /** the blade is seen edge-on, so its width follows cos(angle); downwash streaks and an RPM readout show the push */
    void draw(Canvas c) {
        float px = g.playerX, y = g.y;
        int left = Math.round(px) - 10, top = Math.round(y) - 12;
        g.outlined(c, SCREW_ROWS, left, top);
        g.outlined(c, HEAD_ONLY, left, top + 7);
        float span = 22f * (float) Math.cos(rotAng);
        float by = top + 1;
        g.line(c, px - span, by, px + span, by, Color.rgb(200, 160, 100), 2);
        g.line(c, px - span * 0.6f, by + 2, px + span * 0.6f, by + 2, Color.rgb(150, 110, 60), 1);
        g.rectPx(c, px - span - 1, by - 1, 2, 3, Color.rgb(235, 210, 150));
        g.rectPx(c, px + span - 1, by - 1, 2, 3, Color.rgb(235, 210, 150));
        if (rotor > 0.15f) {
            for (int i = 0; i < 4; i++) {
                float dy = ((g.clock * 70f + i * 9f) % 36f);
                float xx = px - 9 + (i & 1) * 6 + i;
                if (y + 9 + dy < g.floorY) g.line(c, xx, y + 9 + dy, xx, y + 12 + dy, Color.argb((int) (150 * rotor), 200, 235, 255), 1);
            }
            g.text(c, "RPM " + (int) (rotor * 120), px, Math.round(y) + 24, 1, Color.rgb(200, 235, 255), 1);
        }
    }
}
