/*
 * Copyright (c) Fabio Falcinelli 2016.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static com.github.ffalcinelli.jdivert.headers.Tcp.Flag.FIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by fabio on 06/11/2016.
 */
public class LiveCaptureTestCase {
    WinDivert wd;
    EchoServer srv;
    EchoClient clt;

    @BeforeEach
    public void setUp() throws IOException {
        srv = new EchoServer();
        clt = new EchoClient(srv.getAddress(), srv.getPort(), "Test message.");

    }

    public void startupWithFilter(String filter) {
        wd = new WinDivert(filter);
        clt.setWinDivert(wd);
        srv.start();
        clt.start();
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        endThreads();
    }

    public void endThreads() throws InterruptedException {
        if (srv != null) {
            srv.close();
            srv.join();
        }
        if (clt != null) clt.join();
    }

    @Test
    public void passThrough() throws WinDivertException, InterruptedException {
        startupWithFilter("loopback and tcp.DstPort == " + srv.getPort() + " and tcp.PayloadLength > 0");
        wd.open();
        Packet p = wd.recv();
        assertTrue(p.isTcp());
        wd.send(p, false);
        endThreads();
        assertEquals(srv.alterMessage(clt.getMessage()), clt.getResponse());
    }

    @Test
    public void editPacket() throws WinDivertException, InterruptedException {
        startupWithFilter("loopback and tcp.DstPort == " + srv.getPort() + " and tcp.PayloadLength > 0");
        wd.open();
        String message = "Echo message.";
        Packet p = wd.recv();
        String originalPayload = new String(p.getPayload());
        // Replace "Test" with "Echo" in the payload, preserving length and newline
        String newPayloadStr = originalPayload.replace("Test", "Echo");
        p.setPayload(newPayloadStr.getBytes());
        
        wd.send(p);
        endThreads();
        assertEquals(srv.alterMessage(message), clt.getResponse());
    }

    @Test
    public void divert() throws IOException, WinDivertException, InterruptedException {
        EchoServer spoofer = new EchoServer();
        spoofer.start();
        startupWithFilter("loopback and (tcp.DstPort == " + srv.getPort() + " or " +
                "tcp.SrcPort == " + spoofer.getPort() + ")");
        wd.open();
        Packet p;
        long deadline = System.currentTimeMillis() + 10000;
        do {
            p = wd.recv();
            if (p.getDstPort().orElse(-1) == srv.getPort())
                p.setDstPort(spoofer.getPort());

            if (p.getSrcPort().orElse(-1) == spoofer.getPort())
                p.setSrcPort(srv.getPort());

            wd.send(p);
        } while (!p.getTcp().get().is(FIN) && System.currentTimeMillis() < deadline);
        endThreads();
        spoofer.close();
        spoofer.join();
        assertEquals(spoofer.alterMessage(clt.getMessage()), clt.getResponse());
    }


    public static class EchoClient extends Thread {
        InetAddress address;
        int port;
        WinDivert winDivert;
        String response, message;

        public EchoClient(InetAddress address, int port, String message) {
            this.address = address;
            this.port = port;
            this.message = message;
        }

        public void setWinDivert(WinDivert winDivert) {
            this.winDivert = winDivert;
        }

        public void waitForWindivert() {
            while (winDivert != null && !winDivert.isOpen()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        }

        public void run() {
            waitForWindivert();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address, port), 5000);
                socket.setSoTimeout(5000);
                try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    synchronized (this) {
                        out.println(message);
                        response = in.readLine();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public String getMessage() {
            return message;
        }

        public String getResponse() {
            synchronized (this) {
                return response;
            }
        }
    }

    public static class EchoServer extends Thread {
        private ServerSocket socket;
        private boolean stop;

        public EchoServer(int portNumber) throws IOException {
            socket = new ServerSocket(portNumber, 50, InetAddress.getByName("127.0.0.1"));
        }

        public EchoServer() throws IOException {
            this(0);
        }

        public int getPort() {
            return socket.getLocalPort();
        }

        public InetAddress getAddress() {
            return socket.getInetAddress();
        }

        public void run() {
            try {
                while (!stop) {
                    try (Socket clientSocket = socket.accept();
                         PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                        String data = in.readLine();
                        if (data != null)
                            out.print(alterMessage(data));
                        out.flush();
                    }
                }
            } catch (IOException e) {
            }
        }

        public String alterMessage(String message) {
            return String.format("{message: %s, port: %d}", message.toUpperCase(), getPort());
        }

        public void close() {
            closeAnyway(socket);
            stop = true;
        }
    }

    public static void closeAnyway(Object... toClose) {
        java.util.Arrays.stream(toClose)
                .filter(obj -> obj instanceof AutoCloseable)
                .map(obj -> (AutoCloseable) obj)
                .forEach(ac -> {
                    try {
                        ac.close();
                    } catch (Exception ignore) {
                    }
                });
    }
}
