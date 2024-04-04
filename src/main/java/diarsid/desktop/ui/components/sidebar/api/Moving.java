package diarsid.desktop.ui.components.sidebar.api;

import java.io.Serializable;
import java.util.Objects;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Behavior.Type.INSTANT;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Behavior.Type.SMOOTH;

public class Moving implements Serializable {

    public final Sidebar.Behavior.Type type;
    public final Double seconds;

    Moving() {
        this.type = INSTANT;
        this.seconds = null;
    }

    Moving(double seconds) {
        this.type = SMOOTH;
        this.seconds = seconds;
    }

    public boolean is(Sidebar.Behavior.Type type) {
        return this.type.is(type);
    }
}
