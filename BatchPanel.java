import java.awt.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.table.*;

/** Descriptive experiment results; verified timing and failure rates stay separate. */
public final class BatchPanel extends JPanel {
    private final JTextField losses=new JTextField("0, 5, 10, 20"),delays=new JTextField("10-80");
    private final JSpinner repeats=new JSpinner(new SpinnerNumberModel(3,1,20,1));
    private final JButton start=new JButton("Run batch"),cancel=new JButton("Cancel batch");
    private final JLabel state=LabTheme.label("Choose a batch input file above.",13,LabTheme.MUTED,false);
    private final JProgressBar progress=new JProgressBar();
    private final Results results=new Results();
    private final Consumer<Boolean> busy;
    private final Supplier<Path> input;
    private BatchRunner runner;
    private boolean running,externalBusy;
    public BatchPanel(Supplier<Path> input,Consumer<Boolean> busy) {
        super(new BorderLayout(0,14));this.input=input;this.busy=busy;setBorder(new EmptyBorder(16,0,0,0));
        JPanel top=new JPanel(new BorderLayout(0,12));
        JTextArea intro=text("Compare repeated transfers of the same file. Each loss percentage is paired with every delay range.",2);top.add(intro,BorderLayout.NORTH);
        JPanel fields=new JPanel(new GridBagLayout());GridBagConstraints c=new GridBagConstraints();c.gridy=0;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;c.insets=new Insets(0,0,0,10);
        fields.add(field("LOSS (%) - comma separated",losses),c);c.gridx=1;fields.add(field("DELAY (ms) - min-max ranges",delays),c);
        c.gridx=2;c.weightx=.25;c.insets=new Insets(0,0,0,0);fields.add(field("REPEATS",repeats),c);top.add(fields,BorderLayout.CENTER);
        JPanel actions=new JPanel(new BorderLayout(0,8));JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        start.putClientProperty("primary",true);buttons.add(start);buttons.add(cancel);actions.add(buttons,BorderLayout.NORTH);
        progress.setBorderPainted(false);progress.setPreferredSize(new Dimension(300,24));progress.setStringPainted(true);actions.add(progress,BorderLayout.SOUTH);top.add(actions,BorderLayout.SOUTH);add(top,BorderLayout.NORTH);
        JTable table=new JTable(results);table.setRowHeight(34);table.setFillsViewportHeight(true);table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setBackground(LabTheme.BG);table.setForeground(LabTheme.TEXT);table.setGridColor(LabTheme.LINE);table.setSelectionBackground(new Color(40,65,94));table.setSelectionForeground(LabTheme.TEXT);
        table.getTableHeader().setDefaultRenderer((list,value,selected,focus,row,column)->{
            JLabel label=LabTheme.label(String.valueOf(value),11,LabTheme.MUTED,true);label.setOpaque(true);label.setBackground(LabTheme.SURFACE);label.setBorder(new EmptyBorder(10,7,10,7));return label;
        });
        DefaultTableCellRenderer cells=new DefaultTableCellRenderer();cells.setHorizontalAlignment(SwingConstants.CENTER);table.setDefaultRenderer(Object.class,cells);
        JScrollPane scroll=new JScrollPane(table,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        table.getTableHeader().setPreferredSize(new Dimension(300,34));scroll.setColumnHeaderView(table.getTableHeader());add(scroll,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(0,7));bottom.add(state,BorderLayout.NORTH);
        bottom.add(text("Time: average of verified runs. Retries: average of verified + failed runs. Cancelled and unstarted trials are excluded from success percentages. Timing includes setup and verification.",3),BorderLayout.CENTER);add(bottom,BorderLayout.SOUTH);
        DocumentListener changed=new DocumentListener(){public void insertUpdate(DocumentEvent e){plan();}public void removeUpdate(DocumentEvent e){plan();}public void changedUpdate(DocumentEvent e){plan();}};
        losses.getDocument().addDocumentListener(changed);delays.getDocument().addDocumentListener(changed);repeats.addChangeListener(e->plan());
        start.addActionListener(e->run());cancel.addActionListener(e->{cancel.setEnabled(false);state.setText("Cancelling the active transfer...");cancelBatch();});
        plan();updateControls();
    }
    private static JTextArea text(String value,int rows){JTextArea area=new JTextArea(value,rows,1);area.setLineWrap(true);area.setWrapStyleWord(true);area.setEditable(false);area.setOpaque(false);area.setForeground(LabTheme.MUTED);area.setFont(new Font("Segoe UI",Font.PLAIN,12));return area;}
    private static JPanel field(String label,JComponent input){JPanel panel=new JPanel(new BorderLayout(0,6));panel.add(LabTheme.label(label,11,LabTheme.MUTED,true),BorderLayout.NORTH);panel.add(input,BorderLayout.CENTER);return panel;}
    private void plan(){if(running)return;try{int count=BatchRunner.conditions(losses.getText(),delays.getText()).size()*(Integer)repeats.getValue();progress.setString(count+" trials planned");}catch(Exception e){progress.setString("Enter loss percentages and delay ranges");}}
    public void setExternalBusy(boolean busy){externalBusy=busy;updateControls();}
    public void cancelBatch(){BatchRunner active=runner;if(active!=null)new Thread(active::close,"packetlab-batch-cancel").start();}
    private void updateControls(){boolean editable=!running&&!externalBusy;start.setEnabled(editable);losses.setEnabled(editable);delays.setEnabled(editable);repeats.setEnabled(editable);cancel.setEnabled(running);}
    private void run(){
        try {
            Path file=input.get();if(file==null)throw new IllegalArgumentException("Choose a batch input file above or use its demo file first.");
            repeats.commitEdit();int count=(Integer)repeats.getValue();List<BatchRunner.Condition> conditions=BatchRunner.conditions(losses.getText(),delays.getText());
            int total=conditions.size()*count;if(total>100)throw new IllegalArgumentException("Limit the batch to 100 total trials.");
            results.reset(conditions);running=true;runner=new BatchRunner();BatchRunner active=runner;updateControls();busy.accept(true);
            progress.setMaximum(total);progress.setValue(0);state.setText("Preparing "+file.getFileName()+"...");
            new SwingWorker<BatchRunner.Report,Object>() {
                @Override protected BatchRunner.Report doInBackground()throws Exception {
                    try{return active.run(file,Path.of("runs").toAbsolutePath(),conditions,count,trial->publish(trial),message->publish(message));}
                    finally{active.close();}
                }
                @Override protected void process(List<Object> events){for(Object event:events){
                    if(event instanceof BatchRunner.Trial trial){results.add(trial);progress.setValue(results.trials.size());progress.setString(results.trials.size()+" / "+total+" trials recorded");}
                    else state.setText(event.toString());
                }}
                @Override protected void done(){
                    try{BatchRunner.Report report=get();long verified=report.trials().stream().filter(t->t.outcome()==BatchRunner.Outcome.VERIFIED).count();
                        state.setText((report.cancelled()?"Batch cancelled":"Batch finished")+" · "+verified+" verified · "+report.trials().size()+" / "+total+" trials recorded");
                        state.setToolTipText("Files stored in "+report.directory());
                    }catch(Exception e){Throwable reason=e.getCause()==null?e:e.getCause();state.setText("Batch stopped: "+reason.getMessage());}
                    finally{runner=null;running=false;busy.accept(false);updateControls();}
                }
            }.execute();
        }catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Batch experiments",JOptionPane.ERROR_MESSAGE);}
    }
    static final class Results extends AbstractTableModel {
        private List<BatchRunner.Condition> conditions=List.of();private final List<BatchRunner.Trial> trials=new ArrayList<>();
        private final String[] titles={"Loss","Delay (ms)","Verified / run","Failed","Cancelled","Success %","Avg time (s)","Avg retries"};
        void reset(List<BatchRunner.Condition> values){conditions=values;trials.clear();fireTableDataChanged();}
        void add(BatchRunner.Trial value){trials.add(value);fireTableDataChanged();}
        public int getRowCount(){return conditions.size();}public int getColumnCount(){return titles.length;}public String getColumnName(int col){return titles[col];}
        public Object getValueAt(int row,int col){BatchRunner.Condition c=conditions.get(row);BatchRunner.Aggregate a=BatchRunner.aggregate(trials,c);return switch(col){
            case 0->String.format(Locale.ROOT,"%.1f%%",c.loss()*100);case 1->c.minDelay()+"–"+c.maxDelay();case 2->a.verified()+" / "+a.finished();case 3->a.failed();
            case 4->a.cancelled();case 5->number(a.successPercent(),"%.1f%%");case 6->number(a.averageSeconds(),"%.2f");case 7->number(a.averageRetries(),"%.1f");default->"";
        };}
        private String number(double value,String format){return Double.isNaN(value)?"—":String.format(Locale.ROOT,format,value);}
    }
}