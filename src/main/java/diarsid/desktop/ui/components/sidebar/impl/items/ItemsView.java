package diarsid.desktop.ui.components.sidebar.impl.items;

import java.io.Serializable;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import diarsid.desktop.ui.components.sidebar.api.Item;
import diarsid.desktop.ui.components.sidebar.api.Items;
import diarsid.desktop.ui.components.sidepane.api.Sidepane;
import diarsid.support.javafx.geometry.Screen;

import static javafx.css.PseudoClass.getPseudoClass;

import static diarsid.desktop.ui.components.sidebar.api.Items.Alignment.PARALLEL_TO_SIDE;
import static diarsid.desktop.ui.geometry.Rectangle.Side.Orientation.HORIZONTAL;
import static diarsid.desktop.ui.geometry.Rectangle.Side.Orientation.VERTICAL;

public class ItemsView implements
        Sidepane.Content.View<List<Item>>,
        Sidepane.Content.Storable {

    private static final PseudoClass CSS_ITEMS_VERTICAL = getPseudoClass(VERTICAL.name().toLowerCase());
    private static final PseudoClass CSS_ITEMS_HORIZONTAL = getPseudoClass(HORIZONTAL.name().toLowerCase());

    private final Supplier<List<Item>> initialItems;

    private Items.Alignment itemsAlignment;
    private SidebarItems items;
    private HBox itemsViewHorizontal;
    private VBox itemsViewVertical;
    private Pane content;

    public ItemsView(
            Items.Alignment itemsAlignment,
            Supplier<List<Item>> initialItems) {
        this.itemsAlignment = itemsAlignment;
        this.initialItems = initialItems;
    }

    @Override
    public void initOnMount(
            Runnable invokeResize,
            Runnable hideContextMenu,
            BiConsumer<MouseEvent, Menu> onMenuInvoked) {
        this.items = new SidebarItems(
                initialItems,
                (prop, oldV, newV) -> {
                    invokeResize.run();
                },
                hideContextMenu,
                (event, itemSubMenu) -> {
                    onMenuInvoked.accept(event, itemSubMenu);
                });

        this.itemsViewHorizontal = new HBox();
        this.itemsViewHorizontal.getStyleClass().add("sidepane-items");
        this.itemsViewHorizontal.pseudoClassStateChanged(CSS_ITEMS_VERTICAL, false);
        this.itemsViewHorizontal.pseudoClassStateChanged(CSS_ITEMS_HORIZONTAL, true);

        this.itemsViewVertical = new VBox();
        this.itemsViewVertical.getStyleClass().add("sidepane-items");
        this.itemsViewVertical.pseudoClassStateChanged(CSS_ITEMS_VERTICAL, true);
        this.itemsViewVertical.pseudoClassStateChanged(CSS_ITEMS_HORIZONTAL, false);
    }

    @Override
    public void adoptChange(Screen.Side newSide) {
        switch ( newSide.orientation ) {
            case VERTICAL:
                if ( this.itemsAlignment.is(PARALLEL_TO_SIDE) ) {
                    this.content = this.itemsViewVertical;
                }
                else {
                    this.content = this.itemsViewHorizontal;
                }
                this.content.getChildren().addAll(this.items.nodes());

                break;
            case HORIZONTAL:
                if ( this.itemsAlignment.is(PARALLEL_TO_SIDE) ) {
                    this.content = this.itemsViewHorizontal;
                }
                else {
                    this.content = this.itemsViewVertical;
                }
                this.content.getChildren().addAll(this.items.nodes());

                break;
            default:
                throw newSide.orientation.unsupported();
        }
    }

    @Override
    public void adoptGivenChange(Consumer<List<Item>> mutation) {
        this.items.apply(mutation);

        List<Node> currentItemNodes = this.content.getChildren();
        currentItemNodes.clear();
        currentItemNodes.addAll(this.items.nodes());
    }

    @Override
    public void pseudoClassStateChanged(PseudoClass pseudoClass, boolean active) {
        this.itemsViewHorizontal.pseudoClassStateChanged(pseudoClass, active);
        this.itemsViewVertical.pseudoClassStateChanged(pseudoClass, active);
    }

    @Override
    public Node node() {
        return this.content;
    }

    @Override
    public Serializable stateToStore() {
        return this.itemsAlignment;
    }

    @Override
    public void restoreStateFromStored(Serializable storedState) {
        this.itemsAlignment = (Items.Alignment) storedState;
    }
}
