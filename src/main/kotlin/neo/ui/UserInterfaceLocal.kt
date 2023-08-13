package neo.ui

import neo.Renderer.Material.idMaterial
import neo.Renderer.RenderSystem_init
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DemoFile.idDemoFile
import neo.framework.FileSystem_h.fileSystem
import neo.framework.File_h.idFile
import neo.framework.KeyInput.idKeyInput.KeysFromBinding
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT
import neo.idlib.Text.Lexer.LEXFL_ALLOWMULTICHARLITERALS
import neo.idlib.Text.Lexer.LEXFL_NOFATALERRORS
import neo.idlib.Text.Lexer.LEXFL_NOSTRINGCONCAT
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.va
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CBool
import neo.idlib.containers.List.idList
import neo.idlib.math.Vector.idVec4
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.ListGUI.idListGUI
import neo.ui.ListGUILocal.idListGUILocal
import neo.ui.Rectangle.idRectangle
import neo.ui.UserInterface.idUserInterface
import neo.ui.UserInterface.idUserInterface.idUserInterfaceManager
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinStr
import java.nio.ByteBuffer

/**
 *
 */
class UserInterfaceLocal {
    /*
     ===============================================================================

     idUserInterfaceLocal

     ===============================================================================
     */
    class idUserInterfaceLocal : idUserInterface() {
        // friend class idUserInterfaceManagerLocal;
        private val activateStr = idStr()
        private var active = false
        private var bindHandler: idWindow? = null

        //
        private var cursorX = 0f
        private var cursorY = 0f
        var desktop: idWindow? = null
        var interactive = false
        private var loading = false
        private val pendingCmd = idStr()

        //
        private val source = idStr()
        private val returnCmd = idStr()

        //
        private val state = idDict()

        //
        private var refs = 1

        //
        private var time = 0
        private val timeStamp = longArrayOf(0)
        private var uniqued = false

        //
        //
        // ~idUserInterfaceLocal();
        override fun Name(): String {
            return source.toString()
        }

        override fun Comment(): String? {
            return if (desktop != null) {
                desktop!!.GetComment()
            } else ""
        }

        override fun IsInteractive(): Boolean {
            return interactive
        }

        override fun InitFromFile(qpath: String?, rebuild: Boolean /*= true*/, cache: Boolean /*= true*/): Boolean {
            if (!(qpath != null && !qpath.isEmpty())) {
                // FIXME: Memory leak!!
                return false
            }

//            int sz = sizeof(idWindow.class);
//            sz = sizeof(idSimpleWindow.class);
            loading = true
            if (rebuild || desktop == null) {
                desktop = idWindow(this)
            }
            //            System.out.println("FAAAAAAAAAAAAAAAAAAR " + desktop);
            source.set(qpath)
            state.Set("text", "Test Text!")
            val src =
                idParser(LEXFL_NOFATALERRORS or LEXFL_NOSTRINGCONCAT or LEXFL_ALLOWMULTICHARLITERALS or LEXFL_ALLOWBACKSLASHSTRINGCONCAT)

            //Load the timestamp so reload guis will work correctly
            fileSystem.ReadFile(qpath, null, timeStamp)
            src.LoadFile(qpath)
            if (src.IsLoaded()) {
                val token = idToken()
                while (src.ReadToken(token)) {
                    if (Icmp(token, "windowDef") == 0) {
                        desktop!!.SetDC(UserInterface.uiManagerLocal.dc)
                        if (desktop!!.Parse(src, rebuild)) {
                            desktop!!.SetFlag(Window.WIN_DESKTOP)
                            desktop!!.FixupParms()
                        }
                        //                        continue;
                    }
                }
                state.Set("name", qpath)
            } else {
                desktop!!.SetDC(UserInterface.uiManagerLocal.dc)
                desktop!!.SetFlag(Window.WIN_DESKTOP)
                desktop!!.name = idStr("Desktop")
                desktop!!.text = idWinStr(va("Invalid GUI: %s", qpath)) //TODO:clean this mess up.
                desktop!!.rect.set(idRectangle(0.0f, 0.0f, 640.0f, 480.0f))
                desktop!!.drawRect.set(desktop!!.rect.data)
                desktop!!.foreColor.set(idVec4(1.0f, 1.0f, 1.0f, 1.0f))
                desktop!!.backColor.set(idVec4(0.0f, 0.0f, 0.0f, 1.0f))
                desktop!!.SetupFromState()
                Common.common.Warning("Couldn't load gui: '%s'", qpath)
            }
            interactive = desktop!!.Interactive()
            if (UserInterface.uiManagerLocal.guis.Find(this) == null) {
                UserInterface.uiManagerLocal.guis.Append(this)
            }
            loading = false
            return true
        }

        override fun HandleEvent(event: sysEvent_s, _time: Int, updateVisuals: CBool?): String? {
            time = _time
            //            System.out.println(System.nanoTime()+"HandleEvent time="+_time+" "+Common.com_ticNumber);
            if (bindHandler != null && event.evType === sysEventType_t.SE_KEY && event.evValue2 == 1) {
                val ret = bindHandler!!.HandleEvent(event, updateVisuals)
                bindHandler = null
                return ret
            }
            if (event.evType === sysEventType_t.SE_MOUSE) {
                cursorX += event.evValue.toFloat()
                cursorY += event.evValue2.toFloat()
                if (cursorX < 0) {
                    cursorX = 0f
                }
                if (cursorY < 0) {
                    cursorY = 0f
                }
            }
            return if (desktop != null) {
                desktop!!.HandleEvent(event, updateVisuals)
            } else ""
        }

        override fun HandleNamedEvent(namedEvent: String?) {
            desktop!!.RunNamedEvent(namedEvent)
        }

        override fun Redraw(_time: Int) {
            if (RenderSystem_init.r_skipGuiShaders!!.GetInteger() > 5) {
                return
            }
            if (!loading && desktop != null) {
                time = _time
                UserInterface.uiManagerLocal.dc.PushClipRect(UserInterface.uiManagerLocal.screenRect)
                desktop!!.Redraw(0f, 0f)
                UserInterface.uiManagerLocal.dc.PopClipRect()
            }
        }

        override fun DrawCursor() {
            val cursorX = floatArrayOf(cursorX)
            val cursorY = floatArrayOf(cursorY)
            if (null == desktop || desktop!!.GetFlags() and Window.WIN_MENUGUI != 0) {
                UserInterface.uiManagerLocal.dc.DrawCursor(cursorX, cursorY, 32.0f)
            } else {
                UserInterface.uiManagerLocal.dc.DrawCursor(cursorX, cursorY, 64.0f)
            }
        }

        override fun State(): idDict {
            return state
        }

        override fun DeleteStateVar(varName: String?) {
            state.Delete(varName!!)
        }

        override fun SetStateString(varName: String?, value: String?) {
            state.Set(varName, value!!)
        }

        override fun SetStateBool(varName: String?, value: Boolean) {
            state.SetBool(varName, value)
        }

        override fun SetStateInt(varName: String?, value: Int) {
            state.SetInt(varName, value)
        }

        override fun SetStateFloat(varName: String?, value: Float) {
            state.SetFloat(varName, value)
        }

        // Gets a gui state variable
        override fun GetStateString(varName: String?, defaultString: String? /*= ""*/): String? {
            return state.GetString(varName, defaultString)
        }

        fun GetStateBool(varName: String?, defaultString: String? /*= "0"*/): Boolean {
            return state.GetBool(varName, defaultString!!)
        }

        override fun GetStateInt(varName: String?, defaultString: String? /*= "0"*/): Int {
            return state.GetInt(varName, defaultString!!)
        }

        override fun GetStateFloat(varName: String?, defaultString: String? /*= "0"*/): Float {
            return state.GetFloat(varName, defaultString!!)
        }

        override fun StateChanged(_time: Int, redraw: Boolean) {
            time = _time
            if (desktop != null) {
                desktop!!.StateChanged(redraw)
            }
            interactive = if (state.GetBool("noninteractive")) {
                false
            } else {
                if (desktop != null) {
                    desktop!!.Interactive()
                } else {
                    false
                }
            }
        }

        override fun Activate(activate: Boolean, _time: Int): String {
            time = _time
            active = activate
            if (desktop != null) {
                activateStr.set("")
                desktop!!.Activate(activate, activateStr)
                return activateStr.toString()
            }
            return ""
        }

        override fun Trigger(_time: Int) {
            time = _time
            if (desktop != null) {
                desktop!!.Trigger()
            }
        }

        override fun ReadFromDemoFile(f: idDemoFile) {
//	idStr work;
            f.ReadDict(state)
            source.set(state.GetString("name"))
            if (desktop == null) {
                f.Log("creating new gui\n")
                desktop = idWindow(this)
                desktop!!.SetFlag(Window.WIN_DESKTOP)
                desktop!!.SetDC(UserInterface.uiManagerLocal.dc)
                desktop!!.ReadFromDemoFile(f)
            } else {
                f.Log("re-using gui\n")
                desktop!!.ReadFromDemoFile(f, false)
            }
            cursorX = f.ReadFloat()
            cursorY = f.ReadFloat()
            var add = true
            val c = UserInterface.uiManagerLocal.demoGuis.Num()
            for (i in 0 until c) {
                if (UserInterface.uiManagerLocal.demoGuis[i] == this) {
                    add = false
                    break
                }
            }
            if (add) {
                UserInterface.uiManagerLocal.demoGuis.Append(this)
            }
        }

        override fun WriteToDemoFile(f: idDemoFile) {
//	idStr work;
            f.WriteDict(state)
            if (desktop != null) {
                desktop!!.WriteToDemoFile(f)
            }
            f.WriteFloat(cursorX)
            f.WriteFloat(cursorY)
        }

        override fun WriteToSaveGame(savefile: idFile): Boolean {
            var len: Int
            var kv: idKeyValue?
            var string: String
            val num = state.GetNumKeyVals()
            savefile.WriteInt(num)
            for (i in 0 until num) {
                kv = state.GetKeyVal(i)
                len = kv!!.GetKey().Length()
                string = kv.GetKey().toString()
                savefile.WriteInt(len)
                savefile.WriteString(string)
                len = kv.GetValue().Length()
                string = kv.GetValue().toString()
                savefile.WriteInt(len)
                savefile.WriteString(string)
            }
            savefile.WriteBool(active)
            savefile.WriteBool(interactive)
            savefile.WriteBool(uniqued)
            savefile.WriteInt(time)
            len = activateStr.Length()
            savefile.WriteInt(len)
            savefile.WriteString(activateStr)
            len = pendingCmd.Length()
            savefile.WriteInt(len)
            savefile.WriteString(pendingCmd)
            len = returnCmd.Length()
            savefile.WriteInt(len)
            savefile.WriteString(returnCmd)
            savefile.WriteFloat(cursorX)
            savefile.WriteFloat(cursorY)
            desktop!!.WriteToSaveGame(savefile)
            return true
        }

        override fun ReadFromSaveGame(savefile: idFile): Boolean {
            val num: Int
            var i: Int
            var len: Int
            val key = idStr()
            val value = idStr()
            num = savefile.ReadInt()
            state.Clear()
            i = 0
            while (i < num) {
                len = savefile.ReadInt()
                key.Fill(' ', len)
                savefile.ReadString(key)
                len = savefile.ReadInt()
                value.Fill(' ', len)
                savefile.ReadString(value)
                state.Set(key, value)
                i++
            }
            active = savefile.ReadBool()
            interactive = savefile.ReadBool()
            uniqued = savefile.ReadBool()
            time = savefile.ReadInt()
            len = savefile.ReadInt()
            activateStr.Fill(' ', len)
            savefile.ReadString(activateStr)
            len = savefile.ReadInt()
            pendingCmd.Fill(' ', len)
            savefile.ReadString(pendingCmd)
            len = savefile.ReadInt()
            returnCmd.Fill(' ', len)
            savefile.ReadString(returnCmd)
            cursorX = savefile.ReadFloat()
            cursorY = savefile.ReadFloat()
            desktop!!.ReadFromSaveGame(savefile)
            return true
        }

        override fun SetKeyBindingNames() {
            if (null == desktop) {
                return
            }
            // walk the windows
            RecurseSetKeyBindingNames(desktop!!)
        }

        override fun IsUniqued(): Boolean {
            return uniqued
        }

        override fun SetUniqued(b: Boolean) {
            uniqued = b
        }

        override fun SetCursor(x: Float, y: Float) {
            cursorX = x
            cursorY = y
        }

        override fun CursorX(): Float {
            return cursorX
        }

        override fun CursorY(): Float {
            return cursorY
        }

        fun GetStateDict(): idDict {
            return state
        }

        fun GetSourceFile(): String {
            return source.toString()
        }

        fun  /*ID_TIME_T*/GetTimeStamp(): LongArray {
            return timeStamp
        }

        fun GetDesktop(): idWindow? {
            return desktop
        }

        fun SetBindHandler(win: idWindow?) {
            bindHandler = win
        }

        fun Active(): Boolean {
            return active
        }

        fun GetTime(): Int {
            return time
        }

        fun SetTime(_time: Int) {
            time = _time
        }

        fun ClearRefs() {
            refs = 0
        }

        fun AddRef() {
            refs++
        }

        fun GetRefs(): Int {
            return refs
        }

        fun RecurseSetKeyBindingNames(window: idWindow) {
            var i: Int
            val v = window.GetWinVarByName("bind")
            if (v != null) {
                SetStateString(v.GetName(), KeysFromBinding(v.GetName()))
            }
            i = 0
            while (i < window.GetChildCount()) {
                val next = window.GetChild(i)
                next?.let { RecurseSetKeyBindingNames(it) }
                i++
            }
        }

        fun GetPendingCmd(): idStr {
            return pendingCmd
        }

        fun GetReturnCmd(): idStr {
            return returnCmd
        }

        override fun GetStateboolean(varName: String?, defaultString: String?): Boolean {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oSet(FindGui: idUserInterface?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    /*
     ===============================================================================

     idUserInterfaceManagerLocal

     ===============================================================================
     */
    class idUserInterfaceManagerLocal : idUserInterfaceManager() {
        // friend class idUserInterfaceLocal;
        val dc = idDeviceContext()
        val demoGuis = idList<idUserInterfaceLocal?>()
        val guis = idList<idUserInterfaceLocal?>()
        val screenRect = idRectangle()

        //
        //
        override fun Init() {
            screenRect.set(idRectangle(0f, 0f, 640f, 480f))
            dc.Init()
        }

        override fun Shutdown() {
            guis.DeleteContents(true)
            demoGuis.DeleteContents(true)
            dc.Shutdown()
        }

        override fun Touch(name: String?) {
            val gui = Alloc()
            gui.InitFromFile(name)
            //	delete gui;
        }

        override fun WritePrecacheCommands(f: idFile) {
            val c = guis.Num()
            for (i in 0 until c) {
                val str = String.format("touchGui %s\n", guis[i]!!.Name())
                Common.common.Printf("%s", str)
                f.Printf("%s", str)
            }
        }

        override fun SetSize(width: Float, height: Float) {
            dc.SetSize(width, height)
        }

        override fun BeginLevelLoad() {
            val c = guis.Num()
            for (i in 0 until c) {
                if (guis[i]!!.GetDesktop()!!.GetFlags() and Window.WIN_MENUGUI == 0) {
                    guis[i]!!.ClearRefs()
                    /*
                     delete guis[ i ];
                     guis.RemoveIndex( i );
                     i--; c--;
                     */
                }
            }
        }

        override fun EndLevelLoad() {
            var c = guis.Num()
            var i = 0
            while (i < c) {
                if (guis[i]!!.GetRefs() == 0) {
                    //common.Printf( "purging %s.\n", guis[i].GetSourceFile() );

                    // use this to make sure no materials still reference this gui
                    var remove = true
                    for (j in 0 until DeclManager.declManager.GetNumDecls(declType_t.DECL_MATERIAL)) {
                        val material =
                            DeclManager.declManager.DeclByIndex(declType_t.DECL_MATERIAL, j, false) as idMaterial?
                        if (material!!.GlobalGui() === guis[i]) {
                            remove = false
                            break
                        }
                    }
                    if (remove) {
//				delete guis[ i ];
                        guis.RemoveIndex(i)
                        i--
                        c--
                    }
                }
                i++
            }
        }

        override fun Reload(all: Boolean) {
            val  /*ID_TIME_T*/ts = LongArray(1)
            val c = guis.Num()
            for (i in 0 until c) {
                if (!all) {
                    fileSystem.ReadFile(guis[i]!!.GetSourceFile(), null, ts)
                    if (ts[0] <= guis[i]!!.GetTimeStamp()[0]) {
                        continue
                    }
                }
                guis[i]!!.InitFromFile(guis[i]!!.GetSourceFile())
                Common.common.Printf("reloading %s.\n", guis[i]!!.GetSourceFile())
            }
        }

        override fun ListGuis() {
            val c = guis.Num()
            Common.common.Printf("\n   size   refs   name\n")
            var  /*size_t*/total = 0
            var copies = 0
            var unique = 0
            for (i in 0 until c) {
                val gui = guis[i]
                val isUnique = guis[i]!!.interactive
                if (isUnique) {
                    unique++
                } else {
                    copies++
                }
                Common.common.Printf(
                    "%6.1fk %4d (%s) %s ( %d transitions )\n",
                    0 / 1024.0f,
                    guis[i]!!.GetRefs(),
                    if (isUnique) "unique" else "copy",
                    guis[i]!!
                        .GetSourceFile(),
                    guis[i]!!.desktop!!.NumTransitions()
                )
                total += 0
            }
            Common.common.Printf(
                "===========\n  %d total Guis ( %d copies, %d unique ), %.2f total Mbytes",
                c,
                copies,
                unique,
                total / (1024.0f * 1024.0f)
            )
        }

        override fun CheckGui(qpath: String?): Boolean {
            val file: idFile? = fileSystem.OpenFileRead(qpath!!)
            if (file != null) {
                fileSystem.CloseFile(file)
                return true
            }
            return false
        }

        override fun Alloc(): idUserInterface {
            return idUserInterfaceLocal()
        }

        override fun DeAlloc(gui: idUserInterface?) {
            if (gui != null) {
                val c = guis.Num()
                for (i in 0 until c) {
                    if (guis[i] === gui) {
//				delete guis[i];
                        guis.RemoveIndex(i)
                        return
                    }
                }
            }
        }

        override fun FindGui(
            qpath: String?,
            autoLoad: Boolean /*= false*/,
            needInteractive: Boolean /*= false*/,
            forceUnique: Boolean /*= false*/
        ): idUserInterface? {
            val c = guis.Num()
            for (i in 0 until c) {
//		idUserInterfaceLocal gui = guis.get(i);
                if (0 == Icmp(guis[i]!!.GetSourceFile(), qpath!!)) {
                    if (!forceUnique && (needInteractive || guis[i]!!.IsInteractive())) {
                        break
                    }
                    guis[i]!!.AddRef()
                    return guis[i]
                }
            }
            if (autoLoad) {
                val gui = Alloc()
                if (gui.InitFromFile(qpath)) {
                    gui.SetUniqued(!forceUnique && needInteractive)
                    return gui
                    //                } else {
//			delete gui;
                }
            }
            return null
        }

        override fun FindDemoGui(qpath: String?): idUserInterface? {
            val c = demoGuis.Num()
            for (i in 0 until c) {
                if (0 == Icmp(demoGuis[i]!!.GetSourceFile(), qpath!!)) {
                    return demoGuis[i]
                }
            }
            return null
        }

        override fun AllocListGUI(): idListGUI {
            return idListGUILocal()
        }

        // This is unnecessary.
        override fun FreeListGUI(listgui: idListGUI?) {
//            delete listgui;
//            listgui = null
        }
    }
}
