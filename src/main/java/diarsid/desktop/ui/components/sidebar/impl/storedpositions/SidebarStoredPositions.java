package diarsid.desktop.ui.components.sidebar.impl.storedpositions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidebar.api.Sidebar;
import diarsid.files.objects.InFile;
import diarsid.filesystem.api.Directory;
import diarsid.filesystem.api.FileSystem;

import static diarsid.files.objects.InFile.Initializer.OnClassExceptionDo.REWRITE_WITH_INITIAL;

public class SidebarStoredPositions {

    private static final Logger log = LoggerFactory.getLogger(SidebarStoredPosition.class);

    private static class StoredPositionInFileInitializer implements InFile.Initializer<SidebarStoredPosition> {

        private final SidebarStoredPosition initial;

        public StoredPositionInFileInitializer(SidebarStoredPosition initial) {
            this.initial = initial;
        }

        @Override
        public SidebarStoredPosition onFileCreatedGetInitial() {
            log.info("[SIDEBAR POSITION] write initial: " + this.initial);
            return this.initial;
        }

        @Override
        public void onFileAlreadyExists(SidebarStoredPosition existingPosition) {
            log.info("[SIDEBAR POSITION] existing: " + existingPosition);
        }

        @Override
        public OnClassExceptionDo doOnClassException(Throwable t) {
            log.info("[SIDEBAR POSITION] existing read exception: " + t.getMessage());
            return REWRITE_WITH_INITIAL;
        }

        @Override
        public Class<SidebarStoredPosition> type() {
            return SidebarStoredPosition.class;
        }
    }

    private final Directory storedPositionsFilesDirectory;

    public SidebarStoredPositions() {
        this.storedPositionsFilesDirectory = FileSystem
                .DEFAULT_INSTANCE
                .userHomeDirectory()
                .orThrow(reason -> new IllegalStateException("User.home directory expected - " + reason))
                .directoryCreateIfNotExists(".java/.in-file-objects/.sidebar")
                .orThrow(reason -> new IllegalStateException("Cannot create or access to directory {user.home}/.java/.in-file-objects/.sidebar - " + reason));
    }

    public InFile<SidebarStoredPosition> readOrWriteIfAbsent(String name, Sidebar.Position initial) {
        SidebarStoredPosition storedInitial = new SidebarStoredPosition(name, initial);

        InFile.Initializer<SidebarStoredPosition> initializer = new StoredPositionInFileInitializer(storedInitial);

        InFile<SidebarStoredPosition> positionInFile = new InFile<>(this.storedPositionsFilesDirectory, name, initializer);

        return positionInFile;
    }
}
