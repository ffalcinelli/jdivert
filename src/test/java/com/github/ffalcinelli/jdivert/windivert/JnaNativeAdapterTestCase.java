package com.github.ffalcinelli.jdivert.windivert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JnaNativeAdapterTestCase {

    @Test
    public void testFormatMessage() {
        JnaNativeAdapter adapter = new JnaNativeAdapter();
        String msg = adapter.formatMessage(5); // Access Denied
        assertNotNull(msg);
        assertTrue(msg.toLowerCase().contains("access") || msg.toLowerCase().contains("negato"));
    }

    @Test
    public void testJnaWinDivertAddressMapping() {
        JnaNativeAdapter.JnaWinDivertAddress jnaAddr = new JnaNativeAdapter.JnaWinDivertAddress();
        jnaAddr.bitfield1 = 2; // Layer.FLOW
        jnaAddr.Timestamp = 123456L;
        
        // Sync Java fields to native memory
        jnaAddr.write();
        // Sync native memory back to Java fields (triggers union type logic)
        jnaAddr.read();
        
        assertEquals(123456L, jnaAddr.Timestamp);
    }
    
    @Test
    public void testGetLastError() {
        JnaNativeAdapter adapter = new JnaNativeAdapter();
        int err = adapter.getLastError();
        // Should be 0 if no error happened in this thread recently
        assertTrue(err >= 0);
    }
}
