package com.elishajkmiller.formulaheist;

import android.graphics.Color;

/** The game's fixed data: store items, death-card facts, the formulas and the room names. */
final class Content {
    private Content() { }

    static final int NF = 60;                // formulas to recover

    // ---------- card finishes ----------
    // Every pull rolls a finish: 0 normal, 1 holo foil, 2 secret gold. All formulas are equally likely.
    static final int HOLO_ODDS = 8;                         // 1 in 8 pulls is holo
    static final int GOLD_ODDS = 40;                        // 1 in 40 pulls is a secret gold card
    static final int DUP_PAY = 10;                          // electrons for a duplicate (x2 holo, x5 gold)
    static final String[] FINNAME = {"NORMAL", "HOLO", "GOLD"};

    // ---------- clothes store: shirts, pants and jetpacks, each bought and worn on its own ----------

    static final String[] TABS = {"SHIRTS", "PANTS", "JETPACKS"};
    static final String[][] INAME = {
        {"LAB COAT", "FIRE RED", "OCEAN BLUE", "NIGHT SHIFT", "GOLD RUSH", "PINK STORM", "HAZMAT", "STEALTH"},
        {"DEFAULT", "BLACK", "KHAKI", "LIGHT JEANS", "RED", "GREEN", "PURPLE", "GOLD"},
        {"ROCKET", "PULSE JET", "STEAM", "ION", "DARK MATTER", "FUSION", "PLASMA", "PHOTON"}
    };
    static final int[][] ICOST = {      // electrons
        {0, 25, 25, 40, 40, 60, 60, 100},
        {0, 15, 15, 25, 25, 40, 40, 60},
        {0, 20, 30, 40, 50, 70, 90, 150}
    };
    /** the sprite letters each kind of item recolours, in the order of its colours */
    static final char[][] IKEYS = {{'W', 'w', 'x', 't', 'V'}, {'D', 'd'}, {'O', 'o', 'h'}};
    static final int[][][] IPAL = {     // null means the plain colours drawn in pal()
        {
            null,
            new int[] {Color.rgb(220, 70, 60), Color.rgb(170, 45, 45), Color.rgb(130, 35, 40), Color.rgb(255, 220, 80), Color.rgb(235, 235, 240)},
            new int[] {Color.rgb(90, 160, 240), Color.rgb(60, 120, 200), Color.rgb(45, 90, 160), Color.rgb(255, 255, 255), Color.rgb(200, 230, 255)},
            new int[] {Color.rgb(70, 72, 86), Color.rgb(50, 52, 64), Color.rgb(38, 40, 52), Color.rgb(120, 235, 255), Color.rgb(160, 170, 190)},
            new int[] {Color.rgb(245, 205, 90), Color.rgb(205, 160, 55), Color.rgb(165, 120, 40), Color.rgb(120, 40, 40), Color.rgb(255, 245, 220)},
            new int[] {Color.rgb(255, 140, 190), Color.rgb(215, 100, 150), Color.rgb(170, 75, 120), Color.rgb(255, 255, 255), Color.rgb(255, 230, 245)},
            new int[] {Color.rgb(235, 225, 70), Color.rgb(195, 185, 45), Color.rgb(150, 140, 35), Color.rgb(40, 40, 40), Color.rgb(235, 225, 70)},
            new int[] {Color.rgb(40, 40, 46), Color.rgb(28, 28, 34), Color.rgb(20, 20, 26), Color.rgb(200, 50, 50), Color.rgb(60, 62, 70)}
        },
        {
            null,
            new int[] {Color.rgb(28, 28, 34), Color.rgb(46, 46, 56)},
            new int[] {Color.rgb(150, 130, 90), Color.rgb(185, 165, 120)},
            new int[] {Color.rgb(70, 110, 170), Color.rgb(100, 140, 200)},
            new int[] {Color.rgb(150, 40, 50), Color.rgb(190, 70, 80)},
            new int[] {Color.rgb(40, 100, 60), Color.rgb(70, 140, 90)},
            new int[] {Color.rgb(90, 50, 120), Color.rgb(125, 80, 155)},
            new int[] {Color.rgb(190, 150, 50), Color.rgb(225, 185, 80)}
        },
        {
            null,
            new int[] {Color.rgb(120, 130, 100), Color.rgb(80, 90, 70), Color.rgb(165, 175, 140)},
            new int[] {Color.rgb(175, 180, 195), Color.rgb(120, 125, 145), Color.rgb(225, 230, 245)},
            new int[] {Color.rgb(80, 210, 230), Color.rgb(40, 140, 170), Color.rgb(180, 245, 255)},
            new int[] {Color.rgb(60, 60, 70), Color.rgb(32, 32, 40), Color.rgb(100, 100, 118)},
            new int[] {Color.rgb(235, 190, 70), Color.rgb(180, 135, 40), Color.rgb(255, 230, 130)},
            new int[] {Color.rgb(240, 120, 180), Color.rgb(185, 80, 135), Color.rgb(255, 175, 215)},
            null
        }
    };
    static final int RAINBOW_PACK = 7;

    // ---------- text data: death-card facts and the formulas ----------

    static final String[] FACTS = {
        "Newton's 2nd law: a = F / m. More thrust, more acceleration.",
        "Newton's 3rd law: the exhaust goes down, so you go up.",
        "Light takes about 1.3 seconds to reach us from the Moon.",
        "An atom is almost entirely empty space.",
        "Sound moves at about 343 m/s in air. You are slower, for now.",
        "Lightning heats the air around it hotter than the surface of the Sun.",
        "Drag grows with speed until it balances gravity. That is terminal velocity.",
        "Energy is never destroyed, only changed from one form to another.",
        "Electrons repel each other, which is why static makes hair stand up."
    };

    /** the formulas as they are written in a textbook, drawn with a real font so they are easy to read */
    static final String[] FORMD = {
        "F = ma", "E = mc\u00b2", "V = IR", "PV = nRT", "v = u + at", "F = G m1 m2 / r\u00b2", "E = hf", "S = k ln W",
        "\u0394x \u0394p \u2265 \u0127/2", "F = \u2212kx", "KE = \u00bd m v\u00b2", "PE = m g h", "v = f \u03bb",
        "n1 sin \u03b81 = n2 sin \u03b82", "F = k q1 q2 / r\u00b2", "p = m v", "W = F d", "P = E / t",
        "T = 2\u03c0 \u221a(L/g)", "a = v\u00b2 / r",
        "P = F / A",
        "\u03c1 = m / V",
        "F = \u03c1 g V",
        "Q = m c \u0394T",
        "I = Q / t",
        "P = I V",
        "C = Q / V",
        "E = \u00bd C V\u00b2",
        "F = q v B sin \u03b8",
        "\u03c4 = r F sin \u03b8",
        "L = I \u03c9",
        "a = \u0394v / \u0394t",
        "s = \u00bd a t\u00b2",
        "v\u00b2 = u\u00b2 + 2 a s",
        "f = 1 / T",
        "\u03bb = h / p",
        "E = \u00bd k x\u00b2",
        "\u03c9 = 2\u03c0 f",
        "T = 2\u03c0 \u221a(m/k)",
        "F = \u03bc N",
        "ε = −N ΔΦ / Δt",
        "P = σ A T⁴",
        "λ T = b",
        "F = 6π η r v",
        "P + ½ρv² + ρgh = const",
        "A₁ v₁ = A₂ v₂",
        "v = √(GM / r)",
        "v = √(2GM / r)",
        "T² ∝ r³",
        "g = GM / r²",
        "E = k q / r²",
        "R = ρ L / A",
        "R = R₁ + R₂",
        "τ = RC",
        "γ = 1 / √(1 − v²/c²)",
        "Δt′ = γ Δt",
        "N = N₀ e^(−λt)",
        "K = hf − φ",
        "ΔU = Q − W",
        "η = 1 − Tc / Th"
    };
    static final String[] FNAME = {
        "NEWTON'S SECOND LAW",
        "MASS-ENERGY",
        "OHM'S LAW",
        "IDEAL GAS LAW",
        "CONSTANT ACCELERATION",
        "UNIVERSAL GRAVITATION",
        "PLANCK RELATION",
        "ENTROPY",
        "UNCERTAINTY",
        "HOOKE'S LAW",
        "KINETIC ENERGY",
        "POTENTIAL ENERGY",
        "WAVE SPEED",
        "SNELL'S LAW",
        "COULOMB'S LAW",
        "MOMENTUM",
        "WORK",
        "POWER",
        "PENDULUM",
        "CENTRIPETAL ACCELERATION",
        "PRESSURE",
        "DENSITY",
        "BUOYANCY",
        "HEAT",
        "CURRENT",
        "ELECTRIC POWER",
        "CAPACITANCE",
        "CAPACITOR ENERGY",
        "MAGNETIC FORCE",
        "TORQUE",
        "ANGULAR MOMENTUM",
        "ACCELERATION",
        "DISTANCE FROM REST",
        "FINAL SPEED",
        "FREQUENCY",
        "DE BROGLIE WAVELENGTH",
        "SPRING ENERGY",
        "ANGULAR FREQUENCY",
        "SPRING PERIOD",
        "FRICTION",
        "FARADAY'S LAW",
        "STEFAN-BOLTZMANN LAW",
        "WIEN'S LAW",
        "STOKES DRAG",
        "BERNOULLI'S PRINCIPLE",
        "CONTINUITY",
        "ORBITAL SPEED",
        "ESCAPE VELOCITY",
        "KEPLER'S THIRD LAW",
        "GRAVITY FIELD",
        "ELECTRIC FIELD",
        "RESISTANCE OF A WIRE",
        "RESISTORS IN SERIES",
        "RC TIME CONSTANT",
        "LORENTZ FACTOR",
        "TIME DILATION",
        "RADIOACTIVE DECAY",
        "PHOTOELECTRIC EFFECT",
        "FIRST LAW OF THERMODYNAMICS",
        "CARNOT EFFICIENCY"
    };
    static final String[] FDESC = {
        "PUSH HARDER AND A THING SPEEDS UP MORE. HEAVIER THINGS SPEED UP LESS.",
        "A TINY BIT OF MASS HOLDS A HUGE AMOUNT OF ENERGY.",
        "VOLTAGE EQUALS CURRENT TIMES RESISTANCE. IT IS WHY WIRES GET WARM.",
        "SQUEEZE A GAS AND ITS PRESSURE OR ITS TEMPERATURE GOES UP.",
        "YOUR SPEED AFTER ACCELERATING FOR A WHILE IS YOUR START SPEED PLUS A TIMES T.",
        "EVERYTHING PULLS ON EVERYTHING ELSE, WEAKER WITH DISTANCE SQUARED.",
        "LIGHT COMES IN PACKETS. HIGHER FREQUENCY MEANS MORE ENERGY PER PACKET.",
        "DISORDER GROWS BECAUSE THERE ARE MORE MESSY WAYS TO BE THAN TIDY ONES.",
        "YOU CANNOT KNOW EXACTLY WHERE A PARTICLE IS AND HOW FAST IT IS GOING.",
        "A SPRING PUSHES BACK IN PROPORTION TO HOW FAR YOU STRETCH IT.",
        "DOUBLE THE SPEED AND YOU QUADRUPLE THE ENERGY.",
        "LIFT SOMETHING HIGHER AND YOU STORE MORE ENERGY IN IT.",
        "THE SPEED OF A WAVE IS ITS FREQUENCY TIMES ITS WAVELENGTH.",
        "THIS IS HOW LIGHT BENDS WHEN IT GOES INTO WATER OR GLASS.",
        "LIKE CHARGES PUSH APART AND OPPOSITE CHARGES PULL TOGETHER.",
        "MASS TIMES VELOCITY. IT IS CONSERVED IN EVERY COLLISION.",
        "WORK IS THE FORCE TIMES THE DISTANCE IT MOVES SOMETHING.",
        "POWER IS HOW FAST ENERGY IS USED: ENERGY DIVIDED BY TIME.",
        "A LONGER PENDULUM SWINGS MORE SLOWLY.",
        "TO GO IN A CIRCLE YOU MUST ACCELERATE TOWARD THE CENTER.",
        "FORCE SPREAD OVER AN AREA. A SHARP POINT PUSHES HARD ON A TINY SPOT.",
        "MASS PACKED INTO A VOLUME. DENSE THINGS SINK IN LIGHTER ONES.",
        "A THING IN A FLUID IS PUSHED UP BY THE WEIGHT OF THE FLUID IT MOVES OUT OF THE WAY.",
        "HEAT NEEDED TO WARM SOMETHING DEPENDS ON ITS MASS AND WHAT IT IS MADE OF.",
        "CURRENT IS CHARGE FLOWING PAST A POINT EVERY SECOND.",
        "POWER IN A CIRCUIT IS THE CURRENT TIMES THE VOLTAGE.",
        "HOW MUCH CHARGE A CAPACITOR HOLDS FOR EACH VOLT PUT ACROSS IT.",
        "A CAPACITOR STORES ENERGY IN ITS ELECTRIC FIELD. DOUBLE THE VOLTAGE, FOUR TIMES THE ENERGY.",
        "A MOVING CHARGE IN A MAGNETIC FIELD IS PUSHED SIDEWAYS, AT RIGHT ANGLES TO ITS MOTION.",
        "A TWIST DEPENDS ON THE FORCE AND HOW FAR FROM THE PIVOT YOU PUSH. THAT IS WHY LONG WRENCHES HELP.",
        "SPINNING THINGS KEEP SPINNING. A BIGGER, FASTER SPIN HAS MORE ANGULAR MOMENTUM.",
        "ACCELERATION IS HOW QUICKLY YOUR SPEED CHANGES.",
        "START FROM REST AND DISTANCE GROWS WITH THE SQUARE OF TIME.",
        "SPEED SQUARED CHANGES BY TWICE THE ACCELERATION TIMES THE DISTANCE. NO TIME NEEDED.",
        "FREQUENCY IS HOW MANY CYCLES HAPPEN EACH SECOND. IT IS ONE OVER THE PERIOD.",
        "EVERYTHING MOVING HAS A WAVELENGTH. FOR BIG THINGS IT IS TOO SMALL TO NOTICE.",
        "A STRETCHED OR SQUASHED SPRING STORES ENERGY. DOUBLE THE STRETCH, FOUR TIMES THE ENERGY.",
        "HOW FAST SOMETHING TURNS IN RADIANS EACH SECOND. ONE FULL TURN IS TWO PI RADIANS.",
        "A WEIGHT ON A SPRING BOUNCES SLOWER IF IT IS HEAVIER OR THE SPRING IS SOFTER.",
        "SLIDING FRICTION PUSHES BACK IN PROPORTION TO HOW HARD THE SURFACES PRESS TOGETHER.",
        "A CHANGING MAGNETIC FIELD THROUGH A COIL PUSHES A VOLTAGE ALONG IT. THIS IS HOW GENERATORS WORK.",
        "HOT THINGS GLOW. DOUBLE THE TEMPERATURE AND THEY RADIATE SIXTEEN TIMES THE POWER.",
        "THE HOTTER A THING IS, THE BLUER ITS GLOW. RED HOT IS COOLER THAN WHITE HOT.",
        "A SMALL BALL SINKING SLOWLY THROUGH HONEY FEELS A DRAG THAT GROWS WITH ITS SPEED.",
        "FASTER MOVING FLUID HAS LOWER PRESSURE. THAT IS PART OF HOW A WING GETS LIFT.",
        "WATER SPEEDS UP WHEN A PIPE NARROWS, BECAUSE THE SAME AMOUNT MUST GET THROUGH EVERY SECOND.",
        "MOVE SIDEWAYS FAST ENOUGH AND YOU KEEP MISSING THE GROUND. THAT IS AN ORBIT.",
        "THE SPEED YOU NEED TO LEAVE A PLANET AND NEVER FALL BACK.",
        "PLANETS FAR FROM THE SUN TAKE MUCH LONGER TO GO ROUND IT.",
        "HOW STRONGLY A PLANET PULLS, AT A GIVEN DISTANCE FROM ITS CENTER.",
        "A CHARGE FILLS THE SPACE AROUND IT WITH A PUSH OR PULL THAT FADES WITH DISTANCE SQUARED.",
        "LONG THIN WIRES RESIST CURRENT MORE THAN SHORT THICK ONES.",
        "PUT RESISTORS ONE AFTER ANOTHER AND THEIR RESISTANCES ADD UP.",
        "HOW LONG A CAPACITOR TAKES TO CHARGE THROUGH A RESISTOR. BIGGER R OR C MEANS SLOWER.",
        "NEAR THE SPEED OF LIGHT STRANGE THINGS HAPPEN, AND THIS NUMBER SAYS HOW STRANGE.",
        "A FAST MOVING CLOCK TICKS MORE SLOWLY THAN ONE THAT STAYS STILL.",
        "UNSTABLE ATOMS BREAK DOWN AT A STEADY RATE, SO HALF ARE LEFT AFTER EACH HALF LIFE.",
        "LIGHT KNOCKS ELECTRONS OFF METAL, BUT ONLY IF EACH PACKET HAS ENOUGH ENERGY.",
        "ENERGY IN A SYSTEM CHANGES BY THE HEAT ADDED MINUS THE WORK IT DOES.",
        "NO ENGINE CAN BEAT THIS LIMIT. A BIGGER HEAT DIFFERENCE GIVES A BETTER ENGINE."
    };

}
