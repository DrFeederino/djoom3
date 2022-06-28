package neo.sys

import neo.Renderer.RenderSystem_init
import neo.Renderer.tr_local
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.FileSystem_h
import neo.framework.UsercmdGen
import neo.idlib.Lib.idLib
import neo.idlib.Text.Str.idStr
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

object win_glimp {
    private val ospath: StringBuilder = StringBuilder(FileSystem_h.MAX_OSPATH)
    var errorCallback: GLFWErrorCallback? = null
    var window: Long = 0
    private var initialFrames = 0
    private var isEnabled = false

    /*
     ===================
     GLW_SetFullScreen
     ===================
     */
    fun GLW_SetFullScreen(parms: glimpParms_t): Boolean {
        if (!glfwInit())
            throw IllegalStateException("Unable to initialize GLFW")
        glfwDefaultWindowHints()
        glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err).set())
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        if (window == 0L) {
            window = glfwCreateWindow(parms.width, parms.height, "DOOM 3", MemoryUtil.NULL, MemoryUtil.NULL)
        }

        val currentMode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!
        glfwSetWindowPos(
            window,
            (currentMode.width() - parms.width) / 2,
            (currentMode.height() - parms.height) / 2
        )

        glfwMakeContextCurrent(window)
        GL.createCapabilities();
        glViewport(0, 0, parms.width, parms.height)
        glfwShowWindow(window)
        glfwFocusWindow(window)
        glfwPollEvents()

        glfwSetInputMode(window, GLFW_LOCK_KEY_MODS, GLFW_FALSE)
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
        glfwSetKeyCallback(window, UsercmdGen.usercmdGen.keyboardCallback)
        glfwSetCursorPosCallback(window, UsercmdGen.usercmdGen.mouseCursorCallback)
        glfwSetScrollCallback(window, UsercmdGen.usercmdGen.mouseScrollCallback)
        glfwSetMouseButtonCallback(window, UsercmdGen.usercmdGen.mouseButtonCallback)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        idLib.common.Printf("ok\n")
        return true
    }

    /*
     ===================
     GLimp_Init

     This is the platform specific OpenGL initialization function.  It
     is responsible for loading OpenGL, initializing it,
     creating a window of the appropriate size, doing
     fullscreen manipulations, etc.  Its overall responsibility is
     to make sure that a functional OpenGL subsystem is operating
     when it returns to the ref.

     If there is any failure, the renderer will revert back to safe
     parameters and try again.
     ===================
     */
    fun GLimp_Init(parms: glimpParms_t): Boolean {
        if (!GLW_SetFullScreen(parms)) {
            GLimp_Shutdown()
            return false
        }
        return true
    }

    // If the desired mode can't be set satisfactorily, false will be returned.
    // The renderer will then reset the glimpParms to "safe mode" of 640x480
    // fullscreen and try again.  If that also fails, the error will be fatal.
    fun GLimp_SetScreenParms(parms: glimpParms_t): Boolean {
        return false
    }

    /*
     ===================
     GLimp_Shutdown

     This routine does all OS specific shutdown procedures for the OpenGL
     subsystem.
     ===================
     */
    fun GLimp_Shutdown() {
        glfwDestroyWindow(window)
        glfwTerminate()
    }

    // Destroys the rendering context, closes the window, resets the resolution,
    // and resets the gamma ramps.
    fun GLimp_SwapBuffers() {
        println("Swapping")
        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    /*
     ========================
     GLimp_GetOldGammaRamp
     ========================
     */
    @Deprecated("")
    fun GLimp_SaveGamma() { //TODO:is this function needed?
//	HDC			hDC;
//	BOOL		success;
//
//	hDC = GetDC( GetDesktopWindow() );
//	success = GetDeviceGammaRamp( hDC, win32.oldHardwareGamma );
//	common->DPrintf( "...getting default gamma ramp: %s\n", success ? "success" : "failed" );
//	ReleaseDC( GetDesktopWindow(), hDC );
    }

    /*
     ========================
     GLimp_RestoreGamma
     ========================
     */
    @Deprecated("")
    fun GLimp_RestoreGamma() { //TODO:is this function needed?
//	HDC hDC;
//	BOOL success;
//
//	// if we never read in a reasonable looking
//	// table, don't write it out
//	if ( win32.oldHardwareGamma[0][255] == 0 ) {
//		return;
//	}
//
//	hDC = GetDC( GetDesktopWindow() );
//	success = SetDeviceGammaRamp( hDC, win32.oldHardwareGamma );
//	common->DPrintf ( "...restoring hardware gamma: %s\n", success ? "success" : "failed" );
//	ReleaseDC( GetDesktopWindow(), hDC );
    }

    // Calls the system specific swapbuffers routine, and may also perform
    // other system specific cvar checks that happen every frame.
    // This will not be called if 'r_drawBuffer GL_FRONT'
    fun GLimp_SetGamma(gamma: Float, brightness: Float, contrast: Float) {
//        try {
//            //    public static void GLimp_SetGamma(short[] red/*[256]*/, short[] green/*[256]*/, short[] blue/*[256]*/) {
////        Gamma.setDisplayGamma(null, gamma, 0, 0);
//            Display.setDisplayConfiguration(gamma, 0, 0);//TODO:check if GL was started.
//        } catch (LWJGLException ex) {
//            Logger.getLogger(win_glimp.class.getName()).log(Level.SEVERE, null, ex);
//        }
    }

    //    // Sets the hardware gamma ramps for gamma and brightness adjustment.
    //    // These are now taken as 16 bit values, so we can take full advantage
    //    // of dacs with >8 bits of precision
    //    public static boolean GLimp_SpawnRenderThread(glimpRenderThread function) {
    //        throw new TODO_Exception();
    //    }
    // Returns false if the system only has a single processor
    fun GLimp_BackEndSleep(): Any {
        throw TODO_Exception()
    }

    fun GLimp_FrontEndSleep() {
        throw TODO_Exception()
    }

    fun GLimp_WakeBackEnd(data: Any) {
        throw TODO_Exception()
    }

    // these functions implement the dual processor syncronization
    fun GLimp_ActivateContext() {
        throw TODO_Exception()
    }

    fun GLimp_DeactivateContext() {
        throw TODO_Exception()
    }

    // These are used for managing SMP handoffs of the OpenGL context
    // between threads, and as a performance tunining aid.  Setting
    // 'r_skipRenderContext 1' will call GLimp_DeactivateContext() before
    // the 3D rendering code, and GLimp_ActivateContext() afterwards.  On
    // most OpenGL implementations, this will result in all OpenGL calls
    // being immediate returns, which lets us guage how much time is
    // being spent inside OpenGL.
    fun GLimp_EnableLogging(enable: Boolean) { //TODO:activate this function. EDIT:make sure it works.
        var enable = enable
        try {
            // return if we're already active
            if (isEnabled && enable) {
                // decrement log counter and stop if it has reached 0
                RenderSystem_init.r_logFile.SetInteger(RenderSystem_init.r_logFile.GetInteger() - 1)
                if (RenderSystem_init.r_logFile.GetInteger() != 0) {
                    return
                }
                idLib.common.Printf("closing logfile '%s' after %d frames.\n", ospath, initialFrames)
                enable = false
                tr_local.tr.logFile!!.close()
                tr_local.tr.logFile = null
            }

            // return if we're already disabled
            if (!enable && !isEnabled) {
                return
            }
            isEnabled = enable
            if (enable) {
                if (TempDump.NOT(tr_local.tr.logFile)) {
//			struct tm		*newtime;
//			ID_TIME_T			aclock;
                    var qpath = ""
                    var i: Int
                    val path: String
                    initialFrames = RenderSystem_init.r_logFile.GetInteger()

                    // scan for an unused filename
                    i = 0
                    while (i < 9999) {
                        qpath = String.format("renderlog_%d.txt", i)
                        if (FileSystem_h.fileSystem.ReadFile(qpath, null, null) == -1) {
                            break // use this name
                        }
                        i++
                    }
                    path = FileSystem_h.fileSystem.RelativePathToOSPath(qpath, "fs_savepath")
                    idStr.Companion.Copynz(ospath, path)
                    tr_local.tr.logFile = FileChannel.open(Paths.get(ospath.toString()), TempDump.fopenOptions("wt"))

                    // write the time out to the top of the file
//			time( &aclock );
//			newtime = localtime( &aclock );
                    tr_local.tr.logFile!!.write(TempDump.atobb(String.format("// %s", Date())))
                    tr_local.tr.logFile!!.write(
                        TempDump.atobb(
                            String.format(
                                "// %s\n\n",
                                idLib.cvarSystem.GetCVarString("si_version")
                            )
                        )
                    )
                }
            }
        } catch (ex: IOException) {
            Logger.getLogger(win_glimp::class.java.name).log(Level.SEVERE, null, ex)
            idLib.common.Warning("---GLimp_EnableLogging---\n%s\n---", ex.message!!)
        }
    }

    /*
     ====================================================================

     IMPLEMENTATION SPECIFIC FUNCTIONS

     ====================================================================
     */
    class glimpParms_t {
        var displayHz = 0
        var fullScreen = false
        var height = 0
        var multiSamples = 0
        var stereo = false
        var width = 0
    }
}