package diarsid.desktop.ui.components.sidepane.impl.areas;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.stage.Stage;

import diarsid.desktop.ui.geometry.Rectangle;
import diarsid.support.javafx.geometry.Screen;

public abstract class SidepaneStagePropertiesListener {

    protected final Screen screen;
    protected final ReadOnlyDoubleProperty x;
    protected final ReadOnlyDoubleProperty y;
    protected final ReadOnlyDoubleProperty width;
    protected final ReadOnlyDoubleProperty height;
    protected final ObjectProperty<Rectangle.Side> side;

    public SidepaneStagePropertiesListener(
            Screen screen,
            Stage stage,
            ObjectProperty<Rectangle.Side> side) {
        this(screen,
                stage.xProperty(),
                stage.yProperty(),
                stage.widthProperty(),
                stage.heightProperty(),
                side);
    }

    public SidepaneStagePropertiesListener(
            Screen screen,
            ReadOnlyDoubleProperty x,
            ReadOnlyDoubleProperty y,
            ReadOnlyDoubleProperty width,
            ReadOnlyDoubleProperty height,
            ObjectProperty<Rectangle.Side> side) {
        this.screen = screen;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.side = side;

        this.x.addListener((prop, oldX, newX) -> {
            double oldY = this.y.get();
            this.onStageAnchorChange((double) oldX, (double) newX, oldY, oldY);
        });

        this.y.addListener((prop, oldY, newY) -> {
            double oldX = this.x.get();
            this.onStageAnchorChange(oldX, oldX, (double) oldY, (double) oldY);
        });

        this.width.addListener((prop, oldWidth, newWidth) -> {
            double oldHeight = this.height.get();
            this.onStageSizeChange((double) oldWidth, (double) newWidth, oldHeight, oldHeight);
        });

        this.height.addListener((prop, oldHeight, newHeight) -> {
            double oldWidth = this.width.get();
            this.onStageSizeChange(oldWidth, oldWidth, (double) oldHeight, (double) newHeight);
        });

        this.side.addListener((prop, oldSize, newSize) -> {
            this.onSideChange(oldSize, newSize);
        });
    }

    protected void onStageSizeChange(double oldWidth, double newWidth, double oldHeight, double newHeight) {

    }

    protected void onStageAnchorChange(double oldX, double newX, double oldY, double newY) {

    }

    protected void onSideChange(Rectangle.Side oldSide, Rectangle.Side newSide) {

    }
}
