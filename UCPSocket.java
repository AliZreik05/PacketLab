// UCPSocket.java
// Reusable socket abstraction over the UnreliableChannel server.

import java.net.*;
import java.nio.charset.StandardCharsets;

public class UCPSocket {

    private static final int BUFFER_SIZE = 65535;

    private final DatagramSocket socket;
    private final InetAddress    channelAddr;
    private final int            channelPort;

    public final String myName, peerName;

    /**
     * Binds to {@code localPort}, sends HELLO to the channel, and blocks
     * until the channel replies "ASSIGN|<myName>|<peerName>".
     */
    public UCPSocket(int localPort, String serverHostName, int serverPort) throws Exception {
        this.socket      = new DatagramSocket(localPort);
        this.channelAddr = InetAddress.getByName(serverHostName);
        this.channelPort = serverPort;

        // ── HELLO handshake ──
        sendRaw("HELLO".getBytes(StandardCharsets.UTF_8));
        System.out.println("[UCPSocket] HELLO sent — waiting for name assignment...");

        byte[] buf = new byte[BUFFER_SIZE];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        socket.receive(dp);

        String[] parts = new String(dp.getData(), 0, dp.getLength(),
                                    StandardCharsets.UTF_8).split("\\|");
        if (parts.length != 3 || !"ASSIGN".equals(parts[0]))
            throw new Exception("Unexpected handshake reply");

        this.myName   = parts[1];
        this.peerName = parts[2];
        System.out.printf("[UCPSocket] Assigned name=%s  peer=%s%n", myName, peerName);
    }

    private void sendRaw(byte[] data) throws Exception {
        socket.send(new DatagramPacket(data, data.length, channelAddr, channelPort));
    }

    /** Send {@code len} bytes from {@code b} to the peer. */
    public void ucp_send(byte[] b, int len) throws Exception {
        String msg = new String(b, 0, len, StandardCharsets.ISO_8859_1);
        sendRaw(new UCPPacket(myName, peerName, 0, msg, false).toBytes());
    }

    /** Block until a data packet arrives. Returns null on END. */
    public byte[] ucp_receive() throws Exception {
        byte[] buf = new byte[BUFFER_SIZE];
        while (true) {
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            socket.receive(dp);
            try {
                UCPPacket pkt = UCPPacket.fromBytes(dp.getData(), dp.getLength());
                if (pkt.isEnd) {
                    System.out.println("[UCPSocket] END received from peer.");
                    return null;
                }
                return pkt.message.getBytes(StandardCharsets.ISO_8859_1);
            } catch (IllegalArgumentException ignored) {
                // malformed — skip
            }
        }
    }

    /** Send END to the peer (via the channel) and close the socket. */
    public void close() throws Exception {
        sendRaw(UCPPacket.end(myName, peerName).toBytes());
        System.out.println("[UCPSocket] END sent. Closing socket.");
        socket.close();
    }

    public String getMyName()   { return myName; }
    public String getPeerName() { return peerName; }

    public void setTimeout(int timeoutMs) throws SocketException {
        socket.setSoTimeout(timeoutMs);
    }
}
