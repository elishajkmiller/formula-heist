# Thrust Lab

Jetpack Joyride style Android game with a physics theme. One file, src/com/example/jetlab/Main.java: physics, drawing and touch in a single View.

- Build with ./build.sh (no Android SDK or Gradle; fetches its own tools on first run). Output is dist/jetlab.apk.
- Tap on the floor to jump, hold in the air to fire the jetpack. Real units on screen: 1 m is a ninth of the screen height, gravity 22 m/s^2 (game value, faster than Earth).
- Compiles with ecj -source 8 because the JDK here has no ct.sym for --release.

## How I want things written
- No em-dashes. Short, plain replies; no headers or bullets unless it really is a list.
- No real names or addresses in code, tests, comments or commits. Invent stand-ins.
- No Co-Authored-By lines. Never run sudo.

## Vehicles and feel
Blueprint crates ("B") appear every 220 to 370 m. Picking one up freezes time to 15% while the vehicle assembles from flying 3x3 pixel blocks (BUILD state), then you ride it: Spring Stomper (bounces on its own, tap in the air to dive), Gravity Inverter (tap flips gravity) or Quantum Entangler (run on the floor or ceiling, tap teleports to the mirror spot on the other side where an entangled twin waits). A hit breaks the vehicle instead of ending the run. Feel: screen shake, hit-stop, white flash, vibration (needs VIBRATE) and synthesized sounds (Sfx class, no audio files).
