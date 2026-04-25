// format: <src>|<dest>|<seq>|<isEnd>|<message>

import java.nio.charset.StandardCharsets;

public class UCPPacket {    // Wire format for packets exchanged through the UnreliableChannel.

    String src, dest, message;
    int seq;
    boolean isEnd;      // true for END control packets, false for data packets

    UCPPacket(String src, String dest, int seq, String message, boolean isEnd) {
        this.src = src; 
        this.dest = dest;
        this.seq = seq; 
        this.message = message; 
        this.isEnd = isEnd;
    }

    static UCPPacket end(String src, String dest) {     //builds END packet which signals the session is over
        return new UCPPacket(src, dest, -1, "END", true);
    }

    static UCPPacket fromBytes(byte[] data, int length) {       //parses raw data into a UCPPacket, splits on "|" with a limit of 5
        String[] p = new String(data, 0, length, StandardCharsets.UTF_8).split("\\|", 5);
        if (p.length < 5) throw new IllegalArgumentException("Malformed packet");
        String src = p[0];
        String dest = p[1];
        int seq = Integer.parseInt(p[2]);
        boolean isEnd = Boolean.parseBoolean(p[3]);
        String message = p[4];
        return new UCPPacket(src, dest, seq, message, isEnd);
    }

    byte[] toBytes() {      //serializes the packet to bytes so we can send it over the socket
        return (src + "|" + dest + "|" + seq + "|" + isEnd + "|" + message).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return isEnd ? "[END] " + src + " -> " + dest
                     : src + " -> " + dest + " | Seq=" + seq + " | Msg=\"" + message + "\"";
    }
}