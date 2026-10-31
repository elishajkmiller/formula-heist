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

## Story and launch
NULLCORP, an evil science company, stole every theory and formula; the mission is to steal them back and share them with the world. A run starts outside on the grass in front of NULLCORP's brick wall. Stretch the coil-spring catapult (drag back, release): stretch sets speed and angle, shown with energy (1/2 m v^2). He flies in a real arc (game gravity) and smashes through the wall, then the run begins inside the lab (rooms: mission control, chemistry lab, computer room, observatory). Too weak and he tumbles on the grass and you try again. States: START, LAUNCH, FLY, MISS, PLAY, BUILD, DEAD, ARCHIVE, SCRATCH. The scenery holds still until he moves.

## Formulas (scratch-off archive)
20 real physics formulas (FORM/FNAME/FDESC arrays in Main.java) are rolled up as parchment scrolls that appear every 140 to 260 m. Collecting one saves it (SharedPreferences "owned"); in the ARCHIVE (button bottom right on the title, aiming and death screens) tap a silver card and scratch it off with a finger; 60% scratched reveals it ("scratched" is saved). The 5x3 pixel font only has A-Z, 0-9 and . , ! - + / : ' ^ = ? so formulas must stay in those characters.

## Text and the prism
UI text is a small 3x5 pixel font (CH/GL in Main.java: A-Z, 0-9 and . , ! - + / : ' ^ = ?), drawn with text()/textOut()/wrapText(); y is the top of the text, 5*sc tall (a 5x7 version overlapped everything, do not go bigger), with a 1 pixel dark outline instead of a drop shadow. Centred text that is too wide splits onto two lines. Formulas use a serif italic with real symbols (FORMD) drawn at screen resolution. The scratch cards show a glass prism: a white beam is refracted by Snell's law with a Cauchy fit for BK7 glass, 60 degree apex, 50 degree incidence; the red to violet spread is drawn 6x wider than real glass so the rainbow is visible (preview/prism-card.png). The break-in is part of PLAY (breaking = true): you can jump and use the jetpack on the way to the wall, and the hole opens at the height you hit it.
