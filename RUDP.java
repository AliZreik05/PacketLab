import java.util.*;
import java.io.*;
import java.net.*;

class RUDP {
    static final int HEADER_SIZE = 4;   //Size of header containing a 4-byte sequence number
    static final int ACK_TIMEOUT_MS = 500;      //The time that the sender waits for an ack before retransmission
    static final int EOF_LINGER_MS = 2000;      //The time that the receiver stays alive after EOF to re-ack straggled packets

    static byte[] intToBytes(int value) {       //converts an integer to a 4 bytes array where the most significant byte is placed first
        return new byte[] { 
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }

    static int bytesToInt(byte[] bytes, int start) {        //reconstructs the int by reading the 4 bytes after the start
        return ((bytes[start] & 0xff) << 24)                //& 0xff masks the sign bit to elimitate the possibility of corrupt data due to negative bytes
             | ((bytes[start + 1] & 0xff) << 16)
             | ((bytes[start + 2] & 0xff) << 8)
             | (bytes[start + 3] & 0xff);
    }
}

class RUDPSender {          //sends data reliably over an unreliable channel using a stop-and-wait system where each packet gets a sequence number and the sender blocks until it receives a matching ack
    static final int CHUNK_SIZE = 1024;         //max number of bytes per packet
    UCPSocket socket;
    int sequenceNumber = 0;         
    RUDPSender(int port, String serverHostName, int serverPort) throws Exception {
        socket = new UCPSocket(port, serverHostName, serverPort);
        socket.setTimeout(RUDP.ACK_TIMEOUT_MS);         //ucp_receive() throws SocketTimeOutException of no ack is received in time
    }
    void sendFile(String fileName) throws FileNotFoundException, IOException {         // Read file and send chunks of size up to 1024, Receiver needs filename to known where to save the file
        try (FileInputStream fis = new FileInputStream(fileName)) {
        byte[] buffer = new byte[CHUNK_SIZE];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {            // Send file content chunk by chunk
            rudp_send(buffer, bytesRead);
        }

        rudp_send(buffer, 0);       // Indicate EOF

        int dotIndex = fileName.lastIndexOf('.');       // Send file name to server (after appending -copy (e.g. "file.txt" -> "file-copy.txt"))
        if (dotIndex != -1) {
            fileName = fileName.substring(0, dotIndex) + "-copy" + fileName.substring(dotIndex);
        } 
        else {
            fileName += "-copy";
        }
        sendString(fileName);

        } // Close the input even when the transfer fails.
    }
    
    void sendString(String s) { sendBytes(s.getBytes()); } 

    void sendBytes(byte[] b) {          // Send bytes in chunks of size at most 1024 followed by a zero-length EOF packet.
        for (int i = 0; i < b.length; i += CHUNK_SIZE) {
            int end = Math.min(b.length, i + CHUNK_SIZE);
            byte[] chunk = Arrays.copyOfRange(b, i, end);
            rudp_send(chunk, chunk.length);
        }

        rudp_send(null, 0);     // Indicate EOF
    }

    private void rudp_send(byte[] bytes, int len) {         //send logic. Builds a packet (header + payload), sends it, and waits for the matching ack.Retries on timeout up to the per-packet attempt limit.
        if (len < 0) {
            throw new IllegalArgumentException("Packet length cannot be negative.");
        }
        if (len > 0 && (bytes == null || len > bytes.length)) {
            throw new IllegalArgumentException("Packet length exceeds payload size.");
        }

        byte[] packet = new byte[RUDP.HEADER_SIZE + len];       //build the packet
        byte[] sequenceBytes = RUDP.intToBytes(sequenceNumber);
        System.arraycopy(sequenceBytes, 0, packet, 0, RUDP.HEADER_SIZE);
        if (len > 0) {
            System.arraycopy(bytes, 0, packet, RUDP.HEADER_SIZE, len);
        }

        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Transfer cancelled");
                if (attempt > 0) System.out.println("[RETRY] " + sequenceNumber);
ProtocolEvent.emit("SEND", sequenceNumber, len == 0 ? "EOF" : "DATA", 0);
                socket.ucp_send(packet, packet.length);
                System.out.println("[DATA TRANSMISSION]: " + sequenceNumber + " | " + packet.length);
                long deadline = System.nanoTime() + RUDP.ACK_TIMEOUT_MS * 1_000_000L;
                while (true) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) throw new SocketTimeoutException();
                    socket.setTimeout((int) Math.max(1, remaining / 1_000_000));
                    byte[] ack = socket.ucp_receive();
                    if (ack == null) throw new IOException("Peer closed before acknowledgement");
                    if (ack != null && ack.length == RUDP.HEADER_SIZE && RUDP.bytesToInt(ack, 0) == sequenceNumber) {
                        System.out.println("[ACK] " + sequenceNumber);
                        ProtocolEvent.emit("ACK_RECEIVE", sequenceNumber, "ACK", 0);
                        sequenceNumber++;
                        return;
                    }
                }
            } 
            catch (SocketTimeoutException ignored) {        // Retry the same packet within the attempt budget.
                ProtocolEvent.emit("TIMEOUT", sequenceNumber, len == 0 ? "EOF" : "DATA", 0);
            } 
            catch (Exception e) {
                throw new RuntimeException("Failed to send RUDP packet.", e);
            }
        }
        throw new RuntimeException("No acknowledgement after 40 attempts for packet " + sequenceNumber);
    }

}

class RUDPReceiver {        //receives the data send by sender and sends an ack for every in-order packet. Also handles duplicate pcakets by re-acking without dilvery.
    static final int INITIAL_SIZE = 1024;       //initial size of the receiver buffer (doubles when needed)
    UCPSocket socket;
    int sequenceNumber = 0;     //the one we are expecting
    byte[] bufferedPacket = null;           //a buffer for the packet that arrived during EOF but belongs to next stream
    RUDPReceiver(int port, String serverHostName, int serverPort) throws Exception {
        socket = new UCPSocket(port, serverHostName, serverPort);
    }
    String receiveString() { return new String(receiveBytes()); } 

    // Stream file data to disk instead of retaining the entire file in memory.
    long receiveTo(OutputStream output) throws IOException {
        long total = 0;
        byte[] chunk;
        while ((chunk = rudp_receive()) != null) {
            output.write(chunk);
            total += chunk.length;
            System.out.println("[PROGRESS] " + total);
        }
        return total;
    }

    byte[] receiveBytes() {            // Keep receiving chunks until an EOF is received, uses a buffer that doubles in size when it fills up
        byte[] buffer = new byte[INITIAL_SIZE];
        int len = 0;
        byte[] chunk;
        while((chunk = rudp_receive()) != null) {
            if(len + chunk.length > buffer.length) buffer = doubleSize(buffer);
            insertBytes(buffer, chunk, len);
            len += chunk.length;
        }
        return Arrays.copyOf(buffer, len);          // Trim to actual length
    }

    private byte[] rudp_receive() {         //receives and returns next in-order packet or null. on EOF, out-of-order packets are discarded and dupes are re-acked
        while (true) {
            try {
                byte[] packet = nextPacket();       //check buffer before socket
                if (packet == null) {
                    throw new IOException("Peer closed before end of stream");
                }
                if (packet.length < RUDP.HEADER_SIZE) {         //if packet is too short, skip it
                    continue;
                }

                int receivedSequence = RUDP.bytesToInt(packet, 0);
                System.out.println("[DATA RECEPTION]: " + sequenceNumber + " | " + packet.length + " | " + (receivedSequence == sequenceNumber ? "OK" : "DISCARDED"));
                if (receivedSequence == sequenceNumber) {
                    ProtocolEvent.emit("RECEIVE", receivedSequence, packet.length == 4 ? "EOF" : "DATA", 0);
                    sendAck(receivedSequence);
                    sequenceNumber++;

                    if (packet.length == RUDP.HEADER_SIZE) {        //packet with only header and no payload which signifies EOF
                        lingerAfterEof(receivedSequence);       //stay alive to re-ack retrasnmitted EOF
                        return null;
                    }
                    return Arrays.copyOfRange(packet, RUDP.HEADER_SIZE, packet.length);
                }

                if (receivedSequence < sequenceNumber) {        //if this packet was already acked, send it again so the sender can move on
                    ProtocolEvent.emit("DUPLICATE", receivedSequence, packet.length == 4 ? "EOF" : "DATA", 0);
                    sendAck(receivedSequence);
                }
            } 
            //out of order packets are dropped silently
            catch (Exception e) {           
                throw new RuntimeException("Failed to receive RUDP packet.", e);
            }
        }
    }

    private void sendAck(int ackSequence) throws Exception {        // Sends a 4-byte ACK containing the sequence number being acknowledged
        byte[] ack = RUDP.intToBytes(ackSequence);
        ProtocolEvent.emit("ACK_SEND", ackSequence, "ACK", 0);
        socket.ucp_send(ack, ack.length);
    }

    private byte[] nextPacket() throws Exception {          //returns the buffered packet (the one saved during linger, if exists), otherwise reads from socket
        if (bufferedPacket != null) {
            byte[] packet = bufferedPacket;
            bufferedPacket = null;
            return packet;
        }
        return socket.ucp_receive();
    }

    private void lingerAfterEof(int eofSequence) throws Exception {         //After receiving an EOF, we wait up to EOF_LINGER_MS for any retransmitted copies of it. This covers the case where the sender never received our ACK and keeps resending. If the next expected packet arrives during this window, we buffer it and exit early.
        long deadline = System.currentTimeMillis() + RUDP.EOF_LINGER_MS;
        try {
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {       //grace period expired
                    return;
                }

                socket.setTimeout((int) remaining);     //shrink timeout as deadline approaches
                byte[] packet = socket.ucp_receive();
                if (packet == null || packet.length < RUDP.HEADER_SIZE) {
                    continue;
                }

                int receivedSequence = RUDP.bytesToInt(packet, 0);
                if (receivedSequence == eofSequence) {       // Sender retransmitted the EOF — re-ACK and reset the deadline
                    ProtocolEvent.emit("DUPLICATE", receivedSequence, "EOF", 0);
                    sendAck(receivedSequence);
                    deadline = System.currentTimeMillis() + RUDP.EOF_LINGER_MS;
                    continue;
                }

                if (receivedSequence < sequenceNumber) {        // Old duplicate from earlier in the stream, just re-ACK it
                    ProtocolEvent.emit("DUPLICATE", receivedSequence, packet.length == 4 ? "EOF" : "DATA", 0);
                    sendAck(receivedSequence);
                    continue;
                }

                bufferedPacket = packet;
                return;
            }
        } 
        catch (SocketTimeoutException ignored) {
            // No retransmitted EOF arrived during the grace period.
        } 
        finally {
            socket.setTimeout(30000);   // Bound waits when the sender disappears.
        }
    }

    private byte[] doubleSize(byte[] b) { return Arrays.copyOf(b, b.length * 2); }          // Doubles the capacity of a byte buffer while preserving its contents
    private void insertBytes(byte[] b, byte[] chunk, int start) { 
        for(int i = 0; i < chunk.length; ++i)
            b[i + start] = chunk[i];
    }
}
