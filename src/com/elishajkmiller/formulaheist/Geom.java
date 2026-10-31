package com.elishajkmiller.formulaheist;

/** Small collision tests used by every hazard. */
final class Geom {
    private Geom() { }

    static boolean circleHitsRect(float cx, float cy, float r, float rx, float ry, float rw, float rh) {
        float nx = Math.max(rx, Math.min(cx, rx + rw)), ny = Math.max(ry, Math.min(cy, ry + rh));
        float dx = cx - nx, dy = cy - ny;
        return dx * dx + dy * dy <= r * r;
    }

    static boolean circleHitsSquare(float cx, float cy, float r, float sx, float sy, float half) {
        float nx = Math.max(sx - half, Math.min(cx, sx + half)), ny = Math.max(sy - half, Math.min(cy, sy + half));
        float dx = cx - nx, dy = cy - ny;
        return dx * dx + dy * dy <= r * r;
    }

    static float segDist(float cx, float cy, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float t = ((cx - ax) * dx + (cy - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0f, Math.min(1f, t));
        float qx = ax + t * dx - cx, qy = ay + t * dy - cy;
        return (float) Math.sqrt(qx * qx + qy * qy);
    }
}
