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
Blueprint crates ("B") appear every 220 to 370 m. Picking one up freezes time to 15% while the vehicle assembles from flying 3x3 pixel blocks (BUILD state), then you ride it: Spring Stomper (bounces on its own, tap in the air to dive), Gravity Inverter (tap flips gravity) Quantum Entangler (run on the floor or ceiling, tap teleports to the mirror spot on the other side where an entangled twin waits; five uses then it burns out). A hit breaks the vehicle instead of ending the run. Feel: screen shake, hit-stop, white flash, vibration (needs VIBRATE) and synthesized sounds (Sfx class, no audio files).

## Story and launch
NULLCORP, an evil science company, stole every theory and formula; the mission is to steal them back and share them with the world. A run starts outside on the grass in front of NULLCORP's brick wall. Stretch the coil-spring catapult (drag back, release): stretch sets speed and angle, shown with energy (1/2 m v^2). He flies in a real arc (game gravity) and smashes through the wall, then the run begins inside the lab (rooms: mission control, chemistry lab, computer room, observatory). Too weak and he tumbles on the grass and you try again. States: START, LAUNCH, FLY, MISS, PLAY, BUILD, DEAD, ARCHIVE, SCRATCH. The scenery holds still until he moves.

## Formulas (scratch tickets)
20 real physics formulas (FORM-free: FORMD/FNAME/FDESC arrays in Main.java). A parchment scroll on the track (every 140 to 260 m) is a scratch ticket (prefs "tickets"). In the ARCHIVE (button bottom right on the title and death screens, blinking "SCRATCH A TICKET" when you have one) tap "SCRATCH A TICKET": the formula under it is rolled when you open it (pending, saved, so closing the screen cannot re-roll) with formulas you do not own three times as likely, so it can be one you already have. A new one is added to your collection (prefs "formulas"); a duplicate pays DUPLICATE_PAY electrons. Scratching 60% of the prism foil reveals the card. Owned cards can be tapped in the grid to read again. The 3x5 pixel font only has A-Z, 0-9 and . , ! - + / : ' ^ = ? so strings must stay in those characters.

## Text and the prism
UI text is a small 3x5 pixel font (CH/GL in Main.java: A-Z, 0-9 and . , ! - + / : ' ^ = ?), drawn with text()/textOut()/wrapText(); y is the top of the text, 5*sc tall (a 5x7 version overlapped everything, do not go bigger), with a 1 pixel dark outline instead of a drop shadow. Centred text that is too wide splits onto two lines. Formulas use a serif italic with real symbols (FORMD) drawn at screen resolution. The scratch cards show a glass prism: a white beam is refracted by Snell's law with a Cauchy fit for BK7 glass, 60 degree apex, 50 degree incidence; the red to violet spread is drawn 6x wider than real glass so the rainbow is visible (preview/prism-card.png). The break-in is part of PLAY (breaking = true): you can jump and use the jetpack on the way to the wall, and the hole opens at the height you hit it.

## Code map (src/com/example/jetlab/Main.java)
Main (Activity, fullscreen, pause/resume) and Game (the View). Game is organised in labelled sections: constants and text data, game state (grouped by drawing, world, particles, run, break-in, vehicles, death, archive, sound), touch input, archive and prism helpers, physics (update loop, death ragdoll), vehicles, spawning, drawing, the yard and wall, rooms, player and vehicle sprites, HUD and screens, and the pixel font. Sfx synthesizes the sounds on a background thread. Build with ./build.sh; nothing outside Main.java and tools/ is needed.

## Clothes store
Electrons you collect are banked across runs (prefs "bank"). The STORE button (bottom left on the title and death screens) opens a store with three tabs: SHIRTS, PANTS and JETPACKS, each with 8 items bought and worn on their own (INAME/ICOST/IPAL in Main.java). An item recolours sprite letters through the OVR table that pal() checks first: shirts W w x (coat), t (tie), V (collar); pants D d; jetpacks O o h. The last jetpack (RAINBOW) cycles hue every frame. Bought and worn items are saved (inv0-2 bit masks, worn0-2). Earlier whole outfits were migrated into shirts.

## DNA Helix vehicle
A fourth crate vehicle (HELIX): hold to rise (light gravity 14, thrust 30), release to sink, tap to fire base pairs (A-T and C-G rungs) that destroy a zapper or missile they touch (0.3 s cooldown). Its body is a double helix that trails the head by following a 64 sample history of the head's height (yHist), twisting as it goes.

## Settings
The SETTINGS button (bottom centre on the title and death screens) opens: haptics, sounds, music (a looping chiptune synthesized in Sfx.song(), no audio file), and RESET PROGRESS behind an "ARE YOU SURE?" pop-up (erases formulas, tickets, clothes, electrons and best distance; settings stay). Choices are saved in prefs ("haptics", "sound", "music").

## Characters
Settings has a CHARACTER row: MALE or FEMALE. Both are cartoon characters (HEAD_M/HEAD_F and their open-mouth variants in Main.java): the male has tousled brown hair, browline glasses and a moustache; the female has long wavy copper hair falling over her shoulders, lashes, freckles and blush, a gold necklace and a rounded chest (femaleBody). applyCharacter rewrites the shared SPR_RUN arrays, the cockpit head and the Quantum figure in place; pal() recolours the hair letters B and b copper for the female through OVR. The choice is saved in prefs ("female") and survives a progress reset.
