package diarsid.desktop.ui.components.sidebar.impl;

import java.util.List;
import java.util.function.Supplier;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import diarsid.desktop.ui.components.sidebar.api.Sidebar;
import diarsid.desktop.ui.mouse.watching.MouseWatcher;
import diarsid.files.objects.InFile;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.javafx.PlatformActions;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Items.Alignment.PARALLEL_TO_SIDE;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.TOP_CENTER;

public class SidebarBuilderImpl implements Sidebar.Builder {


    private String name;
    private Sidebar.Position position;
    private BooleanProperty isPinned;
    private boolean saveState;
    private NamedThreadSource namedThreadSource;
    private Sidebar.Items.Alignment itemsAlignment;
    private Supplier<List<Sidebar.Item>> initialItems;
    private Sidebar.Behavior.Show show;
    private Sidebar.Behavior.Hide hide;
    private MouseWatcher mouseWatcher;

    public SidebarBuilderImpl() {
        this.name = Sidebar.class.getSimpleName() + "." + ProcessHandle.current().pid();
        this.position = TOP_CENTER;
        this.isPinned = new SimpleBooleanProperty(false);
        this.saveState = true;
        this.namedThreadSource = null;
        this.itemsAlignment = PARALLEL_TO_SIDE;
        this.initialItems = () -> emptyList();
        this.show = Sidebar.Behavior.Show.seconds(0.15);
        this.hide = Sidebar.Behavior.Hide.seconds(0.15);
        this.mouseWatcher = null;
    }

    @Override
    public Sidebar.Builder name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public Sidebar.Builder isPinned(boolean isPinned) {
        this.isPinned.set(isPinned);
        return this;
    }

    @Override
    public Sidebar.Builder isPinned(BooleanProperty isPinned) {
        this.isPinned = isPinned;
        return this;
    }

    @Override
    public Sidebar.Builder saveState(boolean saveState) {
        this.saveState = saveState;
        return this;
    }

    @Override
    public Sidebar.Builder initialPosition(Sidebar.Position initialPosition) {
        this.position = initialPosition;
        return this;
    }

    @Override
    public Sidebar.Builder async(NamedThreadSource async) {
        this.namedThreadSource = async;
        return this;
    }

    @Override
    public Sidebar.Builder itemsAlignment(Sidebar.Items.Alignment alignment) {
        this.itemsAlignment = alignment;
        return this;
    }

    @Override
    public Sidebar.Builder items(Supplier<List<Sidebar.Item>> initialItems) {
        this.initialItems = initialItems;
        return this;
    }

    @Override
    public Sidebar.Builder show(Sidebar.Behavior.Show show) {
        this.show = show;
        return this;
    }

    @Override
    public Sidebar.Builder hide(Sidebar.Behavior.Hide hide) {
        this.hide = hide;
        return this;
    }

    @Override
    public Sidebar.Builder mouseWatcher(MouseWatcher mouseWatcher) {
        this.mouseWatcher = mouseWatcher;
        return this;
    }

    private void apply(SidebarStoredState storedState) {
        this.position = storedState.position();
        this.isPinned.set(storedState.isPinned());
        this.itemsAlignment = storedState.alignment();
        this.show = storedState.show();
        this.hide = storedState.hide();
    }

    @Override
    public Sidebar done() {
        boolean needStartMouseWatcher = false;
        if ( isNull(this.mouseWatcher) ) {
            this.mouseWatcher = new MouseWatcher(10);
            needStartMouseWatcher = true;
        }

        Sidebar sidebar;
        if ( this.saveState ) {
            SidebarStoredState storedInitial = new SidebarStoredState(
                    this.name, this.position, this.isPinned.get(), this.itemsAlignment, this.show, this.hide);

            SidebarStoredState.InFileInitializer initializer = new SidebarStoredState.InFileInitializer(storedInitial);

            InFile<SidebarStoredState> stateInFile = new InFile<>(name, initializer);

            if ( initializer.existing().isPresent() ) {
                this.apply(stateInFile.get());
            }

            sidebar = PlatformActions.doGet(() -> {
                return new SidebarImpl(
                        name,
                        position,
                        isPinned,
                        namedThreadSource,
                        itemsAlignment,
                        initialItems,
                        show,
                        hide,
                        mouseWatcher);
            });

            sidebar.position().addListener((prop, oldPosition, newPosition) -> {
                stateInFile.modifyIfPresent((state) -> {
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
                return new SidebarImpl(
                        name,
                        position,
                        isPinned,
                        namedThreadSource,
                        itemsAlignment,
                        initialItems,
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
