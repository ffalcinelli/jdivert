package com.github.ffalcinelli.jdivert.windivert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    public void testMapNetworkAddress() {
        JnaNativeAdapter adapter = new JnaNativeAdapter();
        WinDivertAddress addr = new WinDivertAddress();
        addr.setLayer(0); // NETWORK
        addr.setOutbound(true);
        addr.setLoopback(false);
        addr.setImpostor(true);
        addr.Union.Network.IfIdx = 1;
        addr.Union.Network.SubIfIdx = 2;

        JnaNativeAdapter.JnaWinDivertAddress jnaAddr = new JnaNativeAdapter.JnaWinDivertAddress();
        adapter.mapFromPojo(addr, jnaAddr);
        
        assertEquals(0, jnaAddr.getLayer());
        assertTrue(jnaAddr.isOutbound());
        assertFalse(jnaAddr.isLoopback());
        assertTrue(jnaAddr.isImpostor());
        
        jnaAddr.write();
        jnaAddr.read();
        assertEquals(1, jnaAddr.Union.Network.IfIdx);
        assertEquals(2, jnaAddr.Union.Network.SubIfIdx);
        
        WinDivertAddress pojo = new WinDivertAddress();
        adapter.mapToPojo(jnaAddr, pojo);
        assertEquals(0, pojo.getLayer());
        assertTrue(pojo.isOutbound());
        assertEquals(1, pojo.Union.Network.IfIdx);
    }

    @Test
    public void testMapFlowAddress() {
        JnaNativeAdapter adapter = new JnaNativeAdapter();
        WinDivertAddress addr = new WinDivertAddress();
        addr.setLayer(2); // FLOW
        addr.setOutbound(false);
        addr.Union.Flow.EndpointId = 12345L;
        addr.Union.Flow.ParentEndpointId = 67890L;
        addr.Union.Flow.ProcessId = 1000;

        JnaNativeAdapter.JnaWinDivertAddress jnaAddr = new JnaNativeAdapter.JnaWinDivertAddress();
        adapter.mapFromPojo(addr, jnaAddr);
        
        assertEquals(2, jnaAddr.bitfield1 & 0xFF);
        
        jnaAddr.write();
        jnaAddr.read();
        assertEquals(12345L, jnaAddr.Union.Flow.EndpointId);
        assertEquals(67890L, jnaAddr.Union.Flow.ParentEndpointId);
        assertEquals(1000, jnaAddr.Union.Flow.ProcessId);

        WinDivertAddress pojo = new WinDivertAddress();
        adapter.mapToPojo(jnaAddr, pojo);
        assertEquals(2, pojo.getLayer());
        assertEquals(12345L, pojo.Union.Flow.EndpointId);
    }

    @Test
    public void testMapSocketAddress() {
        JnaNativeAdapter adapter = new JnaNativeAdapter();
        WinDivertAddress addr = new WinDivertAddress();
        addr.setLayer(3); // SOCKET
        addr.Union.Socket.ProcessId = 1234;
        addr.Union.Socket.LocalPort = 80;
        addr.Union.Socket.RemotePort = 443;
        addr.Union.Socket.Protocol = 6; // TCP

        JnaNativeAdapter.JnaWinDivertAddress jnaAddr = new JnaNativeAdapter.JnaWinDivertAddress();
        adapter.mapFromPojo(addr, jnaAddr);
        
        assertEquals(3, jnaAddr.bitfield1 & 0xFF);
        
        jnaAddr.write();
        jnaAddr.read();
        assertEquals(1234, jnaAddr.Union.Socket.ProcessId);
        assertEquals(80, jnaAddr.Union.Socket.LocalPort);
        assertEquals(443, jnaAddr.Union.Socket.RemotePort);
        assertEquals(6, jnaAddr.Union.Socket.Protocol);

        WinDivertAddress pojo = new WinDivertAddress();
        adapter.mapToPojo(jnaAddr, pojo);
        assertEquals(3, pojo.getLayer());
        assertEquals(1234, pojo.Union.Socket.ProcessId);
    }

    @Test
    public void testMapReflectAddress() {
        JnaNativeAdapter adapter = new JnaNativeAdapter();
        WinDivertAddress addr = new WinDivertAddress();
        addr.setLayer(4); // REFLECT
        addr.Union.Reflect.ProcessId = 5678;
        addr.Union.Reflect.Layer = 0; // NETWORK
        addr.Union.Reflect.Flags = 1;

        JnaNativeAdapter.JnaWinDivertAddress jnaAddr = new JnaNativeAdapter.JnaWinDivertAddress();
        adapter.mapFromPojo(addr, jnaAddr);
        
        assertEquals(4, jnaAddr.bitfield1 & 0xFF);
        
        jnaAddr.write();
        jnaAddr.read();
        assertEquals(5678, jnaAddr.Union.Reflect.ProcessId);
        assertEquals(0, jnaAddr.Union.Reflect.Layer);
        assertEquals(1, jnaAddr.Union.Reflect.Flags);

        WinDivertAddress pojo = new WinDivertAddress();
        adapter.mapToPojo(jnaAddr, pojo);
        assertEquals(4, pojo.getLayer());
        assertEquals(5678, pojo.Union.Reflect.ProcessId);
    }
}
