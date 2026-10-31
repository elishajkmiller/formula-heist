package com.elishajkmiller.formulaheist;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.Random;

import static com.elishajkmiller.formulaheist.Game.*;
import static com.elishajkmiller.formulaheist.Sprites.*;

/**
 * A room seen through the wall as you fly past, one subclass each. To add a room, write a subclass and list it
 * in all(); the rooms repeat along the wall in that order.
 */
abstract class Room {
    final Game g;

    Room(Game g) { this.g = g; }

    static Room[] all(Game g) {
        return new Room[] {new Control(g), new Lab(g), new Computer(g), new Observatory(g), new Flex(g),
                new Gym(g), new Break(g), new CatBox(g), new Wheel(g), new Broken(g)};
    }

    /** what the plate above the opening says */
    abstract String name();
    /** the inside, already clipped to the opening at ox, oy (ow by oh); n is the room's number along the wall */
    abstract void draw(Canvas c, int ox, int oy, int ow, int oh, int n);
    /** the name plate blinks red */
    boolean alarm() { return false; }

    // ---------- drawing helpers shared by the rooms ----------

    void rectPx(Canvas c, float x, float yy, float rw, float rh, int col) { g.rectPx(c, x, yy, rw, rh, col); }

    void text(Canvas c, String s, float x, float yy, int sc, int col, int align) { g.text(c, s, x, yy, sc, col, align); }

    void textOut(Canvas c, String s, float x, float yy, int sc, int col, int align, boolean outline) { g.textOut(c, s, x, yy, sc, col, align, outline); }

    /** a tiny scientist for the rooms: legs swing with phase, coat colour given */
    void miniScientist(Canvas c, int cx, int base, int swing, int coat, boolean goggles) {
        int skin = Color.rgb(240, 200, 160);
        rectPx(c, cx - 2 + swing, base - 4, 2, 4, Color.rgb(50, 60, 100));
        rectPx(c, cx - swing, base - 4, 2, 4, Color.rgb(50, 60, 100));
        rectPx(c, cx - 2 + swing, base - 1, 2, 1, Color.rgb(30, 30, 40));
        rectPx(c, cx - swing, base - 1, 2, 1, Color.rgb(30, 30, 40));
        rectPx(c, cx - 3, base - 10, 6, 6, coat);
        rectPx(c, cx - 3, base - 10, 1, 6, Color.rgb(170, 180, 200));
        rectPx(c, cx - 2, base - 15, 5, 5, skin);
        rectPx(c, cx - 2, base - 15, 5, 2, Color.rgb(90, 60, 40));
        rectPx(c, cx, base - 13, 1, 1, Color.rgb(20, 20, 30));
        rectPx(c, cx + 2, base - 13, 1, 1, Color.rgb(20, 20, 30));
        if (goggles) rectPx(c, cx - 1, base - 14, 4, 1, Color.rgb(120, 235, 255));
    }

    void drawCat(Canvas c, int cx, int top, boolean alive, int alpha) {
        int fur = Color.argb(alpha, 232, 150, 60), dark = Color.argb(alpha, 160, 90, 30), ink = Color.argb(alpha, 20, 20, 30);
        if (alive) {
            rectPx(c, cx - 6, top - 10, 12, 10, fur);                        // sitting up
            rectPx(c, cx - 6, top - 19, 12, 9, fur);
            rectPx(c, cx - 6, top - 22, 3, 3, fur);                          // ears
            rectPx(c, cx + 3, top - 22, 3, 3, fur);
            rectPx(c, cx - 4, top - 16, 2, 2, Color.argb(alpha, 120, 255, 120));
            rectPx(c, cx + 2, top - 16, 2, 2, Color.argb(alpha, 120, 255, 120));
            rectPx(c, cx - 5, top - 10, 10, 1, dark);
            rectPx(c, cx + 6, top - 8, 3, 8, fur);                           // tail
        } else {
            rectPx(c, cx - 9, top - 7, 18, 7, fur);                          // lying on its back with its legs up
            rectPx(c, cx - 8, top - 12, 2, 5, fur);
            rectPx(c, cx + 5, top - 12, 2, 5, fur);
            rectPx(c, cx - 12, top - 10, 7, 8, fur);                         // head
            rectPx(c, cx - 11, top - 8, 1, 1, ink);                          // x x eyes
            rectPx(c, cx - 9, top - 8, 1, 1, ink);
            rectPx(c, cx - 10, top - 7, 1, 1, ink);
            rectPx(c, cx - 11, top - 6, 1, 1, ink);
            rectPx(c, cx - 9, top - 6, 1, 1, ink);
        }
    }

    /** draw a sprite with its own palette, so the clothes store and hair colours do not recolour it */
    void drawPix(Canvas c, String[] rows, int left, int top, String keys, int[] cols) {
        g.p.setStyle(Paint.Style.FILL);
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int k = 0; k < row.length(); k++) {
                int ki = keys.indexOf(row.charAt(k));
                if (ki < 0) continue;
                g.p.setColor(cols[ki]);
                c.drawRect(left + k, top + r, left + k + 1, top + r + 1, g.p);
            }
        }
    }

    final float[] hsv = new float[3];

    int hue(float deg) {
        hsv[0] = ((deg % 360f) + 360f) % 360f; hsv[1] = 0.8f; hsv[2] = 1f;
        return Color.HSVToColor(hsv);
    }

    // ---------- the rooms ----------

    static final class Control extends Room {
        Control(Game g) { super(g); }
        String name() { return "MISSION CONTROL"; }

        void draw(Canvas c, int ox, int oy, int ow, int oh, int k) {
            int[] skin = {Color.rgb(240, 200, 160), Color.rgb(205, 160, 120), Color.rgb(150, 105, 75)};
            rectPx(c, ox, oy, ow, oh, Color.rgb(44, 62, 60));
            rectPx(c, ox, oy + oh - 6, ow, 6, Color.rgb(70, 78, 70));
            // big world map with a ground track
            int sx = ox + 52, sy = oy + 6;
            rectPx(c, sx - 2, sy - 2, 70, 40, Color.rgb(12, 16, 22));
            rectPx(c, sx, sy, 66, 36, Color.rgb(10, 30, 70));
            for (int gx = 0; gx < 66; gx += 11) rectPx(c, sx + gx, sy, 1, 36, Color.rgb(20, 50, 100));
            for (int gy = 0; gy < 36; gy += 9) rectPx(c, sx, sy + gy, 66, 1, Color.rgb(20, 50, 100));
            int land = Color.rgb(30, 100, 70);
            rectPx(c, sx + 6, sy + 8, 12, 10, land);
            rectPx(c, sx + 10, sy + 18, 6, 10, land);
            rectPx(c, sx + 28, sy + 6, 10, 7, land);
            rectPx(c, sx + 30, sy + 13, 10, 14, land);
            rectPx(c, sx + 46, sy + 8, 14, 8, land);
            for (int i = 0; i < 66; i += 2) rectPx(c, sx + i, sy + 18 + (float) Math.sin(i * 0.18) * 12, 1, 1, Color.rgb(255, 220, 90));
            int mi = (int) ((g.clock * 10) % 66);
            rectPx(c, sx + mi - 1, sy + 17 + (float) Math.sin(mi * 0.18) * 12, 3, 3, Color.WHITE);
            // flanking data screens
            rectPx(c, ox + 10, oy + 6, 34, 28, Color.rgb(10, 22, 18));
            int di = ((int) (g.clock * 3)) % 18;
            for (int i = 0; i < 4; i++) textOut(c, DIG[di + i % 3], ox + 12, oy + 8 + i * 6, 1, Color.rgb(90, 230, 120), 0, false);
            rectPx(c, ox + ow - 44, oy + 6, 34, 28, Color.rgb(10, 22, 18));
            for (int b = 0; b < 6; b++) {
                int bh = (int) (Math.abs(Math.sin(g.clock * 2 + b * 1.7)) * 18) + 2;
                rectPx(c, ox + ow - 42 + b * 5, oy + 32 - bh, 3, bh, Color.rgb(80, 230, 120));
            }
            // tiered rows of two-tone green consoles with controllers in white shirts and headsets
            for (int row = 0; row < 3; row++) {
                int ry = oy + 56 + row * 18;
                for (int i = 0; i < 5; i++) {
                    int cx = ox + 6 + i * 33 + (row == 1 ? 8 : 0);
                    if (cx + 24 > ox + ow) continue;
                    int hsh = k * 31 + row * 7 + i * 13;
                    if ((hsh & 3) != 0) {
                        int bob = (((int) (g.clock * 3) + hsh) & 7) == 0 ? 1 : 0;
                        rectPx(c, cx + 7, ry - 8 + bob, 10, 9, Color.rgb(240, 244, 250));
                        rectPx(c, cx + 9, ry - 14 + bob, 6, 6, skin[(hsh >> 2) % 3 < 0 ? 0 : (hsh >> 2) % 3]);
                        rectPx(c, cx + 9, ry - 14 + bob, 6, 2, HAIRS[(hsh >> 1) & 3]);
                        rectPx(c, cx + 8, ry - 13 + bob, 1, 4, Color.rgb(40, 40, 50));
                        rectPx(c, cx + 15, ry - 13 + bob, 1, 4, Color.rgb(40, 40, 50));
                    }
                    rectPx(c, cx, ry, 24, 4, Color.rgb(60, 120, 85));
                    rectPx(c, cx, ry + 4, 24, 9, Color.rgb(120, 170, 135));
                    rectPx(c, cx + 3, ry + 5, 7, 5, Color.rgb(15, 40, 25));
                    rectPx(c, cx + 3, ry + 5 + ((int) (g.clock * 8) + hsh) % 5, 7, 1, Color.rgb(90, 220, 120));
                    for (int b = 0; b < 5; b++) {
                        boolean on = (((int) (g.clock * 2) + hsh + b) & 3) == 0;
                        rectPx(c, cx + 12 + b * 2, ry + 6, 1, 3, on ? Color.rgb(255, 190, 60) : Color.rgb(70, 70, 55));
                    }
                }
            }
            // rocket-shaped coffee pot in the corner
            rectPx(c, ox + 4, oy + oh - 18, 3, 10, Color.rgb(235, 235, 240));
            rectPx(c, ox + 4, oy + oh - 20, 3, 2, Color.rgb(220, 70, 70));
            rectPx(c, ox + 3, oy + oh - 10, 5, 2, Color.rgb(220, 70, 70));
            // cigarette haze drifting up
            for (int i = 0; i < 6; i++) {
                float hy = oy + oh - ((g.clock * 6 + i * 17) % 80);
                float hx = ox + 20 + i * 26 + (float) Math.sin(g.clock + i) * 4;
                rectPx(c, hx, hy, 14, 2, Color.argb(28, 200, 200, 190));
            }
        }
    }

    static final class Lab extends Room {
        Lab(Game g) { super(g); }
        String name() { return "CHEMISTRY LAB"; }

        void draw(Canvas c, int ox, int oy, int ow, int oh, int n) {
            int[] liq = {Color.rgb(90, 230, 120), Color.rgb(255, 120, 200), Color.rgb(120, 200, 255), Color.rgb(255, 210, 80), Color.rgb(170, 120, 255)};
            rectPx(c, ox, oy, ow, oh, Color.rgb(60, 95, 105));
            for (int gx = 0; gx < ow; gx += 12) rectPx(c, ox + gx, oy, 1, oh, Color.rgb(50, 82, 92));
            // chart on the wall
            for (int r = 0; r < 4; r++) for (int q = 0; q < 8; q++)
                rectPx(c, ox + 12 + q * 6, oy + 8 + r * 6, 5, 5, ((q + r) & 1) == 0 ? Color.rgb(90, 150, 200) : Color.rgb(200, 150, 90));
            // shelf with jars
            rectPx(c, ox + 100, oy + 24, 60, 2, Color.rgb(110, 80, 50));
            for (int i = 0; i < 5; i++) rectPx(c, ox + 104 + i * 11, oy + 14, 7, 10, liq[(i + 2) % 5]);
            // bench
            int by = oy + oh - 24;
            rectPx(c, ox, by, ow, oh - 24, Color.rgb(110, 80, 50));
            rectPx(c, ox, by, ow, 3, Color.rgb(150, 115, 75));
            for (int i = 0; i < 6; i++) {
                int fx = ox + 10 + i * 26;
                int col = liq[i % 5];
                rectPx(c, fx + 3, by - 22, 4, 8, Color.rgb(200, 225, 235));
                rectPx(c, fx + 2, by - 14, 6, 3, Color.rgb(200, 225, 235));
                rectPx(c, fx, by - 11, 10, 11, Color.rgb(200, 225, 235));
                rectPx(c, fx + 1, by - 8, 8, 8, col);
                for (int b = 0; b < 2; b++) {
                    float yy = by - 4 - ((g.clock * 8 + i * 5 + b * 7) % 14);
                    rectPx(c, fx + 3 + b * 3, yy, 1, 1, Color.WHITE);
                }
            }
            // burner flame
            int fx2 = ox + ow - 14;
            rectPx(c, fx2, by - 4, 6, 4, Color.rgb(90, 90, 100));
            int fl = 4 + (int) (g.clock * 20) % 3;
            rectPx(c, fx2 + 1, by - 4 - fl, 4, fl, Color.rgb(90, 140, 255));
            rectPx(c, fx2 + 2, by - 4 - fl + 1, 2, fl - 1, Color.rgb(255, 220, 120));
        }
    }

    static final class Computer extends Room {
        Computer(Game g) { super(g); }
        String name() { return "COMPUTER ROOM"; }

        void draw(Canvas c, int ox, int oy, int ow, int oh, int n) {
            rectPx(c, ox, oy, ow, oh, Color.rgb(30, 36, 52));
            rectPx(c, ox, oy + oh - 6, ow, 6, Color.rgb(60, 62, 70));
            for (int i = 0; i < 4; i++) {
                int cx = ox + 4 + i * 41, cy = oy + 6, ch = oh - 14;
                rectPx(c, cx, cy, 38, ch, Color.rgb(150, 155, 168));
                rectPx(c, cx + 2, cy + 2, 34, ch - 4, Color.rgb(100, 106, 120));
                // blinking lamp rows
                for (int r = 0; r < 4; r++) for (int q = 0; q < 8; q++) {
                    boolean on = (((int) (g.clock * 5) + r * 3 + q * 5 + i) % 4) == 0;
                    rectPx(c, cx + 5 + q * 4, cy + ch - 24 + r * 5, 2, 2, on ? Color.rgb(255, 190, 60) : Color.rgb(60, 56, 50));
                }
                // two tape reels turning against each other
                for (int rl = 0; rl < 2; rl++) {
                    float rx = cx + 10 + rl * 18, ry = cy + 18;
                    float ang = g.clock * ((rl == 0) ? 4f : -3.2f) + i;
                    g.p.setStyle(Paint.Style.FILL);
                    g.p.setColor(Color.rgb(25, 25, 32));
                    c.drawCircle(rx, ry, 8, g.p);
                    g.p.setColor(Color.rgb(150, 100, 60));
                    c.drawCircle(rx, ry, 6, g.p);
                    g.p.setStyle(Paint.Style.STROKE);
                    g.p.setStrokeWidth(1);
                    g.p.setColor(Color.rgb(25, 25, 32));
                    for (int s = 0; s < 3; s++) {
                        double a = ang + s * 2.0944;
                        c.drawLine(rx, ry, rx + (float) Math.cos(a) * 6, ry + (float) Math.sin(a) * 6, g.p);
                    }
                    g.p.setStyle(Paint.Style.FILL);
                }
                rectPx(c, cx + 10, cy + 27, 18, 1, Color.rgb(60, 40, 30));
            }
        }
    }

    static final class Observatory extends Room {
        Observatory(Game g) { super(g); }
        String name() { return "OBSERVATORY"; }

        void draw(Canvas c, int ox, int oy, int ow, int oh, int n) {
            rectPx(c, ox, oy, ow, oh, Color.rgb(8, 10, 28));
            for (int i = 0; i < 34; i++) {
                int sx = (i * 37) % ow, sy = (i * 53) % (oh - 16);
                boolean bright = (((int) (g.clock * 3) + i) & 3) == 0;
                rectPx(c, ox + sx, oy + sy, 1, 1, bright ? Color.WHITE : Color.rgb(110, 120, 160));
            }
            g.p.setStyle(Paint.Style.FILL);
            g.p.setColor(Color.rgb(225, 225, 205));
            c.drawCircle(ox + ow - 30, oy + 24, 10, g.p);
            g.p.setColor(Color.rgb(190, 190, 170));
            c.drawCircle(ox + ow - 33, oy + 21, 3, g.p);
            c.drawCircle(ox + ow - 26, oy + 28, 2, g.p);
            // dome walls either side of the slit
            rectPx(c, ox, oy, 22, oh, Color.rgb(52, 56, 70));
            rectPx(c, ox + ow - 22, oy, 22, oh, Color.rgb(52, 56, 70));
            rectPx(c, ox + 22, oy, 2, oh, Color.rgb(90, 96, 115));
            rectPx(c, ox + ow - 24, oy, 2, oh, Color.rgb(90, 96, 115));
            rectPx(c, ox, oy + oh - 10, ow, 10, Color.rgb(44, 46, 58));
            // telescope tube and mount
            float bx = ox + ow / 2f - 10, by = oy + oh - 10;
            g.path.reset();
            g.path.moveTo(bx - 6, by);
            g.path.lineTo(bx + 6, by);
            g.path.lineTo(bx + 36, oy + 22);
            g.path.lineTo(bx + 22, oy + 18);
            g.path.close();
            g.p.setColor(Color.rgb(170, 176, 192));
            c.drawPath(g.path, g.p);
            rectPx(c, bx + 20, oy + 16, 18, 6, Color.rgb(60, 66, 80));
            rectPx(c, bx - 3, by - 8, 6, 8, Color.rgb(110, 114, 130));
        }
    }

        /**
         * The flex room: racks of glowing RAM sticks and a cartoon guy lounging on a giant stick of RAM, propped on one
         * elbow with a smirk, now and then flexing an arm. Just a made-up character in a famous kind of pose.
         */
    static final class Flex extends Room {
        Flex(Game g) { super(g); }
        String name() { return "FLEX ROOM"; }

        void draw(Canvas c, int ox, int oy, int ow, int oh, int n) {
            rectPx(c, ox, oy, ow, oh, Color.rgb(16, 14, 32));
            // racks of RAM sticks with RGB strips along the top
            for (int row = 0; row < 2; row++) {
                int sy = oy + 4 + row * 36;
                rectPx(c, ox, sy + 32, ow, 3, Color.rgb(58, 62, 84));
                for (int i = 0; i < 17; i++) {
                    int x = ox + 4 + i * 10;
                    rectPx(c, x, sy + 2, 6, 29, Color.rgb(32, 34, 48));
                    rectPx(c, x + 1, sy + 4, 4, 25, Color.rgb(50, 54, 74));
                    for (int k = 0; k < 4; k++) rectPx(c, x + 2, sy + 7 + k * 6, 2, 3, Color.rgb(20, 22, 30));
                    rectPx(c, x + 1, sy, 4, 3, hue(g.clock * 80 + i * 22 + row * 70));
                    rectPx(c, x + 1, sy + 29, 4, 2, Color.rgb(228, 188, 70));
                }
            }
            rectPx(c, ox, oy + oh - 6, ow, 6, Color.rgb(34, 30, 52));
            // the giant RAM stick he lounges on
            int bw = 64, bx = ox + (ow - bw) / 2, by = oy + oh - 26;
            rectPx(c, bx, by, bw, 18, Color.rgb(10, 12, 22));
            rectPx(c, bx + 1, by + 1, bw - 2, 14, Color.rgb(52, 58, 82));
            for (int i = 0; i < 3; i++) rectPx(c, bx + 6 + i * 20, by + 4, 12, 7, Color.rgb(20, 22, 32));
            rectPx(c, bx + 1, by + 1, bw - 2, 2, hue(g.clock * 80));
            for (int x = bx + 3; x < bx + bw - 4; x += 4) rectPx(c, x, by + 15, 3, 3, Color.rgb(228, 188, 70));
            // the guy, a pixel-art sprite lying on the stick; the bare chest and the jeans have their own colours
            int top = by - 13, gx = bx + 12;
            drawPix(c, FLEX_GUY, gx, top, FLEX_KEYS, FLEX_COLS);
            boolean flex = ((int) (g.clock * 0.5f + 0.3f) & 1) == 1 && (g.clock * 0.5f + 0.3f) % 1f < 0.5f;
            if (flex) {
                int sk = Color.rgb(232, 190, 152), sh = Color.rgb(196, 146, 110);
                rectPx(c, gx + 16, top + 1, 3, 9, sk);                           // the free arm flexed up
                rectPx(c, gx + 13, top + 3, 4, 3, sk);                           // bicep
                rectPx(c, gx + 15, top - 2, 4, 3, sk);                           // fist
                rectPx(c, gx + 16, top + 1, 1, 9, sh);
                text(c, "FLEX!", ox + ow / 2f, top - 10, 1, Color.rgb(255, 215, 110), 1);
            }
            // a soft glow off the sticks
            rectPx(c, ox, oy, ow, oh, Color.argb(16, 160, 90, 255));
        }
    }

        /** the gym: a punching bag, a barbell of iron plates, a mirror and a scientist on a treadmill */
    static final class Gym extends Room {
        Gym(Game g) { super(g); }
        String name() { return "GYM"; }

        void draw(Canvas c, int ox, int oy, int ow, int oh, int n) {
            int fl = oy + oh - 14;
            rectPx(c, ox, oy, ow, oh, Color.rgb(46, 62, 84));
            rectPx(c, ox, fl, ow, 14, Color.rgb(36, 40, 52));
            rectPx(c, ox, fl, ow, 1, Color.rgb(90, 96, 120));
            textOut(c, "NO PAIN NO GAIN", ox + ow / 2f, oy + 3, 1, Color.rgb(255, 215, 110), 1, false);
            // mirror
            rectPx(c, ox + 36, oy + 14, 54, 40, Color.rgb(24, 28, 40));
            rectPx(c, ox + 37, oy + 15, 52, 38, Color.rgb(150, 178, 204));
            for (int i = 0; i < 4; i++) rectPx(c, ox + 44 + i * 9 - (int) (g.clock * 6 % 9), oy + 15, 2, 38, Color.argb(50, 255, 255, 255));
            // the punching bag swings on a chain
            c.save();
            c.rotate((float) Math.sin(g.clock * 2.2f) * 14f, ox + 16, oy + 8);
            rectPx(c, ox + 16, oy + 8, 1, 14, Color.rgb(150, 150, 164));
            rectPx(c, ox + 10, oy + 22, 13, 30, Color.rgb(180, 50, 50));
            rectPx(c, ox + 10, oy + 22, 13, 2, Color.rgb(110, 30, 34));
            rectPx(c, ox + 10, oy + 50, 13, 2, Color.rgb(110, 30, 34));
            c.restore();
            // a barbell with iron plates (FE)
            int by = fl - 11;
            rectPx(c, ox + 34, by + 4, 62, 2, Color.rgb(190, 196, 210));
            for (int k = 0; k < 2; k++) {
                int px0 = ox + 34 + k * 49;
                rectPx(c, px0, by - 2, 11, 17, Color.rgb(20, 22, 30));
                rectPx(c, px0 + 1, by - 1, 9, 15, Color.rgb(96, 102, 124));
                textOut(c, "FE", px0 + 5, by + 3, 1, Color.rgb(230, 235, 245), 1, false);
            }
            // a treadmill with a scientist running on it
            int tx = ox + 106;
            rectPx(c, tx, fl - 7, 52, 7, Color.rgb(20, 22, 30));
            rectPx(c, tx + 2, fl - 6, 48, 5, Color.rgb(60, 66, 84));
            for (int x = tx + 4 - (int) (g.clock * 30 % 8); x < tx + 48; x += 8) if (x > tx + 2) rectPx(c, x, fl - 4, 3, 1, Color.rgb(120, 126, 150));
            rectPx(c, tx + 44, fl - 36, 3, 30, Color.rgb(90, 96, 116));
            rectPx(c, tx + 36, fl - 40, 14, 7, Color.rgb(20, 22, 30));
            rectPx(c, tx + 37, fl - 39, 12, 5, hue(g.clock * 60));
            miniScientist(c, tx + 24, fl - 7, new int[] {0, 2, 0, -2}[((int) (g.clock * 10)) & 3], Color.rgb(235, 240, 248), true);
            if (((int) (g.clock * 6) & 1) == 0) rectPx(c, tx + 28, fl - 24, 1, 2, Color.rgb(150, 220, 255));   // sweat
            if (((int) (g.clock * 6) & 1) == 1) rectPx(c, tx + 18, fl - 27, 1, 2, Color.rgb(150, 220, 255));
        }
    }

        /** the break room: a sleeping scientist, a steaming coffee machine, donuts and a very honest sign */
    static final class Break extends Room {
        Break(Game g) { super(g); }
        String name() { return "BREAK ROOM"; }

        void draw(Canvas c, int ox, int oy, int ow, int oh, int n) {
            int fl = oy + oh - 12;
            rectPx(c, ox, oy, ow, oh, Color.rgb(176, 156, 118));
            rectPx(c, ox, fl, ow, 12, Color.rgb(98, 80, 68));
            rectPx(c, ox + 18, oy + 8, 134, 14, Color.rgb(20, 60, 44));
            rectPx(c, ox + 19, oy + 9, 132, 12, Color.rgb(32, 90, 64));
            textOut(c, "DAYS SINCE LAST EXPLOSION: 0", ox + ow / 2f, oy + 12, 1, Color.WHITE, 1, false);
            // the couch with someone asleep on it, a small scientist like the ones in mission control
            rectPx(c, ox + 8, fl - 22, 48, 22, Color.rgb(120, 40, 44));
            rectPx(c, ox + 8, fl - 22, 48, 8, Color.rgb(150, 56, 60));
            rectPx(c, ox + 11, fl - 9, 42, 5, Color.rgb(98, 30, 36));
            rectPx(c, ox + 14, fl - 15, 8, 7, Color.rgb(240, 200, 160));               // head on the armrest
            rectPx(c, ox + 14, fl - 16, 8, 3, Color.rgb(90, 60, 40));
            rectPx(c, ox + 15, fl - 12, 2, 1, Color.rgb(40, 30, 30));                  // closed eyes
            rectPx(c, ox + 19, fl - 12, 2, 1, Color.rgb(40, 30, 30));
            rectPx(c, ox + 22, fl - 14, 20, 6, Color.rgb(235, 240, 248));              // coat
            rectPx(c, ox + 42, fl - 14, 9, 6, Color.rgb(50, 60, 100));
            for (int i = 0; i < 3; i++) {
                float t = (g.clock * 0.7f + i * 0.33f) % 1f;
                textOut(c, "Z", ox + 20 + i * 4 + t * 6, fl - 22 - t * 16, 1, Color.argb((int) (255 * (1 - t)), 255, 255, 255), 1, false);
            }
            // a table with donuts
            rectPx(c, ox + 78, fl - 20, 34, 3, Color.rgb(110, 76, 50));
            rectPx(c, ox + 80, fl - 17, 3, 17, Color.rgb(90, 60, 40));
            rectPx(c, ox + 107, fl - 17, 3, 17, Color.rgb(90, 60, 40));
            for (int i = 0; i < 3; i++) {
                rectPx(c, ox + 83 + i * 9, fl - 26, 7, 6, Color.rgb(200, 140, 80));
                rectPx(c, ox + 83 + i * 9, fl - 26, 7, 3, i == 1 ? Color.rgb(250, 240, 240) : Color.rgb(240, 120, 170));
                rectPx(c, ox + 85 + i * 9, fl - 24, 3, 2, Color.rgb(176, 156, 118));
            }
            // a big coffee machine with steam
            int mx = ox + 124;
            rectPx(c, mx, fl - 44, 28, 44, Color.rgb(46, 50, 64));
            rectPx(c, mx + 2, fl - 42, 24, 12, Color.rgb(20, 24, 30));
            textOut(c, "COFFEE", mx + 14, fl - 40, 1, Color.rgb(255, 190, 90), 1, false);
            rectPx(c, mx + 20, fl - 27, 4, 4, ((int) (g.clock * 3) & 1) == 0 ? Color.rgb(255, 70, 60) : Color.rgb(100, 40, 40));
            rectPx(c, mx + 8, fl - 20, 12, 12, Color.rgb(230, 230, 240));               // a mug
            rectPx(c, mx + 10, fl - 18, 8, 3, Color.rgb(90, 50, 30));
            for (int i = 0; i < 3; i++) {
                float t = (g.clock * 0.9f + i * 0.33f) % 1f;
                rectPx(c, mx + 10 + i * 3 + (float) Math.sin(g.clock * 3 + i) * 1.5f, fl - 22 - t * 14, 2, 2, Color.argb((int) (150 * (1 - t)), 255, 255, 255));
            }
        }
    }

        /** the cat box: a cardboard box with a cat that is alive and not alive until you look */
    static final class CatBox extends Room {
        CatBox(Game g) { super(g); }
        String name() { return "THE CAT BOX"; }

        void draw(Canvas c, int ox, int oy, int ow, int oh, int n) {
            int fl = oy + oh - 12;
            rectPx(c, ox, oy, ow, oh, Color.rgb(22, 50, 58));
            rectPx(c, ox, fl, ow, 12, Color.rgb(34, 40, 50));
            // a table
            rectPx(c, ox + 22, fl - 28, 126, 4, Color.rgb(100, 76, 54));
            rectPx(c, ox + 26, fl - 24, 4, 24, Color.rgb(80, 60, 44));
            rectPx(c, ox + 140, fl - 24, 4, 24, Color.rgb(80, 60, 44));
            // the box
            int bx = ox + 62, by = fl - 28 - 32;
            rectPx(c, bx, by, 48, 32, Color.rgb(60, 40, 24));
            rectPx(c, bx + 1, by + 1, 46, 30, Color.rgb(196, 152, 100));
            rectPx(c, bx + 21, by + 1, 6, 30, Color.rgb(176, 134, 86));
            textOut(c, "DO NOT OPEN", bx + 24, by + 18, 1, Color.rgb(90, 40, 30), 1, false);
            // the cat on top: alive, then both, then not alive, then both again
            float t = g.clock % 2.8f;
            boolean alive = t < 1f, dead = t >= 1.4f && t < 2.4f;
            int cx = bx + 24, cy = by;
            if (alive || !dead) drawCat(c, cx, cy, true, alive ? 255 : 110);
            if (dead || (!alive && !dead)) drawCat(c, cx, cy, false, dead ? 255 : 110);
            textOut(c, alive ? "ALIVE" : dead ? "DEAD" : "BOTH", cx, oy + 8, 1, alive ? Color.rgb(120, 255, 160) : dead ? Color.rgb(255, 110, 100) : Color.rgb(255, 215, 110), 1, false);
            // a geiger counter that clicks and a flask
            rectPx(c, ox + 26, fl - 44, 22, 16, Color.rgb(50, 56, 70));
            rectPx(c, ox + 28, fl - 42, 18, 8, Color.rgb(200, 210, 190));
            rectPx(c, ox + 37 + (float) Math.sin(g.clock * 17) * 4, fl - 41, 1, 6, Color.rgb(200, 40, 40));
            rectPx(c, ox + 33, fl - 31, 8, 2, Color.rgb(255, 190, 60));
            rectPx(c, ox + 122, fl - 44, 12, 16, Color.rgb(200, 235, 220));
            rectPx(c, ox + 123, fl - 38, 10, 9, Color.rgb(90, 230, 120));
            rectPx(c, ox + 125, fl - 47, 6, 3, Color.rgb(200, 235, 220));
        }
    }

    static final class Wheel extends Room {
        Wheel(Game g) { super(g); }
        String name() { return "POWER ROOM"; }

        void draw(Canvas c, int ox, int oy, int ow, int oh, int n) {
            int fl = oy + oh - 12;
            rectPx(c, ox, oy, ow, oh, Color.rgb(34, 30, 46));
            rectPx(c, ox, fl, ow, 12, Color.rgb(54, 46, 60));
            int sp = (int) (g.clock * 14);
            float glow = 0.55f + 0.45f * (float) Math.sin(g.clock * 6);
            for (int i = 0; i < 4; i++) {                                         // bulbs that flicker with the pedalling
                int bx = ox + 18 + i * 42;
                rectPx(c, bx, oy, 1, 8, Color.rgb(120, 120, 140));
                rectPx(c, bx - 3, oy + 8, 7, 8, Color.argb(255, (int) (255 * glow), (int) (230 * glow), (int) (120 * glow)));
                rectPx(c, bx - 6, oy + 5, 13, 14, Color.argb((int) (40 * glow), 255, 230, 140));
            }
            textOut(c, "POWER: 1 SCIENTIST", ox + ow / 2f, oy + 24, 1, Color.rgb(255, 215, 110), 1, false);
            int cx = ox + 85, cy = fl - 34;
            g.p.setStyle(Paint.Style.STROKE);
            g.p.setStrokeWidth(3);
            g.p.setColor(Color.rgb(170, 176, 196));
            c.drawCircle(cx, cy, 30, g.p);
            g.p.setStrokeWidth(1);
            g.p.setColor(Color.rgb(110, 116, 140));
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + g.clock * 3;
                c.drawLine(cx, cy, cx + (float) Math.cos(a) * 30, cy + (float) Math.sin(a) * 30, g.p);
            }
            g.p.setStyle(Paint.Style.FILL);
            rectPx(c, cx - 4, cy - 4, 8, 8, Color.rgb(90, 96, 120));
            rectPx(c, cx - 22, fl - 3, 8, 3, Color.rgb(90, 96, 120));          // the stand
            rectPx(c, cx + 14, fl - 3, 8, 3, Color.rgb(90, 96, 120));
            miniScientist(c, cx, cy + 28, new int[] {0, 2, 0, -2}[sp & 3], Color.rgb(235, 240, 248), true);
        }
    }

        /** the power room: a scientist running in a hamster wheel to keep the lights on */
        /** ERROR: nothing in here but a busted, cracked door, sparks and a glitch now and then */
    static final class Broken extends Room {
        Broken(Game g) { super(g); }
        String name() { return "ERROR"; }
        boolean alarm() { return true; }

        void draw(Canvas c, int ox, int oy, int ow, int oh, int n) {
            int fl = oy + oh - 12;
            rectPx(c, ox, oy, ow, oh, Color.rgb(20, 22, 30));
            rectPx(c, ox, fl, ow, 12, Color.rgb(36, 36, 44));
            rectPx(c, ox, fl, ow, 1, Color.rgb(70, 70, 82));
            int fx = ox + 62, ftop = oy + 10, fw = 46;
            // light spilling through the gap behind the door
            rectPx(c, fx, ftop, fw, fl - ftop, Color.rgb(226, 214, 150));
            rectPx(c, fx + 4, ftop + 4, fw - 8, fl - ftop - 4, Color.argb(120, 255, 250, 210));
            // the frame
            rectPx(c, fx - 4, ftop - 4, fw + 8, 4, Color.rgb(60, 64, 80));
            rectPx(c, fx - 4, ftop, 4, fl - ftop, Color.rgb(60, 64, 80));
            rectPx(c, fx + fw, ftop, 4, fl - ftop, Color.rgb(60, 64, 80));
            // the door, hanging off its lower hinge and tilted out
            c.save();
            c.rotate(7f, fx + 2, fl);
            int dw = 40, dh = fl - ftop - 2;
            rectPx(c, fx + 2, fl - dh, dw, dh, Color.rgb(90, 98, 96));
            rectPx(c, fx + 4, fl - dh + 3, dw - 4, 26, Color.rgb(76, 84, 82));
            rectPx(c, fx + 4, fl - dh + 33, dw - 4, dh - 38, Color.rgb(76, 84, 82));
            rectPx(c, fx + 2, fl - dh, 1, dh, Color.rgb(130, 138, 134));
            // a dent and a missing corner that the light shows through
            rectPx(c, fx + dw - 12, fl - dh, 14, 12, Color.rgb(226, 214, 150));
            rectPx(c, fx + dw - 12, fl - dh + 10, 8, 3, Color.rgb(226, 214, 150));
            rectPx(c, fx + dw - 12, fl - dh + 12, 3, 4, Color.rgb(226, 214, 150));
            rectPx(c, fx + 10, fl - dh + 40, 12, 8, Color.rgb(66, 72, 70));
            // cracks across the leaf
            int[][] starts = {{fx + 12, fl - dh + 4}, {fx + 26, fl - dh + 20}, {fx + 8, fl - dh + 52}};
            Random r = new Random(5);
            for (int[] st : starts) {
                int cx = st[0], cy = st[1];
                for (int k = 0; k < 24; k++) {
                    rectPx(c, cx, cy, 1, 1, Color.rgb(20, 24, 26));
                    cy++; cx += r.nextInt(3) - 1;
                    if (k % 6 == 5) { rectPx(c, cx + 1, cy, 1, 1, Color.rgb(20, 24, 26)); rectPx(c, cx + 2, cy + 1, 1, 1, Color.rgb(20, 24, 26)); }
                }
            }
            // a dead keypad with a red X
            rectPx(c, fx + 28, fl - dh + 34, 8, 12, Color.rgb(30, 32, 40));
            rectPx(c, fx + 29, fl - dh + 35, 6, 4, Color.rgb(60, 20, 20));
            rectPx(c, fx + 30, fl - dh + 36, 1, 1, Color.rgb(255, 70, 60));
            rectPx(c, fx + 33, fl - dh + 36, 1, 1, Color.rgb(255, 70, 60));
            rectPx(c, fx + 31, fl - dh + 37, 2, 1, Color.rgb(255, 70, 60));
            c.restore();
            // loose wires from above, swaying, with sparks at the top hinge
            for (int k = 0; k < 3; k++) {
                float sx = (float) Math.sin(g.clock * 2 + k) * 2;
                int wx = fx + 8 + k * 14;
                for (int j = 0; j < 14; j++) rectPx(c, wx + sx * j / 14f, ftop - 3 + j, 1, 1, k == 1 ? Color.rgb(220, 60, 60) : Color.rgb(90, 140, 220));
                if (((int) (g.clock * 9) + k) % 5 == 0) {
                    rectPx(c, wx + sx, ftop + 11, 2, 2, Color.rgb(255, 235, 140));
                    rectPx(c, wx + sx - 2 + (int) (g.clock * 40) % 5, ftop + 12 + (int) (g.clock * 30) % 4, 1, 1, Color.WHITE);
                }
            }
            // a warning lamp over the door, and rubble on the floor
            rectPx(c, fx + fw / 2 - 3, ftop - 10, 6, 5, ((int) (g.clock * 3) & 1) == 0 ? Color.rgb(255, 70, 60) : Color.rgb(90, 30, 30));
            for (int k = 0; k < 8; k++) rectPx(c, fx - 14 + k * 9 + (k * 5) % 4, fl - 2 - (k % 3), 3 + k % 3, 2 + k % 3, Color.rgb(70 + k * 4, 72 + k * 4, 80));
            // a glitch every so often: shifted coloured slices
            float gt = g.clock % 2.2f;
            if (gt < 0.14f) {
                for (int k = 0; k < 4; k++) {
                    int gy = oy + 6 + (int) ((k * 29 + g.clock * 97) % (oh - 12));
                    rectPx(c, ox + (k * 13) % 7, gy, ow - 8, 3, k % 2 == 0 ? Color.argb(70, 0, 255, 255) : Color.argb(70, 255, 0, 200));
                }
            }
        }
    }
}
