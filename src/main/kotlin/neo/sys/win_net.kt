package neo.sys

import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.Common
import neo.idlib.containers.CInt
import neo.sys.sys_public.netadr_t
import java.net.*
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class win_net {
    class net_interface(/*unsigned*/
        var ip: Long, /*unsigned*/
        var mask: Long
    )

    class udpMsg_s {
        var address: netadr_t? = null
        var data: ByteArray = ByteArray(MAX_UDP_MSG_SIZE)
        var next: udpMsg_s? = null
        var size = 0
        var time = 0
    }

    class idUDPLag {
        var recieveFirst: udpMsg_s?

        //						~idUDPLag( void );
        var recieveLast: udpMsg_s? = null
        var sendFirst: udpMsg_s?
        var sendLast: udpMsg_s?

        init {
            recieveFirst = recieveLast
            sendLast = recieveFirst
            sendFirst = sendLast //TODO:check this
        } //        public idBlockAlloc<udpMsg_t> udpMsgAllocator = new idBlockAlloc(64);
    }

    companion object {
        const val MAX_INTERFACES = 32

        //=============================================================================
        const val MAX_UDP_MSG_SIZE = 1400
        val net_forceDrop: idCVar =
            idCVar("net_forceDrop", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER, "percentage packet loss")
        val net_forceLatency: idCVar =
            idCVar("net_forceLatency", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER, "milliseconds latency")
        val net_ip: idCVar = idCVar("net_ip", "localhost", CVarSystem.CVAR_SYSTEM, "local IP address")
        val net_port: idCVar =
            idCVar("net_port", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER, "local IP port number")
        val net_socksEnabled: idCVar =
            idCVar(
                "net_socksEnabled",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
                ""
            )
        val net_socksPassword: idCVar =
            idCVar("net_socksPassword", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE, "")
        val net_socksPort: idCVar = idCVar(
            "net_socksPort",
            "1080",
            CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
            ""
        )
        val net_socksServer: idCVar =
            idCVar("net_socksServer", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE, "")
        val net_socksUsername: idCVar =
            idCVar("net_socksUsername", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE, "")
        val netint: Array<net_interface?> = arrayOfNulls<net_interface?>(Companion.MAX_INTERFACES)
        var num_interfaces = 0
        var usingSocks = false

        //    static WSADATA winsockdata;
        var winsockInitialized = false

        //=============================================================================
        /*
         ====================
         NET_ErrorString
         ====================
         */
        fun NET_ErrorString(): String {
            throw TODO_Exception()
            //	int		code;
//
//	code = WSAGetLastError();
//	switch( code ) {
//	case WSAEINTR: return "WSAEINTR";
//	case WSAEBADF: return "WSAEBADF";
//	case WSAEACCES: return "WSAEACCES";
//	case WSAEDISCON: return "WSAEDISCON";
//	case WSAEFAULT: return "WSAEFAULT";
//	case WSAEINVAL: return "WSAEINVAL";
//	case WSAEMFILE: return "WSAEMFILE";
//	case WSAEWOULDBLOCK: return "WSAEWOULDBLOCK";
//	case WSAEINPROGRESS: return "WSAEINPROGRESS";
//	case WSAEALREADY: return "WSAEALREADY";
//	case WSAENOTSOCK: return "WSAENOTSOCK";
//	case WSAEDESTADDRREQ: return "WSAEDESTADDRREQ";
//	case WSAEMSGSIZE: return "WSAEMSGSIZE";
//	case WSAEPROTOTYPE: return "WSAEPROTOTYPE";
//	case WSAENOPROTOOPT: return "WSAENOPROTOOPT";
//	case WSAEPROTONOSUPPORT: return "WSAEPROTONOSUPPORT";
//	case WSAESOCKTNOSUPPORT: return "WSAESOCKTNOSUPPORT";
//	case WSAEOPNOTSUPP: return "WSAEOPNOTSUPP";
//	case WSAEPFNOSUPPORT: return "WSAEPFNOSUPPORT";
//	case WSAEAFNOSUPPORT: return "WSAEAFNOSUPPORT";
//	case WSAEADDRINUSE: return "WSAEADDRINUSE";
//	case WSAEADDRNOTAVAIL: return "WSAEADDRNOTAVAIL";
//	case WSAENETDOWN: return "WSAENETDOWN";
//	case WSAENETUNREACH: return "WSAENETUNREACH";
//	case WSAENETRESET: return "WSAENETRESET";
//	case WSAECONNABORTED: return "WSWSAECONNABORTEDAEINTR";
//	case WSAECONNRESET: return "WSAECONNRESET";
//	case WSAENOBUFS: return "WSAENOBUFS";
//	case WSAEISCONN: return "WSAEISCONN";
//	case WSAENOTCONN: return "WSAENOTCONN";
//	case WSAESHUTDOWN: return "WSAESHUTDOWN";
//	case WSAETOOMANYREFS: return "WSAETOOMANYREFS";
//	case WSAETIMEDOUT: return "WSAETIMEDOUT";
//	case WSAECONNREFUSED: return "WSAECONNREFUSED";
//	case WSAELOOP: return "WSAELOOP";
//	case WSAENAMETOOLONG: return "WSAENAMETOOLONG";
//	case WSAEHOSTDOWN: return "WSAEHOSTDOWN";
//	case WSASYSNOTREADY: return "WSASYSNOTREADY";
//	case WSAVERNOTSUPPORTED: return "WSAVERNOTSUPPORTED";
//	case WSANOTINITIALISED: return "WSANOTINITIALISED";
//	case WSAHOST_NOT_FOUND: return "WSAHOST_NOT_FOUND";
//	case WSATRY_AGAIN: return "WSATRY_AGAIN";
//	case WSANO_RECOVERY: return "WSANO_RECOVERY";
//	case WSANO_DATA: return "WSANO_DATA";
//	default: return "NO ERROR";
//	}
        }

        /*
         ====================
         Net_NetadrToSockadr
         ====================
         */
        fun Net_NetadrToSockadr(a: netadr_t, s: SocketAddress) {
            throw TODO_Exception()
            //	memset( s, 0, sizeof(*s) );
//
//	if( a->type == NA_BROADCAST ) {
//		((struct sockaddr_in *)s)->sin_family = AF_INET;
//		((struct sockaddr_in *)s)->sin_addr.s_addr = INADDR_BROADCAST;
//	}
//	else if( a->type == NA_IP || a->type == NA_LOOPBACK ) {
//		((struct sockaddr_in *)s)->sin_family = AF_INET;
//		((struct sockaddr_in *)s)->sin_addr.s_addr = *(int *)&a->ip;
//	}
//
//	((struct sockaddr_in *)s)->sin_port = htons( (short)a->port );
        }

        /*
         ====================
         Net_SockadrToNetadr
         ====================
         */
        fun Net_SockadrToNetadr(s: SocketAddress, a: netadr_t) {
            throw TODO_Exception()
            //	unsigned int ip;
//	if (s->sa_family == AF_INET) {
//		ip = ((struct sockaddr_in *)s)->sin_addr.s_addr;
//		*(unsigned int *)&a->ip = ip;
//		a->port = htons( ((struct sockaddr_in *)s)->sin_port );
//		// we store in network order, that loopback test is host order..
//		ip = ntohl( ip );
//		if ( ip == INADDR_LOOPBACK ) {
//			a->type = NA_LOOPBACK;
//		} else {
//			a->type = NA_IP;
//		}
//	}
        }

        /*
         =============
         Net_ExtractPort
         =============
         */
        fun Net_ExtractPort(src: String, buf: String, bufsize: Int, port: IntArray): Boolean {
            throw TODO_Exception()
            //	char *p;
//	strncpy( buf, src, bufsize );
//	p = buf; p += Min( bufsize - 1, (int)strlen( src ) ); *p = '\0';
//	p = strchr( buf, ':' );
//	if ( !p ) {
//		return false;
//	}
//	*p = '\0';
//	*port = strtol( p+1, NULL, 10 );
//	if ( errno == ERANGE ) {
//		return false;
//	}
//	return true;
        }

        /*
         =============
         Net_StringToSockaddr
         =============
         */
        fun Net_StringToSockaddr(s: String, sadr: SocketAddress, doDNSResolve: Boolean): Boolean {
            throw TODO_Exception()
            //	struct hostent	*h;
//	char buf[256];
//	int port;
//
//	memset( sadr, 0, sizeof( *sadr ) );
//
//	((struct sockaddr_in *)sadr)->sin_family = AF_INET;
//	((struct sockaddr_in *)sadr)->sin_port = 0;
//
//	if( s[0] >= '0' && s[0] <= '9' ) {
//		unsigned long ret = inet_addr(s);
//		if ( ret != INADDR_NONE ) {
//			*(int *)&((struct sockaddr_in *)sadr)->sin_addr = ret;
//		} else {
//			// check for port
//			if ( !Net_ExtractPort( s, buf, sizeof( buf ), &port ) ) {
//				return false;
//			}
//			ret = inet_addr( buf );
//			if ( ret == INADDR_NONE ) {
//				return false;
//			}
//			*(int *)&((struct sockaddr_in *)sadr)->sin_addr = ret;
//			((struct sockaddr_in *)sadr)->sin_port = htons( port );
//		}
//	} else if ( doDNSResolve ) {
//		// try to remove the port first, otherwise the DNS gets confused into multiple timeouts
//		// failed or not failed, buf is expected to contain the appropriate host to resolve
//		if ( Net_ExtractPort( s, buf, sizeof( buf ), &port ) ) {
//			((struct sockaddr_in *)sadr)->sin_port = htons( port );
//		}
//		h = gethostbyname( buf );
//		if ( h == 0 ) {
//			return false;
//		}
//		*(int *)&((struct sockaddr_in *)sadr)->sin_addr = *(int *)h->h_addr_list[0];
//	}
//
//	return true;
        }


        /*
         ==================
         Net_WaitForUDPPacket
         ==================
         */
        fun Net_WaitForUDPPacket(netSocket: Int, timeout: Int): Boolean {
            throw TODO_Exception()
            //	int					ret;
//	fd_set				set;
//	struct timeval		tv;
//
//	if ( !netSocket ) {
//		return false;
//	}
//
//	if ( timeout <= 0 ) {
//		return true;
//	}
//
//	FD_ZERO( &set );
//	FD_SET( netSocket, &set );
//
//	tv.tv_sec = 0;
//	tv.tv_usec = timeout * 1000;
//
//	ret = select( netSocket + 1, &set, NULL, NULL, &tv );
//
//	if ( ret == -1 ) {
//		common->DPrintf( "Net_WaitForUPDPacket select(): %s\n", strerror( errno ) );
//		return false;
//	}
//
//	// timeout with no data
//	if ( ret == 0 ) {
//		return false;
//	}
//
//	return true;
        }

        /*
         ==================
         Net_GetUDPPacket
         ==================
         */
        fun Net_GetUDPPacket(netSocket: Int, net_from: netadr_t, data: CharArray, size: CInt, maxSize: Int): Boolean {
            throw TODO_Exception()
            //	int 			ret;
//	struct sockaddr	from;
//	int				fromlen;
//	int				err;
//
//	if( !netSocket ) {
//		return false;
//	}
//
//	fromlen = sizeof(from);
//	ret = recvfrom( netSocket, data, maxSize, 0, (struct sockaddr *)&from, &fromlen );
//	if ( ret == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//
//		if( err == WSAEWOULDBLOCK || err == WSAECONNRESET ) {
//			return false;
//		}
//		char	buf[1024];
//		sprintf( buf, "Net_GetUDPPacket: %s\n", NET_ErrorString() );
//		OutputDebugString( buf );
//		return false;
//	}
//
//	if ( netSocket == ip_socket ) {
//		memset( ((struct sockaddr_in *)&from)->sin_zero, 0, 8 );
//	}
//
//	if ( usingSocks && netSocket == ip_socket && memcmp( &from, &socksRelayAddr, fromlen ) == 0 ) {
//		if ( ret < 10 || data[0] != 0 || data[1] != 0 || data[2] != 0 || data[3] != 1 ) {
//			return false;
//		}
//		net_from.type = NA_IP;
//		net_from.ip[0] = data[4];
//		net_from.ip[1] = data[5];
//		net_from.ip[2] = data[6];
//		net_from.ip[3] = data[7];
//		net_from.port = *(short *)&data[8];
//		memmove( data, &data[10], ret - 10 );
//	} else {
//		Net_SockadrToNetadr( &from, &net_from );
//	}
//
//	if( ret == maxSize ) {
//		char	buf[1024];
//		sprintf( buf, "Net_GetUDPPacket: oversize packet from %s\n", Sys_NetAdrToString( net_from ) );
//		OutputDebugString( buf );
//		return false;
//	}
//
//	size = ret;
//
//	return true;
        }

        /*
         ==================
         Net_SendUDPPacket
         ==================
         */
        fun Net_SendUDPPacket(netSocket: Int, length: Int, data: Any, to: netadr_t) {
            throw TODO_Exception()
            //	int				ret;
//	struct sockaddr	addr;
//
//	if( !netSocket ) {
//		return;
//	}
//
//	Net_NetadrToSockadr( &to, &addr );
//
//	if( usingSocks && to.type == NA_IP ) {
//		socksBuf[0] = 0;	// reserved
//		socksBuf[1] = 0;
//		socksBuf[2] = 0;	// fragment (not fragmented)
//		socksBuf[3] = 1;	// address type: IPV4
//		*(int *)&socksBuf[4] = ((struct sockaddr_in *)&addr)->sin_addr.s_addr;
//		*(short *)&socksBuf[8] = ((struct sockaddr_in *)&addr)->sin_port;
//		memcpy( &socksBuf[10], data, length );
//		ret = sendto( netSocket, socksBuf, length+10, 0, &socksRelayAddr, sizeof(socksRelayAddr) );
//	} else {
//		ret = sendto( netSocket, (const char *)data, length, 0, &addr, sizeof(addr) );
//	}
//	if( ret == SOCKET_ERROR ) {
//		int err = WSAGetLastError();
//
//		// wouldblock is silent
//		if( err == WSAEWOULDBLOCK ) {
//			return;
//		}
//
//		// some PPP links do not allow broadcasts and return an error
//		if( ( err == WSAEADDRNOTAVAIL ) && ( to.type == NA_BROADCAST ) ) {
//			return;
//		}
//
//		char	buf[1024];
//		sprintf( buf, "Net_SendUDPPacket: %s\n", NET_ErrorString() );
//		OutputDebugString( buf );
//	}
        }


        /*
         ====================
         Sys_ShutdownNetworking
         ====================
         */
        fun Sys_ShutdownNetworking() {
            throw TODO_Exception()
            //	if ( !winsockInitialized ) {
//		return;
//	}
//	WSACleanup();
//	winsockInitialized = false;
        }

        /*
             ==================
             Sys_IsLANAddress
             ==================
             */
        fun Sys_IsLANAddress(adr: netadr_t?): Boolean {
            throw TODO_Exception()
            //#if ID_NOLANADDRESS
//	common->Printf( "Sys_IsLANAddress: ID_NOLANADDRESS\n" );
//	return false;
//#endif
//	if( adr.type == NA_LOOPBACK ) {
//		return true;
//	}
//
//	if( adr.type != NA_IP ) {
//		return false;
//	}
//
//	if( num_interfaces ) {
//		int i;
//		unsigned long *p_ip;
//		unsigned long ip;
//		p_ip = (unsigned long *)&adr.ip[0];
//		ip = ntohl( *p_ip );
//
//		for( i=0; i < num_interfaces; i++ ) {
//			if( ( netint[i].ip & netint[i].mask ) == ( ip & netint[i].mask ) ) {
//				return true;
//			}
//		}
//	}
//	return false;
        }

        /*
     ====================
     Sys_InitNetworking
     ====================
     */
        fun Sys_InitNetworking() {
            var r: Int
            //
//        r = WSAStartup(MAKEWORD(1, 1),  & winsockdata);
//        if (r) {
//            common.Printf("WARNING: Winsock initialization failed, returned %d\n", r);
//            return;
//        }
//
            winsockInitialized = true
            Common.common.Printf("Winsock Initialized\n")
            val   /*PIP_ADAPTER_INFO*/pAdapterInfo: Enumeration<NetworkInterface>
            var   /*PIP_ADAPTER_INFO*/pAdapter: NetworkInterface
            //        DWORD dwRetVal = 0;
            var   /*PIP_ADDR_STRING*/pIPAddrStrings: Enumeration<InetAddress>
            var pIPAddr: InetAddress
            //        ULONG ulOutBufLen;
//        boolean foundLoopback;
            num_interfaces = 0
            //        foundLoopback = false;
//
//	pAdapterInfo = (IP_ADAPTER_INFO *)malloc( sizeof( IP_ADAPTER_INFO ) );
//	if( !pAdapterInfo ) {
//		common.FatalError( "Sys_InitNetworking: Couldn't malloc( %d )", sizeof( IP_ADAPTER_INFO ) );
//	}
//	ulOutBufLen = sizeof( IP_ADAPTER_INFO );
//
//	// Make an initial call to GetAdaptersInfo to get
//	// the necessary size into the ulOutBufLen variable
//	if( GetAdaptersInfo( pAdapterInfo, &ulOutBufLen ) == ERROR_BUFFER_OVERFLOW ) {
//		free( pAdapterInfo );
//		pAdapterInfo = (IP_ADAPTER_INFO *)malloc( ulOutBufLen );
//		if( !pAdapterInfo ) {
//			common.FatalError( "Sys_InitNetworking: Couldn't malloc( %ld )", ulOutBufLen );
//		}
//	}
//
            try {
                pAdapterInfo =
                    NetworkInterface.getNetworkInterfaces() //if( ( dwRetVal = GetAdaptersInfo( pAdapterInfo, &ulOutBufLen) ) != NO_ERROR ) {
                while (pAdapterInfo.hasMoreElements()) {
                    pAdapter = pAdapterInfo.nextElement()!!
                    Common.common.Printf("Found interface: %s %s - ", pAdapter.name, pAdapter.displayName)
                    pIPAddrStrings = pAdapter.inetAddresses
                    while (pIPAddrStrings.hasMoreElements()) {
                        pIPAddr = pIPAddrStrings.nextElement()
                        /*unsigned*/
                        var ip_a: Long
                        var ip_m: Long = 0
                        if (pIPAddr is Inet6Address) {
                            continue  //TODO:skip ipv6, for now.
                        }
                        //                        if (!idStr.Icmp("127.0.0.1", pIPAddrString.IpAddress.String)) {
//                            foundLoopback = true;
//                        }
//                    foundLoopback |= pIPAddr.isLoopbackAddress();
                        ip_a = TempDump.ntohl(pIPAddr.address)
                        if (pAdapter.interfaceAddresses != null && pAdapter.interfaceAddresses.size > 0) {
                            ip_m = pAdapter.interfaceAddresses[0].networkPrefixLength.toLong()
                        }

                        //skip null netmasks
                        if (TempDump.NOT(ip_m.toDouble())) {
                            Common.common.Printf("%s NULL netmask - skipped", pIPAddr.hostAddress)
                            //                        pIPAddr = pIPAddr.Next;
                            continue
                        }
                        Common.common.Printf("%s/%s", pIPAddr.hostAddress, ip_m)
                        netint[num_interfaces] = net_interface(ip_a, ip_m)
                        num_interfaces++
                        if (num_interfaces >= Companion.MAX_INTERFACES) {
                            Common.common.Printf(
                                "\nSys_InitNetworking: MAX_INTERFACES(%d) hit.\n",
                                Companion.MAX_INTERFACES
                            )
                            //                            free( pAdapterInfo );
                            return
                        }
                    }
                    Common.common.Printf("\n")
                }
            } catch (ex: SocketException) {
                Logger.getLogger(win_net::class.java.name).log(Level.SEVERE, null, ex)
                // happens if you have no network connection
                Common.common.Printf("Sys_InitNetworking: GetAdaptersInfo failed (%ld).\n", -1 /*dwRetVal*/)
            }

//        //TODO: check if java is as retarded as win32.
//        // for some retarded reason, win32 doesn't count loopback as an adapter...
//        if (!foundLoopback && num_interfaces < MAX_INTERFACES) {
//            common.Printf("Sys_InitNetworking: adding loopback interface\n");
//            netint[num_interfaces].ip = ntohl(inet_addr("127.0.0.1"));
//            netint[num_interfaces].mask = ntohl(inet_addr("255.0.0.0"));
//            num_interfaces++;
//        }
//            free( pAdapterInfo );
        }

        /*
    =============
    Sys_StringToNetAdr
    =============
    */
        fun Sys_StringToNetAdr(s: String?, a: netadr_t?, doDNSResolve: Boolean): Boolean {
            throw TODO_Exception()
            //	struct sockaddr sadr;
//
//	if ( !Net_StringToSockaddr( s, &sadr, doDNSResolve ) ) {
//		return false;
//	}
//
//	Net_SockadrToNetadr( &sadr, a );
//	return true;
        }

        /*
     =============
     Sys_NetAdrToString
     =============
     */
        fun Sys_NetAdrToString(a: netadr_t): String {
            throw TODO_Exception()
            //	static int index = 0;
//	static char buf[ 4 ][ 64 ];	// flip/flop
//	char *s;
//
//	s = buf[index];
//	index = (index + 1) & 3;
//
//	if ( a.type == NA_LOOPBACK ) {
//		if ( a.port ) {
//			idStr::snPrintf( s, 64, "localhost:%i", a.port );
//		} else {
//			idStr::snPrintf( s, 64, "localhost" );
//		}
//	} else if ( a.type == NA_IP ) {
//		idStr::snPrintf( s, 64, "%i.%i.%i.%i:%i", a.ip[0], a.ip[1], a.ip[2], a.ip[3], a.port );
//	}
//	return s;
        }

        /*
     ===================
     Sys_CompareNetAdrBase

     Compares without the port
     ===================
     */
        fun Sys_CompareNetAdrBase(a: netadr_t?, b: netadr_t?): Boolean {
            throw TODO_Exception()
            //	if ( a.type != b.type ) {
//		return false;
//	}
//
//	if ( a.type == NA_LOOPBACK ) {
//		return true;
//	}
//
//	if ( a.type == NA_IP ) {
//		if ( a.ip[0] == b.ip[0] && a.ip[1] == b.ip[1] && a.ip[2] == b.ip[2] && a.ip[3] == b.ip[3] ) {
//			return true;
//		}
//		return false;
//	}
//
//	common->Printf( "Sys_CompareNetAdrBase: bad address type\n" );
//	return false;
        }

        /*
     ====================
     NET_IPSocket
     ====================
     */
        fun NET_IPSocket(net_interface: String, port: Int, bound_to: netadr_t): Int {
            throw TODO_Exception()
            //	SOCKET				newsocket;
//	struct sockaddr_in	address;
//	unsigned long		_true = 1;
//	int					i = 1;
//	int					err;
//
//	if( net_interface ) {
//		common->DPrintf( "Opening IP socket: %s:%i\n", net_interface, port );
//	} else {
//		common->DPrintf( "Opening IP socket: localhost:%i\n", port );
//	}
//
//	if( ( newsocket = socket( AF_INET, SOCK_DGRAM, IPPROTO_UDP ) ) == INVALID_SOCKET ) {
//		err = WSAGetLastError();
//		if( err != WSAEAFNOSUPPORT ) {
//			common->Printf( "WARNING: UDP_OpenSocket: socket: %s\n", NET_ErrorString() );
//		}
//		return 0;
//	}
//
//	// make it non-blocking
//	if( ioctlsocket( newsocket, FIONBIO, &_true ) == SOCKET_ERROR ) {
//		common->Printf( "WARNING: UDP_OpenSocket: ioctl FIONBIO: %s\n", NET_ErrorString() );
//		return 0;
//	}
//
//	// make it broadcast capable
//	if( setsockopt( newsocket, SOL_SOCKET, SO_BROADCAST, (char *)&i, sizeof(i) ) == SOCKET_ERROR ) {
//		common->Printf( "WARNING: UDP_OpenSocket: setsockopt SO_BROADCAST: %s\n", NET_ErrorString() );
//		return 0;
//	}
//
//	if( !net_interface || !net_interface[0] || !idStr::Icmp( net_interface, "localhost" ) ) {
//		address.sin_addr.s_addr = INADDR_ANY;
//	}
//	else {
//		Net_StringToSockaddr( net_interface, (struct sockaddr *)&address, true );
//	}
//
//	if( port == PORT_ANY ) {
//		address.sin_port = 0;
//	}
//	else {
//		address.sin_port = htons( (short)port );
//	}
//
//	address.sin_family = AF_INET;
//
//	if( bind( newsocket, (const struct sockaddr *)&address, sizeof(address) ) == SOCKET_ERROR ) {
//		common->Printf( "WARNING: UDP_OpenSocket: bind: %s\n", NET_ErrorString() );
//		closesocket( newsocket );
//		return 0;
//	}
//
//	// if the port was PORT_ANY, we need to query again to know the real port we got bound to
//	// ( this used to be in idPort::InitForPort )
//	if ( bound_to ) {
//		int len = sizeof( address );
//		getsockname( newsocket, (sockaddr *)&address, &len );
//		Net_SockadrToNetadr( (sockaddr *)&address, bound_to );
//	}
//
//	return newsocket;
        }

        /*
         ====================
         NET_OpenSocks
         ====================
         */
        fun NET_OpenSocks(port: Int) {
            throw TODO_Exception()
            //	struct sockaddr_in	address;
//	int					err;
//	struct hostent		*h;
//	int					len;
//	bool			rfc1929;
//	unsigned char		buf[64];
//
//	usingSocks = false;
//
//	common->Printf( "Opening connection to SOCKS server.\n" );
//
//	if ( ( socks_socket = socket( AF_INET, SOCK_STREAM, IPPROTO_TCP ) ) == INVALID_SOCKET ) {
//		err = WSAGetLastError();
//		common->Printf( "WARNING: NET_OpenSocks: socket: %s\n", NET_ErrorString() );
//		return;
//	}
//
//	h = gethostbyname( net_socksServer.GetString() );
//	if ( h == NULL ) {
//		err = WSAGetLastError();
//		common->Printf( "WARNING: NET_OpenSocks: gethostbyname: %s\n", NET_ErrorString() );
//		return;
//	}
//	if ( h->h_addrtype != AF_INET ) {
//		common->Printf( "WARNING: NET_OpenSocks: gethostbyname: address type was not AF_INET\n" );
//		return;
//	}
//	address.sin_family = AF_INET;
//	address.sin_addr.s_addr = *(int *)h->h_addr_list[0];
//	address.sin_port = htons( (short)net_socksPort.GetInteger() );
//
//	if ( connect( socks_socket, (struct sockaddr *)&address, sizeof( address ) ) == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//		common->Printf( "NET_OpenSocks: connect: %s\n", NET_ErrorString() );
//		return;
//	}
//
//	// send socks authentication handshake
//	if ( *net_socksUsername.GetString() || *net_socksPassword.GetString() ) {
//		rfc1929 = true;
//	}
//	else {
//		rfc1929 = false;
//	}
//
//	buf[0] = 5;		// SOCKS version
//	// method count
//	if ( rfc1929 ) {
//		buf[1] = 2;
//		len = 4;
//	}
//	else {
//		buf[1] = 1;
//		len = 3;
//	}
//	buf[2] = 0;		// method #1 - method id #00: no authentication
//	if ( rfc1929 ) {
//		buf[2] = 2;		// method #2 - method id #02: username/password
//	}
//	if ( send( socks_socket, (const char *)buf, len, 0 ) == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//		common->Printf( "NET_OpenSocks: send: %s\n", NET_ErrorString() );
//		return;
//	}
//
//	// get the response
//	len = recv( socks_socket, (char *)buf, 64, 0 );
//	if ( len == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//		common->Printf( "NET_OpenSocks: recv: %s\n", NET_ErrorString() );
//		return;
//	}
//	if ( len != 2 || buf[0] != 5 ) {
//		common->Printf( "NET_OpenSocks: bad response\n" );
//		return;
//	}
//	switch( buf[1] ) {
//	case 0:	// no authentication
//		break;
//	case 2: // username/password authentication
//		break;
//	default:
//		common->Printf( "NET_OpenSocks: request denied\n" );
//		return;
//	}
//
//	// do username/password authentication if needed
//	if ( buf[1] == 2 ) {
//		int		ulen;
//		int		plen;
//
//		// build the request
//		ulen = strlen( net_socksUsername.GetString() );
//		plen = strlen( net_socksPassword.GetString() );
//
//		buf[0] = 1;		// username/password authentication version
//		buf[1] = ulen;
//		if ( ulen ) {
//			memcpy( &buf[2], net_socksUsername.GetString(), ulen );
//		}
//		buf[2 + ulen] = plen;
//		if ( plen ) {
//			memcpy( &buf[3 + ulen], net_socksPassword.GetString(), plen );
//		}
//
//		// send it
//		if ( send( socks_socket, (const char *)buf, 3 + ulen + plen, 0 ) == SOCKET_ERROR ) {
//			err = WSAGetLastError();
//			common->Printf( "NET_OpenSocks: send: %s\n", NET_ErrorString() );
//			return;
//		}
//
//		// get the response
//		len = recv( socks_socket, (char *)buf, 64, 0 );
//		if ( len == SOCKET_ERROR ) {
//			err = WSAGetLastError();
//			common->Printf( "NET_OpenSocks: recv: %s\n", NET_ErrorString() );
//			return;
//		}
//		if ( len != 2 || buf[0] != 1 ) {
//			common->Printf( "NET_OpenSocks: bad response\n" );
//			return;
//		}
//		if ( buf[1] != 0 ) {
//			common->Printf( "NET_OpenSocks: authentication failed\n" );
//			return;
//		}
//	}
//
//	// send the UDP associate request
//	buf[0] = 5;		// SOCKS version
//	buf[1] = 3;		// command: UDP associate
//	buf[2] = 0;		// reserved
//	buf[3] = 1;		// address type: IPV4
//	*(int *)&buf[4] = INADDR_ANY;
//	*(short *)&buf[8] = htons( (short)port );		// port
//	if ( send( socks_socket, (const char *)buf, 10, 0 ) == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//		common->Printf( "NET_OpenSocks: send: %s\n", NET_ErrorString() );
//		return;
//	}
//
//	// get the response
//	len = recv( socks_socket, (char *)buf, 64, 0 );
//	if( len == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//		common->Printf( "NET_OpenSocks: recv: %s\n", NET_ErrorString() );
//		return;
//	}
//	if( len < 2 || buf[0] != 5 ) {
//		common->Printf( "NET_OpenSocks: bad response\n" );
//		return;
//	}
//	// check completion code
//	if( buf[1] != 0 ) {
//		common->Printf( "NET_OpenSocks: request denied: %i\n", buf[1] );
//		return;
//	}
//	if( buf[3] != 1 ) {
//		common->Printf( "NET_OpenSocks: relay address is not IPV4: %i\n", buf[3] );
//		return;
//	}
//	((struct sockaddr_in *)&socksRelayAddr)->sin_family = AF_INET;
//	((struct sockaddr_in *)&socksRelayAddr)->sin_addr.s_addr = *(int *)&buf[4];
//	((struct sockaddr_in *)&socksRelayAddr)->sin_port = *(short *)&buf[8];
//	memset( ((struct sockaddr_in *)&socksRelayAddr)->sin_zero, 0, 8 );
//
//	usingSocks = true;
        }
    }
}