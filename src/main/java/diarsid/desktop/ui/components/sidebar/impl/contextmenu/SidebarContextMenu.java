package diarsid.desktop.ui.components.sidebar.impl.contextmenu;

import java.util.function.Consumer;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import diarsid.desktop.ui.components.sidebar.api.Sidebar;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.BOTTOM_CENTER;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.LEFT_CENTER;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.RIGHT_CENTER;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.TOP_CENTER;
import static diarsid.support.javafx.PropertiesUtil.revert;

public class SidebarContextMenu extends ContextMenu {

    private final BooleanProperty pin;
    private volatile SidebarItemSubMenu selectedItemSubmenu;

    public SidebarContextMenu(
            BooleanProperty pin,
            Consumer<Sidebar.Position.Relative> setRelativePosition) {
        this.pin = pin;
        MenuItem pinUnpin = new MenuItem(this.pinOrUnpin());
        MenuItem settings = new MenuItem("settings");
        Menu centerAt = new Menu("center at...");

        MenuItem topCenter = new MenuItem("top");
        MenuItem rightCenter = new MenuItem("right");
        MenuItem bottomCenter = new MenuItem("bottom");
        MenuItem leftCenter = new MenuItem("left");

        this.pin.addListener((prop, oldValue, newValue) -> {
            pinUnpin.setText(this.pinOrUnpin());
            if ( newValue ) {
                super.getItems().remove(centerAt);
            }
            else {
                super.getItems().add(centerAt);
            }
        });

        pinUnpin.setOnAction(event -> {
            revert(this.pin);
        });

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

        centerAt.getItems().setAll(topCenter, rightCenter, bottomCenter, leftCenter);

        super.getItems().setAll(pinUnpin, settings);

        if ( ! this.pin.get() ) {
            super.getItems().add(centerAt);
        }

        super.getItems().forEach(menuItem -> {
            menuItem.getStyleClass().add("sidebar-context-menu-item");
        });

        super.getStyleClass().add("sidebar-context-menu");
    }

    private String pinOrUnpin() {
        return this.pin.get() ? "unpin" : "pin";
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
