import java.awt.*;
import javax.swing.*;

public class ExperimentModesTest {
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            LabTheme.install();JPanel single=new JPanel(new BorderLayout()),batch=new JPanel(new BorderLayout());
            JTextField singleFile=new JTextField("single.bin"),batchFile=new JTextField("batch.png");single.add(singleFile,BorderLayout.NORTH);batch.add(batchFile,BorderLayout.NORTH);
            JTabbedPane details=new JTabbedPane();details.addTab("Packet flow",new JPanel());details.addTab("Run summary",new JPanel());single.add(details);
            ExperimentModes modes=new ExperimentModes(single,batch);LabTheme.style(modes);
            check(single.isVisible()&&!batch.isVisible(),"Single mode must be the default");
            button(modes,"Batch experiments").doClick();
            check(batch.isVisible()&&!single.isVisible(),"Batch must be a separate top-level page");
            check(!SwingUtilities.isDescendingFrom(batch,details),"Batch must not be nested in single-transfer tabs");
            batchFile.setText("another-batch.png");button(modes,"Single transfer").doClick();
            check(single.isVisible()&&singleFile.getText().equals("single.bin"),"Switching must retain the independent single input");
            button(modes,"Batch experiments").doClick();check(batchFile.getText().equals("another-batch.png"),"Switching must retain the batch input");
            check(Boolean.TRUE.equals(button(modes,"Batch experiments").getClientProperty("primary")),"Active mode must be visually selected");
        });
        System.out.println("PASS top-level modes, independent selection retention, active mode, and single-view nesting");
    }
    static JButton button(Container parent,String name){for(Component c:parent.getComponents()){if(c instanceof JButton b&&b.getText().equals(name))return b;if(c instanceof Container child){JButton found=button(child,name);if(found!=null)return found;}}return null;}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}