package diarsid.desktop.ui.components.sidepane.impl;

import java.io.Serializable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.input.MouseEvent;

import diarsid.desktop.ui.components.sidepane.api.Sidepane;
import diarsid.desktop.ui.geometry.Rectangle;

class ViewSpy<T> implements Sidepane.Content.View<T>, Sidepane.Content.Storable {

    private final Sidepane.Content.View<T> view;
    private final Sidepane.Content.Storable storableView;
    private final Runnable onChange;

    public ViewSpy(Sidepane.Content.View<T> view, Runnable onChange) {
        this.view = view;
        this.storableView = (Sidepane.Content.Storable) view;
        this.onChange = onChange;
    }

    @Override
    public void initOnMount(Runnable invokeResize, Runnable hideContextMenu, BiConsumer<MouseEvent, Menu> onMenuInvoked) {
        this.view.initOnMount(invokeResize, hideContextMenu, onMenuInvoked);
    }

    @Override
    public void adoptChange(Rectangle.Side newSide) {
        this.view.adoptChange(newSide);
        this.onChange.run();
    }

    @Override
    public void adoptGivenChange(Consumer<T> mutation) {
        this.view.adoptGivenChange(mutation);
        this.onChange.run();
    }

    @Override
    public void pseudoClassStateChanged(PseudoClass pseudoClass, boolean active) {
        this.view.pseudoClassStateChanged(pseudoClass, active);
    }

    @Override
    public Serializable stateToStore() {
        return this.storableView.stateToStore();
    }

    @Override
    public void restoreStateFromStored(Serializable storedState) {
        this.storableView.restoreStateFromStored(storedState);
    }

    @Override
    public Node node() {
        return this.view.node();
    }
}
