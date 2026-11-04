import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Child-process entry point used by the local lab; output paths are chosen locally. */
public final class LabEndpoint {
    public static void main(String[] args) {
        try {
            System.setProperty("packetlab.role", args[0].equals("send") ? "sender" : "receiver");
            int channelPort = Integer.parseInt(args[1]);
            Path file = Path.of(args[2]);
            if (args[0].equals("send")) {
                RUDPSender sender = new RUDPSender(0, "127.0.0.1", channelPort);
                try (UCPSocket ignored = sender.socket) {
                    sender.sendFile(file.toString());
                }
            } else if (args[0].equals("receive")) {
                RUDPReceiver receiver = new RUDPReceiver(0, "127.0.0.1", channelPort);
                try (UCPSocket ignored = receiver.socket;
                     OutputStream output = Files.newOutputStream(file)) {
                    receiver.receiveTo(output);
                    receiver.receiveString(); // Legacy filename stream; never use it as an output path.
                }
            } else {
                throw new IllegalArgumentException("Expected send or receive");
            }
            System.out.println("[COMPLETE]");
        } catch (Exception e) {
            System.err.println("[FAILED] " + e.getMessage());
            System.exit(1);
        }
    }
}
