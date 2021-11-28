package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.TempDump
import neo.framework.FileSystem_h.idFileList
import neo.framework.Session.logStats_t
import neo.idlib.Lib.idLib
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.math.Vector.idVec4
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.Rectangle.idRectangle
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import java.util.*

neo.framework.*
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
import neo.framework.EditField.FindMatches
import neo.framework.EditField.FindIndexMatch
import neo.framework.EditField.PrintMatches
import neo.framework.EditField.PrintCvarMatches
import neo.framework.EventLoop.idEventLoop
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
import neo.framework.Session_local.fileTIME_T
import neo.framework.Session_local.logCmd_t
import neo.framework.Session_local.mapSpawnData_t
import neo.framework.Session_local.idSessionLocal.cdKeyState_t
import neo.framework.Session_menu.idListSaveGameCompare
import java.util.stream.IntStream
import java.util.function.IntUnaryOperator
import java.nio.file.StandardOpenOption
import java.util.HashSet
import java.nio.LongBuffer
import java.lang.StackTraceElement
import java.lang.NoSuchFieldException
import javax.swing.undo.CannotUndoException
import org.junit.Before

/**
 *
 */
class MarkerWindow {
    class markerData_t {
        var mat: idMaterial? = null
        val rect: idRectangle? = idRectangle()
        var time = 0
    }

    class idMarkerWindow : idWindow {
        private val loggedStats: Array<logStats_t?>? = arrayOfNulls<logStats_t?>(Session.Companion.MAX_LOGGED_STATS)
        private var currentMarker = 0
        private var currentTime = 0
        private var imageBuff: IntArray?
        private val markerColor: idVec4? = null
        private var markerMat: idMaterial? = null
        private var markerStop: idMaterial? = null

        //
        //
        private val markerTimes: idList<markerData_t?>? = idList()
        private var numStats = 0

        //virtual ~idMarkerWindow();
        private val statData: idStr? = null

        //
        //        @Override
        //        public idWinVar GetWinVarByName(final String _name, boolean winLookup /*= false*/) {
        //            return super.GetWinVarByName(_name, winLookup);
        //        }
        //
        private var stopTime = 0

        constructor(gui: idUserInterfaceLocal?) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext?, gui: idUserInterfaceLocal?) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        fun HandleEvent(event: sysEvent_s?, updateVisuals: Boolean): String? {
            if (!(event.evType == sysEventType_t.SE_KEY && event.evValue2 != 0)) {
                return ""
            }
            val key = event.evValue
            if (event.evValue2 != 0 && key == KeyInput.K_MOUSE1) {
                gui.GetDesktop().SetChildWinVarVal("markerText", "text", "")
                val c = markerTimes.Num()
                var i: Int
                i = 0
                while (i < c) {
                    val md = markerTimes.oGet(i)
                    if (md.rect.Contains(gui.CursorX(), gui.CursorY())) {
                        currentMarker = i
                        gui.SetStateInt("currentMarker", md.time)
                        stopTime = md.time
                        gui.GetDesktop().SetChildWinVarVal(
                            "markerText",
                            "text",
                            Str.va("Marker set at %.2i:%.2i", md.time / 60 / 60, md.time / 60 % 60)
                        )
                        gui.GetDesktop().SetChildWinVarVal("markerText", "visible", "1")
                        gui.GetDesktop().SetChildWinVarVal("markerBackground", "matcolor", "1 1 1 1")
                        gui.GetDesktop().SetChildWinVarVal("markerBackground", "text", "")
                        gui.GetDesktop().SetChildWinVarVal("markerBackground", "background", md.mat.GetName())
                        break
                    }
                    i++
                }
                if (i == c) {
                    // no marker selected;
                    currentMarker = -1
                    gui.SetStateInt("currentMarker", currentTime)
                    stopTime = currentTime
                    gui.GetDesktop().SetChildWinVarVal(
                        "markerText",
                        "text",
                        Str.va("Marker set at %.2i:%.2i", currentTime / 60 / 60, currentTime / 60 % 60)
                    )
                    gui.GetDesktop().SetChildWinVarVal("markerText", "visible", "1")
                    gui.GetDesktop().SetChildWinVarVal("markerBackground", "matcolor", "0 0 0 0")
                    gui.GetDesktop().SetChildWinVarVal("markerBackground", "text", "No Preview")
                }
                val pct = gui.State().GetFloat("loadPct")
                val len = gui.State().GetInt("loadLength")
                if (stopTime > len * pct) {
                    return "cmdDemoGotoMarker"
                }
            } else if (key == KeyInput.K_MOUSE2) {
                stopTime = -1
                gui.GetDesktop().SetChildWinVarVal("markerText", "text", "")
                gui.SetStateInt("currentMarker", -1)
                return "cmdDemoGotoMarker"
            } else if (key == KeyInput.K_SPACE) {
                return "cmdDemoPauseFrame"
            }
            return ""
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            var pct: Float
            var r = idRectangle(clientRect)
            var len = gui.State().GetInt("loadLength")
            if (len == 0) {
                len = 1
            }
            if (numStats > 1) {
                val c = markerTimes.Num()
                if (c > 0) {
                    for (i in 0 until c) {
                        val md = markerTimes.oGet(i)
                        if (md.rect.w == 0f) {
                            md.rect.x = r.x + r.w * (md.time.toFloat() / len) - 8
                            md.rect.y = r.y + r.h - 20
                            md.rect.w = 16f
                            md.rect.h = 16f
                        }
                        dc.DrawMaterial(md.rect.x, md.rect.y, md.rect.w, md.rect.h, markerMat, markerColor)
                    }
                }
            }
            r.y += 10f
            if (r.w > 0 && r.Contains(gui.CursorX(), gui.CursorY())) {
                pct = (gui.CursorX() - r.x) / r.w
                currentTime = (len * pct).toInt()
                r.x = if (gui.CursorX() > r.x + r.w - 40) gui.CursorX() - 40 else gui.CursorX()
                r.y = gui.CursorY() - 15
                r.w = 40f
                r.h = 20f
                dc.DrawText(
                    Str.va("%.2i:%.2i", currentTime / 60 / 60, currentTime / 60 % 60),
                    0.25f,
                    0,
                    idDeviceContext.Companion.colorWhite,
                    r,
                    false
                )
            }
            if (stopTime >= 0 && markerStop != null) {
                r = idRectangle(clientRect)
                r.y += (r.h - 32) / 2
                pct = stopTime.toFloat() / len
                r.x += r.w * pct - 16
                val color = idVec4(1, 1, 1, 0.65f)
                dc.DrawMaterial(r.x, r.y, 32f, 32f, markerStop, color)
            }
        }

        override fun RouteMouseCoords(xd: Float, yd: Float): String? {
            val ret = super.RouteMouseCoords(xd, yd)
            val r = idRectangle()
            var i: Int
            val c = markerTimes.Num()
            var len = gui.State().GetInt("loadLength")
            if (len == 0) {
                len = 1
            }
            i = 0
            while (i < c) {
                val md = markerTimes.oGet(i)
                if (md.rect.Contains(gui.CursorY(), gui.CursorX())) {
                    gui.GetDesktop().SetChildWinVarVal("markerBackground", "background", md.mat.GetName())
                    gui.GetDesktop().SetChildWinVarVal("markerBackground", "matcolor", "1 1 1 1")
                    gui.GetDesktop().SetChildWinVarVal("markerBackground", "text", "")
                    break
                }
                i++
            }
            if (i >= c) {
                if (currentMarker == -1) {
                    gui.GetDesktop().SetChildWinVarVal("markerBackground", "matcolor", "0 0 0 0")
                    gui.GetDesktop().SetChildWinVarVal("markerBackground", "text", "No Preview")
                } else {
                    val md = markerTimes.oGet(currentMarker)
                    gui.GetDesktop().SetChildWinVarVal("markerBackground", "background", md.mat.GetName())
                    gui.GetDesktop().SetChildWinVarVal("markerBackground", "matcolor", "1 1 1 1")
                    gui.GetDesktop().SetChildWinVarVal("markerBackground", "text", "")
                }
            }
            return ret
        }

        override fun Activate(activate: Boolean, act: idStr?) {
            super.Activate(activate, act)
            if (activate) {
                var i: Int
                gui.GetDesktop().SetChildWinVarVal("markerText", "text", "")
                imageBuff = IntArray(512 * 64 * 4) // Mem_Alloc(512 * 64 * 4);
                markerTimes.Clear()
                currentMarker = -1
                currentTime = -1
                stopTime = -1
                statData.oSet(gui.State().GetString("statData"))
                numStats = 0
                if (statData.Length() != 0) {
                    val file = idLib.fileSystem.OpenFileRead(statData.toString())
                    if (file != null) {
                        numStats = file.ReadInt()
                        //                        file->Read(loggedStats, numStats * sizeof(loggedStats[0]));
                        i = 0
                        while (i < numStats) {
                            file.Read(loggedStats.get(i))
                            if (loggedStats.get(i).health < 0) {
                                loggedStats.get(i).health = 0
                            }
                            if (loggedStats.get(i).stamina < 0) {
                                loggedStats.get(i).stamina = 0
                            }
                            if (loggedStats.get(i).heartRate < 0) {
                                loggedStats.get(i).heartRate = 0
                            }
                            if (loggedStats.get(i).combat < 0) {
                                loggedStats.get(i).combat = 0
                            }
                            i++
                        }
                        idLib.fileSystem.CloseFile(file)
                    }
                }
                if (numStats > 1 && background != null) {
                    val markerPath = statData
                    markerPath.StripFilename()
                    val markers: idFileList?
                    markers = idLib.fileSystem.ListFiles(markerPath.toString(), ".tga", false, true)
                    var name: idStr
                    i = 0
                    while (i < markers.GetNumFiles()) {
                        name = idStr(markers.GetFile(i))
                        val md = markerData_t()
                        md.mat = DeclManager.declManager.FindMaterial(name)
                        md.mat.SetSort(Material.SS_GUI.toFloat())
                        name.StripPath()
                        name.StripFileExtension()
                        md.time = name.toString().toInt()
                        markerTimes.Append(md)
                        i++
                    }
                    idLib.fileSystem.FreeFileList(markers)
                    //                    memset(imageBuff, 0, 512 * 64 * 4);
                    Arrays.fill(imageBuff, 0, 512 * 64 * 4, 0)
                    val step = 511.0f / (numStats - 1)
                    val startX = 0f
                    var x1: Float
                    var y1: Float
                    var x2: Float
                    var y2: Float
                    x1 = 0 - step
                    i = 0
                    while (i < numStats - 1) {
                        x1 += step
                        x2 = x1 + step
                        y1 = 63 * (loggedStats.get(i).health.toFloat() / HEALTH_MAX)
                        y2 = 63 * (loggedStats.get(i + 1).health.toFloat() / HEALTH_MAX)
                        Line(x1, y1, x2, y2, imageBuff, -0xffff01)
                        y1 = 63 * (loggedStats.get(i).heartRate.toFloat() / RATE_MAX)
                        y2 = 63 * (loggedStats.get(i + 1).heartRate.toFloat() / RATE_MAX)
                        Line(x1, y1, x2, y2, imageBuff, -0xff0100)
                        // stamina not quite as high on graph so health does not get obscured with both at 100%
                        y1 = 62 * (loggedStats.get(i).stamina.toFloat() / STAMINA_MAX)
                        y2 = 62 * (loggedStats.get(i + 1).stamina.toFloat() / STAMINA_MAX)
                        Line(x1, y1, x2, y2, imageBuff, -0x10000)
                        y1 = 63 * (loggedStats.get(i).combat.toFloat() / COMBAT_MAX)
                        y2 = 63 * (loggedStats.get(i + 1).combat.toFloat() / COMBAT_MAX)
                        Line(x1, y1, x2, y2, imageBuff, -0xff0001)
                        i++
                    }
                    val stage = background.GetStage(0)
                    stage?.texture?.image?.get(0)
                        ?.UploadScratch(TempDump.wrapToNativeBuffer(TempDump.itob(imageBuff)), 512, 64)
                    //                    Mem_Free(imageBuff);
                    imageBuff = null
                }
            }
        }

        override fun ParseInternalVar(_name: String?, src: idParser?): Boolean {
            if (idStr.Companion.Icmp(_name, "markerMat") == 0) {
                val str = idStr()
                ParseString(src, str)
                markerMat = DeclManager.declManager.FindMaterial(str)
                markerMat.SetSort(Material.SS_GUI.toFloat())
                return true
            }
            if (idStr.Companion.Icmp(_name, "markerStop") == 0) {
                val str = idStr()
                ParseString(src, str)
                markerStop = DeclManager.declManager.FindMaterial(str)
                markerStop.SetSort(Material.SS_GUI.toFloat())
                return true
            }
            if (idStr.Companion.Icmp(_name, "markerColor") == 0) {
                ParseVec4(src, markerColor)
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        private fun CommonInit() {
            numStats = 0
            currentTime = -1
            currentMarker = -1
            stopTime = -1
            imageBuff = null
            markerMat = null
            markerStop = null
        }

        private fun Line(x1: Int, y1: Int, x2: Int, y2: Int, out: IntArray?, color: Int) {
            var x1 = x1
            var y1 = y1
            var deltax = Math.abs(x2 - x1)
            var deltay = Math.abs(y2 - y1)
            val incx = if (x1 > x2) -1 else 1
            val incy = if (y1 > y2) -1 else 1
            val right: Int
            val up: Int
            var dir: Int
            if (deltax > deltay) {
                right = deltay * 2
                up = right - deltax * 2
                dir = right - deltax
                while (deltax-- >= 0) {
                    Point(x1, y1, out, color)
                    x1 += incx
                    y1 += if (dir > 0) incy else 0
                    dir += if (dir > 0) up else right
                }
            } else {
                right = deltax * 2
                up = right - deltay * 2
                dir = right - deltay
                while (deltay-- >= 0) {
                    Point(x1, y1, out, color)
                    x1 += if (dir > 0) incx else 0
                    y1 += incy
                    dir += if (dir > 0) up else right
                }
            }
        }

        private fun Line(x1: Float, y1: Float, x2: Float, y2: Float, out: IntArray?, color: Int) {
            this.Line(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt(), out, color)
        }

        private fun Point(x: Int, y: Int, out: IntArray?, color: Int) {
            val index = (63 - y) * 512 + x
            if (index >= 0 && index < 512 * 64) {
                out.get(index) = color
            } else {
                idLib.common.Warning("Out of bounds on point %d : %d", x, y)
            }
        }

        companion object {
            const val COMBAT_MAX = 100
            const val HEALTH_MAX = 100
            const val RATE_MAX = 125
            const val STAMINA_MAX = 12
        }
    }
}