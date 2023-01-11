package diarsid.desktop.ui.components.sidebar.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidebar.api.Sidebar;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.objects.references.Possible;

import static java.util.Collections.synchronizedList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Session.TouchListener.TOUCH_IS_PROGRAMMATICAL;
import static diarsid.support.concurrency.threads.ThreadsUtil.shutdownAndWait;
import static diarsid.support.objects.collections.CollectionUtils.nonEmpty;
import static diarsid.support.objects.references.References.simplePossibleButEmpty;

public class SidebarSession implements Sidebar.Session {

    private static final Logger log = LoggerFactory.getLogger(SidebarSession.class);

    private final String name;
    private final int millisToFinishSession;
    private final Consumer<String> onActivation;
    private final Runnable onDeactivation;
    private final Supplier<Boolean> canDeactivate;
    private final ReadWriteLock lock;
    private final Possible<Future<?>> deactivation;
    private final List<String> blocks;
    private final ScheduledExecutorService async;
    private final List<TouchListener> touchListeners;

    public SidebarSession(
            String name,
            int millisToFinishSession,
            NamedThreadSource namedThreadSource,
            Consumer<String> onActivation,
            Runnable onDeactivation,
            Supplier<Boolean> canDeactivate) {
        this.name = name;
        this.millisToFinishSession = millisToFinishSession;
        this.onActivation = onActivation;
        this.onDeactivation = onDeactivation;
        this.canDeactivate = canDeactivate;
        this.lock = new ReentrantReadWriteLock();
        this.deactivation = simplePossibleButEmpty();
        this.blocks = new ArrayList<>();
        this.async = namedThreadSource.newNamedScheduledExecutorService(
                this.getClass().getSimpleName() + "[" + name + "]",
                1);
        this.touchListeners = synchronizedList(new ArrayList<>());
    }

    @Override
    public boolean isActive() {
        lock.readLock().lock();
        try {
            return deactivation.isPresent();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void touch() {
        lock.writeLock().lock();
        try {
            if ( blocks.isEmpty() ) {
                doTouch(TOUCH_IS_PROGRAMMATICAL);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void touch(String touchKind) {
        lock.writeLock().lock();
        try {
            if ( blocks.isEmpty() ) {
                doTouch(touchKind);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void touchAndBlock(String block) {
        lock.writeLock().lock();
        try {
            if ( blocks.isEmpty() ) {
                doTouch(TOUCH_IS_PROGRAMMATICAL);
                block(block);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void touchAndBlock(String touchKind, String block) {
        lock.writeLock().lock();
        try {
            if ( blocks.isEmpty() ) {
                doTouch(touchKind);
                block(block);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void doTouch(String touchKind) {
        if ( deactivation.isPresent() ) {
            prolongActivity();
        }
        else {
            activate(touchKind);
        }
    }

    @Override
    public void block(String block) {
        lock.writeLock().lock();
        try {
            deactivation.ifPresent(asyncJob -> asyncJob.cancel(true));
            blocks.add(block);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void unblock(String block) {
        lock.writeLock().lock();
        try {
            boolean removed = blocks.remove(block);
            if ( blocks.isEmpty() && this.isActive() ) {
                tryDeactivate();
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void unblock() {
        lock.writeLock().lock();
        try {
            blocks.clear();
            tryDeactivate();
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean isBlocked() {
        lock.writeLock().lock();
        try {
            return nonEmpty(blocks);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean hasBlock(String name) {
        lock.writeLock().lock();
        try {
            return blocks.contains(name);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void add(TouchListener touchListener) {
        this.touchListeners.add(touchListener);
    }

    @Override
    public boolean remove(TouchListener touchListener) {
        return this.touchListeners.remove(touchListener);
    }

    public void dispose() {
        lock.writeLock().lock();
        try {
            deactivation.ifPresent(future -> future.cancel(true));
            shutdownAndWait(async);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void activate(String touchKind) {
        Platform.runLater(() -> {
            onActivation.accept(touchKind);
            for ( TouchListener touchListener : this.touchListeners ) {
                try {
                    touchListener.onTouchedOf(touchKind);
                }
                catch (Throwable t) {
                    log.error(t.getMessage(), t);
                }
            }
        });
        deactivation.ifPresent(asyncJob -> asyncJob.cancel(true));
        deactivation.resetTo(async.schedule(this::tryDeactivate, this.millisToFinishSession, MILLISECONDS));
    }

    private void prolongActivity() {
        deactivation.ifPresent(asyncJob -> asyncJob.cancel(true));
        deactivation.resetTo(async.schedule(this::tryDeactivate, this.millisToFinishSession, MILLISECONDS));
    }

    private void tryDeactivate() {
        lock.writeLock().lock();
        try {
            if ( blocks.isEmpty() ) {
                boolean canDeactivate = this.canDeactivate.get();
                if ( canDeactivate ) {
                    Platform.runLater(() -> {
                        this.onDeactivation.run();
                    });
                    deactivation.nullify();
                }
                else {
                    deactivation.ifPresent(asyncJob -> asyncJob.cancel(true));
                    deactivation.resetTo(async.schedule(this::tryDeactivate, this.millisToFinishSession, MILLISECONDS));
                }
            }
            else {
                deactivation.ifPresent(asyncJob -> asyncJob.cancel(true));
                deactivation.resetTo(async.schedule(this::tryDeactivate, this.millisToFinishSession, MILLISECONDS));
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
