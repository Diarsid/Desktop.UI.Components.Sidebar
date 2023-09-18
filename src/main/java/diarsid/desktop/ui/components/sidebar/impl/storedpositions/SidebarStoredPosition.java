package diarsid.desktop.ui.components.sidebar.impl.storedpositions;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import diarsid.desktop.ui.components.sidebar.api.AbsolutePosition;
import diarsid.desktop.ui.components.sidebar.api.Sidebar;
import diarsid.desktop.ui.geometry.Rectangle;

import static java.time.LocalDateTime.now;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class SidebarStoredPosition implements Serializable {

    public final String name;
    public final LocalDateTime time;
    private final Sidebar.Position.Relative relativePosition;
    private final Double absoluteCoordinate;
    private final Rectangle.Side absoluteSide;

    public SidebarStoredPosition(String name, Sidebar.Position position) {
        this(name, now(), position);
    }

    public SidebarStoredPosition(String name, LocalDateTime time, Sidebar.Position position) {
        this.name = name;
        this.time = time;

        if ( position instanceof Sidebar.Position.Current ) {
            var currentPosition = (Sidebar.Position.Current) position;

            if ( currentPosition.hasRelative() ) {
                this.relativePosition = currentPosition.relativeOrThrow();
                this.absoluteSide = null;
                this.absoluteCoordinate = null;
            }
            else {
                this.relativePosition = null;
                this.absoluteSide = currentPosition.side();
                this.absoluteCoordinate = currentPosition.coordinate();
            }
            return;
        }

        if ( position instanceof Sidebar.Position.Relative ) {
            this.relativePosition = (Sidebar.Position.Relative) position;
            this.absoluteSide = null;
            this.absoluteCoordinate = null;
        }
        else {
            Sidebar.Position.Absolute absolutePosition = (Sidebar.Position.Absolute) position;
            this.relativePosition = null;
            this.absoluteSide = absolutePosition.side();
            this.absoluteCoordinate = absolutePosition.coordinate();
        }
    }

    public Sidebar.Position position() {
        if ( nonNull(relativePosition) ) {
            return relativePosition;
        }
        else {
            return new AbsolutePosition(absoluteSide, absoluteCoordinate);
        }
    }

    public SidebarStoredPosition newWith(Sidebar.Position newPosition) {
        return new SidebarStoredPosition(this.name, now(), newPosition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SidebarStoredPosition)) return false;
        SidebarStoredPosition that = (SidebarStoredPosition) o;
        return name.equals(that.name) &&
                time.equals(that.time) &&
                relativePosition == that.relativePosition &&
                Objects.equals(absoluteCoordinate, that.absoluteCoordinate) &&
                absoluteSide == that.absoluteSide;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, time, relativePosition, absoluteCoordinate, absoluteSide);
    }

    @Override
    public String toString() {
        if ( isNull(relativePosition) ) {
            return "SidebarStoredPosition{" +
                    "name='" + name + '\'' +
                    ", time=" + time +
                    ", side=" + absoluteSide +
                    ", coordinate=" + absoluteCoordinate +
                    '}';
        }
        else {
            return "SidebarStoredPosition{" +
                    "name='" + name + '\'' +
                    ", time=" + time +
                    ", position=" + relativePosition +
                    '}';
        }
    }
}
