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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by fabio on 06/11/2016.
 */
public class WinDivertRunningTestCase {

    int port;
    WinDivert w;
    EchoServer srv;
    EchoClient clt;

    Thread srvThread;
    Thread cltThread;
    String message = "This is a test message.";

    @Before
    public void setUp() throws IOException {
        port = getRandomFreePort();
        w = new WinDivert("tcp.DstPort == " + port);
        srv = new EchoServer(port, w);
        clt = new EchoClient("localhost", srv.getPort(), message, w);

        srvThread = new Thread(srv);
        cltThread = new Thread(clt);
        srvThread.start();
        cltThread.start();
    }

    public void waitForTermination() throws InterruptedException {
        srv.close();
        if (cltThread != null) cltThread.join();
        if (srvThread != null) srvThread.join();
    }

    @After
    public void tearDown() throws WinDivertException {
        if (srv != null) srv.close();
        if (w != null) w.close();
    }

    @Test
    public void passThrough() throws WinDivertException, InterruptedException, IOException {
        w.open();
        Packet p = w.recv();
        assertTrue(p.isTcp());
        w.send(p, true);
        waitForTermination();
        assertEquals(
                message.toUpperCase() + " [" + port + "]",
                clt.getLastMessage());
    }

    public static int getRandomFreePort() throws IOException {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            return socket.getLocalPort();
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignore) {

                }
            }
        }
    }

    public static class Peer implements Runnable {
        protected WinDivert w;
        protected int delay = 200;

        public void waitForWinDivert() {
            try {
                while (!w.isOpen()) {
                    Thread.sleep(delay);
                }
                Thread.sleep(delay);
            } catch (InterruptedException e) {
            }
        }

        public void run() {
            waitForWinDivert();
        }

    }

    public static class EchoClient extends Peer {
        String toAddress;
        int portNumber;
        String message;
        String lastMessage;

        public EchoClient(String toAddress, int portNumber, String message, WinDivert w) {
            this.toAddress = toAddress;
            this.portNumber = portNumber;
            this.message = message;
            this.w = w;
        }

        public void run() {
            Socket echoSocket = null;
            PrintWriter out = null;
            BufferedReader in = null;
            do {
                waitForWinDivert();
                try {
                    echoSocket = new Socket(toAddress, portNumber);
                    out = new PrintWriter(echoSocket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(
                            echoSocket.getInputStream()));

                    out.println(message.toLowerCase());
                    String temp = in.readLine();
                    if (temp!=null)
                        lastMessage = temp;
                } catch (SocketException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (out != null) out.close();
                    try {
                        if (in != null) in.close();
                    } catch (Exception ignore) {
                    }
                    try {
                        if (echoSocket != null) echoSocket.close();
                    } catch (Exception ignore) {
                    }
                }
            } while (true);
        }

        public String getLastMessage() {
            return lastMessage;
        }
    }

    public static class EchoServer extends Peer {
        private ServerSocket serverSocket;
        boolean stop;

        public EchoServer(int portNumber) throws IOException {
            this(portNumber, null);
        }

        public EchoServer(int portNumber, WinDivert w) throws IOException {
            serverSocket = new ServerSocket(portNumber);
            this.w = w;
        }

        public int getPort() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void run() {
            PrintWriter out = null;
            BufferedReader in = null;
            Socket clientSocket = null;
            try {
                waitForWinDivert();
                while (!stop) {
                    clientSocket = serverSocket.accept();
                    out = new PrintWriter(clientSocket.getOutputStream(),
                            true);
                    in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()));

                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        out.println(inputLine.toUpperCase() + " [" + getPort() + "]");
                    }
                }
            } catch (Exception e) {

            } finally {
                if (out != null) out.close();
                try {
                    if (in != null) in.close();
                } catch (IOException ignore) {
                }
                try {
                    if (clientSocket != null) clientSocket.close();
                } catch (IOException ignore) {
                }
            }
        }

        public void close() {
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (IOException ignore) {
            }
            stop = true;
        }

    }
}
