import java.util.*;
import java.io.*;
import java.net.*;

class RUDPSender {
    static final int CHUNK_SIZE = 1024;
    UCPClient socket;
    int sequenceNumber = 0;
    RUDPSender(int port, String serverHostName, int serverPort) {
        socket = new UCPClient(port, serverHostName, serverPort);
    }
    void sendFile(String fileName, String receiverHostName, int receiverPort) throws FileNotFoundException, IOException {
        // Read file and send chunks of size up to 1024
        FileInputStream fis = new FileInputStream(fileName);
        byte[] buffer = new byte[CHUNK_SIZE + 4];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            rudp_send(buffer, bytesRead, receiverHostName, receiverPort);
        }

        // Indicate EOF
        rudp_send(buffer, 0, receiverHostName, receiverPort);

        // Send file name to server
        sendString(fileName, receiverHostName, receiverPort);

        // Close streams and sockets
        fis.close();
    }
    
    void sendString(String s, String receiverHostName, int receiverPort) { sendBytes(s.getBytes(), receiverHostName, receiverPort); } 

    void sendBytes(byte[] b, String receiverHostName, int receiverPort) {
        // Send bytes in chunks of size at most 1024
        for (int i = 0; i < b.length; i += CHUNK_SIZE) {
            int end = Math.min(b.length, i + CHUNK_SIZE);
            byte[] chunk = Arrays.copyOfRange(b, i, end);
            rudp_send(chunk, chunk.length, receiverHostName, receiverPort);
        }

        // Indicate EOF
        rudp_send(null, 0, receiverHostName, receiverPort);
    }

    void rudp_send(byte[] bytes, int len, String receiverHostName, int receiverPort) {
        // TO-DO
        // if bytes is null and len is 0, this is an EOF 
    }

}

class RUDPReceiver {
    static final int INITIAL_SIZE = 1024;
    UCPClient socket;
    int sequenceNumber = 0;
    RUDPReceiver(int port, String serverHostName, int serverPort) {
        socket = new UCPClient(port, serverHostName, serverPort);
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
        // TO-DO
        // return 0 when receiving an EOF
    }

    private byte[] doubleSize(byte[] b) { return Arrays.copyOf(b, b.length * 2); }
    private void insertBytes(byte[] b, byte[] chunk, int start) { 
        for(int i = 0; i < chunk.length; ++i)
            b[i + start] = chunk[i];
    }
}
