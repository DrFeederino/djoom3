package neo.idlib.math

import neo.TempDump.SERiAL
import neo.TempDump.TODO_Exception
import neo.framework.DeclAF.idAFVector.type
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.Matrix.idMatX
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Random.idRandom
import neo.idlib.math.Rotation.idRotation
import org.lwjgl.BufferUtils
import java.nio.*
import java.util.*
import java.util.stream.Stream

neo.Renderer.Material.idMaterial
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
import neo.idlib.Lib
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
import neo.idlib.MapFile
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
import neo.idlib.precompiled
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
import neo.idlib.BitMsg
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
object Vector {
    private val vec2_origin: idVec2? = idVec2(0.0f, 0.0f)
    private val vec3_origin: idVec3 = idVec3(0.0f, 0.0f, 0.0f)
    private val vec3_zero: idVec3 = Vector.vec3_origin
    private val vec4_origin: idVec4? = idVec4(0.0f, 0.0f, 0.0f, 0.0f)
    private val vec4_zero: idVec4? = Vector.vec4_origin
    private val vec5_origin: idVec5? = idVec5(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    private val vec6_infinity: idVec6? =
        idVec6(idMath.INFINITY, idMath.INFINITY, idMath.INFINITY, idMath.INFINITY, idMath.INFINITY, idMath.INFINITY)
    private val vec6_origin: idVec6? = idVec6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    private val vec6_zero: idVec6? = Vector.vec6_origin

    @Deprecated("")
    fun RAD2DEG(a: Double): Float {
        return a.toFloat() * idMath.M_RAD2DEG
    }

    fun RAD2DEG(a: Float): Float {
        return a * idMath.M_RAD2DEG
    }

    fun getVec2_origin(): idVec2? {
        return idVec2(Vector.vec2_origin)
    }

    fun getVec3_origin(): idVec3 {
        return idVec3(0.0f, 0.0f, 0.0f)
    }

    fun getVec3_zero(): idVec3 {
        return idVec3(Vector.vec3_zero)
    }

    fun getVec4_origin(): idVec4? {
        return idVec4(Vector.vec4_origin)
    }

    fun getVec4_zero(): idVec4? {
        return idVec4(Vector.vec4_zero)
    }

    fun getVec5_origin(): idVec5? {
        return idVec5(Vector.vec5_origin)
    }

    fun getVec6_origin(): idVec6? {
        return idVec6(Vector.vec6_origin.p)
    }

    fun getVec6_zero(): idVec6? {
        return idVec6(Vector.vec6_zero.p)
    }

    fun getVec6_infinity(): idVec6? {
        return idVec6(Vector.vec6_infinity.p)
    }

    /*
     ===============================================================================

     Old 3D vector macros, should no longer be used.

     ===============================================================================
     */
    fun DotProduct(a: DoubleArray?, b: DoubleArray?): Double {
        return a.get(0) * b.get(0) + a.get(1) * b.get(1) + a.get(2) * b.get(2)
    }

    fun DotProduct(a: FloatArray?, b: FloatArray?): Float {
        return a.get(0) * b.get(0) + a.get(1) * b.get(1) + a.get(2) * b.get(2)
    }

    fun DotProduct(a: idVec3, b: idVec3): Float {
        return a.oGet(0) * b.oGet(0) + a.oGet(1) * b.oGet(1) + a.oGet(2) * b.oGet(2)
    }

    fun DotProduct(a: idVec3, b: idVec4?): Float {
        return Vector.DotProduct(a, b.ToVec3())
    }

    fun DotProduct(a: idVec3, b: idVec5?): Float {
        return Vector.DotProduct(a, b.ToVec3())
    }

    fun DotProduct(a: idPlane?, b: idPlane?): Float {
        return a.oGet(0) * b.oGet(0) + a.oGet(1) * b.oGet(1) + a.oGet(2) * b.oGet(2)
    }

    fun VectorSubtract(a: DoubleArray?, b: DoubleArray?, c: DoubleArray?): DoubleArray? {
        c.get(0) = a.get(0) - b.get(0)
        c.get(1) = a.get(1) - b.get(1)
        c.get(2) = a.get(2) - b.get(2)
        return c
    }

    fun VectorSubtract(a: FloatArray?, b: FloatArray?, c: FloatArray?): FloatArray? {
        c.get(0) = a.get(0) - b.get(0)
        c.get(1) = a.get(1) - b.get(1)
        c.get(2) = a.get(2) - b.get(2)
        return c
    }

    fun VectorSubtract(a: idVec3, b: idVec3, c: FloatArray?): FloatArray? {
        c.get(0) = a.oGet(0) - b.oGet(0)
        c.get(1) = a.oGet(1) - b.oGet(1)
        c.get(2) = a.oGet(2) - b.oGet(2)
        return c
    }

    fun VectorSubtract(a: idVec3, b: idVec3, c: idVec3): idVec3 {
        c.oSet(0, a.oGet(0) - b.oGet(0))
        c.oSet(1, a.oGet(1) - b.oGet(1))
        c.oSet(2, a.oGet(2) - b.oGet(2))
        return c
    }

    fun VectorAdd(a: DoubleArray?, b: DoubleArray?, c: Array<Double?>?) {
        c.get(0) = a.get(0) + b.get(0)
        c.get(1) = a.get(1) + b.get(1)
        c.get(2) = a.get(2) + b.get(2)
    }

    fun VectorScale(v: DoubleArray?, s: Double, o: Array<Double?>?) {
        o.get(0) = v.get(0) * s
        o.get(1) = v.get(1) * s
        o.get(2) = v.get(2) * s
    }

    fun VectorMA(v: DoubleArray?, s: Double, b: DoubleArray?, o: Array<Double?>?) {
        o.get(0) = v.get(0) + b.get(0) * s
        o.get(1) = v.get(1) + b.get(1) * s
        o.get(2) = v.get(2) + b.get(2) * s
    }

    fun VectorMA(v: idVec3, s: Float, b: idVec3, o: idVec3) {
        o.oSet(0, v.oGet(0) + b.oGet(0) * s)
        o.oSet(1, v.oGet(1) + b.oGet(1) * s)
        o.oSet(2, v.oGet(2) + b.oGet(2) * s)
    }

    fun VectorCopy(a: DoubleArray?, b: Array<Double?>?) {
        b.get(0) = a.get(0)
        b.get(1) = a.get(1)
        b.get(2) = a.get(2)
    }

    fun VectorCopy(a: idVec3, b: idVec3) {
        b.oSet(a)
    }

    fun VectorCopy(a: idVec3, b: idVec5?) {
        b.oSet(a)
    }

    fun VectorCopy(a: idVec5?, b: idVec3) {
        b.oSet(a.ToVec3())
    }

    interface idVec<type : idVec<*>?> {
        //reflection was too slow.
        //never thought I would say this, but thank God for type erasure.
        fun oGet(index: Int): Float {
            throw TODO_Exception()
        }

        fun oSet(a: type?): type? {
            throw TODO_Exception()
        }

        fun oSet(index: Int, value: Float): Float {
            throw TODO_Exception()
        }

        fun oPlus(a: type?): type? {
            throw TODO_Exception()
        }

        fun oMinus(a: type?): type? {
            throw TODO_Exception()
        }

        fun oMultiply(a: type?): Float {
            throw TODO_Exception()
        }

        fun oMultiply(a: Float): type? {
            throw TODO_Exception()
        }

        fun oDivide(a: Float): type? {
            throw TODO_Exception()
        }

        fun oPluSet(a: type?): type? {
            throw TODO_Exception()
        }

        fun GetDimension(): Int {
            throw TODO_Exception()
        }

        fun Zero() {
            throw TODO_Exception()
        }
    }

    //===============================================================
    //
    //	idVec2 - 2D vector
    //
    //===============================================================
    class idVec2 : idVec<idVec2?>, SERiAL {
        var x = 0f
        var y = 0f

        constructor()
        constructor(x: Float, y: Float) {
            this.x = x
            this.y = y
        }

        constructor(v: idVec2?) {
            x = v.x
            y = v.y
        }

        fun Set(x: Float, y: Float) {
            this.x = x
            this.y = y
        }

        override fun Zero() {
            y = 0.0f
            x = y
        }

        //public	float			operator[]( int index ) const;
        override fun oSet(index: Int, value: Float): Float {
            return if (index == 1) {
                value.also { y = it }
            } else {
                value.also { x = it }
            }
        }

        //public	float &			operator[]( int index );
        fun oPluSet(index: Int, value: Float): Float {
            return if (index == 1) {
                value.let { y += it; y }
            } else {
                value.let { x += it; x }
            }
        }

        //public	idVec2			operator-() const;
        override fun oGet(index: Int): Float { //TODO:rename you lazy sod
            return if (index == 1) {
                y
            } else x
        }

        //public	float			operator*( const idVec2 &a ) const;
        override fun oMultiply(a: idVec2?): Float {
            return x * a.x + y * a.y
        }

        //public	idVec2			operator/( const float a ) const;
        //public	idVec2			operator*( const float a ) const;
        override fun oMultiply(a: Float): idVec2? {
            return idVec2(x * a, y * a)
        }

        override fun oDivide(a: Float): idVec2? {
            val inva = 1.0f / a
            return idVec2(x * inva, y * inva)
        }

        //public	idVec2			operator+( const idVec2 &a ) const;
        override fun oPlus(a: idVec2?): idVec2? {
            return idVec2(x + a.x, y + a.y)
        }

        //public	idVec2			operator-( const idVec2 &a ) const;
        override fun oMinus(a: idVec2?): idVec2? {
            return idVec2(x - a.x, y - a.y)
        }

        //public	idVec2 &		operator+=( const idVec2 &a );
        override fun oPluSet(a: idVec2?): idVec2? {
            x += a.x
            y += a.y
            return this
        }

        //public	idVec2 &		operator/=( const idVec2 &a );
        //public	idVec2 &		operator/=( const float a );
        //public	idVec2 &		operator*=( const float a );
        //public	idVec2 &		operator-=( const idVec2 &a );
        fun oMinSet(a: idVec2?): idVec2? {
            x -= a.x
            y -= a.y
            return this
        }

        fun oMulSet(a: Float): idVec2? {
            x *= a
            y *= a
            return this
        }

        //public	friend idVec2	operator*( const float a, const idVec2 b );
        override fun oSet(a: idVec2?): idVec2? {
            x = a.x
            y = a.y
            return this
        }

        fun Compare(a: idVec2?): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y
        }

        //public	bool			operator==(	const idVec2 &a ) const;						// exact compare, no epsilon
        //public	bool			operator!=(	const idVec2 &a ) const;						// exact compare, no epsilon
        fun Compare(a: idVec2?, epsilon: Float): Boolean { // compare with epsilon
            return if (Math.abs(x - a.x) > epsilon) {
                false
            } else Math.abs(y - a.y) <= epsilon
        }

        fun Length(): Float {
            return idMath.Sqrt(x * x + y * y)
        }

        fun LengthFast(): Float {
            val sqrLength: Float
            sqrLength = x * x + y * y
            return sqrLength * idMath.RSqrt(sqrLength)
        }

        fun LengthSqr(): Float {
            return x * x + y * y
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength = x * x + y * y
            invLength = idMath.InvSqrt(sqrLength)
            x *= invLength
            y *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val lengthSqr: Float
            val invLength: Float
            lengthSqr = x * x + y * y
            invLength = idMath.RSqrt(lengthSqr)
            x *= invLength
            y *= invLength
            return invLength * lengthSqr
        }

        fun Truncate(length: Float): idVec2? { // cap length
            val length2: Float
            val ilength: Float
            if (length == 0f) {
                Zero()
            } else {
                length2 = LengthSqr()
                if (length2 > length * length) {
                    ilength = length * idMath.InvSqrt(length2)
                    x *= ilength
                    y *= ilength
                }
            }
            return this
        }

        fun Clamp(min: idVec2?, max: idVec2?) {
            if (x < min.x) {
                x = min.x
            } else if (x > max.x) {
                x = max.x
            }
            if (y < min.y) {
                y = min.y
            } else if (y > max.y) {
                y = max.y
            }
        }

        fun Snap() { // snap to closest integer value
//            x = floor(x + 0.5f);
            x = Math.floor((x + 0.5f).toDouble()).toFloat()
            y = Math.floor((y + 0.5f).toDouble()).toFloat()
        }

        fun SnapInt() { // snap towards integer (floor)
            x = x.toInt().toFloat()
            y = y.toInt().toFloat()
        }

        override fun GetDimension(): Int {
            return 2
        }

        //public	float *			ToFloatPtr( void );
        fun ToFloatPtr(): FloatArray? {
            return floatArrayOf(x, y)
        }

        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        override fun toString(): String {
            return "$x $y"
        }

        /*
         =============
         Lerp

         Linearly inperpolates one vector to another.
         =============
         */
        fun Lerp(v1: idVec2?, v2: idVec2?, l: Float) {
            if (l <= 0.0f) {
                this.oSet(v1) //( * this) = v1;
            } else if (l >= 1.0f) {
                this.oSet(v2) //( * this) = v2;
            } else {
                this.oSet(v2.oMinus(v1).oMultiply(l).oPlus(v1)) //( * this) = v1 + l * (v2 - v1);
            }
        }

        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            @Transient
            val SIZE = 2 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
            fun generateArray(length: Int): Array<idVec2?>? {
                return Stream.generate { idVec2() }.limit(length.toLong()).toArray { _Dummy_.__Array__() }
            }
        }
    }

    //===============================================================
    //
    //	idVec3 - 3D vector
    //
    //===============================================================
    open class idVec3 : idVec<idVec3>, SERiAL {
        var x = 0f
        var y = 0f
        var z = 0f

        constructor()
        constructor(x: Float, y: Float, z: Float) {
            this.x = x
            this.y = y
            this.z = z
        }

        constructor(v: idVec3) {
            x = v.x
            y = v.y
            z = v.z
        }

        @JvmOverloads
        constructor(xyz: FloatArray?, offset: Int = 0) {
            x = xyz.get(offset + 0)
            y = xyz.get(offset + 1)
            z = xyz.get(offset + 2)
        }

        fun Set(x: Float, y: Float, z: Float) {
            this.x = x
            this.y = y
            this.z = z
        }

        override fun Zero() {
            z = 0.0f
            y = z
            x = y
        }

        //public	float			operator[]( final  int index ) final ;
        //public	float &			operator[]( final  int index );
        //public	idVec3			operator-() final ;
        fun oNegative(): idVec3 {
            return idVec3(-x, -y, -z)
        }

        //public	idVec3 &		operator=( final  idVec3 &a );		// required because of a msvc 6 & 7 bug
        override fun oSet(a: idVec3): idVec3 {
            x = a.x
            y = a.y
            z = a.z
            return this
        }

        fun oSet(a: idVec2?): idVec3 {
            x = a.x
            y = a.y
            return this
        }

        //public	float			operator*( final  idVec3 &a ) final ;
        override fun oMultiply(a: idVec3): Float {
            return a.x * x + a.y * y + a.z * z
        }

        //public	idVec3			operator*( final  float a ) final ;
        override fun oMultiply(a: Float): idVec3 {
            return idVec3(x * a, y * a, z * a)
        }

        fun oMultiply(a: idMat3?): idVec3 {
            return idVec3(
                a.getRow(0).oGet(0) * x + a.getRow(1).oGet(0) * y + a.getRow(2).oGet(0) * z,
                a.getRow(0).oGet(1) * x + a.getRow(1).oGet(1) * y + a.getRow(2).oGet(1) * z,
                a.getRow(0).oGet(2) * x + a.getRow(1).oGet(2) * y + a.getRow(2).oGet(2) * z
            )
        }

        fun oMultiply(a: idRotation?): idVec3 {
            return a.oMultiply(this)
        }

        fun oMultiply(a: idMat4?): idVec3 {
            return a.oMultiply(this)
        }

        //public	idVec3			operator/( final  float a ) final ;
        override fun oDivide(a: Float): idVec3 {
            val inva = 1.0f / a
            return idVec3(x * inva, y * inva, z * inva)
        }

        //public	idVec3			operator+( final  idVec3 &a ) final ;F
        override fun oPlus(a: idVec3): idVec3 {
            return idVec3(x + a.x, y + a.y, z + a.z)
        }

        //public	idVec3			operator-( final  idVec3 &a ) final ;
        override fun oMinus(a: idVec3): idVec3 {
            return idVec3(x - a.x, y - a.y, z - a.z)
        }

        //public	idVec3 &		operator+=( final  idVec3 &a );
        override fun oPluSet(a: idVec3): idVec3 {
            x += a.x
            y += a.y
            z += a.z
            return this
        }

        //public	idVec3 &		operator-=( final  idVec3 &a );
        fun oMinSet(a: idVec3): idVec3 {
            x -= a.x
            y -= a.y
            z -= a.z
            return this
        }

        //public	idVec3 &		operator/=( final  idVec3 &a );
        fun oDivSet(a: Float): idVec3 {
            x /= a
            y /= a
            z /= a
            return this
        }

        //public	idVec3 &		operator*=( final  float a );
        fun oMulSet(a: Float): idVec3 {
            x *= a
            y *= a
            z *= a
            return this
        }

        fun oMulSet(mat: idMat3?): idVec3 {
            this.oSet(idMat3.Companion.oMulSet(this, mat))
            return this
        }

        //public	boolean			operator==(	final  idVec3 &a ) final ;						// exact compare, no epsilon
        //public	boolean			operator!=(	final  idVec3 &a ) final ;						// exact compare, no epsilon
        fun oMulSet(rotation: idRotation?): idVec3 {
            this.oSet(rotation.oMultiply(this))
            return this
        }

        fun Compare(a: idVec3): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y && z == a.z
        }

        fun Compare(a: idVec3, epsilon: Float): Boolean { // compare with epsilon
            if (Math.abs(x - a.x) > epsilon) {
                return false
            }
            return if (Math.abs(y - a.y) > epsilon) {
                false
            } else Math.abs(z - a.z) <= epsilon
        }

        //private idVec3  multiply(float a){
        //    return new idVec3( this.x * a, this.y * a, this.z * a );
        //}
        fun oPlus(a: Float): idVec3 {
            x += a
            y += a
            z += a
            return this
        }

        private fun oDivide(a: idVec3, b: Float): idVec3 {
            val invB = 1.0f / b
            return idVec3(a.x * b, a.y * b, a.z * b)
        }

        fun FixDegenerateNormal(): Boolean { // fix degenerate axial cases
            if (x == 0.0f) {
                if (y == 0.0f) {
                    if (z > 0.0f) {
                        if (z != 1.0f) {
                            z = 1.0f
                            return true
                        }
                    } else {
                        if (z != -1.0f) {
                            z = -1.0f
                            return true
                        }
                    }
                    return false
                } else if (z == 0.0f) {
                    if (y > 0.0f) {
                        if (y != 1.0f) {
                            y = 1.0f
                            return true
                        }
                    } else {
                        if (y != -1.0f) {
                            y = -1.0f
                            return true
                        }
                    }
                    return false
                }
            } else if (y == 0.0f) {
                if (z == 0.0f) {
                    if (x > 0.0f) {
                        if (x != 1.0f) {
                            x = 1.0f
                            return true
                        }
                    } else {
                        if (x != -1.0f) {
                            x = -1.0f
                            return true
                        }
                    }
                    return false
                }
            }
            if (Math.abs(x) == 1.0f) {
                if (y != 0.0f || z != 0.0f) {
                    z = 0.0f
                    y = z
                    return true
                }
                return false
            } else if (Math.abs(y) == 1.0f) {
                if (x != 0.0f || z != 0.0f) {
                    z = 0.0f
                    x = z
                    return true
                }
                return false
            } else if (Math.abs(z) == 1.0f) {
                if (x != 0.0f || y != 0.0f) {
                    y = 0.0f
                    x = y
                    return true
                }
                return false
            }
            return false
        }

        fun FixDenormals(): Boolean { // change tiny numbers to zero
            var denormal = false
            if (Math.abs(x) < 1e-30f) {
                x = 0.0f
                denormal = true
            }
            if (Math.abs(y) < 1e-30f) {
                y = 0.0f
                denormal = true
            }
            if (Math.abs(z) < 1e-30f) {
                z = 0.0f
                denormal = true
            }
            return denormal
        }

        fun Cross(a: idVec3): idVec3 {
            return idVec3(y * a.z - z * a.y, z * a.x - x * a.z, x * a.y - y * a.x)
        }

        fun Cross(a: idVec3, b: idVec3): idVec3 {
            x = a.y * b.z - a.z * b.y
            y = a.z * b.x - a.x * b.z
            z = a.x * b.y - a.y * b.x
            return this
        }

        fun Length(): Float {
            return idMath.Sqrt(x * x + y * y + z * z)
        }

        fun LengthSqr(): Float {
            return x * x + y * y + z * z
        }

        fun LengthFast(): Float {
            val sqrLength: Float
            sqrLength = x * x + y * y + z * z
            return sqrLength * idMath.RSqrt(sqrLength)
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength = x * x + y * y + z * z
            invLength = idMath.InvSqrt(sqrLength)
            x *= invLength
            y *= invLength
            z *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength = x * x + y * y + z * z
            invLength = idMath.RSqrt(sqrLength)
            x *= invLength
            y *= invLength
            z *= invLength
            return invLength * sqrLength
        }

        fun Truncate(length: Float): idVec3 { // cap length
            val length2: Float
            val ilength: Float
            if (length != 0.0f) {
                Zero()
            } else {
                length2 = LengthSqr()
                if (length2 > length * length) {
                    ilength = length * idMath.InvSqrt(length2)
                    x *= ilength
                    y *= ilength
                    z *= ilength
                }
            }
            return this
        }

        fun Clamp(min: idVec3, max: idVec3) {
            if (x < min.x) {
                x = min.x
            } else if (x > max.x) {
                x = max.x
            }
            if (y < min.y) {
                y = min.y
            } else if (y > max.y) {
                y = max.y
            }
            if (z < min.z) {
                z = min.z
            } else if (z > max.z) {
                z = max.z
            }
        }

        fun Snap() { // snap to closest integer value
            x = Math.floor((x + 0.5f).toDouble()).toFloat()
            y = Math.floor((y + 0.5f).toDouble()).toFloat()
            z = Math.floor((z + 0.5f).toDouble()).toFloat()
        }

        fun SnapInt() { // snap towards integer (floor)
            x = x.toInt().toFloat()
            y = y.toInt().toFloat()
            z = z.toInt().toFloat()
        }

        override fun GetDimension(): Int {
            return 3
        }

        fun ToYaw(): Float {
            var yaw: Float
            if (y == 0.0f && x == 0.0f) {
                yaw = 0.0f
            } else {
                yaw = Vector.RAD2DEG(Math.atan2(y.toDouble(), x.toDouble()))
                if (yaw < 0.0f) {
                    yaw += 360.0f
                }
            }
            return yaw
        }

        fun ToPitch(): Float {
            val forward: Float
            var pitch: Float
            if (x == 0.0f && y == 0.0f) {
                pitch = if (z > 0.0f) {
                    90.0f
                } else {
                    270.0f
                }
            } else {
                forward = idMath.Sqrt(x * x + y * y)
                pitch = Vector.RAD2DEG(Math.atan2(z.toDouble(), forward.toDouble()))
                if (pitch < 0.0f) {
                    pitch += 360.0f
                }
            }
            return pitch
        }

        fun ToAngles(): idAngles? {
            val forward: Float
            var yaw: Float
            var pitch: Float
            if (x == 0.0f && y == 0.0f) {
                yaw = 0.0f
                pitch = if (z > 0.0f) {
                    90.0f
                } else {
                    270.0f
                }
            } else {
                yaw = Vector.RAD2DEG(Math.atan2(y.toDouble(), x.toDouble()))
                if (yaw < 0.0f) {
                    yaw += 360.0f
                }
                forward = idMath.Sqrt(x * x + y * y)
                pitch = Vector.RAD2DEG(Math.atan2(z.toDouble(), forward.toDouble()))
                if (pitch < 0.0f) {
                    pitch += 360.0f
                }
            }
            return idAngles(-pitch, yaw, 0.0f)
        }

        //public	idVec2 &		ToVec2( void );
        fun ToPolar(): idPolar3? {
            val forward: Float
            var yaw: Float
            var pitch: Float
            if (x == 0.0f && y == 0.0f) {
                yaw = 0.0f
                pitch = if (z > 0.0f) {
                    90.0f
                } else {
                    270.0f
                }
            } else {
                yaw = Vector.RAD2DEG(Math.atan2(y.toDouble(), x.toDouble()))
                if (yaw < 0.0f) {
                    yaw += 360.0f
                }
                forward = idMath.Sqrt(x * x + y * y)
                pitch = Vector.RAD2DEG(Math.atan2(z.toDouble(), forward.toDouble()))
                if (pitch < 0.0f) {
                    pitch += 360.0f
                }
            }
            return idPolar3(idMath.Sqrt(x * x + y * y + z * z), yaw, -pitch)
        }

        //public	float *			ToFloatPtr( void );
        // vector should be normalized
        fun ToMat3(): idMat3? {
            val mat = idMat3()
            var d: Float
            mat.setRow(0, x, y, z)
            d = x * x + y * y
            if (d == 0f) {
//		mat[1][0] = 1.0f;
//		mat[1][1] = 0.0f;
//		mat[1][2] = 0.0f;
                mat.setRow(1, 1.0f, 0.0f, 0.0f) //TODO:test, and rename, column??
            } else {
                d = idMath.InvSqrt(d)
                //		mat[1][0] = -y * d;
//		mat[1][1] = x * d;
//		mat[1][2] = 0.0f;
                mat.setRow(1, -y * d, x * d, 0.0f)
            }
            //        mat[2] = Cross( mat[1] );
            mat.setRow(2, Cross(mat.getRow(1)))
            return mat
        }

        fun ToVec2(): idVec2? {
//	return *reinterpret_cast<const idVec2 *>(this);
            return idVec2(x, y)
        }

        fun ToFloatPtr(): FloatArray? {
            return floatArrayOf(x, y, z)
        }

        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        override fun toString(): String {
            return "$x $y $z"
        }

        // vector should be normalized
        fun NormalVectors(left: idVec3, down: idVec3) {
            var d: Float
            d = x * x + y * y
            if (d == 0f) {
                left.x = 1f
                left.y = 0f
                left.z = 0f
            } else {
                d = idMath.InvSqrt(d)
                left.x = -y * d
                left.y = x * d
                left.z = 0f
            }
            down.oSet(left.Cross(this))
        }

        fun OrthogonalBasis(left: idVec3, up: idVec3) {
            val l: Float
            val s: Float
            if (Math.abs(z) > 0.7f) {
                l = y * y + z * z
                s = idMath.InvSqrt(l)
                up.x = 0f
                up.y = z * s
                up.z = -y * s
                left.x = l * s
                left.y = -x * up.z
                left.z = x * up.y
            } else {
                l = x * x + y * y
                s = idMath.InvSqrt(l)
                left.x = -y * s
                left.y = x * s
                left.z = 0f
                up.x = -z * left.y
                up.y = z * left.x
                up.z = l * s
            }
        }

        /*
         =============
         ProjectSelfOntoSphere

         Projects the z component onto a sphere.
         =============
         */
        @JvmOverloads
        fun ProjectOntoPlane(normal: idVec3, overBounce: Float = 1.0f) {
            var backoff: Float
            // x * a.x + y * a.y + z * a.z;
            backoff = this.oMultiply(normal) //	backoff = this.x * normal.x;//TODO:normal.x???
            if (overBounce.toDouble() != 1.0) {
                if (backoff < 0) {
                    backoff *= overBounce
                } else {
                    backoff /= overBounce
                }
            }
            this.oMinSet(oMultiply(backoff, normal)) //	*this -= backoff * normal;
        }

        @JvmOverloads
        fun ProjectAlongPlane(normal: idVec3, epsilon: Float, overBounce: Float = 1.0f): Boolean {
            val cross = idVec3()
            val len: Float
            cross.oSet(this.Cross(normal).Cross(this))
            // normalize so a fixed epsilon can be used
            cross.Normalize()
            len = normal.oMultiply(cross)
            if (Math.abs(len) < epsilon) {
                return false
            }
            cross.oMulSet(overBounce * normal.oMultiply(this) / len) //	cross *= overBounce * ( normal * (*this) ) / len;
            this.oMinSet(cross) //(*this) -= cross;
            return true
        }

        fun ProjectSelfOntoSphere(radius: Float) {
            val rsqr = radius * radius
            val len = Length()
            z = if (len < rsqr * 0.5f) {
                Math.sqrt((rsqr - len).toDouble()).toFloat()
            } else {
                (rsqr / (2.0f * Math.sqrt(len.toDouble()))).toFloat()
            }
        }

        /*
         =============
         Lerp

         Linearly inperpolates one vector to another.
         =============
         */
        fun Lerp(v1: idVec3, v2: idVec3, l: Float) {
            if (l <= 0.0f) {
                this.oSet(v1) //(*this) = v1;
            } else if (l >= 1.0f) {
                this.oSet(v2) //(*this) = v2;
            } else {
                this.oSet(v2.oMinus(v1).oMultiply(l).oPlus(v1)) //(*this) = v1 + l * ( v2 - v1 );
            }
        }

        fun SLerp(v1: idVec3, v2: idVec3, t: Float) {
            val omega: Float
            val cosom: Float
            val sinom: Float
            val scale0: Float
            val scale1: Float
            if (t <= 0.0f) {
//		(*this) = v1;
                oSet(v1)
                return
            } else if (t >= 1.0f) {
//		(*this) = v2;
                oSet(v2)
                return
            }
            cosom = v1.oMultiply(v2)
            if (1.0f - cosom > LERP_DELTA) {
                omega = Math.acos(cosom.toDouble()).toFloat()
                sinom = Math.sin(omega.toDouble()).toFloat()
                scale0 = (Math.sin(((1.0f - t) * omega).toDouble()) / sinom).toFloat()
                scale1 = (Math.sin((t * omega).toDouble()) / sinom).toFloat()
            } else {
                scale0 = 1.0f - t
                scale1 = t
            }

//	(*this) = ( v1 * scale0 + v2 * scale1 );
            oSet(v1.oMultiply(scale0).oPlus(v2.oMultiply(scale1)))
        }

        override fun oGet(i: Int): Float { //TODO:rename you lazy ass
            if (i == 1) {
                return y
            } else if (i == 2) {
                return z
            }
            return x
        }

        override fun oSet(i: Int, value: Float): Float {
            if (i == 1) {
                y = value
            } else if (i == 2) {
                z = value
            } else {
                x = value
            }
            return value
        }

        fun oPluSet(i: Int, value: Float) {
            if (i == 1) {
                y += value
            } else if (i == 2) {
                z += value
            } else {
                x += value
            }
        }

        fun oMinSet(i: Int, value: Float) {
            if (i == 1) {
                y -= value
            } else if (i == 2) {
                z -= value
            } else {
                x -= value
            }
        }

        fun oMulSet(i: Int, value: Float) {
            if (i == 1) {
                y *= value
            } else if (i == 2) {
                z *= value
            } else {
                x *= value
            }
        }

        override fun AllocBuffer(): ByteBuffer? {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer?) {
            x = buffer.getFloat()
            y = buffer.getFloat()
            z = buffer.getFloat()
        }

        override fun Write(): ByteBuffer? {
            val buffer = ByteBuffer.allocate(BYTES)
            buffer.putFloat(x).putFloat(y).putFloat(z).flip()
            return buffer
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o !is idVec3) return false
            val idVec3 = o as idVec3
            if (java.lang.Float.compare(idVec3.x, x) != 0) return false
            return if (java.lang.Float.compare(idVec3.y, y) != 0) false else java.lang.Float.compare(idVec3.z, z) == 0
        }

        override fun hashCode(): Int {
            var result = if (x != +0.0f) java.lang.Float.floatToIntBits(x) else 0
            result = 31 * result + if (y != +0.0f) java.lang.Float.floatToIntBits(y) else 0
            result = 31 * result + if (z != +0.0f) java.lang.Float.floatToIntBits(z) else 0
            return result
        }

        fun ToVec2_oPluSet(v: idVec2?) {
            x += v.x
            y += v.y
        }

        fun ToVec2_oMinSet(v: idVec2?) {
            x -= v.x
            y -= v.y
        }

        fun ToVec2_oMulSet(a: Float) {
            x *= a
            y *= a
        }

        fun ToVec2_Normalize() {
            val v = ToVec2()
            v.Normalize()
            this.oSet(v)
        }

        fun ToVec2_NormalizeFast() {
            val v = ToVec2()
            v.NormalizeFast()
            this.oSet(v)
        }

        companion object {
            @Transient
            val SIZE = 3 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE

            /*
         =============
         SLerp

         Spherical linear interpolation from v1 to v2.
         Vectors are expected to be normalized.
         =============
         */
            private const val LERP_DELTA = 1e-6

            //public	friend idVec3	operator*( final  float a, final  idVec3 b );
            fun oMultiply(a: Float, b: idVec3): idVec3 {
                return idVec3(b.x * a, b.y * a, b.z * a)
            }

            fun generateArray(length: Int): Array<idVec3> {
                val arr = arrayOf<idVec3>()
                for (i in 0..length) {
                    arr[i] = idVec3()
                }
                return arr
            }

            fun generateArray(firstDimensionSize: Int, secondDimensionSize: Int): Array<Array<idVec3>> {
                val out = Array<Array<idVec3>>(firstDimensionSize) { arrayOf<idVec3>(secondDimensionSize) }
                for (i in 0 until firstDimensionSize) {
                    out[i] = generateArray(secondDimensionSize)
                }
                return out
            }

            fun copyVec(`in`: Array<idVec3>?): Array<idVec3>? {
                val out = generateArray(`in`.size)
                for (i in out.indices) {
                    out.get(i).oSet(`in`.get(i))
                }
                return out
            }

            fun toByteBuffer(vecs: Array<idVec3>?): ByteBuffer? {
                val data = BufferUtils.createByteBuffer(BYTES * vecs.size)
                for (vec in vecs) {
                    data.put(vec.Write().rewind())
                }
                return data.flip()
            }
        }
    }

    //===============================================================
    //
    //	idVec4 - 4D vector
    //
    //===============================================================
    class idVec4 : idVec<idVec4?>, SERiAL {
        private val DBG_count = DBG_counter++
        var w = 0f
        var x = 0f
        var y = 0f
        var z = 0f

        constructor()
        constructor(v: idVec4?) {
            x = v.x
            y = v.y
            z = v.z
            w = v.w
        }

        constructor(x: Float, y: Float, z: Float, w: Float) {
            this.x = x
            this.y = y
            this.z = z
            this.w = w
        }

        fun Set(x: Float, y: Float, z: Float, w: Float) {
            this.x = x
            this.y = y
            this.z = z
            this.w = w
        }

        override fun Zero() {
            w = 0.0f
            z = w
            y = z
            x = y
        }

        //public	float			operator[]( final  int index ) final ;
        //public	float &			operator[]( final  int index );
        //public	idVec4			operator-() final ;
        override fun oMultiply(a: idVec4?): Float {
            return x * a.x + y * a.y + z * a.z + w * a.w
        }

        //public	idVec4			operator/( final  float a ) final ;
        override fun oMultiply(a: Float): idVec4? {
            return idVec4(x * a, y * a, z * a, w * a)
        }

        fun oMultiply(a: Float?): idVec4? { //for our reflection method
            return oMultiply(a.toFloat())
        }

        override fun oPlus(a: idVec4?): idVec4? {
            return idVec4(x + a.x, y + a.y, z + a.z, w + a.w)
        }

        override fun oMinus(a: idVec4?): idVec4? {
            return idVec4(x - a.x, y - a.y, z - a.z, w - a.w)
        }

        fun oNegative(): idVec4? {
            return idVec4(-x, -y, -z, -w)
        }

        //public	idVec4 &		operator+=( final  idVec4 &a );
        //public	idVec4			operator-( final  idVec4 &a ) final ;
        fun oMinSet(i: Int, value: Float) { //TODO:rename you lazy ass
            when (i) {
                1 -> y -= value
                2 -> z -= value
                3 -> w -= value
                else -> x -= value
            }
        }

        fun oMulSet(i: Int, value: Float) { //TODO:rename you lazy ass
            when (i) {
                1 -> y *= value
                2 -> z *= value
                3 -> w *= value
                else -> x *= value
            }
        }

        override fun oPluSet(a: idVec4?): idVec4? {
            x += a.x
            y += a.y
            z += a.z
            w += a.w
            return this
        }

        //public	bool			operator==(	final  idVec4 &a ) final ;						// exact compare, no epsilon
        //public	bool			operator!=(	final  idVec4 &a ) final ;						// exact compare, no epsilon
        //public	idVec4 &		operator-=( final  idVec4 &a );
        //public	idVec4 &		operator/=( final  idVec4 &a );
        //public	idVec4 &		operator/=( final  float a );
        //public	idVec4 &		operator*=( final  float a );
        //
        //public	friend idVec4	operator*( final  float a, final  idVec4 b );
        fun Compare(a: idVec4?): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y && z == a.z && w == a.w
        }

        fun Compare(a: idVec4?, epsilon: Float): Boolean { // compare with epsilon
            if (Math.abs(x - a.x) > epsilon) {
                return false
            }
            if (Math.abs(y - a.y) > epsilon) {
                return false
            }
            return if (Math.abs(z - a.z) > epsilon) {
                false
            } else Math.abs(w - a.w) <= epsilon
        }

        fun Length(): Float {
            return idMath.Sqrt(x * x + y * y + z * z + w * w)
        }

        fun LengthSqr(): Float {
            return x * x + y * y + z * z + w * w
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength = x * x + y * y + z * z + w * w
            invLength = idMath.InvSqrt(sqrLength)
            x *= invLength
            y *= invLength
            z *= invLength
            w *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength = x * x + y * y + z * z + w * w
            invLength = idMath.RSqrt(sqrLength)
            x *= invLength
            y *= invLength
            z *= invLength
            w *= invLength
            return invLength * sqrLength
        }

        //public	idVec2 &		ToVec2( void );
        override fun GetDimension(): Int {
            return 4
        }

        //public	idVec3 &		ToVec3( void );
        @Deprecated("")
        fun ToVec2(): idVec2? {
//	return *reinterpret_cast<const idVec2 *>(this);
            return idVec2(x, y)
        }

        //public	float *			ToFloatPtr( void );
        @Deprecated("")
        fun ToVec3(): idVec3 {
//	return *reinterpret_cast<const idVec3 *>(this);
            return idVec3(x, y, z)
        }

        fun ToFloatPtr(): FloatArray? {
            return floatArrayOf(x, y, z, w) //TODO:put shit in array si we can referef it
        }

        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        override fun toString(): String {
            return "$x $y $z $w"
        }

        /*
         =============
         Lerp

         Linearly inperpolates one vector to another.
         =============
         */
        fun Lerp(v1: idVec4?, v2: idVec4?, l: Float) {
            if (l <= 0.0f) {
//		(*this) = v1;
                x = v1.x
                y = v1.y
                z = v1.z
                w = v1.w
            } else if (l >= 1.0f) {
//		(*this) = v2;
                x = v2.x
                y = v2.y
                z = v2.z
                w = v2.w
            } else {
//		(*this) = v1 + l * ( v2 - v1 );
                w = v1.w + l * (v2.w - v1.w)
                x = v1.x + l * (v2.x - v1.x)
                y = v1.y + l * (v2.y - v1.y)
                z = v1.z + l * (v2.z - v1.z)
            }
        }

        override fun oSet(a: idVec4?): idVec4? {
            x = a.x
            y = a.y
            z = a.z
            w = a.w
            return this
        }

        fun oSet(a: idVec3): idVec4? {
            x = a.x
            y = a.y
            z = a.z
            return this
        }

        override fun oGet(i: Int): Float { //TODO:rename you lazy ass
            return when (i) {
                1 -> y
                2 -> z
                3 -> w
                else -> x
            }
        }

        override fun oSet(i: Int, value: Float): Float { //TODO:rename you lazy ass
            return when (i) {
                1 -> value.also { y = it }
                2 -> value.also { z = it }
                3 -> value.also { w = it }
                else -> value.also { x = it }
            }
        }

        fun oPluSet(i: Int, value: Float): Float {
            return when (i) {
                1 -> value.let { y += it; y }
                2 -> value.let { z += it; z }
                3 -> value.let { w += it; w }
                else -> value.let { x += it; x }
            }
        }

        override fun AllocBuffer(): ByteBuffer? {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer?) {
            x = buffer.getFloat()
            y = buffer.getFloat()
            z = buffer.getFloat()
            w = buffer.getFloat()
        }

        override fun Write(): ByteBuffer? {
            val buffer = AllocBuffer()
            buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w).flip()
            return buffer
        }

        override fun oDivide(a: Float): idVec4? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            @Transient
            val SIZE = 4 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
            private var DBG_counter = 0
            fun generateArray(length: Int): Array<idVec4?>? {
                return Stream.generate { idVec4() }.limit(length.toLong()).toArray { _Dummy_.__Array__() }
            }

            fun toByteBuffer(vecs: Array<idVec4?>?): ByteBuffer? {
                val data = BufferUtils.createByteBuffer(BYTES * vecs.size)
                for (vec in vecs) {
                    data.put(vec.Write().rewind())
                }
                return data.flip()
            }
        }
    }

    //===============================================================
    //
    //	idVec5 - 5D vector
    //
    //===============================================================
    class idVec5 : idVec<idVec5?>, SERiAL {
        var s = 0f
        var t = 0f
        var x = 0f
        var y = 0f
        var z = 0f

        constructor()
        constructor(xyz: idVec3, st: idVec2?) {
            x = xyz.x
            y = xyz.y
            z = xyz.z
            //	s = st[0];
            s = st.x
            //	t = st[1];
            t = st.y
        }

        constructor(x: Float, y: Float, z: Float, s: Float, t: Float) {
            this.x = x
            this.y = y
            this.z = z
            this.s = s
            this.t = t
        }

        constructor(a: idVec3) {
            x = a.x
            y = a.y
            z = a.z
        }

        //copy constructor
        constructor(a: idVec5?) {
            x = a.x
            y = a.y
            z = a.z
            s = a.s
            t = a.t
        }

        //public	float			operator[]( int index ) final ;
        override fun oGet(i: Int): Float { //TODO:rename you lazy sod
            return when (i) {
                1 -> y
                2 -> z
                3 -> s
                4 -> t
                else -> x
            }
        }

        override fun oSet(i: Int, value: Float): Float {
            return when (i) {
                1 -> value.also { y = it }
                2 -> value.also { z = it }
                3 -> value.also { s = it }
                4 -> value.also { t = it }
                else -> value.also { x = it }
            }
        }

        override fun oSet(a: idVec5?): idVec5? {
            x = a.x
            y = a.y
            z = a.z
            s = a.s
            t = a.t
            return this
        }

        //public	float &			operator[]( int index );
        //public	idVec5 &		operator=( final  idVec3 &a );
        fun oSet(a: idVec3): idVec5? {
            x = a.x
            y = a.y
            z = a.z
            return this
        }

        override fun GetDimension(): Int {
            return 5
        }

        fun ToVec3(): idVec3 {
//	return *reinterpret_cast<const idVec3 *>(this);
            return idVec3(x, y, z)
        }

        //public	float *			ToFloatPtr( void );
        //public	idVec3 &		ToVec3( void );
        fun ToFloatPtr(): FloatArray? {
            return floatArrayOf(x, y, z) //TODO:array!?
        }

        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        fun Lerp(v1: idVec5?, v2: idVec5?, l: Float) {
            if (l <= 0.0f) {
                this.oSet(v1) //(*this) = v1;
            } else if (l >= 1.0f) {
                this.oSet(v2) //(*this) = v2;
            } else {
                x = v1.x + l * (v2.x - v1.x)
                y = v1.y + l * (v2.y - v1.y)
                z = v1.z + l * (v2.z - v1.z)
                s = v1.s + l * (v2.s - v1.s)
                t = v1.t + l * (v2.t - v1.t)
            }
        }

        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oPlus(a: idVec5?): idVec5? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oMinus(a: idVec5?): idVec5? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oMultiply(a: idVec5?): Float {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oDivide(a: Float): idVec5? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun ToVec3_oMulSet(axis: idMat3?) {
            this.oSet(ToVec3().oMulSet(axis))
        }

        fun ToVec3_oPluSet(origin: idVec3) {
            this.oSet(ToVec3().oPluSet(origin))
        }

        companion object {
            @Transient
            val SIZE = 5 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
            fun generateArray(length: Int): Array<idVec5?>? {
                return Stream.generate { idVec5() }.limit(length.toLong()).toArray { _Dummy_.__Array__() }
            }
        }
    }

    //===============================================================
    //
    //	idVec6 - 6D vector
    //
    //===============================================================
    class idVec6 : idVec<idVec6?>, SERiAL {
        private val DBG_count = DBG_counter++

        //
        //
        var p: FloatArray? = FloatArray(6)

        constructor() {
            DBG_idVec6++
            val a = 0
        }

        constructor(a: FloatArray?) {
//	memcpy( p, a, 6 * sizeof( float ) );
            System.arraycopy(a, 0, p, 0, 6)
        }

        constructor(v: idVec6?) {
            System.arraycopy(v.p, 0, p, 0, 6)
        }

        constructor(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
            p.get(0) = a1
            p.get(1) = a2
            p.get(2) = a3
            p.get(3) = a4
            p.get(4) = a5
            p.get(5) = a6
        }

        fun Set(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
            p.get(0) = a1
            p.get(1) = a2
            p.get(2) = a3
            p.get(3) = a4
            p.get(4) = a5
            p.get(5) = a6
        }

        override fun Zero() {
            p.get(5) = 0.0f
            p.get(4) = p.get(5)
            p.get(3) = p.get(4)
            p.get(2) = p.get(3)
            p.get(1) = p.get(2)
            p.get(0) = p.get(1)
        }

        //public 	float			operator[]( final  int index ) final ;
        //public 	float &			operator[]( final  int index );
        fun oNegative(): idVec6? {
            return idVec6(-p.get(0), -p.get(1), -p.get(2), -p.get(3), -p.get(4), -p.get(5))
        }

        override fun oMultiply(a: Float): idVec6? {
            return idVec6(p.get(0) * a, p.get(1) * a, p.get(2) * a, p.get(3) * a, p.get(4) * a, p.get(5) * a)
        }

        //public 	idVec6			operator/( final  float a ) final ;
        override fun oMultiply(a: idVec6?): Float {
            return p.get(0) * a.p.get(0) + p.get(1) * a.p.get(1) + p.get(2) * a.p.get(2) + p.get(3) * a.p.get(3) + p.get(
                4
            ) * a.p.get(4) + p.get(5) * a.p.get(5)
        }

        //public 	idVec6			operator-( final  idVec6 &a ) final ;
        override fun oPlus(a: idVec6?): idVec6? {
            return idVec6(
                p.get(0) + a.p.get(0),
                p.get(1) + a.p.get(1),
                p.get(2) + a.p.get(2),
                p.get(3) + a.p.get(3),
                p.get(4) + a.p.get(4),
                p.get(5) + a.p.get(5)
            )
        }

        //public 	idVec6 &		operator*=( final  float a );
        //public 	idVec6 &		operator/=( final  float a );
        override fun oPluSet(a: idVec6?): idVec6? {
            p.get(0) += a.p.get(0)
            p.get(1) += a.p.get(1)
            p.get(2) += a.p.get(2)
            p.get(3) += a.p.get(3)
            p.get(4) += a.p.get(4)
            p.get(5) += a.p.get(5)
            return this
        }

        //public 	idVec6 &		operator-=( final  idVec6 &a );
        //
        //public 	friend idVec6	operator*( final  float a, final  idVec6 b );
        fun Compare(a: idVec6?): Boolean { // exact compare, no epsilon
            return (p.get(0) == a.p.get(0) && p.get(1) == a.p.get(1) && p.get(2) == a.p.get(2)
                    && p.get(3) == a.p.get(3) && p.get(4) == a.p.get(4) && p.get(5) == a.p.get(5))
        }

        fun Compare(a: idVec6?, epsilon: Float): Boolean { // compare with epsilon
            if (Math.abs(p.get(0) - a.p.get(0)) > epsilon) {
                return false
            }
            if (Math.abs(p.get(1) - a.p.get(1)) > epsilon) {
                return false
            }
            if (Math.abs(p.get(2) - a.p.get(2)) > epsilon) {
                return false
            }
            if (Math.abs(p.get(3) - a.p.get(3)) > epsilon) {
                return false
            }
            return if (Math.abs(p.get(4) - a.p.get(4)) > epsilon) {
                false
            } else Math.abs(p.get(5) - a.p.get(5)) <= epsilon
        }

        //public 	bool			operator==(	final  idVec6 &a ) final ;						// exact compare, no epsilon
        //public 	bool			operator!=(	final  idVec6 &a ) final ;						// exact compare, no epsilon
        fun Length(): Float {
            return idMath.Sqrt(
                p.get(0) * p.get(0) + p.get(1) * p.get(1) + p.get(2) * p.get(2) + p.get(3) * p.get(3) + p.get(
                    4
                ) * p.get(4) + p.get(5) * p.get(5)
            )
        }

        fun LengthSqr(): Float {
            return p.get(0) * p.get(0) + p.get(1) * p.get(1) + p.get(2) * p.get(2) + p.get(3) * p.get(3) + p.get(4) * p.get(
                4
            ) + p.get(5) * p.get(5)
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength =
                p.get(0) * p.get(0) + p.get(1) * p.get(1) + p.get(2) * p.get(2) + p.get(3) * p.get(3) + p.get(4) * p.get(
                    4
                ) + p.get(5) * p.get(5)
            invLength = idMath.InvSqrt(sqrLength)
            p.get(0) *= invLength
            p.get(1) *= invLength
            p.get(2) *= invLength
            p.get(3) *= invLength
            p.get(4) *= invLength
            p.get(5) *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength =
                p.get(0) * p.get(0) + p.get(1) * p.get(1) + p.get(2) * p.get(2) + p.get(3) * p.get(3) + p.get(4) * p.get(
                    4
                ) + p.get(5) * p.get(5)
            invLength = idMath.RSqrt(sqrLength)
            p.get(0) *= invLength
            p.get(1) *= invLength
            p.get(2) *= invLength
            p.get(3) *= invLength
            p.get(4) *= invLength
            p.get(5) *= invLength
            return invLength * sqrLength
        }

        override fun GetDimension(): Int {
            return 6
        }

        fun SubVec3(index: Int): idVec3 {
//	return *reinterpret_cast<const idVec3 *>(p + index * 3);
            var index = index
            return idVec3(p.get(3.let { index *= it; index }), p.get(index + 1), p.get(index + 2))
        }

        //public 	idVec3 &		SubVec3( int index );
        fun ToFloatPtr(): FloatArray? {
            return p
        }

        //public 	float *			ToFloatPtr( void );
        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        override fun oSet(a: idVec6?): idVec6? {
            p.get(0) = a.p.get(0)
            p.get(1) = a.p.get(1)
            p.get(2) = a.p.get(2)
            p.get(3) = a.p.get(3)
            p.get(4) = a.p.get(4)
            p.get(5) = a.p.get(5)
            return this
        }

        override fun oGet(index: Int): Float {
            return p.get(index)
        }

        override fun oSet(index: Int, value: Float): Float {
            return value.also { p.get(index) = it }
        }

        //
        //        public void setP(final int index, final float value) {
        //            p[index] = value;
        //        }
        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oMinus(a: idVec6?): idVec6? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oDivide(a: Float): idVec6? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun SubVec3_oSet(i: Int, v: idVec3) {
            System.arraycopy(v.ToFloatPtr(), 0, p, i * 3, 3)
        }

        fun SubVec3_oPluSet(i: Int, v: idVec3): idVec3 {
            val off = i * 3
            p.get(off + 0) += v.x
            p.get(off + 1) += v.y
            p.get(off + 2) += v.z
            return idVec3(p, off)
        }

        fun SubVec3_oMinSet(i: Int, v: idVec3): idVec3 {
            return SubVec3_oPluSet(i, v.oNegative())
        }

        fun SubVec3_oMulSet(i: Int, v: Float) {
            val off = i * 3
            p.get(off + 0) *= v
            p.get(off + 1) *= v
            p.get(off + 2) *= v
        }

        fun SubVec3_Normalize(i: Int): Float {
            val v = SubVec3(i)
            val normalize = v.Normalize()
            SubVec3_oSet(i, v)
            return normalize
        }

        override fun hashCode(): Int {
            return Arrays.hashCode(p)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val idVec6 = o as idVec6?
            return Arrays.equals(p, idVec6.p)
        }

        override fun toString(): String {
            return "idVec6{" +
                    "p=" + Arrays.toString(p) +
                    '}'
        }

        companion object {
            @Transient
            val SIZE = 6 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
            private var DBG_counter = 0
            private var DBG_idVec6 = 0
        }
    }

    //===============================================================
    //
    //	idVecX - arbitrary sized vector
    //
    //  The vector lives on 16 byte aligned and 16 byte padded memory.
    //
    //	NOTE: due to the temporary memory pool idVecX cannot be used by multiple threads
    //
    //===============================================================
    class idVecX {
        var p // memory the vector is stored
                : FloatArray?
        var VECX_SIMD = false
        private var alloced // if -1 p points to data set with SetData
                : Int

        //
        //
        private var size // size of the vector
                : Int

        constructor() {
            alloced = 0
            size = alloced
            p = null
        }

        constructor(length: Int) {
            alloced = 0
            size = alloced
            p = null
            SetSize(length)
        }

        constructor(length: Int, data: FloatArray?) {
            alloced = 0
            size = alloced
            p = null
            SetData(length, data)
        }

        @Deprecated("")
        fun VECX_CLEAREND() { //TODO:is this function need for Java?
            var s = size
            ////            while (s < ((s + 3) & ~3)) {
            while (s < p.size) {
                p.get(s++) = 0.0f
            }
        }

        //public					~idVecX( void );
        //public	float			operator[]( const int index ) const;
        fun oGet(index: Int): Float {
            return p.get(index)
        }

        fun oSet(index: Int, value: Float): Float {
            return value.also { p.get(index) = it }
        }

        //public	float &			operator[]( const int index );
        //public	idVecX			operator-() const;
        fun oNegative(): idVecX? {
            var i: Int
            val m = idVecX()
            m.SetTempSize(size)
            i = 0
            while (i < size) {
                m.p.get(i) = -p.get(i)
                i++
            }
            return m
        }

        //public	idVecX &		operator=( const idVecX &a );
        fun oSet(a: idVecX?): idVecX? {
            SetSize(a.size)
            System.arraycopy(a.p, 0, p, 0, p.size)
            tempIndex = 0
            return this
        }

        fun oMultiply(a: Float): idVecX? {
            val m = idVecX()
            m.SetTempSize(size)
            if (VECX_SIMD) {
                Simd.SIMDProcessor.Mul16(m.p, p, a, size)
            } else {
                var i: Int
                i = 0
                while (i < size) {
                    m.p.get(i) = p.get(i) * a
                    i++
                }
            }
            return m
        }

        //public	idVecX			operator/( const float a ) const;
        //public	float			operator*( const idVecX &a ) const;
        fun oMultiply(a: idVecX?): Float {
            var i: Int
            var sum = 0.0f
            assert(size == a.size)
            i = 0
            while (i < size) {
                sum += p.get(i) * a.p.get(i)
                i++
            }
            return sum
        }

        //public	idVecX			operator-( const idVecX &a ) const;
        //public	idVecX			operator+( const idVecX &a ) const;
        fun oPlus(a: idVecX?): idVecX? {
            val m = idVecX()
            assert(size == a.size)
            m.SetTempSize(size)
            //#ifdef VECX_SIMD
//	SIMDProcessor->Add16( m.p, p, a.p, size );
//#else
            var i: Int
            i = 0
            while (i < size) {
                m.p.get(i) = p.get(i) + a.p.get(i)
                i++
            }
            //#endif
            return m
        }

        //public	idVecX &		operator*=( const float a );
        fun oMulSet(a: Float): idVecX? {
//#ifdef VECX_SIMD
//	SIMDProcessor->MulAssign16( p, a, size );
//#else
            var i: Int
            i = 0
            while (i < size) {
                p.get(i) *= a
                i++
            }
            //#endif
            return this
        }

        //public	idVecX &		operator/=( const float a );
        //public	idVecX &		operator+=( const idVecX &a );
        //public	idVecX &		operator-=( const idVecX &a );
        //public	friend idVecX	operator*( const float a, const idVecX b );
        fun Compare(a: idVecX?): Boolean { // exact compare, no epsilon
            var i: Int
            assert(size == a.size)
            i = 0
            while (i < size) {
                if (p.get(i) != a.p.get(i)) {
                    return false
                }
                i++
            }
            return true
        }

        fun Compare(a: idVecX?, epsilon: Float): Boolean { // compare with epsilon
            var i: Int
            assert(size == a.size)
            i = 0
            while (i < size) {
                if (Math.abs(p.get(i) - a.p.get(i)) > epsilon) {
                    return false
                }
                i++
            }
            return true
        }

        //public	bool			operator==(	const idVecX &a ) const;						// exact compare, no epsilon
        //public	bool			operator!=(	const idVecX &a ) const;						// exact compare, no epsilon
        fun SetSize(newSize: Int) {
            val alloc = newSize + 3 and 3.inv()
            if (alloc > alloced && alloced != -1) {
                if (p != null) {
                    p = null
                }
                p = FloatArray(alloc)
                alloced = alloc
            }
            size = newSize
            VECX_CLEAREND()
        }

        @JvmOverloads
        fun ChangeSize(newSize: Int, makeZero: Boolean = false) {
            val alloc = newSize + 3 and 3.inv()
            if (alloc > alloced && alloced != -1) {
                val oldVec = p
                //		p = (float *) Mem_Alloc16( alloc * sizeof( float ) );
                p = FloatArray(alloc)
                alloced = alloc
                if (oldVec != null) {
                    System.arraycopy(oldVec, 0, p, 0, size)
                    //			Mem_Free16( oldVec );//garbage collect me!
                } //TODO:ifelse
                if (makeZero) {
                    // zero any new elements
                    for (i in size until newSize) {
                        p.get(i) = 0.0f
                    }
                }
            }
            size = newSize
            VECX_CLEAREND()
        }

        fun GetSize(): Int {
            return size
        }

        fun SetData(length: Int, data: FloatArray?) {
            if (p != null && (p.get(0) < tempPtr.get(0) || p.get(0) >= tempPtr.get(0) + VECX_MAX_TEMP) && alloced != -1) {
//		Mem_Free16( p );
                p = null
            }
            //	assert( ( ( (int) data ) & 15 ) == 0 ); // data must be 16 byte aligned
            p = data
            size = length
            alloced = -1
            VECX_CLEAREND()
        }

        fun Zero() {
//#ifdef VECX_SIMD
//	SIMDProcessor.Zero16( p, size );
//#else
//	memset( p, 0, size * sizeof( float ) );
//#endif
            Arrays.fill(p, 0, size, 0f)
        }

        fun Zero(length: Int) {
            SetSize(length)
            //#ifdef VECX_SIMD
//	SIMDProcessor.Zero16( p, length );
//#else
//	memset( p, 0, size * sizeof( float ) );
//#endif
            Arrays.fill(p, 0, size, 0f)
        }

        @JvmOverloads
        fun Random(seed: Int, l: Float = 0.0f, u: Float = 1.0f) {
            var i: Int
            val c: Float
            val rnd = idRandom(seed)
            c = u - l
            i = 0
            while (i < size) {
                p.get(i) = l + rnd.RandomFloat() * c
                i++
            }
        }

        @JvmOverloads
        fun Random(length: Int, seed: Int, l: Float = 0.0f, u: Float = 1.0f) {
            var i: Int
            val c: Float
            val rnd = idRandom(seed)
            SetSize(length)
            c = u - l
            i = 0
            while (i < size) {
                if (idMatX.Companion.DISABLE_RANDOM_TEST) { //for testing.
                    p.get(i) = i
                } else {
                    p.get(i) = l + rnd.RandomFloat() * c
                }
                i++
            }
        }

        fun Negate() {
//#ifdef VECX_SIMD
//	SIMDProcessor.Negate16( p, size );
//#else
            var oGet: Int
            oGet = 0
            while (oGet < size) {
                p.get(oGet) = -p.get(oGet)
                oGet++
            }
            //#endif
        }

        fun Clamp(min: Float, max: Float) {
            var i: Int
            i = 0
            while (i < size) {
                if (p.get(i) < min) {
                    p.get(i) = min
                } else if (p.get(i) > max) {
                    p.get(i) = max
                }
                i++
            }
        }

        fun SwapElements(e1: Int, e2: Int): idVecX? {
            val tmp: Float
            tmp = p.get(e1)
            p.get(e1) = p.get(e2)
            p.get(e2) = tmp
            return this
        }

        fun Length(): Float {
            var i: Int
            var sum = 0.0f
            i = 0
            while (i < size) {
                sum += p.get(i) * p.get(i)
                i++
            }
            return idMath.Sqrt(sum)
        }

        fun LengthSqr(): Float {
            var i: Int
            var sum = 0.0f
            i = 0
            while (i < size) {
                sum += p.get(i) * p.get(i)
                i++
            }
            return sum
        }

        fun Normalize(): idVecX? {
            var i: Int
            val m = idVecX()
            val invSqrt: Float
            var sum = 0.0f
            m.SetTempSize(size)
            i = 0
            while (i < size) {
                sum += p.get(i) * p.get(i)
                i++
            }
            invSqrt = idMath.InvSqrt(sum)
            i = 0
            while (i < size) {
                m.p.get(i) = p.get(i) * invSqrt
                i++
            }
            return m
        }

        fun NormalizeSelf(): Float {
            val invSqrt: Float
            var sum = 0.0f
            var i: Int
            i = 0
            while (i < size) {
                sum += p.get(i) * p.get(i)
                i++
            }
            invSqrt = idMath.InvSqrt(sum)
            i = 0
            while (i < size) {
                p.get(i) *= invSqrt
                i++
            }
            return invSqrt * sum
        }

        fun GetDimension(): Int {
            return size
        }

        @Deprecated("readonly")
        fun SubVec3(index: Int): idVec3 {
            var index = index
            assert(index >= 0 && index * 3 + 3 <= size)
            //	return *reinterpret_cast<idVec3 *>(p + index * 3);
            return idVec3(p.get(3.let { index *= it; index }), p.get(index + 1), p.get(index + 2))
        }
        //public	idVec3 &		SubVec3( int index );

        @Deprecated("readonly")
        fun SubVec6(index: Int): idVec6? {
            var index = index
            assert(index >= 0 && index * 6 + 6 <= size)
            //	return *reinterpret_cast<idVec6 *>(p + index * 6);
            return idVec6(
                p.get(6.let { index *= it; index }),
                p.get(index + 1),
                p.get(index + 2),
                p.get(index + 3),
                p.get(index + 4),
                p.get(index + 5)
            )
        }

        //public	idVec6 &		SubVec6( int index );
        fun ToFloatPtr(): FloatArray? {
            return p
        }

        //public	float *			ToFloatPtr( void );
        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        fun SetTempSize(newSize: Int) {
            size = newSize
            alloced = newSize + 3 and 3.inv()
            assert(alloced < VECX_MAX_TEMP)
            if (tempIndex + alloced > VECX_MAX_TEMP) {
                tempIndex = 0
            }
            //            p = idVecX.tempPtr + idVecX.tempIndex;
//            for (int a = 0; a < idVecX.tempIndex; a++) {//TODO:trippple check
//                p[a] = idVecX.tempPtr[a + idVecX.tempIndex];
//            }
            p = FloatArray(alloced)
            tempIndex += alloced
            VECX_CLEAREND()
        }

        fun SubVec3_Normalize(i: Int) {
            val vec3 = idVec3(p, i * 3)
            vec3.Normalize()
            SubVec3_oSet(i, vec3)
        }

        fun SubVec3_oSet(i: Int, v: idVec3) {
            p.get(i * 3 + 0) = v.oGet(0)
            p.get(i * 3 + 1) = v.oGet(1)
            p.get(i * 3 + 2) = v.oGet(2)
        }

        fun SubVec6_oSet(i: Int, v: idVec6?) {
            p.get(i * 6 + 0) = v.oGet(0)
            p.get(i * 6 + 1) = v.oGet(1)
            p.get(i * 6 + 2) = v.oGet(2)
            p.get(i * 6 + 3) = v.oGet(3)
            p.get(i * 6 + 4) = v.oGet(4)
            p.get(i * 6 + 5) = v.oGet(5)
        }

        fun SubVec6_oPluSet(i: Int, v: idVec6?) {
            p.get(i * 6 + 0) += v.oGet(0)
            p.get(i * 6 + 1) += v.oGet(1)
            p.get(i * 6 + 2) += v.oGet(2)
            p.get(i * 6 + 3) += v.oGet(3)
            p.get(i * 6 + 4) += v.oGet(4)
            p.get(i * 6 + 5) += v.oGet(5)
        }

        companion object {
            // friend class idMatX;
            const val VECX_MAX_TEMP = 1024
            private val temp: FloatArray? = FloatArray(VECX_MAX_TEMP + 4) // used to store intermediate results
            private val tempPtr = temp // pointer to 16 byte aligned temporary memory
            private var tempIndex // index into memory pool, wraps around
                    = 0

            fun VECX_QUAD(x: Int): Int {
                return x + 3 and 3.inv()
            }

            @Deprecated("")
            fun VECX_ALLOCA(n: Int): FloatArray? {
//    ( (float *) _alloca16( VECX_QUAD( n ) ) )
//            float[] temp = new float[VECX_QUAD(n)];
//            Arrays.fill(temp, -107374176);
//
//            return temp;
                return FloatArray(VECX_QUAD(n))
            }
        }
    }

    //===============================================================
    //
    //	idPolar3
    //
    //===============================================================
    internal class idPolar3 {
        var radius = 0f
        var theta = 0f
        var phi = 0f

        constructor()
        constructor(radius: Float, theta: Float, phi: Float) {
            assert(radius > 0)
            this.radius = radius
            this.theta = theta
            this.phi = phi
        }

        fun Set(radius: Float, theta: Float, phi: Float) {
            assert(radius > 0)
            this.radius = radius
            this.theta = theta
            this.phi = phi
        }

        //public	float			operator[]( const int index ) const;
        //public	float &			operator[]( const int index );
        //public	idPolar3		operator-() const;
        //public	idPolar3 &		operator=( const idPolar3 &a );
        fun ToVec3(): idVec3 {
            val sp = CFloat()
            val cp = CFloat()
            val st = CFloat()
            val ct = CFloat()
            //            sp = cp = st = ct = 0.0f;
            idMath.SinCos(phi, sp, cp)
            idMath.SinCos(theta, st, ct)
            return idVec3(cp.getVal() * radius * ct.getVal(), cp.getVal() * radius * st.getVal(), radius * sp.getVal())
        }
    }
}