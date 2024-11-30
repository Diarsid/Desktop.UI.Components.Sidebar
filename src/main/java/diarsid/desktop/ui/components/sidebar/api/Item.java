package diarsid.desktop.ui.components.sidebar.api;

import java.util.List;

import javafx.scene.control.MenuItem;

import diarsid.support.javafx.components.Visible;
import diarsid.support.model.Named;
import diarsid.support.model.Unique;

import static java.util.Collections.emptyList;

public interface Item extends Unique, Named, Visible {

    default List<MenuItem> itemContextMenuItems() {
        return emptyList();
    }

    default void onThrownInRun(Throwable t) {
        // for override
    }

    String run(); // runs logic and returns error message, if any. If run is successful, returns null;
}
