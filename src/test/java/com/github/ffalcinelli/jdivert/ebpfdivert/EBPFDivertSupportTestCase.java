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

package com.github.ffalcinelli.jdivert.ebpfdivert;

import com.github.ffalcinelli.jdivert.WinDivertAsyncResult;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EBPFDivertSupportTestCase {

    @AfterEach
    public void tearDown() {
        System.clearProperty("jdivert.ebpf.interfaces");
        Thread.interrupted();
    }

    @Test
    public void interfacesProperty() {
        assertNull(EBPFDivertSupport.interfaces());
        System.setProperty("jdivert.ebpf.interfaces", "  ");
        assertNull(EBPFDivertSupport.interfaces());
        System.setProperty("jdivert.ebpf.interfaces", " , ");
        assertNull(EBPFDivertSupport.interfaces());
        System.setProperty("jdivert.ebpf.interfaces", "lo, ,eth0");
        assertArrayEquals(new String[]{"lo", "eth0"}, EBPFDivertSupport.interfaces());
    }

    @Test
    public void errorWithoutLibraryMessage() {
        WinDivertException e = EBPFDivertSupport.error(-22, "ebpfdivert_open", null);
        assertEquals(22, e.getCode());
        assertEquals("ebpfdivert_open: errno 22", e.getMessage());
    }

    @Test
    public void asyncPropagatesFailures() {
        WinDivertException native_ = new WinDivertException(5, "native");
        WinDivertAsyncResult.AsyncImplementation failed = EBPFDivertSupport.async(() -> {
            throw native_;
        });
        assertSame(native_, assertThrows(WinDivertException.class, failed::waitAndGetResult));
        assertTrue(failed.isCompleted());

        IOException io = new IOException("io");
        WinDivertAsyncResult.AsyncImplementation other = EBPFDivertSupport.async(() -> {
            throw io;
        });
        WinDivertException e = assertThrows(WinDivertException.class, other::waitAndGetResult);
        assertSame(io, e.getCause());
    }

    @Test
    public void asyncWaitIsInterruptible() throws InterruptedException {
        CountDownLatch never = new CountDownLatch(1);
        WinDivertAsyncResult.AsyncImplementation pending = EBPFDivertSupport.async(() -> {
            never.await();
            return 0;
        });
        Thread.currentThread().interrupt();
        WinDivertException e = assertThrows(WinDivertException.class, pending::waitAndGetResult);
        assertTrue(e.getCause() instanceof InterruptedException);
        assertTrue(Thread.interrupted(), "the interrupt flag is restored");
        never.countDown();
    }
}
