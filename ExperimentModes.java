import java.awt.*;
import javax.swing.*;

/** Top-level experiment modes, each retaining its own controls and file selection. */
public final class ExperimentModes extends JPanel {
    private final JPanel pages=new JPanel(new CardLayout());
    private final JButton single=new JButton("Single transfer"),batch=new JButton("Batch experiments");
    public ExperimentModes(JComponent singlePage,JComponent batchPage) {
        super(new BorderLayout(0,16));
        singlePage.setName("single-transfer-page");batchPage.setName("batch-experiments-page");
        pages.add(singlePage,"single");pages.add(batchPage,"batch");
        JPanel navigation=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));navigation.add(single);navigation.add(batch);
        add(navigation,BorderLayout.NORTH);add(pages,BorderLayout.CENTER);
        single.addActionListener(e->showMode(false));batch.addActionListener(e->showMode(true));showMode(false);
    }
    private void showMode(boolean batchMode) {
        ((CardLayout)pages.getLayout()).show(pages,batchMode?"batch":"single");
        single.putClientProperty("primary",!batchMode);batch.putClientProperty("primary",batchMode);
        single.setForeground(batchMode?LabTheme.TEXT:LabTheme.BG);batch.setForeground(batchMode?LabTheme.BG:LabTheme.TEXT);
        single.getAccessibleContext().setAccessibleDescription(batchMode?"Open single transfer mode":"Selected mode");
        batch.getAccessibleContext().setAccessibleDescription(batchMode?"Selected mode":"Open batch experiments mode");
        single.repaint();batch.repaint();
    }
}