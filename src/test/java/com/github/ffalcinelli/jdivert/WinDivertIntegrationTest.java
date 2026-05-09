package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Robust integration tests for WinDivert.
 * Uses dynamic random ports to avoid conflicts with system services like WinRM.
 */
@EnabledOnOs(OS.WINDOWS)
public class WinDivertIntegrationTest {

    private WinDivert wd;

    @AfterEach
    public void tearDown() {
        if (wd != null && wd.isOpen()) {
            wd.close();
        }
    }

    @Test
    public void testTcpPayloadModification() throws Exception {
        // 1. Find a random free port
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        // Use same length to avoid TCP sequence number issues in this simple test
        final String originalMessage = "Original-Request"; // 16 bytes
        final String secretInjectedMessage = "JDivert-Modified"; // 16 bytes

        // 2. Start a simple TCP Echo Server on that port
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
                try (Socket clientSocket = serverSocket.accept();
                     InputStream is = clientSocket.getInputStream();
                     OutputStream os = clientSocket.getOutputStream()) {
                    
                    byte[] buffer = new byte[1024];
                    int read = is.read(buffer);
                    if (read > 0) {
                        // Echo back whatever was received
                        os.write(buffer, 0, read);
                        os.flush();
                    }
                }
            } catch (IOException ignore) {}
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // 3. Open WinDivert to intercept traffic to this port
        wd = new WinDivert("loopback and tcp.DstPort == " + port).open();

        // 4. Client execution
        AtomicReference<String> clientReceived = new AtomicReference<>();
        Thread clientThread = new Thread(() -> {
            try {
                // Wait for wd.recv to be ready
                Thread.sleep(1000); 
                try (Socket socket = new Socket("127.0.0.1", port);
                     OutputStream os = socket.getOutputStream();
                     InputStream is = socket.getInputStream()) {
                    
                    os.write(originalMessage.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    
                    byte[] buf = new byte[1024];
                    int read = is.read(buf);
                    if (read > 0) {
                        clientReceived.set(new String(buf, 0, read, StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        clientThread.start();

        // 5. Interceptor Logic
        boolean modified = false;
        long deadline = System.currentTimeMillis() + 10000;
        
        while (!modified && System.currentTimeMillis() < deadline) {
            Packet p = wd.recv();
            if (p.getPayload() != null && p.getPayload().length > 0) {
                String data = new String(p.getPayload(), StandardCharsets.UTF_8);
                if (data.contains(originalMessage)) {
                    p.setPayload(secretInjectedMessage.getBytes(StandardCharsets.UTF_8));
                    p.recalculateChecksum();
                    modified = true;
                }
            }
            wd.send(p); 
        }

        clientThread.join(10000);
        assertTrue(modified, "Should have intercepted and modified a packet");
        assertEquals(secretInjectedMessage, clientReceived.get(), "Server should have received and echoed the MODIFIED message");
    }

    @Test
    public void testUdpRedirection() throws Exception {
        int portA, portB;
        try (DatagramSocket s1 = new DatagramSocket(0); DatagramSocket s2 = new DatagramSocket(0)) {
            portA = s1.getLocalPort();
            portB = s2.getLocalPort();
        }

        // Goal: Send to Port A, but WinDivert redirects to Port B
        wd = new WinDivert("loopback and udp.DstPort == " + portA).open();

        Thread receiverB = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(portB, InetAddress.getByName("127.0.0.1"))) {
                byte[] buf = new byte[1024];
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.setSoTimeout(3000);
                socket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                assertEquals("RedirectMe", msg);
            } catch (Exception e) {
                fail("Receiver B failed: " + e.getMessage());
            }
        });
        receiverB.start();

        Thread sender = new Thread(() -> {
            try {
                Thread.sleep(500);
                try (DatagramSocket socket = new DatagramSocket()) {
                    byte[] buf = "RedirectMe".getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(buf, buf.length, InetAddress.getByName("127.0.0.1"), portA));
                }
            } catch (Exception ignore) {}
        });
        sender.start();

        Packet p = wd.recv();
        assertEquals(portA, p.getDstPort());
        
        // Redirect to Port B
        p.setDstPort(portB);
        p.recalculateChecksum();
        wd.send(p);

        receiverB.join(5000);
        assertFalse(receiverB.isAlive(), "Receiver B should have finished receiving the redirected packet");
    }

    @Test
    @Disabled("Consistently fails/times out in some virtualized environments (like VirtualBox/Vagrant) where re-injected loopback packets are not reliably captured by multiple handles.")
    public void testInboundOutboundLogic() throws Exception {
        // Use two handles to test direction bit handling. 
        int port;
        try (DatagramSocket socket = new DatagramSocket(0)) {
            port = socket.getLocalPort();
        }

        try (WinDivert wd1 = new WinDivert("udp.DstPort == " + port, Enums.Layer.NETWORK, 100, Enums.Flag.DEFAULT).open();
             WinDivert wd2 = new WinDivert("udp.DstPort == " + port, Enums.Layer.NETWORK, 0, Enums.Flag.DEFAULT).open()) {
            
            // Start a receiver to avoid stack drops
            Thread receiver = new Thread(() -> {
                try (DatagramSocket socket = new DatagramSocket(port)) {
                    byte[] buf = new byte[1024];
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.setSoTimeout(5000);
                    socket.receive(p);
                } catch (Exception ignore) {}
            });
            receiver.start();

            // 1. Trigger an outbound packet via the OS
            try (DatagramSocket socket = new DatagramSocket()) {
                byte[] data = "ping".getBytes();
                socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), port));
            }
            
            Packet p = wd1.recv();
            assertNotNull(p, "wd1 should have captured the OS-triggered outbound packet");
            assertTrue(p.isOutbound(), "Captured packet should be outbound");
            
            // Get real interface indices
            WinDivertAddress addr = p.getWinDivertAddress();
            int ifIdx = addr.Union.Network.IfIdx;
            int subIfIdx = addr.Union.Network.SubIfIdx;

            // 2. Re-inject as INBOUND via wd1 using same interface
            Packet pIn = new Packet(p.getRaw(), new int[]{ifIdx, subIfIdx}, Enums.Direction.INBOUND);
            wd1.send(pIn);
            
            Packet rIn = wd2.recv();
            assertNotNull(rIn, "wd2 should have captured the manually injected inbound packet from wd1");
            assertTrue(rIn.isInbound(), "Captured packet should be inbound");
            
            receiver.join(2000);
        }
    }
}
