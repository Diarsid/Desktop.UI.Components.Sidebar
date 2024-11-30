package diarsid.desktop.ui.components.sidepane.impl;

import java.util.function.Consumer;

class ContentChange<T> extends QueuedAction {

    final Consumer<T> mutation;

    public ContentChange(Consumer<T> mutation) {
        this.mutation = mutation;
    }
}
