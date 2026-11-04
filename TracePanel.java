import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Paced playback of real protocol observations, independent of network timers. */
public final class TracePanel extends JPanel {
    private static final int LIMIT = 10000;
    private static final Color INK = new Color(221, 232, 244), MUTED = new Color(145, 165, 190);
    private static final Color BLUE = new Color(104, 184, 255), GREEN = new Color(89, 218, 169), RED = new Color(255, 117, 127);
    private final List<ProtocolEvent> trace = new ArrayList<>();
    private final List<Long> arrivals = new ArrayList<>();
    private final DefaultListModel<String> rows = new DefaultListModel<>();
    private final JList<String> history = new JList<>(rows);
    private final JTextArea explanation = new JTextArea(2, 45);
    private final JLabel mode = new JLabel("READY  |  Choose a file and start a transfer");
    private final JButton play = new JButton("Pause view"), previous = new JButton("Previous"), next = new JButton("Next step");
    private final JButton restart = new JButton("Replay from start"), latest = new JButton("Latest event");
    private final List<JButton> speedButtons = new ArrayList<>();
    private final Diagram canvas = new Diagram();
    private final Timer timer;
    private final java.util.function.LongSupplier clock;
    private int cursor = -1;
    private long stepStarted;
    private double frozenFraction = 1;
    private double playbackRate = 1;
    private Marker dataMarker, ackMarker;
    private record Marker(ProtocolEvent event, int index) {}
    private boolean running, completed, success, truncated, paused, selecting;

    public TracePanel() { this(System::nanoTime); }
    TracePanel(java.util.function.LongSupplier clock) {
        super(new BorderLayout(0, 10));
        this.clock=clock;
        setBorder(new EmptyBorder(16, 0, 0, 0));
        mode.setFont(new Font("SansSerif", Font.BOLD, 12));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
        double[] rates = {.5,1,2,4};
        String[] labels = {"0.5x","1x","2x","4x"};
        for(int i=0;i<rates.length;i++) {
            double rate=rates[i]; JButton button=new JButton(labels[i]);
            button.setToolTipText("Play the recorded animation at " + labels[i] + "; the network transfer is unaffected");
            button.putClientProperty("primary",rate==playbackRate);
            button.getAccessibleContext().setAccessibleDescription(rate==playbackRate ? "Selected playback speed" : "Playback speed " + labels[i]);
            button.addActionListener(e -> changeSpeed(rate)); speedButtons.add(button);
        }

        toolbar.add(play); toolbar.add(previous); toolbar.add(next);
        toolbar.add(LabTheme.label("Speed",11,LabTheme.MUTED,false));
        for(JButton speedButton:speedButtons) toolbar.add(speedButton);
        JButton toggleHistory=new JButton("History"); toolbar.add(toggleHistory);
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.add(mode, BorderLayout.NORTH); top.add(toolbar, BorderLayout.CENTER); add(top, BorderLayout.NORTH);

        history.setFont(new Font("Monospaced", Font.PLAIN, 12));
        history.setFixedCellHeight(38); history.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        history.setBackground(new Color(24, 34, 49)); history.setForeground(INK);
        history.setSelectionBackground(new Color(47, 75, 106)); history.setSelectionForeground(Color.WHITE);
        JScrollPane scroll = new JScrollPane(history); scroll.setMinimumSize(new Dimension(210, 100));
        JPanel events = new JPanel(new BorderLayout(0, 6));
        JPanel historyActions=new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
        historyActions.add(restart); historyActions.add(latest);
        events.add(historyActions,BorderLayout.NORTH);
        events.add(scroll, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, events);
        split.setResizeWeight(.72); split.setBorder(null); split.setDividerSize(9); split.setContinuousLayout(true);
        JPanel historyHost=new JPanel(new BorderLayout());
        historyHost.add(events,BorderLayout.CENTER); historyHost.setVisible(false);
        split.setRightComponent(historyHost); split.setDividerSize(0);
        toggleHistory.setToolTipText("Show event history and replay shortcuts");
        toggleHistory.addActionListener(e -> {
            boolean show=!historyHost.isVisible(); historyHost.setVisible(show);
            split.setDividerSize(show ? 9 : 0);
            if(show) split.setDividerLocation(.70);
            toggleHistory.setText(show ? "Hide history" : "History");
            split.revalidate();
        });
        add(split,BorderLayout.CENTER);
        explanation.setEditable(false); explanation.setLineWrap(true); explanation.setWrapStyleWord(true);
        explanation.setFont(new Font("SansSerif", Font.PLAIN, 14)); explanation.setBackground(LabTheme.SURFACE); explanation.setForeground(LabTheme.TEXT);
        explanation.setBorder(new EmptyBorder(12,14,12,14));
        canvas.setToolTipText("Blue: data. Green: ACK. Amber: delay. Red: drop/timeout. Dimmed packets are previous state.");
        explanation.setText("Start a transfer to record real UDP events. Pause the view or select a step to inspect it. The transfer itself keeps running.");
        JPanel bottom = new JPanel(new BorderLayout(0, 5)); bottom.add(explanation, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        play.addActionListener(e -> {
            if (paused) {
                paused = false;
                stepStarted = clock.getAsLong() - (long) (frozenFraction * duration());
            } else {
                frozenFraction = fraction();
                paused = true;
            }
            refresh();
        });
        previous.addActionListener(e -> inspect(cursor - 1));
        next.addActionListener(e -> inspect(cursor + 1));
        restart.addActionListener(e -> inspect(0));
        latest.addActionListener(e -> inspect(trace.size() - 1));
        history.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !selecting && history.getSelectedIndex() >= 0) inspect(history.getSelectedIndex());
        });
        timer = new Timer(16, e -> tick());
        history.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                JLabel row = (JLabel) super.getListCellRendererComponent(list,value,index,selected,focus);
                row.setBorder(new EmptyBorder(4,10,4,10)); row.setFont(new Font("Segoe UI",Font.PLAIN,12)); return row;
            }
        }); refresh();
    }
    @Override public void addNotify() { super.addNotify(); timer.start(); }
    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }
    public void reset() {
        trace.clear(); arrivals.clear(); rows.clear(); cursor = -1; paused = false; running = true;
        dataMarker = ackMarker = null; frozenFraction = 1;
        completed = success = truncated = false;
        explanation.setText("Starting the channel and connecting the two endpoints..."); refresh();
    }
    public void accept(ProtocolEvent event) {
        if (trace.size() >= LIMIT) { truncated = true; refresh(); return; }
        int index = trace.size();
        if (event.occurredAt() > 0) {
            while (index > 0 && trace.get(index-1).occurredAt() > event.occurredAt()) index--;
        }
        selecting = true;
        trace.add(index,event); arrivals.add(index,clock.getAsLong());
        rows.add(index,"");
        for(int i=index;i<trace.size();i++) {
            ProtocolEvent item = trace.get(i);
            rows.set(i,String.format("%04d  %-11s %s",i+1,title(item),item.label()));
        }
        if (cursor >= 0 && index <= cursor) cursor++;
        if (cursor >= 0) {
            history.setSelectedIndex(cursor);
            dataMarker=markerAt(trace,cursor,false); ackMarker=markerAt(trace,cursor,true);
        }
        selecting = false;
        // Briefly collect cross-process output before displaying its timestamp order.
        if (cursor == -1 && event.occurredAt() == 0) select(0);
        refresh();
    }
    public void finished(boolean verified) {
        running = false; completed = true; success = verified; refresh();
    }
    private void inspect(int index) {
        if (index < 0 || index >= trace.size()) return;
        paused = true; frozenFraction = 0; select(index); refresh();
    }
    private void select(int index) {
        cursor = index; stepStarted = clock.getAsLong();
        dataMarker = markerAt(trace, index, false); ackMarker = markerAt(trace, index, true);
        explanation.setText(trace.get(index).explanation());
        selecting = true;
        history.setSelectedIndex(index); history.ensureIndexIsVisible(index);
        selecting = false;
        canvas.repaint();
    }
    private void changeSpeed(double rate) {
        double progress = fraction();
        playbackRate = rate;
        frozenFraction = progress;
        stepStarted = clock.getAsLong() - (long) (progress * duration());
        double[] rates = {.5,1,2,4};
        for(int i=0;i<speedButtons.size();i++) {
            JButton button=speedButtons.get(i); boolean selected=rates[i]==rate;
            button.putClientProperty("primary",selected);
            button.setForeground(selected ? LabTheme.BG : LabTheme.TEXT);
            button.getAccessibleContext().setAccessibleDescription(selected ? "Selected playback speed" : "Playback speed " + button.getText());
            button.repaint();
        }
        canvas.repaint();
    }
    private double duration() { return 950_000_000.0 / playbackRate; }
    private double fraction() { return paused ? frozenFraction : Math.min(1, (clock.getAsLong() - stepStarted) / duration()); }
    void tick() {
        if (cursor < 0 && !trace.isEmpty() && ready(0)) { select(0); refresh(); }
        if (!paused && cursor >= 0 && fraction() >= 1 && cursor + 1 < trace.size() && ready(cursor+1)) {
            select(cursor + 1); refresh();
        }
        if (cursor >= 0 && !paused) canvas.repaint();
    }
    double animationFraction() {
        // Use the last 20% of each event to show the packet at its destination.
        return Math.min(1,fraction()/.8);
    }
    private boolean ready(int index) {
        return !running || clock.getAsLong()-arrivals.get(index) >= 200_000_000L;
    }
    private void refresh() {
        String transfer = running ? "Running" : completed ? success ? "Verified" : "Stopped" : "Ready";
        int queued = Math.max(0, trace.size() - cursor - 1);
        mode.setText(transfer + "  |  " + (paused ? "Paused" : queued > 0 ? "Playing" : "Caught up")
                + "  /  Step " + (cursor + 1) + " / " + trace.size() + (queued > 0 ? "  |  " + queued + " pending" : "")
                + (truncated ? "  |  First 10,000 only" : ""));
        play.setText(paused ? "Play view" : "Pause view"); play.setEnabled(!trace.isEmpty());
        previous.setEnabled(cursor > 0); next.setEnabled(cursor >= 0 && cursor + 1 < trace.size());
        restart.setEnabled(!trace.isEmpty()); latest.setEnabled(!trace.isEmpty()); canvas.repaint();
    }
    static String title(ProtocolEvent e) {
        return switch (e.action()) {
            case "SEND" -> "Send"; case "ACK_SEND" -> "Send ACK"; case "DELAY" -> "Delay";
            case "FORWARD" -> "Forward"; case "DROP" -> "Dropped"; case "TIMEOUT" -> "Timeout";
            case "RECEIVE" -> "Accepted"; case "DUPLICATE" -> "Duplicate"; case "ACK_RECEIVE" -> "ACK arrived";
            default -> e.action();
        };
    }
    /** A single event has one visual location; reception/ACK markers never create extra flights. */
    static int[] route(ProtocolEvent e) {
        boolean ack = e.kind().equals("ACK");
        int source = ack ? 2 : 0, destination = ack ? 0 : 2;
        return switch (e.action()) {
            case "SEND", "ACK_SEND" -> new int[]{source, 1};
            case "FORWARD" -> new int[]{1, destination};
            case "DROP", "DELAY" -> new int[]{1, 1};
            case "RECEIVE", "DUPLICATE", "ACK_RECEIVE" -> new int[]{destination, destination};
            case "TIMEOUT" -> new int[]{0, 0};
            default -> new int[]{1, 1};
        };
    }
    private static Marker markerAt(List<ProtocolEvent> events, int index, boolean ack) {
        for (int i=index; i>=0; i--) {
            ProtocolEvent event = events.get(i);
            if (!event.action().equals("TIMEOUT") && event.kind().equals("ACK") == ack) return new Marker(event,i);
        }
        return null;
    }
    static Color stateColor(ProtocolEvent event) {
        return switch(event.action()) {
            case "DELAY" -> new Color(246,190,91);
            case "DROP", "TIMEOUT" -> RED;
            case "DUPLICATE" -> new Color(189,160,255);
            default -> event.kind().equals("ACK") ? GREEN : BLUE;
        };
    }
    static String stateLabel(ProtocolEvent event) {
        return switch(event.action()) {
            case "DELAY" -> "WAITING IN CHANNEL - " + event.delay() + " ms";
            case "DROP" -> "DROPPED - this copy stops here";
            case "TIMEOUT" -> "TIMEOUT - sender will retry";
            case "DUPLICATE" -> "DUPLICATE - acknowledged again";
            case "RECEIVE" -> "ACCEPTED BY RECEIVER";
            case "ACK_RECEIVE" -> "ACK ARRIVED AT SENDER";
            default -> "IN TRANSIT";
        };
    }
    private final class Diagram extends JPanel {
        Diagram() {
            setPreferredSize(new Dimension(690, 300)); setMinimumSize(new Dimension(480, 275));
            setBackground(new Color(15, 24, 38));
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Preserve readable spacing on the logical canvas; shrink it as one unit only
            // when the allocated pane is too small to contain the lanes and their captions.
            int w = Math.max(480,getWidth()), h = Math.max(250,getHeight());
            double scale=Math.min(getWidth()/(double)w,getHeight()/(double)h);
            g.translate((getWidth()-w*scale)/2,(getHeight()-h*scale)/2);
            g.scale(scale,scale);
            g.setColor(new Color(26,38,55));
            for (int gx=18;gx<w;gx+=24) for(int gy=18;gy<h;gy+=24) g.fillOval(gx,gy,2,2);
            int[] x = {80, w / 2, w - 80};
            int dataY = Math.max(115, (int) (h * .46)), ackY = dataY + 62;
            ProtocolEvent event = cursor >= 0 ? trace.get(cursor) : null;
            Color accent = event == null ? BLUE : stateColor(event);
            g.setFont(new Font("SansSerif", Font.BOLD, 12)); g.setColor(MUTED);
            g.drawString("SENDER", x[0] - 27, 27); g.drawString("UNRELIABLE CHANNEL", x[1] - 69, 27); g.drawString("RECEIVER", x[2] - 32, 27);
            int[] path = event == null ? new int[]{-1, -1} : route(event);
            for (int i = 0; i < 3; i++) {
                boolean active = event != null && (path[0] == i || path[1] == i);
                g.setColor(active ? new Color(39, 63, 88) : new Color(27, 39, 56));
                g.fillRoundRect(x[i] - 61, 40, 122, 45, 16, 16);
                if (active) { g.setColor(accent); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(x[i] - 61, 40, 122, 45, 12, 12); }
                g.setColor(INK); g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                center(g, i == 0 ? "Send & wait" : i == 1 ? "Delay or drop" : "Accept & ACK", x[i], 67);
            }
            g.setStroke(new BasicStroke(2)); g.setColor(new Color(46, 65, 88));
            arrow(g, x[0], x[2], dataY); arrow(g, x[2], x[0], ackY);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11)); g.setColor(MUTED);
            g.drawString("DATA / EOF", 18, dataY - 20); g.drawString("ACK", 18, ackY - 20);
            if (event != null) {
                drawMarker(g, dataMarker, x, dataY);
                drawMarker(g, ackMarker, x, ackY);
                g.setColor(accent); g.setFont(new Font("SansSerif", Font.BOLD, 12));

                if (event.action().equals("TIMEOUT")) {
                    g.setColor(RED); g.setStroke(new BasicStroke(2));
                    g.drawRoundRect(x[0]-64,37,128,51,16,16);
                }
            }
            g.dispose();
        }
        private void drawMarker(Graphics2D g, Marker marker, int[] x, int y) {
            if (marker == null) return;
            ProtocolEvent e = marker.event();
            int[] path = route(e);
            double t = marker.index() == cursor ? animationFraction() : 1;
            double smooth = t*t*(3-2*t);
            int px = (int) (x[path[0]] + (x[path[1]]-x[path[0]])*smooth);
            boolean current = marker.index() == cursor;
            Color color = stateColor(e);
            // Keep the last data and ACK visible as context while the other lane advances.
            if (!current) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,.58f));
            g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),32));
            g.fillRoundRect(px-53,y-20,106,40,16,16);
            g.setColor(color); g.fillRoundRect(px-48,y-15,96,30,12,12);
            g.setColor(new Color(12,22,34)); g.setFont(new Font("SansSerif",Font.BOLD,12));
            center(g,(e.action().equals("DROP") ? "X " : e.action().equals("DELAY") ? "... " : "")+e.label(),px,y+5);
            g.setColor(color); g.setFont(new Font("SansSerif",Font.PLAIN,10));
            String caption = e.action().equals("DELAY") ? "DELAY " + e.delay() + " ms"
                    : e.action().equals("DROP") ? "DROPPED" : title(e).toUpperCase();
            center(g,caption,px,y+32);
            g.setComposite(AlphaComposite.SrcOver);
        }
        private void center(Graphics2D g, String text, int x, int y) { g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y); }
        private void arrow(Graphics2D g, int from, int to, int y) {
            g.drawLine(from, y, to, y); int direction = to > from ? 1 : -1;
            g.drawLine(to, y, to - direction * 8, y - 5); g.drawLine(to, y, to - direction * 8, y + 5);
        }
    }
}