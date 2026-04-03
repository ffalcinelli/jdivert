package com.github.ffalcinelli.jdivert.exceptions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WinDivertExceptionTestCase {

    @Test
    public void testConstructorWithCode() {
        WinDivertException e = new WinDivertException(5);
        assertEquals(5, e.getCode());
    }

    @Test
    public void testConstructorWithCodeAndMessage() {
        WinDivertException e = new WinDivertException(5, "Error message");
        assertEquals(5, e.getCode());
        assertEquals("Error message", e.getMessage());
    }

    @Test
    public void testConstructorWithCodeMessageAndCause() {
        Throwable cause = new RuntimeException("Cause");
        WinDivertException e = new WinDivertException(5, "Error message", cause);
        assertEquals(5, e.getCode());
        assertEquals("Error message", e.getMessage());
        assertEquals(cause, e.getCause());
    }

    @Test
    public void testToString() {
        WinDivertException e = new WinDivertException(5, "Error message");
        String s = e.toString();
        assertTrue(s.contains("5"));
        assertTrue(s.contains("Error message"));
    }
}
