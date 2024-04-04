package diarsid.desktop.ui.components.sidebar.impl;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidebar.api.AbsolutePosition;
import diarsid.desktop.ui.components.sidebar.api.Sidebar;
import diarsid.desktop.ui.geometry.Rectangle;
import diarsid.files.objects.InFile;
import diarsid.support.objects.references.Result;

import static java.lang.String.format;
import static java.time.LocalDateTime.now;
import static java.util.Objects.nonNull;

import static diarsid.files.objects.InFile.Initializer.OnClassExceptionDo.REWRITE_WITH_INITIAL;

public class SidebarStoredState implements Serializable {

    public static class InFileInitializer implements InFile.Initializer<SidebarStoredState> {

        private static final Logger log = LoggerFactory.getLogger(InFileInitializer.class);

        private final SidebarStoredState initial;
        private final Consumer<SidebarStoredState> doWhenExists;
        private final Runnable doWhenCreated;

        private boolean used;
        private SidebarStoredState existing;

        public InFileInitializer(SidebarStoredState initial) {
            this(initial, null, null);
        }

        public InFileInitializer(
                SidebarStoredState initial,
                Consumer<SidebarStoredState> doWhenExists,
                Runnable doWhenCreated) {
            this.initial = initial;
            this.doWhenExists = doWhenExists;
            this.doWhenCreated = doWhenCreated;

            this.used = false;
            this.existing = null;
        }

        @Override
        public SidebarStoredState onFileCreatedGetInitial() {
            log.info("[SIDEBAR IN-FILE STATE] write initial: " + this.initial);
            this.used = true;
            if ( nonNull(this.doWhenCreated) ) {
                try {
                    this.doWhenCreated.run();
                }
                catch (Throwable t) {
                    log.error("Exception on creation callback: ", t);
                }
            }
            return this.initial;
        }

        @Override
        public void onFileAlreadyExists(SidebarStoredState existing) {
            log.info("[SIDEBAR IN-FILE STATE] existing: " + existing);
            this.existing = existing;
            this.used = true;
            if ( nonNull(this.doWhenExists) ) {
                try {
                    this.doWhenExists.accept(this.existing);
                }
                catch (Throwable t) {
                    log.error("Exception on existing callback:", t);
                }
            }
        }

        @Override
        public OnClassExceptionDo doOnClassException(Throwable t) {
            log.info("[SIDEBAR IN-FILE STATE] existing read exception: " + t.getMessage());
            return REWRITE_WITH_INITIAL;
        }

        @Override
        public Class<SidebarStoredState> type() {
            return SidebarStoredState.class;
        }

        public Result<SidebarStoredState> existing() {
            if ( ! this.used ) {
                return Result.empty(format("Cannot get existing %s before use of %s",
                        SidebarStoredState.class.getSimpleName(),
                        InFileInitializer.class.getSimpleName()));
            }

            if ( nonNull(this.existing) ) {
                return Result.completed(this.existing);
            }
            else {
                return Result.empty(format("%s does not exist!", SidebarStoredState.class.getSimpleName()));
            }
        }
    }

    private final String name;
    private LocalDateTime time;
    private Sidebar.Position.Relative relativePosition;
    private Double absoluteCoordinate;
    private Rectangle.Side absoluteSide;
    private boolean isPinned;
    private Sidebar.Items.Alignment alignment;
    private Sidebar.Behavior.Show show;
    private Sidebar.Behavior.Hide hide;

    public SidebarStoredState(
            String name,
            Sidebar.Position position,
            boolean isPinned,
            Sidebar.Items.Alignment alignment,
            Sidebar.Behavior.Show show,
            Sidebar.Behavior.Hide hide) {
        this(name, now(), position, isPinned, alignment, show, hide);
    }

    public SidebarStoredState(
            String name,
            LocalDateTime time,
            Sidebar.Position position,
            boolean isPinned,
            Sidebar.Items.Alignment alignment,
            Sidebar.Behavior.Show show,
            Sidebar.Behavior.Hide hide) {
        this.name = name;
        this.time = time;
        this.isPinned = isPinned;
        this.alignment = alignment;
        this.show = show;
        this.hide = hide;
        this.position(position);
    }

    public Sidebar.Position position() {
        if ( nonNull(relativePosition) ) {
            return relativePosition;
        }
        else {
            return new AbsolutePosition(absoluteSide, absoluteCoordinate);
        }
    }

    public String name() {
        return this.name;
    }

    public LocalDateTime time() {
        return this.time;
    }

    public boolean isPinned() {
        return this.isPinned;
    }

    public Sidebar.Items.Alignment alignment() {
        return this.alignment;
    }

    public Sidebar.Behavior.Show show() {
        return this.show;
    }

    public Sidebar.Behavior.Hide hide() {
        return this.hide;
    }

    public void position(Sidebar.Position position) {
        this.time = now();

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

    public void pinned(boolean pinned) {
        this.time = now();
        this.isPinned = pinned;
    }

    public void alignment(Sidebar.Items.Alignment alignment) {
        this.time = now();
        this.alignment = alignment;
    }

    public void show(Sidebar.Behavior.Show show) {
        this.time = now();
        this.show = show;
    }

    public void hide(Sidebar.Behavior.Hide hide) {
        this.time = now();
        this.hide = hide;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SidebarStoredState)) return false;
        SidebarStoredState that = (SidebarStoredState) o;
        return isPinned == that.isPinned &&
                name.equals(that.name) &&
                time.equals(that.time) &&
                relativePosition == that.relativePosition &&
                absoluteCoordinate.equals(that.absoluteCoordinate) &&
                absoluteSide == that.absoluteSide &&
                alignment == that.alignment &&
                show.equals(that.show) &&
                hide.equals(that.hide);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, time, relativePosition, absoluteCoordinate, absoluteSide, isPinned, alignment, show, hide);
    }
}
