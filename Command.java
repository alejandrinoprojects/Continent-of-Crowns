import java.util.List;

public abstract class Command {
    protected List<Unit> units;

    public Command(List<Unit> units) {
        this.units = units;
    }

    public abstract void execute();
}