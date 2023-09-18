package diarsid.desktop.ui.components.sidebar.api;

import java.io.Closeable;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.control.MenuItem;

import diarsid.desktop.ui.components.sidebar.impl.SidebarImpl;
import diarsid.desktop.ui.geometry.Rectangle;
import diarsid.desktop.ui.mouse.watching.WatchBearer;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.javafx.PlatformActions;
import diarsid.support.javafx.components.Movable;
import diarsid.support.javafx.components.Visible;
import diarsid.support.model.Named;
import diarsid.support.model.Unique;
import diarsid.support.objects.CommonEnum;

import static java.util.Collections.emptyList;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.Place.CENTER;
import static diarsid.desktop.ui.geometry.Rectangle.Side.BOTTOM;
import static diarsid.desktop.ui.geometry.Rectangle.Side.LEFT;
import static diarsid.desktop.ui.geometry.Rectangle.Side.RIGHT;
import static diarsid.desktop.ui.geometry.Rectangle.Side.TOP;

public interface Sidebar extends Named, Rectangle, Visible, Closeable, WatchBearer {

    static Sidebar createInstance(
            String name,
            Position position,
            BooleanProperty isPinned,
            NamedThreadSource namedThreadSource,
            Items.Alignment itemsAlignment,
            Supplier<List<Item>> initialItems,
            double showTime,
            double hideTime) {
        return PlatformActions.doGet(() -> {
            return new SidebarImpl(
                    name,
                    position,
                    isPinned,
                    namedThreadSource,
                    itemsAlignment,
                    initialItems,
                    showTime,
                    hideTime);
        });
    }

    enum State implements CommonEnum<State> {

        IS_SHOWING(true, true),
        IS_SHOWN(false, true),
        IS_HIDING(true, true),
        IS_HIDDEN(false, false);

        public boolean isInMove;
        public boolean isVisible;

        State(boolean isInMove, boolean isVisible) {
            this.isInMove = isInMove;
            this.isVisible = isVisible;
        }
    }

    ReadOnlyObjectProperty<Position.Current> position();

    ReadOnlyObjectProperty<State> state();

    Control control();

    @Override
    void close(); // override for not throwing IOException

    interface Position extends Serializable {

        interface Current extends Absolute {

            boolean hasRelative();

            Relative relativeOrThrow();
        }

        enum Relative implements Position, CommonEnum<Relative> {

            TOP_CENTER(TOP, CENTER),
            RIGHT_CENTER(RIGHT, CENTER),
            BOTTOM_CENTER(BOTTOM, CENTER),
            LEFT_CENTER(LEFT, CENTER);

            public static enum Place implements CommonEnum<Place> {
                CENTER;
            }

            Relative(Side side, Place place) {
                this.side = side;
                this.place = place;
            }

            private final Side side;
            private final Place place;


            @Override
            public Side side() {
                return this.side;
            }

            public Place place() {
                return this.place;
            }
        }

        interface Absolute extends Position {

            double coordinate();
        }

        Rectangle.Side side();

    }

    interface Control extends Movable {

        /* All methods of this interface are meant to be executed asynchronously.
         * Method invocation only issue a command to underlying component but do not wait for completion and returns
         * before actual action will be completed or even before it will actually begin
         */

        void moveTo(double coordinateOnCurrentSide);

        void moveTo(Rectangle.Side side, double coordinateOnSide);

        void moveTo(Position.Relative relativePosition);

        default void moveTo(Position.Absolute position) {
            this.moveTo(position.side(), position.coordinate());
        }

        Session session();

        Items items();

    }

    interface Items {

        enum Alignment implements CommonEnum<Alignment> {

            PARALLEL_TO_SIDE,
            PERPENDICULAR_TO_SIDE;
        }

        Items.Alignment itemsAlignment();

        /* Intended to be read-only.
         * Returns unmodifiable List or copy of original List though it's changes will be ignored. In order
         * to change items list use change(Consumer<List<Item>>) method.
         */
        List<Item> all();

        /*
         * Passes actual modifiable list of Items.
         * All changes made to passed list will be reflected on sidebar when method returns.
         * It is possible to change items completely or even remove all ot them using this method.         *
         */
        void change(Consumer<List<Item>> allItemsToChange);

    }

    interface Item extends Unique, Named, Visible, Runnable {

        default List<MenuItem> itemsContextMenuItems() {
            return emptyList();
        }

        default void onThrownInRun(Throwable t) {
            // for override
        }
    }

    /*
     * All methods of this interface are meant to be executed asynchronously.
     * Method invocation only issue a command to underlying component but do not wait for completion and returns
     * before actual action will be completed or even before it will actually begin.
     *
     * Session is an abstraction that represents a some period of time when user interacts with sidebar as with
     * desktop UI. User's mouse entering the sidebar is considered as 'touch' and can be imitated with .touch()
     * method. Touch means a single contact with hidden (or hiding) sidebar, which activates it. Sidebar will
     * remain active while user's mouse is inside a sidebar or session is 'blocked'.
     *
     * Session block means a prohibition for activated ('touched') sidebar to deactivate itself with time.
     * It is possible to set multiple blocks. Session will not end normally and sidebar will not hide while any
     * blocks are set. Methods .block(String) and .unblock(String) can be used for set and remove blocks respectively.
     * Method .unblock() that doesn't accept a block name removes all existing blocks.
     *
     * Method .touchAndBlock(String) will start session AND block it after sidebar fully appears.
     *
     * For example, you don't want a sidebar to be automatically deactivated while some window is still open:
     *
     * xWindow.onOpened(() -> {
     *     sidebar.control().session().touchAndBlock("X_WINDOW_OPEN");
     * });
     *
     * xWindow.onClosed(() -> {
     *     sidebar.control().session().unblock("X_WINDOW_OPEN");
     * });
     */
    interface Session {

        boolean isActive();

        void touch();

        void touchAndBlock(String block);

        void block(String block);

        void unblock(String block);

        void unblock();

        boolean isBlocked();

        default boolean isNotBlocked() {
            return ! this.isBlocked();
        }

        boolean hasBlock(String name);

        void add(Touch.Listener touchListener);

        boolean remove(Touch.Listener touchListener);

        interface Touch {

            interface Kind {

                String MANUAL = "TOUCH_IS_MANUAL";
                String PROGRAMMATICAL = "TOUCH_IS_PROGRAMMATICAL";

            }

            interface Listener {

                void onTouchedOf(String touchKind);

            }
        }
    }

}
