package com.example.jetlab;

import android.graphics.Color;

import java.util.Arrays;

/**
 * The pixel art: the scientist in every pose, the vehicles, the background people's colours and the palette.
 * Each letter in a sprite row is a colour looked up by pal(); a dot is clear.
 */
final class Sprites {
    private Sprites() { }

    static final int[] COATS = {Color.rgb(235, 240, 248), Color.rgb(190, 215, 240), Color.rgb(205, 205, 215)};
    static final int[] HAIRS = {Color.rgb(110, 70, 40), Color.rgb(30, 30, 40), Color.rgb(170, 170, 180), Color.rgb(190, 90, 50)};

    static final int[] OVR = new int[128];     // the worn items' colours, looked up first by pal()

    static final String[] SPR_HEAD = {
        "......b..bb.....",
        ".....bBbBBBb....",
        "....bBBBBBBBBb..",
        "....BBBSSSSSSS..",
        "....BsSSBBSBBS..",
        "....BsSSEeSEeSS.",
        "....BsSSEESEESs.",
        "..P.BsSaSSSSsSS.",
        "..P.Bsaaeeeeeaa.",
        "..P..saaaaaaaa..",
        ".hOOowWWVtW.....",
        ".hOOowxxVtW.....",
        ".OOOowxxxSS.....",
        ".OOoowxxxSN.....",
        ".NNNNwWWWWW.....",
        "..NN.wwwwww....."
    };

    static final String[] SPR_HEAD_B = {
        ".......b..bb....",
        ".....bBbBBBb....",
        "....bBBBBBBBBb..",
        "....BBBSSSSSSS..",
        "....BsSSBBSBBS..",
        "....BsSSEeSEeSS.",
        "....BsSSEESEESs.",
        "..P.BsSaSSSSsSS.",
        "..P.Bsaaeeeeeaa.",
        "..P..saaannaaa..",
        ".hOOowwWWVt.....",
        ".hOOowxxWVt.....",
        ".OOOowxxSSW.....",
        ".OOoowxxSNW.....",
        ".NNNNwwWWWW.....",
        "..NN.wwwwww....."
    };

    static final String[] SPR_RUN2 = {
        "......b..bb.....",
        ".....bBbBBBb....",
        "....bBBBBBBBBb..",
        "....BBBSSSSSSS..",
        "....BsSSBBSBBS..",
        "....BsSSEeSEeSS.",
        "....BsSSEESEESs.",
        "..P.BsSaSSSSsSS.",
        "..P.Bsaaeeeeeaa.",
        "..P..saaaaaaaa..",
        ".hOOoxwwVtw.....",
        ".hOOoxxxVtw.....",
        ".OOOoxxSSWW.....",
        ".OOooxxSNWW.....",
        ".NNNNxwwWWw.....",
        "..NN.wwwwww....."
    };

    static final String[] SPR_RUN3 = {
        ".......b..bb....",
        ".....bBbBBBb....",
        "....bBBBBBBBBb..",
        "....BBBSSSSSSS..",
        "....BsSSBBSBBS..",
        "....BsSSEeSEeSS.",
        "....BsSSEESEESs.",
        "..P.BsSaSSSSsSS.",
        "..P.Bsaaeeeeeaa.",
        "..P..saaannaaa..",
        ".hOOowwVtWW.....",
        ".hOOowxxtWW.....",
        ".OOOowxxSSW.....",
        ".OOoowxxSNW.....",
        ".NNNNwwWWWW.....",
        "..NN.wwwwww....."
    };

    /** skin over the top row of the eyes, drawn on top of the head for a blink */
    static final String[] SPR_BLINK = {
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "......SS..SS....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................"
    };

    /** upper body per run phase: same outline, shading and tie shift so the torso seems to turn */
    static final String[][] SPR_RUN = {SPR_HEAD, SPR_HEAD_B, SPR_RUN2, SPR_RUN3};

    static final String[][] MALE = {SPR_HEAD.clone(), SPR_HEAD_B.clone(), SPR_RUN2.clone(), SPR_RUN3.clone()};   // the originals

    // The two characters. The male has tousled brown hair, browline glasses and a moustache; the female has long
    // wavy copper hair that falls over her shoulders, big eyes, blush, a gold necklace and a rounded chest.
    static final String[] HEAD_M = {
        "................",
        "................",
        ".....bBBBBBBb...",
        "....bBBBBBBBBb..",
        "....BBBBBSSSSB..",
        "....BSFFFFFFSB..",
        "....BSFEeSEeFB..",
        "....BSFEESEEFB..",
        "....BsSBBBBSsB..",
        ".....sSSeeSSs..."
    };

    static final String[] HEAD_M_B = {
        "................",
        "................",
        ".....bBBBBBBb...",
        "....bBBBBBBBBb..",
        "....BBBBBSSSSB..",
        "....BSFFFFFFSB..",
        "....BSFEeSEeFB..",
        "....BSFEESEEFB..",
        "....BsSBBBBSsB..",
        ".....sSSnnSSs..."
    };

    static final String[] HEAD_F = {
        "................",
        "................",
        ".....bBBBBBBb...",
        "....bBBbBBBBBb..",
        "...BBBBSSSSBBBB.",
        "...BBSsSSSSsSBB.",
        "...BBSEeSSEeSBB.",
        "...BBSEESSEESBB.",
        "...BBSpSnnSpSBB.",
        "...BBbSSPPSSbBB."
    };

    static final String[] HEAD_F_B = {
        "................",
        "................",
        ".....bBBBBBBb...",
        "....bBBbBBBBBb..",
        "...BBBBSSSSBBBB.",
        "...BBSsSSSSsSBB.",
        "...BBSEeSSEeSBB.",
        "...BBSEESSEESBB.",
        "...BBSpSEESpSBB.",
        "...BBbSSPPSSbBB."
    };

    /**
     * The torso rows for the female: the arm sits one row lower so the upper chest shows, a small V neckline
     * replaces the collar and tie, and her hair falls over the back shoulder. The chest itself is a separate
     * sprite (BUST) so it can jiggle.
     */
    static String[] femaleBody(String[] body) {
        char[][] r = new char[body.length][];
        for (int i = 0; i < body.length; i++) r[i] = body[i].toCharArray();
        for (int i = 3; i >= 1; i--) {                  // lower the arm by one row
            for (int c = 6; c < 12; c++) {
                if (r[i][c] == 'x' || r[i][c] == 'S' || r[i][c] == 'N') { r[i + 1][c] = r[i][c]; r[i][c] = 'W'; }
            }
        }
        // the same small V neckline on every step (the collar and tie positions differ per step, which made it flicker)
        String top = "wWSSWw", next = "wWWSWW";
        for (int c = 0; c < 6; c++) { r[0][5 + c] = top.charAt(c); r[1][5 + c] = next.charAt(c); }
        String[] out = new String[body.length];
        for (int i = 0; i < body.length; i++) out[i] = new String(r[i]);
        return out;
    }

    /** her long hair hanging down the front, drawn behind the body so the chest sits in front of it */
    static final String[] HAIR_LOCK = {
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "............BBB.",
        "............BBB.",
        ".............BBb",
        ".............BBb",
        "............BBb.",
        "...........BBBb.",
        "............Bb.."
    };

    static final String[] BUST = {"WW", "Ww", "w."};      // small, on the upper chest just under the collar

    /** the hanging lock swung backwards a little more or less in four frames, so it streams as she runs */
    static final String[][] LOCK_FRAMES = lockFrames();

    static String[] shiftRows(String[] base, int from, int by) {
        String[] out = base.clone();
        for (int i = from; i < out.length; i++) {
            String r = out[i];
            String gap = by < 0 ? "........".substring(0, -by) : "........".substring(0, by);
            out[i] = by < 0 ? r.substring(-by) + gap : gap + r.substring(0, r.length() - by);
        }
        return out;
    }

    static String[][] lockFrames() {
        String[] f1 = shiftRows(HAIR_LOCK, 13, -1);
        String[] f2 = shiftRows(shiftRows(HAIR_LOCK, 12, -1), 15, -1);
        return new String[][] {HAIR_LOCK, f1, f2, f1};
    }

    /** her hair down the back: it runs on from the hair at the back of her head, behind the pack, and ends just below it */
    static final String[] HAIR_TAIL = {
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..BB............",
        ".BBB............",
        ".BBB............",
        "BBB.............",
        "BB..............",
        "BBb.............",
        "BB..............",
        "BBb.............",
        ".BB.............",
        ".b.............."
    };

    static final String[][] TAIL_FRAMES = tailFrames();

    static String[][] tailFrames() {
        String[] f1 = shiftRows(HAIR_TAIL, 14, 1);
        String[] f2 = shiftRows(shiftRows(HAIR_TAIL, 12, 1), 15, 1);
        return new String[][] {HAIR_TAIL, f1, f2, f1};
    }

    /** rewrite the shared sprite arrays in place for the chosen character */
    static void applyCharacter(boolean female) {
        for (int k = 0; k < SPR_RUN.length; k++) {
            boolean alt = (k & 1) == 1;      // the second and fourth step have the mouth open
            String[] head = female ? (alt ? HEAD_F_B : HEAD_F) : (alt ? HEAD_M_B : HEAD_M);
            String[] body = Arrays.copyOfRange(MALE[k], HEAD_ROWS, MALE[k].length);
            if (female) body = femaleBody(body);
            for (int i = 0; i < HEAD_ROWS; i++) SPR_RUN[k][i] = head[i];
            for (int i = 0; i < body.length; i++) SPR_RUN[k][HEAD_ROWS + i] = body[i];
        }
        SPR_BLINK[6] = female ? "......SS..SS...." : ".......SS.SS....";     // the lids cover wherever this face's eyes are
        // the vehicle cockpits show just the face, without the hose or the long hair outside the glass
        for (int i = 0; i < HEAD_ROWS; i++) HEAD_ONLY[i] = ("...." + SPR_HEAD[i].substring(4)).replace('P', '.');
        for (int i = 0; i < SPR_HEAD.length; i++) QUANT[i] = ".." + SPR_HEAD[i] + "..";
    }

    static final String[] LEGS_A = {
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....KKKK.KKKK..",
        "....LKKKK.LKKKK."
    };

    static final String[] LEGS_B = {
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        "....DDd..DDd....",
        "....KKKK.KKKK...",
        "........LKKKKK.."
    };

    static final String[] LEGS_M = {
        ".....DDDDDd.....",
        ".....DDDDDd.....",
        ".....DDDDDd.....",
        ".....DDDDDd.....",
        ".....DDDDDd.....",
        ".....KKKKKKK....",
        "....LKKKKKKKK..."
    };

    static final String[] LEGS_C = {
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd..DDd...",
        ".....KKKK.KKKK..",
        "....LKKKK......."
    };

    static final String[] LEGS_AIR = {
        "....DDDdDDDd....",
        "...DDDd.DDDd....",
        "..DDDd..DDDd....",
        ".DDDd...DDDd....",
        ".DDd....DDd.....",
        ".KKK....KKKK....",
        "................"
    };

    static final String[] LEGS_AIR2 = {
        "....DDDdDDDd....",
        "...DDDd..DDd....",
        "..DDDd...DDDd...",
        ".DDDd....DDDd...",
        ".DDd.....DDd....",
        ".KKK.....KKK....",
        "................"
    };

    static final String[] LEGS_FALL1 = {
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd.DDDd...",
        ".....DDd..DDd...",
        ".....KKK..KKK...",
        ".....KKK..KKK..."
    };

    static final String[] LEGS_FALL2 = {
        ".....DDd.DDd....",
        ".....DDd.DDd....",
        ".....DDd..DDDd..",
        ".....DDd...DDd..",
        ".....DDd...DDd..",
        ".....KKK...KKKK.",
        "................"
    };

    /** face down on the floor after a zap, facing right (28 wide, 11 tall) */
    static final String[] SPR_FLAT = {
        "............................",
        "...........ooO..bb.b........",
        "..........hOOOoBBbBBb.......",
        "..........hOOOoBBBBBBBb.....",
        "....DDd...hOOOoWwBBBBBBBB...",
        "...DDLd...NNNNwWWWBsSSSSSB..",
        "..KKd.dDDDDDWWWWWWWBsSEXSs..",
        "..KK...DDDDDwWWWWWWWxsSSSSSa",
        "KKKDDDDDDDDDwwwwwwwwxsaannra",
        "LKKDDDDDDDDDwwwwwwwwxaaaaaa.",
        "......................xxxSSS"
    };

    static final int HEAD_ROWS = 10;
    static final String[] HEAD_ONLY = headOnly();

    /** just the head, without the hose, for the vehicle cockpits */
    static String[] headOnly() {
        String[] h = Arrays.copyOfRange(SPR_HEAD, 0, HEAD_ROWS);
        for (int i = 0; i < h.length; i++) h[i] = h[i].replace('P', '.');
        return h;
    }

    static final String[] BODY_STOMP = {
        "....mmmmmmmmmmmm....",
        "...mMMMMMMMMMMMMm...",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMMMMMMMMMMMMMMm..",
        "..mRRRRRRRRRRRRRRm..",
        "..mRYRRRRRRRRRRYRm..",
        "..mRRRRRRRRRRRRRRm..",
        "...mmmmmmmmmmmmmm..."
    };

    static final String[] LEGS_STOMP = {
        "......MM......MM....", ".....MM......MM.....", "......MM......MM....", ".....MM......MM.....",
        "......MM......MM....", "....KKKK....KKKK....", "...KKKKK....KKKKK..."
    };

    static final String[] LEGS_STOMP_C = {
        "....MMMM....MMMM....", "...KKKKK....KKKKK...", "...KKKKK....KKKKK...", "...KKKKKK..KKKKKK..."
    };

    static final String[] POD = {
        "......RRRRRRRR......",
        ".....RRRRRRRRRR.....",
        "....mmmmmmmmmmmm....",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "..mMggggggggggggMm..",
        "....mmmmmmmmmmmm....",
        ".....UUUUUUUUUU.....",
        "......UUUUUUUU......"
    };

    /** the helix's head: the pod with green and magenta caps, the two ends of a base pair */
    static final String[] HELIX_HEAD = recolor(POD, 'R', 'Q', 'U', 'q');

    static String[] recolor(String[] rows, char a, char a2, char b, char b2) {
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++) out[i] = rows[i].replace(a, a2).replace(b, b2);
        return out;
    }

    /** the glider bird's body: the pod with white feathers, a beak and a tail */
    static final String[] BIRD_ROWS = birdRows();

    static String[] birdRows() {
        String[] rows = recolor(POD, 'R', 'W', 'U', 'w');
        char[][] r = new char[rows.length][];
        for (int i = 0; i < rows.length; i++) r[i] = rows[i].toCharArray();
        for (int i = 7; i <= 9; i++) { r[i][18] = 'O'; r[i][19] = i == 8 ? 'O' : '.'; }       // beak
        for (int i = 9; i <= 12; i++) { r[i][0] = 'V'; if (i > 9) r[i][1] = 'V'; }           // tail
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++) out[i] = new String(r[i]);
        return out;
    }

    /** the aerial screw's body: the pod with a mast on top for the rotor */
    static final String[] SCREW_ROWS = screwRows();

    static String[] screwRows() {
        String[] out = new String[POD.length + 4];
        for (int i = 0; i < 4; i++) out[i] = "..........xx..........";
        System.arraycopy(recolor(POD, 'R', 'x', 'U', 'm'), 0, out, 4, POD.length);
        return out;
    }

    static final String[] BOARD = {
        "..mMMMMMMMMMMMMMMm..",
        "...mmmmmmmmmmmmmm...",
        "..UU............UU..",
        "...UUUUUUUUUUUUUU..."
    };

    /** the scientist standing on a board with two linked orbs, padded to the vehicle width */
    static final String[] QUANT = quant();

    static String[] quant() {
        String[] out = new String[SPR_HEAD.length + LEGS_A.length + BOARD.length];
        int n = 0;
        for (String r : SPR_HEAD) out[n++] = ".." + r + "..";
        for (String r : LEGS_A) out[n++] = ".." + r + "..";
        for (String r : BOARD) out[n++] = r;
        return out;
    }

    static int pal(char ch) {
        int ov = ch < 128 ? OVR[ch] : 0;
        if (ov != 0) return ov;
        switch (ch) {
            case 'F': return Color.rgb(120, 130, 160);
            case 'p': return Color.rgb(238, 150, 160);
            case 'Q': return Color.rgb(80, 220, 130);
            case 'q': return Color.rgb(230, 90, 200);
            case 'B': return Color.rgb(80, 50, 30);
            case 'b': return Color.rgb(135, 90, 55);
            case 'E': return Color.rgb(30, 30, 45);
            case 'n': return Color.rgb(150, 70, 70);
            case 'h': return Color.rgb(255, 135, 85);
            case 'x': return Color.rgb(150, 160, 185);
            case 'd': return Color.rgb(78, 92, 140);
            case 'L': return Color.rgb(95, 95, 108);
            case 'y': return Color.rgb(150, 110, 70);
            case 't': return Color.rgb(40, 45, 70);
            case 'S': return Color.rgb(240, 200, 160);
            case 'G': return Color.rgb(50, 60, 80);
            case 'C': return Color.rgb(120, 235, 255);
            case 'O': return Color.rgb(225, 80, 40);
            case 'a': return Color.rgb(170, 140, 115);
            case 'r': return Color.rgb(230, 120, 140);
            case 'X': return Color.rgb(60, 40, 40);
            case 'e': return Color.rgb(255, 255, 255);
            case 'P': return Color.rgb(210, 170, 60);
            case 'o': return Color.rgb(150, 45, 25);
            case 'N': return Color.rgb(120, 125, 140);
            case 'W': return Color.rgb(240, 244, 250);
            case 'w': return Color.rgb(195, 203, 220);
            case 'D': return Color.rgb(50, 60, 100);
            case 's': return Color.rgb(205, 160, 120);
            case 'T': return Color.rgb(60, 45, 40);
            case 'V': return Color.rgb(70, 100, 160);
            case 'R': return Color.rgb(220, 70, 70);
            case 'U': return Color.rgb(70, 110, 230);
            case 'Y': return Color.rgb(255, 200, 80);
            case 'M': return Color.rgb(170, 178, 195);
            case 'm': return Color.rgb(100, 108, 128);
            case 'g': return Color.rgb(140, 215, 235);
            case 'K': return Color.rgb(30, 30, 40);
            default: return 0;
        }
    }

    /** the lounging guy in the flex room: 38 x 14, about the size of the controllers in mission control (a made-up cartoon man) */
    static final String[] FLEX_GUY = {
        "..HHHHHHHH............................",
        ".HHHHHHHHH............................",
        "HHHHHHHHHH...................JJJJ.....",
        "HHHHHHSHHH..................JJJJJJ....",
        "HHSHHSSSSH..................JJJJJJ....",
        "HsSSKSSKSHs.................JJJJJJ....",
        "HHSSSSSmSH..................JJJJJJ....",
        "sHHSSmmSHss.................JJJJJJ....",
        "sSHHSSSHsss....s........BB..JJJJJJ....",
        "sS......ssshhhhshhhhSSSSSBJJJJJJJJJWWW",
        "sS......SSShhhhshhhhSsssSBJJJJJJJJJWWW",
        "SSSS....SSSSsSSSSSSSSSSSSBJJJJJJJJJWWW",
        "SSSS....sssssssssssssssssBjjjjjjjjjwww",
        "......................................"
    };

    static final String FLEX_KEYS = "SshHKJjWwBm";

    static final int[] FLEX_COLS = {
        Color.rgb(232, 190, 152), Color.rgb(196, 146, 110), Color.rgb(246, 212, 178), Color.rgb(78, 50, 34),
        Color.rgb(24, 22, 30), Color.rgb(64, 88, 146), Color.rgb(46, 66, 116), Color.rgb(228, 228, 238),
        Color.rgb(120, 124, 140), Color.rgb(48, 66, 112), Color.rgb(150, 70, 70)
    };
}
