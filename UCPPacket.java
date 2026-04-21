// UCPPacket.java
// Wire format (UTF-8): <src>|<dest>|<seq>|<isEnd>|<message>

import java.nio.charset.StandardCharsets;

public class UCPPacket {

    public final String  src, dest, message;
    public final int     seq;
    public final boolean isEnd;

    private UCPPacket(String src, String dest, int seq, String message, boolean isEnd) {
        this.src = src; this.dest = dest;
        this.seq = seq; this.message = message; this.isEnd = isEnd;
    }

    public static UCPPacket data(String src, String dest, int seq, String message) {
        return new UCPPacket(src, dest, seq, message, false);
    }

    public static UCPPacket end(String src, String dest) {
        return new UCPPacket(src, dest, -1, "END", true);
    }

    public byte[] toBytes() {
        return (src + "|" + dest + "|" + seq + "|" + isEnd + "|" + message)
                .getBytes(StandardCharsets.UTF_8);
    }

    public static UCPPacket fromBytes(byte[] data, int length) {
        String[] p = new String(data, 0, length, StandardCharsets.UTF_8).split("\\|", 5);
        if (p.length < 5) throw new IllegalArgumentException("Malformed packet");
        return new UCPPacket(p[0], p[1], Integer.parseInt(p[2]),
                             p[4], Boolean.parseBoolean(p[3]));
    }

    @Override
    public String toString() {
        return isEnd ? "[END] " + src + " -> " + dest
                     : src + " -> " + dest + " | Seq=" + seq + " | Msg=\"" + message + "\"";
    }
}