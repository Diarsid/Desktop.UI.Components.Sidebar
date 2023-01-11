package diarsid.desktop.ui.components.sidebar.impl;

import javafx.beans.property.ObjectProperty;
import javafx.stage.Stage;

import diarsid.desktop.ui.geometry.Anchor;
import diarsid.desktop.ui.geometry.MutableRectangle;
import diarsid.desktop.ui.geometry.RealMutableRectangle;
import diarsid.desktop.ui.geometry.Rectangle;
import diarsid.desktop.ui.geometry.Size;
import diarsid.support.javafx.geometry.Screen;

import static diarsid.desktop.ui.geometry.MutableAnchor.mutableAnchor;
import static diarsid.desktop.ui.geometry.MutableSize.mutableSize;

public class SidebarAreaForTouch extends SidebarStagePropertiesListener implements Rectangle {

    private final MutableRectangle touchArea;
    private final double screenWidthMinusOne;
    private final double screenHeightMinusOne;

    public SidebarAreaForTouch(Screen screen, Stage stage, ObjectProperty<Side> side) {
        super(screen, stage, side);

        this.screenWidthMinusOne = super.screen.width() - 1;
        this.screenHeightMinusOne = super.screen.height() - 1;

        double initialX;
        double initialY;
        double initialWidth;
        double initialHeight;

        Side currentSide = super.side.get();
        switch ( currentSide ) {
            case TOP:
                initialX = super.x.get();
                initialY = 0;
                initialWidth = super.width.get();
                initialHeight = 1;
                break;
            case LEFT:
                initialX = 0;
                initialY = super.y.get();
                initialWidth = 1;
                initialHeight = super.height.get();
                break;
            case RIGHT:
                initialX = this.screenWidthMinusOne;
                initialY = super.y.get();
                initialWidth = 1;
                initialHeight = super.height.get();
                break;
            case BOTTOM:
                initialX = super.x.get();
                initialY = this.screenHeightMinusOne;
                initialWidth = super.width.get();
                initialHeight = 1;
                break;
            default:
                throw currentSide.unsupported();
        }

        this.touchArea = new RealMutableRectangle(
                mutableAnchor(initialX, initialY),
                mutableSize(initialWidth, initialHeight));
    }

    @Override
    protected void onStageAnchorChange(double oldX, double newX, double oldY, double newY) {
        Side side = super.side.get();
        switch ( side ) {
            case TOP:
                this.touchArea.anchor().set(newX, 0);
                break;
            case LEFT:
                this.touchArea.anchor().set(0, newY);
                break;
            case RIGHT:
                this.touchArea.anchor().set(this.screenWidthMinusOne, newY);
                break;
            case BOTTOM:
                this.touchArea.anchor().set(newX, this.screenHeightMinusOne);
                break;
            default:
                throw side.unsupported();
        }
    }

    @Override
    protected void onStageSizeChange(double oldWidth, double newWidth, double oldHeight, double newHeight) {
        Side side = super.side.get();
        switch ( side.orientation ) {
            case HORIZONTAL:
                this.touchArea.size().set(newWidth, 1);
                break;
            case VERTICAL:
                this.touchArea.size().set(1, newHeight);
                break;
            default:
                throw side.unsupported();
        }
    }

    @Override
    protected void onSideChange(Side oldSide, Side newSide) {
        switch ( newSide.orientation ) {
            case HORIZONTAL:
                this.touchArea.size().set(super.width.get(), 1);
                break;
            case VERTICAL:
                this.touchArea.size().set(1, super.height.get());
                break;
            default:
                throw newSide.unsupported();
        }
    }

    @Override
    public Anchor anchor() {
        return this.touchArea.anchor();
    }

    @Override
    public Size size() {
        return this.touchArea.size();
    }
}
