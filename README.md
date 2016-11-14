# jdivert

[![AppVeyor Build Status](https://img.shields.io/appveyor/ci/ffalcinelli/jdivert/master.svg)](https://ci.appveyor.com/project/ffalcinelli/jdivert) [![Coverage Status](https://img.shields.io/codecov/c/github/ffalcinelli/jdivert/master.svg)](https://codecov.io/github/ffalcinelli/jdivert)

Java bindings for [WinDivert](https://reqrypt.org/windivert.html), a Windows driver that allows user-mode applications to capture/modify/drop network packets sent to/from the Windows network stack.

## Requirements

- Java 1.6+
- Windows Vista/7/8/10 or Windows Server 2008 (32 or 64 bit)
- Administrator Privileges

## Installation

Add JDivert as a dependency in your project:

### Maven

Put these lines under section `dependencies` in your `pom.xml`

```xml
<dependency>
  <groupId>com.github.ffalcinelli</groupId>
  <artifactId>jdivert</artifactId>
  <version>1.0</version>
</dependency>
```

### Gradle

In your `build.gradle` file make sure you include jdivert into dependencies list

```groovy
repositories {
    mavenCentral()
}

dependencies {
    compile 'com.github.ffalcinelli:jdivert:1.0'
}
```

JDivert bundles [WinDivert](https://reqrypt.org/windivert.html) 1.1.8 into its JAR file distribution. The first time
`WinDivertDLL` interface gets initialized, it will copy WinDivert .sys and .dll files inside a temporary directory and will point JNA to
load them by this directory by setting `jna.library.path` system property.
To have less impact in projects using JNA, the `jna.library.path` setting is saved before and restored after the WinDivert deployment and load.
Upon exit, temporary dir will be removed and so the files in it.


## Getting Started

JDivert consists of two main classes: `WinDivert` and
`Packet`. This follows the [PyDivert](https://github.com/ffalcinelli/pydivert) structure.

First, you usually want to create a WinDivert object to start capturing network traffic and then call .recv() to receive the first Packet that was captured.
By receiving packets, they are taken out of the Windows network stack and will not be sent out unless you take action. You can re-inject packets by calling .send(packet). The following example opens a WinDivert handle, receives a single packet, prints it, re-injects it, and then exits:

```java
// Capture only TCP packets to port 80, i.e. HTTP requests.
WinDivert w = new WinDivert("tcp.DstPort == 80 and tcp.PayloadLength > 0");

w.open(); // packets will be captured from now on

Packet packet = w.recv();  // read a single packet
System.out.println(packet);
w.send(packet);  // re-inject the packet into the network stack

w.close();  // stop capturing packets
```

Packets that are not matched by the "tcp.DstPort == 80 and tcp.PayloadLength > 0" filter will not be handled by WinDivert and continue as usual. The syntax for the filter language is described in the [WinDivert documentation](https://reqrypt.org/windivert-doc.html#filter_language).

## API Reference Documentation

The API Reference Documentation for JDivert can be found [here](https://ffalcinelli.github.io/jdivert).