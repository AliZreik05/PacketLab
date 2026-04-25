import java.util.*;
import java.io.*;
import java.net.*;

public class RUDPDestination {
    public static void main(String[] args) {
        try {
            int receiverPort = Integer.parseInt(args[1]);

            RUDPReceiver receiver = new RUDPReceiver(receiverPort, "localhost", 6789);

            byte[] b = receiver.receiveBytes();
            String fileName = receiver.receiveString();

            FileOutputStream fos = new FileOutputStream(fileName);
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