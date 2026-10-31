package com.example.formulaheist;

// the small plain objects that move through the world

final class Coin { float x, y; boolean taken; }

/**
 * A background scientist. mode: 0 stands alone, 1 walks left, 2 walks right, 3 stands in a group,
 * 4 is one of the NULLCORP guards who stand behind you at the start and chase you at the wall.
 */
final class Npc {
    float x, phase, pointT, nextPoint, walk;
    int coat, hair, mode, face;
    // guards only: running, then tumbling when you smash through the wall
    boolean running, falling, settled;
    float fy, fvy, ang, angV, vw;      // height above the floor and its speed (pixels), spin, speed along the ground
    int bounces;
}

/** a stolen formula, rolled up */
final class Scroll { float x, y; }

/** a base pair shot forward by the DNA helix */
final class Bolt { float x, y; int kind; }

final class Crate { float x, y; int type; }

final class Zapper {
    float x, y, len, angle, spin;
    /** half the bar's extent along each axis */
    float hx() { return (float) Math.cos(angle) * len / 2; }
    float hy() { return (float) Math.sin(angle) * len / 2; }
}

final class Missile {
    float x, y, warn;
    boolean flying;
}
