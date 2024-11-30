package diarsid.desktop.ui.components.sidepane.impl;

import diarsid.desktop.ui.components.sidepane.api.Sidepane;
import diarsid.desktop.ui.geometry.Rectangle;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

class CurrentPosition implements Sidepane.Position.Current {

    private final Rectangle.Side side;
    private final double coordinate;
    private final Relative relative;

    CurrentPosition(Rectangle.Side side, double coordinate) {
        this.side = side;
        this.coordinate = coordinate;
        this.relative = null;
    }

    CurrentPosition(Rectangle.Side side, double coordinate, Relative relative) {
        this.side = side;
        this.coordinate = coordinate;
        this.relative = relative;
    }

    @Override
    public boolean hasRelative() {
        return nonNull(relative);
    }

    @Override
    public Relative relativeOrThrow() {
        if ( isNull(relative) ) {
            throw new NullPointerException();
        }

        return relative;
    }

    @Override
    public double coordinate() {
        return this.coordinate;
    }

    @Override
    public Rectangle.Side side() {
        return this.side;
    }

    @Override
    public String toString() {
        return "CurrentPosition{" +
                "side=" + side +
                ", coordinate=" + coordinate +
                ", relative=" + relative +
                '}';
    }
}
