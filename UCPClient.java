// UCPClient.java
// Sends 1000 packets to its peer through the UnreliableChannel server.
// Usage: java UCPClient <localPort> <channelHost> <channelPort>

import java.nio.charset.StandardCharsets;

public class UCPClient {

    private static final int TOTAL = 1000;          //total number of packets to send
    private static final int INTERVAL_MS = 500;         //delay between sends in ms

    public static void main(String[] args) throws Exception {

        try {
            int clientPort = Integer.parseInt(args[0]);
            String serverHostName = args[1];
            int serverPort = Integer.parseInt(args[2]);
            UCPSocket socket = new UCPSocket(clientPort, serverHostName, serverPort);

            String me = socket.getMyName();
            String peer = socket.getPeerName();
            System.out.printf("[%s] Session started. Peer is %s.%n", me, peer);

            // Receiver thread so we can send and receive at the same time
            int[] received = {0};       
            Thread receiver = new Thread(() -> {
                try {
                    byte[] data;
                    while ((data = socket.ucp_receive()) != null) {
                        received[0]++;
                        System.out.printf("[%s RECV] \"%s\" | Total received: %d%n", me, new String(data, StandardCharsets.ISO_8859_1), received[0]);
                    }
                } 
                catch (Exception e) {
                    System.err.println(me + " receiver error: " + e.getMessage());
                }
            }, me + "-receiver");
            receiver.setDaemon(true);           //dies automatically when main thread exits
            receiver.start();

            // Sender loop, sends 1000 packets, one every 500ms
            for (int i = 1; i <= TOTAL; i++) {
                byte[] data = ("Packet #" + i + " from " + me).getBytes(StandardCharsets.ISO_8859_1);
                socket.ucp_send(data, data.length);
                System.out.printf("[%s SEND] Packet #%d%n", me, i);
                Thread.sleep(INTERVAL_MS);
            }

            socket.close();
            Thread.sleep(3000);         //give the thread time to drain any remaining packets

            System.out.printf("%n[%s] Done. Total packets received from %s: %d%n", me, peer, received[0]);
        }
        catch(Exception e) {
            System.err.println("Usage: java UCPClient <localPort> <channelHost> <channelPort>");
            System.err.println("\nError:");
            System.err.println(e);
        } 
    }
}