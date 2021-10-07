package neo.framework;

import java.util.Objects;

/**
 *
 */
public class BuildDefines {

    // build an exe with no CVAR_CHEAT controls
    public static final boolean ID_ALLOW_CHEATS = false;
    public static final boolean ID_ALLOW_D3XP = true;
    // don't define ID_ALLOW_TOOLS when we don't want tool code in the executable.
    public static final boolean ID_ALLOW_TOOLS = true;
    public static final boolean ID_BT_STUB;
    // verify checksums in clientinfo traffic
    // NOTE: this makes the network protocol incompatible
    public static final boolean ID_CLIENTINFO_TAGS = false;
    /*
     ===============================================================================

     Preprocessor settings for compiling different versions.

     ===============================================================================
     */
    // memory debugging
    //#define ID_REDIRECT_NEWDELETE
    //#define ID_DEBUG_MEMORY
    //#define ID_DEBUG_UNINITIALIZED_MEMORY
    // if enabled, the console won't toggle upon ~, unless you start the binary with +set com_allowConsole 1
    // Ctrl+Alt+~ will always toggle the console no matter what
    public static final boolean ID_CONSOLE_LOCK;
    // for win32 this is defined in preprocessor settings so that MFC can be
    // compiled out.
    public static final boolean ID_DEDICATED = false;
    // if this is defined, the executable positively won't work with any paks other
    // than the demo pak, even if productid is present.
    public static final boolean ID_DEMO_BUILD = Objects.equals(System.getProperty("ID_DEMO_BUILD"), Boolean.TRUE.toString());
    public static final boolean ID_ENABLE_CURL = true;
    public static final boolean ID_ENFORCE_KEY;
    // fake a pure client. useful to connect an all-debug client to a server
    public static final boolean ID_FAKE_PURE = false;
    // useful for network debugging, turns off 'LAN' checks, all IPs are classified 'internet'
    public static final boolean ID_NOLANADDRESS = false;
    public static final boolean ID_OPENAL = true;
    // let .dds be loaded from FS without altering pure state. only for developement.
    public static final boolean ID_PURE_ALLOWDDS = false;
    public static final boolean MACOS_X = System.getProperty("os.name").equals("MacOSX");
    public static final boolean _DEBUG = true;
    public static final boolean _WIN32 = System.getProperty("os.name").startsWith("Windows");
    public static final boolean WIN32 = _WIN32;
    public static final boolean __linux__ = System.getProperty("os.name").equals("Linux");

    static {
        if (_WIN32 || MACOS_X) {
            ID_CONSOLE_LOCK = !_DEBUG;
        } else {
            ID_CONSOLE_LOCK = false;
        }
    }

    static {
        if (__linux__) {
            ID_BT_STUB = _DEBUG;
        } else {
            ID_BT_STUB = true;
        }
    }

    static {
        ID_ENFORCE_KEY = !ID_DEDICATED && !ID_DEMO_BUILD;
    }

}
