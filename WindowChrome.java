import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

/** App-owned dark title bar, with normal window controls and edge resizing. */
public final class WindowChrome extends JPanel {
    private final JFrame frame;
    private Rectangle normalBounds;
    private final WindowButton maximize;
    private static final int N=1,S=2,W=4,E=8;

    public static void install(JFrame frame,JComponent content) {
        frame.setUndecorated(true);
        frame.setIconImages(List.of(icon(16),icon(32),icon(48),icon(128)));
        frame.setContentPane(new WindowChrome(frame,content));
    }
    private WindowChrome(JFrame frame,JComponent content) {
        super(new BorderLayout()); this.frame=frame;
        setBackground(LabTheme.BG);
        setBorder(new CompoundBorder(new LineBorder(LabTheme.LINE),new EmptyBorder(4,4,4,4)));
        JPanel title=new JPanel(new BorderLayout());title.setBackground(LabTheme.BG);
        title.setBorder(new MatteBorder(0,0,1,0,LabTheme.LINE));title.setPreferredSize(new Dimension(300,39));
        JLabel brand=LabTheme.label("PacketLab",12,LabTheme.MUTED,false);
        brand.setIcon(new ImageIcon(icon(20)));brand.setIconTextGap(9);brand.setBorder(new EmptyBorder(0,10,0,0));
        title.add(brand,BorderLayout.WEST);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));controls.setOpaque(false);
        WindowButton minimize=new WindowButton("Minimize",0);
        maximize=new WindowButton("Maximize / restore",1);
        WindowButton close=new WindowButton("Close",2);
        minimize.addActionListener(e->frame.setState(Frame.ICONIFIED));
        maximize.addActionListener(e->toggleMaximize());
        close.addActionListener(e->frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING)));
        controls.add(minimize);controls.add(maximize);controls.add(close);title.add(controls,BorderLayout.EAST);
        add(title,BorderLayout.NORTH);add(content,BorderLayout.CENTER);
        MouseAdapter drag=new MouseAdapter() {
            Point press; Point origin;
            @Override public void mousePressed(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e))return;
                press=e.getLocationOnScreen();origin=frame.getLocation();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if(press==null)return;
                Point now=e.getLocationOnScreen();
                if(isMaximized()) {
                    double ratio=(press.x-frame.getX())/(double)frame.getWidth();
                    restore();
                    frame.setLocation(now.x-(int)(frame.getWidth()*ratio),now.y-20);
                    press=now;origin=frame.getLocation();
                } else frame.setLocation(origin.x+now.x-press.x,origin.y+now.y-press.y);
            }
            @Override public void mouseReleased(MouseEvent e){press=null;}
            @Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2&&SwingUtilities.isLeftMouseButton(e))toggleMaximize();}
        };
        title.addMouseListener(drag);title.addMouseMotionListener(drag);
        brand.addMouseListener(drag);brand.addMouseMotionListener(drag);
        MouseAdapter resize=new MouseAdapter() {
            int edges;Point press;Rectangle bounds;
            @Override public void mouseMoved(MouseEvent e){setCursor(Cursor.getPredefinedCursor(cursorFor(edgesAt(e.getPoint()))));}
            @Override public void mouseExited(MouseEvent e){if(press==null)setCursor(Cursor.getDefaultCursor());}
            @Override public void mousePressed(MouseEvent e){
                edges=edgesAt(e.getPoint());if(edges==0||!SwingUtilities.isLeftMouseButton(e))return;
                press=e.getLocationOnScreen();bounds=frame.getBounds();
            }
            @Override public void mouseDragged(MouseEvent e){
                if(press==null)return;Point now=e.getLocationOnScreen();
                frame.setBounds(resized(bounds,edges,now.x-press.x,now.y-press.y,frame.getMinimumSize()));
            }
            @Override public void mouseReleased(MouseEvent e){press=null;}
        };
        addMouseListener(resize);addMouseMotionListener(resize);
        frame.addWindowStateListener(e->{maximize.repaint();});
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F4,InputEvent.ALT_DOWN_MASK),"closeWindow");
        frame.getRootPane().getActionMap().put("closeWindow",new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));}
        });
    }
    private boolean isMaximized(){return (frame.getExtendedState()&Frame.MAXIMIZED_BOTH)!=0;}
    private void restore(){
        frame.setExtendedState(Frame.NORMAL);
        if(normalBounds!=null)frame.setBounds(normalBounds);
    }
    private void toggleMaximize(){
        if(isMaximized()){restore();return;}
        normalBounds=frame.getBounds();
        GraphicsConfiguration config=frame.getGraphicsConfiguration();Rectangle usable=new Rectangle(config.getBounds());
        Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(config);
        usable.x+=insets.left;usable.y+=insets.top;usable.width-=insets.left+insets.right;usable.height-=insets.top+insets.bottom;
        frame.setMaximizedBounds(usable);frame.setExtendedState(Frame.MAXIMIZED_BOTH);
    }
    private int edgesAt(Point point){
        if(isMaximized())return 0;
        return (point.y<6?N:point.y>=getHeight()-6?S:0)|(point.x<6?W:point.x>=getWidth()-6?E:0);
    }
    private static int cursorFor(int edges){return switch(edges){
        case N -> Cursor.N_RESIZE_CURSOR;case S -> Cursor.S_RESIZE_CURSOR;case W -> Cursor.W_RESIZE_CURSOR;case E -> Cursor.E_RESIZE_CURSOR;
        case N|W -> Cursor.NW_RESIZE_CURSOR;case N|E -> Cursor.NE_RESIZE_CURSOR;case S|W -> Cursor.SW_RESIZE_CURSOR;case S|E -> Cursor.SE_RESIZE_CURSOR;
        default -> Cursor.DEFAULT_CURSOR;
    };}
    static Rectangle resized(Rectangle b,int edges,int dx,int dy,Dimension min){
        int left=b.x,right=b.x+b.width,top=b.y,bottom=b.y+b.height;
        if((edges&W)!=0)left=Math.min(left+dx,right-min.width);
        if((edges&E)!=0)right=Math.max(right+dx,left+min.width);
        if((edges&N)!=0)top=Math.min(top+dy,bottom-min.height);
        if((edges&S)!=0)bottom=Math.max(bottom+dy,top+min.height);
        return new Rectangle(left,top,right-left,bottom-top);
    }
    static BufferedImage icon(int size){
        BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.scale(size/32.0,size/32.0);
        g.setColor(new Color(27,47,73));g.fillRoundRect(0,0,32,32,9,9);
        g.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(LabTheme.ACCENT);g.drawLine(8,10,23,10);g.drawLine(23,10,20,7);g.drawLine(23,10,20,13);
        g.setColor(new Color(89,218,169));g.drawLine(24,22,9,22);g.drawLine(9,22,12,19);g.drawLine(9,22,12,25);
        g.dispose();return image;
    }
    private final class WindowButton extends JButton {
        private final int type;
        WindowButton(String name,int type){
            this.type=type;setToolTipText(name);getAccessibleContext().setAccessibleName(name);
            setPreferredSize(new Dimension(44,37));setBorderPainted(false);setContentAreaFilled(false);setFocusPainted(false);setRolloverEnabled(true);
        }
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            if(getModel().isRollover()||hasFocus()){g.setColor(type==2?new Color(183,52,67):new Color(36,51,72));g.fillRoundRect(2,2,getWidth()-4,getHeight()-4,7,7);}
            g.setColor(LabTheme.TEXT);g.setStroke(new BasicStroke(1.3f));int x=getWidth()/2,y=getHeight()/2;
            if(type==0)g.drawLine(x-5,y+3,x+5,y+3);
            else if(type==2){g.drawLine(x-4,y-4,x+4,y+4);g.drawLine(x+4,y-4,x-4,y+4);}
            else if(isMaximized()){g.drawRect(x-3,y-5,8,8);g.setColor(LabTheme.BG);g.fillRect(x-5,y-3,8,8);g.setColor(LabTheme.TEXT);g.drawRect(x-5,y-3,8,8);}
            else g.drawRect(x-4,y-4,9,9);
            g.dispose();
        }
    }
}