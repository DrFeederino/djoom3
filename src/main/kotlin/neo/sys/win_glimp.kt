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
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryUtil
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

//import org.lwjgl.LWJGLException;
//import org.lwjgl.opengl.Display;
//import org.lwjgl.opengl.DisplayMode;
/**
 *
 */
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
////#if 0
////	// for some reason, bounds checker claims that windows is
////	// writing past the bounds of dm in the get display frequency call
////	union {
////		DEVMODE dm;
////		byte	filler[1024];
////	} hack;
////#endif
//        DisplayMode dm = null;
////	int		cdsRet;
//
////	DisplayMode		devmode;
////	int			modeNum;
//        boolean matched;
//
//        // first make sure the user is not trying to select a mode that his card/monitor can't handle
//        matched = false;
//        DisplayMode[] displayModes = Display.getAvailableDisplayModes();
//        Arrays.sort(displayModes, Comparator.comparingInt(DisplayMode::getWidth));
//        for (DisplayMode devmode : displayModes) {
////		if ( !EnumDisplaySettings( NULL, modeNum, &devmode ) ) {
//            if (matched) {
//                // we got a resolution match, but not a frequency match
//                // so disable the frequency requirement
//                common.Printf("...^3%dhz is unsupported at %dx%d^0\n", parms.displayHz, parms.width, parms.height);
//                parms.displayHz = 0;
//                break;
//            }
////			common.Printf( "...^3%dx%d is unsupported in 32 bit^0\n", parms.width, parms.height );
////			return false;
////		}
//            if (devmode.getWidth() >= parms.width
//                    && devmode.getHeight() >= parms.height
//                    && devmode.getBitsPerPixel() == 32) {
//
//                matched = true;
//
//                if (parms.displayHz == 0 || devmode.getFrequency() == parms.displayHz) {
//                    dm = devmode;
//                    break;
//                }
//            }
//        }
//
////	memset( &dm, 0, sizeof( dm ) );
////	dm.dmSize = sizeof( dm );
////
////	dm.dmPelsWidth  = parms.width;
////	dm.dmPelsHeight = parms.height;
////	dm.dmBitsPerPel = 32;
////	dm.dmFields     = DM_PELSWIDTH | DM_PELSHEIGHT | DM_BITSPERPEL;
////
////	if ( parms.displayHz != 0 ) {
////		dm.dmDisplayFrequency = parms.displayHz;
////		dm.dmFields |= DM_DISPLAYFREQUENCY;
////	}
////
//        common.Printf("...calling CDS: ");
//
        // try setting the exact mode requested, because some drivers don't report
        // the low res modes in EnumDisplaySettings, but still work
//        if (dm != null) {
//            Display.setDisplayModeAndFullscreen(dm);
//            Display.setDisplayModeAndFullscreen(dm);
//            Display.setDisplayMode(dm);
//            Display.setVSyncEnabled(true);
//            Display.setTitle("BLAAAAAAAAAAAAAAAAAArrrGGGGHH!!");
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit())
            throw IllegalStateException("Unable to initialize GLFW");
        glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err).set())
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 1);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        window = glfwCreateWindow(parms.width, parms.height, "DOOM 3", MemoryUtil.NULL, MemoryUtil.NULL)

        val currentMode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!
        glfwSetWindowPos(
            window,
            (currentMode.width() - parms.width) / 2,
            (currentMode.height() - parms.height) / 2
        )
        glfwMakeContextCurrent(window)
        GL.createCapabilities();

        var glVersion = glGetString(GL_VERSION);

        glfwShowWindow(window)
        glfwFocusWindow(window)

        glfwSetInputMode(window, GLFW_LOCK_KEY_MODS, GLFW_FALSE)
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
        glfwSetKeyCallback(window, UsercmdGen.usercmdGen.keyboardCallback)
        glfwSetCursorPosCallback(window, UsercmdGen.usercmdGen.mouseCursorCallback)
        glfwSetScrollCallback(window, UsercmdGen.usercmdGen.mouseScrollCallback)
        glfwSetMouseButtonCallback(window, UsercmdGen.usercmdGen.mouseButtonCallback)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        idLib.common.Printf("ok\n")
        return true
//
//	//
//	// the exact mode failed, so scan EnumDisplaySettings for the next largest mode
//	//
//	common.Printf( "^3failed^0, " );
//
//	PrintCDSError( cdsRet );
//
//	common.Printf( "...trying next higher resolution:" );
//
//	// we could do a better matching job here...
//	for ( modeNum = 0 ; ; modeNum++ ) {
//		if ( !EnumDisplaySettings( null, modeNum, &devmode ) ) {
//			break;
//		}
//		if ( (int)devmode.dmPelsWidth >= parms.width
//			&& (int)devmode.dmPelsHeight >= parms.height
//			&& devmode.dmBitsPerPel == 32 ) {
//
//			if ( ( cdsRet = ChangeDisplaySettings( &devmode, CDS_FULLSCREEN ) ) == DISP_CHANGE_SUCCESSFUL ) {
//				common.Printf( "ok\n" );
//				win32.cdsFullscreen = true;
//
//				return true;
//			}
//			break;
//		}
//	}
//        common.Printf("\n...^3no high res mode found^0\n");
        return false
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
        Configuration.DISABLE_CHECKS.set(true)
        Configuration.DEBUG.set(true)
////	const char	*driverName;
////	HDC		hDC;
////
//        common.Printf("Initializing OpenGL subsystem\n");
//
//        // check our desktop attributes
////	hDC = GetDC( GetDesktopWindow() );
//        win32.desktopBitsPixel = Display.getDesktopDisplayMode().getBitsPerPixel();
//        win32.desktopWidth = Display.getDesktopDisplayMode().getWidth();
//        win32.desktopHeight = Display.getDesktopDisplayMode().getHeight();
////	ReleaseDC( GetDesktopWindow(), hDC );
//        // we can't run in a window unless it is 32 bpp
//        if (win32.desktopBitsPixel < 32 && !parms.fullScreen) {
//            common.Printf("^3Windowed mode requires 32 bit desktop depth^0\n");
//            return false;
//        }
//
//        // save the hardware gamma so it can be
//        // restored on exit
//        GLimp_SaveGamma();
//
////        // create our window classes if we haven't already
////        GLW_CreateWindowClasses();
////
////	// this will load the dll and set all our qgl* function pointers,
////	// but doesn't create a window
////
////	// r_glDriver is only intended for using instrumented OpenGL
////	// dlls.  Normal users should never have to use it, and it is
////	// not archived.
////	driverName = r_glDriver.GetString()[0] ? r_glDriver.GetString() : "opengl32";
////	if ( !QGL_Init( driverName ) ) {
////		common.Printf( "^3GLimp_Init() could not load r_glDriver \"%s\"^0\n", driverName );
////		return false;
////	}
////
////	// getting the wgl extensions involves creating a fake window to get a context,
////	// which is pretty disgusting, and seems to mess with the AGP VAR allocation
////	GLW_GetWGLExtensionsWithFakeWindow();
////
//        // try to change to fullscreen
////        if (parms.fullScreen) {//TODO:change this back.
//        try {
        println(Thread.currentThread().toString())
        if (!GLW_SetFullScreen(parms)) {
            GLimp_Shutdown()
            return false
        }
        //            //TODO: comment
//            Display.create();
////            Display.makeCurrent();
////            Display.create(new PixelFormat(), new ContextAttribs(4, 0));
//            Display.isCreated();
//            GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
////
////            GL11.glMatrixMode(GL11.GL_PROJECTION); // Select The Projection Matrix
////            GL11.glLoadIdentity(); // Reset The Projection Matrix
////            GL11.glMatrixMode(GL11.GL_MODELVIEW); // Select The Modelview Matrix
////            GL11.glLoadIdentity(); // Reset The Modelview Matrix
////
////            GL11.glShadeModel(GL11.GL_SMOOTH); // Enables Smooth Shading
////            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f); // Black Background
////            GL11.glClearDepth(1.0f); // Depth Buffer Setup
////            GL11.glEnable(GL11.GL_DEPTH_TEST); // Enables Depth Testing
////            GL11.glDepthFunc(GL11.GL_LEQUAL); // The Type Of Depth Test To Do
////            GL11.glHint(GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_NICEST); // Really Nice Perspective Calculations
//        } catch (LWJGLException ex) {
//            Logger.getLogger(win_glimp.class.getName()).log(Level.SEVERE, null, ex);
//            return false;
//        }
//
////        // try to create a window with the correct pixel format
////        // and init the renderer context
//////        if (!GLW_CreateWindow(parms)) {
////        if (!glcreate) {
////            GLimp_Shutdown();
////            return false;
////        }
//        // check logging
//        GLimp_EnableLogging(itob(r_logFile.GetInteger()));
        return true
    }

    // If the desired mode can't be set satisfactorily, false will be returned.
    // The renderer will then reset the glimpParms to "safe mode" of 640x480
    // fullscreen and try again.  If that also fails, the error will be fatal.
    fun GLimp_SetScreenParms(parms: glimpParms_t): Boolean {
//        final DisplayMode dm;
//        final boolean ret;
//
//        dm = new DisplayMode(parms.width, parms.height);
//
//        win32.cdsFullscreen = parms.fullScreen;
//
////        try {
////            if (parms.fullScreen) {
////                Display.setDisplayModeAndFullscreen(dm);
////                return Display.getDisplayMode().equals(dm) && Display.isFullscreen();
////            } else {
////                final int x = Display.getDesktopDisplayMode().getWidth() / 2;
////                final int y = Display.getDesktopDisplayMode().getHeight() / 2;
////                Display.setDisplayMode(dm);
////                Display.setLocation(x, y);
////                return Display.getDisplayMode().equals(dm);
////            }
////        } catch (LWJGLException e) {
////            e.printStackTrace();
////        }
        return false
    }

    /*
     ===================
     GLimp_Shutdown

     This routine does all OS specific shutdown procedures for the OpenGL
     subsystem.
     ===================
     */
    // will set up gl up with the new parms
    fun GLimp_Shutdown() {
//        final String[] success = {"failed", "success"};
//        int retVal;
//
//        common.Printf("Shutting down OpenGL subsystem\n");
//
////
////        // set current context to NULL
////        if (qwglMakeCurrent) {
////            retVal = qwglMakeCurrent(null, null) != 0;
////            common.Printf("...wglMakeCurrent( NULL, NULL ): %s\n", success[retVal]);
////        }
////
////        // delete HGLRC
////        if (win32.hGLRC) {
////            retVal = qwglDeleteContext(win32.hGLRC) != 0;
////            common.Printf("...deleting GL context: %s\n", success[retVal]);
////            win32.hGLRC = NULL;
////        }
////
////        // release DC
////        if (win32.hDC) {
////            retVal = ReleaseDC(win32.hWnd, win32.hDC) != 0;
////            common.Printf("...releasing DC: %s\n", success[retVal]);
////            win32.hDC = NULL;
////        }
//        // destroy window
//        if (Display.isCreated()) {
////        if (win32.hWnd) {
//            common.Printf("...destroying window\n");
//            Display.destroy();
////            ShowWindow(win32.hWnd, SW_HIDE);
////            DestroyWindow(win32.hWnd);
////            win32.hWnd = NULL;
//        }
//        // close the thread so the handle doesn't dangle
//        if (win32.renderThreadHandle != null) {
//            common.Printf("...closing smp thread\n");
//            win32.renderThreadHandle.interrupt();
//            win32.renderThreadHandle = null;
//        }
//
////        // restore gamma
////        GLimp_RestoreGamma();//TODO:check if our java opengl requires restoring gamma.
////
////        // shutdown QGL subsystem
////        QGL_Shutdown();//not necessary.
        glfwDestroyWindow(window)
        glfwTerminate()
    }

    // Destroys the rendering context, closes the window, resets the resolution,
    // and resets the gamma ramps.
    fun GLimp_SwapBuffers() {
        println(Thread.currentThread().toString())
        GL.createCapabilities();
        if (RenderSystem_init.r_swapInterval.IsModified()) {
            RenderSystem_init.r_swapInterval.ClearModified()

//        if (wglSwapIntervalEXT) {
//            //
//            // wglSwapinterval is a windows-private extension,
//            // so we must check for it here instead of portably
//            //
//            wglSwapIntervalEXT(r_swapInterval.GetInteger());
//        }
        }
        //            Display.swapBuffers();//qwglSwapBuffers(win32.hDC);
        glfwPollEvents()
        glfwSwapBuffers(window)

        //Sys_DebugPrintf( "*** SwapBuffers() ***\n" );
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