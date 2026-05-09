package com.github.ffalcinelli.jdivert;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EnumsTestCase {

    @Test
    public void layerValues() {
        assertEquals(0, Enums.Layer.NETWORK.getValue());
        assertEquals(1, Enums.Layer.NETWORK_FORWARD.getValue());
        assertEquals(2, Enums.Layer.FLOW.getValue());
        assertEquals(3, Enums.Layer.SOCKET.getValue());
        assertEquals(4, Enums.Layer.REFLECT.getValue());
    }

    @Test
    public void flagValues() {
        assertEquals(0, Enums.Flag.DEFAULT.getValue());
        assertEquals(1, Enums.Flag.SNIFF.getValue());
        assertEquals(2, Enums.Flag.DROP.getValue());
        assertEquals(4, Enums.Flag.RECV_ONLY.getValue());
        assertEquals(8, Enums.Flag.SEND_ONLY.getValue());
        assertEquals(16, Enums.Flag.NO_INSTALL.getValue());
        assertEquals(32, Enums.Flag.FRAGMENTS.getValue());
    }

    @Test
    public void paramValues() {
        assertEquals(0, Enums.Param.QUEUE_LEN.getValue());
        assertEquals(32, Enums.Param.QUEUE_LEN.getMin());
        assertEquals(16384, Enums.Param.QUEUE_LEN.getMax());
        assertEquals(4096, Enums.Param.QUEUE_LEN.getDefault());
    }

    @Test
    public void directionFromValue() {
        assertEquals(Enums.Direction.OUTBOUND, Enums.Direction.fromValue(0));
        assertEquals(Enums.Direction.INBOUND, Enums.Direction.fromValue(1));
        assertThrows(IllegalArgumentException.class, () -> Enums.Direction.fromValue(2));
    }

    @Test
    public void protocolFromValue() {
        assertEquals(Enums.Protocol.TCP, Enums.Protocol.fromValue(6));
        assertEquals(Enums.Protocol.UDP, Enums.Protocol.fromValue(17));
        assertThrows(IllegalArgumentException.class, () -> Enums.Protocol.fromValue(999));
    }

    @Test
    public void calcChecksumsOptionValues() {
        assertEquals(1, Enums.CalcChecksumsOption.NO_IP_CHECKSUM.getValue());
        assertEquals(2, Enums.CalcChecksumsOption.NO_ICMP_CHECKSUM.getValue());
        assertEquals(4, Enums.CalcChecksumsOption.NO_ICMPV6_CHECKSUM.getValue());
        assertEquals(8, Enums.CalcChecksumsOption.NO_TCP_CHECKSUM.getValue());
        assertEquals(16, Enums.CalcChecksumsOption.NO_UDP_CHECKSUM.getValue());
    }

    @Test
    public void shutdownValues() {
        assertEquals(1, Enums.Shutdown.RECV.getValue());
        assertEquals(2, Enums.Shutdown.SEND.getValue());
        assertEquals(3, Enums.Shutdown.BOTH.getValue());
    }
}
