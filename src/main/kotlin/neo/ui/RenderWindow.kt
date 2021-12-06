package neo.ui

import neo.Game.Animation.Anim.idMD5Anim
import neo.Game.GameEdit
import neo.Renderer.RenderSystem
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.*
import neo.TempDump
import neo.framework.Common
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinStr
import neo.ui.Winvar.idWinVar
import neo.ui.Winvar.idWinVec4
import kotlin.math.atan

/**
 *
 */
class RenderWindow {
    class idRenderWindow : idWindow {
        private val animClass: idStr = idStr()
        private val animName: idWinStr = idWinStr()
        private val lightColor: idWinVec4 = idWinVec4()
        private val lightOrigin: idWinVec4 = idWinVec4()
        private val modelName: idWinStr = idWinStr()
        private val modelOrigin: idWinVec4 = idWinVec4()
        private val modelRotate: idWinVec4 = idWinVec4()
        private val needsRender: idWinBool = idWinBool()
        private val rLight: renderLight_s = renderLight_s()
        private val viewOffset: idWinVec4 = idWinVec4()
        private var animEndTime = 0
        private var animLength = 0
        private var   /*qhandle_t*/lightDef = 0
        private var modelAnim: idMD5Anim? = null
        private var   /*qhandle_t*/modelDef = 0
        private lateinit var refdef: renderView_s
        private var updateAnimation = false
        private lateinit var world: idRenderWorld
        private lateinit var worldEntity: renderEntity_s

        //
        private val   /*qhandle_t*/worldModelDef = 0

        //
        //
        constructor(gui: idUserInterfaceLocal) : super(gui) {
            dc = null
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext, gui: idUserInterfaceLocal) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            PreRender()
            Render(time)

//            memset(refdef, 0, sizeof(refdef));
            refdef = renderView_s()
            refdef.vieworg.set(viewOffset.ToVec3())
            //refdef.vieworg.Set(-128, 0, 0);
            refdef.viewaxis.Identity()
            refdef.shaderParms[0] = 1f
            refdef.shaderParms[1] = 1f
            refdef.shaderParms[2] = 1f
            refdef.shaderParms[3] = 1f
            refdef.x = drawRect.x.toInt()
            refdef.y = drawRect.y.toInt()
            refdef.width = drawRect.w.toInt()
            refdef.height = drawRect.h.toInt()
            refdef.fov_x = 90f
            refdef.fov_y = (2 * atan((drawRect.h / drawRect.w).toDouble()) * idMath.M_RAD2DEG).toFloat()
            refdef.time = time
            world.RenderScene(refdef)
        }

        override fun GetWinVarByName(
            _name: String,
            winLookup: Boolean /*= false*/,
            owner: Array<drawWin_t?>? /*= NULL*/
        ): idWinVar? {
            if (idStr.Companion.Icmp(_name, "model") == 0) {
                return modelName
            }
            if (idStr.Companion.Icmp(_name, "anim") == 0) {
                return animName
            }
            if (idStr.Companion.Icmp(_name, "lightOrigin") == 0) {
                return lightOrigin
            }
            if (idStr.Companion.Icmp(_name, "lightColor") == 0) {
                return lightColor
            }
            if (idStr.Companion.Icmp(_name, "modelOrigin") == 0) {
                return modelOrigin
            }
            if (idStr.Companion.Icmp(_name, "modelRotate") == 0) {
                return modelRotate
            }
            if (idStr.Companion.Icmp(_name, "viewOffset") == 0) {
                return viewOffset
            }
            return if (idStr.Companion.Icmp(_name, "needsRender") == 0) {
                needsRender
            } else super.GetWinVarByName(_name, winLookup, owner)
        }

        private fun CommonInit() {
            world = RenderSystem.renderSystem.AllocRenderWorld()
            needsRender.data = true
            lightOrigin.set(idVec4(-128.0f, 0.0f, 0.0f, 1.0f))
            lightColor.set(idVec4(1.0f, 1.0f, 1.0f, 1.0f))
            modelOrigin.Zero()
            viewOffset.set(idVec4(-128.0f, 0.0f, 0.0f, 1.0f))
            modelAnim = null
            animLength = 0
            animEndTime = -1
            modelDef = -1
            updateAnimation = true
        }

        override fun ParseInternalVar(_name: String, src: idParser): Boolean {
            if (idStr.Companion.Icmp(_name, "animClass") == 0) {
                ParseString(src, animClass)
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        /**
         * This function renders the 3D shit to the screen.
         *
         * @param time
         */
        private fun Render(time: Int) {
            rLight.origin.set(lightOrigin.ToVec3()) //TODO:ref?
            rLight.shaderParms[RenderWorld.SHADERPARM_RED] = lightColor.x()
            rLight.shaderParms[RenderWorld.SHADERPARM_GREEN] = lightColor.y()
            rLight.shaderParms[RenderWorld.SHADERPARM_BLUE] = lightColor.z()
            world.UpdateLightDef(lightDef, rLight)
            if (worldEntity.hModel != null) {
                if (updateAnimation) {
                    BuildAnimation(time)
                }
                if (modelAnim != null) {
                    if (time > animEndTime) {
                        animEndTime = time + animLength
                    }
                    GameEdit.gameEdit.ANIM_CreateAnimFrame(
                        worldEntity.hModel,
                        modelAnim,
                        worldEntity.numJoints,
                        worldEntity.joints,
                        animLength - (animEndTime - time),
                        Vector.getVec3_origin(),
                        false
                    )
                }
                worldEntity.axis.set(idAngles(modelRotate.x(), modelRotate.y(), modelRotate.z()).ToMat3())
                //                System.out.printf("x=%f, y=%f, z=%f\n", modelRotate.x(), modelRotate.y(), modelRotate.z());
                world.UpdateEntityDef(modelDef, worldEntity)
            }
        }

        private fun PreRender() {
            if (needsRender.oCastBoolean()) {
                world.InitFromMap(null)
                val spawnArgs = idDict()
                spawnArgs.Set("classname", "light")
                spawnArgs.Set("name", "light_1")
                spawnArgs.Set("origin", lightOrigin.ToVec3().ToString())
                spawnArgs.Set("_color", lightColor.ToVec3().ToString())
                GameEdit.gameEdit.ParseSpawnArgsToRenderLight(spawnArgs, rLight)
                lightDef = world.AddLightDef(rLight)
                if (!TempDump.isNotNullOrEmpty(modelName.c_str())) {
                    Common.common.Warning("Window '%s' in gui '%s': no model set", GetName(), GetGui().GetSourceFile())
                }
                worldEntity = renderEntity_s()
                spawnArgs.Clear()
                spawnArgs.Set("classname", "func_static")
                spawnArgs.Set("model", modelName.c_str())
                spawnArgs.Set("origin", modelOrigin.c_str())
                GameEdit.gameEdit.ParseSpawnArgsToRenderEntity(spawnArgs, worldEntity)
                if (worldEntity.hModel != null) {
                    val v = idVec3(modelRotate.ToVec3())
                    worldEntity.axis.set(v.ToMat3())
                    worldEntity.shaderParms[0] = 1f
                    worldEntity.shaderParms[1] = 1f
                    worldEntity.shaderParms[2] = 1f
                    worldEntity.shaderParms[3] = 1f
                    modelDef = world.AddEntityDef(worldEntity)
                }
                needsRender.data = false
            }
        }

        private fun BuildAnimation(time: Int) {
            if (!updateAnimation) {
                return
            }
            if (animName.Length() != 0 && animClass.Length() != 0) {
                worldEntity.numJoints = worldEntity.hModel!!.NumJoints()
                worldEntity.joints = arrayOfNulls(worldEntity.numJoints)
                modelAnim = GameEdit.gameEdit.ANIM_GetAnimFromEntityDef(animClass.toString(), animName.toString())
                if (modelAnim != null) {
                    animLength = GameEdit.gameEdit.ANIM_GetLength(modelAnim)
                    animEndTime = time + animLength
                }
            }
            updateAnimation = false
        }
    }
}