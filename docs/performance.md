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
On Linux, captured packets go from the kernel ring buffer (8 MiB per handle, `-Djdivert.ebpf.ringBytes`) into
libebpfdivert's user-space queue, which honours the same parameters as WinDivert:
- `Param.QUEUE_LEN`, `Param.QUEUE_TIME` and `Param.QUEUE_SIZE` bound the queue by packets, age and bytes.
- Packets that overflow the queue are dropped and counted in `STAT_QUEUE_FULL`; ring overflows (the kernel then
  lets packets pass) are counted in `STAT_RINGBUF_FULL`.
  ```java
  diverter.setParam(Param.QUEUE_LEN, 8192);
  ```
- Filters that lower exactly to kernel rules are cheapest; filters on payload or other rich fields are
  pre-filtered in the kernel and completed in user space, costing a round trip for non-matching packets.

---

## 6. JVM Garbage Collection
In high-throughput environments (processing thousands of packets per second), object allocations for packet metadata can put pressure on the Java Garbage Collector.
- Use low-latency collectors such as **ZGC** or **Shenandoah** to avoid GC pauses:
  ```bash
  java -XX:+UseZGC -jar your-app.jar
  ```
- Monitor GC activity. Any pause in the application thread halts packet processing, quickly filling native and Java queues.
