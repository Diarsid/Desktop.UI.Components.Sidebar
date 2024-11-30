package diarsid.desktop.ui.components.sidepane.api;

import java.io.Serializable;

import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Behavior.Type.INSTANT;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Behavior.Type.SMOOTH;

public class Moving implements Serializable {

    public final Sidepane.Behavior.Type type;
    public final Double seconds;

    Moving() {
        this.type = INSTANT;
        this.seconds = null;
    }

    Moving(double seconds) {
        this.type = SMOOTH;
        this.seconds = seconds;
    }

    public boolean is(Sidepane.Behavior.Type type) {
        return this.type.is(type);
    }
}
