package com.example.jetlab;

import android.graphics.Color;

/** The optics behind the prism on every scratch card: real refraction through BK7 glass, worked out once at load. */
final class Prism {
    private Prism() { }

    // An equilateral glass prism (apex 60 degrees). A white beam hits the left face at 50 degrees to
    // the normal. Each colour is bent by Snell's law, n1 sin(a1) = n2 sin(a2), at both faces, using a
    // Cauchy fit for BK7 glass, n = 1.5046 + 0.0042 / L^2 (L in micrometres). Violet is slowed most,
    // so it bends most. Real glass spreads red to violet by only about 1.6 degrees, so the spread is
    // drawn 6 times wider to make the rainbow visible; every angle is otherwise exact.
    static final int NW = 26;
    static final float DISP = 6f;

    static final float PCX = 84f, PCY = 52f, PHALF = 40f;      // prism centre and half base, in a 200 x 104 card
    static final float[] LAM = new float[NW];
    static final int[] LCOL = new int[NW];
    static final float[] E1 = new float[2], D0 = new float[2];
    static final float[] E2X = new float[NW], E2Y = new float[NW], D2X = new float[NW], D2Y = new float[NW];
    static final float[] P0 = new float[2], P1 = new float[2], P2 = new float[2];

    static {
        float hgt = PHALF * (float) Math.sqrt(3);
        P0[0] = PCX; P0[1] = PCY - hgt / 2; P1[0] = PCX - PHALF; P1[1] = PCY + hgt / 2; P2[0] = PCX + PHALF; P2[1] = PCY + hgt / 2;
        double phi = Math.toRadians(20);                        // the beam travels up and to the right at 20 degrees
        D0[0] = (float) Math.cos(phi); D0[1] = (float) -Math.sin(phi);
        E1[0] = (P1[0] + P0[0]) / 2; E1[1] = (P1[1] + P0[1]) / 2;  // it hits the middle of the left face
        double nl0 = -0.8660254, nl1 = -0.5;                    // outward normal of the left face (y points down)
        double nr0 = 0.8660254, nr1 = -0.5;                     // outward normal of the right face
        double nMean = 1.5046 + 0.0042 / (0.55 * 0.55);
        for (int i = 0; i < NW; i++) {
            double lam = 400 + i * 300.0 / (NW - 1);
            LAM[i] = (float) lam;
            LCOL[i] = wavelengthColor(lam);
            double um = lam / 1000.0;
            double n = nMean + DISP * (1.5046 + 0.0042 / (um * um) - nMean);
            double[] d1 = refract(D0[0], D0[1], nl0, nl1, 1.0 / n);
            double[] hit = d1 == null ? null : hitFace(E1[0], E1[1], d1[0], d1[1], P0, P2);
            double[] d2 = (d1 == null || hit == null) ? null : refract(d1[0], d1[1], -nr0, -nr1, n);
            if (d2 == null) { E2X[i] = Float.NaN; continue; }
            E2X[i] = (float) hit[0]; E2Y[i] = (float) hit[1];
            D2X[i] = (float) d2[0]; D2Y[i] = (float) d2[1];
        }
    }

    /** Snell refraction. d is the unit direction, nrm the unit normal facing against d, eta = n1 / n2. */
    static double[] refract(double dx, double dy, double nx, double ny, double eta) {
        double cos1 = -(dx * nx + dy * ny);
        double k = 1 - eta * eta * (1 - cos1 * cos1);
        if (k < 0) return null;                                 // total internal reflection
        double f = eta * cos1 - Math.sqrt(k);
        return new double[] {eta * dx + f * nx, eta * dy + f * ny};
    }

    /** where the ray from (px, py) along (dx, dy) crosses the segment a-b, or null */
    static double[] hitFace(double px, double py, double dx, double dy, float[] a, float[] b) {
        double ex = b[0] - a[0], ey = b[1] - a[1];
        double den = dx * ey - dy * ex;
        if (Math.abs(den) < 1e-9) return null;
        double t = ((a[0] - px) * ey - (a[1] - py) * ex) / den;
        double u = ((a[0] - px) * dy - (a[1] - py) * dx) / den;
        if (t <= 0 || u < 0 || u > 1) return null;
        return new double[] {px + dx * t, py + dy * t};
    }

    static int wavelengthColor(double w) {
        double r, g, b;
        if (w < 440) { r = -(w - 440) / 60; g = 0; b = 1; }
        else if (w < 490) { r = 0; g = (w - 440) / 50; b = 1; }
        else if (w < 510) { r = 0; g = 1; b = -(w - 510) / 20; }
        else if (w < 580) { r = (w - 510) / 70; g = 1; b = 0; }
        else if (w < 645) { r = 1; g = -(w - 645) / 65; b = 0; }
        else { r = 1; g = 0; b = 0; }
        double f = w < 420 ? 0.3 + 0.7 * (w - 380) / 40 : w > 700 ? 0.3 + 0.7 * (750 - w) / 50 : 1;
        return Color.rgb((int) (255 * Math.pow(Math.max(0, Math.min(1, r * f)), 0.8)),
                (int) (255 * Math.pow(Math.max(0, Math.min(1, g * f)), 0.8)),
                (int) (255 * Math.pow(Math.max(0, Math.min(1, b * f)), 0.8)));
    }
}
