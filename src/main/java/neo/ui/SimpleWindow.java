package neo.ui;

import neo.Renderer.Material.idMaterial;
import neo.framework.File_h.idFile;
import neo.idlib.Text.Str.idStr;
import neo.idlib.math.Matrix.idMat3;
import neo.idlib.math.Rotation.idRotation;
import neo.idlib.math.Vector.idVec2;
import neo.idlib.math.Vector.idVec3;
import neo.ui.DeviceContext.idDeviceContext;
import neo.ui.Rectangle.idRectangle;
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal;
import neo.ui.Winvar.*;

import static neo.Renderer.Material.SS_GUI;
import static neo.TempDump.itob;
import static neo.framework.DeclManager.declManager;
import static neo.idlib.Lib.colorBlack;
import static neo.idlib.math.Matrix.idMat3.getMat3_identity;
import static neo.idlib.math.Vector.getVec3_origin;
import static neo.ui.Window.*;

/**
 *
 */
public class SimpleWindow {

    static class drawWin_t {

        private static int DBG_counter = 0;
        final int DBG_index;
        idSimpleWindow simp;
        idWindow win;

        public drawWin_t() {
            this.DBG_index = DBG_counter++;
        }
    }

    public static class idSimpleWindow {
//	friend class idWindow;

        private static final idVec3 org = new idVec3();
        private static final idRotation rot = new idRotation();
        private static final idMat3 smat = new idMat3();
        private static final idVec3 vec = new idVec3(0, 0, 1);
        public static int DBG_idSimpleWindow = 0;
        //
        //
        private static int DBG_countersOfCreation = 0;
        //
        private static idMat3 trans = new idMat3();
        private final int DBG_count = DBG_countersOfCreation++;
        //
        public idStr name;
        protected idWinVec4 backColor = new idWinVec4();
        protected idWinBackground backGroundName = new idWinBackground();
        //
        protected idMaterial background;
        protected idWinVec4 borderColor = new idWinVec4();
        protected float borderSize;
        protected final idRectangle clientRect = new idRectangle();        // client area
        protected idDeviceContext dc;
        protected final idRectangle drawRect = new idRectangle();          // overall rect
        protected int flags;
        protected int fontNum;
        protected idWinVec4 foreColor = new idWinVec4();
        protected idUserInterfaceLocal gui;
        //
        protected idWinBool hideCursor = new idWinBool();
        //
        protected idWindow mParent;
        protected idWinVec4 matColor = new idWinVec4();
        protected float matScalex;
        protected float matScaley;
        protected idVec2 origin;
        protected idWinRectangle rect = new idWinRectangle();// overall rect
        protected idWinFloat rotate = new idWinFloat();
        protected idWinVec2 shear = new idWinVec2();
        //
        protected idWinStr text = new idWinStr();
        protected int textAlign;
        protected float textAlignx;
        protected float textAligny;
        protected final idRectangle textRect = new idRectangle();
        protected idWinFloat textScale = new idWinFloat();
        protected int textShadow;
        protected idWinBool visible = new idWinBool();

        public idSimpleWindow(idWindow win) {
            gui = win.GetGui();
            dc = win.dc;
            drawRect.oSet(win.drawRect);
            clientRect.oSet(win.clientRect);
            textRect.oSet(win.textRect);
            origin = new idVec2(win.origin);
            fontNum = win.fontNum;
            name = new idStr(win.name);
            matScalex = win.matScalex;
            matScaley = win.matScaley;
            borderSize = win.borderSize;
            textAlign = win.textAlign;
            textAlignx = win.textAlignx;
            textAligny = win.textAligny;
            background = win.background;
            flags = win.flags;
            textShadow = win.textShadow;

            text.oSet(win.text);
            visible.oSet(win.visible);
            rect.oSet(win.rect);
            backColor.oSet(win.backColor);
            matColor.oSet(win.matColor);
            foreColor.oSet(win.foreColor);
            borderColor.oSet(win.borderColor);
            textScale.oSet(win.textScale);
            rotate.oSet(win.rotate);
            shear.oSet(win.shear);
            backGroundName.oSet(win.backGroundName);
            if (backGroundName.Length() != 0) {
                background = declManager.FindMaterial(backGroundName.data);
                background.SetSort(SS_GUI);
                background.SetImageClassifications(1);    // just for resource tracking
            }
            backGroundName.SetMaterialPtr(background);

            // 
            //  added parent
            mParent = win.GetParent();
            // 

            hideCursor.oSet(win.hideCursor);

            idWindow parent = win.GetParent();
            if (parent != null) {
                if (text.NeedsUpdate()) {
                    DBG_idSimpleWindow++;
//                    if(DBG_idSimpleWindow++==26)
//                    if(DBG_idSimpleWindow++==27)
                    parent.AddUpdateVar(text);
//                    System.out.println(">>" + this);
                }
                if (visible.NeedsUpdate()) {
                    parent.AddUpdateVar(visible);
                }
                if (rect.NeedsUpdate()) {
                    parent.AddUpdateVar(rect);
                }
                if (backColor.NeedsUpdate()) {
                    parent.AddUpdateVar(backColor);
                }
                if (matColor.NeedsUpdate()) {
                    parent.AddUpdateVar(matColor);
                }
                if (foreColor.NeedsUpdate()) {
                    parent.AddUpdateVar(foreColor);
                }
                if (borderColor.NeedsUpdate()) {
                    parent.AddUpdateVar(borderColor);
                }
                if (textScale.NeedsUpdate()) {
                    parent.AddUpdateVar(textScale);
                }
                if (rotate.NeedsUpdate()) {
                    parent.AddUpdateVar(rotate);
                }
                if (shear.NeedsUpdate()) {
                    parent.AddUpdateVar(shear);
                }
                if (backGroundName.NeedsUpdate()) {
                    parent.AddUpdateVar(backGroundName);
                }
            }
        }
//	virtual			~idSimpleWindow();

        public void Redraw(float x, float y) {
            if (!visible.data) {
                return;
            }

            CalcClientRect(0, 0);
            dc.SetFont(fontNum);
            drawRect.Offset(x, y);
            clientRect.Offset(x, y);
            textRect.Offset(x, y);
            SetupTransforms(x, y);
            if ((flags & WIN_NOCLIP) != 0) {
                dc.EnableClipping(false);
            }
            DrawBackground(drawRect);
            DrawBorderAndCaption(drawRect);
            if (textShadow != 0) {
                idStr shadowText = text.data;
                idRectangle shadowRect = new idRectangle(textRect);

                shadowText.RemoveColors();
                shadowRect.x += textShadow;
                shadowRect.y += textShadow;

                dc.DrawText(shadowText, textScale.data, textAlign, colorBlack, shadowRect, !itob(flags & WIN_NOWRAP), -1);
            }
            dc.DrawText(text.data, textScale.data, textAlign, foreColor.data, textRect, !itob(flags & WIN_NOWRAP), -1);
            dc.SetTransformInfo(getVec3_origin(), getMat3_identity());
            if ((flags & WIN_NOCLIP) != 0) {
                dc.EnableClipping(true);
            }
            drawRect.Offset(-x, -y);
            clientRect.Offset(-x, -y);
            textRect.Offset(-x, -y);
        }

        public void StateChanged(boolean redraw) {
            if (redraw && background != null && background.CinematicLength() != 0) {
                background.UpdateCinematic(gui.GetTime());
            }
        }

        public idWinVar GetWinVarByName(final String _name) {
            idWinVar retVar = null;
            if (idStr.Icmp(_name, "background") == 0) {//TODO:should this be a switch?
                retVar = backGroundName;
            }
            if (idStr.Icmp(_name, "visible") == 0) {
                retVar = visible;
            }
            if (idStr.Icmp(_name, "rect") == 0) {
                retVar = rect;
            }
            if (idStr.Icmp(_name, "backColor") == 0) {
                retVar = backColor;
            }
            if (idStr.Icmp(_name, "matColor") == 0) {
                retVar = matColor;
            }
            if (idStr.Icmp(_name, "foreColor") == 0) {
                retVar = foreColor;
            }
            if (idStr.Icmp(_name, "borderColor") == 0) {
                retVar = borderColor;
            }
            if (idStr.Icmp(_name, "textScale") == 0) {
                retVar = textScale;
            }
            if (idStr.Icmp(_name, "rotate") == 0) {
                retVar = rotate;
            }
            if (idStr.Icmp(_name, "shear") == 0) {
                retVar = shear;
            }
            if (idStr.Icmp(_name, "text") == 0) {
                retVar = text;
            }
            return retVar;
        }

        public int GetWinVarOffset(idWinVar wv, drawWin_t owner) {
            int ret = -1;

//	if ( wv == &rect ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->rect;
//	}
//
//	if ( wv == &backColor ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->backColor;
//	}
//
//	if ( wv == &matColor ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->matColor;
//	}
//
//	if ( wv == &foreColor ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->foreColor;
//	}
//
//	if ( wv == &borderColor ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->borderColor;
//	}
//
//	if ( wv == &textScale ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->textScale;
//	}
//
//	if ( wv == &rotate ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->rotate;
//	}
//
//	if ( ret != -1 ) {
//		owner->simp = this;
//	}
            return ret;
        }

        public idWindow GetParent() {
            return mParent;
        }

        public void WriteToSaveGame(idFile savefile) {

            savefile.WriteInt(flags);
            savefile.Write(drawRect);
            savefile.Write(clientRect);
            savefile.Write(textRect);
            savefile.Write(origin);
            savefile.WriteInt(fontNum);
            savefile.WriteFloat(matScalex);
            savefile.WriteFloat(matScaley);
            savefile.WriteFloat(borderSize);
            savefile.WriteInt(textAlign);
            savefile.WriteFloat(textAlignx);
            savefile.WriteFloat(textAligny);
            savefile.WriteInt(textShadow);

            text.WriteToSaveGame(savefile);
            visible.WriteToSaveGame(savefile);
            rect.WriteToSaveGame(savefile);
            backColor.WriteToSaveGame(savefile);
            matColor.WriteToSaveGame(savefile);
            foreColor.WriteToSaveGame(savefile);
            borderColor.WriteToSaveGame(savefile);
            textScale.WriteToSaveGame(savefile);
            rotate.WriteToSaveGame(savefile);
            shear.WriteToSaveGame(savefile);
            backGroundName.WriteToSaveGame(savefile);

            int stringLen;

            if (background != null) {
                stringLen = background.GetName().length();
                savefile.WriteInt(stringLen);
                savefile.WriteString(background.GetName());
            } else {
                stringLen = 0;
                savefile.WriteInt(stringLen);
            }

        }

        public void ReadFromSaveGame(idFile savefile) {

            flags = savefile.ReadInt();
            savefile.Read(drawRect);
            savefile.Read(clientRect);
            savefile.Read(textRect);
            savefile.Read(origin);
            fontNum = savefile.ReadInt();
            matScalex = savefile.ReadFloat();
            matScaley = savefile.ReadFloat();
            borderSize = savefile.ReadFloat();
            textAlign = savefile.ReadInt();
            textAlignx = savefile.ReadFloat();
            textAligny = savefile.ReadFloat();
            textShadow = savefile.ReadInt();

            text.ReadFromSaveGame(savefile);
            visible.ReadFromSaveGame(savefile);
            rect.ReadFromSaveGame(savefile);
            backColor.ReadFromSaveGame(savefile);
            matColor.ReadFromSaveGame(savefile);
            foreColor.ReadFromSaveGame(savefile);
            borderColor.ReadFromSaveGame(savefile);
            textScale.ReadFromSaveGame(savefile);
            rotate.ReadFromSaveGame(savefile);
            shear.ReadFromSaveGame(savefile);
            backGroundName.ReadFromSaveGame(savefile);

            int stringLen;

            stringLen = savefile.ReadInt();
            if (stringLen > 0) {
                idStr backName = new idStr();

                backName.Fill(' ', stringLen);
                savefile.ReadString(backName);

                background = declManager.FindMaterial(backName);
                background.SetSort(SS_GUI);
            } else {
                background = null;
            }

        }

        protected void CalcClientRect(float xofs, float yofs) {

            drawRect.oSet(rect.data);

            if ((flags & WIN_INVERTRECT) != 0) {
                drawRect.x = rect.x() - rect.w();
                drawRect.y = rect.y() - rect.h();
            }

            drawRect.x += xofs;
            drawRect.y += yofs;

            clientRect.oSet(drawRect);
            if (rect.h() > 0.0 && rect.w() > 0.0) {

                if (((flags & WIN_BORDER) != 0) && borderSize != 0) {
                    clientRect.x += borderSize;
                    clientRect.y += borderSize;
                    clientRect.w -= borderSize;
                    clientRect.h -= borderSize;
                }

                textRect.oSet(clientRect);
                textRect.x += 2.0;
                textRect.w -= 2.0;
                textRect.y += 2.0;
                textRect.h -= 2.0;
                textRect.x += textAlignx;
                textRect.y += textAligny;

            }
            origin.Set(rect.x() + (rect.w() / 2), rect.y() + (rect.h() / 2));
        }

        protected void SetupTransforms(float x, float y) {

            trans.Identity();
            org.Set(origin.x + x, origin.y + y, 0);
            if (rotate != null && rotate.data != 0) {

                rot.Set(org, vec, rotate.data);
                trans = rot.ToMat3();
            }

            smat.Identity();
            if (shear.x() != 0 || shear.y() != 0) {
                smat.oSet(0, 1, shear.x());
                smat.oSet(1, 0, shear.y());
                trans.oMulSet(smat);
            }

            if (!trans.IsIdentity()) {
                dc.SetTransformInfo(org, trans);
            }
        }

        protected void DrawBackground(final idRectangle drawRect) {
            if (backColor.w() > 0) {
                dc.DrawFilledRect(drawRect.x, drawRect.y, drawRect.w, drawRect.h, backColor.data);
            }

            if (background != null) {
                if (matColor.w() > 0) {
                    float scaleX, scaleY;
                    if ((flags & WIN_NATURALMAT) != 0) {
                        scaleX = drawRect.w / background.GetImageWidth();
                        scaleY = drawRect.h / background.GetImageHeight();
                    } else {
                        scaleX = matScalex;
                        scaleY = matScaley;
                    }
                    dc.DrawMaterial(drawRect.x, drawRect.y, drawRect.w, drawRect.h, background, matColor.data, scaleX, scaleY);
                }
            }
        }

        protected void DrawBorderAndCaption(final idRectangle drawRect) {
            if ((flags & WIN_BORDER) != 0) {
                if (borderSize != 0) {
                    dc.DrawRect(drawRect.x, drawRect.y, drawRect.w, drawRect.h, borderSize, borderColor.data);
                }
            }
        }
    }

}
