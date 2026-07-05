# Performance Considerations

JDivert is designed for low-overhead, high-performance packet interception. This document details techniques and settings to maximize throughput and minimize latency on both Windows and Linux.

---

## 1. Zero-Copy Architecture
JDivert employs a **Zero-Copy Architecture** using direct `java.nio.ByteBuffer` objects:
- Captured packet data is referenced directly from memory allocated by the driver, bypassing JVM heap allocations.
- Packet header parsing and modification occur in-place.
- Payload modifications reuse the same native buffer when the new payload fits within its capacity, reducing allocation and GC stress.

---

## 2. JNA Optimization: `jna.nosys`
On older Java versions using JNA, the JNA library search path can degrade startup times. Setting the `jna.nosys` property to `true` speeds up initialization:
```bash
java -Djna.nosys=true -jar your-app.jar
```

---

## 3. Project Panama (Java 22+)
For maximum performance, run JDivert on **Java 22 or higher**:
- The Project Panama adapter (`EBPFDivertPanamaNativeAdapter` / `WinDivertPanamaNativeAdapter`) uses Java's native Foreign Function & Memory API.
- This replaces JNA with direct native access, reducing the JNI call overhead by up to $3\times$ to $5\times$.

---

## 4. Specific Filtering (Kernel-Mode Filtering)
Always write the most restrictive filter expression possible:
- Filtering is executed in **kernel space** (via WFP on Windows or eBPF classifiers on Linux).
- This is orders of magnitude faster than receiving all packets (`"true"`) and filtering them within Java.
- Unmatched packets proceed down the stack with zero user-space transit overhead.

---

## 5. Packet Queue Sizing & Tuning

### Windows (WinDivert Queue)
The WinDivert driver has an internal packet queue. If the application processes packets too slowly, the queue overflows and the driver drops packets. 
- You can tune parameters like `Param.QUEUE_LEN`, `Param.QUEUE_TIME`, `Param.QUEUE_SIZE` using `w.setParam(Param, value)`.

### Linux (eBPF Queue)
On Linux, intercepted packets are placed in a kernel BPF Ring Buffer (`pcap_ringbuf`) and retrieved via a poll loop.
- To handle packet bursts, JDivert implements a user-space FIFO cache queue.
- If the user-space queue exceeds its capacity, incoming packets are dropped to prevent memory exhaustion, incrementing `STAT_QUEUE_FULL`.
- The maximum queue size can be adjusted at runtime:
  ```java
  // Set max queue size to 2048 packets on Linux
  diverter.setParam(Param.QUEUE_LEN, 2048);
  ```

---

## 6. JVM Garbage Collection
In high-throughput environments (processing thousands of packets per second), object allocations for packet metadata can put pressure on the Java Garbage Collector.
- Use low-latency collectors such as **ZGC** or **Shenandoah** to avoid GC pauses:
  ```bash
  java -XX:+UseZGC -jar your-app.jar
  ```
- Monitor GC activity. Any pause in the application thread halts packet processing, quickly filling native and Java queues.
