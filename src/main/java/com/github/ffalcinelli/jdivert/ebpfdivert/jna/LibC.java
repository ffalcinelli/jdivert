package com.github.ffalcinelli.jdivert.ebpfdivert.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * JNA Bindings for libc.so
 */
public interface LibC extends Library {
    LibC INSTANCE = Native.load("c", LibC.class);

    int vasprintf(PointerByReference ret, String format, Pointer ap);
    void free(Pointer ptr);
    int close(int fd);
    int socket(int domain, int type, int protocol);
    int setsockopt(int sockfd, int level, int optname, Pointer optval, int optlen);
    int bind(int sockfd, Structure addr, int addrlen);
    int send(int sockfd, ByteBuffer buf, int len, int flags);
    int sendto(int sockfd, ByteBuffer buf, int len, int flags, Structure dest_addr, int addrlen);

    class sockaddr_ll extends Structure {
        public short sll_family = 17; // AF_PACKET = 17
        public short sll_protocol;
        public int sll_ifindex;
        public short sll_hatype;
        public byte sll_pkttype;
        public byte sll_halen;
        public byte[] sll_addr = new byte[8];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("sll_family", "sll_protocol", "sll_ifindex", "sll_hatype", "sll_pkttype", "sll_halen", "sll_addr");
        }
    }

    class sockaddr_in extends Structure {
        public short sin_family = 2; // AF_INET = 2
        public short sin_port;
        public int sin_addr;
        public byte[] sin_zero = new byte[8];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("sin_family", "sin_port", "sin_addr", "sin_zero");
        }
    }

    class sockaddr_in6 extends Structure {
        public short sin6_family = 10; // AF_INET6 = 10
        public short sin6_port;
        public int sin6_flowinfo;
        public byte[] sin6_addr = new byte[16];
        public int sin6_scope_id;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("sin6_family", "sin6_port", "sin6_flowinfo", "sin6_addr", "sin6_scope_id");
        }
    }
}
