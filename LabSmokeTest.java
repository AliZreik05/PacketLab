import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Integration checks run real UDP sockets and child JVMs, without test dependencies. */
public final class LabSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("runs"), "tests-");
        verify(root, "empty.bin", new byte[0], 0, 0, 0);
        byte[] binary = new byte[8193];
        new Random(42).nextBytes(binary);
        verify(root, "binary with spaces.bin", binary, 0, 0, 0);
        verify(root, "lossy.bin", binary, .2, 0, 30);
        Path input = root.resolve("cancel.bin"); Files.write(input, binary);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch sent = new CountDownLatch(1);
        try (LabSession session = new LabSession(line -> { if (line.contains("[DATA TRANSMISSION]")) sent.countDown(); })) {
            Future<?> future = executor.submit(() -> {
                try {
                    session.transfer(input, root, 1, 0, 0);
                    throw new AssertionError("100% loss must not succeed");
                } catch (Exception expected) {
                    if (!expected.getMessage().contains("cancelled")) throw new RuntimeException(expected);
                }
            });
            if (!sent.await(15, TimeUnit.SECONDS)) throw new AssertionError("Transfer did not start");
            session.close(); future.get(10, TimeUnit.SECONDS);
            System.out.println("PASS cancellation during 100% loss");
        } finally { executor.shutdownNow(); }
        try (LabSession session = new LabSession(line -> {})) {
            try {
                session.transfer(input, root, 1, 0, 0);
                throw new AssertionError("100% loss must exhaust retries");
            } catch (java.io.IOException expected) {
                if (!expected.getMessage().contains("endpoint failed")) throw expected;
                System.out.println("PASS bounded retry failure during 100% loss");
            }
        }
        try (var paths = Files.walk(root)) {
            if (paths.anyMatch(path -> path.getFileName().toString().equals("received.part")))
                throw new AssertionError("Partial files remain after cleanup");
        }
        System.out.println("ALL CHECKS PASSED");
    }
    private static void verify(Path root, String name, byte[] data, double loss, int low, int high) throws Exception {
        Path input = root.resolve(name); Files.write(input, data);
        List<String> events = new CopyOnWriteArrayList<>();
        try (LabSession session = new LabSession(events::add)) {
            LabSession.Result result;
            try { result = session.transfer(input, root, loss, low, high); }
            catch (Exception e) { events.forEach(System.err::println); throw e; }
            if (!Arrays.equals(data, Files.readAllBytes(result.file()))) throw new AssertionError(name);
            List<ProtocolEvent> trace = events.stream().map(ProtocolEvent::parse).filter(Objects::nonNull).toList();
            for (String action : List.of("SEND", "DELAY", "FORWARD", "RECEIVE", "ACK_SEND", "ACK_RECEIVE")) {
                if (trace.stream().noneMatch(event -> event.action().equals(action)))
                    throw new AssertionError("Missing visualization event: " + action);
            }
            if(loss == 0) {
                List<ProtocolEvent> ordered = trace.stream().sorted(Comparator.comparingLong(ProtocolEvent::occurredAt)).toList();
                for(String kind : List.of(data.length==0 ? "EOF" : "DATA","ACK")) {
                    String[] actions = kind.equals("ACK") ? new String[]{"ACK_SEND","DELAY","FORWARD","ACK_RECEIVE"}
                            : new String[]{"SEND","DELAY","FORWARD","RECEIVE"};
                    int previous=-1;
                    for(String action:actions) {
                        int found=-1;
                        for(int i=0;i<ordered.size();i++) {
                            ProtocolEvent event=ordered.get(i);
                            if(event.sequence()==0 && event.kind().equals(kind) && event.action().equals(action)) {found=i;break;}
                        }
                        if(found<=previous) { ordered.stream().filter(e->e.sequence()==0).forEach(System.err::println); throw new AssertionError("Noncausal event order: "+kind+" "+action); }
                        previous=found;
                    }
                }
            }
            String firstKind = data.length == 0 ? "EOF" : "DATA";
            if (trace.stream().noneMatch(event -> event.action().equals("DELAY") && event.sequence() == 0 && event.kind().equals(firstKind))
                    || trace.stream().noneMatch(event -> event.action().equals("DELAY") && event.sequence() == 0 && event.kind().equals("ACK")))
                throw new AssertionError("Channel must distinguish data/EOF from ACKs regardless of registration order");
            System.out.println("PASS " + name + " (" + result.bytes() + " bytes)");
        }
    }
}
