package neo.sys

import com.sun.management.OperatingSystemMXBean
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.idlib.Text.Str.idStr
import neo.sys.sys_public.sysMemoryStats_s
import java.io.File
import java.lang.management.ManagementFactory

object win_shared {
    public val sys_timeBase = System.currentTimeMillis()

    /*
     ================
     Sys_Milliseconds
     ================
     */
    fun Sys_Milliseconds(): Int {
        return (System.currentTimeMillis() - sys_timeBase).toInt()
    }

    /*
     ================
     Sys_GetSystemRam

     returns amount of physical memory in MB
     ================
     */
    fun Sys_GetSystemRam(): Int {
        val ram = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalPhysicalMemorySize
        return (ram / 1000000).toInt()
    }

    /*
     ================
     Sys_GetDriveFreeSpace
     returns in megabytes
     ================
     */
    fun Sys_GetDriveFreeSpace(path: String): Long {
        return File(path).freeSpace / (1024L * 1024L)
    }

    /*
     ================
     Sys_GetVideoRam
     returns in megabytes
     ================
     */
    fun Sys_GetVideoRam(): Int {
        return 0
    }

    /*
     ================
     Sys_GetCurrentMemoryStatus

     returns OS mem info
     all values are in kB except the memoryload
     ================
     */
    fun Sys_GetCurrentMemoryStatus(stats: sysMemoryStats_s) {
        throw TODO_Exception()
        //	MEMORYSTATUSEX statex;
//	unsigned __int64 work;
//
//	memset( &statex, sizeof( statex ), 0 );
//	statex.dwLength = sizeof( statex );
//	GlobalMemoryStatusEx( &statex );
//
//	memset( &stats, 0, sizeof( stats ) );
//
//	stats.memoryLoad = statex.dwMemoryLoad;
//
//	work = statex.ullTotalPhys >> 20;
//	stats.totalPhysical = *(int*)&work;
//
//	work = statex.ullAvailPhys >> 20;
//	stats.availPhysical = *(int*)&work;
//
//	work = statex.ullAvailPageFile >> 20;
//	stats.availPageFile = *(int*)&work;
//
//	work = statex.ullTotalPageFile >> 20;
//	stats.totalPageFile = *(int*)&work;
//
//	work = statex.ullTotalVirtual >> 20;
//	stats.totalVirtual = *(int*)&work;
//
//	work = statex.ullAvailVirtual >> 20;
//	stats.availVirtual = *(int*)&work;
//
//	work = statex.ullAvailExtendedVirtual >> 20;
//	stats.availExtendedVirtual = *(int*)&work;
    }

    /*
     ================
     Sys_LockMemory
     ================
     */
    fun Sys_LockMemory(ptr: Any, bytes: Int): Boolean {
        throw TODO_Exception()
        //	return ( VirtualLock( ptr, (SIZE_T)bytes ) != FALSE );
    }

    /*
     ================
     Sys_UnlockMemory
     ================
     */
    fun Sys_UnlockMemory(ptr: Any, bytes: Int): Boolean {
        throw TODO_Exception()
        //	return ( VirtualUnlock( ptr, (SIZE_T)bytes ) != FALSE );
    }

    /*
     ================
     Sys_SetPhysicalWorkMemory
     ================
     */
    fun Sys_SetPhysicalWorkMemory(minBytes: Int, maxBytes: Int) {
        throw UnsupportedOperationException()
        //	::SetProcessWorkingSetSize( GetCurrentProcess(), minBytes, maxBytes );
    }

    fun Sys_GetCurrentUser(): String {
        var s_userName: String = ""
        if (!TempDump.isNotNullOrEmpty(System.getProperty("user.name").also { s_userName = it })) {
            s_userName = "player"
        }
        return s_userName
    }

    /*
     ===============================================================================

     Call stack

     ===============================================================================
     */
    //    static final int UNDECORATE_FLAGS = UNDNAME_NO_MS_KEYWORDS
    //            | UNDNAME_NO_ACCESS_SPECIFIERS
    //            | UNDNAME_NO_FUNCTION_RETURNS
    //            | UNDNAME_NO_ALLOCATION_MODEL
    //            | UNDNAME_NO_ALLOCATION_LANGUAGE
    //            | UNDNAME_NO_MEMBER_TYPE;
    /*
     ==================
     Sym_Init
     ==================
     */
    fun Sym_Init(addr: Long) {}

    /*
     ==================
     Sym_Shutdown
     ==================
     */
    fun Sym_Shutdown() {}

    /*
     ==================
     Sym_GetFuncInfo
     ==================
     */
    fun Sym_GetFuncInfo(addr: Long, module: idStr, funcName: idStr) {
        throw TODO_Exception()
    }

    /*
     ==================
     GetFuncAddr
     ==================
     */
    fun  /*address_t*/GetFuncAddr(   /*address_t*/midPtPtr: Long): Long {
        throw TODO_Exception()
    }

    /*
     ==================
     GetCallerAddr
     ==================
     */
    fun  /*address_t*/GetCallerAddr(_ebp: Long): Long {
        throw TODO_Exception()
    }

    /*
     ==================
     Sys_GetCallStack

     use /Oy option
     ==================
     */
    fun Sys_GetCallStack(   /*address_t*/callStack: Long, callStackSize: Int) {
        throw TODO_Exception()
    }

    /*
     ==================
     Sys_GetCallStackStr
     ==================
     */
    fun Sys_GetCallStackStr(   /*address_t*/callStack: Long, callStackSize: Int): String {
        throw TODO_Exception()
    }

    /*
     ==================
     Sys_GetCallStackCurStr
     ==================
     */
    fun Sys_GetCallStackCurStr(depth: Int): String {
        throw TODO_Exception()
    }

    /*
     ==================
     Sys_GetCallStackCurAddressStr
     ==================
     */
    fun Sys_GetCallStackCurAddressStr(depth: Int): String {
        throw TODO_Exception()
    }

    /*
     ==================
     Sys_ShutdownSymbols
     ==================
     */
    fun Sys_ShutdownSymbols() {
        Sym_Shutdown()
    }
}