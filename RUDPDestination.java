import java.util.*;
import java.io.*;
import java.net.*;

public class RUDPDestination {          // Entry point for the receiver side. Waits for a file transfer and saves the received file.
    public static void main(String[] args) {    
        try {
            int receiverPort = Integer.parseInt(args[1]);

            RUDPReceiver receiver = new RUDPReceiver(receiverPort, "localhost", 6789);      //connects to channel at port 6789, not directly to host

            byte[] b = receiver.receiveBytes();     //sender sends file content first
            String fileName = receiver.receiveString();     //sender then sends the file name as a separate stream

            FileOutputStream fos = new FileOutputStream(fileName);      //write the received bytes to disk under the given filename
            fos.write(b);
            fos.flush();
            fos.close();

            System.out.println("[COMPLETE]");
        }
        catch(Exception e) {
            System.err.println("Usage: java RUDPDestination -p <recvPort>");
            System.err.println("\nError:");
            System.err.println(e);
        }
    }
}