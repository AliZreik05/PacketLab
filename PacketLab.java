import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Desktop controls for real stop-and-wait UDP experiments on localhost. */
public final class PacketLab extends JFrame {
    private final JTextField source = new JTextField();
    private final JComboBox<Experiment> scenario=new JComboBox<>(Experiment.values());
    private int customLoss=10,customMin=10,customMax=80;
    private Experiment previousScenario=Experiment.RANDOM;
    private final JSpinner loss = new JSpinner(new SpinnerNumberModel(10, 0, 100, 5));
    private final JSpinner minDelay = new JSpinner(new SpinnerNumberModel(10, 0, 5000, 10));
    private final JSpinner maxDelay = new JSpinner(new SpinnerNumberModel(80, 0, 5000, 10));
    private final JTextArea log = new JTextArea();
    private final TracePanel trace = new TracePanel();
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
        JPanel page = new JPanel(new BorderLayout(22, 20));
        page.setBorder(new EmptyBorder(24, 26, 18, 26)); setContentPane(page);
        JPanel heading = new JPanel(new BorderLayout());
        JPanel brand = new JPanel(new GridLayout(0,1,0,4));
        brand.add(LabTheme.label("PacketLab", 29, LabTheme.TEXT, true));
        brand.add(LabTheme.label("Explore how reliable delivery survives an unreliable network.", 13, LabTheme.MUTED, false));
        heading.add(brand, BorderLayout.WEST);
        JLabel badge = LabTheme.label("LOCAL UDP  /  STOP-AND-WAIT", 11, LabTheme.ACCENT, true);
        heading.add(badge, BorderLayout.EAST); page.add(heading, BorderLayout.NORTH);

        JPanel sidebar = new JPanel(); sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(270, 500));
        JPanel fileCard = LabTheme.card("01  /  SOURCE FILE");
        JPanel fileControls = new JPanel(new GridLayout(0,1,0,10)); fileControls.setOpaque(false);
        source.setEditable(false); source.setText("");
        source.setToolTipText("The original stays untouched. A received copy is saved under runs/.");
        fileControls.add(source); fileControls.add(browse); fileControls.add(demo);
        fileCard.add(fileControls,BorderLayout.CENTER); fileCard.setMaximumSize(new Dimension(300,205));
        sidebar.add(fileCard); sidebar.add(Box.createVerticalStrut(16));
        JPanel networkCard = LabTheme.card("02  /  NETWORK CONDITIONS");
        JPanel conditions = new JPanel(new GridLayout(0,1,0,12)); conditions.setOpaque(false);
        scenario.setToolTipText("Choose a repeatable demonstration or custom random loss");
        conditions.add(scenario);
        String[] settingNames = {"Packet loss (%)", "Min delay (ms)", "Max delay (ms)"};
        JSpinner[] settings = {loss,minDelay,maxDelay};
        for (int i=0;i<settings.length;i++) {
            JPanel row = new JPanel(new BorderLayout(10,0)); row.setOpaque(false);
            row.add(LabTheme.label(settingNames[i],13,LabTheme.TEXT,false),BorderLayout.WEST);
            settings[i].setPreferredSize(new Dimension(85,36)); row.add(settings[i],BorderLayout.EAST);
            conditions.add(row);
        }
        networkCard.add(conditions,BorderLayout.CENTER); networkCard.setMaximumSize(new Dimension(300,255));
        sidebar.add(networkCard); sidebar.add(Box.createVerticalStrut(16));
        JPanel actions = new JPanel(new GridLayout(0,1,0,10));
        start.putClientProperty("primary", true); actions.add(start); actions.add(cancel); actions.add(open);
        actions.setMaximumSize(new Dimension(300,130)); sidebar.add(actions); sidebar.add(Box.createVerticalGlue());
        sidebar.add(LabTheme.label("1,024-byte payloads  /  SHA-256 verified",11,LabTheme.MUTED,false));
        page.add(sidebar,BorderLayout.WEST);

        JPanel workspace = new JPanel(new BorderLayout(0,14));
        JPanel summary = new JPanel(new BorderLayout());
        summary.setBorder(new EmptyBorder(5,0,8,0));
        JPanel summaryBody = new JPanel(new BorderLayout(0,12)); summaryBody.setOpaque(false);
        metrics.setFont(new Font("Segoe UI",Font.BOLD,15)); metrics.setForeground(LabTheme.TEXT);
        summaryBody.add(metrics,BorderLayout.NORTH);
        progress.setBorderPainted(false); progress.setStringPainted(true); progress.setString("Ready when you are");
        progress.setPreferredSize(new Dimension(600,25)); summaryBody.add(progress,BorderLayout.SOUTH);
        summary.add(summaryBody,BorderLayout.CENTER); workspace.add(summary,BorderLayout.NORTH);
        log.setEditable(false); log.setFont(new Font("Consolas",Font.PLAIN,12));
        log.setBackground(LabTheme.BG); log.setForeground(new Color(180,207,232));
        log.setBorder(new EmptyBorder(14,14,14,14));
        log.setText("Choose a file or use the demo, then start a transfer.\n");
        JTabbedPane views = new JTabbedPane();
        views.addTab("Packet flow", trace); views.addTab("Technical log",new JScrollPane(log));
        workspace.add(views,BorderLayout.CENTER); page.add(workspace,BorderLayout.CENTER);
        status.setForeground(LabTheme.MUTED); status.setFont(new Font("Segoe UI",Font.PLAIN,12));
        page.add(status,BorderLayout.SOUTH);
        cancel.setEnabled(false); open.setEnabled(false);
        LabTheme.style(page);
        scenario.addActionListener(e -> {
            Experiment selected=(Experiment)scenario.getSelectedItem();
            if(previousScenario.custom()) {
                customLoss=(Integer)loss.getValue();customMin=(Integer)minDelay.getValue();customMax=(Integer)maxDelay.getValue();
            }
            loss.setValue(selected.custom() ? customLoss : 0);
            minDelay.setValue(selected.custom() ? customMin : 10);
            maxDelay.setValue(selected.custom() ? customMax : 80);
            loss.setEnabled(selected.custom());minDelay.setEnabled(selected.custom());maxDelay.setEnabled(selected.custom());
            status.setText(selected.description()); previousScenario=selected;
        });
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
        setMinimumSize(new Dimension(1160, 820)); setSize(1050, 880); setLocationRelativeTo(null);
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
            Experiment experiment=(Experiment)scenario.getSelectedItem();
            double probability = ((Integer) loss.getValue()) / 100.0;
            fileSize = Files.size(input); sent = retries = drops = 0;
            log.setText(""); progress.setValue(0); progress.setString("Starting channel...");
            status.setText(experiment.description());
            setRunning(true); received = null; open.setEnabled(false);
            trace.reset();
            new SwingWorker<LabSession.Result, String>() {
                private final LabSession active = new LabSession(line -> publish(line));
                { session = active; }
                @Override protected LabSession.Result doInBackground() throws Exception {
                    return active.transfer(input, Path.of("runs").toAbsolutePath(), probability, low, high, experiment);
                }
                @Override protected void process(List<String> lines) {
                    for (String line : lines) {
                        ProtocolEvent event = ProtocolEvent.parse(line);
                        if (event != null) trace.accept(event);
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
                        trace.finished(true);
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        progress.setString("Not completed"); status.setText(cause.getMessage());
                        log.append("\n" + cause.getMessage() + "\n");
                        trace.finished(false);
                    } finally { session = null; setRunning(false); }
                }
            }.execute();
        } catch (Exception ex) { error(ex); }
    }
    private void setRunning(boolean running) {
        start.setEnabled(!running); cancel.setEnabled(running); browse.setEnabled(!running);
        demo.setEnabled(!running); scenario.setEnabled(!running);
        boolean custom=((Experiment)scenario.getSelectedItem()).custom();
        loss.setEnabled(!running && custom); minDelay.setEnabled(!running && custom); maxDelay.setEnabled(!running && custom);
    }
    private void error(Exception e) { JOptionPane.showMessageDialog(this, e.getMessage(), "PacketLab", JOptionPane.ERROR_MESSAGE); }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LabTheme.install();
            new PacketLab().setVisible(true);
        });
    }
}
