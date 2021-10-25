package neo.ui;

import neo.Renderer.Material.idMaterial;
import neo.idlib.Text.Parser.idParser;
import neo.idlib.Text.Str.idStr;
import neo.idlib.Text.Token.idToken;
import neo.idlib.containers.HashTable.idHashTable;
import neo.idlib.containers.List.idList;
import neo.idlib.containers.idStrList;
import neo.idlib.math.Vector.idVec2;
import neo.idlib.math.Vector.idVec4;
import neo.sys.sys_public.sysEvent_s;
import neo.ui.DeviceContext.idDeviceContext;
import neo.ui.Rectangle.idRectangle;
import neo.ui.SimpleWindow.drawWin_t;
import neo.ui.SliderWindow.idSliderWindow;
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal;
import neo.ui.Winvar.idWinVar;

import static neo.Renderer.Material.MF_DEFAULTED;
import static neo.Renderer.Material.SS_GUI;
import static neo.TempDump.etoi;
import static neo.TempDump.isNotNullOrEmpty;
import static neo.framework.Common.common;
import static neo.framework.DeclManager.declManager;
import static neo.framework.KeyInput.*;
import static neo.idlib.Lib.idLib.sys;
import static neo.idlib.Text.Lexer.*;
import static neo.idlib.Text.Str.va;
import static neo.sys.sys_public.sysEventType_t.SE_CHAR;
import static neo.sys.sys_public.sysEventType_t.SE_KEY;
import static neo.ui.DeviceContext.idDeviceContext.ALIGN.*;
import static neo.ui.Window.*;
import static neo.ui.Window.idWindow.ON.ON_ENTER;

/**
 *
 */
public class ListWindow {

    public static final int TAB_TYPE_ICON = 1;
    //
    // enum {
    public static final int TAB_TYPE_TEXT = 0;
    //
    // Time in milliseconds between clicks to register as a double-click
    static final int doubleClickSpeed = 300;
    // Number of pixels above the text that the rect starts
    static final int pixelOffset = 3;
    //
    // number of pixels between columns
    static final int tabBorder = 4;
// };

    public static class idTabRect {

        int align;
        idVec2 iconSize = new idVec2();
        float iconVOffset;
        int type;
        int valign;
        int w;
        int x;
    }

    public static class idListWindow extends idWindow {

        //
        private int clickTime;
        private final idList<Integer> currentSel = new idList<>();
        private boolean horizontal;
        private final idHashTable<idMaterial> iconMaterials = new idHashTable<>();
        //
        private final idStrList listItems = new idStrList();
        private final idStr listName = new idStr();
        private boolean multipleSel;
        private idSliderWindow scroller;
        private float sizeBias;
        private final idStr tabAlignStr = new idStr();
        private final idStr tabIconSizeStr = new idStr();
        private final idStr tabIconVOffsetStr = new idStr();
        private final idList<idTabRect> tabInfo = new idList<>();
        private final idStr tabStopStr = new idStr();
        private final idStr tabTypeStr = new idStr();
        private final idStr tabVAlignStr = new idStr();
        private int top;
        private final idStr typed = new idStr();
        //
        private int typedTime;
        //
        //

        public idListWindow(idUserInterfaceLocal gui) {
            super(gui);
            this.gui = gui;
            CommonInit();
        }

        public idListWindow(idDeviceContext dc, idUserInterfaceLocal gui) {
            super(dc, gui);
            this.dc = dc;
            this.gui = gui;
            CommonInit();
        }

        @Override
        public String HandleEvent(final sysEvent_s event, boolean[] updateVisuals) {
            // need to call this to allow proper focus and capturing on embedded children
            final String ret = super.HandleEvent(event, updateVisuals);

            float vert = GetMaxCharHeight();
            int numVisibleLines = (int) (textRect.h / vert);

            int key = event.evValue;

            if (event.evType == SE_KEY) {
                if (0 == event.evValue2) {
                    // We only care about key down, not up
                    return ret;
                }

                if (key == K_MOUSE1 || key == K_MOUSE2) {
                    // If the user clicked in the scroller, then ignore it
                    if (scroller.Contains(gui.CursorX(), gui.CursorY())) {
                        return ret;
                    }
                }

                if ((key == K_ENTER || key == K_KP_ENTER)) {
                    RunScript(etoi(ON_ENTER));
                    return cmd.toString();
                }

                if (key == K_MWHEELUP) {
                    key = K_UPARROW;
                } else if (key == K_MWHEELDOWN) {
                    key = K_DOWNARROW;
                }

                if (key == K_MOUSE1) {
                    if (Contains(gui.CursorX(), gui.CursorY())) {
                        int cur = (int) ((gui.CursorY() - actualY - pixelOffset) / vert) + top;
                        if (cur >= 0 && cur < listItems.size()) {
                            if (multipleSel && idKeyInput.IsDown(K_CTRL)) {
                                if (IsSelected(cur)) {
                                    ClearSelection(cur);
                                } else {
                                    AddCurrentSel(cur);
                                }
                            } else {
                                if (IsSelected(cur) && (gui.GetTime() < clickTime + doubleClickSpeed)) {
                                    // Double-click causes ON_ENTER to get run
                                    RunScript(etoi(ON_ENTER));
                                    return cmd.toString();
                                }
                                SetCurrentSel(cur);

                                clickTime = gui.GetTime();
                            }
                        } else {
                            SetCurrentSel(listItems.size() - 1);
                        }
                    }
                } else if (key == K_UPARROW || key == K_PGUP || key == K_DOWNARROW || key == K_PGDN) {
                    int numLines = 1;

                    if (key == K_PGUP || key == K_PGDN) {
                        numLines = numVisibleLines / 2;
                    }

                    if (key == K_UPARROW || key == K_PGUP) {
                        numLines = -numLines;
                    }

                    if (idKeyInput.IsDown(K_CTRL)) {
                        top += numLines;
                    } else {
                        SetCurrentSel(GetCurrentSel() + numLines);
                    }
                } else {
                    return ret;
                }
            } else if (event.evType == SE_CHAR) {
                if (!idStr.CharIsPrintable(key)) {
                    return ret;
                }

                if (gui.GetTime() > typedTime + 1000) {
                    typed.oSet("");
                }
                typedTime = gui.GetTime();
                typed.Append((char) key);

                for (int i = 0; i < listItems.size(); i++) {
                    if (idStr.Icmpn(typed, listItems.get(i), typed.Length()) == 0) {
                        SetCurrentSel(i);
                        break;
                    }
                }

            } else {
                return ret;
            }

            if (GetCurrentSel() < 0) {
                SetCurrentSel(0);
            }

            if (GetCurrentSel() >= listItems.size()) {
                SetCurrentSel(listItems.size() - 1);
            }

            if (scroller.GetHigh() > 0.0f) {
                if (!idKeyInput.IsDown(K_CTRL)) {
                    if (top > GetCurrentSel() - 1) {
                        top = GetCurrentSel() - 1;
                    }
                    if (top < GetCurrentSel() - numVisibleLines + 2) {
                        top = GetCurrentSel() - numVisibleLines + 2;
                    }
                }

                if (top > listItems.size() - 2) {
                    top = listItems.size() - 2;
                }
                if (top < 0) {
                    top = 0;
                }
                scroller.SetValue(top);
            } else {
                top = 0;
                scroller.SetValue(0.0f);
            }

            if (key != K_MOUSE1) {
                // Send a fake mouse click event so onAction gets run in our parents
                final sysEvent_s ev = sys.GenerateMouseButtonEvent(1, true);
                super.HandleEvent(ev, updateVisuals);
            }

            if (currentSel.Num() > 0) {
                for (int i = 0; i < currentSel.Num(); i++) {
                    gui.SetStateInt(va("%s_sel_%d", listName, i), currentSel.oGet(i));
                }
            } else {
                gui.SetStateInt(va("%s_sel_0", listName), 0);
            }
            gui.SetStateInt(va("%s_numsel", listName), currentSel.Num());

            return ret;
        }

        @Override
        public void PostParse() {
            super.PostParse();

            InitScroller(horizontal);

            final idList<Integer> tabStops = new idList<>();
            final idList<Integer> tabAligns = new idList<>();
            if (tabStopStr.Length() != 0) {
                idParser src = new idParser(tabStopStr.toString(), tabStopStr.Length(), "tabstops", LEXFL_NOFATALERRORS | LEXFL_NOSTRINGCONCAT | LEXFL_NOSTRINGESCAPECHARS);
                idToken tok = new idToken();
                while (src.ReadToken(tok)) {
                    if (tok.equals(",")) {
                        continue;
                    }
                    tabStops.Append(Integer.parseInt(tok.toString()));
                }
            }
            if (tabAlignStr.Length() != 0) {
                idParser src = new idParser(tabAlignStr.toString(), tabAlignStr.Length(), "tabaligns", LEXFL_NOFATALERRORS | LEXFL_NOSTRINGCONCAT | LEXFL_NOSTRINGESCAPECHARS);
                idToken tok = new idToken();
                while (src.ReadToken(tok)) {
                    if (tok.equals(",")) {
                        continue;
                    }
                    tabAligns.Append(Integer.parseInt(tok.toString()));
                }
            }
            final idList<Integer> tabVAligns = new idList<>();
            if (tabVAlignStr.Length() != 0) {
                idParser src = new idParser(tabVAlignStr.toString(), tabVAlignStr.Length(), "tabvaligns", LEXFL_NOFATALERRORS | LEXFL_NOSTRINGCONCAT | LEXFL_NOSTRINGESCAPECHARS);
                idToken tok = new idToken();
                while (src.ReadToken(tok)) {
                    if (tok.equals(",")) {
                        continue;
                    }
                    tabVAligns.Append(Integer.parseInt(tok.toString()));
                }
            }

            final idList<Integer> tabTypes = new idList<>();
            if (tabTypeStr.Length() != 0) {
                idParser src = new idParser(tabTypeStr.toString(), tabTypeStr.Length(), "tabtypes", LEXFL_NOFATALERRORS | LEXFL_NOSTRINGCONCAT | LEXFL_NOSTRINGESCAPECHARS);
                idToken tok = new idToken();
                while (src.ReadToken(tok)) {
                    if (tok.equals(",")) {
                        continue;
                    }
                    tabTypes.Append(Integer.parseInt(tok.toString()));
                }
            }
            final idList<idVec2> tabSizes = new idList<>();
            if (tabIconSizeStr.Length() != 0) {
                idParser src = new idParser(tabIconSizeStr.toString(), tabIconSizeStr.Length(), "tabiconsizes", LEXFL_NOFATALERRORS | LEXFL_NOSTRINGCONCAT | LEXFL_NOSTRINGESCAPECHARS);
                idToken tok = new idToken();
                while (src.ReadToken(tok)) {
                    if (tok.equals(",")) {
                        continue;
                    }
                    idVec2 size = new idVec2();
                    size.x = Integer.parseInt(tok.toString());

                    src.ReadToken(tok);    //","
                    src.ReadToken(tok);

                    size.y = Integer.parseInt(tok.toString());
                    tabSizes.Append(size);
                }
            }

            final idList<Float> tabIconVOffsets = new idList<>();
            if (tabIconVOffsetStr.Length() != 0) {
                idParser src = new idParser(tabIconVOffsetStr.toString(), tabIconVOffsetStr.Length(), "tabiconvoffsets", LEXFL_NOFATALERRORS | LEXFL_NOSTRINGCONCAT | LEXFL_NOSTRINGESCAPECHARS);
                idToken tok = new idToken();
                while (src.ReadToken(tok)) {
                    if (tok.equals(",")) {
                        continue;
                    }
                    tabIconVOffsets.Append(Float.parseFloat(tok.toString()));
                }
            }

            int c = tabStops.Num();
            boolean doAligns = (tabAligns.Num() == tabStops.Num());
            for (int i = 0; i < c; i++) {
                idTabRect r = new idTabRect();
                r.x = tabStops.oGet(i);
                r.w = (i < c - 1) ? tabStops.oGet(i + 1) - r.x - tabBorder : -1;
                r.align = (doAligns) ? tabAligns.oGet(i) : 0;
                if (tabVAligns.Num() > 0) {
                    r.valign = tabVAligns.oGet(i);
                } else {
                    r.valign = 0;
                }
                if (tabTypes.Num() > 0) {
                    r.type = tabTypes.oGet(i);
                } else {
                    r.type = TAB_TYPE_TEXT;
                }
                if (tabSizes.Num() > 0) {
                    r.iconSize = tabSizes.oGet(i);
                } else {
                    r.iconSize.Zero();
                }
                if (tabIconVOffsets.Num() > 0) {
                    r.iconVOffset = tabIconVOffsets.oGet(i);
                } else {
                    r.iconVOffset = 0;
                }
                tabInfo.Append(r);
            }
            flags |= WIN_CANFOCUS;
        }

        @Override
        public void Draw(int time, float x, float y) {
            idVec4 color;
            idStr work = new idStr();
            int count = listItems.size();
            idRectangle rect = new idRectangle(textRect);
            float scale = textScale.data;
            float lineHeight = GetMaxCharHeight();

            float bottom = textRect.Bottom();
            float width = textRect.w;

            if (scroller.GetHigh() > 0.0f) {
                if (horizontal) {
                    bottom -= sizeBias;
                } else {
                    width -= sizeBias;
                    rect.w = width;
                }
            }

            if (noEvents.oCastBoolean() || !Contains(gui.CursorX(), gui.CursorY())) {
                hover = false;
            }

            for (int i = top; i < count; i++) {
                if (IsSelected(i)) {
                    rect.h = lineHeight;
                    dc.DrawFilledRect(rect.x, rect.y + pixelOffset, rect.w, rect.h, borderColor.data);
                    if ((flags & WIN_FOCUS) != 0) {
                        idVec4 color2 = borderColor.data;
                        color2.w = 1.0f;
                        dc.DrawRect(rect.x, rect.y + pixelOffset, rect.w, rect.h, 1.0f, color2);
                    }
                }
                rect.y++;
                rect.h = lineHeight - 1;
                if (hover && !noEvents.oCastBoolean() && Contains(rect, gui.CursorX(), gui.CursorY())) {
                    color = hoverColor.data;
                } else {
                    color = foreColor.data;
                }
                rect.h = lineHeight + pixelOffset;
                rect.y--;

                if (tabInfo.Num() > 0) {
                    int start = 0;
                    int tab = 0;
                    int stop = listItems.get(i).Find('\t', 0);
                    while (start < listItems.get(i).Length()) {
                        if (tab >= tabInfo.Num()) {
                            common.Warning("idListWindow::Draw: gui '%s' window '%s' tabInfo.Num() exceeded", gui.GetSourceFile(), name);
                            break;
                        }
                        listItems.get(i).Mid(start, stop - start, work);

                        rect.x = textRect.x + tabInfo.oGet(tab).x;
                        rect.w = (tabInfo.oGet(tab).w == -1) ? width - tabInfo.oGet(tab).x : tabInfo.oGet(tab).w;
                        dc.PushClipRect(rect);

                        if (tabInfo.oGet(tab).type == TAB_TYPE_TEXT) {
                            dc.DrawText(work, scale, tabInfo.oGet(tab).align, color, rect, false, -1);
                        } else if (tabInfo.oGet(tab).type == TAB_TYPE_ICON) {

                            final idMaterial[] hashMat = {null};
                            idMaterial iconMat;

                            // leaving the icon name empty doesn't draw anything
                            if (isNotNullOrEmpty(work)) {

                                if (iconMaterials.Get(work.toString(), hashMat) == false) {
                                    iconMat = declManager.FindMaterial("_default");
                                } else {
                                    iconMat = hashMat[0];
                                }

                                idRectangle iconRect = new idRectangle();
                                iconRect.w = tabInfo.oGet(tab).iconSize.x;
                                iconRect.h = tabInfo.oGet(tab).iconSize.y;

                                if (tabInfo.oGet(tab).align == etoi(ALIGN_LEFT)) {
                                    iconRect.x = rect.x;
                                } else if (tabInfo.oGet(tab).align == etoi(ALIGN_CENTER)) {
                                    iconRect.x = rect.x + rect.w / 2.0f - iconRect.w / 2.0f;
                                } else if (tabInfo.oGet(tab).align == etoi(ALIGN_RIGHT)) {
                                    iconRect.x = rect.x + rect.w - iconRect.w;
                                }

                                if (tabInfo.oGet(tab).valign == 0) { //Top
                                    iconRect.y = rect.y + tabInfo.oGet(tab).iconVOffset;
                                } else if (tabInfo.oGet(tab).valign == 1) { //Center
                                    iconRect.y = rect.y + rect.h / 2.0f - iconRect.h / 2.0f + tabInfo.oGet(tab).iconVOffset;
                                } else if (tabInfo.oGet(tab).valign == 2) { //Bottom
                                    iconRect.y = rect.y + rect.h - iconRect.h + tabInfo.oGet(tab).iconVOffset;
                                }

                                dc.DrawMaterial(iconRect.x, iconRect.y, iconRect.w, iconRect.h, iconMat, new idVec4(1.0f, 1.0f, 1.0f, 1.0f), 1.0f, 1.0f);
                            }
                        }

                        dc.PopClipRect();

                        start = stop + 1;
                        stop = listItems.get(i).Find('\t', start);
                        if (stop < 0) {
                            stop = listItems.get(i).Length();
                        }
                        tab++;
                    }
                    rect.x = textRect.x;
                    rect.w = width;
                } else {
                    dc.DrawText(listItems.get(i), scale, 0, color, rect, false, -1);
                }
                rect.y += lineHeight;
                if (rect.y > bottom) {
                    break;
                }
            }
        }

        @Override
        public void Activate(boolean activate, idStr act) {
            super.Activate(activate, act);

            if (activate) {
                UpdateList();
            }
        }

        @Override
        public void HandleBuddyUpdate(idWindow buddy) {
            top = (int) scroller.GetValue();
        }

        @Override
        public void StateChanged(boolean redraw /*= false*/) {
            UpdateList();
        }

        @Override
        public int/*size_t*/ Allocated() {
            return super.Allocated();
        }

        @Override
        public idWinVar GetWinVarByName(final String _name, boolean winLookup /*= false*/, drawWin_t[] owner /*= NULL*/) {
            return super.GetWinVarByName(_name, winLookup, owner);
        }

        public void UpdateList() {
            idStr str = new idStr(), strName;
            listItems.clear();
            for (int i = 0; i < MAX_LIST_ITEMS; i++) {
                if (gui.State().GetString(va("%s_item_%d", listName, i), "", str)) {
                    if (str.Length() != 0) {
                        listItems.add(str);
                    }
                } else {
                    break;
                }
            }
            float vert = GetMaxCharHeight();
            int fit = (int) (textRect.h / vert);
            if (listItems.size() < fit) {
                scroller.SetRange(0.0f, 0.0f, 1.0f);
            } else {
                scroller.SetRange(0.0f, (listItems.size() - fit) + 1.0f, 1.0f);
            }

            SetCurrentSel(gui.State().GetInt(va("%s_sel_0", listName)));

            float value = scroller.GetValue();
            if (value > listItems.size() - 1) {
                value = listItems.size() - 1;
            }
            if (value < 0.0f) {
                value = 0.0f;
            }
            scroller.SetValue(value);
            top = (int) value;

            typedTime = 0;
            clickTime = 0;
            typed.oSet("");
        }

        @Override
        protected boolean ParseInternalVar(final String _name, idParser src) {
            if (idStr.Icmp(_name, "horizontal") == 0) {
                horizontal = src.ParseBool();
                return true;
            }
            if (idStr.Icmp(_name, "listname") == 0) {
                ParseString(src, listName);
                return true;
            }
            if (idStr.Icmp(_name, "tabstops") == 0) {
                ParseString(src, tabStopStr);
                return true;
            }
            if (idStr.Icmp(_name, "tabaligns") == 0) {
                ParseString(src, tabAlignStr);
                return true;
            }
            if (idStr.Icmp(_name, "multipleSel") == 0) {
                multipleSel = src.ParseBool();
                return true;
            }
            if (idStr.Icmp(_name, "tabvaligns") == 0) {
                ParseString(src, tabVAlignStr);
                return true;
            }
            if (idStr.Icmp(_name, "tabTypes") == 0) {
                ParseString(src, tabTypeStr);
                return true;
            }
            if (idStr.Icmp(_name, "tabIconSizes") == 0) {
                ParseString(src, tabIconSizeStr);
                return true;
            }
            if (idStr.Icmp(_name, "tabIconVOffset") == 0) {
                ParseString(src, tabIconVOffsetStr);
                return true;
            }

            idStr strName = new idStr(_name);
            if (idStr.Icmp(strName.Left(4), "mtr_") == 0) {
                idStr matName = new idStr();
                final idMaterial mat;

                ParseString(src, matName);
                mat = declManager.FindMaterial(matName);
                mat.SetImageClassifications(1);    // just for resource tracking
                if (mat != null && !mat.TestMaterialFlag(MF_DEFAULTED)) {
                    mat.SetSort(SS_GUI);
                }
                iconMaterials.Set(_name, mat);
                return true;
            }

            return super.ParseInternalVar(_name, src);
        }

        private void CommonInit() {
            typed.oSet("");
            typedTime = 0;
            clickTime = 0;
            currentSel.Clear();
            top = 0;
            sizeBias = 0;
            horizontal = false;
            scroller = new idSliderWindow(dc, gui);
            multipleSel = false;
        }

        /*
         ================
         idListWindow::InitScroller

         This is the same as in idEditWindow
         ================
         */
        private void InitScroller(boolean horizontal) {
            String thumbImage = "guis/assets/scrollbar_thumb.tga";
            String barImage = "guis/assets/scrollbarv.tga";
            String scrollerName = "_scrollerWinV";

            if (horizontal) {
                barImage = "guis/assets/scrollbarh.tga";
                scrollerName = "_scrollerWinH";
            }

            final idMaterial mat = declManager.FindMaterial(barImage);
            mat.SetSort(SS_GUI);
            sizeBias = mat.GetImageWidth();

            idRectangle scrollRect = new idRectangle();
            if (horizontal) {
                sizeBias = mat.GetImageHeight();
                scrollRect.x = 0;
                scrollRect.y = (clientRect.h - sizeBias);
                scrollRect.w = clientRect.w;
                scrollRect.h = sizeBias;
            } else {
                scrollRect.x = (clientRect.w - sizeBias);
                scrollRect.y = 0;
                scrollRect.w = sizeBias;
                scrollRect.h = clientRect.h;
            }

            scroller.InitWithDefaults(scrollerName, scrollRect, foreColor.data, matColor.data, mat.GetName(), thumbImage, !horizontal, true);
            InsertChild(scroller, null);
            scroller.SetBuddy(this);
        }

        private void SetCurrentSel(int sel) {
            currentSel.Clear();
            currentSel.Append(sel);
        }

        private void AddCurrentSel(int sel) {
            currentSel.Append(sel);
        }

        private int GetCurrentSel() {
            return (currentSel.Num() != 0) ? currentSel.oGet(0) : 0;
        }

        private boolean IsSelected(int index) {
            return (currentSel.FindIndex(index) >= 0);
        }

        private void ClearSelection(int sel) {
            int cur = currentSel.FindIndex(sel);
            if (cur >= 0) {
                currentSel.RemoveIndex(cur);
            }
        }
    }

}
