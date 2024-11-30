package diarsid.desktop.ui.components.sidepane.impl.areas;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.stage.Stage;

import diarsid.desktop.ui.geometry.Anchor;
import diarsid.desktop.ui.geometry.MutableRectangle;
import diarsid.desktop.ui.geometry.RealMutableRectangle;
import diarsid.desktop.ui.geometry.Rectangle;
import diarsid.desktop.ui.geometry.Size;
import diarsid.support.javafx.geometry.Screen;

import static diarsid.desktop.ui.geometry.MutableAnchor.mutableAnchor;
import static diarsid.desktop.ui.geometry.MutableSize.mutableSize;
import static diarsid.desktop.ui.geometry.Rectangle.Side.BOTTOM;
import static diarsid.desktop.ui.geometry.Rectangle.Side.LEFT;
import static diarsid.desktop.ui.geometry.Rectangle.Side.RIGHT;
import static diarsid.desktop.ui.geometry.Rectangle.Side.TOP;

public class SidepaneAreaWhenHidden extends SidepaneStagePropertiesListener implements Rectangle  {

    private final MutableRectangle hiddenArea;
    private final double screenWidth;
    private final double screenHeight;
    private final Insets insets;

    public SidepaneAreaWhenHidden(Screen screen, Stage stage, ObjectProperty<Side> side) {
        super(screen, stage, side);

        this.screenWidth = super.screen.width();
        this.screenHeight = super.screen.height();

        this.insets = new Insets(-1, +1, +1, -1);

        double initialX;
        double initialY;
        double initialWidth = super.width.get();
        double initialHeight = super.height.get();

        Side currentSide = super.side.get();
        switch ( currentSide ) {
            case TOP:
                initialX = super.x.get();
                initialY = 0 - initialHeight + this.insetOf(TOP);
                break;
            case LEFT:
                initialX = 0 - initialWidth + this.insetOf(LEFT);
                initialY = super.y.get();
                break;
            case RIGHT:
                initialX = this.screenWidth + this.insetOf(RIGHT);
                initialY = super.y.get();
                break;
            case BOTTOM:
                initialX = super.x.get();
                initialY = this.screenHeight + this.insetOf(BOTTOM);
                break;
            default:
                throw currentSide.unsupported();
        }

        this.hiddenArea = new RealMutableRectangle(
                mutableAnchor(initialX, initialY),
                mutableSize(initialWidth, initialHeight));
    }

    public double insetOf(Side side) {
        switch ( side ) {
            case TOP: return this.insets.getTop();
            case LEFT: return this.insets.getLeft();
            case RIGHT: return this.insets.getRight();
            case BOTTOM: return this.insets.getBottom();
            default:
                throw side.unsupported();
        }
    }

    @Override
    protected void onStageAnchorChange(double oldX, double newX, double oldY, double newY) {
        Side side = super.side.get();
        switch ( side ) {
            case TOP:
                this.hiddenArea.anchor().set(newX, 0 - super.height.get() + this.insetOf(TOP));
                break;
            case LEFT:
                this.hiddenArea.anchor().set(0 - super.width.get() + this.insetOf(LEFT), newY);
                break;
            case RIGHT:
                this.hiddenArea.anchor().set(this.screenWidth + this.insetOf(RIGHT), newY);
                break;
            case BOTTOM:
                this.hiddenArea.anchor().set(newX, this.screenHeight + this.insetOf(BOTTOM));
                break;
            default:
                throw side.unsupported();
        }
    }

    @Override
    protected void onStageSizeChange(double oldWidth, double newWidth, double oldHeight, double newHeight) {
        Side side = super.side.get();

        this.hiddenArea.size().set(newWidth, newHeight);

        switch ( side ) {
            case TOP:
                this.hiddenArea.anchor().set(super.x.get(), 0 - newHeight + this.insetOf(TOP));
                break;
            case LEFT:
                this.hiddenArea.anchor().set(0 - newWidth + this.insetOf(LEFT), super.y.get());
                break;
            case RIGHT:
                this.hiddenArea.anchor().set(this.screenWidth + this.insetOf(RIGHT), super.y.get());
                break;
            case BOTTOM:
                this.hiddenArea.anchor().set(super.x.get(), this.screenHeight + this.insetOf(BOTTOM));
                break;
            default:
                throw side.unsupported();
        }
    }

    @Override
    public Anchor anchor() {
        return this.hiddenArea.anchor();
    }

    @Override
    public Size size() {
        return this.hiddenArea.size();
    }
}
