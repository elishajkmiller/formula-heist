package com.example.formulaheist;

import android.graphics.Canvas;

import static com.example.formulaheist.Game.*;

/**
 * A vehicle from a blue crate. Game keeps the shared physics (speed limits, the floor and the ceiling);
 * each vehicle changes how it feels through these hooks. To add one, write a subclass and list it in KINDS.
 */
abstract class Vehicle {
    interface Maker { Vehicle make(Game g); }

    /** every vehicle a crate can hold; a crate's type is an index into this */
    static final Maker[] KINDS = {Stomper::new, Inverter::new, Quantum::new, Helix::new, Bird::new, Screw::new};

    final Game g;

    Vehicle(Game g) { this.g = g; }

    abstract String name();
    abstract String hint();
    /** the body as it is assembled from flying blocks */
    abstract String[] rows();
    abstract void draw(Canvas c);

    /** pixels from the centre to the top and the bottom edge */
    int hTop() { return 8; }
    int hBot() { return hTop(); }
    /** hit radius in pixels */
    float radius() { return 7f; }
    /** row of the head inside the body while it is built, or -1 when no head shows */
    int cockpit() { return 3; }
    /** sticks to a surface and switches which one gravity points at */
    boolean flips() { return false; }
    /** the legs keep stepping, so the run cycle advances */
    boolean walks() { return false; }
    /** bounces off the floor instead of landing on it */
    boolean bounces() { return false; }

    /** once, when it is finished building */
    void start() { }
    /** a tap */
    void press() { }
    /** timers, at the start of each frame */
    void tick(float dt) { }
    /** vertical acceleration in m/s^2, down positive */
    float accel(float dt) { return GRAVITY * g.gravDir; }
    /** after the frame's movement */
    void update(float dt) { }
    /** hit the ceiling going up at speed vy (negative) */
    void bumpCeiling(float vy) { }
    /** reached the floor at speed impact; only for vehicles that bounce */
    void bounce(float impact) { }
}
