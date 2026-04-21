import java.util.*;
import java.io.*;
import java.net.*;

public class RUDPSource {
    public static final int MAX_CHUNK_SIZE = 1024;
    public static void main(String[] args) {
        try {
            // Extract server address and file name
            String[] address = args[1].split(":");
            String receiverHostName = address[0];
            int receiverPort = Integer.parseInt(address[1]);

            String fileName = args[3];

            // Create RDTSender
            int clientPort = (receiverHostName.equals("localhost") || receiverHostName.equals("127.0.0.1")) && receiverPort == 8000 ? 8001 : 8000; // Making sure to avoid using server's port
            RUDPSender sender = new RUDPSender(clientPort, "localhost", 6789);

            sender.sendFile(fileName, receiverHostName, receiverPort);
        }
        catch(Exception e) {
            System.err.println("Usage: java RUDPSource -r <recvHost>:<recvPort> -f <fileName>");
            System.err.println("\n Error:");
            System.err.println(e);
        }
    }
}