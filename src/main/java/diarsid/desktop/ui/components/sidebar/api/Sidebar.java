package diarsid.desktop.ui.components.sidebar.api;

import java.io.Closeable;
import java.io.Serializable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.MenuItem;

import diarsid.desktop.ui.components.sidebar.impl.SidebarBuilderImpl;
import diarsid.desktop.ui.geometry.Rectangle;
import diarsid.desktop.ui.mouse.watching.MouseWatcher;
import diarsid.desktop.ui.mouse.watching.WatchBearer;
import diarsid.support.concurrency.threads.NamedThreadSource;
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

    public interface Builder {

        public static Builder create() {
            return new SidebarBuilderImpl();
        }

        Builder name(String name);

        Builder isPinned(boolean isPinned);

        Builder isPinned(BooleanProperty isPinned);

        Builder saveState(boolean saveState);

        Builder initialPosition(Position initialPosition);

        Builder async(NamedThreadSource async);

        Builder itemsAlignment(Items.Alignment alignment);

        Builder items(Supplier<List<Item>> items);

        Builder show(Behavior.Show show);

        Builder hide(Behavior.Hide hide);

        Builder mouseWatcher(MouseWatcher mouseWatcher);

        Sidebar done();
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

        OnTouchDelay onTouchDelay();

    }

    public interface Behavior {

        public static enum Type implements CommonEnum<Type> {
            INSTANT,
            SMOOTH
        }

        public static final class Hide extends Moving {

            public static Hide instant() {
                return new Hide();
            }

            public static Hide seconds(double seconds) {
                if ( seconds < 0.01 ) {
                    return instant();
                }
                return new Hide(seconds);
            }

            private Hide() {
                super();
            }

            private Hide(double seconds) {
                super(seconds);
            }
        }

        public static final class Show extends Moving {

            public static Show instant() {
                return new Show();
            }

            public static Show seconds(double seconds) {
                if ( seconds < 0.01 ) {
                    return instant();
                }
                return new Show(seconds);
            }

            private Show() {
                super();
            }

            private Show(double seconds) {
                super(seconds);
            }
        }
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

    interface Item extends Unique, Named, Visible {

        default List<MenuItem> itemContextMenuItems() {
            return emptyList();
        }

        default void onThrownInRun(Throwable t) {
            // for override
        }

        String run(); // runs logic and returns error message, if any. If run is successful, returns null;
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

        void touchAndBlock(String block, long millisForBlockToExist);

        void block(String block);

        void block(String block, long millisForBlockToExist);

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

    interface OnTouchDelay {

        void set(ReadOnlyIntegerProperty delay);

        default void set(int delay) {
            this.set(new SimpleIntegerProperty(delay));
        }

        long getOrZero();

        boolean isSet();
    }

}
