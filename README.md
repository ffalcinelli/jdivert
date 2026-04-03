# JDivert

[![Build and Test](https://github.com/ffalcinelli/jdivert/actions/workflows/test.yml/badge.svg)](https://github.com/ffalcinelli/jdivert/actions/workflows/test.yml)
[![Coverage Status](https://img.shields.io/codecov/c/github/ffalcinelli/jdivert/master.svg)](https://codecov.io/github/ffalcinelli/jdivert)
[![Maven Central Repo](https://img.shields.io/maven-central/v/com.github.ffalcinelli/jdivert.svg)](https://search.maven.org/artifact/com.github.ffalcinelli/jdivert/3.0.0/jar)
[![license](https://img.shields.io/badge/license-LGPLv3%20%7C%20GPLv2-blue.svg)](https://github.com/ffalcinelli/jdivert/blob/master/LICENSE)

**JDivert** is a powerful Java binding for [WinDivert](https://reqrypt.org/windivert.html), a Windows driver that allows user-mode applications to capture, modify, and drop network packets sent to or from the Windows network stack.

## Features

- **Capture** network packets matching a specific filter.
- **Modify** packet headers and payloads on the fly.
- **Drop** unwanted packets.
- **Inject** new or modified packets into the network stack.
- **Support for WinDivert 2.2+** advanced features (FLOW, SOCKET, and REFLECT layers).
- **Bundled Binaries**: No need to manually install WinDivert; the 64-bit DLL and driver are included.

## Requirements

- **Java 8+** (64-bit)
- **Windows 11** (64-bit) or Windows Server 2008 R2+
- **Administrator Privileges** (required to interact with the WinDivert driver)

> [!NOTE]
> Windows Server is currently untested but likely works if it meets the architecture requirements.

## Installation

Add JDivert as a dependency in your project:

### Maven

```xml
<dependency>
  <groupId>com.github.ffalcinelli</groupId>
  <artifactId>jdivert</artifactId>
  <version>3.0.0</version>
</dependency>
```

### Gradle

```groovy
dependencies {
    implementation 'com.github.ffalcinelli:jdivert:3.0.0'
}
```

JDivert bundles WinDivert 2.2.2 into its JAR file distribution. The first time
`WinDivertDLL` interface gets initialized, it will copy WinDivert .sys and .dll files (64-bit) inside a temporary directory and will point JNA to
load them by this directory by setting `jna.library.path` system property.

## Quick Start

The main entry points are `com.github.ffalcinelli.jdivert.WinDivert` for capturing and `com.github.ffalcinelli.jdivert.Packet` for manipulation.

### Basic Capture and Re-injection

```java
import com.github.ffalcinelli.jdivert.WinDivert;
import com.github.ffalcinelli.jdivert.Packet;

// Capture only TCP packets to port 80 (HTTP requests)
try (WinDivert w = new WinDivert("tcp.DstPort == 80")) {
    w.open();
    while (true) {
        Packet packet = w.recv();
        System.out.println("Captured: " + packet);
        w.send(packet);  // Re-inject the packet back into the stack
    }
}
```

When you call `.recv()`, the packet is **taken out** of the Windows network stack. It will not reach its destination unless you explicitly call `.send(packet)`.

### Packet Modification

You can easily modify packet headers and recalculate checksums automatically.

```java
import com.github.ffalcinelli.jdivert.WinDivert;
import com.github.ffalcinelli.jdivert.Packet;

try (WinDivert w = new WinDivert("tcp.DstPort == 1234")) {
    w.open();
    while (true) {
        Packet packet = w.recv();
        // Redirect traffic to port 80
        packet.getTcp().setDstPort(80);
        
        // WinDivert handles checksum recalculation by default when sending
        w.send(packet);
    }
}
```

## Common Use Cases

### 1. Simple Firewall (Dropping Packets)
By simply not calling `.send(packet)`, the packet is effectively dropped and never reaches its destination.

### 2. Payload Inspection and Modification
You can inspect or modify the raw bytes of the packet payload.

### 3. Traffic Logging
Log detailed information about network flows.

## WinDivert Layers

WinDivert supports different layers for capturing different types of traffic:

- `Layer.NETWORK` (default): Captures IP packets.
- `Layer.FLOW`: Captures connection events.
- `Layer.SOCKET`: Captures socket-level events.

```java
import com.github.ffalcinelli.jdivert.WinDivert;
import com.github.ffalcinelli.jdivert.Enums.Layer;

try (WinDivert w = new WinDivert("true", Layer.FLOW)) {
    w.open();
    // ...
}
```

## Security

For information on supported versions, reporting vulnerabilities, and security best practices, please see our [Security Policy](SECURITY.md).

## Development

To set up a development environment:

1. Clone the repository.
2. Run tests (requires Admin): `./gradlew test`

### Testing on other Operating Systems (using Vagrant)

Since JDivert requires Windows and Administrator privileges, you can use **Vagrant** to run the test suite on a Windows 11 virtual machine from a Linux or macOS host.

**Prerequisites:**
- [Vagrant](https://www.vagrantup.com/)
- [VirtualBox](https://www.virtualbox.org/)

**Steps:**

1.  **Bring up the VM:**
    ```bash
    vagrant up
    ```

2.  **Run the tests:**
    ```bash
    vagrant powershell -c 'cd C:/jdivert; ./gradlew test'
    ```

## API Reference

The full API documentation is available at [https://ffalcinelli.github.io/jdivert/](https://ffalcinelli.github.io/jdivert/).

## License

JDivert is dual-licensed under the **LGPL-3.0-or-later** and **GPL-2.0-or-later** licenses to match the WinDivert driver's licensing strategy.

- [GNU Lesser General Public License v3.0 or later](LICENSE-LGPL-3.0-or-later)
- [GNU General Public License v2.0 or later](LICENSE-GPL-2.0-or-later)

See the [LICENSE](LICENSE) file for more details.
