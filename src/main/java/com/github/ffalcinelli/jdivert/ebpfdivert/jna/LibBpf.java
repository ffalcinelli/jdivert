package com.github.ffalcinelli.jdivert.ebpfdivert.jna;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

import java.util.Arrays;
import java.util.List;

/**
 * JNA Bindings for libbpf.so
 */
public interface LibBpf extends Library {
    LibBpf INSTANCE = Native.load("bpf", LibBpf.class);

    // libbpf functions
    Pointer bpf_object__open_file(String path, Pointer opts);
    int bpf_object__load(Pointer obj);
    void bpf_object__close(Pointer obj);

    Pointer bpf_object__find_program_by_name(Pointer obj, String name);
    int bpf_program__fd(Pointer prog);
    Pointer bpf_program__attach(Pointer prog);
    int bpf_link__destroy(Pointer link);

    Pointer bpf_object__find_map_by_name(Pointer obj, String name);
    int bpf_map__fd(Pointer map);

    int bpf_map_update_elem(int fd, Pointer key, Pointer value, long flags);
    int bpf_map_lookup_elem(int fd, Pointer key, Pointer value);

    // TC functions
    int bpf_tc_hook_create(BpfTcHook hook);
    int bpf_tc_hook_destroy(BpfTcHook hook);
    int bpf_tc_attach(BpfTcHook hook, BpfTcOpts opts);
    int bpf_tc_detach(BpfTcHook hook, BpfTcOpts opts);
    int libbpf_num_possible_cpus();

    // Structures
    class BpfTcHook extends Structure {
        public long sz = 152; // sizeof(struct bpf_tc_hook)
        public int ifindex;
        public int attach_point;
        public int parent;
        public long[] reserved = new long[16];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("sz", "ifindex", "attach_point", "parent", "reserved");
        }
    }

    class BpfTcOpts extends Structure {
        public long sz = 160; // sizeof(struct bpf_tc_opts)
        public int prog_fd;
        public int flags;
        public int prog_id;
        public int handle;
        public int priority;
        public long[] reserved = new long[16];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("sz", "prog_fd", "flags", "prog_id", "handle", "priority", "reserved");
        }
    }

    // Ring Buffer functions
    interface ring_buffer_sample_fn extends Callback {
        int callback(Pointer ctx, Pointer data, long size);
    }

    Pointer ring_buffer__new(int map_fd, ring_buffer_sample_fn sample_cb, Pointer ctx, Pointer opts);
    void ring_buffer__free(Pointer rb);
    int ring_buffer__poll(Pointer rb, int timeout_ms);

    /**
     * Minimal representation of divert_pkt_header from ebpfdivert
     */
    class divert_pkt_header extends Structure {
        public int ifindex;
        public int direction;
        public int packet_len;
        public int l2_len;

        public divert_pkt_header() {}
        public divert_pkt_header(Pointer p) {
            super(p);
            read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("ifindex", "direction", "packet_len", "l2_len");
        }
    }
}
