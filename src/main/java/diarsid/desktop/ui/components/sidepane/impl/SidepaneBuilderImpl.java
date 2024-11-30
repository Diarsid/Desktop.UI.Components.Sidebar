package diarsid.desktop.ui.components.sidepane.impl;

import java.io.Serializable;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import diarsid.desktop.ui.components.sidepane.api.Sidepane;
import diarsid.desktop.ui.mouse.watching.MouseWatcher;
import diarsid.files.objects.InFile;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.javafx.PlatformActions;

import static java.util.Objects.isNull;

import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Position.Relative.TOP_CENTER;

public class SidepaneBuilderImpl<T> implements Sidepane.Builder<T> {


    private String name;
    private Sidepane.Position position;
    private BooleanProperty isPinned;
    private boolean saveState;
    private NamedThreadSource namedThreadSource;
    private Sidepane.Content.View<T> view;
    private Sidepane.Behavior.Show show;
    private Sidepane.Behavior.Hide hide;
    private MouseWatcher mouseWatcher;

    public SidepaneBuilderImpl() {
        this.name = Sidepane.class.getSimpleName() + "." + ProcessHandle.current().pid();
        this.position = TOP_CENTER;
        this.isPinned = new SimpleBooleanProperty(false);
        this.saveState = true;
        this.namedThreadSource = null;
        this.show = Sidepane.Behavior.Show.seconds(0.15);
        this.hide = Sidepane.Behavior.Hide.seconds(0.15);
        this.mouseWatcher = null;
    }

    @Override
    public Sidepane.Builder<T> name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public Sidepane.Builder<T> isPinned(boolean isPinned) {
        this.isPinned.set(isPinned);
        return this;
    }

    @Override
    public Sidepane.Builder<T> isPinned(BooleanProperty isPinned) {
        this.isPinned = isPinned;
        return this;
    }

    @Override
    public Sidepane.Builder<T> saveState(boolean saveState) {
        this.saveState = saveState;
        return this;
    }

    @Override
    public Sidepane.Builder<T> initialPosition(Sidepane.Position initialPosition) {
        this.position = initialPosition;
        return this;
    }

    @Override
    public Sidepane.Builder<T> async(NamedThreadSource async) {
        this.namedThreadSource = async;
        return this;
    }

    @Override
    public Sidepane.Builder<T> contentView(Sidepane.Content.View<T> view) {
        this.view = view;
        return this;
    }

    @Override
    public Sidepane.Builder<T> show(Sidepane.Behavior.Show show) {
        this.show = show;
        return this;
    }

    @Override
    public Sidepane.Builder<T> hide(Sidepane.Behavior.Hide hide) {
        this.hide = hide;
        return this;
    }

    @Override
    public Sidepane.Builder<T> mouseWatcher(MouseWatcher mouseWatcher) {
        this.mouseWatcher = mouseWatcher;
        return this;
    }

    @Override
    public Sidepane<T> done() {
        boolean needStartMouseWatcher = false;
        if ( isNull(this.mouseWatcher) ) {
            this.mouseWatcher = new MouseWatcher(10);
            needStartMouseWatcher = true;
        }

        SidepaneImpl<T> sidebar;
        if ( this.saveState ) {
            boolean isStorable = this.view instanceof Sidepane.Content.Storable;
            Serializable stateToStore;
            if ( isStorable ) {
                stateToStore = ((Sidepane.Content.Storable) this.view).stateToStore();
            }
            else {
                stateToStore = null;
            }

            SidepaneStoredState storedInitial = new SidepaneStoredState(
                    this.name, this.position, this.isPinned.get(), this.show, this.hide, stateToStore);

            SidepaneStoredState.InFileInitializer initializer = new SidepaneStoredState.InFileInitializer(storedInitial);

            InFile<SidepaneStoredState> stateInFile = new InFile<>(name, initializer);

            if ( initializer.existing().isPresent() ) {
                SidepaneStoredState storedState = stateInFile.get();

                this.position = storedState.position();
                this.isPinned.set(storedState.isPinned());
                this.show = storedState.show();
                this.hide = storedState.hide();

                if ( isStorable ) {
                    ((Sidepane.Content.Storable) this.view).restoreStateFromStored(storedState.storedState());
                }
            }

            if ( isStorable ) {
                ViewSpy<T> viewSpy = new ViewSpy<>(
                        this.view,
                        () -> {
                            stateInFile.modifyIfPresent((state) -> {
                                Serializable changed = ((Sidepane.Content.Storable) this.view).stateToStore();
                                state.storedState(changed);
                            });
                        });

                this.view = viewSpy;
            }

            sidebar = PlatformActions.doGet(() -> {
                return new SidepaneImpl<>(
                        name,
                        position,
                        isPinned,
                        namedThreadSource,
                        view,
                        show,
                        hide,
                        mouseWatcher);
            });

            sidebar.position().addListener((prop, oldPosition, newPosition) -> {
                stateInFile.modifyIfPresent((state)-> {
                    state.position(newPosition);
                });
            });

            sidebar.control().movePermission().addListener((prop, oldPermission, newPermission) -> {
                stateInFile.modifyIfPresent((state) -> {
                    state.pinned(newPermission);
                });
            });
        }
        else {
            sidebar = PlatformActions.doGet(() -> {
                return new SidepaneImpl<>(
                        name,
                        position,
                        isPinned,
                        namedThreadSource,
                        view,
                        show,
                        hide,
                        mouseWatcher);
            });
        }

        if ( needStartMouseWatcher ) {
            this.mouseWatcher.startWork();
        }

        return sidebar;
    }
}
