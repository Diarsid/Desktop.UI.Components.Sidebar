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
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Session.Touch.Kind.PROGRAMMATICAL;
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
    private final ScheduledExecutorService asyncUnblock;
    private final List<Touch.Listener> touchListeners;

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
        this.asyncUnblock = namedThreadSource.newNamedScheduledExecutorService(
                this.getClass().getSimpleName() + "[" + name + "].unblock",
                1);
        this.touchListeners = synchronizedList(new ArrayList<>());
    }

    @Override
    public boolean isActive() {
        this.lock.readLock().lock();
        try {
            return this.deactivation.isPresent();
        }
        finally {
            this.lock.readLock().unlock();
        }
    }

    @Override
    public void touch() {
        this.lock.writeLock().lock();
        try {
            if ( this.blocks.isEmpty() ) {
                this.doTouch(PROGRAMMATICAL);
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    public void touch(String touchKind) {
        this.lock.writeLock().lock();
        try {
            if ( this.blocks.isEmpty() ) {
                this.doTouch(touchKind);
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void touchAndBlock(String block) {
        this.lock.writeLock().lock();
        try {
            if ( blocks.isEmpty() ) {
                this.doTouch(PROGRAMMATICAL);
                this.block(block);
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void touchAndBlock(String block, long millisForBlockToExist) {
        this.lock.writeLock().lock();
        try {
            if ( this.blocks.isEmpty() ) {
                this.doTouch(PROGRAMMATICAL);
                this.block(block);
                this.asyncUnblock.schedule(
                        () -> {
                            this.unblock(block);
                        },
                        millisForBlockToExist, MILLISECONDS);
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    public void touchAndBlock(String touchKind, String block) {
        this.lock.writeLock().lock();
        try {
            if ( this.blocks.isEmpty() ) {
                this.doTouch(touchKind);
                this.block(block);
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    public void touchAndBlock(String touchKind, String block, long millisForBlockToExist) {
        this.lock.writeLock().lock();
        try {
            if ( this.blocks.isEmpty() ) {
                this.doTouch(touchKind);
                this.block(block);
                this.asyncUnblock.schedule(
                        () -> {
                            unblock(block);
                        },
                        millisForBlockToExist, MILLISECONDS);
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    private void doTouch(String touchKind) {
        if ( this.deactivation.isPresent() ) {
            this.prolongActivity();
        }
        else {
            this.activate(touchKind);
        }
    }

    @Override
    public void block(String block) {
        this.lock.writeLock().lock();
        try {
            this.deactivation.ifPresent(asyncJob -> asyncJob.cancel(true));
            this.blocks.add(block);
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void block(String block, long millisForBlockToExist) {
        this.lock.writeLock().lock();
        try {
            this.deactivation.ifPresent(asyncJob -> asyncJob.cancel(true));
            this.blocks.add(block);
            this.asyncUnblock.schedule(
                    () -> {
                        this.unblock(block);
                    },
                    millisForBlockToExist, MILLISECONDS);
        }
        finally {
            this.lock.writeLock().unlock();
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
    public void add(Touch.Listener touchListener) {
        this.touchListeners.add(touchListener);
    }

    @Override
    public boolean remove(Touch.Listener touchListener) {
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
            for ( Touch.Listener touchListener : this.touchListeners ) {
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
                    Platform.runLater(this.onDeactivation);
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
