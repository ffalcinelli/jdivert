package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import com.sun.jna.Memory;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WinDivertAsyncResultTestCase {

    @Test
    public void testConstructorAndPending() {
        WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.Internal = new com.sun.jna.platform.win32.BaseTSD.ULONG_PTR(0x103); // STATUS_PENDING
        
        WinDivertAsyncResult<Integer> result = new WinDivertAsyncResult<>(
                WinNT.INVALID_HANDLE_VALUE,
                overlapped,
                new Memory(10),
                new WinDivertAddress(),
                (len, buffer, address) -> len
        );
        
        assertFalse(result.isCompleted());
    }

    @Test
    public void testAlreadyCompleted() {
        WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.Internal = new com.sun.jna.platform.win32.BaseTSD.ULONG_PTR(0x0); // SUCCESS
        
        WinDivertAsyncResult<Integer> result = new WinDivertAsyncResult<>(
                WinNT.INVALID_HANDLE_VALUE,
                overlapped,
                new Memory(10),
                new WinDivertAddress(),
                (len, buffer, address) -> len
        );
        
        // This will attempt to call GetOverlappedResult which will throw because the handle is invalid,
        // but it catches WinDivertException and returns true anyway!
        assertTrue(result.isCompleted());
    }
}
