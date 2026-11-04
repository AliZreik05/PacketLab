import java.awt.*;
import java.util.Arrays;
import javax.swing.*;

public class TracePanelTest {
    public static void main(String[] args) throws Exception {
        long future=java.time.Instant.now().getEpochSecond()*1_000_000_000L+30_000_000_000L;
        UCPPacket packet=new UCPPacket("A","B",future,"test",false);
        byte[] wire=packet.toBytes();
        check(UCPPacket.fromBytes(wire,wire.length).seq==future,"Ordering stamp must survive relay serialization");
        ProtocolEvent.observe(future);
        java.io.ByteArrayOutputStream captured=new java.io.ByteArrayOutputStream();
        java.io.PrintStream original=System.out;
        try {
            System.setOut(new java.io.PrintStream(captured));
            ProtocolEvent.emit("RECEIVE",0,"DATA",0);
        } finally { System.setOut(original); }
        check(ProtocolEvent.parse(captured.toString()).occurredAt()>future,"Receive must follow a remote send even if its timestamp is ahead of the local clock");
        SwingUtilities.invokeAndWait(() -> {
            java.util.concurrent.atomic.AtomicLong time=new java.util.concurrent.atomic.AtomicLong();
            TracePanel fast=new TracePanel(time::get);
            fast.reset();
            for(int i=0;i<20;i++)fast.accept(new ProtocolEvent("SEND",i,"DATA",0));
            JList<?> fastList=find(fast,JList.class);
            button(fast,"4x").doClick();
            time.set(190_000_000L);fast.tick();
            check(fastList.getSelectedIndex()==0,"4x must not skip the first event");
            check(fast.animationFraction()==1,"4x must visibly reach the destination before advancing");
            time.set(237_500_000L);fast.tick();
            check(fastList.getSelectedIndex()==1,"4x must advance at 237.5 ms");
            time.addAndGet(95_000_000L);fast.tick();
            double beforeChange=fast.animationFraction();
            button(fast,"2x").doClick();
            check(Math.abs(fast.animationFraction()-beforeChange)<.00001,"Changing speed must preserve packet position");
            button(fast,"Pause view").doClick();
            double beforePause=fast.animationFraction();
            time.addAndGet(10_000_000_000L);fast.tick();
            check(fast.animationFraction()==beforePause && fastList.getSelectedIndex()==1,"Paused fast playback must not move");
            button(fast,"Play view").doClick();
            check(Math.abs(fast.animationFraction()-beforePause)<.00001,"Resuming must preserve packet position");
            button(fast,"4x").doClick();
            for(int i=2;i<20;i++) {
                time.addAndGet(237_500_000L);fast.tick();
                check(fastList.getSelectedIndex()==i,"4x must visit every event in order");
            }
            assertRoute("SEND", "DATA", 0, 1);
            assertRoute("ACK_SEND", "ACK", 2, 1);
            assertRoute("FORWARD", "DATA", 1, 2);
            assertRoute("FORWARD", "ACK", 1, 0);
            assertRoute("DROP", "ACK", 1, 1);
            assertRoute("TIMEOUT", "DATA", 0, 0);
            assertRoute("DUPLICATE", "DATA", 2, 2);
            TracePanel panel = new TracePanel();
            panel.reset();
            for (int i = 0; i < 100; i++) panel.accept(new ProtocolEvent("SEND", i, "DATA", 0));
            JList<?> list = find(panel, JList.class);
            check(list.getSelectedIndex() == 0, "Burst must not overwrite the displayed event");
            button(panel, "Next step").doClick();
            check(list.getSelectedIndex() == 1, "Next must advance exactly one step");
            panel.accept(new ProtocolEvent("DROP", 100, "DATA", 0));
            panel.finished(true);
            check(list.getSelectedIndex() == 1, "New events and completion must not move a paused view");
            check(find(panel, JTextArea.class).getText().contains("DATA 1"), "Explanation must match selection");
            button(panel, "Latest event").doClick();
            check(list.getSelectedIndex() == 100, "Latest must select the last captured event");
            button(panel, "Previous").doClick();
            check(list.getSelectedIndex() == 99, "Previous must step back");
            button(panel, "First step").doClick();
            check(list.getSelectedIndex() == 0, "Replay starts at first event");
            panel.reset();
            panel.accept(new ProtocolEvent("RECEIVE",5,"DATA",0,400));
            panel.accept(new ProtocolEvent("FORWARD",5,"DATA",25,300));
            panel.accept(new ProtocolEvent("SEND",5,"DATA",0,100));
            panel.accept(new ProtocolEvent("DELAY",5,"DATA",25,200));
            panel.accept(new ProtocolEvent("ACK_RECEIVE",5,"ACK",0,800));
            panel.accept(new ProtocolEvent("FORWARD",5,"ACK",41,700));
            panel.accept(new ProtocolEvent("DELAY",5,"ACK",41,600));
            panel.accept(new ProtocolEvent("ACK_SEND",5,"ACK",0,500));
            String[] ordered = {"Send", "Delay", "Forward", "Accepted", "Send ACK", "Delay", "Forward", "ACK arrived"};
            for(int i=0;i<ordered.length;i++) {
                check(list.getModel().getElementAt(i).toString().contains(ordered[i]), "Out-of-order process logs must be sorted at step " + i);
            }
            button(panel,"First step").doClick();
            check(find(panel,JTextArea.class).getText().contains("sender sends DATA 5"),"Replay must begin with SEND even if its log arrives late");
            button(panel,"Next step").doClick();
            check(find(panel,JTextArea.class).getText().contains("25 ms"),"Delay must precede forwarding and acceptance");
            ProtocolEvent parsed=ProtocolEvent.parse("channel  [EVENT] FORWARD|5|ACK|41|700");
            check(parsed != null && parsed.occurredAt()==700,"Timestamp must survive event serialization");
            panel.reset(); panel.accept(new ProtocolEvent("SEND", 0, "EOF", 0));
            check(list.getModel().getSize() == 1 && list.getSelectedIndex() == 0, "Reset must discard previous playback");
            for (int i = 1; i < 10010; i++) panel.accept(new ProtocolEvent("SEND", i, "DATA", 0));
            check(list.getModel().getSize() == 10000, "History must remain bounded");
        });
        System.out.println("PASS visualization routes, burst pacing, pause/step/replay, reset and history limit");
    }
    static void assertRoute(String action, String kind, int a, int b) {
        check(Arrays.equals(TracePanel.route(new ProtocolEvent(action, 7, kind, 0)), new int[]{a,b}), action + " route");
    }
    static void check(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
    static JButton button(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton b && b.getText().equals(name)) return b;
            if (c instanceof Container child) { JButton found = button(child, name); if (found != null) return found; }
        }
        return null;
    }
    static <T> T find(Container parent, Class<T> type) {
        for (Component c : parent.getComponents()) {
            if (type.isInstance(c)) return type.cast(c);
            if (c instanceof Container child) { T found = find(child, type); if (found != null) return found; }
        }
        return null;
    }
}