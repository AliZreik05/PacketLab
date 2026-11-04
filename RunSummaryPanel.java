import java.awt.*;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** One compact results view; it never interrupts packet playback. */
public final class RunSummaryPanel extends JPanel {
    private final JLabel outcome=LabTheme.label("No run yet",24,LabTheme.TEXT,true);
    private final JTextArea context=text(3), detail=text(2), note=text(2);
    private final JTextField output=new JTextField(), hash=new JTextField();
    private final JLabel[] values=new JLabel[6];
    public RunSummaryPanel() {
        super(new BorderLayout(0,18));setBorder(new EmptyBorder(22,0,0,0));
        JPanel header=new JPanel(new BorderLayout(0,10));header.add(outcome,BorderLayout.NORTH);header.add(context,BorderLayout.CENTER);
        context.setText("Complete a transfer to see its measured results here.");add(header,BorderLayout.NORTH);
        JPanel stats=new JPanel(new GridLayout(2,3,12,12));
        String[] names={"DURATION","TRANSMISSION ATTEMPTS","RETRANSMISSIONS","DATA / EOF DROPPED","ACKS DROPPED","DUPLICATES"};
        for(int i=0;i<values.length;i++) {
            JPanel card=LabTheme.card(names[i]);values[i]=LabTheme.label("—",24,LabTheme.TEXT,true);
            card.add(values[i],BorderLayout.CENTER);stats.add(card);
        }
        JPanel body=new JPanel(new BorderLayout(0,16));body.add(stats,BorderLayout.NORTH);
        JPanel details=new JPanel();details.setLayout(new BoxLayout(details,BoxLayout.Y_AXIS));
        details.add(detail);details.add(Box.createVerticalStrut(12));
        details.add(field("RECEIVED FILE",output));details.add(Box.createVerticalStrut(12));details.add(field("SHA-256",hash));
        note.setText("Counts include file data, filename metadata, and EOF. Session-close messages are excluded.\nPlayback speed does not affect these measurements.");
        details.add(Box.createVerticalStrut(12));details.add(note);body.add(details,BorderLayout.CENTER);add(body,BorderLayout.CENTER);
        output.setEditable(false);hash.setEditable(false);hash.setFont(new Font("Consolas",Font.PLAIN,12));
        output.setText("Not available");hash.setText("Not verified");
    }
    private static JTextArea text(int rows) {
        JTextArea area=new JTextArea(rows,30);area.setEditable(false);area.setOpaque(false);area.setLineWrap(true);area.setWrapStyleWord(true);
        area.setFont(new Font("Segoe UI",Font.PLAIN,13));area.setForeground(LabTheme.MUTED);return area;
    }
    private static JPanel field(String label,JTextField input) {
        JPanel panel=new JPanel(new BorderLayout(0,6));panel.add(LabTheme.label(label,11,LabTheme.MUTED,true),BorderLayout.NORTH);
        panel.add(input,BorderLayout.CENTER);panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,66));return panel;
    }
    public void begin(Path input,long bytes,Experiment scenario,double loss,int low,int high) {
        outcome.setText("Transfer running");outcome.setForeground(LabTheme.ACCENT);
        context.setText(String.format(Locale.ROOT,"%s  ·  %,d bytes\n%s  ·  Random loss %.0f%%  ·  Delay %d–%d ms",input.getFileName(),bytes,scenario,loss*100,low,high));
        detail.setText("Waiting for the transfer and file-integrity check to finish.");
        for(JLabel value:values)value.setText("—");output.setText("Not available");hash.setText("Not verified");
    }
    public void finish(RunStatistics.Snapshot stats,double seconds,LabSession.Result result,boolean cancelled,String failure) {
        boolean verified=result!=null;
        outcome.setText(verified?"File verified":cancelled?"Transfer cancelled":"Transfer failed");
        outcome.setForeground(verified?new Color(89,218,169):cancelled?new Color(246,190,91):new Color(255,117,127));
        values[0].setText(String.format(Locale.ROOT,"%.2f s",seconds));
        long[] counts={stats.attempts(),stats.retransmissions(),stats.dataDrops(),stats.ackDrops(),stats.duplicates()};
        for(int i=0;i<counts.length;i++)values[i+1].setText(String.format(Locale.ROOT,"%,d",counts[i]));
        if(verified) {
            output.setText(result.file().toString());output.setCaretPosition(0);output.setToolTipText(result.file().toString());
            hash.setText(result.sha256());hash.setCaretPosition(0);
            detail.setText(String.format(Locale.ROOT,"Original and received SHA-256 hashes match. %,d bytes verified.\nTimeouts observed: %,d. Duration includes setup, transfer, verification and cleanup.",result.bytes(),stats.timeouts()));
        } else {
            output.setText("No verified output file");output.setToolTipText(null);hash.setText("Not verified");
            detail.setText((failure==null?"The run did not complete.":failure)+"\nTimeouts observed: "+stats.timeouts()+". Counts describe events captured before the run stopped.");
        }
    }
}