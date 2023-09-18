package diarsid.desktop.ui.components.sidebar.impl.contextmenu;

import java.util.function.Consumer;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import diarsid.desktop.ui.components.sidebar.api.Sidebar;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.BOTTOM_CENTER;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.LEFT_CENTER;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.RIGHT_CENTER;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.TOP_CENTER;

public class SidebarContextMenu extends ContextMenu {

    private volatile SidebarItemSubMenu selectedItemSubmenu;

    public SidebarContextMenu(Consumer<Sidebar.Position.Relative> setRelativePosition) {
        MenuItem pinUnpin = new MenuItem("pin / unpin");
        MenuItem settings = new MenuItem("settings");
        Menu setPosition = new Menu("set position...");

        MenuItem topCenter = new MenuItem("top center");
        MenuItem rightCenter = new MenuItem("right center");
        MenuItem bottomCenter = new MenuItem("bottom center");
        MenuItem leftCenter = new MenuItem("left center");

        topCenter.setOnAction(event -> {
            setRelativePosition.accept(TOP_CENTER);
        });

        rightCenter.setOnAction(event -> {
            setRelativePosition.accept(RIGHT_CENTER);
        });

        bottomCenter.setOnAction(event -> {
            setRelativePosition.accept(BOTTOM_CENTER);
        });

        leftCenter.setOnAction(event -> {
            setRelativePosition.accept(LEFT_CENTER);
        });

        setPosition.getItems().setAll(topCenter, rightCenter, bottomCenter, leftCenter);

        super.getItems().setAll(pinUnpin, settings, setPosition);

        super.getItems().forEach(menuItem -> {
            menuItem.getStyleClass().add("sidebar-context-menu-item");
        });

        super.getStyleClass().add("sidebar-context-menu");
    }

    public void addSelectedItemSubmenu(SidebarItemSubMenu itemSubmenu) {
        this.removeSelectedItemSubmenu();
        this.selectedItemSubmenu = itemSubmenu;
        if ( ! itemSubmenu.getItems().isEmpty() ) {
            super.getItems().add(itemSubmenu);
        }
    }

    public void removeSelectedItemSubmenu() {
        super.getItems().remove(this.selectedItemSubmenu);
        this.selectedItemSubmenu = null;
    }
}
