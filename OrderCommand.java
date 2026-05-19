import java.util.List;

public class OrderCommand extends Command {
    Order order;

    public OrderCommand(List<Unit> units, Order order) {
        super(units);
        this.order = order;
    }

    @Override
    public void execute() {
        if (order.commandType == CommandType.HOLD_POSITION) {
            for (Unit u : units) {
                u.applyHoldPosition();
            }
            return;
        }

        for (Unit u : units) {
            u.applyNewOrder(new Order(order.commandType, order.targetQ, order.targetR));
        }
    }
}