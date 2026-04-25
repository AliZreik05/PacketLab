// format: <src>|<dest>|<seq>|<isEnd>|<message>

import java.nio.charset.StandardCharsets;

public class UCPPacket {

    String src, dest, message;
    int seq;
    boolean isEnd;

    UCPPacket(String src, String dest, int seq, String message, boolean isEnd) {
        this.src = src; 
        this.dest = dest;
        this.seq = seq; 
        this.message = message; 
        this.isEnd = isEnd;
    }

    static UCPPacket end(String src, String dest) {
        return new UCPPacket(src, dest, -1, "END", true);
    }

    static UCPPacket fromBytes(byte[] data, int length) {
        String[] p = new String(data, 0, length, StandardCharsets.UTF_8).split("\\|", 5);
        if (p.length < 5) throw new IllegalArgumentException("Malformed packet");
        String src = p[0];
        String dest = p[1];
        int seq = Integer.parseInt(p[2]);
        boolean isEnd = Boolean.parseBoolean(p[3]);
        String message = p[4];
        return new UCPPacket(src, dest, seq, message, isEnd);
    }

    byte[] toBytes() {
        return (src + "|" + dest + "|" + seq + "|" + isEnd + "|" + message).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return isEnd ? "[END] " + src + " -> " + dest
                     : src + " -> " + dest + " | Seq=" + seq + " | Msg=\"" + message + "\"";
    }
}