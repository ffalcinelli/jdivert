package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WinDivertAsyncResultTestCase {

    @Test
    public void testConstructorAndPending() {
        try (WinDivertAsyncResult<Integer> result = new WinDivertAsyncResult<>(
                null,
                null,
                new WinDivertAddress(),
                (len, buffer, address) -> len,
                new WinDivertAsyncResult.AsyncImplementation() {
                    @Override
                    public boolean isCompleted() {
                        return false;
                    }

                    @Override
                    public int waitAndGetResult() {
                        return 0;
                    }
                }
        )) {

            assertFalse(result.isCompleted());
        }
    }

    @Test
    public void testAlreadyCompleted() throws WinDivertException {
        try (WinDivertAsyncResult<Integer> result = new WinDivertAsyncResult<>(
                null,
                null,
                new WinDivertAddress(),
                (len, buffer, address) -> 10,
                new WinDivertAsyncResult.AsyncImplementation() {
                    @Override
                    public boolean isCompleted() {
                        return true;
                    }

                    @Override
                    public int waitAndGetResult() {
                        return 10;
                    }
                }
        )) {

            assertTrue(result.isCompleted());
            assertEquals(10, result.get());
        }
    }
}
