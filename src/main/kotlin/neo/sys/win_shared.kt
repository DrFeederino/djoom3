package neo.sys

import com.sun.management.OperatingSystemMXBean
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.idlib.Text.Str.idStr
import neo.sys.sys_public.sysMemoryStats_s
import java.io.File
import java.lang.management.ManagementFactory

/**
 *
 */
object win_shared {
    private val sys_timeBase = System.currentTimeMillis()

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
//        if (_WIN32) {
//            try {
//                final int colon, MB;
//                String memory = cmd("cmd /c \"systeminfo | find \"Total\"\"");
//                colon = memory.indexOf(':') + 1;
//                MB = memory.indexOf("MB");
//
//                memory = memory.substring(colon, MB).replace(",", "").trim();
//
//                return atoi(memory);
//            } catch (IOException ex) {
//                Logger.getLogger(win_shared.class.getName()).log(Level.SEVERE, null, ex);
//            }
//        }
//        return -1;
        val ram = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalPhysicalMemorySize
        return (ram / 1000000).toInt()
    }

    /*
     ================
     Sys_GetDriveFreeSpace
     returns in megabytes
     ================
     */
    fun Sys_GetDriveFreeSpace(path: String?): Long {
        return File(path).freeSpace / (1024L * 1024L)
        //	DWORDLONG lpFreeBytesAvailable;
//	DWORDLONG lpTotalNumberOfBytes;
//	DWORDLONG lpTotalNumberOfFreeBytes;
//	int ret = 26;
//	//FIXME: see why this is failing on some machines
//	if ( ::GetDiskFreeSpaceEx( path, (PULARGE_INTEGER)&lpFreeBytesAvailable, (PULARGE_INTEGER)&lpTotalNumberOfBytes, (PULARGE_INTEGER)&lpTotalNumberOfFreeBytes ) ) {
//		ret = ( double )( lpFreeBytesAvailable ) / ( 1024.0 * 1024.0 );
//	}
//	return ret;
    }

    /*
     ================
     Sys_GetVideoRam
     returns in megabytes
     ================
     */
    fun Sys_GetVideoRam(): Int {
//#ifdef	ID_DEDICATED
        return 0
        //#else
//	unsigned int retSize = 64;
//
//	CComPtr<IWbemLocator> spLoc = NULL;
//	HRESULT hr = CoCreateInstance( CLSID_WbemLocator, 0, CLSCTX_SERVER, IID_IWbemLocator, ( LPVOID * ) &spLoc );
//	if ( hr != S_OK || spLoc == NULL ) {
//		return retSize;
//	}
//
//	CComBSTR bstrNamespace( _T( "\\\\.\\root\\CIMV2" ) );
//	CComPtr<IWbemServices> spServices;
//
//	// Connect to CIM
//	hr = spLoc->ConnectServer( bstrNamespace, NULL, NULL, 0, NULL, 0, 0, &spServices );
//	if ( hr != WBEM_S_NO_ERROR ) {
//		return retSize;
//	}
//
//	// Switch the security level to IMPERSONATE so that provider will grant access to system-level objects.  
//	hr = CoSetProxyBlanket( spServices, RPC_C_AUTHN_WINNT, RPC_C_AUTHZ_NONE, NULL, RPC_C_AUTHN_LEVEL_CALL, RPC_C_IMP_LEVEL_IMPERSONATE, NULL, EOAC_NONE );
//	if ( hr != S_OK ) {
//		return retSize;
//	}
//
//	// Get the vid controller
//	CComPtr<IEnumWbemClassObject> spEnumInst = NULL;
//	hr = spServices->CreateInstanceEnum( CComBSTR( "Win32_VideoController" ), WBEM_FLAG_SHALLOW, NULL, &spEnumInst ); 
//	if ( hr != WBEM_S_NO_ERROR || spEnumInst == NULL ) {
//		return retSize;
//	}
//
//	ULONG uNumOfInstances = 0;
//	CComPtr<IWbemClassObject> spInstance = NULL;
//	hr = spEnumInst->Next( 10000, 1, &spInstance, &uNumOfInstances );
//
//	if ( hr == S_OK && spInstance ) {
//		// Get properties from the object
//		CComVariant varSize;
//		hr = spInstance->Get( CComBSTR( _T( "AdapterRAM" ) ), 0, &varSize, 0, 0 );
//		if ( hr == S_OK ) {
//			retSize = varSize.intVal / ( 1024 * 1024 );
//			if ( retSize == 0 ) {
//				retSize = 64;
//			}
//		}
//	}
//	return retSize;
//#endif
    }

    /*
     ================
     Sys_GetCurrentMemoryStatus

     returns OS mem info
     all values are in kB except the memoryload
     ================
     */
    fun Sys_GetCurrentMemoryStatus(stats: sysMemoryStats_s?) {
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
    fun Sys_LockMemory(ptr: Any?, bytes: Int): Boolean {
        throw TODO_Exception()
        //	return ( VirtualLock( ptr, (SIZE_T)bytes ) != FALSE );
    }

    /*
     ================
     Sys_UnlockMemory
     ================
     */
    fun Sys_UnlockMemory(ptr: Any?, bytes: Int): Boolean {
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

    fun Sys_GetCurrentUser(): String? {
        var s_userName: String?
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
    fun Sym_GetFuncInfo(addr: Long, module: idStr?, funcName: idStr?) {
        throw TODO_Exception()
        //	module = "";
//	sprintf( funcName, "0x%08x", addr );
    }

    /*
     ==================
     GetFuncAddr
     ==================
     */
    fun  /*address_t*/GetFuncAddr(   /*address_t*/midPtPtr: Long): Long {
        throw TODO_Exception()
        //	long temp;
//	do {
//		temp = (long)(*(long*)midPtPtr);
//		if ( (temp&0x00FFFFFF) == PROLOGUE_SIGNATURE ) {
//			break;
//		}
//		midPtPtr--;
//	} while(true);
//
//	return midPtPtr;
    }

    /*
     ==================
     GetCallerAddr
     ==================
     */
    fun  /*address_t*/GetCallerAddr(_ebp: Long): Long {
        throw TODO_Exception()
        //	long midPtPtr;
//	long res = 0;
//
//	__asm {
//		mov		eax, _ebp
//		mov		ecx, [eax]		// check for end of stack frames list
//		test	ecx, ecx		// check for zero stack frame
//		jz		label
//		mov		eax, [eax+4]	// get the ret address
//		test	eax, eax		// check for zero return address
//		jz		label
//		mov		midPtPtr, eax
//	}
//	res = GetFuncAddr( midPtPtr );
//label:
//	return res;
    }

    /*
     ==================
     Sys_GetCallStack

     use /Oy option
     ==================
     */
    fun Sys_GetCallStack(   /*address_t*/callStack: Long, callStackSize: Int) {
        throw TODO_Exception()
        //#if 1 //def _DEBUG
//	int i;
//	long m_ebp;
//
//	__asm {
//		mov eax, ebp
//		mov m_ebp, eax
//	}
//	// skip last two functions
//	m_ebp = *((long*)m_ebp);
//	m_ebp = *((long*)m_ebp);
//	// list functions
//	for ( i = 0; i < callStackSize; i++ ) {
//		callStack[i] = GetCallerAddr( m_ebp );
//		if ( callStack[i] == 0 ) {
//			break;
//		}
//		m_ebp = *((long*)m_ebp);
//	}
//#else
//	int i = 0;
//#endif
//	while( i < callStackSize ) {
//		callStack[i++] = 0;
//	}
    }

    /*
     ==================
     Sys_GetCallStackStr
     ==================
     */
    fun Sys_GetCallStackStr(   /*address_t*/callStack: Long, callStackSize: Int): String? {
        throw TODO_Exception()
        //	static char string[MAX_STRING_CHARS*2];
//	int index, i;
//	idStr module, funcName;
//
//	index = 0;
//	for ( i = callStackSize-1; i >= 0; i-- ) {
//		Sym_GetFuncInfo( callStack[i], module, funcName );
//		index += sprintf( string+index, " -> %s", funcName.c_str() );
//	}
//	return string;
    }

    /*
     ==================
     Sys_GetCallStackCurStr
     ==================
     */
    fun Sys_GetCallStackCurStr(depth: Int): String? {
        throw TODO_Exception()
        //	long/*address_t*/ *callStack;
//
//	callStack = (long/*address_t*/ *) _alloca( depth * sizeof( long/*address_t*/ ) );
//	Sys_GetCallStack( callStack, depth );
//	return Sys_GetCallStackStr( callStack, depth );
    }

    /*
     ==================
     Sys_GetCallStackCurAddressStr
     ==================
     */
    fun Sys_GetCallStackCurAddressStr(depth: Int): String? {
        throw TODO_Exception()
        //	static char string[MAX_STRING_CHARS*2];
//	long/*address_t*/ *callStack;
//	int index, i;
//
//	callStack = (long/*address_t*/ *) _alloca( depth * sizeof( long/*address_t*/ ) );
//	Sys_GetCallStack( callStack, depth );
//
//	index = 0;
//	for ( i = depth-1; i >= 0; i-- ) {
//		index += sprintf( string+index, " -> 0x%08x", callStack[i] );
//	}
//	return string;
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