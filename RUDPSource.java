import java.util.*;
import java.io.*;
import java.net.*;

public class RUDPSource {       // Entry point for the sender side. Parses args, creates an RUDPSender, and kicks off the transfer.
    public static void main(String[] args) {
        try {
            String[] address = args[1].split(":");          // Extract server address and file name (split on the ":" in the input (eg. <host>:<port>))
            String receiverHostName = address[0];
            int receiverPort = Integer.parseInt(address[1]);

            String fileName = args[3];

            // Create RDTSender by picking a local port that does not clash with the destination.
            int clientPort = (receiverHostName.equals("localhost") || receiverHostName.equals("127.0.0.1")) && receiverPort == 8000 ? 8001 : 8000; // Making sure to avoid using server's port
            
            RUDPSender sender = new RUDPSender(clientPort, "localhost", 6789);      //sender always talks through the channel at port 6789, not directly to destination

            sender.sendFile(fileName);
            System.out.println("[COMPLETE]");
        }
        catch(Exception e) {
            System.err.println("Usage: java RUDPSource -r <recvHost>:<recvPort> -f <fileName>");
            System.err.println("\nError:");
            System.err.println(e);
        }
    }
}