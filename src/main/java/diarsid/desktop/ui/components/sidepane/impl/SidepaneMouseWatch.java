package diarsid.desktop.ui.components.sidepane.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;

import diarsid.desktop.ui.components.sidepane.api.Sidepane;
import diarsid.desktop.ui.components.sidepane.impl.areas.SidepaneAreaForTouch;
import diarsid.desktop.ui.mouse.watching.Watch;
import diarsid.support.concurrency.threads.NamedThreadSource;

import static java.util.Objects.nonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class SidepaneMouseWatch {

    private final Runnable fencedManualTouch;
    private final ReadOnlyIntegerProperty delayMillis;

    private final Lock manualTouchRunning;

    private final ScheduledExecutorService async;
    private volatile ScheduledFuture<?> delayed;

    final Watch watch;

    public SidepaneMouseWatch(
            String sidebarName,
            SidepaneAreaForTouch touchArea,
            Runnable manualTouch,
            NamedThreadSource namedThreadSource,
            ReadOnlyIntegerProperty delayMillis) {
        this.delayMillis = delayMillis;
        this.async = namedThreadSource.newNamedScheduledExecutorService(
                Sidepane.class.getSimpleName() + "." + sidebarName + ".delay",
                1);

        this.manualTouchRunning = new ReentrantLock(true);

        this.fencedManualTouch = () -> {
            this.manualTouchRunning.lock();
            try {
                manualTouch.run();
                this.delayed = null;
            }
            finally {
                this.manualTouchRunning.unlock();
            }
        };

        this.watch = new Watch(
                Sidepane.class.getSimpleName() + "." + sidebarName,
                (point) -> {
                    return touchArea.contains(point.x, point.y);
                },
                (point, isActive) -> {
                    long currentDelayMillis = this.delayMillis.get();

                    this.manualTouchRunning.lock();
                    try {
                        if ( nonNull(this.delayed) && ! this.delayed.isDone() ) {
                            this.delayed.cancel(true);
                            this.delayed = null;
                        }
                    }
                    finally {
                        this.manualTouchRunning.unlock();
                    }

                    if ( isActive ) {
                        if ( currentDelayMillis > 0 ) {
                            this.delayed = this.async.schedule(
                                    this::asyncRunManualTouchSafely,
                                    currentDelayMillis,
                                    MILLISECONDS);
                        }
                        else {
                            this.asyncRunManualTouchSafely();
                        }
                    }
                });
    }

    private void asyncRunManualTouchSafely() {
        try {
            Platform.runLater(this.fencedManualTouch);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
