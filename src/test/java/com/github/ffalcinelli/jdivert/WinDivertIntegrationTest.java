package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

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

        final String secretInjectedMessage = "JDivert-Modified-Payload";

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
        // Capture traffic going TO the server port on loopback
        wd = new WinDivert("loopback and tcp.DstPort == " + port).open();

        // 4. Client execution
        final String originalMessage = "Original-Request";
        
        // We run client in a thread so we can handle recv/send in main thread
        AtomicReference<String> clientReceived = new AtomicReference<>();
        Thread clientThread = new Thread(() -> {
            try {
                Thread.sleep(500); // Wait for wd.recv to be ready
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
        // We need to pass through SYN, and modify the PSH/ACK packet containing data
        boolean modified = false;
        long deadline = System.currentTimeMillis() + 5000;
        
        while (!modified && System.currentTimeMillis() < deadline) {
            Packet p = wd.recv();
            if (p.getPayload() != null && p.getPayload().length > 0) {
                // Verify we got the original message
                String data = new String(p.getPayload(), StandardCharsets.UTF_8);
                if (data.contains(originalMessage)) {
                    // Modify it!
                    p.setPayload(secretInjectedMessage.getBytes(StandardCharsets.UTF_8));
                    p.recalculateChecksum();
                    modified = true;
                }
            }
            wd.send(p); // Re-inject (modified or not)
        }

        clientThread.join(5000);
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
    public void testInboundOutboundLogic() throws Exception {
        // Using loopback ICMP to verify inbound/outbound bits without external network noise
        WinDivert icmpWd = new WinDivert("loopback and icmp").open();
        try {
            AtomicReference<Packet> outbound = new AtomicReference<>();
            AtomicReference<Packet> inbound = new AtomicReference<>();
            
            Thread pinger = new Thread(() -> {
                try {
                    Thread.sleep(500);
                    // Pinging localhost generates loopback traffic
                    InetAddress.getByName("127.0.0.1").isReachable(2000);
                } catch (Exception ignore) {}
            });
            pinger.start();

            // Capture request and reply
            for (int i = 0; i < 2; i++) {
                Packet p = icmpWd.recv();
                // For WinDivert loopback, request is outbound, reply is inbound
                if (p.isOutbound()) outbound.set(p);
                else inbound.set(p);
                icmpWd.send(p); 
            }

            assertNotNull(outbound.get(), "Should have captured an outbound ICMP packet");
            assertNotNull(inbound.get(), "Should have captured an inbound ICMP packet");
            
            assertTrue(outbound.get().isOutbound());
            assertTrue(inbound.get().isInbound());
            
        } finally {
            icmpWd.close();
        }
    }

    private static class AtomicReference<T> {
        private T value;
        public void set(T v) { this.value = v; }
        public T get() { return value; }
    }
}
