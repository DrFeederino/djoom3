package neo.Game

import neo.Game.Player.idPlayer
import neo.Renderer.Model
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.TempDump
import neo.framework.DeclManager
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object PlayerIcon {
    val iconKeys /*[ ICON_NONE ]*/: Array<String?>? = arrayOf(
        "mtr_icon_lag",
        "mtr_icon_chat"
    )

    enum class playerIconType_t {
        ICON_LAG, ICON_CHAT, ICON_NONE
    }

    class idPlayerIcon {
        var   /*qhandle_t*/iconHandle: Int
        var iconType: playerIconType_t?
        var renderEnt: renderEntity_s? = null

        // ~idPlayerIcon();
        fun Draw(player: idPlayer?,    /*jointHandle_t*/joint: Int) {
            val origin = idVec3()
            val axis = idMat3()
            if (joint == Model.INVALID_JOINT) {
                FreeIcon()
                return
            }
            player.GetJointWorldTransform(joint, Game_local.gameLocal.time, origin, axis)
            origin.z += 16.0f
            Draw(player, origin)
        }

        fun Draw(player: idPlayer?, origin: idVec3?) {
            val localPlayer = Game_local.gameLocal.GetLocalPlayer()
            if (null == localPlayer || null == localPlayer.GetRenderView()) {
                FreeIcon()
                return
            }
            val axis = localPlayer.GetRenderView().viewaxis
            if (player.isLagged) {
                // create the icon if necessary, or update if already created
                if (!CreateIcon(player, playerIconType_t.ICON_LAG, origin, axis)) {
                    UpdateIcon(player, origin, axis)
                }
            } else if (player.isChatting) {
                if (!CreateIcon(player, playerIconType_t.ICON_CHAT, origin, axis)) {
                    UpdateIcon(player, origin, axis)
                }
            } else {
                FreeIcon()
            }
        }

        fun FreeIcon() {
            if (iconHandle != -1) {
                Game_local.gameRenderWorld.FreeEntityDef(iconHandle)
                iconHandle = -1
            }
            iconType = playerIconType_t.ICON_NONE
        }

        fun CreateIcon(
            player: idPlayer?,
            type: playerIconType_t?,
            mtr: String?,
            origin: idVec3?,
            axis: idMat3?
        ): Boolean {
            assert(type != playerIconType_t.ICON_NONE)
            if (type == iconType) {
                return false
            }
            FreeIcon()

//	memset( &renderEnt, 0, sizeof( renderEnt ) );
            renderEnt = renderEntity_s()
            renderEnt.origin.oSet(origin)
            renderEnt.axis.oSet(axis)
            renderEnt.shaderParms[RenderWorld.SHADERPARM_RED] = 1.0f
            renderEnt.shaderParms[RenderWorld.SHADERPARM_GREEN] = 1.0f
            renderEnt.shaderParms[RenderWorld.SHADERPARM_BLUE] = 1.0f
            renderEnt.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 1.0f
            renderEnt.shaderParms[RenderWorld.SHADERPARM_SPRITE_WIDTH] = 16.0f
            renderEnt.shaderParms[RenderWorld.SHADERPARM_SPRITE_HEIGHT] = 16.0f
            renderEnt.hModel = ModelManager.renderModelManager.FindModel("_sprite")
            renderEnt.callback = null
            renderEnt.numJoints = 0
            renderEnt.joints = null
            renderEnt.customSkin = null
            renderEnt.noShadow = true
            renderEnt.noSelfShadow = true
            renderEnt.customShader = DeclManager.declManager.FindMaterial(mtr)
            renderEnt.referenceShader = null
            renderEnt.bounds.oSet(renderEnt.hModel.Bounds(renderEnt))
            iconHandle = Game_local.gameRenderWorld.AddEntityDef(renderEnt)
            iconType = type
            return true
        }

        fun CreateIcon(player: idPlayer?, type: playerIconType_t?, origin: idVec3?, axis: idMat3?): Boolean {
            assert(type != playerIconType_t.ICON_NONE)
            val mtr = player.spawnArgs.GetString(PlayerIcon.iconKeys[TempDump.etoi(type)], "_default")
            return CreateIcon(player, type, mtr, origin, axis)
        }

        fun UpdateIcon(player: idPlayer?, origin: idVec3?, axis: idMat3?) {
            assert(iconHandle >= 0)
            renderEnt.origin.oSet(origin)
            renderEnt.axis.oSet(axis)
            Game_local.gameRenderWorld.UpdateEntityDef(iconHandle, renderEnt)
        }

        //
        //
        init {
            iconHandle = -1
            iconType = playerIconType_t.ICON_NONE
        }
    }
}