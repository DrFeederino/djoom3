package neo.framework

/**
 *
 */
object Licensee {
    const val ASYNC_PROTOCOL_MAJOR = 1
    val BASE_GAMEDIR: String? = "base"
    val CDKEY_FILE: String? = "doomkey"
    val CDKEY_TEXT: String? = """
         
         // Do not give this file to ANYONE.
         // id Software or Zenimax will NEVER ask you to send this file to them.
         
         """.trimIndent()
    val CD_BASEDIR: String? = "Doom"
    val CD_EXE: String? = "doom.exe"
    val CONFIG_FILE: String? = "DoomConfig.cfg"

    //
    val CONFIG_SPEC: String? = "config.spec"
    val EDITOR_DEFAULT_PROJECT: String? = "doom.qe4"
    val EDITOR_REGISTRY_KEY: String? = "DOOMRadiant"
    val EDITOR_WINDOWTEXT: String? = "DOOMEdit"
    val ENGINE_VERSION: String? = "DOOM 1.3.1" // printed in console

    /*
     ===============================================================================

     Definitions for information that is related to a licensee's game name and location.

     ===============================================================================
     */
    val GAME_NAME: String? = "DOOM 3" // appears on window titles and errors
    val IDNET_HOST: String? = "idnet.ua-corp.com"
    const val IDNET_MASTER_PORT = 27650
    val LINUX_DEFAULT_PATH: String? = "/usr/local/games/doom3"
    const val NUM_SERVER_PORTS = 4
    const val PORT_SERVER = 27666
    const val RENDERDEMO_VERSION = 2
    const val SAVEGAME_VERSION = 17
    val SOURCE_CODE_BASE_FOLDER: String? = "neo"
    val WIN32_CONSOLE_CLASS: String? = "DOOM 3 WinConsole"
    val WIN32_FAKE_WINDOW_CLASS_NAME: String? = "DOOM3_WGL_FAKE"
    val WIN32_WINDOW_CLASS_NAME: String? = "DOOM3"
    val XPKEY_FILE: String? = "xpkey"
}