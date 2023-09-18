package diarsid.desktop.ui.components.sidebar.impl.contextmenu;

import javafx.scene.control.Menu;

import diarsid.desktop.ui.components.sidebar.api.Sidebar;

public class SidebarItemSubMenu extends Menu {

    private final Sidebar.Item item;

    public SidebarItemSubMenu(Sidebar.Item item) {
        this.item = item;
        super.setText(item.name());

        item.itemsContextMenuItems().forEach(menuItem -> {
            menuItem.getStyleClass().add("sidebar-context-item-submenu-item");
            menuItem.getStyleClass().add("sidebar-context-item-submenu-item-" + menuItem.getText());
        });

        super.getItems().setAll(item.itemsContextMenuItems());

        super.getStyleClass().add("sidebar-context-menu");
        super.getStyleClass().add("sidebar-context-item-submenu");
        super.getStyleClass().add("sidebar-context-item-submenu-" + item.name());
        super.getStyleClass().add("sidebar-context-item-submenu-" + item.uuid());
    }
}
