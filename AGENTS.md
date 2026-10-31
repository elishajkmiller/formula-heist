# Thrust Lab

Jetpack Joyride style Android game with a physics theme. One file, src/com/example/jetlab/Main.java: physics, drawing and touch in a single View.

- Build with ./build.sh (no Android SDK or Gradle; fetches its own tools on first run). Output is dist/jetlab.apk.
- Hold the screen to fire the jetpack. Real units on screen: 1 m is a ninth of the screen height, gravity 9.8 m/s^2.
- Compiles with ecj -source 8 because the JDK here has no ct.sym for --release.

## How I want things written
- No em-dashes. Short, plain replies; no headers or bullets unless it really is a list.
- No real names or addresses in code, tests, comments or commits. Invent stand-ins.
- No Co-Authored-By lines. Never run sudo.
