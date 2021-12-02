package neo.ui

import neo.framework.Common
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.ui.ListGUI.idListGUI
import neo.ui.UserInterface.idUserInterface

/**
 *
 */
class ListGUILocal {
    /*
     ===============================================================================

     feed data to a listDef
     each item has an id and a display string

     ===============================================================================
     */
    class idListGUILocal : idListGUI() {
        private val m_ids: idList<Int?>?
        private val m_name: idStr?
        private var m_pGUI: idUserInterface? = null
        private var m_stateUpdates: Boolean
        private var m_water: Int

        // idListGUI interface
        override fun Config(pGUI: idUserInterface?, name: String?) {
            m_pGUI = pGUI
            m_name.set("" + name)
        }

        override fun Add(id: Int, s: idStr?) {
            val i = m_ids.FindIndex(id)
            if (i == -1) {
                Append(s)
                m_ids.Append(id)
            } else {
                this.set(i, s)
            }
            StateChanged()
        }

        // use the element count as index for the ids
        override fun Push(s: idStr?) {
            Append(s)
            m_ids.Append(m_ids.Num())
            StateChanged()
        }

        override fun Del(id: Int): Boolean {
            val i = m_ids.FindIndex(id)
            if (i == -1) {
                return false
            }
            m_ids.RemoveIndex(i)
            RemoveIndex(i)
            StateChanged()
            return true
        }

        override fun Clear() {
            m_ids.Clear()
            super.Clear()
            if (m_pGUI != null) {
                // will clear all the GUI variables and will set m_water back to 0
                StateChanged()
            }
        }

        override fun GetSelection(
            s: Array<String?>?,
            size: Int,
            _sel: Int /*= 0*/
        ): Int { // returns the id, not the list index (or -1)
            if (s != null) {
//                s[0] = '\0';
                s[0] = ""
            }
            var sel = m_pGUI.State().GetInt(Str.va("%s_sel_%d", m_name, _sel), "-1")
            if (sel == -1 || sel >= m_ids.Num()) {
                return -1
            }
            if (s != null) {
                idStr.Companion.snPrintf(s, size, m_pGUI.State().GetString(Str.va("%s_item_%d", m_name, sel), ""))
            }
            // don't let overflow
            if (sel >= m_ids.Num()) {
                sel = 0
            }
            m_pGUI.SetStateInt(Str.va("%s_selid_0", m_name), m_ids.get(sel))
            return m_ids.get(sel)
        }

        override fun SetSelection(sel: Int) {
            m_pGUI.SetStateInt(Str.va("%s_sel_0", m_name), sel)
            StateChanged()
        }

        override fun GetNumSelections(): Int {
            return m_pGUI.State().GetInt(Str.va("%s_numsel", m_name))
        }

        override fun IsConfigured(): Boolean {
            return m_pGUI != null
        }

        override fun SetStateChanges(enable: Boolean) {
            m_stateUpdates = enable
            StateChanged()
        }

        override fun Shutdown() {
            m_pGUI = null
            m_name.Clear()
            Clear()
        }

        private fun StateChanged() {
            var i: Int
            if (!m_stateUpdates) {
                return
            }
            i = 0
            while (i < Num()) {
                m_pGUI.SetStateString(Str.va("%s_item_%d", m_name, i), get(i).toString())
                i++
            }
            i = Num()
            while (i < m_water) {
                m_pGUI.SetStateString(Str.va("%s_item_%d", m_name, i), "")
                i++
            }
            m_water = Num()
            m_pGUI.StateChanged(Common.com_frameTime)
        }

        //
        //
        init {
            m_name = idStr()
            m_water = 0
            m_ids = idList()
            m_stateUpdates = true
        }
    }
}