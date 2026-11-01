# Formula Heist

Jetpack Joyride style Android game with a physics theme. Source in src/com/elishajkmiller/formulaheist/ (see the code map below); Game.java is the View with physics, drawing and touch.

- Build with ./build.sh (no Android SDK or Gradle; fetches its own tools on first run). Output is dist/formula-heist.apk.
- Tap on the floor to jump, hold in the air to fire the jetpack. Real units on screen: 1 m is a ninth of the screen height, gravity 22 m/s^2 (game value, faster than Earth).
- Compiles with ecj -source 8 because the JDK here has no ct.sym for --release.

## How I want things written
- No em-dashes. Short, plain replies; no headers or bullets unless it really is a list.
- No real names or addresses in code, tests, comments or commits. Invent stand-ins.
- No Co-Authored-By lines. Never run sudo.

## Vehicles and feel
Blueprint crates ("B") appear every 220 to 370 m. Picking one up freezes time to 15% while the vehicle assembles from flying 3x3 pixel blocks (BUILD state), then you ride it: Bouncy LEM (a lunar module with RCS thrusters, bounces on its own, hold to fire the thrusters up like a jetpack; Stomper.java), Gravity Inverter (tap flips gravity) Quantum Entangler (run on the floor or ceiling, tap teleports to the mirror spot on the other side where an entangled twin waits; five uses then it burns out). A hit breaks the vehicle instead of ending the run. Feel: screen shake, hit-stop, white flash, vibration (needs VIBRATE) and synthesized sounds (Sfx class, no audio files). Firing the jetpack plays a looping roar (Sfx.jet, low-passed noise around 450 Hz with an 80 Hz rumble, flutter and a quiet crackle) that a small thread fades in and out; doFrame turns it on exactly while the flame is drawn.

## Story and launch
NULLCORP, an evil science company, stole every theory and formula; the mission is to steal them back and share them with the world. A run starts outside on the grass in front of NULLCORP's brick wall. Stretch the coil-spring catapult (drag back, release): stretch sets speed and angle, shown with energy (1/2 m v^2). He flies in a real arc (game gravity) and smashes through the wall, then the run begins inside the lab (rooms: mission control, chemistry lab, computer room, observatory). Too weak and he tumbles on the grass and you try again. States: START, LAUNCH, FLY, MISS, PLAY, BUILD, DEAD, ARCHIVE, SCRATCH. The scenery holds still until he moves.

## Formulas (scratch tickets)
60 real physics formulas (FORMD/FNAME/FDESC arrays in Content.java; NF is the count, ownedMask is a 64 bit mask so up to 64 fit). A parchment scroll on the track (every 140 to 260 m) is a scratch ticket (prefs "tickets"). In the ARCHIVE (button bottom right on the title and death screens, blinking "SCRATCH A TICKET" when you have one) tap "SCRATCH A TICKET": the formula under it is rolled when you open it (pending, saved, so closing the screen cannot re-roll) with formulas you do not own three times as likely, so it can be one you already have. A new one is added to your collection (prefs "formulas"); a duplicate pays DUPLICATE_PAY electrons. Scratching 60% of the prism foil reveals the card. While a finger is on the card it zooms in 8% (cardZoom, eased; onDraw redraws the card region bigger and scratchAt undoes the zoom for the finger) and a looping shimmer (Sfx.shimmer via the Loop class, shared with the jet roar) rises an octave with progress; both ease back when the finger lifts. A new formula shows "NEW!" centred at the top and spinning spotlight rays (spotlight(), 12 gold wedges) behind the card, with a synthesized trumpet fanfare (Sfx.fanfare, S_FANFARE); a repeat says "DUPLICATE! +N E-". Owned cards can be tapped in the grid to read again (shown in the best finish you own).

## Card finishes
No rarity tiers. Every formula is equally likely (x3 if you do not own it yet). Each pull rolls a finish: GOLD 1 in 40 (GOLD_ODDS), HOLO 1 in 8 (HOLO_ODDS), otherwise NORMAL. You can own all three of each formula (prefs "plain", "holo", "gold" bit masks; the open ticket's finish is "pendingFin"). Holo paper has sliding rainbow foil bands and twinkles, gold is a shining gold gradient (cardPaper()). The finishes you own are stacked behind the front card in the archive grid and the big card, peeking out up and right; tap the big card to bring the next finish forward. A new finish of a formula you had is "NEW HOLO!" and so on, with the fanfare and spotlight. A duplicate pays DUP_PAY 10 electrons x2 holo, x5 gold. The 3x5 pixel font only has A-Z, 0-9 and . , ! - + / : ' ^ = ? so strings must stay in those characters.

## F-Droid
LICENSE is GPL-3.0-or-later. fastlane/metadata/android/en-US holds the store text and icon. build.gradle and settings.gradle map the repo layout (src/, res/, root manifest) for a Gradle build; they are untested here (no Gradle or SDK on this machine). The app id is still com.elishajkmiller.formulaheist and there are no screenshots yet.

## Text and the prism
UI text is a small 3x5 pixel font (CH/GL in Game.java: A-Z, 0-9 and . , ! - + / : ' ^ = ?), drawn with text()/textOut()/wrapText(); y is the top of the text, 5*sc tall (a 5x7 version overlapped everything, do not go bigger), with a 1 pixel dark outline instead of a drop shadow. Centred text that is too wide splits onto two lines. Formulas use a serif italic with real symbols (FORMD) drawn at screen resolution. The scratch cards show a glass prism: a white beam is refracted by Snell's law with a Cauchy fit for BK7 glass, 60 degree apex, 50 degree incidence; the red to violet spread is drawn 6x wider than real glass so the rainbow is visible (preview/prism-card.png). The break-in is part of PLAY (breaking = true): you can jump and use the jetpack on the way to the wall, and the hole opens at the height you hit it.

## Code map (src/com/elishajkmiller/formulaheist/)
- Main.java: the Activity (fullscreen, pause and resume).
- Game.java: the View. State, touch input, settings, store, archive and scratch card, the update loop (physics, death ragdoll, spawning) and all drawing that is not a vehicle, hazard or room. Labelled sections in that order.
- Vehicle.java: base class for crate vehicles, with the hooks Game calls (press, tick, accel, update, bounce, draw, sizes). Vehicle.KINDS lists them; a crate's type is an index into it. One file each: Stomper, Inverter, Quantum, Helix, Bird, Screw.
- Hazards.java: Hazard base class and Spikes, Saw, Laser (hit test, draw, update, cut by helix shots). Spawned in Game.spawn().
- Room.java: Room base class and one nested subclass per room, listed in Room.all(); shared room drawing helpers live at the top.
- Content.java: fixed data: store items, death-card facts, formulas (FORMD/FNAME/FDESC, NF).
- Sprites.java: pixel art, character switching (applyCharacter) and the palette (pal, OVR).
- Prism.java: the BK7 prism optics for the scratch card. Geom.java: collision tests.
- Sfx.java: synthesized sounds and music on a background thread. Entities.java: small plain objects (Coin, Npc, Scroll, Bolt, Crate, Zapper, Missile).
Game, the vehicles and the rooms reach shared constants and art through static imports. Build with ./build.sh; it compiles every .java under src/.

## Clothes store
Electrons you collect are banked across runs (prefs "bank"). The STORE button (bottom left on the title and death screens) opens a store with three tabs: SHIRTS, PANTS and JETPACKS, each with 8 items bought and worn on their own (INAME/ICOST/IPAL in Content.java). An item recolours sprite letters through the OVR table that pal() checks first: shirts W w x (coat), t (tie), V (collar); pants D d; jetpacks O o h. Each jetpack is its own engine type with its own exhaust (Game.FLAME, drawFlame) and sound (Sfx.jet types 0-7): ROCKET, PULSE JET, STEAM, ION, DARK MATTER, FUSION, PLASMA, PHOTON (rainbow, cycles hue every frame). Picking one in the store plays a 1 s sample. Vehicles hum their own engine loop while ridden (Sfx.jet types 8-13, Vehicle.kind). Every loop is tuned to A and Sfx.musicPitch moves it to the chord root the music is on; the jump, stomp, flip and teleport one-shots are tuned to A and pitched the same way. The music has no bass line, the jetpack and vehicle tones play the roots. Bought and worn items are saved (inv0-2 bit masks, worn0-2). Earlier whole outfits were migrated into shirts.

## DNA Helix vehicle
A fourth crate vehicle (HELIX): hold to rise (light gravity 14, thrust 30), release to sink, tap to fire base pairs (A-T and C-G rungs) that destroy a zapper or missile they touch (0.3 s cooldown). It runs on DNA: the meter (dna, 1 to 0) drains 0.023 a second (0.047 while rising) and 0.017 per shot, the strand shrinks from the tail toward the head, flickers under 0.25 and then the vehicle breaks. Its body is a double helix that trails the head by following a 64 sample history of the head's height (yHist, in Helix.java), twisting as it goes.

## Bernoulli Bird vehicle
A fifth crate vehicle (BIRD): tap to flap (vy set to -7), hold to glide with spread wings (gravity x0.3). Science hook: while gliding it draws the Bernoulli picture, fast close streamlines and "P-" over the wing, slow wide ones and "P+" under it, so the wing is pushed up. Body is the pod recoloured with a beak and tail (BIRD_ROWS), wing is a fan of lines driven by flapT, nose tilts with vy. Lives in Bird.java.

## Aerial Screw vehicle
A sixth crate vehicle (SCREW), Da Vinci's helicopter idea: hold to spin the rotor up (0 to 1 in about 0.6 s, spins down in 1.4 s), thrust is rotor x 34 against gravity 22, so it hovers near 65 percent. Science hook: the blade pushes air down (streaks and an RPM readout are drawn) and Newton's third law pushes the craft up. The sprite is the pod with a mast (SCREW_ROWS, 4 rows taller, so hTop is 12 and hBot 8); Screw.java draws the edge-on blade by cos(rotAng).

## Settings
The SETTINGS button (bottom centre on the title and death screens) opens: haptics, sounds, music (the run music, generated bar by bar in Sfx.musicLoop with key changes through a diminished seventh bar, and a looping menu tune from Sfx.menuSong(); no audio files), and RESET PROGRESS behind an "ARE YOU SURE?" pop-up (erases formulas, tickets, clothes, electrons and best distance; settings stay). Choices are saved in prefs ("haptics", "sound", "music").

## Characters
Settings has a CHARACTER row: MALE or FEMALE. Both are cartoon characters (HEAD_M/HEAD_F and their open-mouth variants in Sprites.java): the male has tousled brown hair, browline glasses and a moustache; the female has long wavy copper hair falling over her shoulders, big eyes, blush, a gold necklace and a rounded chest (BUST, its own sprite on a small spring so it jiggles a little with steps, jumps and landings, drawn in front of long hair hanging behind it, HAIR_BACK). Heads are wide and round (12 pixels wide, 8 tall, with two blank rows above) so they read as cute. applyCharacter rewrites the shared SPR_RUN arrays, the cockpit head and the Quantum figure in place; pal() recolours the hair letters B and b copper for the female through OVR. The choice is saved in prefs ("female") and survives a progress reset.

## Hazards
Spawned by spawn() with odds that change with distance: electric bars (zappers, from the start), floor and ceiling spikes (from 40 m, later as a pair that leaves a corridor to fly through, from 150 m), missiles (100 m), saw blades sliding on a rail (120 m) and laser gates (200 m: a red dotted warning for a second, then a beam for 0.9 s with one gap to fly through). Spikes and the laser gate are solid walls for helix shots; shots cut saws, zappers and missiles. Each hazard's collision is its hits() method in Hazards.java.

## Rooms
The wall has nine rooms in a loop (the subclasses in Room.java): mission control, chemistry lab, computer room, observatory, FLEX ROOM (racks of RGB RAM sticks and a made-up guy lounging shirtless on a giant RAM stick, flexing now and then), GYM (punching bag, iron "FE" plates, a scientist on a treadmill), BREAK ROOM (a sleeper on the couch, coffee machine, donuts, "DAYS SINCE LAST EXPLOSION: 0"), THE CAT BOX (a cat that is alive, both, then not alive on a box marked DO NOT OPEN) and POWER ROOM (a scientist in a hamster wheel keeping the bulbs lit). Shutters open as you pass.

## Wear and tear, and the ERROR room
The wall has imperfections (wallWear): cracks that branch, damp stains, soot scorches, scratches and a mismatched repair panel with rust, placed by panel number so they stay put as the wall scrolls; the brick wall outside has cracks too. The tenth room is titled ERROR (its plate blinks red): a busted door hanging off its lower hinge with cracks, a missing corner the light shines through, a dead keypad, sparking wires, rubble and an occasional glitch.

## Black hole and wormhole
From about 150 m a black hole that has been cut into four quarters (Piece in Entities.java) drops one loose quarter at a time, every 60 to 110 m, lowest missing one first. A quarter that scrolls off comes back later. The four quarters show in the HUD corner (drawHoleHud). Collecting all four (holeMask == 15, per run) starts the wormhole (startWorm): 7 seconds of invulnerability with a boost of +11 m/s, all hazards cleared and nothing but twisting coin strands spawned (spawnWormCoins), with a square-ring tunnel drawn over the room (drawWorm). When it ends (endWorm) you come out 150 m further on with a clear stretch and 1.5 s of invulnerability. The next black hole starts 250 m later.
