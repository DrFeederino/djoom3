package neo.CM;

import neo.framework.CmdSystem.idCmdSystem;
import neo.idlib.math.Vector;
import neo.idlib.math.Vector.idVec4;

import static neo.Renderer.Material.*;
import static neo.framework.CVarSystem.*;

public class CollisionModel_debug {
    static final idCVar cm_backFaceCull = new idCVar("cm_backFaceCull", "0", CVAR_GAME | CVAR_BOOL, "cull back facing polygons");
    /*
     ===============================================================================

     Visualisation code

     ===============================================================================
     */
    static final int[] cm_contentsFlagByIndex = {
            -1,                                  // 0
            CONTENTS_SOLID,                                 // 1
            CONTENTS_OPAQUE,                                // 2
            CONTENTS_WATER,                                 // 3
            CONTENTS_PLAYERCLIP,                            // 4
            CONTENTS_MONSTERCLIP,                           // 5
            CONTENTS_MOVEABLECLIP,                          // 6
            CONTENTS_IKCLIP,                                // 7
            CONTENTS_BLOOD,                                 // 8
            CONTENTS_BODY,                                  // 9
            CONTENTS_CORPSE,                                // 10
            CONTENTS_TRIGGER,                               // 11
            CONTENTS_AAS_SOLID,                             // 12
            CONTENTS_AAS_OBSTACLE,                          // 13
            CONTENTS_FLASHLIGHT_TRIGGER,                    // 14
            0
    };
    static final String[] cm_contentsNameByIndex = {
            "none",                            // 0
            "solid",                        // 1
            "opaque",                        // 2
            "water",                        // 3
            "playerclip",                    // 4
            "monsterclip",                    // 5
            "moveableclip",                    // 6
            "ikclip",                        // 7
            "blood",                        // 8
            "body",                            // 9
            "corpse",                        // 10
            "trigger",                        // 11
            "aas_solid",                    // 12
            "aas_obstacle",                    // 13
            "flashlight_trigger",            // 14
            null
    };
    static final idCVar cm_debugCollision = new idCVar("cm_debugCollision", "0", CVAR_GAME | CVAR_BOOL, "debug the collision detection");
    static final idCVar cm_drawColor = new idCVar("cm_drawColor", "1 0 0 .5", CVAR_GAME, "color used to draw the collision models");
    static final idCVar cm_drawFilled = new idCVar("cm_drawFilled", "0", CVAR_GAME | CVAR_BOOL, "draw filled polygons");
    static final idCVar cm_drawInternal = new idCVar("cm_drawInternal", "1", CVAR_GAME | CVAR_BOOL, "draw internal edges green");
    //
    static final idCVar cm_drawMask = new idCVar("cm_drawMask", "none", CVAR_GAME, "collision mask", cm_contentsNameByIndex, new idCmdSystem.ArgCompletion_String(cm_contentsNameByIndex));
    static final idCVar cm_drawNormals = new idCVar("cm_drawNormals", "0", CVAR_GAME | CVAR_BOOL, "draw polygon and edge normals");
    static final idCVar cm_testAngle = new idCVar("cm_testAngle", "60", CVAR_GAME | CVAR_FLOAT, "");
    static final idCVar cm_testBox = new idCVar("cm_testBox", "-16 -16 0 16 16 64", CVAR_GAME, "");
    static final idCVar cm_testBoxRotation = new idCVar("cm_testBoxRotation", "0 0 0", CVAR_GAME, "");
    static final idCVar cm_testCollision = new idCVar("cm_testCollision", "0", CVAR_GAME | CVAR_BOOL, "");
    static final idCVar cm_testLength = new idCVar("cm_testLength", "1024", CVAR_GAME | CVAR_FLOAT, "");
    static final idCVar cm_testModel = new idCVar("cm_testModel", "0", CVAR_GAME | CVAR_INTEGER, "");
    static final idCVar cm_testOrigin = new idCVar("cm_testOrigin", "0 0 0", CVAR_GAME, "");
    static final idCVar cm_testRadius = new idCVar("cm_testRadius", "64", CVAR_GAME | CVAR_FLOAT, "");
    static final idCVar cm_testRandomMany = new idCVar("cm_testRandomMany", "0", CVAR_GAME | CVAR_BOOL, "");
    static final idCVar cm_testReset = new idCVar("cm_testReset", "0", CVAR_GAME | CVAR_BOOL, "");
    static final idCVar cm_testRotation = new idCVar("cm_testRotation", "1", CVAR_GAME | CVAR_BOOL, "");
    static final idCVar cm_testTimes = new idCVar("cm_testTimes", "1000", CVAR_GAME | CVAR_INTEGER, "");
    static final idCVar cm_testWalk = new idCVar("cm_testWalk", "1", CVAR_GAME | CVAR_BOOL, "");
    static idVec4 cm_color;

    static int max_rotation = -999999;
    static int max_translation = -999999;
    static int min_rotation = 999999;
    static int min_translation = 999999;
    static int num_rotation = 0;
    static int num_translation = 0;
    static Vector.idVec3 start;
    static Vector.idVec3[] testend;
    static int total_rotation;
    static int total_translation;
}
