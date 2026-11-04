import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicButtonUI;

/** Shared desktop palette and controls, without external dependencies. */
public final class LabTheme {
    public static final Color BG = new Color(12, 18, 29), SURFACE = new Color(21, 30, 44);
    public static final Color LINE = new Color(44, 59, 79), TEXT = new Color(229, 237, 247);
    public static final Color MUTED = new Color(151, 169, 191), ACCENT = new Color(102, 168, 255);
    public static void install() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
        Font font = new Font("Segoe UI", Font.PLAIN, 13);
        for (String key : new String[]{"Label.font", "Button.font", "TextField.font", "TextArea.font", "ComboBox.font", "Spinner.font", "TabbedPane.font", "List.font"}) UIManager.put(key, font);
        for (String key : new String[]{"Panel.background", "Viewport.background", "TabbedPane.background", "ScrollPane.background", "OptionPane.background"}) UIManager.put(key, BG);
        for (String key : new String[]{"Label.foreground", "Button.foreground", "TextField.foreground", "TextArea.foreground", "ComboBox.foreground", "Spinner.foreground", "List.foreground", "TabbedPane.foreground", "OptionPane.messageForeground"}) UIManager.put(key, TEXT);
        for (String key : new String[]{"TextField.background", "TextField.inactiveBackground", "TextArea.background", "ComboBox.background", "Spinner.background", "List.background", "Button.background"}) UIManager.put(key, SURFACE);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextField.selectionBackground", new Color(49, 84, 126));
        UIManager.put("TabbedPane.selected", SURFACE);
        UIManager.put("TabbedPane.contentAreaColor", BG);
        UIManager.put("TabbedPane.focus", BG);
        UIManager.put("TabbedPane.selectHighlight", ACCENT);
        UIManager.put("TabbedPane.tabInsets", new Insets(10, 18, 10, 18));
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(1, 0, 0, 0));
        UIManager.put("Spinner.border", new LineBorder(LINE,1,true));
        UIManager.put("SplitPane.border", new EmptyBorder(0,0,0,0));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumb", LINE); UIManager.put("ScrollBar.track", BG);
        UIManager.put("SplitPane.background", BG); UIManager.put("SplitPaneDivider.draggingColor", LINE);
        UIManager.put("ProgressBar.background", SURFACE); UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.selectionForeground", BG); UIManager.put("ProgressBar.selectionBackground", TEXT);
        UIManager.put("ProgressBar.border", new EmptyBorder(0,0,0,0));
    }
    public static JLabel label(String text, int size, Color color, boolean bold) {
        JLabel label = new JLabel(text); label.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size)); label.setForeground(color); return label;
    }
    public static JPanel card(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 10)); panel.setBackground(SURFACE);
        panel.setBorder(new CompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(14,14,14,14)));
        panel.add(label(title, 11, MUTED, true), BorderLayout.NORTH); return panel;
    }
    public static void style(Component component) {
        if (component instanceof javax.swing.plaf.basic.BasicArrowButton arrow) {
            arrow.setBackground(SURFACE); arrow.setForeground(MUTED);
        } else if (component instanceof JButton b) {
            b.setUI(new BasicButtonUI() {
                @Override protected void paintText(Graphics g, AbstractButton button, Rectangle rect, String text) {
                    g.setColor(button.isEnabled() ? button.getForeground() : MUTED);
                    g.drawString(text,rect.x,rect.y+g.getFontMetrics().getAscent());
                }
                @Override public void paint(Graphics graphics, JComponent c) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean primary = Boolean.TRUE.equals(b.getClientProperty("primary"));
                    Color fill = primary ? ACCENT : new Color(32, 46, 65);
                    if (b.getModel().isRollover() && b.isEnabled()) fill = fill.brighter();
                    if (b.getModel().isPressed()) fill = fill.darker();
                    if (!b.isEnabled()) fill = new Color(23, 32, 46);
                    g.setColor(fill); g.fillRoundRect(0,0,c.getWidth(),c.getHeight(),12,12);
                    if (b.hasFocus()) { g.setColor(ACCENT); g.drawRoundRect(1,1,c.getWidth()-3,c.getHeight()-3,11,11); }
                    g.dispose(); super.paint(graphics,c);
                }
            });
            b.setForeground(Boolean.TRUE.equals(b.getClientProperty("primary")) ? BG : TEXT);
            b.setBorder(new EmptyBorder(8,12,8,12)); b.setContentAreaFilled(false); b.setFocusPainted(false);
            b.setRolloverEnabled(true); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else if (component instanceof JTabbedPane tabs) {
            tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
                @Override protected void paintTabBorder(Graphics g,int placement,int index,int x,int y,int w,int h,boolean selected) {}
                @Override protected void paintFocusIndicator(Graphics g,int placement,Rectangle[] rects,int index,Rectangle icon,Rectangle text,boolean selected) {}
                @Override protected void paintContentBorder(Graphics g,int placement,int selected) {}
                @Override protected void paintTabBackground(Graphics g,int placement,int index,int x,int y,int w,int h,boolean selected) {
                    g.setColor(selected ? SURFACE : BG); g.fillRoundRect(x,y,w,h,10,10);
                    if(selected) { g.setColor(ACCENT); g.fillRect(x+14,y+h-3,w-28,2); }
                }
            });
        } else if (component instanceof JSplitPane split) {
            split.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
                @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                    var divider = super.createDefaultDivider(); divider.setBorder(null); divider.setBackground(BG); return divider;
                }
            });
        } else if (component instanceof JTextField field) {
            field.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(9,10,9,10)));
            field.setBackground(BG); field.setForeground(TEXT); field.setCaretColor(TEXT);
        } else if (component instanceof JComboBox<?> combo) {
            combo.setBorder(new LineBorder(LINE,1,true)); combo.setBackground(SURFACE); combo.setForeground(TEXT);
        } else if (component instanceof JScrollPane scroll) {
            scroll.setBorder(new LineBorder(LINE,1,true));
        }
        if (component instanceof Container container) for (Component child : container.getComponents()) style(child);
    }
}