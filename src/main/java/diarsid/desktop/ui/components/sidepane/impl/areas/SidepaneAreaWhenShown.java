package diarsid.desktop.ui.components.sidepane.impl.areas;

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

public class SidepaneAreaWhenShown extends SidepaneStagePropertiesListener implements Rectangle  {

    private final MutableRectangle shownArea;

    public SidepaneAreaWhenShown(Screen screen, Stage stage, ObjectProperty<Side> side) {
        super(screen, stage, side);

        this.shownArea = new RealMutableRectangle(
                mutableAnchor(super.x.get(), super.y.get()),
                mutableSize(super.width.get(), super.height.get()));
    }

    @Override
    protected void onStageAnchorChange(double oldX, double newX, double oldY, double newY) {
        Side side = super.side.get();
        switch ( side ) {
            case TOP:
                this.shownArea.anchor().set(newX, 0);
                break;
            case LEFT:
                this.shownArea.anchor().set(0, newY);
                break;
            case RIGHT:
                this.shownArea.anchor().set(super.screen.width() - super.width.get(), newY);
                break;
            case BOTTOM:
                this.shownArea.anchor().set(newX, super.screen.height() - super.height.get());
                break;
            default:
                throw side.unsupported();
        }
    }

    @Override
    protected void onStageSizeChange(double oldWidth, double newWidth, double oldHeight, double newHeight) {
        Side side = super.side.get();

        this.shownArea.size().set(newWidth, newHeight);

        switch ( side ) {
            case TOP:
                this.shownArea.anchor().set(super.x.get(), 0);
                break;
            case LEFT:
                this.shownArea.anchor().set(0, super.y.get());
                break;
            case RIGHT:
                this.shownArea.anchor().set(super.screen.width() - newWidth, 0);
                break;
            case BOTTOM:
                this.shownArea.anchor().set(super.x.get(), super.screen.height() - newHeight);
                break;
            default:
                throw side.unsupported();
        }
    }

    @Override
    public Anchor anchor() {
        return this.shownArea.anchor();
    }

    @Override
    public Size size() {
        return this.shownArea.size();
    }
}
