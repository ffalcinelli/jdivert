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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static com.github.ffalcinelli.jdivert.headers.Tcp.Flag.FIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by fabio on 06/11/2016.
 */
public class LiveCaptureTestCase {
    WinDivert wd;
    EchoServer srv;
    EchoClient clt;

    @Before
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

    @After
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
        startupWithFilter("tcp.DstPort == " + srv.getPort() + " and tcp.PayloadLength > 0");
        wd.open();
        Packet p = wd.recv();
        assertTrue(p.isTcp());
        wd.send(p, false);
        endThreads();
        assertEquals(srv.alterMessage(clt.getMessage()), clt.getResponse());
    }

    @Test
    public void editPacket() throws WinDivertException, InterruptedException {
        startupWithFilter("tcp.DstPort == " + srv.getPort() + " and tcp.PayloadLength > 0");
        wd.open();
        String message = "Echo message.";
        Packet p = wd.recv();
        p.setPayload(message.getBytes());
        assertEquals(message, new String(p.getPayload()).trim());
        wd.send(p);
        endThreads();
        assertEquals(srv.alterMessage(message), clt.getResponse());
    }

    @Test
    public void divert() throws IOException, WinDivertException, InterruptedException {
        EchoServer spoofer = new EchoServer();
        spoofer.start();
        startupWithFilter("tcp.DstPort == " + srv.getPort() + " or " +
                "tcp.SrcPort == " + spoofer.getPort());
        wd.open();
        Packet p;
        do {
            p = wd.recv();
            if (p.getDstPort() == srv.getPort())
                p.setDstPort(spoofer.getPort());

            if (p.getSrcPort() == spoofer.getPort())
                p.setSrcPort(srv.getPort());

            wd.send(p);
        } while (!p.getTcp().is(FIN));
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
            PrintWriter out = null;
            BufferedReader in = null;
            Socket socket = null;
            try {
                socket = new Socket(address, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                synchronized (this) {
                    out.println(message);
                    response = in.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeAnyway(out, in, socket);
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
            socket = new ServerSocket(portNumber);
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
            PrintWriter out = null;
            BufferedReader in = null;
            Socket clientSocket = null;
            try {
                while (!stop) {
                    clientSocket = socket.accept();
                    out = new PrintWriter(clientSocket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                    String data = in.readLine();
                    if (data != null)
                        out.print(alterMessage(data));
                    out.flush();

                    closeAnyway(out, in, clientSocket);
                }
            } catch (IOException e) {

            } finally {
                closeAnyway(out, in, clientSocket);
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
        for (Object obj : toClose) {
            if (obj != null) {
                try {
                    //TODO: from Java 1.7 Socket and ServerSocket implement Closeable so this code could be refactored
                    if (obj instanceof Socket)
                        ((Socket) obj).close();
                    else if (obj instanceof ServerSocket)
                        ((ServerSocket) obj).close();
                    else if (obj instanceof Closeable)
                        ((Closeable) obj).close();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
