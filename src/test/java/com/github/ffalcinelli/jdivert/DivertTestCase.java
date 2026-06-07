package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

public class DivertTestCase {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testDivertOpenWindows() throws WinDivertException {
        try (Divert d = new Divert("false").open()) {
            assertTrue(d.isOpen());
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testDivertOpenLinux() {
        Divert d = new Divert("false");
        // eBPF open is not yet implemented, should throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, d::open);
    }
}
