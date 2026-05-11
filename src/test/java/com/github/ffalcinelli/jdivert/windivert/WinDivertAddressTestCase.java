package com.github.ffalcinelli.jdivert.windivert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WinDivertAddressTestCase {

    @Test
    public void testCreation() {
        WinDivertAddress addr = new WinDivertAddress();
        assertNotNull(addr);
        assertEquals(0, addr.getLayer());
    }

    @Test
    public void testGetSetLayer() {
        WinDivertAddress addr = new WinDivertAddress();
        addr.setLayer(2);
        assertEquals(2, addr.getLayer());

        addr.setLayer(1);
        assertEquals(1, addr.getLayer());

        addr.setLayer(3);
        assertEquals(3, addr.getLayer());

        addr.setLayer(4);
        assertEquals(4, addr.getLayer());

        addr.setLayer(99);
        assertEquals(99, addr.getLayer());
    }

    @Test
    public void testGetSetEvent() {
        WinDivertAddress addr = new WinDivertAddress();
        addr.setEvent(5);
        assertEquals(5, addr.getEvent());
    }

    @Test
    public void testGetSetOutbound() {
        WinDivertAddress addr = new WinDivertAddress();
        addr.setOutbound(true);
        assertTrue(addr.isOutbound());
        addr.setOutbound(false);
        assertFalse(addr.isOutbound());
    }

    @Test
    public void testEqualsAndHashCode() {
        WinDivertAddress addr1 = new WinDivertAddress();
        WinDivertAddress addr2 = new WinDivertAddress();

        assertEquals(addr1, addr2);
        assertEquals(addr1.hashCode(), addr2.hashCode());

        addr1.setOutbound(true);
        assertNotEquals(addr1, addr2);

        addr2.setOutbound(true);
        assertEquals(addr1, addr2);
    }

    @Test
    public void testInnerClasses() {
        WinDivertAddress.WinDivertData.NetworkData network = new WinDivertAddress.WinDivertData.NetworkData();
        assertNotNull(network);

        WinDivertAddress.WinDivertData.FlowData flow = new WinDivertAddress.WinDivertData.FlowData();
        assertNotNull(flow);

        WinDivertAddress.WinDivertData.SocketData socket = new WinDivertAddress.WinDivertData.SocketData();
        assertNotNull(socket);

        WinDivertAddress.WinDivertData.ReflectData reflect = new WinDivertAddress.WinDivertData.ReflectData();
        assertNotNull(reflect);
    }
}
