# jdivert

[![Build and Test](https://github.com/ffalcinelli/jdivert/actions/workflows/test.yml/badge.svg)](https://github.com/ffalcinelli/jdivert/actions/workflows/test.yml) [![Coverage Status](https://img.shields.io/codecov/c/github/ffalcinelli/jdivert/master.svg)](https://codecov.io/github/ffalcinelli/jdivert) [![Maven Central Repo](https://img.shields.io/maven-central/v/com.github.ffalcinelli/jdivert.svg)](https://search.maven.org/artifact/com.github.ffalcinelli/jdivert/2.2.2/jar)

Java bindings for [WinDivert](https://reqrypt.org/windivert.html), a Windows driver that allows user-mode applications to capture/modify/drop network packets sent to/from the Windows network stack.

## Requirements

- Java 8+
- Windows 7/8/10/11 or Windows Server 2008 R2+ (32 or 64 bit)
- Administrator Privileges

## Installation

Add JDivert as a dependency in your project:

### Maven

Put these lines under section `dependencies` in your `pom.xml`

```xml
<dependency>
  <groupId>com.github.ffalcinelli</groupId>
  <artifactId>jdivert</artifactId>
  <version>2.2.2</version>
</dependency>
```

### Gradle

In your `build.gradle` file make sure you include jdivert into dependencies list

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.github.ffalcinelli:jdivert:2.2.2'
}
```

JDivert bundles [WinDivert](https://reqrypt.org/windivert.html) 2.2.2 into its JAR file distribution. The first time
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

JDivert supports all features from WinDivert 2.2, including new layers (`NETWORK_FORWARD`, `FLOW`, `SOCKET`, `REFLECT`) and flags like `FRAGMENTS`.

## Development and Testing

Since WinDivert is a Windows driver, testing requires a Windows environment. A `Vagrantfile` is provided to spin up a Windows 11 VM for local testing.

### Local Testing with Vagrant

To run the project tests locally:

1. Install [Vagrant](https://www.vagrantup.com/) and [VirtualBox](https://www.virtualbox.org/).
2. Run `vagrant up` from the project root.
3. Once the VM is ready, execute the tests with:
   ```bash
   vagrant powershell -c 'cd C:/jdivert; ./gradlew test'
   ```

## Licensing

JDivert is dual-licensed under the **GNU General Public License v2.0 (GPLv2)** and the **GNU Lesser General Public License v3.0 (LGPLv3)**. This ensures compatibility with the WinDivert project and flexibility for different usage scenarios.

## Security Policy

Please refer to [SECURITY.md](SECURITY.md) for information on how to report security vulnerabilities.

## API Reference Documentation

The API Reference Documentation for JDivert can be found [here](https://ffalcinelli.github.io/jdivert).
