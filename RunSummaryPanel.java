import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** A single-screen summary. Full paths and diagnostic details can be copied. */
public final class RunSummaryPanel extends JPanel {
    private final JLabel outcome=LabTheme.label("No run yet",24,LabTheme.TEXT,true);
    private final JTextArea context=text(2),detail=text(2),hash=text(2);
    private final JLabel output=LabTheme.label("No verified output",12,LabTheme.MUTED,false);
    private final JButton copy=new JButton("Copy full details");
    private final JLabel[] values=new JLabel[6];
    private String fullDetails="";
    public RunSummaryPanel() {
        super(new BorderLayout(0,14));setBorder(new EmptyBorder(14,0,8,0));
        JPanel header=new JPanel(new BorderLayout(0,8));header.add(outcome,BorderLayout.NORTH);header.add(context,BorderLayout.CENTER);
        context.setText("Complete a transfer to see its measured results here.");add(header,BorderLayout.NORTH);
        JPanel stats=new JPanel(new GridLayout(2,3,10,10));
        String[] names={"DURATION","SEND ATTEMPTS","RETRANSMISSIONS","DATA / EOF DROPPED","ACKS DROPPED","DUPLICATES"};
        for(int i=0;i<values.length;i++) {
            JPanel card=LabTheme.card(names[i]);
            card.setBorder(new javax.swing.border.CompoundBorder(new javax.swing.border.LineBorder(LabTheme.LINE),new EmptyBorder(10,12,10,12)));
            values[i]=LabTheme.label("—",24,LabTheme.TEXT,true);card.add(values[i],BorderLayout.CENTER);stats.add(card);
        }
        stats.setToolTipText("Counts include file data, filename metadata and EOF. Session-close messages are excluded.");
        add(stats,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(0,10));footer.add(detail,BorderLayout.NORTH);
        JPanel file=new JPanel(new BorderLayout(12,0));file.add(output,BorderLayout.CENTER);file.add(copy,BorderLayout.EAST);
        JPanel verification=new JPanel(new BorderLayout(0,7));verification.add(file,BorderLayout.NORTH);
        JPanel fingerprint=new JPanel(new BorderLayout(0,5));fingerprint.add(LabTheme.label("SHA-256",11,LabTheme.MUTED,true),BorderLayout.NORTH);
        hash.setFont(new Font("Consolas",Font.PLAIN,12));hash.setText("Not verified");hash.setWrapStyleWord(false);
        fingerprint.add(hash,BorderLayout.CENTER);verification.add(fingerprint,BorderLayout.CENTER);
        footer.add(verification,BorderLayout.CENTER);add(footer,BorderLayout.SOUTH);
        copy.setEnabled(false);copy.setToolTipText("Copy the full path, hash, settings and counters without expanding this view");
        copy.addActionListener(e->{
            try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(fullDetails),null); }
            catch(Exception ex){JOptionPane.showMessageDialog(this,"Could not copy details: "+ex.getMessage());}
        });
    }
    private static JTextArea text(int rows) {
        JTextArea area=new JTextArea(rows,1);area.setEditable(false);area.setOpaque(false);area.setLineWrap(true);area.setWrapStyleWord(true);
        area.setFont(new Font("Segoe UI",Font.PLAIN,13));area.setForeground(LabTheme.MUTED);return area;
    }
    private static String shortName(String value){return value.length()>64?value.substring(0,30)+"…"+value.substring(value.length()-30):value;}
    public void begin(Path input,long bytes,Experiment scenario,double loss,int low,int high) {
        outcome.setText("Transfer running");outcome.setForeground(LabTheme.ACCENT);
        context.setText(String.format(Locale.ROOT,"%s  ·  %,d bytes\n%s  ·  Random loss %.0f%%  ·  Delay %d–%d ms",shortName(input.getFileName().toString()),bytes,scenario,loss*100,low,high));
        context.setToolTipText(input.toString());detail.setText("Waiting for the transfer and file-integrity check to finish.");
        for(JLabel value:values)value.setText("—");output.setText("No verified output");output.setToolTipText(null);hash.setText("Not verified");
        fullDetails="";copy.setEnabled(false);
    }
    public void finish(RunStatistics.Snapshot stats,double seconds,LabSession.Result result,boolean cancelled,String failure) {
        boolean verified=result!=null;
        outcome.setText(verified?"File verified":cancelled?"Transfer cancelled":"Transfer failed");
        outcome.setForeground(verified?new Color(89,218,169):cancelled?new Color(246,190,91):new Color(255,117,127));
        values[0].setText(String.format(Locale.ROOT,"%.2f s",seconds));
        long[] counts={stats.attempts(),stats.retransmissions(),stats.dataDrops(),stats.ackDrops(),stats.duplicates()};
        for(int i=0;i<counts.length;i++)values[i+1].setText(String.format(Locale.ROOT,"%,d",counts[i]));
        if(verified) {
            output.setText("Received: "+shortName(result.file().getFileName().toString()));output.setToolTipText(result.file().toString());
            hash.setText(result.sha256());hash.setCaretPosition(0);
            detail.setText(String.format(Locale.ROOT,"SHA-256 hashes match · %,d bytes verified · %,d timeouts\nDuration includes setup, transfer, verification and cleanup.",result.bytes(),stats.timeouts()));
        } else {
            output.setText("No verified output file");output.setToolTipText(null);hash.setText("Not verified");
            String reason=failure==null?"The run did not complete.":failure;
            detail.setText(shortName(reason)+"\nTimeouts: "+stats.timeouts()+" · Counts reflect the events observed before the run stopped.");
            detail.setToolTipText(reason);
        }
        fullDetails=outcome.getText()+"\n"+context.getText()+"\nDuration: "+seconds+" s\n"+stats
                +"\nReceived file: "+(verified?result.file():"Not verified")+"\nSHA-256: "+hash.getText()
                +"\n"+(failure==null?"":failure)+"\nCounts include metadata and EOF; session-close packets are excluded.";
        copy.setEnabled(true);revalidate();repaint();
    }
}