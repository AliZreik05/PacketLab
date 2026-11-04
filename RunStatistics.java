import java.util.Objects;

/** Counts every observed transport event, independently of the truncated visual trace. */
public final class RunStatistics {
    public record Snapshot(long attempts,long retransmissions,long timeouts,long duplicates,long dataDrops,long ackDrops) {}
    private long attempts,retransmissions,timeouts,duplicates,dataDrops,ackDrops;
    private int lastSentSequence=-1;
    public synchronized void accept(String line) {
        ProtocolEvent event=ProtocolEvent.parse(line);
        if(event==null)return;
        switch(event.action()) {
            case "SEND" -> {
                attempts++;
                if(event.sequence()==lastSentSequence)retransmissions++;
                lastSentSequence=event.sequence();
            }
            case "TIMEOUT" -> timeouts++;
            case "DUPLICATE" -> duplicates++;
            case "DROP" -> {
                if(event.kind().equals("ACK"))ackDrops++;
                else if(event.kind().equals("DATA")||event.kind().equals("EOF"))dataDrops++;
            }
            default -> {}
        }
    }
    public synchronized Snapshot snapshot(){return new Snapshot(attempts,retransmissions,timeouts,duplicates,dataDrops,ackDrops);}
}