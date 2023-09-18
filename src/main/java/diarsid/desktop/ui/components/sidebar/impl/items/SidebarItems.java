package diarsid.desktop.ui.components.sidebar.impl.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidebar.api.Sidebar;
import diarsid.desktop.ui.components.sidebar.impl.contextmenu.SidebarItemSubMenu;
import diarsid.support.strings.MultilineMessage;

public class SidebarItems {

    private static final Logger log = LoggerFactory.getLogger(SidebarItems.class);

    private final List<Sidebar.Item> items;
    private final List<Node> itemNodes;
    private final List<ItemHolder> itemHolders;

    private final ChangeListener<? super Number> itemNodeSizeChangeListener;
    private final Runnable hideContextMenuWhenItemInvoked;
    private final BiConsumer<MouseEvent, SidebarItemSubMenu> itemContextMenuInvoked;

    public SidebarItems(
            Supplier<List<Sidebar.Item>> initialItems,
            ChangeListener<? super Number> itemNodeSizeChangeListener,
            Runnable hideContextMenuWhenItemInvoked,
            BiConsumer<MouseEvent, SidebarItemSubMenu> itemContextMenuInvoked) {
        this.items = initialItems.get();
        this.itemNodes = new ArrayList<>();
        this.itemHolders = new ArrayList<>();

        this.itemNodeSizeChangeListener = itemNodeSizeChangeListener;
        this.hideContextMenuWhenItemInvoked = hideContextMenuWhenItemInvoked;
        this.itemContextMenuInvoked = itemContextMenuInvoked;

        this.toNodes();
    }

    private void toNodes() {
        this.itemNodes.clear();
        this.itemHolders.forEach(ItemHolder::clear);
        this.itemHolders.clear();

        Sidebar.Item item;
        for ( int i = 0; i < this.items.size(); i++ ) {
            item = this.items.get(i);
            this.itemNodes.add(item.node());
            this.itemHolders.add(new ItemHolder(
                    i,
                    item,
                    this.itemNodeSizeChangeListener,
                    this.hideContextMenuWhenItemInvoked,
                    this.itemContextMenuInvoked));
        }
    }

    public List<Node> nodes() {
        return this.itemNodes;
    }

    public List<Sidebar.Item> unmodifiableList() {
        return Collections.unmodifiableList(this.items);
    }

    public void apply(Consumer<List<Sidebar.Item>> mutation) {
        mutation.accept(this.items);
        MultilineMessage message = new MultilineMessage("[SIDEBAR ITEMS]", "   ");
        message.newLine().add("new version:");
        for ( Sidebar.Item item : this.items ) {
            message.newLine().indent().add("uuid:").add(item.uuid().toString()).add(", name:").add(item.name());
        }
        log.info(message.compose());
        this.toNodes();
    }
}
