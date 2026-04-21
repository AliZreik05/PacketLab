import java.util.*;
import java.io.*;
import java.net.*;

class RUDP {
    static final int HEADER_SIZE = 4;
    static final int ACK_TIMEOUT_MS = 500;
    static final int EOF_LINGER_MS = 2000;

    static byte[] intToBytes(int value) {
        return new byte[] {
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }

    static int bytesToInt(byte[] bytes, int start) {
        return ((bytes[start] & 0xff) << 24)
             | ((bytes[start + 1] & 0xff) << 16)
             | ((bytes[start + 2] & 0xff) << 8)
             | (bytes[start + 3] & 0xff);
    }
}

class RUDPSender {
    static final int CHUNK_SIZE = 1024;
    UCPSocket socket;
    int sequenceNumber = 0;
    RUDPSender(int port, String serverHostName, int serverPort) throws Exception {
        socket = new UCPSocket(port, serverHostName, serverPort);
        socket.setTimeout(RUDP.ACK_TIMEOUT_MS);
    }
    void sendFile(String fileName) throws FileNotFoundException, IOException {
        // Read file and send chunks of size up to 1024
        FileInputStream fis = new FileInputStream(fileName);
        byte[] buffer = new byte[CHUNK_SIZE];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            rudp_send(buffer, bytesRead);
        }

        // Indicate EOF
        rudp_send(buffer, 0);

        // Send file name to server
        sendString(fileName);

        // Close streams and sockets
        fis.close();
    }
    
    void sendString(String s) { sendBytes(s.getBytes()); } 

    void sendBytes(byte[] b) {
        // Send bytes in chunks of size at most 1024
        for (int i = 0; i < b.length; i += CHUNK_SIZE) {
            int end = Math.min(b.length, i + CHUNK_SIZE);
            byte[] chunk = Arrays.copyOfRange(b, i, end);
            rudp_send(chunk, chunk.length);
        }

        // Indicate EOF
        rudp_send(null, 0);
    }

    void rudp_send(byte[] bytes, int len) {
        if (len < 0) {
            throw new IllegalArgumentException("Packet length cannot be negative.");
        }
        if (len > 0 && (bytes == null || len > bytes.length)) {
            throw new IllegalArgumentException("Packet length exceeds payload size.");
        }

        byte[] packet = new byte[RUDP.HEADER_SIZE + len];
        byte[] sequenceBytes = RUDP.intToBytes(sequenceNumber);
        System.arraycopy(sequenceBytes, 0, packet, 0, RUDP.HEADER_SIZE);
        if (len > 0) {
            System.arraycopy(bytes, 0, packet, RUDP.HEADER_SIZE, len);
        }

        while (true) {
            try {
                socket.ucp_send(packet, packet.length);

                while (true) {
                    byte[] ack = socket.ucp_receive();
                    if (ack != null
                            && ack.length == RUDP.HEADER_SIZE
                            && RUDP.bytesToInt(ack, 0) == sequenceNumber) {
                        sequenceNumber++;
                        return;
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // Retry the same packet until the matching ACK arrives.
            } catch (Exception e) {
                throw new RuntimeException("Failed to send RUDP packet.", e);
            }
        }
    }

}

class RUDPReceiver {
    static final int INITIAL_SIZE = 1024;
    UCPSocket socket;
    int sequenceNumber = 0;
    byte[] bufferedPacket = null;
    RUDPReceiver(int port, String serverHostName, int serverPort) throws Exception {
        socket = new UCPSocket(port, serverHostName, serverPort);
    }
    String receiveString() { return new String(receiveBytes()); } 

    byte[] receiveBytes() {
        byte[] buffer = new byte[INITIAL_SIZE];
        int len = 0;
        byte[] chunk;
        while((chunk = rudp_receive()) != null) {
            if(len + chunk.length > buffer.length) buffer = doubleSize(buffer);
            insertBytes(buffer, chunk, len);
            len += chunk.length;
        }
        return Arrays.copyOf(buffer, len);
    }

    byte[] rudp_receive() {
        while (true) {
            try {
                byte[] packet = nextPacket();
                if (packet == null) {
                    return null;
                }
                if (packet.length < RUDP.HEADER_SIZE) {
                    continue;
                }

                int receivedSequence = RUDP.bytesToInt(packet, 0);
                if (receivedSequence == sequenceNumber) {
                    sendAck(receivedSequence);
                    sequenceNumber++;

                    if (packet.length == RUDP.HEADER_SIZE) {
                        lingerAfterEof(receivedSequence);
                        return null;
                    }
                    return Arrays.copyOfRange(packet, RUDP.HEADER_SIZE, packet.length);
                }

                if (receivedSequence < sequenceNumber) {
                    sendAck(receivedSequence);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to receive RUDP packet.", e);
            }
        }
    }

    private void sendAck(int ackSequence) throws Exception {
        byte[] ack = RUDP.intToBytes(ackSequence);
        socket.ucp_send(ack, ack.length);
    }

    private byte[] nextPacket() throws Exception {
        if (bufferedPacket != null) {
            byte[] packet = bufferedPacket;
            bufferedPacket = null;
            return packet;
        }
        return socket.ucp_receive();
    }

    private void lingerAfterEof(int eofSequence) throws Exception {
        long deadline = System.currentTimeMillis() + RUDP.EOF_LINGER_MS;
        try {
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return;
                }

                socket.setTimeout((int) remaining);
                byte[] packet = socket.ucp_receive();
                if (packet == null || packet.length < RUDP.HEADER_SIZE) {
                    continue;
                }

                int receivedSequence = RUDP.bytesToInt(packet, 0);
                if (receivedSequence == eofSequence) {
                    sendAck(receivedSequence);
                    deadline = System.currentTimeMillis() + RUDP.EOF_LINGER_MS;
                    continue;
                }

                if (receivedSequence < sequenceNumber) {
                    sendAck(receivedSequence);
                    continue;
                }

                bufferedPacket = packet;
                return;
            }
        } catch (SocketTimeoutException ignored) {
            // No retransmitted EOF arrived during the grace period.
        } finally {
            socket.setTimeout(0);
        }
    }

    private byte[] doubleSize(byte[] b) { return Arrays.copyOf(b, b.length * 2); }
    private void insertBytes(byte[] b, byte[] chunk, int start) { 
        for(int i = 0; i < chunk.length; ++i)
            b[i + start] = chunk[i];
    }
}
