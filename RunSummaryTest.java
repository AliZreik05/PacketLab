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
            panel.finish(result,2,null,true,"Transfer cancelled");
            check(!contains(panel,"sample-hash")&&contains(panel,"Not verified")&&contains(panel,"Transfer cancelled"),"Cancelled run must clear stale verification");
            panel.begin(Path.of("another.bin"),0,Experiment.NORMAL,0,10,80);
            check(contains(panel,"Transfer running")&&!contains(panel,"sample-hash"),"A new run must reset the previous result");
        });
        System.out.println("PASS summary counts, EOF/ACK distinctions, retry exhaustion, immutable snapshots, and verification reset");
    }
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