/** Small, payload-free event format shared by the protocol and visualizer. */
public record ProtocolEvent(String action, int sequence, String kind, int delay, long occurredAt) {
    public ProtocolEvent(String action, int sequence, String kind, int delay) {
        this(action,sequence,kind,delay,0);
    }
    private static long lastTimestamp;
    private static synchronized long timestamp() {
        java.time.Instant now = java.time.Instant.now();
        long clock = now.getEpochSecond()*1_000_000_000L + now.getNano();
        return lastTimestamp = Math.max(clock,lastTimestamp+1);
    }
    // Carry the last observed stamp over the relay so causal order survives clock skew.
    public static synchronized void observe(long stamp) { lastTimestamp=Math.max(lastTimestamp,stamp); }
    public static synchronized long currentStamp() { return lastTimestamp; }
    public static void emit(String action, int sequence, String kind, int delay) {
        System.out.printf("[EVENT] %s|%d|%s|%d|%d%n", action, sequence, kind, delay, timestamp());
    }
    public static ProtocolEvent parse(String line) {
        int start = line.indexOf("[EVENT] ");
        if (start < 0) return null;
        String[] fields = line.substring(start + 8).strip().split("\\|", -1);
        try {
            if (fields.length != 4 && fields.length != 5) return null;
            return new ProtocolEvent(fields[0], Integer.parseInt(fields[1]), fields[2], Integer.parseInt(fields[3]), fields.length == 5 ? Long.parseLong(fields[4]) : 0);
        } catch (NumberFormatException e) { return null; }
    }
    public String label() { return kind + " " + sequence; }
    public String explanation() {
        String item = label();
        return switch (action) {
            case "SEND" -> "The sender sends " + item + ". It waits up to 500 ms for the matching acknowledgement (ACK).";
            case "TIMEOUT" -> "No matching ACK for packet " + sequence + " arrived within 500 ms. The sender will retry. It cannot tell whether data or its ACK was lost or delayed.";
            case "DROP" -> "The channel dropped " + item + ". " + (kind.equals("ACK")
                    ? "The receiver sent an acknowledgement, but this copy will not reach the sender."
                    : "This copy will not reach the receiver. The sender learns only by waiting for an ACK.");
            case "DELAY" -> "The channel schedules " + item + " for forwarding after " + delay + " ms.";
            case "FORWARD" -> "The channel forwards " + item + " toward the " + (kind.equals("ACK") ? "sender" : "receiver") + ".";
            case "RECEIVE" -> "The receiver accepts " + item + " in order. " + (kind.equals("EOF")
                    ? "EOF marks the end of this stream; it contains no file bytes."
                    : "Its payload is delivered once. DATA can contain file bytes or the legacy filename metadata.");
            case "DUPLICATE" -> "The receiver already accepted packet " + sequence + ". It discards the duplicate payload and sends ACK " + sequence + " again.";
            case "ACK_SEND" -> "The receiver sends ACK " + sequence + " back through the same unreliable channel. This acknowledgement can also be lost or delayed.";
            case "ACK_RECEIVE" -> "The sender received the matching ACK " + sequence + ". It can now advance to the next packet.";
            default -> item;
        };
    }
}
