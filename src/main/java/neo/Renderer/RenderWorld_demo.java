package neo.Renderer;

import neo.idlib.containers.CInt;

/**
 *
 */
public class RenderWorld_demo {

    //#define WRITE_GUIS
    static class demoHeader_t {

        char[] mapname = new char[256];
        CInt sizeofRenderEntity = new CInt();
        CInt sizeofRenderLight = new CInt();
        CInt version = new CInt();
    }

}
