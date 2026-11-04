/** Repeatable teaching scenarios; presets also fix delay below the ACK timeout. */
public enum Experiment {
    RANDOM("Random loss (custom)", "Custom experiment: packets and ACKs may be dropped randomly."),
    NORMAL("Successful delivery", "No loss: follow data to the receiver and its ACK back to the sender."),
    DROP_DATA("Lose a data packet", "The first data packet is dropped once. Watch the sender time out, retry, and complete."),
    DROP_ACK("Lose an ACK", "The first ACK is dropped once. Watch the retry arrive as a duplicate and get acknowledged again.");
    private final String title, description;
    Experiment(String title,String description) { this.title=title;this.description=description; }
    @Override public String toString(){return title;}
    public String description(){return description;}
    public boolean custom(){return this==RANDOM;}
    public boolean drops(String kind){return this==DROP_ACK ? kind.equals("ACK") : this==DROP_DATA && (kind.equals("DATA")||kind.equals("EOF"));}
}