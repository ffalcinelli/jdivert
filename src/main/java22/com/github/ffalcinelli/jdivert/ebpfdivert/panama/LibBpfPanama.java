package com.github.ffalcinelli.jdivert.ebpfdivert.panama;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Panama Bindings for libbpf.so (Java 22+)
 */
public class LibBpfPanama {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIBBPF;

    static {
        SymbolLookup lib;
        try {
            lib = SymbolLookup.libraryLookup("libbpf.so.1", Arena.global())
                    .or(SymbolLookup.libraryLookup("libbpf.so", Arena.global()));
        } catch (Exception e) {
            lib = SymbolLookup.loaderLookup();
        }
        LIBBPF = lib.or(LINKER.defaultLookup());
    }

    public static final MethodHandle bpf_object__open_file = lookup("bpf_object__open_file", 
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    
    public static final MethodHandle bpf_object__load = lookup("bpf_object__load", 
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    
    public static final MethodHandle bpf_object__close = lookup("bpf_object__close", 
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    public static final MethodHandle bpf_object__find_program_by_name = lookup("bpf_object__find_program_by_name", 
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static final MethodHandle bpf_program__attach = lookup("bpf_program__attach", 
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static final MethodHandle bpf_link__destroy = lookup("bpf_link__destroy", 
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    public static final MethodHandle bpf_object__find_map_by_name = lookup("bpf_object__find_map_by_name", 
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static final MethodHandle bpf_map__fd = lookup("bpf_map__fd", 
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    public static final MethodHandle bpf_map_update_elem = lookup("bpf_map_update_elem", 
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

    public static final MethodHandle ring_buffer__new = lookup("ring_buffer__new", 
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static final MethodHandle ring_buffer__free = lookup("ring_buffer__free", 
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    public static final MethodHandle ring_buffer__poll = lookup("ring_buffer__poll", 
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static MethodHandle lookup(String name, FunctionDescriptor desc) {
        return LIBBPF.find(name).map(addr -> LINKER.downcallHandle(addr, desc)).orElse(null);
    }
}
