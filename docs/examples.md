# JDivert Examples

This page provides a collection of copy-pasteable examples for common JDivert use cases.

## 1. Simple Packet Capture and Re-injection

The most basic use case is capturing packets matching a filter and re-injecting them back into the network stack. For a detailed reference on filter syntax, see our [Filter Language Guide](filters.md).

```java
import com.github.ffalcinelli.jdivert.WinDivert;
import com.github.ffalcinelli.jdivert.Packet;

public class BasicCapture {
    public static void main(String[] args) {
        // Capture only TCP packets to port 80
        try (WinDivert w = new WinDivert("tcp.DstPort == 80").open()) {
            System.out.println("Listening for HTTP traffic...");
            
            while (true) {
                Packet packet = w.recv();
                packet.getTcp().ifPresent(tcp -> 
                    System.out.println("Captured TCP packet from port: " + tcp.getSrcPort()));
                
                // Re-inject the packet so it reaches its destination
                w.send(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## 2. Modifying Payloads

JDivert makes it easy to modify both headers and payloads. When you change the payload, JDivert automatically updates the relevant IP and Transport layer length fields.

```java
import com.github.ffalcinelli.jdivert.WinDivert;
import com.github.ffalcinelli.jdivert.Packet;

public class PayloadModifier {
    public static void main(String[] args) {
        try (WinDivert w = new WinDivert("tcp.DstPort == 1234").open()) {
            while (true) {
                Packet packet = w.recv();
...
                // Modify payload
                String original = new String(packet.getPayload());
                String modified = original.replace("foo", "bar");
                packet.setPayload(modified.getBytes());
                
                // Re-inject the modified packet
                w.send(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## 3. Asynchronous Capture

For high-performance applications, you can use the asynchronous API to avoid blocking the main thread while waiting for packets.

```java
import com.github.ffalcinelli.jdivert.WinDivert;
import com.github.ffalcinelli.jdivert.WinDivertAsyncResult;
import com.github.ffalcinelli.jdivert.Packet;
import java.util.concurrent.TimeUnit;

public class AsyncCapture {
    public static void main(String[] args) {
        try (WinDivert w = new WinDivert("true").open()) {
            
            // Start an asynchronous receive operation
            try (WinDivertAsyncResult<Packet> result = w.recvAsync()) {
                // Do other work while waiting...
                System.out.println("Waiting for packet asynchronously...");
                
                // Wait for the result with a timeout
                Packet packet = result.get(5, TimeUnit.SECONDS);
                if (packet != null) {
                    System.out.println("Async captured: " + packet);
                    w.send(packet);
                } else {
                    System.out.println("Timed out waiting for packet.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## 4. Using the FLOW Layer

The `FLOW` layer allows you to monitor connection events rather than individual packets.

```java
import com.github.ffalcinelli.jdivert.WinDivert;
import com.github.ffalcinelli.jdivert.Enums.Layer;
import com.github.ffalcinelli.jdivert.Packet;

public class FlowMonitor {
    public static void main(String[] args) {
        // Use Layer.FLOW to monitor connections
        try (WinDivert w = new WinDivert("true", Layer.FLOW).open()) {
            while (true) {
                Packet flow = w.recv();
                // For FLOW layer, 'flow' contains connection info
                System.out.println("Flow event: " + flow);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## 5. Dropping Packets (Simple Firewall)

To drop a packet, simply don't call `send()` after receiving it.

```java
import com.github.ffalcinelli.jdivert.WinDivert;
public class SimpleFirewall {
    public static void main(String[] args) {
        // Blacklist a specific IP
        try (WinDivert w = new WinDivert("ip.SrcAddr == 1.2.3.4").open()) {
            System.out.println("Blocking 1.2.3.4...");
            while (true) {
                w.recv(); 
...
                // We don't call w.send(packet), so the packet is dropped
                System.out.println("Dropped packet from 1.2.3.4");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```
