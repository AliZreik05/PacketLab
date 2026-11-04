// UCPSocket.java
// Reusable socket abstraction over the UnreliableChannel server.

import java.net.*;
import java.nio.charset.StandardCharsets;

public class UCPSocket implements AutoCloseable {

    private static final int BUFFER_SIZE = 65535;       //max UDP datagram size

    private final DatagramSocket socket;
    private final InetAddress    channelAddr;       //server's address
    private final int            channelPort;       //server's por

    public final String myName, peerName;       //assigned by channel ("A" OR "B")

    /**
     * Binds to {@code localPort}, sends HELLO to the channel, and blocks
     * until the channel replies "ASSIGN|<myName>|<peerName>".
     */
    public UCPSocket(int localPort, String serverHostName, int serverPort) throws Exception {
        this.socket      = new DatagramSocket(localPort);
        this.channelAddr = InetAddress.getByName(serverHostName);
        this.channelPort = serverPort;
        socket.connect(channelAddr, channelPort);
        socket.setSoTimeout(15000);

        //HELLO handshake, needs two clients before session can start
        String role = System.getProperty("packetlab.role", "");
        sendRaw((role.isEmpty() ? "HELLO" : "HELLO|" + role).getBytes(StandardCharsets.UTF_8));
        System.out.println("[UCPSocket] HELLO sent — waiting for name assignment...");

        byte[] buf = new byte[BUFFER_SIZE]; //block until channel assigns us a name and tells us our peers name
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        try {
            socket.receive(dp);
        } catch (Exception e) {
            socket.close();
            throw e;
        }

        String[] parts = new String(dp.getData(), 0, dp.getLength(),        //reply format: "ASSIGN|<myname>|<peername>"
                                    StandardCharsets.UTF_8).split("\\|");
        if (parts.length != 3 || !"ASSIGN".equals(parts[0])) {
            socket.close();
            throw new Exception("Unexpected handshake reply");
        }

        this.myName   = parts[1];
        this.peerName = parts[2];
        socket.setSoTimeout(30000);
        System.out.printf("[UCPSocket] Assigned name=%s  peer=%s%n", myName, peerName);
    }

    private void sendRaw(byte[] data) throws Exception {        //sends raw bytes directly to the channel
        socket.send(new DatagramPacket(data, data.length, channelAddr, channelPort));
    }

    public void ucp_send(byte[] b, int len) throws Exception {      //wraps given bytes in a UCPPacket and sends it to peer
        String msg = new String(b, 0, len, StandardCharsets.ISO_8859_1);        //ISO-8859-1 is used so arbitrary binary bytes survive the string round-trip
        sendRaw(new UCPPacket(myName, peerName, ProtocolEvent.currentStamp(), msg, false).toBytes());
    }

    /** Block until a data packet arrives. Returns null on END. */
    public byte[] ucp_receive() throws Exception {
        byte[] buf = new byte[BUFFER_SIZE];
        while (true) {
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            socket.receive(dp);
            try {
                UCPPacket pkt = UCPPacket.fromBytes(dp.getData(), dp.getLength());
                ProtocolEvent.observe(pkt.seq);
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
        try {
            sendRaw(UCPPacket.end(myName, peerName).toBytes());
        } finally {
            socket.close();
        }
    }

    public String getMyName()   { return myName; }
    public String getPeerName() { return peerName; }

    public void setTimeout(int timeoutMs) throws SocketException {      //sets a receive timeour in ms, 0 means block indefinitely. Used by RUDPSender to implement ack timeouts
        socket.setSoTimeout(timeoutMs);
    }
}
