package diarsid.desktop.ui.components.sidebar.impl.items;

import javafx.scene.control.Menu;

import diarsid.desktop.ui.components.sidebar.api.Item;

class SidebarItemSubMenu extends Menu {

    private final Item item;

    SidebarItemSubMenu(Item item) {
        this.item = item;
        super.setText(item.name());

        item.itemContextMenuItems().forEach(menuItem -> {
            menuItem.getStyleClass().add("sidepane-context-item-submenu-item");
            menuItem.getStyleClass().add("sidepane-context-item-submenu-item-" + menuItem.getText());
        });

        super.getItems().setAll(item.itemContextMenuItems());

        super.getStyleClass().add("sidepane-context-menu");
        super.getStyleClass().add("sidepane-context-item-submenu");
        super.getStyleClass().add("sidepane-context-item-submenu-" + item.name());
        super.getStyleClass().add("sidepane-context-item-submenu-" + item.uuid());
    }
}
