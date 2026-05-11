# Performance Considerations

JDivert is built on JNA, which introduces some overhead compared to pure C. However, with proper configuration and patterns, it is capable of high-performance packet processing.

## 1. JNA Optimization: `jna.nosys`

By default, JNA tries to load system libraries in a way that can be slow. Setting the `jna.nosys` system property to `true` can improve startup time and potentially library lookup performance. JDivert's `DeployHandler` handles the library path, so system lookups are generally unnecessary.

```bash
java -Djna.nosys=true -jar your-app.jar
```

## 2. Packet Buffer Sizing

When calling `w.recv()`, JDivert allocates a buffer. The default size is **65575 bytes** (matching the native `WINDIVERT_MTU_MAX`). 

- **MTU & Loopback**: This high default ensures that even the largest possible IPv6 packets (40 bytes header + 65535 bytes payload) are not truncated.
- **Optimization**: If you are capturing specific traffic known to be small (e.g., only small control packets), you can reduce the buffer size to save memory and allocation time: `w.recv(1024)`.

## 3. Zero-Copy Operations

JDivert is architected to minimize memory copying between the native driver and the Java application.

- **Direct Buffers**: Captured packets are wrapped in direct `java.nio.ByteBuffer` objects. All header parsing and payload access operate directly on this native memory.
- **In-Place Modification**: Modifying a packet (e.g., changing a port or flag) updates the native memory directly.
- **Payload Reuse**: The `packet.setPayload(data)` method will reuse the existing native buffer if the new payload fits within the original buffer's capacity, further reducing allocation overhead.

## 4. Multithreading

WinDivert handles can be used across multiple threads, but there are important considerations:

- **Parallel Processing**: You can have multiple threads calling `recv()` on the *same* handle. The driver will distribute packets among the threads. This is the recommended way to scale processing on multi-core systems.
- **Thread Safety**: The `WinDivert` class itself is thread-safe for `recv()` and `send()` operations.

## 5. Asynchronous I/O (`recvAsync`)

The `recvAsync()` method uses WinDivert's overlapped I/O capabilities. 
- **Benefits**: It allows a single thread to manage multiple concurrent capture operations or to perform other tasks while waiting for network data.
- **Memory Safety**: `WinDivertAsyncResult` implements `AutoCloseable`. Using try-with-resources with async results ensures the underlying native buffer is released exactly once, even if the operation is cancelled or an exception occurs.
- **Scalability**: While `recv()` is fine for many use cases, `recvAsync()` is superior for complex event-driven architectures.

## 5. Filter Optimization

The WinDivert filter string is compiled and executed in **kernel mode**. 
- **Efficiency**: The more specific your filter, the fewer packets the driver has to "divert" to user-mode. 
- **Recommendation**: Always use the most restrictive filter possible. For example, `tcp.DstPort == 80` is much more efficient than `true` followed by an `if` check in Java.

## 6. Garbage Collection (GC)

Since every captured packet creates a new `Packet` object and a new byte array, high-traffic captures can put significant pressure on the Java Garbage Collector.
- **Tune your JVM**: Use a low-latency collector like **ZGC** or **Shenandoah** if you are processing thousands of packets per second.
- **Monitor**: Keep an eye on GC pauses, as they will cause the WinDivert internal queue to fill up, potentially leading to dropped packets.

## 7. WinDivert Queue Length

The WinDivert driver has an internal queue. If your Java application is too slow to process packets, this queue will fill up. 
- You can increase the queue size via flags when creating the `WinDivert` instance (though JDivert currently uses defaults).
- If the queue is full, the driver will drop packets. Ensure your processing logic is as lean as possible.
