/*
 * Copyright (c) Fabio Falcinelli 2026.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.ffalcinelli.jdivert.ebpfdivert.jna;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * JNA mapping of libebpfdivert, the Linux counterpart of WinDivert.
 * <p>
 * Functions return 0 (or a count) on success and a negative errno on failure. Addresses are the
 * 80-byte {@code WINDIVERT_ADDRESS} layout, see {@link com.github.ffalcinelli.jdivert.windivert.AddressCodec}.
 */
public interface LibEbpfDivert extends Library {

    String ebpfdivert_version();

    int ebpfdivert_open_ex(String filter, int layer, short priority, long flags, OpenOpts opts, PointerByReference out);

    int ebpfdivert_recv(Pointer handle, Pointer packet, int packetLen, IntByReference recvLen, Pointer addr, int timeoutMs);

    int ebpfdivert_send(Pointer handle, Pointer packet, int packetLen, IntByReference sendLen, Pointer addr);

    int ebpfdivert_shutdown(Pointer handle, int how);

    int ebpfdivert_close(Pointer handle);

    int ebpfdivert_set_param(Pointer handle, int param, long value);

    int ebpfdivert_get_param(Pointer handle, int param, LongByReference value);

    int ebpfdivert_get_handle_stats(Pointer handle, long[] stats, int statsLen);

    int ebpfdivert_unregister();

    String ebpfdivert_strerror(int err);

    int ebpfdivert_helper_compile_filter(String filter, int layer, PointerByReference errStr, IntByReference errPos);

    int ebpfdivert_helper_eval_filter(String filter, Pointer packet, int packetLen, Pointer addr);

    int ebpfdivert_helper_calc_checksums(Pointer packet, int packetLen, Pointer addr, long flags);

    long ebpfdivert_helper_hash_packet(Pointer packet, int packetLen, long seed);

    /** {@code struct ebpfdivert_open_opts} (64-bit only). */
    @Structure.FieldOrder({"sz", "ifnames", "ring_bytes"})
    class OpenOpts extends Structure {
        public long sz;
        public Pointer ifnames;
        public int ring_bytes;

        public OpenOpts() {
            sz = size();
        }
    }
}
