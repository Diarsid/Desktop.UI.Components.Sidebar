package diarsid.desktop.ui.components.sidebar.impl.items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import diarsid.desktop.ui.components.sidebar.api.Item;
import diarsid.support.objects.references.Possible;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import static javafx.scene.input.MouseEvent.MOUSE_PRESSED;
import static javafx.scene.input.MouseEvent.MOUSE_RELEASED;

import static diarsid.support.objects.references.References.simplePossibleButEmpty;

public class ItemWithIcon implements Item {

    private final UUID uuid;
    private final String name;
    private final Supplier<String> action;

    private final ImageView icon;
    private final ColorAdjust brighter;
    private final ColorAdjust darker;
    private final Possible<Effect> cssEffect;
    private boolean isHovered;
    private final Label iconLabel;
    public final transient List<Process> process;

    public ItemWithIcon(
            UUID uuid,
            String name,
            Supplier<String> action,
            Image image,
            IconsSettings iconsSettings) {
        this.uuid = uuid;
        this.name = name;
        this.action = action;

        this.brighter = new ColorAdjust();
        this.darker = new ColorAdjust();

        iconsSettings.brightnessChange.ifPresent(brightness -> {
            this.brighter.setBrightness(brightness.onHoverBrighter);
            this.darker.setBrightness(brightness.onPressedDarker);
        });

        this.icon = new ImageView();
        double iconSize = iconsSettings.size;
        this.icon.setFitHeight(iconSize);
        this.icon.setFitHeight(iconSize);
        this.icon.setPreserveRatio(true);
        this.icon.setImage(image);
        this.icon.getStyleClass().add("sidepane-item-icon");
        this.iconLabel = new Label();
        this.iconLabel.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        this.iconLabel.setGraphic(this.icon);
        this.iconLabel.setTooltip(new Tooltip(this.name));

        this.cssEffect = simplePossibleButEmpty();
        this.isHovered = false;

        this.iconLabel.hoverProperty().addListener(((observable, oldValue, newValue) -> {
            this.getInitialEffectAtFirstRun();
            if ( (! oldValue) && newValue ) {
                this.icon.setEffect(brighter);
            }
            else {
                this.icon.setEffect(this.cssEffect.or(null));
            }
        }));

        this.iconLabel.setOnMouseMoved(event -> {
            Effect effect = this.icon.getEffect();
            if ( isNull(effect) ) {
                this.icon.setEffect(brighter);
            }
        });

        this.iconLabel.addEventHandler(MOUSE_PRESSED, event -> {
            this.icon.setEffect(darker);
        });

        this.iconLabel.addEventHandler(MOUSE_RELEASED, event -> {
            this.icon.setEffect(brighter);
        });

        this.process = new ArrayList<>();
    }

    private synchronized void getInitialEffectAtFirstRun() {
        if ( ! this.isHovered ) {
            this.isHovered = true;
            Effect initialEffect =  this.icon.getEffect();
            if ( nonNull(initialEffect) ) {
                this.cssEffect.resetTo(initialEffect);
                this.brighter.setInput(initialEffect);
                this.darker.setInput(initialEffect);
            }
        }
    }

    @Override
    public Node node() {
        return this.iconLabel;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public UUID uuid() {
        return this.uuid;
    }

    @Override
    public String run() {
        return this.action.get();
    }
}
