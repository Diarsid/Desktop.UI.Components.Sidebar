package diarsid.desktop.ui.components.sidebar.api;

import java.util.Objects;

import diarsid.desktop.ui.geometry.Rectangle;

import static java.util.Objects.requireNonNull;

public class RealPosition implements Sidebar.Position {

    private final Rectangle.Side side;
    private final double coordinate;

    public RealPosition(Rectangle.Side side, double coordinate) {
        requireNonNull(side);
        this.side = side;
        this.coordinate = coordinate;
    }

    @Override
    public Rectangle.Side side() {
        return this.side;
    }

    @Override
    public double coordinate() {
        return this.coordinate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RealPosition)) return false;
        RealPosition that = (RealPosition) o;
        return Double.compare(that.coordinate, coordinate) == 0 &&
                side == that.side;
    }

    @Override
    public int hashCode() {
        return Objects.hash(side, coordinate);
    }
}
