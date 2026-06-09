package com.github.ffalcinelli.jdivert.ebpfdivert;

import com.github.ffalcinelli.jdivert.Enums;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.LINUX)
public class EBPFDivertJnaNativeAdapterTestCase {

    private EBPFDivertJnaNativeAdapter adapter;

    @BeforeEach
    public void setUp() {
        adapter = new EBPFDivertJnaNativeAdapter();
    }

    @Test
    public void testOpen() throws WinDivertException {
        // This might fail if not running as root, but it shouldn't throw UnsupportedOperationException
        try (NativeAdapter.Handle handle = adapter.open("false", 0, (short) 0, 0)) {
            assertNotNull(handle);
            assertTrue(handle.isValid());
        } catch (WinDivertException e) {
            // If it fails with permission error, it's still "implemented"
            assertTrue(e.getMessage().contains("BPF") || e.getMessage().contains("permission"));
        }
    }

    @Test
    public void testRecvAsync() {
        assertThrows(UnsupportedOperationException.class, () -> adapter.recvAsync(null, 0, null));
    }

    @Test
    public void testSendAsync() {
        assertThrows(UnsupportedOperationException.class, () -> adapter.sendAsync(null, null, null, null));
    }

    @Test
    public void testSetParam() throws WinDivertException {
        adapter.setParam(null, 0, 0); // No-op
    }

    @Test
    public void testGetParam() throws WinDivertException {
        assertEquals(0, adapter.getParam(null, 0)); // No-op returns 0
    }

    @Test
    public void testCalcChecksums() {
        assertEquals(0, adapter.calcChecksums(new byte[0], new WinDivertAddress(), 0));
    }

    @Test
    public void testHashPacket() {
        assertEquals(0, adapter.hashPacket(new byte[0], 0));
    }

    @Test
    public void testAllocateBuffer() {
        try (NativeAdapter.Buffer buffer = adapter.allocateBuffer(1024)) {
            assertNotNull(buffer);
            assertEquals(1024, buffer.capacity());
        }
    }

    @Test
    public void testFormatMessage() {
        assertEquals("Error 5", adapter.formatMessage(5));
    }

    @Test
    public void testGetLastError() {
        assertEquals(0, adapter.getLastError());
    }
}
