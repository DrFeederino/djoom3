package neo.Tools.Compilers.DMap

import neo.Renderer.qgl
import neo.Renderer.tr_backend
import neo.TempDump.TODO_Exception
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Vector.idVec3
import org.lwjgl.opengl.GL11

/**
 *
 */
object gldraw {
    //============================================================
    const val GLSERV_PORT = 25001
    const val WIN_SIZE = 1024
    var draw_socket = 0

    //
    var wins_init = false
    fun Draw_ClearWindow() {
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glDrawBuffer(GL11.GL_FRONT)
        tr_backend.RB_SetGL2D()
        GL11.glClearColor(0.5f, 0.5f, 0.5f, 0f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)

//#if 0
//	int		w, h, g;
//	float	mx, my;
//
//	w = (dmapGlobals.drawBounds.b[1][0] - dmapGlobals.drawBounds.b[0][0]);
//	h = (dmapGlobals.drawBounds.b[1][1] - dmapGlobals.drawBounds.b[0][1]);
//
//	mx = dmapGlobals.drawBounds.b[0][0] + w/2;
//	my = dmapGlobals.drawBounds.b[1][1] + h/2;
//
//	g = w > h ? w : h;
//
//	glLoadIdentity ();
//    gluPerspective (90,  1,  2,  16384);
//	gluLookAt (mx, my, draw_maxs[2] + g/2, mx , my, draw_maxs[2], 0, 1, 0);
//#else
        GL11.glMatrixMode(GL11.GL_PROJECTION)
        GL11.glLoadIdentity()
        GL11.glOrtho(
            dmap.dmapGlobals.drawBounds.get(0, 0).toDouble(),
            dmap.dmapGlobals.drawBounds.get(1, 0).toDouble(),
            dmap.dmapGlobals.drawBounds.get(0, 1).toDouble(),
            dmap.dmapGlobals.drawBounds.get(1, 1).toDouble(),
            -1.0,
            1.0
        )
        GL11.glMatrixMode(GL11.GL_MODELVIEW)
        GL11.glLoadIdentity()
        //#endif
        GL11.glColor3f(0f, 0f, 0f)
        //	glPolygonMode (GL_FRONT_AND_BACK, GL_LINE);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        //	glEnable (GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

//#if 0
////glColor4f (1,0,0,0.5);
////	glBegin( GL_LINE_LOOP );
//	glBegin( GL_QUADS );
//
//	glVertex2f( dmapGlobals.drawBounds.b[0][0] + 20, dmapGlobals.drawBounds.b[0][1] + 20 );
//	glVertex2f( dmapGlobals.drawBounds.b[1][0] - 20, dmapGlobals.drawBounds.b[0][1] + 20 );
//	glVertex2f( dmapGlobals.drawBounds.b[1][0] - 20, dmapGlobals.drawBounds.b[1][1] - 20 );
//	glVertex2f( dmapGlobals.drawBounds.b[0][0] + 20, dmapGlobals.drawBounds.b[1][1] - 20 );
//
//	glEnd ();
//#endif
        GL11.glFlush()
    }

    fun Draw_SetRed() {
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glColor3f(1f, 0f, 0f)
    }

    fun Draw_SetGrey() {
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glColor3f(0.5f, 0.5f, 0.5f)
    }

    fun Draw_SetBlack() {
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glColor3f(0.0f, 0.0f, 0.0f)
    }

    fun DrawWinding(w: idWinding?) {
        var i: Int
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glColor3f(0.3f, 0.0f, 0.0f)
        GL11.glBegin(GL11.GL_POLYGON)
        i = 0
        while (i < w.GetNumPoints()) {
            GL11.glVertex3f(w.get(i).get(0), w.get(i).get(1), w.get(i).get(2))
            i++
        }
        GL11.glEnd()
        GL11.glColor3f(1f, 0f, 0f)
        GL11.glBegin(GL11.GL_LINE_LOOP)
        i = 0
        while (i < w.GetNumPoints()) {
            GL11.glVertex3f(w.get(i).get(0), w.get(i).get(1), w.get(i).get(2))
            i++
        }
        GL11.glEnd()
        GL11.glFlush()
    }

    fun DrawAuxWinding(w: idWinding?) {
        var i: Int
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glColor3f(0.0f, 0.3f, 0.0f)
        GL11.glBegin(GL11.GL_POLYGON)
        i = 0
        while (i < w.GetNumPoints()) {
            GL11.glVertex3f(w.get(i).get(0), w.get(i).get(1), w.get(i).get(2))
            i++
        }
        GL11.glEnd()
        GL11.glColor3f(0.0f, 1.0f, 0.0f)
        GL11.glBegin(GL11.GL_LINE_LOOP)
        i = 0
        while (i < w.GetNumPoints()) {
            GL11.glVertex3f(w.get(i).get(0), w.get(i).get(1), w.get(i).get(2))
            i++
        }
        GL11.glEnd()
        GL11.glFlush()
    }

    fun DrawLine(v1: idVec3?, v2: idVec3?, color: Int) {
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        when (color) {
            0 -> GL11.glColor3f(0f, 0f, 0f)
            1 -> GL11.glColor3f(0f, 0f, 1f)
            2 -> GL11.glColor3f(0f, 1f, 0f)
            3 -> GL11.glColor3f(0f, 1f, 1f)
            4 -> GL11.glColor3f(1f, 0f, 0f)
            5 -> GL11.glColor3f(1f, 0f, 1f)
            6 -> GL11.glColor3f(1f, 1f, 0f)
            7 -> GL11.glColor3f(1f, 1f, 1f)
        }
        GL11.glBegin(GL11.GL_LINES)
        qgl.qglVertex3fv(v1.ToFloatPtr())
        qgl.qglVertex3fv(v2.ToFloatPtr())
        GL11.glEnd()
        GL11.glFlush()
    }

    fun GLS_BeginScene() {
        throw TODO_Exception()
        //        WSADATA winsockdata;
//        WORD wVersionRequested;
//        sockaddr_in address;
//        int r;
//
//        if (!wins_init) {
//            wins_init = true;
//
//            wVersionRequested = MAKEWORD(1, 1);
//
//            r = WSAStartup(MAKEWORD(1, 1), winsockdata);
//
//            if (r != 0) {
//                common.Error("Winsock initialization failed.");
//            }
//
//        }
//
//        // connect a socket to the server
//        draw_socket = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
//        if (draw_socket == -1) {
//            common.Error("draw_socket failed");
//        }
//
//        address.sin_family = AF_INET;
//        address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
//        address.sin_port = GLSERV_PORT;
//        r = connect(draw_socket, (sockaddr) address, sizeof(address));
//        if (r == -1) {
//            closesocket(draw_socket);
//            draw_socket = 0;
//        }
    }

    fun GLS_Winding(w: idWinding?, code: Int) {
        throw TODO_Exception()
        //        byte[] buf = new byte[1024];
//        int i, j;
//
//        if (0 == draw_socket) {
//            return;
//        }
//
//        buf[0] = w.GetNumPoints();//TODO:put int into multiple bytes?
//        buf[1] = code;
//        for (i = 0; i < w.GetNumPoints(); i++) {
//            for (j = 0; j < 3; j++) //			((float *)buf)[2+i*3+j] = (*w)[i][j];
//            {
//                buf[2 + i * 3 + j] = w.oGet(i).oGet(j);//TODO:put float into multiple bytes?
//            }
//        }
//        send(draw_socket, (String) buf, w.GetNumPoints() * 12 + 8, 0);
    }

    fun GLS_Triangle(tri: mapTri_s?, code: Int) {
        throw TODO_Exception()
        //        idWinding w = new idWinding();
//
//        w.SetNumPoints(3);
//        VectorCopy(tri.v[0].xyz, w.oGet(0));
//        VectorCopy(tri.v[1].xyz, w.oGet(1));
//        VectorCopy(tri.v[2].xyz, w.oGet(2));
//        GLS_Winding(w, code);
    }

    fun GLS_EndScene() {
        throw TODO_Exception()
        //        closesocket(draw_socket);
//        draw_socket = 0;
    } //#else
    //void Draw_ClearWindow(  ) {
    //}
    //
    //void DrawWinding( final idWinding w) {
    //}
    //
    //void DrawAuxWinding ( final idWinding w) {
    //}
    //
    //void GLS_Winding( final idWinding w, int code ) {
    //}
    //
    //void GLS_BeginScene () {
    //}
    //
    //void GLS_EndScene ()
    //{
    //}
    //
    //#endif
}