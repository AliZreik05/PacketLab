import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Runs the original protocol in isolated JVMs, so cancellation closes every socket. */
public final class LabSession implements AutoCloseable {
    public record Result(Path file, long bytes, double seconds, String sha256) {}
    private final List<Process> processes = new ArrayList<>();
    private volatile boolean cancelled;
    private final Consumer<String> events;
    public LabSession(Consumer<String> events) { this.events = events; }

    public Result transfer(Path input, Path outputDirectory, double loss, int minDelay, int maxDelay) throws Exception {
        if (!Files.isRegularFile(input)) throw new IOException("Choose an existing file");
        if (!Double.isFinite(loss) || loss < 0 || loss > 1 || minDelay < 0 || maxDelay < minDelay)
            throw new IllegalArgumentException("Invalid loss or delay range");
        Files.createDirectories(outputDirectory);
        Path run = Files.createTempDirectory(outputDirectory, "transfer-");
        Path partial = run.resolve("received.part");
        long started = System.nanoTime();
        int port;
        try (DatagramSocket reservation = new DatagramSocket(0)) { port = reservation.getLocalPort(); }
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Process channel = launch("channel", ready, "UnreliableChannel", "" + port, "" + loss, "" + minDelay, "" + maxDelay);
            if (!ready.await(10, TimeUnit.SECONDS) || !channel.isAlive()) throw new IOException("Channel failed to start");
            checkCancelled();
            Process receiver = launch("receiver", null, "LabEndpoint", "receive", "" + port, partial.toString());
            Process sender = launch("sender", null, "LabEndpoint", "send", "" + port, input.toAbsolutePath().toString());
            // The lab has a fixed experiment budget, separate from the per-packet retry budget.
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
            await(sender, deadline);
            await(receiver, deadline);
            checkCancelled();
            String sourceHash = hash(input);
            String receivedHash = hash(partial);
            if (!sourceHash.equals(receivedHash)) throw new IOException("SHA-256 mismatch: received file differs from source");
            Path result = run.resolve(input.getFileName());
            if (result.equals(partial)) result = run.resolve("received-" + input.getFileName());
            Files.move(partial, result);
            double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
            return new Result(result, Files.size(result), seconds, receivedHash);
        } finally {
            stopProcesses();
            Files.deleteIfExists(partial);
        }
    }

    private synchronized Process launch(String label, CountDownLatch ready, String... arguments) throws IOException {
        if (cancelled) throw new IOException("Transfer cancelled");
        String executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(executable, "-cp", System.getProperty("java.class.path")));
        command.addAll(Arrays.asList(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        processes.add(process);
        Thread reader = new Thread(() -> {
            try (BufferedReader lines = process.inputReader()) {
                String line;
                while ((line = lines.readLine()) != null) {
                    if (ready != null && line.equals("[READY]")) ready.countDown();
                    // The original channel prints binary payloads. Keep the UI log textual and concise.
                    if (!line.startsWith("[CHANNEL] Received") && !line.startsWith("[CHANNEL] Forwarded")
                            && (line.startsWith("[") || line.startsWith("Usage:")))
                        events.accept(label + "  " + line);
                }
            } catch (IOException ignored) { /* Process shutdown closes the pipe. */ }
        }, "packetlab-" + label);
        reader.setDaemon(true);
        reader.start();
        return process;
    }

    private void await(Process process, long deadline) throws Exception {
        while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
            checkCancelled();
            if (System.nanoTime() > deadline) throw new IOException("Experiment exceeded the five-minute limit");
        }
        checkCancelled();
        if (process.exitValue() != 0) throw new IOException("Transfer endpoint failed; see the event log");
    }
    private void checkCancelled() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Transfer cancelled");
    }
    public static String hash(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private synchronized void stopProcesses() {
        for (Process process : processes) if (process.isAlive()) process.destroyForcibly();
        for (Process process : processes) {
            try { process.waitFor(3, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        processes.clear();
    }
    @Override public void close() { cancelled = true; stopProcesses(); }
}
