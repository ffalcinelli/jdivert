# Architecture Overview

JDivert is designed as a high-level, idiomatic Java wrapper around the [WinDivert](https://reqrypt.org/windivert.html) project. It bridges the gap between the C-based native driver and the Java Virtual Machine using [JNA (Java Native Access)](https://github.com/java-native-access/jna).

## Component Breakdown

### 1. The WinDivert Driver
At its core, JDivert relies on the WinDivert driver (`WinDivert64.sys`) and its companion DLL (`WinDivert.dll`). The driver operates at the Windows Network Stack level, allowing user-mode applications to:
- **Capture** packets using a kernel-mode filtering engine (WFP).
- **Inject** packets back into the stack.
- **Modify** or **Drop** packets in transit.

### 2. Native Library Management (`DeployHandler`)
One of JDivert's key features is its "zero-install" philosophy. 
- The native `.dll` and `.sys` files are bundled within the JDivert JAR.
- At runtime, `DeployHandler` extracts these binaries to a stable, version-specific temporary directory (e.g., `%TEMP%/jdivert-3.0.0/`).
- It skips extraction if the files already exist, improving startup time and preventing temporary folder bloat.
- It dynamically configures `jna.library.path` to point to this directory, ensuring JNA can locate and load the WinDivert library without requiring manual installation.

### 3. Native Mapping (`WinDivertDLL`)
The `WinDivertDLL` interface defines the JNA mapping to the native functions exported by `WinDivert.dll`. JDivert uses a **Zero-Copy Architecture** where possible:
- Native adapters (JNA and Panama) expose direct `java.nio.ByteBuffer` objects that map directly to the memory allocated by the driver.
- This eliminates the need to copy packet data between native memory and the Java heap.

### 4. High-Level API (`WinDivert` Class)
The `com.github.ffalcinelli.jdivert.WinDivert` class is the primary entry point for developers. It provides a clean, `AutoCloseable` interface for:
- Opening a capture handle with a specific filter.
- Receiving packets into high-level `Packet` objects that wrap native memory.
- Sending modified packets back to the stack using the same direct buffers.

### 5. Packet Representation (`Packet` & `headers`)
Packets are represented by the `Packet` class, which provides access to:
- **Headers**: Structured access to IPv4, IPv6, TCP, UDP, and ICMP headers. These classes parse data **in-place** from the underlying direct buffer.
- **Payload**: Raw access to the packet's payload data without heap allocation overhead.

JDivert handles the complexities of native memory management using `try-finally` blocks and `AutoCloseable` patterns, ensuring that when you modify a payload or a header, the underlying native buffers are correctly resized and synchronized without memory leaks.

## Data Flow

1. **Initialization**: `WinDivertDLL` is loaded, triggering `DeployHandler` to extract and register native binaries into a versioned path.
2. **Opening**: `new WinDivert(filter).open()` calls the native `WinDivertOpen`.
3. **Capture**: `w.recv()` calls `WinDivertRecvEx`. The raw bytes are wrapped in a **Direct `ByteBuffer`**, which is then passed to a Java `Packet` object. No memory copy occurs here.
4. **Modification**: Methods like `packet.getTcp().setDstPort(80)` or `packet.setPayload(data)` update the direct buffer in-place. If the payload grows beyond the buffer's capacity, a new heap buffer is transparently managed.
5. **Re-injection**: `w.send(packet)` calls `WinDivertSendEx` to push the direct buffer back into the Windows network stack.
6. **Cleanup**: Calling `w.close()` ensures the native handle is closed, and the `WinDivertAsyncResult` (if used) correctly releases its associated native memory.
