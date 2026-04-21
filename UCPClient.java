// UCPClient.java
// Sends 1000 packets to its peer through the UnreliableChannel server.
// Usage: java UCPClient <localPort> <channelHost> <channelPort>

import java.nio.charset.StandardCharsets;

public class UCPClient {

    private static final int TOTAL       = 1000;
    private static final int INTERVAL_MS = 500;

    public static void main(String[] args) throws Exception {

        if (args.length < 3) {
            System.err.println("Usage: java UCPClient <localPort> <channelHost> <channelPort>");
            System.exit(1);
        }

        UCPSocket sock = new UCPSocket(
                Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));

        String me   = sock.getMyName();
        String peer = sock.getPeerName();
        System.out.printf("[%s] Session started. Peer is %s.%n", me, peer);

        // ── Receiver thread ──
        int[] received = {0};
        Thread receiver = new Thread(() -> {
            try {
                byte[] data;
                while ((data = sock.ucp_receive()) != null) {
                    received[0]++;
                    System.out.printf("[%s RECV] \"%s\" | Total received: %d%n",
                            me, new String(data, StandardCharsets.ISO_8859_1), received[0]);
                }
            } catch (Exception e) {
                System.err.println(me + " receiver error: " + e.getMessage());
            }
        }, me + "-receiver");
        receiver.setDaemon(true);
        receiver.start();

        // ── Sender loop ──
        for (int i = 1; i <= TOTAL; i++) {
            byte[] data = ("Packet #" + i + " from " + me).getBytes(StandardCharsets.ISO_8859_1);
            sock.ucp_send(data, data.length);
            System.out.printf("[%s SEND] Packet #%d%n", me, i);
            Thread.sleep(INTERVAL_MS);
        }

        sock.close();
        Thread.sleep(3000); // let receiver drain inbound

        System.out.printf("%n[%s] Done. Total packets received from %s: %d%n",
                me, peer, received[0]);
    }
}