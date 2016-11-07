# jdivert

A Java binding to WinDivert driver

[![AppVeyor Build Status](https://img.shields.io/appveyor/ci/ffalcinelli/pydivert/master.svg)](https://ci.appveyor.com/project/ffalcinelli/jdivert] [![Coverage Status](https://img.shields.io/codecov/c/github/ffalcinelli/jdivert/master.svg)](https://codecov.io/github/ffalcinelli/jdivert)


## Requirements

- Java 1.6+
- Windows Vista/7/8/10 or Windows Server 2008 (32 or 64 bit)
- Administrator Privileges

## Installation

TODO

## Getting Started

JDivert consists of two main classes: com.github.ffalcinelli.jdivert.WinDivert and
com.github.ffalcinelli.jdivert.Packet. This follows the [PyDivert](https://github.com/ffalcinelli/pydivert) structure.

First, you usually want to create a WinDivert object to start capturing network traffic and then call .recv() to receive the first Packet that was captured.
By receiving packets, they are taken out of the Windows network stack and will not be sent out unless you take action. You can re-inject packets by calling .send(packet). The following example opens a WinDivert handle, receives a single packet, prints it, re-injects it, and then exits:

```java
// Capture only TCP packets to port 80, i.e. HTTP requests.
WinDivert w = new WinDivert("tcp.DstPort == 80 and tcp.PayloadLength > 0");

w.open(); // packets will be captured from now on

packet = w.recv();  // read a single packet
System.out.println(packet);
w.send(packet);  // re-inject the packet into the network stack

w.close();  // stop capturing packets
```

Packets that are not matched by the "tcp.DstPort == 80 and tcp.PayloadLength > 0" filter will not be handled by WinDivert and continue as usual. The syntax for the filter language is described in the [WinDivert documentation](https://reqrypt.org/windivert-doc.html#filter_language).

## API Reference Documentation

The API Reference Documentation for JDivert can be found [here](TODO...).