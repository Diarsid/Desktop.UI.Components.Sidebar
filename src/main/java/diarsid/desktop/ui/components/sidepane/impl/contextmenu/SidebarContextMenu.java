package diarsid.desktop.ui.components.sidepane.impl.contextmenu;

import java.util.function.Consumer;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import diarsid.desktop.ui.components.sidepane.api.Sidepane;

import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Position.Relative.BOTTOM_CENTER;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Position.Relative.LEFT_CENTER;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Position.Relative.RIGHT_CENTER;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Position.Relative.TOP_CENTER;
import static diarsid.support.javafx.PropertiesUtil.revert;

public class SidebarContextMenu extends ContextMenu {

    private final BooleanProperty pin;
    private volatile Menu additionalSubmenu;

    public SidebarContextMenu(
            BooleanProperty pin,
            Consumer<Sidepane.Position.Relative> setRelativePosition) {
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
            menuItem.getStyleClass().add("sidepane-context-menu-item");
        });

        super.getStyleClass().add("sidepane-context-menu");
    }

    private String pinOrUnpin() {
        return this.pin.get() ? "unpin" : "pin";
    }

    public void addSubmenu(Menu additionalSubmenu) {
        this.removeAdditionalSubmenu();
        this.additionalSubmenu = additionalSubmenu;
        if ( ! additionalSubmenu.getItems().isEmpty() ) {
            super.getItems().add(additionalSubmenu);
        }
    }

    public void removeAdditionalSubmenu() {
        super.getItems().remove(this.additionalSubmenu);
        this.additionalSubmenu = null;
    }
}
