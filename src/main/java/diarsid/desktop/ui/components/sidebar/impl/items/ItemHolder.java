package diarsid.desktop.ui.components.sidebar.impl.items;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidebar.api.Item;
import diarsid.support.objects.StatefulClearable;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.time.LocalDateTime.now;
import static java.util.Objects.nonNull;
import static java.util.concurrent.CompletableFuture.runAsync;

import static javafx.scene.input.MouseButton.PRIMARY;
import static javafx.scene.input.MouseEvent.MOUSE_DRAGGED;
import static javafx.scene.input.MouseEvent.MOUSE_PRESSED;
import static javafx.scene.input.MouseEvent.MOUSE_RELEASED;

class ItemHolder implements StatefulClearable {

    private static final Logger log = LoggerFactory.getLogger(ItemHolder.class);

    private final int i;
    private final Item item;
    private final SidebarItemSubMenu subMenu;

    private final ChangeListener<? super Number> itemNodeSizeChangeListener;
    private final Runnable hideContextMenuWhenItemInvoked;
    private final BiConsumer<MouseEvent, SidebarItemSubMenu> itemContextMenuInvoked;

    private final EventHandler<? super MouseEvent> onMousePressed;
    private final EventHandler<? super MouseEvent> onMouseDragged;
    private final EventHandler<? super MouseEvent> onMouseReleased;

    private final AtomicReference<EventType<MouseEvent>> lastMouseEvent;

    ItemHolder(
            int i,
            Item item,
            ChangeListener<? super Number> itemNodeSizeChangeListener,
            Runnable hideContextMenuWhenItemInvoked,
            BiConsumer<MouseEvent, SidebarItemSubMenu> itemContextMenuInvoked) {
        this.i = i;
        this.item = item;

        this.itemNodeSizeChangeListener = itemNodeSizeChangeListener;
        this.hideContextMenuWhenItemInvoked = hideContextMenuWhenItemInvoked;
        this.itemContextMenuInvoked = itemContextMenuInvoked;

        this.subMenu = new SidebarItemSubMenu(item);

        this.lastMouseEvent = new AtomicReference<>();

        this.onMousePressed = (event) -> {
            if ( PRIMARY.equals(event.getButton()) ) {
                this.hideContextMenuWhenItemInvoked.run();
                this.lastMouseEvent.set(MOUSE_PRESSED);
            }
            else if ( event.isSecondaryButtonDown() ) {
                this.itemContextMenuInvoked.accept(event, this.subMenu);
                event.consume();
            }
        };

        this.onMouseDragged = (event) -> {
            if ( PRIMARY.equals(event.getButton()) ) {
                this.hideContextMenuWhenItemInvoked.run();
                this.lastMouseEvent.set(MOUSE_DRAGGED);
            }
        };

        this.onMouseReleased = (event) -> {
            try {
                if ( PRIMARY.equals(event.getButton()) ) {
                    var lastMouseEventNow = this.lastMouseEvent.get();
                    if ( nonNull(lastMouseEventNow) && lastMouseEventNow.equals(MOUSE_PRESSED) ) {
                        this.invokeSafely();
                    }
                }
            }
            finally {
                this.lastMouseEvent.set(null);
            }
        };

        Node itemNode = item.node();

        itemNode.getStyleClass().addAll(
                "sidepane-item",
                "sidepane-item-" + i,
                "sidepane-item-" + item.name().toLowerCase(),
                "sidepane-item-" + item.uuid().toString());

        if ( itemNode instanceof Region) {
            Region itemRegion = (Region) itemNode;
            itemRegion.widthProperty().addListener(this.itemNodeSizeChangeListener);
            itemRegion.heightProperty().addListener(this.itemNodeSizeChangeListener);
        }

        itemNode.addEventHandler(MOUSE_PRESSED, this.onMousePressed);
        itemNode.addEventHandler(MOUSE_DRAGGED, this.onMouseDragged);
        itemNode.addEventHandler(MOUSE_RELEASED, this.onMouseReleased);
    }

    @Override
    public void clear() {
        Node itemNode = this.item.node();

        itemNode.getStyleClass().removeIf(styleClass -> styleClass.startsWith("sidepane-item"));

        if ( itemNode instanceof Region) {
            Region itemRegion = (Region) itemNode;
            itemRegion.widthProperty().removeListener(this.itemNodeSizeChangeListener);
            itemRegion.heightProperty().removeListener(this.itemNodeSizeChangeListener);
        }

        itemNode.removeEventHandler(MOUSE_PRESSED, this.onMousePressed);
        itemNode.removeEventHandler(MOUSE_DRAGGED, this.onMouseDragged);
        itemNode.removeEventHandler(MOUSE_RELEASED, this.onMouseReleased);
    }

    private void invokeSafely() {
        runAsync(() -> {
            String originThreadName = currentThread().getName();
            currentThread().setName(format("%s[%s].%s", Item.class.getCanonicalName(), this.item.name(), now()));
            try {
                this.item.run();
                log.info(format("[SIDEBAR ITEM RUN] uuid:%s, name:%s - OK", this.item.uuid(), this.item.name()));
            }
            catch (Throwable t) {
                try {
                    item.onThrownInRun(t);
                    log.info(format("[SIDEBAR ITEM RUN] uuid:%s, name:%s - exception handled", this.item.uuid(), this.item.name()));
                }
                catch (Throwable t2) {
                    log.error("[SIDEBAR ITEM RUN] - exception on callback:", t);
                }
            }
            finally {
                currentThread().setName(originThreadName);
            }
        });
    }
}
