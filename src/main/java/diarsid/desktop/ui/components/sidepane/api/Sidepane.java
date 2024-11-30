package diarsid.desktop.ui.components.sidepane.api;

import java.io.Closeable;
import java.io.Serializable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.Menu;
import javafx.scene.input.MouseEvent;

import diarsid.desktop.ui.components.sidepane.impl.SidepaneBuilderImpl;
import diarsid.desktop.ui.geometry.Rectangle;
import diarsid.desktop.ui.mouse.watching.MouseWatcher;
import diarsid.desktop.ui.mouse.watching.WatchBearer;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.javafx.components.Movable;
import diarsid.support.javafx.components.Visible;
import diarsid.support.javafx.geometry.Screen;
import diarsid.support.model.Named;
import diarsid.support.objects.CommonEnum;

import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Position.Relative.Place.CENTER;
import static diarsid.desktop.ui.geometry.Rectangle.Side.BOTTOM;
import static diarsid.desktop.ui.geometry.Rectangle.Side.LEFT;
import static diarsid.desktop.ui.geometry.Rectangle.Side.RIGHT;
import static diarsid.desktop.ui.geometry.Rectangle.Side.TOP;

public interface Sidepane<T> extends Named, Rectangle, Visible, Closeable, WatchBearer {

    public interface Builder<T> {

        public static <T> Builder<T> create() {
            return new SidepaneBuilderImpl<>();
        }

        Builder<T> name(String name);

        Builder<T> isPinned(boolean isPinned);

        Builder<T> isPinned(BooleanProperty isPinned);

        Builder<T> saveState(boolean saveState);

        Builder<T> initialPosition(Position initialPosition);

        Builder<T> async(NamedThreadSource async);

        Builder<T> contentView(Content.View<T> view);

        Builder<T> show(Behavior.Show show);

        Builder<T> hide(Behavior.Hide hide);

        Builder<T> mouseWatcher(MouseWatcher mouseWatcher);

        Sidepane<T> done();
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

    Control<T> control();

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

    interface Control<T> extends Movable {

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

        Content<T> content();

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

    public static interface Content<T>  {

        /*
         *
         *
         */
        public static interface View<T> extends Visible {

            void initOnMount(
                    Runnable invokeResize,
                    Runnable hideContextMenu,
                    BiConsumer<MouseEvent, Menu> onMenuInvoked);

            void adoptChange(Screen.Side newSide);

            void adoptGivenChange(Consumer<T> mutation);

            /*
             * Override to pass pseudo class change to underlying JavaFX component's of this View implementation
             */
            void pseudoClassStateChanged(PseudoClass pseudoClass, boolean active);

        }

        /*
         * View<T> can extend Storable
         */
        public static interface Storable {

            Serializable stateToStore();

            void restoreStateFromStored(Serializable storedState);
        }

        void change(Consumer<T> mutation);

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
