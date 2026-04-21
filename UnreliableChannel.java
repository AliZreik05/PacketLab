// UnreliableChannel.java
// Simulates an unreliable UDP channel between exactly two clients.
// First client to send HELLO becomes "A", second becomes "B".
// Usage: java UnreliableChannel <listenPort> <p> <minD> <maxD>

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class UnreliableChannel {

    /** Per-direction counters. */
    private static class Stats {
        int recv, lost, delayed;
        double totalDelay;
    }

    private static final Map<String, InetSocketAddress> users  = new LinkedHashMap<>();
    private static final Map<String, Stats>             stats  = new HashMap<>();
    private static final Set<String>                    ended  = new HashSet<>();
    private static final AtomicInteger                  active = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {

        if (args.length < 4) {
            System.err.println("Usage: java UnreliableChannel <listenPort> <p> <minD> <maxD>");
            System.exit(1);
        }

        int    port = Integer.parseInt(args[0]);
        double p    = Double.parseDouble(args[1]);
        int    minD = Integer.parseInt(args[2]);
        int    maxD = Integer.parseInt(args[3]);

        if (p < 0.0 || p > 1.0 || minD < 0 || maxD < minD) {
            System.err.println("[ERROR] Need 0<=p<=1 and 0<=minD<=maxD");
            System.exit(1);
        }

        stats.put("A", new Stats());
        stats.put("B", new Stats());

        System.out.printf("[CHANNEL] Starting on port %d | loss=%.0f%% | delay=[%d, %d] ms%n",
                port, p * 100, minD, maxD);
        System.out.println("[CHANNEL] Waiting for two clients to connect...");

        Random         rnd    = new Random();
        DatagramSocket socket = new DatagramSocket(port);
        byte[]         buffer = new byte[65535];

        // ── Phase 1: HELLO handshake ──
        while (users.size() < 2) {
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            socket.receive(dp);

            String msg = new String(dp.getData(), 0, dp.getLength(),
                                    StandardCharsets.UTF_8).trim();
            InetSocketAddress sender = new InetSocketAddress(dp.getAddress(), dp.getPort());

            if (!"HELLO".equals(msg) || users.containsValue(sender)) continue;

            String name = users.isEmpty() ? "A" : "B";
            users.put(name, sender);
            System.out.printf("[CHANNEL] %s connected — assigned name \"%s\"%n", sender, name);
        }

        sendAssign(socket, "A", "B");
        sendAssign(socket, "B", "A");
        System.out.println("[CHANNEL] Both clients connected. Session started.");

        // ── Phase 2: relay loop ──
        while (ended.size() < 2) {
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            socket.receive(dp);

            UCPPacket pkt;
            try {
                pkt = UCPPacket.fromBytes(dp.getData(), dp.getLength());
            } catch (Exception e) {
                System.err.println("[CHANNEL] Malformed packet — discarding");
                continue;
            }

            String src = pkt.src.toUpperCase();
            Stats  s   = stats.get(src);

            if (pkt.isEnd) {
                System.out.printf("[CHANNEL] END from %s — subjecting to channel unreliability%n", src);
                ended.add(src);
            } else {
                System.out.printf("[CHANNEL] Received %s%n", pkt);
                if (s != null) s.recv++;
            }

            // Loss decision (END is also subject to loss)
            double d = rnd.nextDouble();
            if (d <= p) {
                System.out.printf("[CHANNEL] DROPPED from %s (d=%.3f <= p=%.3f)%n", src, d, p);
                if (s != null && !pkt.isEnd) s.lost++;
                continue;
            }

            int delay = minD + rnd.nextInt(maxD - minD + 1);
            if (!pkt.isEnd) {
                System.out.printf("[CHANNEL] FORWARDING from %s with delay=%d ms%n", src, delay);
                if (s != null) { s.delayed++; s.totalDelay += delay; }
            }
            scheduleRelay(socket, pkt, delay);
        }

        System.out.println("[CHANNEL] Both ENDs received. Draining in-flight packets...");
        while (active.get() > 0) Thread.sleep(50);
        System.out.println("[CHANNEL] All relay threads finished.");

        printStats();
        socket.close();
    }

    private static void sendAssign(DatagramSocket socket, String name, String peer) throws Exception {
        InetSocketAddress addr = users.get(name);
        byte[] data = ("ASSIGN|" + name + "|" + peer).getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(data, data.length, addr.getAddress(), addr.getPort()));
        System.out.printf("[CHANNEL] Sent ASSIGN to %s @ %s (peer=%s)%n", name, addr, peer);
    }

    private static void scheduleRelay(DatagramSocket socket, UCPPacket pkt, int delayMs) {
        active.incrementAndGet();
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                InetSocketAddress addr = users.get(pkt.dest);
                if (addr == null) {
                    System.out.printf("[CHANNEL] WARNING: %s not registered — packet lost: %s%n",
                            pkt.dest, pkt);
                    return;
                }
                byte[] data = pkt.toBytes();
                socket.send(new DatagramPacket(data, data.length,
                        addr.getAddress(), addr.getPort()));
                System.out.printf("[CHANNEL] Forwarded to %s | %s%n", pkt.dest, pkt);
            } catch (Exception e) {
                System.err.println("Relay error: " + e.getMessage());
            } finally {
                active.decrementAndGet();
            }
        });
        t.setDaemon(false);
        t.start();
    }

    private static void printStats() {
        System.out.println("\n========== SESSION STATISTICS ==========");
        for (String name : List.of("A", "B")) {
            Stats s = stats.get(name);
            System.out.printf("Packets received from user %s: %d | Lost: %d | Delayed: %d%n",
                    name, s.recv, s.lost, s.delayed);
        }
        Stats a = stats.get("A"), b = stats.get("B");
        System.out.printf("Average delay from A to B: %.1f ms.%n",
                a.delayed > 0 ? a.totalDelay / a.delayed : 0.0);
        System.out.printf("Average delay from B to A: %.1f ms.%n",
                b.delayed > 0 ? b.totalDelay / b.delayed : 0.0);
        System.out.println("=========================================");
    }
}