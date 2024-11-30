package diarsid.desktop.ui.components.sidepane.impl;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidepane.api.AbsolutePosition;
import diarsid.desktop.ui.components.sidepane.api.Sidepane;
import diarsid.desktop.ui.geometry.Rectangle;
import diarsid.files.objects.InFile;
import diarsid.support.objects.references.Result;

import static java.lang.String.format;
import static java.time.LocalDateTime.now;
import static java.util.Objects.nonNull;

import static diarsid.files.objects.InFile.Initializer.OnClassExceptionDo.REWRITE_WITH_INITIAL;

public class SidepaneStoredState implements Serializable {

    public static class InFileInitializer implements InFile.Initializer<SidepaneStoredState> {

        private static final Logger log = LoggerFactory.getLogger(InFileInitializer.class);

        private final SidepaneStoredState initial;
        private final Consumer<SidepaneStoredState> doWhenExists;
        private final Runnable doWhenCreated;

        private boolean used;
        private SidepaneStoredState existing;

        public InFileInitializer(SidepaneStoredState initial) {
            this(initial, null, null);
        }

        public InFileInitializer(
                SidepaneStoredState initial,
                Consumer<SidepaneStoredState> doWhenExists,
                Runnable doWhenCreated) {
            this.initial = initial;
            this.doWhenExists = doWhenExists;
            this.doWhenCreated = doWhenCreated;

            this.used = false;
            this.existing = null;
        }

        @Override
        public SidepaneStoredState onFileCreatedGetInitial() {
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
        public void onFileAlreadyExists(SidepaneStoredState existing) {
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
        public Class<SidepaneStoredState> type() {
            return SidepaneStoredState.class;
        }

        public Result<SidepaneStoredState> existing() {
            if ( ! this.used ) {
                return Result.empty(format("Cannot get existing %s before use of %s",
                        SidepaneStoredState.class.getSimpleName(),
                        InFileInitializer.class.getSimpleName()));
            }

            if ( nonNull(this.existing) ) {
                return Result.completed(this.existing);
            }
            else {
                return Result.empty(format("%s does not exist!", SidepaneStoredState.class.getSimpleName()));
            }
        }
    }

    private final String name;
    private LocalDateTime time;
    private Sidepane.Position.Relative relativePosition;
    private Double absoluteCoordinate;
    private Rectangle.Side absoluteSide;
    private boolean isPinned;
    private Sidepane.Behavior.Show show;
    private Sidepane.Behavior.Hide hide;
    private Serializable storedState;

    public SidepaneStoredState(
            String name,
            Sidepane.Position position,
            boolean isPinned,
            Sidepane.Behavior.Show show,
            Sidepane.Behavior.Hide hide,
            Serializable storedState) {
        this(name, now(), position, isPinned, show, hide, storedState);
    }

    public SidepaneStoredState(
            String name,
            LocalDateTime time,
            Sidepane.Position position,
            boolean isPinned,
            Sidepane.Behavior.Show show,
            Sidepane.Behavior.Hide hide,
            Serializable storedState) {
        this.name = name;
        this.time = time;
        this.isPinned = isPinned;
        this.show = show;
        this.hide = hide;
        this.storedState = storedState;
        this.position(position);
    }

    public Sidepane.Position position() {
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

    public Serializable storedState() {
        return this.storedState;
    }

    public Sidepane.Behavior.Show show() {
        return this.show;
    }

    public Sidepane.Behavior.Hide hide() {
        return this.hide;
    }

    public void position(Sidepane.Position position) {
        this.time = now();

        if ( position instanceof Sidepane.Position.Current ) {
            var currentPosition = (Sidepane.Position.Current) position;

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

        if ( position instanceof Sidepane.Position.Relative ) {
            this.relativePosition = (Sidepane.Position.Relative) position;
            this.absoluteSide = null;
            this.absoluteCoordinate = null;
        }
        else {
            Sidepane.Position.Absolute absolutePosition = (Sidepane.Position.Absolute) position;
            this.relativePosition = null;
            this.absoluteSide = absolutePosition.side();
            this.absoluteCoordinate = absolutePosition.coordinate();
        }
    }

    public void pinned(boolean pinned) {
        this.time = now();
        this.isPinned = pinned;
    }

    public void storedState(Serializable storedContent) {
        this.time = now();
        this.storedState = storedContent;
    }

    public void show(Sidepane.Behavior.Show show) {
        this.time = now();
        this.show = show;
    }

    public void hide(Sidepane.Behavior.Hide hide) {
        this.time = now();
        this.hide = hide;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SidepaneStoredState)) return false;
        SidepaneStoredState that = (SidepaneStoredState) o;
        return isPinned == that.isPinned &&
                name.equals(that.name) &&
                time.equals(that.time) &&
                relativePosition == that.relativePosition &&
                absoluteCoordinate.equals(that.absoluteCoordinate) &&
                absoluteSide == that.absoluteSide &&
                storedState == that.storedState &&
                show.equals(that.show) &&
                hide.equals(that.hide);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, time, relativePosition, absoluteCoordinate, absoluteSide, isPinned, storedState, show, hide);
    }
}
