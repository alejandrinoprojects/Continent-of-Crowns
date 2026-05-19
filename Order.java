public class Order {
    public final CommandType commandType;
    public final int targetQ;
    public final int targetR;

    public Order(CommandType commandType, int targetQ, int targetR) {
        this.commandType = commandType;
        this.targetQ = targetQ;
        this.targetR = targetR;
    }
}