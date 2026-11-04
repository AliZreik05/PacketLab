import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Desktop controls for real stop-and-wait UDP experiments on localhost. */
public final class PacketLab extends JFrame {
    private final JTextField source = new JTextField();
    private final JSpinner loss = new JSpinner(new SpinnerNumberModel(10, 0, 100, 5));
    private final JSpinner minDelay = new JSpinner(new SpinnerNumberModel(10, 0, 5000, 10));
    private final JSpinner maxDelay = new JSpinner(new SpinnerNumberModel(80, 0, 5000, 10));
    private final JTextArea log = new JTextArea();
    private final JLabel status = new JLabel("Ready to experiment");
    private final JLabel metrics = new JLabel("Packets sent  0       Retries  0       Drops  0");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton start = new JButton("Start transfer"), cancel = new JButton("Cancel");
    private final JButton browse = new JButton("Choose file"), demo = new JButton("Use demo file");
    private final JButton open = new JButton("Open received folder");
    private LabSession session;
    private Path received;
    private long fileSize;
    private int sent, retries, drops;

    public PacketLab() {
        super("PacketLab | Reliable transfers, unreliable networks");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (session != null) new Thread(session::close, "packetlab-cleanup").start();
                dispose();
            }
        });
        JPanel page = new JPanel(new BorderLayout(0, 20));
        page.setBorder(new EmptyBorder(26, 30, 26, 30));
        setContentPane(page);
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 6));
        JLabel title = new JLabel("PacketLab");
        title.setFont(new Font("SansSerif", Font.BOLD, 32));
        heading.add(title);
        heading.add(new JLabel("Drop packets. Add delay. Watch reliability happen."));
        heading.add(new JLabel("LOCAL UDP LAB   /   Stop-and-wait   /   1,024-byte payloads"));
        page.add(heading, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 16));
        JPanel controls = new JPanel(new GridLayout(0, 1, 0, 12));
        JPanel fileRow = new JPanel(new BorderLayout(8, 0));
        source.setEditable(false);
        source.setToolTipText("Choose the file to transfer through the simulated unreliable channel");
        fileRow.add(source, BorderLayout.CENTER);
        JPanel files = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        files.add(browse); files.add(demo); fileRow.add(files, BorderLayout.EAST);
        controls.add(fileRow);
        JPanel conditions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        conditions.add(new JLabel("Packet loss (%)")); conditions.add(loss);
        conditions.add(new JLabel("Delay min (ms)")); conditions.add(minDelay);
        conditions.add(new JLabel("max (ms)")); conditions.add(maxDelay);
        controls.add(conditions);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(start); actions.add(cancel); actions.add(open);
        cancel.setEnabled(false); open.setEnabled(false);
        controls.add(actions);
        progress.setStringPainted(true); progress.setString("No transfer running");
        controls.add(progress); controls.add(metrics);
        body.add(controls, BorderLayout.NORTH);
        log.setEditable(false);
        log.setFont(new Font("Monospaced", Font.PLAIN, 12));
        log.setBackground(new Color(20, 28, 42)); log.setForeground(new Color(190, 221, 235));
        log.setBorder(new EmptyBorder(12, 12, 12, 12));
        log.setText("Your experiment log will appear here.\nA successful run requires matching SHA-256 file hashes.\n");
        body.add(new JScrollPane(log), BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        page.add(status, BorderLayout.SOUTH);
        browse.addActionListener(e -> choose());
        demo.addActionListener(e -> demo());
        start.addActionListener(e -> start());
        cancel.addActionListener(e -> {
            cancel.setEnabled(false); status.setText("Cancelling and closing sockets...");
            LabSession active = session;
            if (active != null) new Thread(active::close, "packetlab-cancel").start();
        });
        open.addActionListener(e -> {
            try { Desktop.getDesktop().open(received.getParent().toFile()); }
            catch (Exception ex) { error(ex); }
        });
        setMinimumSize(new Dimension(820, 600)); setSize(980, 740); setLocationRelativeTo(null);
    }

    private void choose() {
        JFileChooser picker = new JFileChooser();
        if (picker.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            source.setText(picker.getSelectedFile().getAbsolutePath());
    }
    private void demo() {
        try {
            Path directory = Path.of("runs").toAbsolutePath(); Files.createDirectories(directory);
            Path file = Files.createTempFile(directory, "demo-", ".txt");
            Files.writeString(file, "PacketLab: reliable communication over an unreliable channel.\n".repeat(100));
            source.setText(file.toString());
        } catch (Exception ex) { error(ex); }
    }
    private void start() {
        try {
            loss.commitEdit(); minDelay.commitEdit(); maxDelay.commitEdit();
            Path input = Path.of(source.getText());
            if (!Files.isRegularFile(input)) throw new IllegalArgumentException("Choose a file first");
            int low = (Integer) minDelay.getValue(), high = (Integer) maxDelay.getValue();
            if (low > high) throw new IllegalArgumentException("Minimum delay must not exceed maximum delay");
            double probability = ((Integer) loss.getValue()) / 100.0;
            fileSize = Files.size(input); sent = retries = drops = 0;
            log.setText(""); progress.setValue(0); progress.setString("Starting channel...");
            status.setText("Running on localhost. Loss applies to data and acknowledgements.");
            setRunning(true); received = null; open.setEnabled(false);
            new SwingWorker<LabSession.Result, String>() {
                private final LabSession active = new LabSession(line -> publish(line));
                { session = active; }
                @Override protected LabSession.Result doInBackground() throws Exception {
                    return active.transfer(input, Path.of("runs").toAbsolutePath(), probability, low, high);
                }
                @Override protected void process(List<String> lines) {
                    for (String line : lines) {
                        if (line.contains("[DATA TRANSMISSION]")) sent++;
                        if (line.contains("[RETRY]")) retries++;
                        if (line.contains("[CHANNEL] DROPPED")) drops++;
                        if (line.startsWith("receiver  [PROGRESS] ")) {
                            long bytes = Long.parseLong(line.substring(line.lastIndexOf(' ') + 1));
                            progress.setValue(fileSize == 0 ? 0 : (int) Math.min(99, bytes * 100.0 / fileSize));
                            progress.setString(bytes + " / " + fileSize + " bytes received; verification pending");
                        }
                        log.append(line + "\n");
                    }
                    if (log.getDocument().getLength() > 60000) log.replaceRange("", 0, 30000);
                    log.setCaretPosition(log.getDocument().getLength());
                    metrics.setText("Packets sent  " + sent + "       Retries  " + retries + "       Drops  " + drops);
                }
                @Override protected void done() {
                    try {
                        LabSession.Result result = get(); received = result.file();
                        progress.setValue(100); progress.setString("Verified: SHA-256 hashes match");
                        status.setText(String.format("Verified %,d bytes in %.2f s  |  %.1f KiB/s (whole run)",
                                result.bytes(), result.seconds(), result.bytes() / 1024.0 / result.seconds()));
                        log.append("\nSHA-256: " + result.sha256() + "\nSaved: " + received + "\n");
                        open.setEnabled(true);
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        progress.setString("Not completed"); status.setText(cause.getMessage());
                        log.append("\n" + cause.getMessage() + "\n");
                    } finally { session = null; setRunning(false); }
                }
            }.execute();
        } catch (Exception ex) { error(ex); }
    }
    private void setRunning(boolean running) {
        start.setEnabled(!running); cancel.setEnabled(running); browse.setEnabled(!running);
        demo.setEnabled(!running); loss.setEnabled(!running); minDelay.setEnabled(!running); maxDelay.setEnabled(!running);
    }
    private void error(Exception e) { JOptionPane.showMessageDialog(this, e.getMessage(), "PacketLab", JOptionPane.ERROR_MESSAGE); }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new PacketLab().setVisible(true);
        });
    }
}
