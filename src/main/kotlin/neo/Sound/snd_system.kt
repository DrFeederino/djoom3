package neo.Soundimportimport

import neo.Renderer.Cinematic.cinData_t
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Sound.snd_cache.idSoundCache
import neo.Sound.snd_efxfile.idEFXFile
import neo.Sound.snd_emitter.SoundFX
import neo.Sound.snd_emitter.SoundFX_Comb
import neo.Sound.snd_emitter.SoundFX_Lowpass
import neo.Sound.snd_emitter.idSoundChannel
import neo.Sound.snd_local
import neo.Sound.snd_local.idAudioHardware
import neo.Sound.snd_local.idSampleDecoder
import neo.Sound.snd_shader
import neo.Sound.snd_world.idSoundWorldLocal
import neo.Sound.snd_world.s_stats
import neo.Sound.sound.idSoundSystem
import neo.Sound.sound.idSoundWorld
import neo.Sound.sound.soundDecoderInfo_t
import neo.TempDump
import neo.framework.BuildDefines
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_SoundName
import neo.framework.Common
import neo.framework.Common.MemInfo_t
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Simd
import neo.sys.win_main
import neo.sys.win_shared
import neo.sys.win_snd
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import java.nio.*
import java.util.*

neo.idlib.*
import java.nio.*

import neo.idlib.math.Vector.idVec3
import neo.CM.CollisionModel.contactType_t
import neo.CM.CollisionModel.contactInfo_t
import neo.TempDump.SERiAL
import neo.idlib.math.Matrix.idMat3
import neo.CM.CollisionModel.trace_s
import java.lang.UnsupportedOperationException
import neo.idlib.MapFile.idMapFile
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.containers.CInt
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector.idVec6
import neo.idlib.MapFile.idMapEntity
import neo.CM.AbstractCollisionModel_local.cm_node_s
import neo.idlib.containers.CFloat
import neo.CM.AbstractCollisionModel_local.cm_brushRef_s
import neo.CM.AbstractCollisionModel_local.cm_polygonRef_s
import neo.CM.AbstractCollisionModel_local
import neo.CM.CollisionModel_load
import neo.idlib.MapFile.idMapPrimitive
import neo.idlib.MapFile.idMapPatch
import neo.idlib.MapFile.idMapBrush
import neo.framework.CVarSystem.idCVar
import neo.framework.CVarSystem
import neo.Renderer.Material
import neo.CM.CollisionModel_debug
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_String
import neo.idlib.math.Vector.idVec4
import neo.idlib.containers.HashIndex.idHashIndex
import neo.CM.AbstractCollisionModel_local.cm_windingList_s
import neo.CM.CollisionModel_local.idCollisionModelManagerLocal
import neo.CM.CollisionModel.idCollisionModelManager
import neo.CM.CollisionModel_local
import neo.CM.AbstractCollisionModel_local.cm_model_s
import neo.CM.AbstractCollisionModel_local.cm_procNode_s
import neo.framework.Common
import neo.CM.AbstractCollisionModel_local.cm_vertex_s
import neo.CM.AbstractCollisionModel_local.cm_edge_s
import neo.CM.AbstractCollisionModel_local.cm_polygon_s
import neo.idlib.geometry.TraceModel.traceModelVert_t
import neo.idlib.geometry.TraceModel.traceModelEdge_t
import neo.idlib.geometry.TraceModel.traceModelPoly_t
import neo.idlib.geometry.TraceModel.traceModel_t
import neo.idlib.math.Math_h
import neo.CM.AbstractCollisionModel_local.cm_trmPolygon_s
import neo.CM.AbstractCollisionModel_local.cm_trmEdge_s
import neo.CM.AbstractCollisionModel_local.cm_trmVertex_s
import neo.CM.CollisionModel
import neo.idlib.math.Angles.idAngles
import neo.idlib.Text.Str
import neo.idlib.math.Random.idRandom
import neo.idlib.Timer.idTimer
import java.util.Locale
import neo.framework.File_h.idFile
import neo.CM.CollisionModel_files
import neo.framework.FileSystem_h
import neo.idlib.math.Pluecker.idPluecker
import neo.CM.AbstractCollisionModel_local.cm_traceWork_s
import neo.CM.CollisionModel_translate
import java.util.Arrays
import neo.idlib.math.Plane
import neo.CM.CollisionModel_rotate
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Math_h.idMath
import neo.CM.AbstractCollisionModel_local.cm_brush_s
import neo.idlib.geometry.TraceModel
import neo.CM.CollisionModel_contents
import java.util.function.Supplier
import java.util.function.IntFunction
import neo.CM.AbstractCollisionModel_local.cm_polygonRefBlock_s
import neo.CM.AbstractCollisionModel_local.cm_brushRefBlock_s
import neo.CM.AbstractCollisionModel_local.cm_nodeBlock_s
import neo.Renderer.Material.cullType_t
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Token.idToken
import neo.Renderer.RenderWorld
import neo.idlib.geometry.Winding
import neo.framework.DeclManager
import neo.idlib.geometry.Surface_Patch.idSurface_Patch
import neo.idlib.MapFile.idMapBrushSide
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.idlib.containers.StrPool.idPoolStr
import neo.Renderer.ModelManager
import neo.CM.AbstractCollisionModel_local.cm_polygonBlock_s
import neo.CM.AbstractCollisionModel_local.cm_brushBlock_s
import neo.TempDump
import neo.ui.RegExp.idRegister.REGTYPE
import neo.ui.RegExp.idRegister
import neo.ui.Winvar.idWinVar
import neo.idlib.math.Vector.idVec2
import neo.ui.Rectangle.idRectangle
import neo.ui.Winvar.idWinVec4
import neo.ui.Winvar.idWinRectangle
import neo.ui.Winvar.idWinVec2
import neo.ui.Winvar.idWinVec3
import neo.ui.Winvar.idWinFloat
import neo.ui.Winvar.idWinInt
import neo.ui.Winvar.idWinBool
import neo.framework.DemoFile.idDemoFile
import neo.idlib.containers.List.idList
import neo.idlib.Text.Parser.idParser
import neo.ui.Window.idWindow
import neo.ui.Window.wexpOpType_t
import neo.ui.GuiScript.idGuiScriptList
import neo.idlib.math.Interpolate.idInterpolateAccelDecelLinear
import neo.ui.Winvar.idWinBackground
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.rvNamedEvent
import neo.ui.Window.wexpOp_t
import neo.ui.RegExp.idRegisterList
import neo.ui.Window.idWindow.ON
import neo.ui.Winvar.idWinStr
import neo.ui.Window.idTimeLineEvent
import neo.ui.Window.idTransitionData
import kotlin.jvm.JvmOverloads
import neo.ui.DeviceContext.idDeviceContext.CURSOR
import neo.ui.SimpleWindow.idSimpleWindow
import neo.ui.Winvar
import neo.ui.EditWindow.idEditWindow
import neo.ui.ChoiceWindow.idChoiceWindow
import neo.ui.SliderWindow.idSliderWindow
import neo.ui.MarkerWindow.idMarkerWindow
import neo.ui.BindWindow.idBindWindow
import neo.ui.ListWindow.idListWindow
import neo.ui.FieldWindow.idFieldWindow
import neo.ui.RenderWindow.idRenderWindow
import neo.ui.GameSSDWindow.idGameSSDWindow
import neo.ui.GameBearShootWindow.idGameBearShootWindow
import neo.ui.GameBustOutWindow.idGameBustOutWindow
import neo.sys.sys_public.sysEvent_s
import neo.sys.sys_public.sysEventType_t
import neo.framework.KeyInput
import neo.framework.KeyInput.idKeyInput
import neo.Renderer.RenderSystem_init
import neo.idlib.Dict_h.idDict
import neo.ui.GuiScript.idGuiScript
import neo.ui.Window.wexpRegister_t
import neo.idlib.Dict_h.idKeyValue
import neo.framework.UsercmdGen
import neo.framework.DeclTable.idDeclTable
import neo.framework.DeclManager.declType_t
import neo.ui.Window.idRegEntry
import java.util.Objects
import java.lang.NumberFormatException
import neo.ui.UserInterface.idUserInterface
import neo.ui.GuiScript.guiCommandDef_t
import neo.ui.GuiScript.Script_Set
import neo.ui.GuiScript.Script_SetFocus
import neo.ui.GuiScript.Script_EndGame
import neo.ui.GuiScript.Script_ResetTime
import neo.ui.GuiScript.Script_ShowCursor
import neo.ui.GuiScript.Script_ResetCinematics
import neo.ui.GuiScript.Script_Transition
import neo.ui.GuiScript.Script_LocalSound
import neo.ui.GuiScript.Script_RunScript
import neo.ui.GuiScript.Script_EvalRegs
import neo.ui.GuiScript
import neo.ui.GuiScript.idGSWinVar
import neo.idlib.Lib.idLib
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import java.lang.StringBuffer
import neo.sys.win_input
import neo.ui.EditWindow
import java.util.HashMap
import neo.idlib.containers.idStrList
import neo.ui.ListWindow.idTabRect
import neo.ui.ListWindow
import neo.ui.DeviceContext.idDeviceContext.ALIGN
import neo.ui.Winvar.idMultiWinVar
import neo.ui.ListGUI.idListGUI
import neo.framework.Session.logStats_t
import neo.ui.MarkerWindow.markerData_t
import neo.framework.FileSystem_h.idFileList
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.RenderWorld.renderLight_s
import neo.Game.Animation.Anim.idMD5Anim
import neo.Renderer.RenderWorld.renderView_s
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderSystem
import neo.Game.GameEdit
import neo.idlib.geometry.JointTransform.idJointMat
import neo.ui.DeviceContext.idDeviceContext.SCROLLBAR
import neo.Renderer.RenderSystem.fontInfoEx_t
import neo.Renderer.RenderSystem.fontInfo_t
import neo.ui.DeviceContext
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Matrix.idMat4
import neo.Renderer.RenderSystem.glyphInfo_t
import neo.ui.Rectangle.idRegion
import neo.ui.GameSSDWindow.SSDCrossHair
import neo.ui.GameSSDWindow
import neo.ui.GameSSDWindow.SSD
import neo.ui.GameSSDWindow.SSDEntity
import neo.ui.GameSSDWindow.SSDMover
import neo.ui.GameSSDWindow.SSDAsteroid
import neo.ui.GameSSDWindow.SSDAstronaut
import neo.ui.GameSSDWindow.SSDExplosion
import neo.ui.GameSSDWindow.SSDPoints
import neo.ui.GameSSDWindow.SSDProjectile
import neo.ui.GameSSDWindow.SSDPowerup
import neo.ui.GameSSDWindow.SSDLevelStats_t
import neo.ui.GameSSDWindow.SSDAsteroidData_t
import neo.ui.GameSSDWindow.SSDAstronautData_t
import neo.ui.GameSSDWindow.SSDLevelData_t
import neo.ui.GameSSDWindow.SSDPowerupData_t
import neo.ui.GameSSDWindow.SSDWeaponData_t
import neo.ui.GameSSDWindow.SSDGameStats_t
import neo.ui.UserInterfaceLocal.idUserInterfaceManagerLocal
import neo.ui.UserInterface.idUserInterface.idUserInterfaceManager
import neo.ui.UserInterface
import neo.ui.GameBustOutWindow.powerupType_t
import neo.ui.GameBustOutWindow.BOEntity
import neo.ui.GameBustOutWindow.collideDir_t
import neo.ui.GameBustOutWindow
import neo.ui.GameBustOutWindow.BOBrick
import neo.Renderer.Image_files
import neo.ui.ListGUILocal.idListGUILocal
import neo.ui.GameBearShootWindow.BSEntity
import neo.ui.GameBearShootWindow
import neo.sys.RC.doom_resource
import javax.imageio.ImageIO
import java.io.IOException
import neo.framework.CmdSystem.cmdFunction_t
import kotlin.Throws
import neo.idlib.Lib.idException
import neo.TempDump.TODO_Exception
import neo.sys.RC.CreateResourceIDs_f
import neo.sys.win_cpu.bitFlag_s
import neo.framework.BuildDefines
import neo.sys.win_cpu
import neo.sys.sys_public
import java.lang.Process
import java.io.BufferedReader
import neo.sys.win_net.net_interface
import neo.sys.win_net
import neo.sys.sys_public.netadr_t
import java.net.SocketAddress
import java.util.Enumeration
import java.net.NetworkInterface
import java.net.InetAddress
import java.net.Inet6Address
import java.net.SocketException
import neo.sys.win_net.udpMsg_s
import org.lwjgl.openal.ALC
import java.lang.UnsatisfiedLinkError
import neo.Sound.snd_system.idSoundSystemLocal
import java.lang.IllegalStateException
import neo.Sound.snd_local.idAudioHardware
import neo.idlib.math.Simd
import javax.sound.sampled.SourceDataLine
import neo.Sound.snd_local
import neo.sys.win_main
import java.lang.StringBuilder
import java.util.concurrent.ScheduledExecutorService
import neo.sys.sys_public.xthreadInfo
import neo.sys.sys_public.sysMemoryStats_s
import neo.sys.sys_public.xthread_t
import neo.sys.sys_public.xthreadPriority
import neo.sys.win_local
import java.util.concurrent.locks.ReentrantLock
import neo.sys.win_syscon
import neo.sys.win_glimp
import neo.sys.win_local.Win32Vars_t
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.TimeUnit
import java.nio.file.Paths
import java.io.FilenameFilter
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.datatransfer.StringSelection
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.lang.Runnable
import neo.sys.win_main.Sys_In_Restart_f
import neo.sys.win_shared
import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import java.nio.channels.FileChannel
import kotlin.jvm.JvmStatic
import neo.Tools.edit_public
import neo.sys.sys_local
import neo.sys.sys_local.idSysLocal
import java.text.SimpleDateFormat
import neo.sys.sys_public.idSys
import org.lwjgl.glfw.GLFWErrorCallback
import neo.sys.win_glimp.glimpParms_t
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVidMode
import org.lwjgl.opengl.GL
import neo.Renderer.tr_local
import java.awt.event.InputEvent
import neo.idlib.containers.CBool
import java.awt.event.KeyListener
import java.awt.event.MouseListener
import neo.sys.win_net.idUDPLag
import neo.sys.sys_public.netadrtype_t
import java.util.TimerTask
import neo.sys.win_syscon.WinConData
import javax.swing.JFrame
import java.awt.Dimension
import javax.swing.UIManager
import java.lang.ClassNotFoundException
import java.lang.InstantiationException
import java.lang.IllegalAccessException
import javax.swing.UnsupportedLookAndFeelException
import neo.framework.Licensee
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JTextField
import javax.swing.JButton
import neo.sys.win_syscon.Click
import javax.swing.JTextArea
import java.awt.Color
import javax.swing.JScrollPane
import neo.framework.EditField.idEditField
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import neo.Game.GameSys.Event.idEventDef
import neo.Tools.Compilers.AAS.AASFile.idAASSettings
import neo.Game.Game_local.idGameLocal
import neo.Game.Entity.idEntity
import neo.framework.DeclParticle.idDeclParticle
import neo.Game.Game_local.idEntityPtr
import neo.Game.AI.AI.moveCommand_t
import neo.Game.AI.AI.moveStatus_t
import neo.Game.AI.AI.moveType_t
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.AI.AAS.idAASCallback
import neo.Game.Pvs.pvsHandle_t
import neo.Game.AI.AAS.idAAS
import neo.Game.Game_local
import neo.Game.AI.AI.idAI
import neo.Game.Actor.idActor
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.Physics.Physics.idPhysics
import neo.Game.AI.AI.obstaclePath_s
import neo.Game.AI.AI_pathing.obstacle_s
import neo.Game.AI.AI_pathing
import neo.Game.AI.AI_pathing.pathNode_s
import neo.Tools.Compilers.AAS.AASFile
import neo.Game.GameSys.SysCvar
import neo.Game.AI.AI.predictedPath_s
import neo.Game.AI.AI_pathing.pathTrace_s
import neo.Game.AI.AI
import neo.Game.Physics.Clip.idClipModel
import neo.Game.AI.AI_pathing.ballistics_s
import neo.Game.GameSys.Class.eventCallback_t1
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.Class.eventCallback_t2
import neo.Game.AI.AI_Events
import neo.Game.GameSys.Class.eventCallback_t0
import neo.Game.Moveable
import neo.Game.GameSys.Class.eventCallback_t3
import neo.Game.Script.Script_Program.idScriptBool
import neo.Game.Script.Script_Program.idScriptFloat
import neo.Game.Projectile.idProjectile
import neo.Sound.snd_shader.idSoundShader
import neo.Game.AI.AI.idMoveState
import neo.Game.AI.AI.particleEmitter_s
import neo.Game.Physics.Physics_Monster.idPhysics_Monster
import neo.Game.AI.AI.talkState_t
import neo.idlib.math.Angles
import neo.Game.Animation.Anim_Blend.idAnimator
import neo.Game.GameSys.Class.idClass
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Player.idPlayer
import neo.Tools.Compilers.AAS.AASFile.idReachability
import neo.Game.Animation.Anim_Blend.idDeclModelDef
import neo.Game.Animation.Anim.frameCommand_t
import neo.Game.Animation.Anim_Blend.idAnim
import neo.Game.Animation.Anim.frameCommandType_t
import neo.Game.Animation.Anim
import neo.Game.Actor
import neo.Game.Physics.Physics_Monster.monsterMoveResult_t
import neo.Game.Moveable.idMoveable
import neo.Game.Animation.Anim.jointModTransform_t
import neo.Game.Projectile.idSoulCubeMissile
import neo.Game.AI.AAS.aasPath_s
import neo.Game.AI.AAS.aasObstacle_s
import neo.Game.AI.AAS.aasGoal_s
import neo.Game.AI.AI.idAASFindAreaOutOfRange
import neo.Game.AI.AI.idAASFindAttackPosition
import neo.Game.AI.AI.idAASFindCover
import java.lang.Math
import neo.Game.Animation.Anim.animFlags_t
import neo.Game.Player
import neo.Game.AF.afTouch_s
import neo.idlib.math.Quat.idQuat
import neo.Game.AFEntity.idAFAttachment
import neo.Game.Script.Script_Thread.idThread
import neo.Game.AI.AI.idCombatNode
import neo.Game.Misc.idPathCorner
import neo.Game.AFEntity.idAFEntity_Base
import neo.Tools.Compilers.AAS.AASFile.aasTrace_s
import neo.Game.AI.AAS_local.idAASLocal
import neo.Game.AI.AAS_routing.idRoutingObstacle
import neo.Game.AI.AAS_routing.idRoutingCache
import neo.Game.AI.AAS_routing.idRoutingUpdate
import neo.Tools.Compilers.AAS.AASFile.idAASFile
import neo.Tools.Compilers.AAS.AASFileManager
import neo.Tools.Compilers.AAS.AASFile.aasArea_s
import neo.Tools.Compilers.AAS.AASFile.aasFace_s
import neo.Tools.Compilers.AAS.AASFile.aasPortal_s
import neo.Tools.Compilers.AAS.AASFile.aasCluster_s
import neo.Game.AI.AAS_routing
import neo.Game.AI.AAS
import neo.Game.AI.AAS_pathing
import neo.Tools.Compilers.AAS.AASFile.aasNode_s
import neo.Tools.Compilers.AAS.AASFile.aasEdge_s
import neo.Tools.Compilers.AAS.AASFile.idReachability_Walk
import neo.Game.AI.AI_Vagary.idAI_Vagary
import neo.Game.AI.AI_Vagary
import neo.Game.GameSys.Class.eventCallback_t5
import neo.idlib.geometry.Winding2D.idWinding2D
import neo.idlib.BV.Box.idBox
import neo.idlib.containers.Queue.idQueueTemplate
import neo.Game.Script.Script_Program.idVarDef
import neo.idlib.containers.StaticList.idStaticList
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Program
import neo.Game.Script.Script_Program.statement_s
import neo.Game.Script.Script_Program.idTypeDef
import neo.Game.Script.Script_Program.idVarDefName
import neo.Game.Script.idProgram
import neo.Game.Script.Script_Compiler.idCompiler
import neo.Game.Script.Script_Program.idCompileError
import neo.Game.Script.Script_Compiler
import neo.Game.Script.Script_Compiler.opcode_s
import neo.framework.FileSystem_h.fsMode_t
import neo.Game.Script.Script_Program.varEval_s
import neo.Game.Script.Script_Program.idVarDef.initialized_t
import neo.Game.Script.Script_Thread
import neo.Game.Entity.signalNum_t
import neo.Game.Camera.idCamera
import neo.Game.GameSys.Class.eventCallback_t6
import neo.Game.GameSys.Class.eventCallback_t4
import neo.Game.Script.Script_Interpreter.idInterpreter
import neo.Game.Physics.Physics_AF.idAFBody
import neo.Game.Script.Script_Thread.idThread.ListThreads_f
import neo.Game.Script.Script_Program.idScriptObject
import neo.Game.Script.Script_Program.idScriptVariable
import neo.Game.Script.Script_Program.eval_s
import neo.Game.Game
import neo.Game.Script.Script_Interpreter.prstack_s
import neo.Game.Script.Script_Interpreter
import neo.Game.Animation.Anim.AFJointModType_t
import neo.Game.AF.jointConversion_s
import neo.Game.Physics.Physics_AF.idPhysics_AF
import neo.framework.DeclAF.idDeclAF
import neo.framework.DeclManager.declState_t
import neo.Game.AF
import neo.Game.Physics.Physics_AF.idAFConstraint
import neo.Game.Physics.Physics_AF.constraintType_t
import neo.Game.Physics.Physics_AF.idAFConstraint_BallAndSocketJoint
import neo.Game.Physics.Physics_AF.idAFConstraint_UniversalJoint
import neo.Game.Physics.Physics_AF.idAFConstraint_Hinge
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_AF.idAFConstraint_Fixed
import neo.framework.DeclAF.idDeclAF_Body
import neo.Game.AF.idAF
import neo.framework.DeclAF.declAFJointMod_t
import neo.framework.DeclAF.idDeclAF_Constraint
import neo.framework.DeclAF.declAFConstraintType_t
import neo.Game.Physics.Physics_AF.idAFConstraint_Slider
import neo.Game.Physics.Physics_AF.idAFConstraint_Spring
import neo.framework.DeclAF.getJointTransform_t
import neo.Game.FX.idEntityFx
import neo.Game.FX
import neo.Game.FX.idFXLocalAction
import neo.framework.DeclFX.idDeclFX
import neo.framework.DeclFX.idFXSingleAction
import neo.framework.DeclFX.fx_enum
import neo.idlib.BitMsg.idBitMsgDelta
import neo.Game.FX.idTeleporter
import neo.idlib.containers.Hierarchy.idHierarchy
import neo.Game.GameSys.Class.idTypeInfo
import java.lang.FunctionalInterface
import neo.Game.WorldSpawn.idWorldspawn
import neo.Game.Misc.idStaticEntity
import neo.Game.Trigger.idTrigger_Multi
import neo.Game.Target.idTarget_Tip
import neo.Game.Target.idTarget_Remove
import neo.Game.Mover.idMover
import neo.Game.Light.idLight
import neo.Game.Camera.idCameraAnim
import neo.Game.Misc.idFuncEmitter
import neo.Game.Misc.idAnimated
import neo.Game.Projectile.idBFGProjectile
import neo.Game.Trigger.idTrigger_Hurt
import neo.Game.Item.idMoveablePDAItem
import neo.Game.Misc.idLocationEntity
import neo.Game.Misc.idPlayerStart
import neo.Game.Sound.idSound
import neo.Game.Target.idTarget_GiveEmail
import neo.Game.Target.idTarget_SetPrimaryObjective
import neo.Game.Item.idObjectiveComplete
import neo.Game.Target.idTarget
import neo.Game.Camera.idCameraView
import neo.Game.Item.idObjective
import neo.Game.Target.idTarget_SetShaderParm
import neo.Game.Target.idTarget_FadeEntity
import neo.Game.Item.idItem
import neo.Game.Mover.idSplinePath
import neo.Game.AFEntity.idAFEntity_Generic
import neo.Game.Mover.idDoor
import neo.Game.Trigger.idTrigger_Count
import neo.Game.Target.idTarget_EndLevel
import neo.Game.Target.idTarget_CallObjectFunction
import neo.Game.Trigger.idTrigger_Fade
import neo.Game.Item.idPDAItem
import neo.Game.Item.idVideoCDItem
import neo.Game.Misc.idLocationSeparatorEntity
import neo.Game.Projectile.idDebris
import neo.Game.Misc.idSpawnableEntity
import neo.Game.Target.idTarget_LightFadeIn
import neo.Game.Target.idTarget_LightFadeOut
import neo.Game.Item.idItemPowerup
import neo.Game.Misc.idForceField
import neo.Game.Target.idTarget_LockDoor
import neo.Game.Target.idTarget_SetInfluence
import neo.Game.Moveable.idExplodingBarrel
import neo.Game.Target.idTarget_EnableLevelWeapons
import neo.Game.AFEntity.idAFEntity_WithAttachedHead
import neo.Game.Misc.idFuncAASObstacle
import neo.Game.Misc.idVacuumEntity
import neo.Game.Mover.idRotater
import neo.Game.Mover.idElevator
import neo.Game.Misc.idShaking
import neo.Game.Misc.idFuncRadioChatter
import neo.Game.Misc.idFuncPortal
import neo.Game.Item.idMoveableItem
import neo.Game.Misc.idFuncSmoke
import neo.Game.Misc.idPhantomObjects
import neo.Game.Misc.idBeam
import neo.Game.Misc.idExplodable
import neo.Game.Misc.idEarthQuake
import neo.Game.Projectile.idGuidedProjectile
import neo.Game.Target.idTarget_Show
import neo.Game.BrittleFracture.idBrittleFracture
import neo.Game.Trigger.idTrigger_Timer
import neo.Game.Mover.idPendulum
import neo.Game.Item.idItemRemover
import neo.Game.Target.idTarget_GiveSecurity
import neo.Game.Trigger.idTrigger_EntityName
import neo.Game.Moveable.idBarrel
import neo.Game.Misc.idActivator
import neo.Game.Misc.idFuncSplat
import neo.Game.Target.idTarget_Damage
import neo.Game.Target.idTarget_SetKeyVal
import neo.Game.Target.idTarget_EnableStamina
import neo.Game.Misc.idVacuumSeparatorEntity
import neo.Game.Misc.idDamagable
import neo.Game.SecurityCamera.idSecurityCamera
import neo.Game.Trigger.idTrigger_Touch
import neo.Game.AFEntity.idAFEntity_ClawFourFingers
import neo.Game.Mover.idBobber
import neo.Game.Target.idTarget_LevelTrigger
import neo.Game.Target.idTarget_RemoveWeapons
import neo.Game.Mover.idPlat
import neo.Game.Entity.idAnimatedEntity
import neo.Game.GameSys.Event.idEvent
import neo.Game.GameSys.Class.classSpawnFunc_t
import neo.Game.GameSys.Class.idClass.DisplayInfo_f
import neo.Game.GameSys.Class.idClass.ListClasses_f
import neo.Game.GameSys.Class.idEventFunc
import neo.Game.GameSys.Class.idClass_Save
import neo.Game.GameSys.Class.idClass_Restore
import neo.idlib.containers.LinkList.idLinkList
import neo.Game.GameSys.SysCmds.gameDebugLine_t
import neo.Game.GameSys.SysCmds
import neo.idlib.BitMsg.idBitMsg
import neo.framework.Async.NetworkSystem
import neo.Game.GameSys.SysCmds.Cmd_EntityList_f
import neo.Game.GameSys.SysCmds.Cmd_ActiveEntityList_f
import neo.Game.GameSys.SysCmds.Cmd_ListSpawnArgs_f
import neo.Game.GameSys.SysCmds.Cmd_ReloadScript_f
import neo.Game.GameSys.SysCmds.Cmd_Script_f
import neo.Game.GameSys.SysCmds.Cmd_KillMonsters_f
import neo.Game.GameSys.SysCmds.Cmd_KillMovables_f
import neo.Game.GameSys.SysCmds.Cmd_KillRagdolls_f
import neo.Game.Weapon
import neo.Game.Weapon.idWeapon
import neo.Game.GameSys.SysCmds.Cmd_Give_f
import neo.Game.GameSys.SysCmds.Cmd_CenterView_f
import neo.Game.GameSys.SysCmds.Cmd_God_f
import neo.Game.GameSys.SysCmds.Cmd_Notarget_f
import neo.Game.GameSys.SysCmds.Cmd_Noclip_f
import neo.Game.GameSys.SysCmds.Cmd_Kill_f
import neo.Game.GameSys.SysCmds.Cmd_PlayerModel_f
import neo.Game.GameSys.SysCmds.Cmd_Say_f
import neo.Game.GameSys.SysCmds.Cmd_SayTeam_f
import neo.Game.GameSys.SysCmds.Cmd_AddChatLine_f
import neo.Game.GameSys.SysCmds.Cmd_Kick_f
import neo.Game.GameSys.SysCmds.Cmd_GetViewpos_f
import neo.Game.GameSys.SysCmds.Cmd_SetViewpos_f
import neo.Game.GameSys.SysCmds.Cmd_Teleport_f
import neo.Game.GameSys.SysCmds.Cmd_Trigger_f
import neo.Game.GameSys.SysCmds.Cmd_Spawn_f
import neo.Game.GameSys.SysCmds.Cmd_Damage_f
import neo.Game.GameSys.SysCmds.Cmd_Remove_f
import neo.Game.GameSys.SysCmds.Cmd_TestLight_f
import neo.Game.GameSys.SysCmds.Cmd_TestPointLight_f
import neo.Game.GameSys.SysCmds.Cmd_PopLight_f
import neo.Game.GameSys.SysCmds.Cmd_ClearLights_f
import neo.Game.GameSys.SysCmds.Cmd_TestFx_f
import neo.Game.GameSys.SysCmds.Cmd_AddDebugLine_f
import neo.Game.GameSys.SysCmds.Cmd_RemoveDebugLine_f
import neo.Game.GameSys.SysCmds.Cmd_BlinkDebugLine_f
import neo.Game.GameSys.SysCmds.Cmd_ListDebugLines_f
import neo.Game.GameSys.SysCmds.Cmd_ListCollisionModels_f
import neo.Game.GameSys.SysCmds.Cmd_CollisionModelInfo_f
import neo.Game.Animation.Anim_Import.idModelExport
import neo.Game.GameSys.SysCmds.Cmd_ExportModels_f
import neo.Game.Animation.Anim.idAnimManager
import neo.Game.GameSys.SysCmds.Cmd_ReexportModels_f
import neo.Game.GameSys.SysCmds.Cmd_ReloadAnims_f
import neo.Game.GameSys.SysCmds.Cmd_ListAnims_f
import neo.Game.GameSys.SysCmds.Cmd_AASStats_f
import neo.Game.GameSys.SysCmds.Cmd_TestDamage_f
import neo.Game.GameSys.SysCmds.Cmd_TestBoneFx_f
import neo.Game.GameSys.SysCmds.Cmd_TestDeath_f
import neo.Game.GameSys.SysCmds.Cmd_WeaponSplat_f
import neo.Game.GameSys.SysCmds.Cmd_SaveSelected_f
import neo.Game.GameSys.SysCmds.Cmd_DeleteSelected_f
import neo.Game.GameSys.SysCmds.Cmd_SaveMoveables_f
import neo.Game.GameSys.SysCmds.Cmd_SaveRagdolls_f
import neo.Game.GameSys.SysCmds.Cmd_BindRagdoll_f
import neo.Game.GameSys.SysCmds.Cmd_UnbindRagdoll_f
import neo.Game.GameSys.SysCmds.Cmd_GameError_f
import neo.Game.GameSys.SysCmds.Cmd_SaveLights_f
import neo.Game.GameSys.SysCmds.Cmd_SaveParticles_f
import neo.Game.GameSys.SysCmds.Cmd_DisasmScript_f
import neo.Game.GameSys.SysCmds.Cmd_TestSave_f
import neo.Game.GameSys.SysCmds.Cmd_RecordViewNotes_f
import neo.Game.GameSys.SysCmds.Cmd_CloseViewNotes_f
import neo.Game.GameSys.SysCmds.Cmd_ShowViewNotes_f
import neo.Renderer.Model.srfTriangles_s
import neo.Game.GameSys.SysCmds.Cmd_NextGUI_f
import neo.TempDump.void_callback
import neo.Game.GameSys.SysCmds.ArgCompletion_DefFile
import neo.Game.GameSys.SysCmds.Cmd_TestId_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.Game.MultiplayerGame
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_MapName
import neo.Game.GameSys.SysCvar.gameVersion_s
import neo.framework.BuildVersion
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Vector.idVec5
import neo.framework.DeclSkin.idDeclSkin
import neo.Game.Game.refSound_t
import neo.framework.UsercmdGen.usercmd_t
import neo.Game.GameSys.TypeInfo.WriteVariableType_t
import neo.Game.GameSys.NoGameTypeInfo.classTypeInfo_t
import neo.Game.GameSys.NoGameTypeInfo
import neo.Game.GameSys.NoGameTypeInfo.enumTypeInfo_t
import neo.Game.GameSys.TypeInfo.idTypeInfoTools
import neo.Game.GameSys.TypeInfo.idTypeInfoTools.PrintVariable
import neo.Game.GameSys.TypeInfo.idTypeInfoTools.WriteVariable
import neo.Game.GameSys.NoGameTypeInfo.classVariableInfo_t
import neo.Game.GameSys.TypeInfo.idTypeInfoTools.WriteGameStateVariable
import neo.Game.GameSys.TypeInfo.idTypeInfoTools.InitVariable
import neo.Game.GameSys.TypeInfo.idTypeInfoTools.VerifyVariable
import neo.Game.GameSys.TypeInfo.WriteGameState_f
import neo.Game.GameSys.TypeInfo.CompareGameState_f
import neo.Game.GameSys.TypeInfo.TestSaveGame_f
import neo.Game.GameSys.TypeInfo.ListTypeInfo_f.SortTypeInfoBySize
import neo.Game.GameSys.TypeInfo.ListTypeInfo_f.SortTypeInfoByName
import neo.idlib.containers.List.cmp_t
import neo.Game.GameSys.TypeInfo.ListTypeInfo_f
import neo.Game.GameSys.NoGameTypeInfo.constantInfo_t
import neo.Game.GameSys.NoGameTypeInfo.enumValueInfo_t
import neo.Game.IK.idIK
import neo.Game.IK.idIK_Walk
import neo.Game.IK.idIK_Reach
import neo.Game.Physics.Clip.trmCache_s
import neo.Game.Physics.Clip.clipSector_s
import neo.Game.Physics.Clip.clipLink_s
import neo.Game.Physics.Clip.idClip
import neo.Game.Physics.Clip.idClip.listParms_s
import neo.Renderer.RenderWorld.modelTrace_s
import neo.Game.Physics.Push.idPush.pushed_s
import neo.Game.Physics.Push.idPush.pushedGroup_s
import neo.Game.Physics.Push
import neo.Game.Projectile
import neo.Game.AFEntity
import neo.Game.Physics.Physics_Actor.idPhysics_Actor
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics_AF
import neo.Game.Physics.Physics_AF.AFPState_s
import neo.idlib.math.Matrix.idMatX
import neo.idlib.math.Vector.idVecX
import neo.Game.Physics.Physics_AF.idAFConstraint.constraintFlags_s
import neo.Game.Physics.Physics_AF.idAFConstraint_ConeLimit
import neo.Game.Physics.Physics_AF.idAFConstraint_BallAndSocketJointFriction
import neo.Game.Physics.Physics_AF.idAFConstraint_PyramidLimit
import neo.Game.Physics.Physics_AF.idAFConstraint_UniversalJointFriction
import neo.Game.Physics.Physics_AF.idAFConstraint_HingeFriction
import neo.Game.Physics.Physics_AF.idAFConstraint_HingeSteering
import neo.Game.Physics.Physics_AF.idAFConstraint_ContactFriction
import neo.Game.Physics.Physics_AF.idAFConstraint_Contact
import neo.Game.Physics.Physics_AF.AFBodyPState_s
import neo.Game.Physics.Physics_AF.idAFBody.bodyFlags_s
import neo.Game.Physics.Physics_AF.idAFTree
import neo.Game.Physics.Physics_Base.idPhysics_Base
import neo.Game.Physics.Physics_AF.AFCollision_s
import neo.idlib.math.Lcp.idLCP
import neo.idlib.math.Quat.idCQuat
import neo.Game.Physics.Force_Field.forceFieldApplyType
import neo.Game.Physics.Force_Field.forceFieldType
import neo.Game.Physics.Physics_Player.idPhysics_Player
import neo.Game.Physics.Physics_Base.contactEntity_t
import neo.idlib.BV.Bounds
import neo.Game.Physics.Physics
import neo.Game.Physics.Physics_Player
import neo.Game.Physics.Physics_Player.playerPState_s
import neo.Game.Physics.Physics_Player.waterLevel_t
import neo.Game.Physics.Physics_Player.pmtype_t
import neo.Game.Physics.Physics_Static.staticPState_s
import neo.Game.Physics.Physics_Static.idPhysics_Static
import neo.Game.Physics.Physics_Monster
import neo.Game.Physics.Physics_Monster.monsterPState_s
import neo.Game.Physics.Physics_RigidBody
import neo.Game.Physics.Physics_RigidBody.rigidBodyPState_s
import neo.Game.Physics.Physics_RigidBody.rigidBodyIState_s
import neo.idlib.math.Ode.idODE
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody.rigidBodyDerivatives_s
import neo.idlib.math.Ode.deriveFunction_t
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody.RigidBodyDerivatives
import neo.idlib.math.Ode.idODE_Euler
import neo.Game.Physics.Physics_Parametric.parametricPState_s
import neo.idlib.math.Extrapolate.idExtrapolate
import neo.idlib.math.Curve.idCurve_Spline
import neo.Game.Physics.Physics_Parametric
import neo.idlib.math.Extrapolate
import neo.Game.Physics.Physics_Parametric.idPhysics_Parametric
import neo.Game.Physics.Physics_StaticMulti
import neo.Game.Physics.Physics_StaticMulti.idPhysics_StaticMulti
import neo.Game.Pvs.pvsPassage_t
import neo.Game.Pvs.pvsPortal_t
import neo.Game.Pvs.pvsStack_t
import neo.Game.Pvs.pvsCurrent_t
import neo.Game.Pvs
import neo.Game.Pvs.pvsArea_t
import neo.Game.Pvs.pvsType_t
import neo.Renderer.RenderWorld.exitPortal_t
import neo.Game.Pvs.idPVS
import neo.Renderer.RenderWorld.portalConnection_t
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.Game.Animation.Anim.jointAnimInfo_t
import neo.Renderer.Model.idMD5Joint
import neo.Game.Animation.Anim.frameBlend_t
import neo.Game.Animation.Anim.frameLookup_t
import neo.Game.Animation.Anim.jointInfo_t
import neo.Game.Sound
import neo.framework.DeclManager.idDecl
import java.util.Collections
import neo.Game.Animation.Anim_Blend
import java.lang.Character
import neo.Game.Animation.Anim_Blend.idAnimBlend
import neo.Game.Animation.Anim.idAFPoseJointMod
import neo.Game.Animation.Anim.jointMod_t
import neo.idlib.containers.BinSearch
import neo.Game.Animation.Anim_Import
import neo.Game.Animation.Anim_Testmodel.idTestModel
import neo.Game.Actor.copyJoints_t
import neo.Game.Animation.Anim_Testmodel.idTestModel.KeepTestModel_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestSkin_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestShaderParm_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestModel_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.ArgCompletion_TestModel
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestParticleStopTime_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestAnim_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.ArgCompletion_TestAnim
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestBlend_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestModelNextAnim_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestModelPrevAnim_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestModelNextFrame_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestModelPrevFrame_f
import neo.Sound.sound.idSoundWorld
import neo.Game.Game.gameReturn_t
import neo.Game.Game.escReply_t
import neo.Game.Game.allowReply_t
import neo.Sound.snd_shader
import neo.Sound.sound.idSoundEmitter
import neo.Game.AFEntity.jointTransformData_t
import neo.Game.Game.idGameEdit
import neo.Tools.Compilers.AAS.AASFileManager.idAASFileManager
import neo.framework.CmdSystem.idCmdSystem
import neo.framework.Common.idCommon
import neo.framework.CVarSystem.idCVarSystem
import neo.framework.DeclManager.idDeclManager
import neo.framework.FileSystem_h.idFileSystem
import neo.framework.Async.NetworkSystem.idNetworkSystem
import neo.Renderer.ModelManager.idRenderModelManager
import neo.Renderer.RenderSystem.idRenderSystem
import neo.Sound.sound.idSoundSystem
import neo.Game.Game.idGame
import neo.Renderer.RenderWorld.deferredEntityCallback_t
import neo.Game.Misc
import neo.Game.Misc.idSpring
import neo.Game.Physics.Force_Spring.idForce_Spring
import neo.Game.Physics.Force_Field.idForce_Field
import neo.Game.AFEntity.idAFEntity_Gibbable
import neo.Game.Misc.idLiquid
import neo.Renderer.Model_liquid.idRenderModelLiquid
import neo.Game.Misc.idFuncAASPortal
import neo.Game.GameSys.SaveGame
import neo.Game.Actor.idAttachInfo
import neo.Game.Actor.idAnimState
import neo.Game.IK
import neo.Renderer.Material.surfTypes_t
import neo.Game.Light
import neo.Game.Mover
import neo.Game.Mover.idMover.moveState_t
import neo.Game.Mover.idMover.moverCommand_t
import neo.Game.Mover.idMover.rotationState_t
import neo.Game.Mover.idMover.moveStage_t
import neo.Game.Mover.moverState_t
import neo.Game.Mover.floorInfo_s
import neo.Game.Mover.idElevator.elevatorState_t
import neo.Game.Mover.idMover_Binary
import neo.Game.Mover.idMover_Periodic
import neo.Game.Mover.idRiser
import neo.Game.Camera
import neo.Game.Camera.cameraFrame_t
import neo.Game.Entity.signal_t
import neo.TempDump.NiLLABLE
import neo.Game.Entity.idEntity.entityFlags_s
import neo.Game.Entity.signalList_t
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.Renderer.Model.dynamicModel_t
import neo.idlib.math.Curve.idCurve_CatmullRomSpline
import neo.idlib.math.Curve.idCurve_NonUniformBSpline
import neo.idlib.math.Curve.idCurve_NURBS
import neo.idlib.math.Curve.idCurve_BSpline
import neo.Game.Entity.damageEffect_s
import neo.Game.Player.idLevelTriggerInfo
import neo.Game.Player.idObjectiveInfo
import neo.Game.Player.idItemInfo
import neo.Game.Player.aasLocation_t
import neo.idlib.math.Interpolate.idInterpolate
import neo.Game.Player.loggedAccel_t
import neo.Game.GameEdit.idDragEntity
import neo.Game.Player.idInventory
import neo.Game.PlayerView.idPlayerView
import neo.Game.AFEntity.idAFEntity_Vehicle
import neo.Game.PlayerIcon.idPlayerIcon
import neo.Game.MultiplayerGame.gameType_t
import neo.framework.DeclPDA.idDeclPDA
import neo.framework.DeclPDA.idDeclVideo
import neo.Game.Game_network
import neo.Renderer.RenderWorld.guiPoint_t
import neo.framework.DeclPDA.idDeclAudio
import neo.framework.DeclPDA.idDeclEmail
import neo.Game.Target.idTarget_SessionCommand
import neo.Game.Target.idTarget_WaitForButton
import neo.Game.Target.idTarget_SetGlobalShaderTime
import neo.Game.Target.idTarget_SetShaderTime
import neo.Game.Target.idTarget_Give
import neo.Game.Target.idTarget_SetModel
import neo.Game.Target.idTarget_SetFov
import neo.Game.Target.idTarget_FadeSoundClass
import neo.Game.Weapon.weaponStatus_t
import neo.Game.Trigger.idTrigger
import neo.Game.AFEntity.idMultiModelAF
import neo.Game.Physics.Physics_AF.idAFConstraint_Suspension
import neo.Game.AFEntity.idAFEntity_VehicleSimple
import neo.Game.AFEntity.idAFEntity_VehicleFourWheels
import neo.Game.AFEntity.idAFEntity_VehicleSixWheels
import neo.Game.Physics.Force_Constant.idForce_Constant
import neo.Game.Physics.Force_Drag.idForce_Drag
import neo.Game.GameEdit.idCursor3D
import neo.Game.GameEdit.selectedTypeInfo_s
import neo.Game.Moveable.idExplodingBarrel.explode_state_t
import neo.Game.Game.gameExport_t
import neo.Game.Game.gameImport_t
import neo.Sound.snd_system
import neo.Game.Game_local.entityState_s
import neo.Game.Game_local.snapshot_s
import neo.Game.Game_local.entityNetEvent_s
import neo.Game.Game_network.idEventQueue
import neo.Game.Game_local.spawnSpot_t
import neo.Game.GameEdit.idEditEntities
import neo.Game.MultiplayerGame.idMultiplayerGame
import neo.Game.Physics.Push.idPush
import neo.Game.SmokeParticles.idSmokeParticles
import neo.idlib.math.Simd.idSIMD
import neo.framework.DeclManager.idListDecls_f
import neo.framework.DeclManager.idPrintDecls_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Decl
import neo.Game.Game_network.idEventQueue.outOfOrderBehaviour_t
import neo.Game.MultiplayerGame.snd_evt_t
import neo.Game.MultiplayerGame.idMultiplayerGame.msg_evt_t
import neo.Game.GameSys.Class.idAllocError
import neo.Game.Game_local.idGameLocal.sortSpawnPoints
import neo.Game.Game_local.idGameLocal.ArgCompletion_EntityName
import neo.Game.MultiplayerGame.idMultiplayerGame.DropWeapon_f
import neo.Game.MultiplayerGame.idMultiplayerGame.MessageMode_f
import neo.Game.MultiplayerGame.idMultiplayerGame.VoiceChat_f
import neo.Game.MultiplayerGame.idMultiplayerGame.VoiceChatTeam_f
import neo.Game.Game_local.idGameLocal.MapRestart_f
import neo.Game.MultiplayerGame.idMultiplayerGame.ForceReady_f
import neo.Game.PlayerIcon.playerIconType_t
import neo.Game.PlayerIcon
import neo.Game.PlayerView.screenBlob_t
import neo.Game.PlayerView
import neo.Game.Projectile.idProjectile.projectileFlags_s
import neo.Game.Projectile.idProjectile.projectileState_t
import neo.Game.Projectile.beamTarget_t
import neo.Game.SecurityCamera
import neo.Game.SmokeParticles.singleSmoke_t
import neo.framework.DeclParticle.idParticleStage
import neo.Game.SmokeParticles.activeSmokeStage_t
import neo.Game.SmokeParticles
import neo.framework.DeclParticle.particleGen_t
import neo.Game.BrittleFracture.shard_s
import neo.Game.BrittleFracture
import neo.Game.MultiplayerGame.playerVote_t
import neo.Game.MultiplayerGame.mpChatLine_s
import neo.Game.MultiplayerGame.mpPlayerState_s
import neo.Game.MultiplayerGame.idMultiplayerGame.vote_flags_t
import neo.Game.MultiplayerGame.idMultiplayerGame.vote_result_t
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.BV.Frustum.idFrustum
import neo.idlib.BV.Frustum
import neo.idlib.math.Matrix.idMat2
import neo.idlib.math.Matrix.idMat0
import neo.idlib.math.Matrix.idMat5
import neo.idlib.math.Matrix.idMat6
import neo.idlib.math.Lcp
import neo.idlib.math.Lcp.idLCP_Square
import neo.idlib.math.Lcp.idLCP_Symmetric
import neo.idlib.math.Simd.idSIMDProcessor
import neo.idlib.math.Simd_Generic.idSIMD_Generic
import neo.idlib.math.Simd.idSIMD.Test_f
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.dominantTri_s
import neo.idlib.math.Vector.idVec
import neo.TempDump.TypeErasure_Expection
import neo.idlib.math.Curve.idCurve
import neo.idlib.math.Math_h.idMath._flint
import neo.idlib.math.Random.idRandom2
import neo.idlib.math.Vector.idPolar3
import org.lwjgl.BufferUtils
import neo.idlib.math.Complex.idComplex
import neo.idlib.math.Polynomial.idPolynomial
import neo.idlib.math.Polynomial
import neo.TempDump.Deprecation_Exception
import neo.idlib.math.Simd_Generic
import neo.idlib.Text.Str.Measure_t
import neo.TempDump.CPP_class.Char
import neo.idlib.Text.Str.idStr.formatList_t
import neo.idlib.Text.Lexer.punctuation_t
import java.lang.IndexOutOfBoundsException
import neo.idlib.Text.Base64.idBase64
import neo.idlib.Text.Parser.define_s
import neo.idlib.Text.Parser.indent_s
import neo.idlib.Text.Parser.idParser.value_s
import neo.idlib.Text.Parser.idParser.operator_s
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import neo.idlib.geometry.Surface.surfaceEdge_t
import neo.idlib.geometry.Surface.idSurface
import neo.idlib.geometry.Winding2D
import neo.idlib.geometry.TraceModel.idTraceModel.volumeIntegrals_t
import neo.idlib.geometry.TraceModel.idTraceModel.projectionIntegrals_t
import neo.idlib.geometry.TraceModel.idTraceModel.polygonIntegrals_t
import neo.idlib.geometry.Surface_Polytope
import neo.idlib.geometry.Surface_Polytope.idSurface_Polytope
import java.lang.RuntimeException
import java.math.BigInteger
import neo.framework.CVarSystem.idInternalCVar
import neo.framework.CmdSystem.commandDef_s
import neo.TempDump.reflects
import java.util.LinkedList
import neo.idlib.containers.Stack.idStackTemplate
import neo.idlib.containers.StrPool.idStrPool
import neo.idlib.containers.HashIndex
import java.util.function.ToIntFunction
import java.util.stream.Collectors
import neo.idlib.containers.StaticList
import java.lang.CloneNotSupportedException
import neo.idlib.Dict_h.KeyCompare
import neo.idlib.Dict_h.idDict.ListKeys_f
import neo.idlib.Dict_h.idDict.ListValues_f
import neo.idlib.LangDict.idLangKeyValue
import neo.Renderer.Cinematic.cinData_t
import neo.Sound.sound.soundDecoderInfo_t
import neo.framework.Common.MemInfo_t
import neo.Sound.snd_local.waveformatex_s
import org.lwjgl.openal.AL10
import neo.Sound.snd_wavefile.idWaveFile
import neo.Sound.snd_local.idSampleDecoder
import neo.Sound.snd_cache
import neo.Sound.snd_cache.idSoundSample
import neo.Sound.snd_local.waveformat_s
import neo.Sound.snd_local.pcmwaveformat_s
import neo.Sound.snd_local.waveformatextensible_s
import neo.Sound.snd_local.mminfo_s
import neo.Sound.snd_decoder.idSampleDecoderLocal
import neo.sys.win_snd.idAudioHardwareWIN32
import neo.Sound.snd_world.soundPortalTrace_s
import neo.Sound.snd_emitter.idSoundEmitterLocal
import neo.Sound.snd_emitter.idSoundFade
import neo.Sound.sound
import neo.framework.DemoFile.demoSystem_t
import neo.Sound.snd_local.soundDemoCommand_t
import neo.Sound.snd_emitter
import neo.Sound.snd_world.idSoundWorldLocal
import neo.Sound.snd_wavefile
import neo.Sound.snd_emitter.idSoundChannel
import neo.Renderer.Cinematic.idSndWindow
import neo.Sound.snd_emitter.idSlowChannel
import neo.idlib.math.Simd.speakerLabel
import neo.Sound.snd_efxfile.idEFXFile
import neo.Sound.snd_emitter.SoundFX
import neo.Sound.snd_system.openalSource_t
import neo.Sound.snd_cache.idSoundCache
import neo.Sound.snd_world.s_stats
import neo.sys.win_snd
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.ALCCapabilities
import org.lwjgl.openal.ALCapabilities
import org.lwjgl.openal.AL
import neo.Sound.snd_system.ListSounds_f
import neo.Sound.snd_system.ListSoundDecoders_f
import neo.Sound.snd_system.SoundReloadSounds_f
import neo.Sound.snd_system.TestSound_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_SoundName
import neo.Sound.snd_system.SoundSystemRestart_f
import neo.Sound.snd_emitter.SoundFX_Lowpass
import neo.Sound.snd_emitter.SoundFX_Comb
import neo.framework.File_h.idFile_Memory
import org.lwjgl.stb.STBVorbis
import neo.Sound.snd_decoder
import org.lwjgl.PointerBuffer
import neo.Sound.snd_efxfile.idSoundEffect
import neo.Sound.snd_emitter.FracTime
import neo.Sound.snd_emitter.SoundFX_LowpassFast
import neo.framework.File_h.fsOrigin_t
import org.lwjgl.stb.STBVorbisInfo
import neo.Tools.Compilers.AAS.Brush
import neo.Tools.Compilers.AAS.Brush.idBrushSide
import neo.Tools.Compilers.AAS.Brush.idBrush
import neo.idlib.containers.PlaneSet.idPlaneSet
import neo.Tools.Compilers.AAS.Brush.idBrushList
import neo.Tools.Compilers.AAS.Brush.idBrushMap
import neo.Tools.Compilers.AAS.AASBuild.Allowance
import neo.Tools.Compilers.AAS.AASFile.idReachability_Special
import neo.Tools.Compilers.AAS.AASFile_local.idAASFileLocal
import neo.Tools.Compilers.AAS.AASBuild_ledge.idLedge
import neo.Tools.Compilers.AAS.AASBuild_local.aasProcNode_s
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSP
import neo.Tools.Compilers.AAS.AASReach.idAASReach
import neo.Tools.Compilers.AAS.AASCluster.idAASCluster
import neo.Tools.Compilers.AAS.AASBuild.MergeAllowed
import neo.Tools.Compilers.AAS.AASBuild.ExpandedChopAllowed
import neo.Tools.Compilers.AAS.AASBuild.ExpandedMergeAllowed
import neo.Tools.Compilers.AAS.AASBuild
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSPNode
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSPPortal
import neo.Tools.Compilers.AAS.AASBuild.idAASBuild
import neo.Tools.Compilers.AAS.BrushBSP
import neo.Tools.Compilers.AAS.AASBuild_ledge
import neo.Tools.Compilers.AAS.AASBuild_File
import neo.Tools.Compilers.AAS.AASBuild_File.sizeEstimate_s
import neo.Tools.Compilers.AAS.AASBuild.RunAAS_f
import neo.Tools.Compilers.AAS.AASBuild.RunAASDir_f
import neo.Tools.Compilers.AAS.AASBuild.RunReach_f
import neo.Tools.Compilers.AAS.AASFile.idReachability_Fly
import neo.Tools.Compilers.AAS.AASReach
import neo.Tools.Compilers.AAS.AASFile.idReachability_Swim
import neo.Tools.Compilers.AAS.AASFile.idReachability_BarrierJump
import neo.Tools.Compilers.AAS.AASFile.idReachability_WaterJump
import neo.Tools.Compilers.AAS.AASFile.idReachability_WalkOffLedge
import neo.idlib.containers.VectorSet.idVectorSet
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSP.splitterStats_s
import neo.Tools.Compilers.AAS.AASFile_local.aasTraceStack_s
import neo.Tools.Compilers.AAS.AASFile_local
import neo.Tools.Compilers.AAS.AASFileManager.idAASFileManagerLocal
import neo.Tools.Compilers.DMap.dmap.uBrush_t
import neo.Tools.Compilers.DMap.dmap.uEntity_t
import neo.Tools.Compilers.DMap.map
import neo.Tools.Compilers.DMap.dmap
import neo.Tools.Compilers.DMap.dmap.side_s
import neo.Renderer.Material.materialCoverage_t
import neo.Tools.Compilers.DMap.dmap.primitive_s
import neo.Tools.Compilers.DMap.ubrush
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.Tools.Compilers.DMap.tritools
import neo.Tools.Compilers.DMap.dmap.mapLight_t
import neo.Renderer.tr_lightrun
import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.facebsp
import neo.Tools.Compilers.DMap.dmap.uArea_t
import neo.Tools.Compilers.DMap.dmap.dmapGlobals_t
import neo.Tools.Compilers.DMap.dmap.bspface_s
import neo.Tools.Compilers.DMap.portals
import neo.Tools.Compilers.DMap.leakfile
import neo.Tools.Compilers.DMap.usurface
import neo.Tools.Compilers.DMap.optimize
import neo.Tools.Compilers.DMap.tritjunction
import neo.Tools.Compilers.DMap.dmap.shadowOptLevel_t
import neo.Tools.Compilers.DMap.output
import neo.Tools.Compilers.DMap.dmap.bspbrush_s
import neo.Tools.Compilers.DMap.dmap.tree_s
import neo.Tools.Compilers.DMap.tritjunction.hashVert_s
import neo.Tools.Compilers.DMap.optimize.optVertex_s
import neo.Tools.Compilers.DMap.dmap.mesh_t
import neo.Tools.Compilers.DMap.dmap.parseMesh_s
import neo.Tools.Compilers.DMap.dmap.textureVectors_t
import neo.Tools.Compilers.DMap.dmap.drawSurfRef_s
import neo.Tools.Compilers.DMap.dmap.node_s
import neo.Tools.Compilers.DMap.dmap.uPortal_s
import neo.Renderer.tr_local.idRenderLightLocal
import neo.Tools.Compilers.DMap.dmap.Dmap_f
import org.lwjgl.opengl.GL11
import neo.Renderer.tr_backend
import neo.Renderer.qgl
import neo.Tools.Compilers.DMap.glfile
import neo.Renderer.tr_trisurf
import neo.Tools.Compilers.DMap.portals.interAreaPortal_t
import neo.Tools.Compilers.DMap.gldraw
import neo.Tools.Compilers.DMap.optimize.optEdge_s
import neo.Tools.Compilers.DMap.optimize.originalEdges_t
import neo.Tools.Compilers.DMap.optimize.optIsland_t
import neo.Tools.Compilers.DMap.optimize.edgeLength_t
import neo.Tools.Compilers.DMap.optimize.LengthSort
import neo.Tools.Compilers.DMap.optimize.optTri_s
import neo.Tools.Compilers.DMap.optimize.edgeCrossing_s
import neo.Tools.Compilers.DMap.shadowopt3
import neo.Tools.Compilers.DMap.shadowopt3.shadowOptEdge_s
import neo.Tools.Compilers.DMap.shadowopt3.silQuad_s
import neo.Tools.Compilers.DMap.shadowopt3.shadowTri_t
import neo.Renderer.tr_local.optimizedShadow_t
import neo.Tools.Compilers.DMap.shadowopt3.silPlane_t
import neo.Renderer.tr_stencilshadow
import neo.Renderer.tr_local.idRenderEntityLocal
import neo.Renderer.Interaction.srfCullInfo_t
import neo.Renderer.tr_stencilshadow.shadowGen_t
import neo.Renderer.Interaction
import neo.Tools.Compilers.DMap.optimize_gcc
import neo.Tools.Compilers.RoqVQ.Roq.roq
import neo.Tools.Compilers.RoqVQ.Codec.codec
import neo.Tools.Compilers.RoqVQ.NSBitmapImageRep
import neo.Tools.Compilers.RoqVQ.RoqParam.roqParam
import neo.Tools.Compilers.RoqVQ.QuadDefs
import neo.Tools.Compilers.RoqVQ.QuadDefs.quadcel
import neo.Tools.Compilers.RoqVQ.Roq.j_compress_ptr
import neo.Tools.Compilers.RoqVQ.Roq
import neo.Tools.Compilers.RoqVQ.Roq.RoQFileEncode_f
import neo.Tools.Compilers.RoqVQ.Codec
import neo.Tools.Compilers.RoqVQ.GDefs
import neo.Tools.Compilers.RoqVQ.RoqParam
import neo.Tools.Compilers.RenderBump.renderbump
import neo.Tools.Compilers.RenderBump.renderbump.triHash_t
import neo.Tools.Compilers.RenderBump.renderbump.binLink_t
import neo.Tools.Compilers.RenderBump.renderbump.triLink_t
import neo.Tools.Compilers.RenderBump.renderbump.renderBump_t
import neo.Renderer.Image_process
import neo.Tools.Compilers.RenderBump.renderbump.RenderBump_f
import neo.Tools.Compilers.RenderBump.renderbump.RenderBumpFlat_f
import org.lwjgl.opengl.ARBMultitexture
import org.lwjgl.opengl.ARBVertexBufferObject
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.ARBImaging
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.ARBTextureCompression
import org.lwjgl.opengl.ARBVertexShader
import org.lwjgl.opengl.ARBVertexProgram
import org.lwjgl.opengl.EXTDepthBoundsTest
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL43
import neo.Renderer.Image.idImageManager
import neo.Renderer.Image.ddsFilePixelFormat_t
import neo.Renderer.Image.ddsFileHeader_t
import neo.Renderer.Image.idImage
import neo.framework.FileSystem_h.backgroundDownload_s
import neo.Renderer.Image.cubeFiles_t
import neo.Renderer.Image.textureDepth_t
import neo.Renderer.Material.textureFilter_t
import neo.Renderer.Image.GeneratorFunction
import neo.Renderer.Material.textureRepeat_t
import neo.Renderer.Image.textureType_t
import neo.framework.FileSystem_h.dlType_t
import neo.Renderer.tr_local.tmu_t
import org.lwjgl.opengl.GL31
import neo.Renderer.Image_load
import org.lwjgl.opengl.EXTTextureCompressionS3TC
import org.lwjgl.opengl.EXTTextureFilterAnisotropic
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.EXTBGRA
import neo.Renderer.Image_program
import neo.Renderer.Image_init.R_DefaultImage
import neo.Renderer.Image_init.R_WhiteImage
import neo.Renderer.Image_init.R_BlackImage
import neo.Renderer.Image_init.R_BorderClampImage
import neo.Renderer.Image_init.R_FlatNormalImage
import neo.Renderer.Image_init.R_AmbientNormalImage
import neo.Renderer.Image_init.R_SpecularTableImage
import neo.Renderer.Image_init.R_Specular2DTableImage
import neo.Renderer.Image_init.R_RampImage
import neo.Renderer.Image_init.R_AlphaNotchImage
import neo.Renderer.Image_init.R_FogImage
import neo.Renderer.Image_init.R_FogEnterImage
import neo.Renderer.Image_init.makeNormalizeVectorCubeMap
import neo.Renderer.Image_init.R_CreateNoFalloffImage
import neo.Renderer.Image_init.R_QuadraticImage
import neo.Renderer.Image_init.R_RGBA8Image
import neo.Renderer.Image_init.R_ReloadImages_f
import neo.Renderer.Image_init.R_ListImages_f
import neo.Renderer.Image_init.R_CombineCubeImages_f
import org.lwjgl.opengl.EXTSharedTexturePalette
import neo.Renderer.Image.idImageManager.filterName_t
import neo.Renderer.Image_init
import neo.Renderer.Model.silEdge_t
import neo.Renderer.Model.lightingCache_s
import neo.Renderer.VertexCache.vertCache_s
import neo.Renderer.tr_local.viewDef_s
import neo.Renderer.tr_font
import neo.Renderer.tr_font.poor
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_main
import neo.Renderer.tr_local.frameData_t
import neo.Renderer.tr_local.frameMemoryBlock_s
import neo.Renderer.tr_local.viewEntity_s
import neo.Renderer.tr_local.viewLight_s
import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_main.R_QsortSurfaces
import neo.Renderer.tr_light
import neo.Renderer.tr_subview
import neo.Renderer.tr_render
import neo.Renderer.draw_arb.RB_ARB_DrawThreeTextureInteraction
import neo.Renderer.draw_arb.RB_ARB_DrawInteraction
import neo.Renderer.draw_common
import neo.Renderer.draw_arb
import neo.Renderer.tr_render.DrawInteraction
import neo.Renderer.tr_local.drawInteraction_t
import neo.Renderer.VertexCache
import org.lwjgl.opengl.ARBTextureEnvCombine
import org.lwjgl.opengl.ARBTextureEnvDot3
import neo.Renderer.Material.stageVertexColor_t
import neo.Renderer.GuiModel.guiModelSurface_t
import neo.Renderer.GuiModel.idGuiModel
import neo.Renderer.Material.expOpType_t
import neo.Renderer.Material.expOp_t
import neo.Renderer.Material.colorStage_t
import neo.Renderer.Cinematic.idCinematic
import neo.Renderer.Material.dynamicidImage_t
import neo.Renderer.Material.texgen_t
import neo.Renderer.Material.textureStage_t
import neo.Renderer.MegaTexture.idMegaTexture
import neo.Renderer.Material.stageLighting_t
import neo.Renderer.Material.newShaderStage_t
import neo.Renderer.Material.decalInfo_t
import neo.Renderer.Material.deform_t
import neo.Renderer.Material.mtrParsingData_s
import neo.Renderer.Material.expRegister_t
import neo.Renderer.draw_arb2
import org.lwjgl.opengl.ARBFragmentProgram
import neo.Renderer.Material.idMaterial.infoParm_t
import neo.Renderer.Model_ma.ma_t
import neo.Renderer.Model_ma
import neo.Renderer.Model_ma.maNodeHeader_t
import neo.Renderer.Model_ma.maAttribHeader_t
import neo.Renderer.Model_ma.maTransform_s
import neo.Renderer.Model_ma.maMesh_t
import neo.Renderer.Model_ma.maFace_t
import neo.Renderer.Model_ma.maObject_t
import neo.Renderer.Model_ma.maFileNode_t
import neo.Renderer.Model_ma.maMaterialNode_s
import neo.Renderer.Model_ma.maMaterial_t
import neo.Renderer.Model_ma.maModel_s
import neo.Renderer.tr_local.backEndName_t
import neo.Renderer.ModelOverlay.idRenderModelOverlay
import neo.Renderer.tr_deform
import neo.Renderer.tr_guisurf
import neo.Renderer.ModelDecal.idRenderModelDecal
import neo.Renderer.Interaction.idInteraction
import neo.Renderer.tr_local.areaReference_s
import neo.Renderer.tr_local.backEndState_t
import neo.Renderer.RenderSystem.glconfig_s
import neo.Renderer.tr_local.idRenderSystemLocal
import neo.Renderer.RenderWorld_local.portalArea_s
import neo.Renderer.tr_local.idRenderLight
import neo.Renderer.RenderWorld_local.doublePortal_s
import neo.Renderer.tr_local.shadowFrustum_t
import neo.Renderer.RenderWorld_local.idRenderWorldLocal
import neo.Renderer.tr_local.idRenderEntity
import neo.Renderer.tr_local.renderCommand_t
import neo.Renderer.tr_local.emptyCommand_t
import neo.Renderer.tr_local.glstate_t
import neo.Renderer.tr_local.backEndCounters_t
import neo.Renderer.tr_local.drawSurfsCommand_t
import neo.Renderer.tr_local.performanceCounters_t
import neo.Renderer.tr_local.renderCrop_t
import neo.Renderer.tr_rendertools
import neo.Renderer.tr_local.demoCommand_t
import neo.Renderer.tr_local.setBufferCommand_t
import neo.Renderer.MegaTexture
import neo.Renderer.tr_local.copyRenderCommand_t
import neo.Renderer.tr_local.localTrace_t
import neo.Renderer.tr_trace
import neo.Renderer.Cinematic
import neo.Renderer.Cinematic.cinStatus_t
import neo.Renderer.Cinematic.idCinematicLocal
import neo.Renderer.draw_arb2.progDef_t
import neo.Renderer.tr_local.program_t
import neo.Renderer.draw_arb2.RB_ARB2_DrawInteraction
import neo.Renderer.tr_local.programParameter_t
import neo.Renderer.draw_arb2.R_ReloadARBPrograms_f
import neo.Renderer.Model_ase.ase_t
import neo.Renderer.Model_ase.aseModel_s
import neo.Renderer.Model_ase
import neo.Renderer.Model_ase.aseObject_t
import neo.Renderer.Model_ase.aseMesh_t
import neo.Renderer.Model_ase.aseMaterial_t
import neo.Renderer.Model_ase.ASE
import neo.Renderer.Model_ase.ASE_KeyGEOMOBJECT
import neo.Renderer.Model_ase.ASE_KeyGROUP
import neo.Renderer.Model_ase.ASE_KeyMATERIAL_LIST
import neo.Renderer.Model_ase.aseFace_t
import neo.Renderer.Model_ase.ASE_KeyMAP_DIFFUSE
import neo.Renderer.Model_ase.ASE_KeyMATERIAL
import neo.Renderer.Model_ase.ASE_KeyNODE_TM
import neo.Renderer.Model_ase.ASE_KeyMESH_VERTEX_LIST
import neo.Renderer.Model_ase.ASE_KeyMESH_FACE_LIST
import neo.Renderer.Model_ase.ASE_KeyTFACE_LIST
import neo.Renderer.Model_ase.ASE_KeyCFACE_LIST
import neo.Renderer.Model_ase.ASE_KeyMESH_TVERTLIST
import neo.Renderer.Model_ase.ASE_KeyMESH_CVERTLIST
import neo.Renderer.Model_ase.ASE_KeyMESH_NORMALS
import neo.Renderer.Model_ase.ASE_KeyMESH
import neo.Renderer.Model_ase.ASE_KeyMESH_ANIMATION
import neo.Renderer.Model_lwo.lwClip
import neo.Renderer.Model_lwo.lwPlugin
import neo.Renderer.Model_lwo
import neo.Renderer.Model_lwo.lwFreeClip
import neo.Renderer.Model_lwo.lwEnvelope
import neo.Renderer.Model_lwo.lwKey
import neo.Renderer.Model_lwo.compare_keys
import neo.Renderer.Model_lwo.lwFreeEnvelope
import neo.Renderer.Model_lwo.LW
import neo.Renderer.Model_lwo.lwNode
import neo.Renderer.Model_lwo.lwObject
import neo.Renderer.Model_lwo.lwLayer
import neo.Renderer.Model_lwo.lwVMap
import neo.Renderer.Model_lwo.lwSurface
import neo.Renderer.Model_lwo.lwTexture
import neo.Renderer.Model_lwo.lwFreeSurface
import neo.Renderer.Model_lwo.lwPolygonList
import neo.Renderer.Model_lwo.lwPolygon
import neo.Renderer.Model_lwo.lwPointList
import neo.Renderer.Model_lwo.lwPoint
import neo.Renderer.Model_lwo.lwPolVert
import neo.Renderer.Model_lwo.lwTagList
import neo.Renderer.Model_lwo.lwTMap
import neo.Renderer.Model_lwo.lwGradKey
import neo.Renderer.Model_lwo.lwFreeTexture
import neo.Renderer.Model_lwo.lwFreePlugin
import neo.Renderer.Model_lwo.compare_textures
import neo.Renderer.Model_lwo.compare_shaders
import neo.Renderer.Model_lwo.lwFreeVMap
import neo.Renderer.Model_lwo.lwVMapPt
import neo.Renderer.Model_lwo.lwEParam
import neo.Renderer.Model_lwo.lwClipAnim
import neo.Renderer.Model_lwo.lwClipCycle
import neo.Renderer.Model_lwo.lwClipSeq
import neo.Renderer.Model_lwo.lwClipStill
import neo.Renderer.Model_lwo.lwClipXRef
import neo.Renderer.Model_lwo.lwVParam
import neo.Renderer.Model_lwo.lwGradient
import neo.Renderer.Model_lwo.lwImageMap
import neo.Renderer.Model_lwo.lwProcedural
import neo.Renderer.Model_lwo.lwTParam
import neo.Renderer.Model_lwo.lwCParam
import neo.Renderer.Model_lwo.lwLine
import neo.Renderer.Model_lwo.lwRMap
import neo.Renderer.Model_lwo.lwFree
import neo.Renderer.Model_lwo.lwFreeLayer
import neo.Renderer.Model_md3
import neo.Renderer.Model_md3.md3XyzNormal_t
import neo.Renderer.Model_md3.md3Shader_t
import neo.Renderer.Model_md3.md3Triangle_t
import neo.Renderer.Model_md3.md3St_t
import neo.Renderer.Model_md3.md3Frame_s
import neo.Renderer.Model_md3.md3Surface_s
import neo.Renderer.Model_md3.md3Tag_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.Model_md3.md3Header_s
import neo.Renderer.tr_local.deformInfo_s
import neo.Renderer.Model_md5.vertexWeight_s
import neo.Renderer.Model_md5
import neo.Renderer.Model_md5.idMD5Mesh
import neo.Renderer.Model_md5.idRenderModelMD5
import neo.Renderer.Model_prt
import neo.Renderer.tr_deform.eyeIsland_t
import neo.Renderer.tr_render.triFunc
import neo.Renderer.tr_render.RB_T_RenderTriangleSurface
import neo.Renderer.Image_init.imageClassificate_t
import neo.Renderer.Image_init.IMAGE_CLASSIFICATION
import neo.Renderer.Image_init.sortedImage_t
import neo.Renderer.Image_init.R_QsortImageSizes
import neo.Renderer.Image_init.R_AlphaRampImage
import neo.Renderer.Image_init.R_RGB8Image
import neo.Renderer.Model_beam
import neo.Renderer.ModelDecal.decalProjectionInfo_s
import neo.Renderer.ModelDecal
import java.util.NoSuchElementException
import neo.Renderer.tr_guisurf.R_ReloadGuis_f
import neo.Renderer.tr_guisurf.R_ListGuis_f
import neo.Renderer.tr_subview.orientation_t
import neo.Renderer.tr_trisurf.SilEdgeSort
import neo.Renderer.tr_trisurf.faceTangents_t
import neo.Renderer.tr_trisurf.tangentVert_t
import neo.Renderer.tr_trisurf.indexSort_t
import neo.Renderer.tr_trisurf.IndexSort
import neo.Renderer.tr_trisurf.R_ShowTriSurfMemory_f
import neo.Renderer.draw_common.RB_T_FillDepthBuffer
import neo.Renderer.draw_common.RB_T_Shadow
import neo.Renderer.draw_common.RB_T_BlendLight
import neo.Renderer.draw_common.RB_T_BasicFog
import neo.Renderer.Image_files.BMPHeader_t
import neo.Renderer.Image_files.pcx_t
import neo.Renderer.Image_files.TargaHeader
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.awt.image.DataBufferByte
import neo.Renderer.Interaction.surfaceInteraction_t
import neo.Renderer.Interaction.areaNumRef_s
import neo.Renderer.Interaction.idInteraction.frustumStates
import neo.Renderer.tr_shadowbounds
import neo.Renderer.Interaction.R_ShowInteractionMemory_f
import neo.Renderer.MegaTexture.fillColors
import neo.Renderer.MegaTexture.idTextureTile
import neo.Renderer.MegaTexture.megaTextureHeader_t
import neo.Renderer.MegaTexture.idTextureLevel
import neo.Renderer.MegaTexture.R_EmptyLevelImage
import neo.Renderer.MegaTexture._TargaHeader
import neo.Renderer.MegaTexture.idMegaTexture.MakeMegaTexture_f
import neo.Renderer.Model_local
import neo.Renderer.Model_local.idRenderModelStatic.matchVert_s
import neo.idlib.containers.VectorSet.idVectorSubset
import neo.TempDump.Atomics.renderEntityShadow
import neo.TempDump.Atomics.renderLightShadow
import neo.TempDump.Atomics.renderViewShadow
import neo.Renderer.RenderWorld.R_ListRenderLightDefs_f
import neo.Renderer.RenderWorld.R_ListRenderEntityDefs_f
import neo.Renderer.tr_polytope
import neo.Renderer.RenderWorld_local.portal_s
import neo.Renderer.tr_lightrun.R_ModulateLights_f
import neo.Renderer.tr_lightrun.R_RegenerateWorld_f
import neo.Renderer.VertexCache.idVertexCache
import neo.Renderer.VertexCache.vertBlockTag_t
import neo.Renderer.VertexCache.R_ListVertexCache_f
import neo.idlib.geometry.DrawVert
import neo.Renderer.Model_liquid
import neo.Renderer.Model_sprite
import neo.Renderer.ModelManager.idRenderModelManagerLocal
import neo.Renderer.ModelManager.idRenderModelManagerLocal.ListModels_f
import neo.Renderer.ModelManager.idRenderModelManagerLocal.PrintModel_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_ModelName
import neo.Renderer.ModelManager.idRenderModelManagerLocal.ReloadModels_f
import neo.Renderer.ModelManager.idRenderModelManagerLocal.TouchModel_f
import neo.Renderer.Model_beam.idRenderModelBeam
import neo.Renderer.Model_sprite.idRenderModelSprite
import neo.Renderer.Model_md3.idRenderModelMD3
import neo.Renderer.Model_prt.idRenderModelPrt
import neo.Renderer.ModelOverlay.overlayVertex_s
import neo.Renderer.ModelOverlay.overlaySurface_s
import neo.Renderer.ModelOverlay.overlayMaterial_s
import neo.Renderer.ModelOverlay
import neo.Renderer.tr_rendertools.debugLine_s
import neo.Renderer.tr_rendertools.debugPolygon_s
import neo.Renderer.tr_rendertools.debugText_s
import neo.Renderer.simplex
import neo.Renderer.tr_turboshadow
import neo.Renderer.tr_orderIndexes
import neo.Renderer.tr_orderIndexes.vertRef_s
import neo.Renderer.tr_shadowbounds.polyhedron
import neo.Renderer.tr_shadowbounds.poly
import neo.Renderer.tr_shadowbounds.MyArray
import neo.Renderer.tr_shadowbounds.edge
import neo.Renderer.tr_stencilshadow.indexRef_t
import neo.Renderer.RenderSystem_init.vidmode_s
import neo.Renderer.RenderSystem_init.R_SizeUp_f
import neo.Renderer.RenderSystem_init.R_SizeDown_f
import neo.Renderer.RenderSystem_init.R_TouchGui_f
import neo.Renderer.RenderSystem_init.R_ScreenShot_f
import neo.Renderer.RenderSystem_init.R_EnvShot_f
import neo.Renderer.RenderSystem_init.R_MakeAmbientMap_f
import neo.Renderer.RenderSystem_init.R_Benchmark_f
import neo.Renderer.RenderSystem_init.GfxInfo_f
import neo.Renderer.RenderSystem_init.R_TestImage_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_ImageName
import neo.Renderer.RenderSystem_init.R_TestVideo_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_VideoName
import neo.Renderer.RenderSystem_init.R_ReportSurfaceAreas_f
import neo.Renderer.RenderSystem_init.R_ReportImageDuplication_f
import neo.Renderer.RenderSystem_init.R_VidRestart_f
import neo.Renderer.RenderSystem_init.R_ListModes_f
import neo.Renderer.RenderSystem_init.R_ReloadSurface_f
import org.lwjgl.opengl.EXTStencilWrap
import neo.Renderer.RenderSystem_init.R_QsortSurfaceAreas
import neo.Renderer.RenderWorld_local.areaNode_t
import neo.Renderer.RenderWorld_local
import neo.Renderer.RenderWorld_portals.portalStack_s
import neo.Renderer.RenderWorld_portals
import neo.Renderer.RenderWorld_demo.demoHeader_t
import neo.framework.Async.MsgChannel
import neo.framework.Compressor.idCompressor
import neo.framework.Async.MsgChannel.idMsgQueue
import neo.sys.sys_public.idPort
import neo.framework.File_h.idFile_BitMsg
import neo.framework.Async.ServerScan.idServerScan
import neo.framework.Async.AsyncNetwork
import neo.framework.Async.ServerScan.networkServer_t
import neo.framework.Async.ServerScan.inServer_t
import neo.framework.Async.ServerScan.serverSort_t
import neo.framework.Async.ServerScan.scan_state_t
import neo.framework.Async.ServerScan
import neo.framework.Async.ServerScan.idServerScan.Cmp
import neo.framework.Async.MsgChannel.idMsgChannel
import neo.framework.Async.AsyncClient.clientState_t
import neo.framework.Async.AsyncClient.pakDlEntry_t
import neo.framework.FileSystem_h.dlMime_t
import neo.framework.Async.AsyncClient.clientUpdateState_t
import neo.framework.Async.AsyncClient.idAsyncClient.HandleGuiCommand
import neo.framework.Session.msgBoxType_t
import neo.framework.Async.AsyncNetwork.CLIENT_RELIABLE
import neo.framework.Async.AsyncClient
import neo.framework.Async.AsyncNetwork.CLIENT_UNRELIABLE
import neo.framework.Async.AsyncNetwork.SERVER_UNRELIABLE
import neo.framework.Async.AsyncNetwork.SERVER_RELIABLE
import neo.framework.Async.AsyncNetwork.SERVER_PRINT
import neo.framework.Async.AsyncClient.authKeyMsg_t
import neo.framework.Async.AsyncClient.authBadKeyStatus_t
import neo.framework.FileSystem_h.fsPureReply_t
import neo.framework.File_h.idFile_Permanent
import neo.framework.FileSystem_h.dlStatus_t
import neo.framework.Async.AsyncNetwork.SERVER_DL
import neo.framework.Async.AsyncNetwork.SERVER_PAK
import neo.framework.Session.HandleGuiCommand_t
import neo.framework.Async.AsyncServer.authReply_t
import neo.framework.Async.AsyncServer.authReplyMsg_t
import neo.framework.Async.AsyncServer.authState_t
import neo.framework.Async.AsyncServer.serverClientState_t
import neo.framework.Async.AsyncServer.idAsyncServer
import neo.framework.Async.AsyncServer.challenge_s
import neo.framework.Async.AsyncServer
import neo.framework.Async.AsyncServer.serverClient_s
import neo.framework.FileSystem_h.findFile_t
import neo.framework.Async.AsyncServer.RConRedirect
import neo.framework.Async.AsyncClient.idAsyncClient
import neo.framework.Async.AsyncNetwork.master_s
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.SpawnServer_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.Connect_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.Reconnect_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.GetServerInfo_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.GetLANServers_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.ListServers_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.RemoteConsole_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.Heartbeat_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.Kick_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.CheckNewVersion_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.UpdateUI_f
import neo.framework.UsercmdGen.inhibit_t
import neo.framework.Unzip.tm_unz
import neo.framework.Common.version_s
import kotlin.jvm.Volatile
import neo.framework.Common.idCommonLocal
import neo.framework.Common.ListHash
import neo.idlib.LangDict.idLangDict
import neo.framework.Common.idCommonLocal.asyncStats_t
import neo.framework.Common.errorParm_t
import neo.framework.Common.Com_ExecMachineSpec_f
import neo.framework.Common.Com_Error_f
import neo.framework.Common.Com_Crash_f
import neo.framework.Common.Com_Freeze_f
import neo.framework.Common.Com_Quit_f
import neo.framework.Common.Com_WriteConfig_f
import neo.framework.Common.Com_ReloadEngine_f
import neo.framework.Common.Com_SetMachineSpec_f
import neo.framework.Common.Com_Editor_f
import neo.framework.Common.Com_EditLights_f
import neo.framework.Common.Com_EditSounds_f
import neo.framework.Common.Com_EditDecls_f
import neo.framework.Common.Com_EditAFs_f
import neo.framework.Common.Com_EditParticles_f
import neo.framework.Common.Com_EditScripts_f
import neo.framework.Common.Com_EditGUIs_f
import neo.framework.Common.Com_EditPDAs_f
import neo.framework.Common.Com_ScriptDebugger_f
import neo.framework.Common.Com_MaterialEditor_f
import neo.framework.Common.Com_LocalizeGuis_f
import neo.framework.Common.Com_LocalizeMaps_f
import neo.framework.Common.Com_ReloadLanguage_f
import neo.framework.Common.Com_LocalizeGuiParmsTest_f
import neo.framework.Common.Com_LocalizeMapsTest_f
import neo.framework.Common.Com_StartBuild_f
import neo.framework.Common.Com_FinishBuild_f
import neo.framework.Common.Com_Help_f
import neo.framework.DeclAF.idAFVector
import neo.framework.DeclAF.idAFVector.type
import neo.framework.File_h
import neo.idlib.containers.CLong
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import neo.framework.File_h.idFile_InZip
import neo.framework.Console.idConsoleLocal
import neo.framework.Console.idConsole
import neo.framework.Console.Con_Clear_f
import neo.framework.Console.Con_Dump_f
import neo.framework.Session.Session_RescanSI_f
import neo.framework.Session.Session_Map_f
import neo.framework.Session.Session_DevMap_f
import neo.framework.Session.Session_TestMap_f
import neo.framework.Session.Sess_WritePrecache_f
import neo.framework.Session.Session_PromptKey_f
import neo.framework.Session.Session_DemoShot_f
import neo.framework.Session.Session_RecordDemo_f
import neo.framework.Session.Session_CompressDemo_f
import neo.framework.Session.Session_StopRecordingDemo_f
import neo.framework.Session.Session_PlayDemo_f
import neo.framework.Session.Session_TimeDemo_f
import neo.framework.Session_local.timeDemo_t
import neo.framework.Session.Session_TimeDemoQuit_f
import neo.framework.Session.Session_AVIDemo_f
import neo.framework.Session.Session_AVIGame_f
import neo.framework.Session.Session_AVICmdDemo_f
import neo.framework.Session.Session_WriteCmdDemo_f
import neo.framework.Session.Session_PlayCmdDemo_f
import neo.framework.Session.Session_TimeCmdDemo_f
import neo.framework.Session.Session_Disconnect_f
import neo.framework.Session.Session_EndOfDemo_f
import neo.framework.Session.Session_ExitCmdDemo_f
import neo.framework.Session.Session_TestGUI_f
import neo.framework.Session.LoadGame_f
import neo.framework.Session.SaveGame_f
import neo.framework.Session.TakeViewNotes_f
import neo.framework.Session.TakeViewNotes2_f
import neo.framework.Session.Session_Hitch_f
import neo.framework.Session_local.idSessionLocal
import neo.framework.Session.idSession
import neo.framework.DeclSkin.skinMapping_t
import neo.framework.DemoFile
import neo.framework.KeyInput.keyname_t
import neo.framework.KeyInput.idKey
import neo.framework.KeyInput.Key_Bind_f
import neo.framework.KeyInput.idKeyInput.ArgCompletion_KeyName
import neo.framework.KeyInput.Key_BindUnBindTwo_f
import neo.framework.KeyInput.Key_Unbind_f
import neo.framework.KeyInput.Key_Unbindall_f
import neo.framework.KeyInput.Key_ListBinds_f
import neo.framework.CmdSystem.idCmdSystemLocal
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Boolean
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_FileName
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_ConfigName
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_SaveGame
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_DemoName
import neo.framework.CmdSystem.idCmdSystemLocal.SystemList_f
import neo.framework.CmdSystem.idCmdSystemLocal.RendererList_f
import neo.framework.CmdSystem.idCmdSystemLocal.SoundList_f
import neo.framework.CmdSystem.idCmdSystemLocal.GameList_f
import neo.framework.CmdSystem.idCmdSystemLocal.ToolList_f
import neo.framework.CmdSystem.idCmdSystemLocal.Exec_f
import neo.framework.CmdSystem.idCmdSystemLocal.Vstr_f
import neo.framework.CmdSystem.idCmdSystemLocal.Echo_f
import neo.framework.CmdSystem.idCmdSystemLocal.Parse_f
import neo.framework.CmdSystem.idCmdSystemLocal.Wait_f
import neo.framework.EditField.autoComplete_s
import neo.framework.EditField
import neo.framework.EditField.FindMatches
import neo.framework.EditField.FindIndexMatch
import neo.framework.EditField.PrintMatches
import neo.framework.EditField.PrintCvarMatches
import neo.framework.EventLoop.idEventLoop
import neo.framework.Compressor
import neo.framework.Compressor.idCompressor_None
import neo.framework.Compressor.idCompressor_BitStream
import neo.framework.Compressor.idCompressor_RunLength
import neo.framework.Compressor.idCompressor_RunLength_ZeroBased
import neo.framework.Compressor.idCompressor_Huffman
import neo.framework.Compressor.idCompressor_Arithmetic
import neo.framework.Compressor.idCompressor_LZSS
import neo.framework.Compressor.idCompressor_LZSS_WordAligned
import neo.framework.Compressor.idCompressor_LZW
import neo.framework.Compressor.huffmanNode_t
import neo.framework.Compressor.nodetype
import neo.framework.Compressor.idCompressor_Arithmetic.acProbs_t
import neo.framework.Compressor.idCompressor_Arithmetic.acSymbol_t
import neo.framework.Compressor.idCompressor_Arithmetic.acProbs_s
import neo.framework.Compressor.idCompressor_Arithmetic.acSymbol_s
import neo.framework.Compressor.idCompressor_LZW.dictionary
import neo.framework.CVarSystem.idCVarSystemLocal
import neo.framework.CVarSystem.idCVarSystemLocal.Toggle_f
import neo.framework.CVarSystem.idCVarSystemLocal.Set_f
import neo.framework.CVarSystem.idCVarSystemLocal.SetS_f
import neo.framework.CVarSystem.idCVarSystemLocal.SetU_f
import neo.framework.CVarSystem.idCVarSystemLocal.SetT_f
import neo.framework.CVarSystem.idCVarSystemLocal.SetA_f
import neo.framework.CVarSystem.idCVarSystemLocal.Reset_f
import neo.framework.CVarSystem.idCVarSystemLocal.Restart_f
import neo.framework.CVarSystem.idCVarSystemLocal.show
import neo.framework.FileSystem_h.idFileSystemLocal
import neo.framework.UsercmdGen.idUsercmdGenLocal
import neo.framework.UsercmdGen.userCmdString_t
import neo.framework.UsercmdGen.usercmdButton_t
import neo.framework.UsercmdGen.idUsercmdGen
import neo.framework.UsercmdGen.idUsercmdGenLocal.KeyboardCallback
import neo.framework.UsercmdGen.idUsercmdGenLocal.MouseButtonCallback
import neo.framework.UsercmdGen.idUsercmdGenLocal.MouseCursorCallback
import neo.framework.UsercmdGen.idUsercmdGenLocal.MouseScrollCallback
import neo.sys.sys_public.joystickAxis_t
import neo.framework.UsercmdGen.buttonState_t
import org.lwjgl.glfw.GLFWCursorPosCallback
import org.lwjgl.glfw.GLFWScrollCallback
import org.lwjgl.glfw.GLFWMouseButtonCallback
import org.lwjgl.glfw.GLFWKeyCallback
import neo.framework.DeclManager.huffmanCode_s
import neo.framework.DeclManager.huffmanNode_s
import neo.framework.DeclManager.idDeclManagerLocal
import java.lang.NoSuchMethodException
import java.lang.SecurityException
import neo.framework.DeclManager.idDeclBase
import neo.framework.DeclManager.idDeclLocal
import neo.framework.DeclManager.idDeclFile
import java.lang.IllegalArgumentException
import java.lang.reflect.InvocationTargetException
import neo.framework.DeclManager.idDeclType
import neo.framework.DeclManager.idDeclFolder
import neo.framework.DeclManager.idDeclManagerLocal.ListDecls_f
import neo.framework.DeclManager.idDeclManagerLocal.ReloadDecls_f
import neo.framework.DeclManager.idDeclManagerLocal.TouchDecl_f
import neo.framework.DeclManager.ListHuffmanFrequencies_f
import neo.framework.DeclParticle.ParticleParmDesc
import neo.framework.DeclParticle
import neo.framework.DeclParticle.idParticleParm
import neo.framework.DeclParticle.prtCustomPth_t
import neo.framework.DeclParticle.prtDirection_t
import neo.framework.DeclParticle.prtDistribution_t
import neo.framework.DeclParticle.prtOrientation_t
import neo.framework.FileSystem_h.pureExclusion_s
import neo.framework.FileSystem_h.excludeExtension
import neo.framework.FileSystem_h.excludePathPrefixAndExtension
import neo.framework.FileSystem_h.excludeFullName
import neo.framework.FileSystem_h.idInitExclusions
import neo.framework.FileSystem_h.urlDownload_s
import neo.framework.FileSystem_h.fileDownload_s
import neo.framework.FileSystem_h.idModList
import neo.framework.FileSystem_h.pureExclusionFunc_t
import neo.framework.FileSystem_h.fileInPack_s
import neo.framework.FileSystem_h.addonInfo_t
import neo.framework.FileSystem_h.binaryStatus_t
import neo.framework.FileSystem_h.pureStatus_t
import neo.framework.FileSystem_h.directory_t
import neo.framework.FileSystem_h.searchpath_s
import neo.framework.FileSystem_h.pack_t
import neo.framework.FileSystem_h.idDEntry
import neo.framework.FileSystem_h.idFileSystemLocal.BackgroundDownloadThread
import java.nio.file.InvalidPathException
import java.util.UUID
import java.nio.file.Files
import java.nio.file.LinkOption
import neo.framework.FileSystem_h.idFileSystemLocal.Dir_f
import neo.framework.FileSystem_h.idFileSystemLocal.DirTree_f
import neo.framework.FileSystem_h.idFileSystemLocal.Path_f
import neo.framework.FileSystem_h.idFileSystemLocal.TouchFile_f
import neo.framework.FileSystem_h.idFileSystemLocal.TouchFileList_f
import neo.framework.DemoChecksum
import neo.framework.Session_local.fileTIME_T
import neo.framework.Session_local.logCmd_t
import neo.framework.Session_local.mapSpawnData_t
import neo.framework.Session_local.idSessionLocal.cdKeyState_t
import neo.framework.Session_local
import neo.framework.Session_menu.idListSaveGameCompare
import java.util.stream.IntStream
import java.util.function.IntUnaryOperator
import java.nio.file.StandardOpenOption
import java.util.HashSet
import java.lang.StackTraceElement
import java.lang.NoSuchFieldException
import javax.swing.undo.CannotUndoException
import org.junit.Before

/**
 *
 */
object snd_system {
    var soundSystemLocal: idSoundSystemLocal? = idSoundSystemLocal()
    var soundSystem: idSoundSystem? = snd_system.soundSystemLocal
    fun setSoundSystem(soundSystem: idSoundSystem?) {
        snd_system.soundSystemLocal = soundSystem
        snd_system.soundSystem = snd_system.soundSystemLocal
    }

    /*
     ===================================================================================

     idSoundSystemLocal

     ===================================================================================
     */
    internal class openalSource_t {
        var chan: idSoundChannel? = null
        var   /*ALuint*/handle = 0
        var inUse = false
        var looping = false
        var startTime = 0
        var stereo = false
    }

    class idSoundSystemLocal     //        static {
    //            if (ID_OPENAL) {//TODO: turn on the rest of our openAL extensions.
    //                // off by default. OpenAL DLL gets loaded on-demand. EDIT: not anymore.
    //                //s_libOpenAL = new idCVar("s_libOpenAL", "openal32.dll", CVAR_SOUND | CVAR_ARCHIVE, "OpenAL DLL name/path");
    //                s_useOpenAL = new idCVar("s_useOpenAL", "1", CVAR_SOUND | CVAR_BOOL | CVAR_ARCHIVE, "use OpenAL");
    //                s_useEAXReverb = new idCVar("s_useEAXReverb", "1", CVAR_SOUND | CVAR_BOOL | CVAR_ARCHIVE, "use EAX reverb");
    //                s_muteEAXReverb = new idCVar("s_muteEAXReverb", "0", CVAR_SOUND | CVAR_BOOL, "mute eax reverb");
    //                s_decompressionLimit = new idCVar("s_decompressionLimit", "6", CVAR_SOUND | CVAR_INTEGER | CVAR_ARCHIVE, "specifies maximum uncompressed sample length in seconds");
    //            } else {
    //                s_libOpenAL = new idCVar("s_libOpenAL", "openal32.dll", CVAR_SOUND | CVAR_ARCHIVE, "OpenAL is not supported in this build");
    //                s_useOpenAL = new idCVar("s_useOpenAL", "0", CVAR_SOUND | CVAR_BOOL | CVAR_ROM, "OpenAL is not supported in this build");
    //                s_useEAXReverb = new idCVar("s_useEAXReverb", "0", CVAR_SOUND | CVAR_BOOL | CVAR_ROM, "EAX not available in this build");
    //                s_muteEAXReverb = new idCVar("s_muteEAXReverb", "0", CVAR_SOUND | CVAR_BOOL | CVAR_ROM, "mute eax reverb");
    //                s_decompressionLimit = new idCVar("s_decompressionLimit", "6", CVAR_SOUND | CVAR_INTEGER | CVAR_ROM, "specifies maximum uncompressed sample length in seconds");
    //            }
    //        }
        : idSoundSystem() {
        companion object {
            val s_clipVolumes: idCVar? = idCVar("s_clipVolumes", "1", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_constantAmplitude: idCVar? =
                idCVar("s_constantAmplitude", "-1", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_decompressionLimit: idCVar? = idCVar(
                "s_decompressionLimit",
                "6",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
                "specifies maximum uncompressed sample length in seconds"
            )
            val s_doorDistanceAdd: idCVar? = idCVar(
                "s_doorDistanceAdd",
                "150",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "reduce sound volume with this distance when going through a door"
            )
            val s_dotbias2: idCVar? = idCVar("s_dotbias2", "1.1", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_dotbias6: idCVar? = idCVar("s_dotbias6", "0.8", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_drawSounds: idCVar? = idCVar(
                "s_drawSounds",
                "0",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_INTEGER,
                "",
                0,
                2,
                ArgCompletion_Integer(0, 2)
            )
            val s_enviroSuitCutoffFreq: idCVar? =
                idCVar("s_enviroSuitCutoffFreq", "2000", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_enviroSuitCutoffQ: idCVar? =
                idCVar("s_enviroSuitCutoffQ", "2", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_enviroSuitVolumeScale: idCVar? =
                idCVar("s_enviroSuitVolumeScale", "0.9", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_force22kHz: idCVar? = idCVar("s_force22kHz", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_globalFraction: idCVar? = idCVar(
                "s_globalFraction",
                "0.8",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "volume to all speakers when not spatialized"
            )

            //
            val s_libOpenAL: idCVar? = idCVar(
                "s_libOpenAL",
                "openal32.dll",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE,
                "OpenAL DLL name/path"
            )
            val s_maxSoundsPerShader: idCVar? = idCVar(
                "s_maxSoundsPerShader",
                "0",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE,
                "",
                0,
                10,
                ArgCompletion_Integer(0, 10)
            )
            val s_meterTopTime: idCVar? = idCVar(
                "s_meterTopTime",
                "2000",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
                ""
            )
            val s_minVolume2: idCVar? =
                idCVar("s_minVolume2", "0.25", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_minVolume6: idCVar? = idCVar("s_minVolume6", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_muteEAXReverb: idCVar? =
                idCVar("s_muteEAXReverb", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "mute eax reverb")

            //
            //
            val s_noSound: idCVar? = null
            val s_numberOfSpeakers: idCVar? = idCVar(
                "s_numberOfSpeakers",
                "2",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE,
                "number of speakers"
            )
            val s_playDefaultSound: idCVar? = idCVar(
                "s_playDefaultSound",
                "1",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
                "play a beep for missing sounds"
            )
            val s_quadraticFalloff: idCVar? =
                idCVar("s_quadraticFalloff", "1", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_realTimeDecoding: idCVar? = idCVar(
                "s_realTimeDecoding",
                "1",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_INIT,
                ""
            )
            val s_reverbFeedback: idCVar? =
                idCVar("s_reverbFeedback", "0.333", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_reverbTime: idCVar? =
                idCVar("s_reverbTime", "1000", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_reverse: idCVar? =
                idCVar("s_reverse", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL, "")
            val s_showLevelMeter: idCVar? =
                idCVar("s_showLevelMeter", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_showStartSound: idCVar? =
                idCVar("s_showStartSound", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_singleEmitter: idCVar? = idCVar(
                "s_singleEmitter",
                "0",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_INTEGER,
                "mute all sounds but this emitter"
            )
            val s_skipHelltimeFX: idCVar? =
                idCVar("s_skipHelltimeFX", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")

            //
            val s_slowAttenuate: idCVar? = idCVar(
                "s_slowAttenuate",
                "1",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL,
                "slowmo sounds attenuate over shorted distance"
            )
            val s_spatializationDecay: idCVar? = idCVar(
                "s_spatializationDecay",
                "2",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                ""
            )
            val s_subFraction: idCVar? = idCVar(
                "s_subFraction",
                "0.75",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "volume to subwoofer in 5.1"
            )
            val s_useEAXReverb: idCVar? = idCVar(
                "s_useEAXReverb",
                "1",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ARCHIVE,
                "use EAX reverb"
            )
            val s_useOcclusion: idCVar? =
                idCVar("s_useOcclusion", "1", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_useOpenAL: idCVar? = idCVar(
                "s_useOpenAL",
                "1",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ARCHIVE,
                "use OpenAL"
            )
            val s_volume: idCVar? = idCVar(
                "s_volume_dB",
                "0",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "volume in dB"
            )

            // mark available during initialization, or through an explicit test
            var EAXAvailable = -1
            var useEAXReverb = true

            // latches
            var useOpenAL = true

            init {
                if (BuildDefines.ID_DEDICATED) {
                    s_noSound = idCVar(
                        "s_noSound",
                        "1",
                        CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ROM,
                        ""
                    )
                } else {
                    s_noSound = idCVar(
                        "s_noSound",
                        "0",
                        CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_NOCHEAT,
                        ""
                    )
                }
            }
        }

        var CurrentSoundTime // set by the async thread and only used by the main thread
                = 0

        //        public boolean alEAXSet;
        //        public boolean alEAXGet;
        //        public boolean alEAXSetBufferMode;
        //        public boolean alEAXGetBufferMode;
        var EFXDatabase: idEFXFile? = idEFXFile()
        var buffers // statistics
                = 0

        //
        var currentSoundWorld // the one to mix each async tic
                : idSoundWorldLocal? = null
        var efxloaded = false
        var finalMixBuffer // points inside realAccum at a 16 byte aligned boundary
                : FloatArray?

        //
        val fxList: idList<SoundFX?>? = idList()

        //
        var graph: IntArray?

        //
        var isInitialized = false

        //
        var meterTops: IntArray? = IntArray(256)
        var meterTopsTime: IntArray? = IntArray(256)
        var muted = false

        //
        /*unsigned*/  var nextWriteBlock = 0

        //
        var olddwCurrentWritePos // statistics
                = 0
        var openalContext: Long = 0

        //
        var openalDevice: Long = 0
        var   /*ALsizei*/openalSourceCount = 0
        var openalSources: Array<openalSource_t?>? = arrayOfNulls<openalSource_t?>(256)

        //
        var realAccum: FloatArray? = FloatArray(6 * Simd.MIXBUFFER_SAMPLES + 16)
        var shutdown = false
        var snd_audio_hw: idAudioHardware? = null
        var soundCache: idSoundCache? = null

        //
        var soundStats: s_stats? = s_stats() // NOTE: updated throughout the code, not displayed anywhere

        //
        var volumesDB: FloatArray? = FloatArray(1200) // dB to float volume conversion

        // all non-hardware initialization
        /*
         ===============
         idSoundSystemLocal::Init

         initialize the sound system
         ===============
         */
        override fun Init() {
            Common.common.Printf("----- Initializing Sound System ------\n")
            isInitialized = false
            muted = false
            shutdown = false
            currentSoundWorld = null
            soundCache = null
            olddwCurrentWritePos = 0
            buffers = 0
            CurrentSoundTime = 0
            nextWriteBlock = -0x1
            meterTops = IntArray(meterTops.size)
            meterTopsTime = IntArray(meterTopsTime.size)
            for (i in -600..599) {
                val pt = i * 0.1f
                volumesDB.get(i + 600) = Math.pow(2.0, (pt * (1.0f / 6.0f)).toDouble()).toFloat()
            }

            // make a 16 byte aligned finalMixBuffer
            finalMixBuffer = realAccum //(float[]) ((((int) realAccum) + 15) & ~15);
            graph = null
            if (!s_noSound.GetBool()) {
                idSampleDecoder.Companion.Init()
                soundCache = idSoundCache()
            }

            // set up openal device and context
            Common.common.StartupVariable("s_useOpenAL", true)
            Common.common.StartupVariable("s_useEAXReverb", true)
            if (s_useOpenAL.GetBool() && s_useEAXReverb.GetBool()) {
                if (!win_snd.Sys_LoadOpenAL()) {
                    s_useOpenAL.SetBool(false)
                } else {
                    Common.common.Printf("Setup OpenAL device and context... ")
                    openalDevice = ALC10.alcOpenDevice(null as ByteBuffer?)
                    openalContext = ALC10.alcCreateContext(openalDevice, null as IntArray?)
                    ALC10.alcMakeContextCurrent(openalContext)
                    val alcCapabilities = ALC.createCapabilities(openalDevice)
                    val alCapabilities = AL.createCapabilities(alcCapabilities)
                    Common.common.Printf("Done.\n")

                    // try to obtain EAX extensions
                    if (s_useEAXReverb.GetBool() && AL10.alIsExtensionPresent( /*ID_ALCHAR*/"EAX4.0")) {
                        s_useOpenAL.SetBool(true) // EAX presence causes AL enable
                        //                        alEAXSet = true;//(EAXSet) alGetProcAddress(/*ID_ALCHAR*/"EAXSet");
//                        alEAXGet = true;//(EAXGet) alGetProcAddress(/*ID_ALCHAR*/"EAXGet");
                        Common.common.Printf("OpenAL: found EAX 4.0 extension\n")
                    } else {
                        Common.common.Printf("OpenAL: EAX 4.0 extension not found\n")
                        s_useEAXReverb.SetBool(false)
                        //                        alEAXSet = false;//(EAXSet) null;
//                        alEAXGet = false;//(EAXGet) null;
                    }

                    // try to obtain EAX-RAM extension - not required for operation
//                    if (alIsExtensionPresent(/*ID_ALCHAR*/"EAX-RAM")) {
//                        alEAXSetBufferMode = true;//(EAXSetBufferMode) alGetProcAddress(/*ID_ALCHAR*/"EAXSetBufferMode");
//                        alEAXGetBufferMode = true;//(EAXGetBufferMode) alGetProcAddress(/*ID_ALCHAR*/"EAXGetBufferMode");
//                        common.Printf("OpenAL: found EAX-RAM extension, %dkB\\%dkB\n", alGetInteger(alGetEnumValue(/*ID_ALCHAR*/"AL_EAX_RAM_FREE")) / 1024, alGetInteger(alGetEnumValue(/*ID_ALCHAR*/"AL_EAX_RAM_SIZE")) / 1024);
//                    } else {
//                        alEAXSetBufferMode = false;//(EAXSetBufferMode) null;
//                        alEAXGetBufferMode = false;//(EAXGetBufferMode) null;
//                        common.Printf("OpenAL: no EAX-RAM extension\n");
//                    }
                    if (!s_useOpenAL.GetBool()) {
                        Common.common.Printf("OpenAL: disabling ( no EAX ). Using legacy mixer.\n")
                        ALC10.alcMakeContextCurrent(openalContext)
                        ALC10.alcDestroyContext(openalContext)
                        openalContext = 0
                        ALC10.alcCloseDevice(openalDevice)
                        openalDevice = 0
                    } else {
                        var   /*ALuint*/handle: Int
                        openalSourceCount = 0
                        while (openalSourceCount < 256) {
                            AL10.alGetError()
                            handle = AL10.alGenSources() //alGenSources(1, handle);
                            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                                break
                            } else {
                                // store in source array
                                openalSources.get(openalSourceCount) = openalSource_t()
                                openalSources.get(openalSourceCount).handle = handle
                                openalSources.get(openalSourceCount).startTime = 0
                                openalSources.get(openalSourceCount).chan = null
                                openalSources.get(openalSourceCount).inUse = false
                                openalSources.get(openalSourceCount).looping = false

                                // initialise sources
                                AL10.alSourcef(handle, AL10.AL_ROLLOFF_FACTOR, 0.0f)

                                // found one source
                                openalSourceCount++
                            }
                        }
                        Common.common.Printf(
                            "OpenAL: found %s\n",
                            ALC10.alcGetString(openalDevice, ALC10.ALC_DEVICE_SPECIFIER)
                        )
                        Common.common.Printf("OpenAL: found %d hardware voices\n", openalSourceCount)

                        // adjust source count to allow for at least eight stereo sounds to play
                        openalSourceCount -= 8
                        EAXAvailable = 1
                    }
                }
            }
            useOpenAL = s_useOpenAL.GetBool()
            useEAXReverb = s_useEAXReverb.GetBool()
            CmdSystem.cmdSystem.AddCommand(
                "listSounds",
                ListSounds_f.INSTANCE,
                CmdSystem.CMD_FL_SOUND,
                "lists all sounds"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listSoundDecoders",
                ListSoundDecoders_f.INSTANCE,
                CmdSystem.CMD_FL_SOUND,
                "list active sound decoders"
            )
            CmdSystem.cmdSystem.AddCommand(
                "reloadSounds",
                SoundReloadSounds_f.INSTANCE,
                CmdSystem.CMD_FL_SOUND or CmdSystem.CMD_FL_CHEAT,
                "reloads all sounds"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testSound",
                TestSound_f.INSTANCE,
                CmdSystem.CMD_FL_SOUND or CmdSystem.CMD_FL_CHEAT,
                "tests a sound",
                ArgCompletion_SoundName.Companion.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "s_restart",
                SoundSystemRestart_f.INSTANCE,
                CmdSystem.CMD_FL_SOUND,
                "restarts the sound system"
            )
            Common.common.Printf("sound system initialized.\n")
            Common.common.Printf("--------------------------------------\n")
        }

        // shutdown routine
        override fun Shutdown() {
            ShutdownHW()

            // EAX or not, the list needs to be cleared
            EFXDatabase.Clear()

            // destroy openal sources
            if (useOpenAL) {
                efxloaded = false

                // adjust source count back up to allow for freeing of all resources
                openalSourceCount += 8
                for ( /*ALsizei*/i in openalSources.indices) {
                    // stop source
                    if (openalSources.get(i) != null) {
                        AL10.alSourceStop(openalSources.get(i).handle)
                        AL10.alSourcei(openalSources.get(i).handle, AL10.AL_BUFFER, 0)
                        AL10.alDeleteSources(openalSources.get(i).handle)

                        // clear entry in source array
                        openalSources.get(i).handle = 0
                        openalSources.get(i).startTime = 0
                        openalSources.get(i).chan = null
                        openalSources.get(i).inUse = false
                        openalSources.get(i).looping = false
                    }
                }
            }

            // destroy all the sounds (hardware buffers as well)
//	delete soundCache;
            soundCache = null

            // destroy openal device and context
            if (useOpenAL) {
                ALC10.alcMakeContextCurrent(openalContext)
                ALC10.alcDestroyContext(openalContext)
                openalContext = 0
                ALC10.alcCloseDevice(openalDevice)
                openalDevice = 0
            }
            win_snd.Sys_FreeOpenAL()
            idSampleDecoder.Companion.Shutdown()
        }

        override fun ClearBuffer() {

            // check to make sure hardware actually exists
            if (TempDump.NOT(snd_audio_hw)) {
                return
            }
            val fBlock = shortArrayOf(0)
            val   /*ulong*/fBlockLen: Long = 0

            //TODO:see what this block does.
//            if (!snd_audio_hw.Lock( /*(void **)*/fBlock, fBlockLen)) {
//                return;
//            }
            if (fBlock[0] != 0) {
//                SIMDProcessor.Memset(fBlock, 0, fBlockLen);
                Arrays.fill(fBlock, 0, fBlockLen.toInt(), 0.toByte().toShort())
                //                snd_audio_hw.Unlock(fBlock, fBlockLen);
            }
        }

        // sound is attached to the window, and must be recreated when the window is changed
        override fun ShutdownHW(): Boolean {
            if (!isInitialized) {
                return false
            }
            shutdown = true // don't do anything at AsyncUpdate() time
            win_main.Sys_Sleep(100) // sleep long enough to make sure any async sound talking to hardware has returned
            Common.common.Printf("Shutting down sound hardware\n")

//	delete snd_audio_hw;
            snd_audio_hw = null
            isInitialized = false
            if (graph != null) {
//                Mem_Free(graph);//TODO:remove all this memory crap.
                graph = null
            }
            return true
        }

        override fun InitHW(): Boolean {
            if (s_noSound.GetBool()) {
                return false
            }

//	delete snd_audio_hw;
            snd_audio_hw = idAudioHardware.Companion.Alloc()
            if (snd_audio_hw == null) {
                return false
            }
            if (!useOpenAL) {
                if (!snd_audio_hw.Initialize()) {
                    snd_audio_hw = null
                    return false
                }
                if (snd_audio_hw.GetNumberOfSpeakers() == 0) {
                    return false
                }
                s_numberOfSpeakers.SetInteger(snd_audio_hw.GetNumberOfSpeakers())
            }
            isInitialized = true
            shutdown = false
            return true
        }

        /*
         ===================
         idSoundSystemLocal::AsyncUpdate
         called from async sound thread when com_asyncSound == 1 ( Windows )
         ===================
         */
        // async loop, called at 60Hz
        override fun AsyncUpdate(time: Int): Int {
            if (!isInitialized || shutdown || TempDump.NOT(snd_audio_hw)) {
                return 0
            }
            var   /*ulong*/dwCurrentWritePos: Long = 0
            val   /*dword*/dwCurrentBlock: Int

            // If not using openal, get actual playback position from sound hardware
            if (useOpenAL) {
                // here we do it in samples ( overflows in 27 hours or so )
                dwCurrentWritePos =
                    idMath.Ftol(win_shared.Sys_Milliseconds() * 44.1f) % (Simd.MIXBUFFER_SAMPLES * snd_local.ROOM_SLICES_IN_BUFFER)
                dwCurrentBlock = (dwCurrentWritePos / Simd.MIXBUFFER_SAMPLES).toInt()
            } else {
                // and here in bytes
                // get the current byte position in the buffer where the sound hardware is currently reading
                if (!snd_audio_hw.GetCurrentPosition(dwCurrentWritePos)) {
                    return 0
                }
                // mixBufferSize is in bytes
                dwCurrentBlock = (dwCurrentWritePos / snd_audio_hw.GetMixBufferSize()).toInt()
            }
            if (nextWriteBlock == -0x1) {
                nextWriteBlock = dwCurrentBlock
            }
            if (dwCurrentBlock != nextWriteBlock) {
                return 0
            }

            // lock the buffer so we can actually write to it
            val fBlock: ShortArray? = null
            val   /*ulong*/fBlockLen: Long = 0
            if (!useOpenAL) {
                snd_audio_hw.Lock( /*(void **)*/fBlock, fBlockLen)
                if (null == fBlock) {
                    return 0
                }
            }
            var j: Int
            soundStats.runs++
            soundStats.activeSounds = 0
            val numSpeakers = snd_audio_hw.GetNumberOfSpeakers()
            nextWriteBlock++
            nextWriteBlock %= snd_local.ROOM_SLICES_IN_BUFFER
            val newPosition = nextWriteBlock * Simd.MIXBUFFER_SAMPLES
            if (newPosition < olddwCurrentWritePos) {
                buffers++ // buffer wrapped
            }

            // nextWriteSample is in multi-channel samples inside the buffer
            val nextWriteSamples = nextWriteBlock * Simd.MIXBUFFER_SAMPLES
            olddwCurrentWritePos = newPosition

            // newSoundTime is in multi-channel samples since the sound system was started
            val newSoundTime = buffers * Simd.MIXBUFFER_SAMPLES * snd_local.ROOM_SLICES_IN_BUFFER + nextWriteSamples

            // check for impending overflow
            // FIXME: we don't handle sound wrap-around correctly yet
            if (newSoundTime > 0x6fffffff) {
                buffers = 0
            }
            if (newSoundTime - CurrentSoundTime > Simd.MIXBUFFER_SAMPLES) {
                soundStats.missedWindow++
            }
            if (useOpenAL) {
                // enable audio hardware caching
                ALC10.alcSuspendContext(openalContext)
            } else {
                // clear the buffer for all the mixing output
//                SIMDProcessor.Memset(finalMixBuffer, 0, MIXBUFFER_SAMPLES * sizeof(float) * numSpeakers);
                Arrays.fill(finalMixBuffer, 0, 0, (Simd.MIXBUFFER_SAMPLES * numSpeakers).toFloat())
            }

            // let the active sound world mix all the channels in unless muted or avi demo recording
            if (!muted && currentSoundWorld != null && null == currentSoundWorld.fpa[0]) {
                currentSoundWorld.MixLoop(newSoundTime, numSpeakers, finalMixBuffer)
            }
            if (useOpenAL) {
                // disable audio hardware caching (this updates ALL settings since last alcSuspendContext)
                ALC10.alcProcessContext(openalContext)
            } else {
//                short[] dest = fBlock + nextWriteSamples * numSpeakers;
                val dest = nextWriteSamples * numSpeakers
                Simd.SIMDProcessor.MixedSoundToSamples(
                    fBlock,
                    dest,
                    finalMixBuffer,
                    Simd.MIXBUFFER_SAMPLES * numSpeakers
                )

                // allow swapping the left / right speaker channels for people with miswired systems
                if (numSpeakers == 2 && s_reverse.GetBool()) {
                    j = 0
                    while (j < Simd.MIXBUFFER_SAMPLES) {
                        val temp = fBlock.get(dest + j * 2)
                        fBlock.get(dest + j * 2) = fBlock.get(dest + j * 2 + 1)
                        fBlock.get(dest + j * 2 + 1) = temp
                        j++
                    }
                }
                snd_audio_hw.Unlock(fBlock, fBlockLen)
            }
            CurrentSoundTime = newSoundTime
            soundStats.timeinprocess = win_shared.Sys_Milliseconds() - time
            return soundStats.timeinprocess
        }

        /*
         ===================
         idSoundSystemLocal::AsyncUpdateWrite
         sound output using a write API. all the scheduling based on time
         we mix MIXBUFFER_SAMPLES at a time, but we feed the audio device with smaller chunks (and more often)
         called by the sound thread when com_asyncSound is 3 ( Linux )
         ===================
         */
        // async loop, when the sound driver uses a write strategy
        override fun AsyncUpdateWrite(inTime: Int): Int {
            if (!isInitialized || shutdown || TempDump.NOT(snd_audio_hw)) {
                return 0
            }
            if (!useOpenAL) {
                snd_audio_hw.Flush()
            }
            val   /*unsigned int*/dwCurrentBlock = (inTime * 44.1f / Simd.MIXBUFFER_SAMPLES).toLong()
            if (nextWriteBlock == -0x1) {
                nextWriteBlock = dwCurrentBlock.toInt()
            }
            if (dwCurrentBlock < nextWriteBlock) {
                return 0
            }
            if (nextWriteBlock.toLong() != dwCurrentBlock) {
                win_main.Sys_Printf("missed %d sound updates\n", dwCurrentBlock - nextWriteBlock)
            }
            val sampleTime = (dwCurrentBlock * Simd.MIXBUFFER_SAMPLES).toInt()
            val numSpeakers = snd_audio_hw.GetNumberOfSpeakers()
            if (useOpenAL) {
                // enable audio hardware caching
                ALC10.alcSuspendContext(openalContext)
            } else {
                // clear the buffer for all the mixing output
//                SIMDProcessor.Memset(finalMixBuffer, 0, MIXBUFFER_SAMPLES * sizeof(float) * numSpeakers);
                Arrays.fill(finalMixBuffer, 0f)
            }

            // let the active sound world mix all the channels in unless muted or avi demo recording
            if (!muted && currentSoundWorld != null && null == currentSoundWorld.fpa[0]) {
                currentSoundWorld.MixLoop(sampleTime, numSpeakers, finalMixBuffer)
            }
            if (useOpenAL) {
                // disable audio hardware caching (this updates ALL settings since last alcSuspendContext)
                ALC10.alcProcessContext(openalContext)
            } else {
                val dest = snd_audio_hw.GetMixBuffer()
                Simd.SIMDProcessor.MixedSoundToSamples(dest, finalMixBuffer, Simd.MIXBUFFER_SAMPLES * numSpeakers)

                // allow swapping the left / right speaker channels for people with miswired systems
                if (numSpeakers == 2 && s_reverse.GetBool()) {
                    var j: Int
                    j = 0
                    while (j < Simd.MIXBUFFER_SAMPLES) {
                        val temp = dest[j * 2]
                        dest[j * 2] = dest[j * 2 + 1]
                        dest[j * 2 + 1] = temp
                        j++
                    }
                }
                snd_audio_hw.Write(false)
            }

            // only move to the next block if the write was successful
            nextWriteBlock = (dwCurrentBlock + 1).toInt()
            CurrentSoundTime = sampleTime
            return win_shared.Sys_Milliseconds() - inTime
        }

        /*
         ===================
         idSoundSystemLocal::AsyncMix
         Mac OSX version. The system uses it's own thread and an IOProc callback
         ===================
         */
        // direct mixing called from the sound driver thread for OSes that support it
        override fun AsyncMix(soundTime: Int, mixBuffer: FloatArray?): Int {
            val inTime: Int
            val numSpeakers: Int
            if (!isInitialized || shutdown || TempDump.NOT(snd_audio_hw)) {
                return 0
            }
            inTime = win_shared.Sys_Milliseconds()
            numSpeakers = snd_audio_hw.GetNumberOfSpeakers()

            // let the active sound world mix all the channels in unless muted or avi demo recording
            if (!muted && currentSoundWorld != null && null == currentSoundWorld.fpa[0]) {
                currentSoundWorld.MixLoop(soundTime, numSpeakers, mixBuffer)
            }
            CurrentSoundTime = soundTime
            return win_shared.Sys_Milliseconds() - inTime
        }

        override fun SetMute(muteOn: Boolean) {
            muted = muteOn
        }

        override fun ImageForTime(milliseconds: Int, waveform: Boolean): cinData_t? {
            val ret = cinData_t()
            var i: Int
            var j: Int
            if (!isInitialized || TempDump.NOT(snd_audio_hw)) {
//		memset( &ret, 0, sizeof( ret ) );
                return ret
            }
            Sys_EnterCriticalSection()
            if (null == graph) {
                graph = IntArray(256 * 128 * 4) // Mem_Alloc(256 * 128 * 4);
            }
            //	memset( graph, 0, 256*128 * 4 );
            val accum = finalMixBuffer // unfortunately, these are already clamped
            val time = win_shared.Sys_Milliseconds()
            val numSpeakers = snd_audio_hw.GetNumberOfSpeakers()
            if (!waveform) {
                j = 0
                while (j < numSpeakers) {
                    var meter = 0
                    i = 0
                    while (i < Simd.MIXBUFFER_SAMPLES) {
                        val result = Math.abs(accum.get(i * numSpeakers + j))
                        if (result > meter) {
                            meter = result.toInt()
                        }
                        i++
                    }
                    meter /= 256 // 32768 becomes 128
                    if (meter > 128) {
                        meter = 128
                    }
                    var offset: Int
                    var xsize: Int
                    if (numSpeakers == 6) {
                        offset = j * 40
                        xsize = 20
                    } else {
                        offset = j * 128
                        xsize = 63
                    }
                    var x: Int
                    var y: Int
                    val   /*dword*/color = -0xff0100
                    y = 0
                    while (y < 128) {
                        x = 0
                        while (x < xsize) {
                            graph.get((127 - y) * 256 + offset + x) = color
                            x++
                        }
                        // #if 0
                        // if ( y == 80 ) {
                        // color = 0xff00ffff;
                        // } else if ( y == 112 ) {
                        // color = 0xff0000ff;
                        // }
// #endif
                        if (y > meter) {
                            break
                        }
                        y++
                    }
                    if (meter > meterTops.get(j)) {
                        meterTops.get(j) = meter
                        meterTopsTime.get(j) = time + s_meterTopTime.GetInteger()
                    } else if (time > meterTopsTime.get(j) && meterTops.get(j) > 0) {
                        meterTops.get(j)--
                        if (meterTops.get(j) != 0) {
                            meterTops.get(j)--
                        }
                    }
                    j++
                }
                j = 0
                while (j < numSpeakers) {
                    val meter = meterTops.get(j)
                    var offset: Int
                    var xsize: Int
                    if (numSpeakers == 6) {
                        offset = j * 40
                        xsize = 20
                    } else {
                        offset = j * 128
                        xsize = 63
                    }
                    var x: Int
                    var y: Int
                    var   /*dword*/color: Int
                    color = if (meter <= 80) {
                        -0xff8100
                    } else if (meter <= 112) {
                        -0xff8081
                    } else {
                        -0xffff81
                    }
                    y = meter
                    while (y < 128 && y < meter + 4) {
                        x = 0
                        while (x < xsize) {
                            graph.get((127 - y) * 256 + offset + x) = color
                            x++
                        }
                        y++
                    }
                    j++
                }
            } else {
                val colors = intArrayOf(-0xff8100, -0xff8081, -0xffff81, -0xff0100, -0xff0001, -0xffff01)
                j = 0
                while (j < numSpeakers) {
                    var xx = 0
                    var fmeter: Float
                    val step = Simd.MIXBUFFER_SAMPLES / 256
                    i = 0
                    while (i < Simd.MIXBUFFER_SAMPLES) {
                        fmeter = 0.0f
                        for (x in 0 until step) {
                            var result = accum.get((i + x) * numSpeakers + j)
                            result = result / 32768.0f
                            fmeter += result
                        }
                        fmeter /= 4.0f
                        if (fmeter < -1.0f) {
                            fmeter = -1.0f
                        } else if (fmeter > 1.0f) {
                            fmeter = 1.0f
                        }
                        var meter = (fmeter * 63.0f).toInt()
                        graph.get((meter + 64) * 256 + xx) = colors[j]
                        if (meter < 0) {
                            meter = -meter
                        }
                        if (meter > meterTops.get(xx)) {
                            meterTops.get(xx) = meter
                            meterTopsTime.get(xx) = time + 100
                        } else if (time > meterTopsTime.get(xx) && meterTops.get(xx) > 0) {
                            meterTops.get(xx)--
                            if (meterTops.get(xx) != 0) {
                                meterTops.get(xx)--
                            }
                        }
                        xx++
                        i += step
                    }
                    j++
                }
                i = 0
                while (i < 256) {
                    val meter = meterTops.get(i)
                    for (y in -meter until meter) {
                        graph.get((y + 64) * 256 + i) = colors[j]
                    }
                    i++
                }
            }
            ret.imageHeight = 128
            ret.imageWidth = 256
            val image = BufferUtils.createByteBuffer(graph.size * 4)
            image.asIntBuffer().put(graph)
            ret.image = image
            Sys_LeaveCriticalSection()
            return ret
        }

        override fun GetSoundDecoderInfo(index: Int, decoderInfo: soundDecoderInfo_t?): Int {
            var i: Int
            var j: Int
            val firstEmitter: Int
            var firstChannel: Int
            val sw = snd_system.soundSystemLocal.currentSoundWorld
            if (index < 0) {
                firstEmitter = 0
                firstChannel = 0
            } else {
                firstEmitter = index / snd_local.SOUND_MAX_CHANNELS
                firstChannel = index - firstEmitter * snd_local.SOUND_MAX_CHANNELS + 1
            }
            i = firstEmitter
            while (i < sw.emitters.Num()) {
                val sound = sw.emitters.oGet(i)
                if (null == sound) {
                    i++
                    continue
                }

                // run through all the channels
                j = firstChannel
                while (j < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = sound.channels[j]
                    if (chan.decoder == null) {
                        j++
                        continue
                    }
                    val sample = chan.decoder.GetSample()
                    if (sample == null) {
                        j++
                        continue
                    }
                    decoderInfo.name = sample.name
                    decoderInfo.format.oSet(if (sample.objectInfo.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) "OGG" else "WAV")
                    decoderInfo.numChannels = sample.objectInfo.nChannels
                    decoderInfo.numSamplesPerSecond = sample.objectInfo.nSamplesPerSec.toLong()
                    decoderInfo.num44kHzSamples = sample.LengthIn44kHzSamples()
                    decoderInfo.numBytes = sample.objectMemSize
                    decoderInfo.looping = chan.parms.soundShaderFlags and snd_shader.SSF_LOOPING != 0
                    decoderInfo.lastVolume = chan.lastVolume
                    decoderInfo.start44kHzTime = chan.trigger44kHzTime
                    decoderInfo.current44kHzTime = snd_system.soundSystemLocal.GetCurrent44kHzTime()
                    return i * snd_local.SOUND_MAX_CHANNELS + j
                    j++
                }
                firstChannel = 0
                i++
            }
            return -1
        }

        // if rw == NULL, no portal occlusion or rendered debugging is available
        override fun AllocSoundWorld(rw: idRenderWorld?): idSoundWorld? {
            val local = idSoundWorldLocal()
            local.Init(rw)
            return local
        }

        /*
         ===================
         idSoundSystemLocal::SetPlayingSoundWorld

         specifying NULL will cause silence to be played
         ===================
         */
        // specifying NULL will cause silence to be played
        override fun SetPlayingSoundWorld(soundWorld: idSoundWorld?) {
            currentSoundWorld = soundWorld as idSoundWorldLocal?
        }

        // some tools, like the sound dialog, may be used in both the game and the editor
        // This can return NULL, so check!
        override fun GetPlayingSoundWorld(): idSoundWorld? {
            return currentSoundWorld
        }

        override fun BeginLevelLoad() {
            if (!isInitialized) {
                return
            }
            soundCache.BeginLevelLoad()
            if (efxloaded) {
                EFXDatabase.UnloadFile()
                efxloaded = false
            }
        }

        override fun EndLevelLoad(mapString: String?) {
            if (!isInitialized) {
                return
            }
            soundCache.EndLevelLoad()
            val efxname = idStr("efxs/")
            val mapname = idStr(mapString)
            mapname.SetFileExtension(".efx")
            mapname.StripPath()
            efxname.oPluSet(mapname)
            efxloaded = EFXDatabase.LoadFile(efxname.toString())
            if (efxloaded) {
                Common.common.Printf("sound: found %s\n", efxname)
            } else {
                Common.common.Printf("sound: missing %s\n", efxname)
            }
        }

        override fun PrintMemInfo(mi: MemInfo_t?) {
            soundCache.PrintMemInfo(mi)
        }

        override fun IsEAXAvailable(): Int {
//#if !ID_OPENAL
            return -1
            //#else
//	ALCdevice	*device;
//	ALCcontext	*context;
//
//	if ( EAXAvailable != -1 ) {
//		return EAXAvailable;
//	}
//
//	if ( !Sys_LoadOpenAL() ) {
//		EAXAvailable = 2;
//		return 2;
//	}
//	// when dynamically loading the OpenAL subsystem, we need to get a context before alIsExtensionPresent would work
//	device = alcOpenDevice( NULL );
//	context = alcCreateContext( device, NULL );
//	alcMakeContextCurrent( context );
//	if ( alIsExtensionPresent( ID_ALCHAR "EAX4.0" ) ) {
//		alcMakeContextCurrent( NULL );
//		alcDestroyContext( context );
//		alcCloseDevice( device );
//		EAXAvailable = 1;
//		return 1;
//	}
//	alcMakeContextCurrent( NULL );
//	alcDestroyContext( context );
//	alcCloseDevice( device );
//	EAXAvailable = 0;
//	return 0;
//#endif
        }

        //-------------------------
        fun GetCurrent44kHzTime(): Int {
            return if (snd_audio_hw != null) {
                CurrentSoundTime
            } else {
                // NOTE: this would overflow 31bits within about 1h20 ( not that important since we get a snd_audio_hw right away pbly )
                //return ( ( Sys_Milliseconds()*441 ) / 10 ) * 4;
                idMath.FtoiFast(win_shared.Sys_Milliseconds() * 176.4f)
            }
        }

        fun dB2Scale(`val`: Float): Float {
            if (`val` == 0.0f) {
                return 1.0f // most common
            } else if (`val` <= -60.0f) {
                return 0.0f
            } else if (`val` >= 60.0f) {
                return Math.pow(2.0, (`val` * (1.0f / 6.0f)).toDouble()).toFloat()
            }
            val ival = ((`val` + 60.0f) * 10.0f).toInt()
            return volumesDB.get(ival)
        }

        fun SamplesToMilliseconds(samples: Int): Int {
            return samples / (snd_local.PRIMARYFREQ / 1000)
        }

        fun MillisecondsToSamples(ms: Int): Int {
            return ms * (snd_local.PRIMARYFREQ / 1000)
        }

        fun DoEnviroSuit(samples: FloatArray?, numSamples: Int, numSpeakers: Int) {
            var out: FloatArray
            val out_p = 2
            var `in`: FloatArray
            val in_p = 2
            assert(!useOpenAL)
            if (0 == fxList.Num()) {
                for (i in 0..5) {
                    var fx: SoundFX

                    // lowpass filter
                    fx = SoundFX_Lowpass()
                    fx.SetChannel(i)
                    fxList.Append(fx)

                    // comb
                    fx = SoundFX_Comb()
                    fx.SetChannel(i)
                    fx.SetParameter((i * 100).toFloat())
                    fxList.Append(fx)

                    // comb
                    fx = SoundFX_Comb()
                    fx.SetChannel(i)
                    fx.SetParameter((i * 100 + 5).toFloat())
                    fxList.Append(fx)
                }
            }
            for (i in 0 until numSpeakers) {
                var j: Int

                // restore previous samples
//		memset( in, 0, 10000 * sizeof( float ) );
                out = FloatArray(10000)
                //		memset( out, 0, 10000 * sizeof( float ) );
                `in` = FloatArray(10000)

                // fx loop
                for (k in 0 until fxList.Num()) {
                    val fx = fxList.oGet(k)

                    // skip if we're not the right channel
                    if (fx.GetChannel() != i) {
                        continue
                    }

                    // get samples and continuity
                    run {
                        val in1 = floatArrayOf(0f)
                        val in2 = floatArrayOf(0f)
                        val out1 = floatArrayOf(0f)
                        val out2 = floatArrayOf(0f)
                        fx.GetContinuitySamples(in1, in2, out1, out2)
                        `in`[in_p - 1] = in1[0]
                        `in`[in_p - 2] = in2[0]
                        out[out_p - 1] = out1[0]
                        out[out_p - 2] = out2[0]
                    }
                    j = 0
                    while (j < numSamples) {
                        `in`[in_p + j] = samples.get(j * numSpeakers + i) * s_enviroSuitVolumeScale.GetFloat()
                        j++
                    }

                    // process fx loop
                    j = 0
                    while (j < numSamples) {
                        fx.ProcessSample(`in`, in_p + j, out, out_p + j) //TODO:float[], int index, float[], int index
                        j++
                    }

                    // store samples and continuity
                    fx.SetContinuitySamples(
                        `in`[in_p + numSamples - 2],
                        `in`[in_p + numSamples - 3],
                        out[out_p + numSamples - 2],
                        out[out_p + numSamples - 3]
                    )
                    j = 0
                    while (j < numSamples) {
                        samples.get(j * numSpeakers + i) = out[out_p + j]
                        j++
                    }
                }
            }
        }

        fun  /*ALuint*/AllocOpenALSource(chan: idSoundChannel?, looping: Boolean, stereo: Boolean): Int {
            var timeOldestZeroVolSingleShot = win_shared.Sys_Milliseconds()
            var timeOldestZeroVolLooping = win_shared.Sys_Milliseconds()
            var timeOldestSingle = win_shared.Sys_Milliseconds()
            var iOldestZeroVolSingleShot = -1
            var iOldestZeroVolLooping = -1
            var iOldestSingle = -1
            var iUnused = -1
            var index = -1
            var   /*ALsizei*/i: Int

            // Grab current msec time
            val time = win_shared.Sys_Milliseconds()

            // Cycle through all sources
            i = 0
            while (i < openalSourceCount) {

                // Use any unused source first,
                // Then find oldest single shot quiet source,
                // Then find oldest looping quiet source and
                // Lastly find oldest single shot non quiet source..
                if (!openalSources.get(i).inUse) {
                    iUnused = i
                    break
                } else if (!openalSources.get(i).looping && openalSources.get(i).chan.lastVolume < snd_local.SND_EPSILON) {
                    if (openalSources.get(i).startTime < timeOldestZeroVolSingleShot) {
                        timeOldestZeroVolSingleShot = openalSources.get(i).startTime
                        iOldestZeroVolSingleShot = i
                    }
                } else if (openalSources.get(i).looping && openalSources.get(i).chan.lastVolume < snd_local.SND_EPSILON) {
                    if (openalSources.get(i).startTime < timeOldestZeroVolLooping) {
                        timeOldestZeroVolLooping = openalSources.get(i).startTime
                        iOldestZeroVolLooping = i
                    }
                } else if (!openalSources.get(i).looping) {
                    if (openalSources.get(i).startTime < timeOldestSingle) {
                        timeOldestSingle = openalSources.get(i).startTime
                        iOldestSingle = i
                    }
                }
                i++
            }
            if (iUnused != -1) {
                index = iUnused
            } else if (iOldestZeroVolSingleShot != -1) {
                index = iOldestZeroVolSingleShot
            } else if (iOldestZeroVolLooping != -1) {
                index = iOldestZeroVolLooping
            } else if (iOldestSingle != -1) {
                index = iOldestSingle
            }
            return if (index != -1) {
                // stop the channel that is being ripped off
                if (openalSources.get(index).chan != null) {
                    // stop the channel only when not looping
                    if (!openalSources.get(index).looping) {
                        openalSources.get(index).chan.Stop()
                    } else {
                        openalSources.get(index).chan.triggered = true
                    }

                    // Free hardware resources
                    openalSources.get(index).chan.ALStop()
                }

                // Initialize structure
                openalSources.get(index).startTime = time
                openalSources.get(index).chan = chan
                openalSources.get(index).inUse = true
                openalSources.get(index).looping = looping
                openalSources.get(index).stereo = stereo
                openalSources.get(index).handle
            } else {
                0
            }
        }

        fun FreeOpenALSource(   /*ALuint*/handle: Int) {
            var   /*ALsizei*/i: Int
            i = 0
            while (i < openalSourceCount) {
                if (openalSources.get(i).handle == handle) {
                    if (openalSources.get(i).chan != null) {
                        openalSources.get(i).chan.openalSource = 0
                    }
                    // #if ID_OPENAL
                    // // Reset source EAX ROOM level when freeing stereo source
                    // if ( openalSources[i].stereo && alEAXSet ) {
                    // long Room = EAXSOURCE_DEFAULTROOM;
                    // alEAXSet( &EAXPROPERTYID_EAX_Source, EAXSOURCE_ROOM, openalSources[i].handle, &Room, sizeof(Room));
                    // }
// #endif
                    // Initialize structure
                    openalSources.get(i).startTime = 0
                    openalSources.get(i).chan = null
                    openalSources.get(i).inUse = false
                    openalSources.get(i).looping = false
                    openalSources.get(i).stereo = false
                }
                i++
            }
        }
    }

    /*
     ===============
     SoundReloadSounds_f

     this is called from the main thread
     ===============
     */
    internal class SoundReloadSounds_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (TempDump.NOT(snd_system.soundSystemLocal.soundCache)) {
                return
            }
            val force = args.Argc() == 2
            snd_system.soundSystem.SetMute(true)
            snd_system.soundSystemLocal.soundCache.ReloadSounds(force)
            snd_system.soundSystem.SetMute(false)
            Common.common.Printf("sound: changed sounds reloaded\n")
        }

        companion object {
            val INSTANCE: cmdFunction_t? = SoundReloadSounds_f()
        }
    }

    /*
     ===============
     ListSounds_f

     Optional parameter to only list sounds containing that string
     ===============
     */
    internal class ListSounds_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val snd = args.Argv(1)
            if (TempDump.NOT(snd_system.soundSystemLocal.soundCache)) {
                Common.common.Printf("No sound.\n")
                return
            }
            var totalSounds = 0
            var totalSamples = 0
            var totalMemory = 0
            var totalPCMMemory = 0
            i = 0
            while (i < snd_system.soundSystemLocal.soundCache.GetNumObjects()) {
                val sample = snd_system.soundSystemLocal.soundCache.GetObject(i)
                if (TempDump.NOT(sample)) {
                    i++
                    continue
                }
                if (snd != null && sample.name.Find(snd, false) < 0) {
                    i++
                    continue
                }
                val info = sample.objectInfo
                val stereo = if (info.nChannels == 2) "ST" else "  "
                val format = if (info.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) "OGG" else "WAV"
                val defaulted = if (sample.defaultSound) "(DEFAULTED)" else if (sample.purged) "(PURGED)" else ""
                Common.common.Printf(
                    "%s %dkHz %6dms %5dkB %4s %s%s\n", stereo, sample.objectInfo.nSamplesPerSec / 1000,
                    snd_system.soundSystemLocal.SamplesToMilliseconds(sample.LengthIn44kHzSamples()),
                    sample.objectMemSize shr 10, format, sample.name, defaulted
                )
                if (!sample.purged) {
                    totalSamples += sample.objectSize
                    if (info.wFormatTag != snd_local.WAVE_FORMAT_TAG_OGG) {
                        totalPCMMemory += sample.objectMemSize
                    }
                    if (!sample.hardwareBuffer) {
                        totalMemory += sample.objectMemSize
                    }
                }
                totalSounds++
                i++
            }
            Common.common.Printf("%8d total sounds\n", totalSounds)
            Common.common.Printf("%8d total samples loaded\n", totalSamples)
            Common.common.Printf("%8d kB total system memory used\n", totalMemory shr 10)
            //#if ID_OPENAL
//	common.Printf( "%8d kB total OpenAL audio memory used\n", ( alGetInteger( alGetEnumValue( "AL_EAX_RAM_SIZE" ) ) - alGetInteger( alGetEnumValue( "AL_EAX_RAM_FREE" ) ) ) >> 10 );
//#endif
        }

        companion object {
            val INSTANCE: cmdFunction_t? = ListSounds_f()
        }
    }

    /*
     ===============
     ListSoundDecoders_f
     ===============
     */
    internal class ListSoundDecoders_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var j: Int
            var numActiveDecoders: Int
            var numWaitingDecoders: Int
            val sw = snd_system.soundSystemLocal.currentSoundWorld
            numWaitingDecoders = 0
            numActiveDecoders = numWaitingDecoders
            i = 0
            while (i < sw.emitters.Num()) {
                val sound = sw.emitters.oGet(i)
                if (TempDump.NOT(sound)) {
                    i++
                    continue
                }

                // run through all the channels
                j = 0
                while (j < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = sound.channels[j]
                    if (chan.decoder == null) {
                        j++
                        continue
                    }
                    val sample = chan.decoder.GetSample()
                    if (sample != null) {
                        j++
                        continue
                    }
                    val format =
                        if (chan.leadinSample.objectInfo.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) "OGG" else "WAV"
                    Common.common.Printf("%3d waiting %s: %s\n", numWaitingDecoders, format, chan.leadinSample.name)
                    numWaitingDecoders++
                    j++
                }
                i++
            }
            i = 0
            while (i < sw.emitters.Num()) {
                val sound = sw.emitters.oGet(i)
                if (TempDump.NOT(sound)) {
                    i++
                    continue
                }

                // run through all the channels
                j = 0
                while (j < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = sound.channels[j]
                    if (chan.decoder == null) {
                        j++
                        continue
                    }
                    val sample = chan.decoder.GetSample()
                    if (sample == null) {
                        j++
                        continue
                    }
                    val format = if (sample.objectInfo.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) "OGG" else "WAV"
                    val localTime = snd_system.soundSystemLocal.GetCurrent44kHzTime() - chan.trigger44kHzTime
                    val sampleTime = sample.LengthIn44kHzSamples() * sample.objectInfo.nChannels
                    var percent: Int
                    percent = if (localTime > sampleTime) {
                        if (chan.parms.soundShaderFlags and snd_shader.SSF_LOOPING != 0) {
                            localTime % sampleTime * 100 / sampleTime
                        } else {
                            100
                        }
                    } else {
                        localTime * 100 / sampleTime
                    }
                    Common.common.Printf("%3d decoding %3d%% %s: %s\n", numActiveDecoders, percent, format, sample.name)
                    numActiveDecoders++
                    j++
                }
                i++
            }
            Common.common.Printf("%d decoders\n", numWaitingDecoders + numActiveDecoders)
            Common.common.Printf("%d waiting decoders\n", numWaitingDecoders)
            Common.common.Printf("%d active decoders\n", numActiveDecoders)
            Common.common.Printf(
                "%d kB decoder memory in %d blocks\n",
                idSampleDecoder.Companion.GetUsedBlockMemory() shr 10,
                idSampleDecoder.Companion.GetNumUsedBlocks()
            )
        }

        companion object {
            val INSTANCE: cmdFunction_t? = ListSoundDecoders_f()
        }
    }

    /*
     ===============
     TestSound_f

     this is called from the main thread
     ===============
     */
    internal class TestSound_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args.Argc() != 2) {
                Common.common.Printf("Usage: testSound <file>\n")
                return
            }
            if (snd_system.soundSystemLocal.currentSoundWorld != null) {
                snd_system.soundSystemLocal.currentSoundWorld.PlayShaderDirectly(args.Argv(1))
            }
        }

        companion object {
            val INSTANCE: cmdFunction_t? = TestSound_f()
        }
    }

    /*
     ===============
     SoundSystemRestart_f

     restart the sound thread

     this is called from the main thread
     ===============
     */
    internal class SoundSystemRestart_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            snd_system.soundSystem.SetMute(true)
            snd_system.soundSystemLocal.ShutdownHW()
            snd_system.soundSystemLocal.InitHW()
            snd_system.soundSystem.SetMute(false)
        }

        companion object {
            val INSTANCE: cmdFunction_t? = SoundSystemRestart_f()
        }
    }
}