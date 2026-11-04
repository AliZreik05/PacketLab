import java.awt.*;
import java.nio.file.Path;
import javax.swing.*;

public class RunSummaryTest {
    public static void main(String[] args)throws Exception {
        RunStatistics stats=new RunStatistics();
        send(stats,"SEND",0,"DATA");send(stats,"DROP",0,"ACK");send(stats,"TIMEOUT",0,"DATA");
        send(stats,"SEND",0,"DATA");send(stats,"DUPLICATE",0,"DATA");send(stats,"ACK_RECEIVE",0,"ACK");
        send(stats,"SEND",1,"EOF");send(stats,"DROP",1,"EOF");send(stats,"TIMEOUT",1,"EOF");send(stats,"SEND",1,"EOF");
        stats.accept("channel [CHANNEL] DROPPED from A (session close)");
        RunStatistics.Snapshot result=stats.snapshot();
        check(result.attempts()==4&&result.retransmissions()==2&&result.timeouts()==2&&result.duplicates()==1&&result.dataDrops()==1&&result.ackDrops()==1,"Mixed data/EOF/ACK accounting");
        for(int i=2;i<10005;i++)send(stats,"SEND",i,"DATA");
        check(stats.snapshot().attempts()==10007,"Counters must cover events beyond the visual trace limit");
        check(result.attempts()==4,"Snapshots must remain immutable");
        RunStatistics exhausted=new RunStatistics();
        for(int i=0;i<40;i++){send(exhausted,"SEND",0,"DATA");send(exhausted,"TIMEOUT",0,"DATA");}
        check(exhausted.snapshot().retransmissions()==39&&exhausted.snapshot().timeouts()==40,"Final timeout must not invent an extra retry");
        SwingUtilities.invokeAndWait(()->{
            LabTheme.install();RunSummaryPanel panel=new RunSummaryPanel();
            panel.begin(Path.of("sample.bin"),42,Experiment.DROP_ACK,0,10,80);
            panel.finish(result,2,new LabSession.Result(Path.of("received.bin"),42,2,"sample-hash",result),false,null);
            check(contains(panel,"sample-hash"),"Verified run must show its hash");
            String longName="long-folder-name/".repeat(20)+"received.bin";
            panel.finish(result,2,new LabSession.Result(Path.of(longName),42,2,"f".repeat(64),result),false,null);
            for(int width:new int[]{520,700,900}) {
                panel.setSize(width,440);layout(panel);
                check(!hasScrollPane(panel),"Summary must contain no scroll panes");
                check(hasPathTooltip(panel,Path.of(longName).toString()),"Full file path must remain available without expanding the layout");
                check(inside(panel,panel.getBounds().width,panel.getBounds().height),"Summary content must fit at "+width);
            }
            panel.finish(result,2,null,true,"Transfer cancelled");
            check(!contains(panel,"sample-hash")&&contains(panel,"Not verified")&&contains(panel,"Transfer cancelled"),"Cancelled run must clear stale verification");
            panel.begin(Path.of("another.bin"),0,Experiment.NORMAL,0,10,80);
            check(contains(panel,"Transfer running")&&!contains(panel,"sample-hash"),"A new run must reset the previous result");
        });
        System.out.println("PASS summary counts, EOF/ACK distinctions, retry exhaustion, immutable snapshots, and verification reset");
    }
    static boolean hasScrollPane(Container parent){for(Component c:parent.getComponents()){if(c instanceof JScrollPane)return true;if(c instanceof Container child&&hasScrollPane(child))return true;}return false;}
    static boolean hasPathTooltip(Container parent,String path){for(Component c:parent.getComponents()){if(c instanceof JComponent jc&&path.equals(jc.getToolTipText()))return true;if(c instanceof Container child&&hasPathTooltip(child,path))return true;}return false;}
    static boolean inside(Container parent,int width,int height){
        for(Component c:parent.getComponents())if(c.isVisible()) {
            if(c.getX()<0||c.getY()<0||c.getX()+c.getWidth()>width||c.getY()+c.getHeight()>height)return false;
            if(c instanceof Container child&&!inside(child,child.getWidth(),child.getHeight()))return false;
        }
        return true;
    }
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested);}
    static void send(RunStatistics stats,String action,int sequence,String kind){stats.accept("worker [EVENT] "+action+"|"+sequence+"|"+kind+"|0|1");}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static boolean contains(Container parent,String text){
        for(Component c:parent.getComponents()){
            if(c instanceof JLabel label&&label.getText().contains(text))return true;
            if(c instanceof javax.swing.text.JTextComponent field&&field.getText().contains(text))return true;
            if(c instanceof Container child&&contains(child,text))return true;
        }
        return false;
    }
}