//package diarsid.desktop.ui.components.sidebar.obsolete;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.locks.Lock;
//import java.util.concurrent.locks.ReentrantReadWriteLock;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import diarsid.desktop.ui.geometry.Rectangle;
//import diarsid.files.objects.store.DefaultObjectStoreListenerOnChanged;
//import diarsid.files.objects.store.DefaultObjectStoreListenerOnCreated;
//import diarsid.files.objects.store.DefaultObjectStoreListenerOnRemoved;
//import diarsid.files.objects.store.FileObjectStore;
//import diarsid.files.objects.store.ObjectStore;
//import diarsid.support.javafx.geometry.Screen;
//
//import static java.util.stream.Collectors.toList;
//
//public class CachedPositionsImpl implements Positions {
//
//    private static final Logger log = LoggerFactory.getLogger(Positions.class);
//
//    private final Lock readLock;
//    private final Lock writeLock;
//    private final ObjectStore<String, StoredPosition> store;
//    private final Map<String, StoredPosition> positionsByNames;
//    private final Map<Rectangle.Side, List<StoredPosition>> positionsBySides;
//
//    public CachedPositionsImpl(ObjectStore<String, StoredPosition> store) {
//        this.store = store;
//        this.positionsByNames = new HashMap<>();
//        this.positionsBySides = new HashMap<>();
//
//        for ( Screen.Side screenSide : Screen.Side.values() ) {
//            this.positionsBySides.put(screenSide, new ArrayList<>());
//        }
//
//        ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
//        this.readLock = readWriteLock.readLock();
//        this.writeLock = readWriteLock.writeLock();
//
//        ObjectStore.Listener.OnCreated<String, StoredPosition> createdListener = new DefaultObjectStoreListenerOnCreated<>(this::addInternally);
//
//        ObjectStore.Listener.OnRemoved removedListener = new DefaultObjectStoreListenerOnRemoved(this::removeInternally);
//
//        ObjectStore.Listener.OnChanged<String, StoredPosition> changedListener = new DefaultObjectStoreListenerOnChanged<>(this::changeInternally);
//
//        this.writeLock.lock();
//        try {
//            store.getAll().forEach(position -> {
//                this.positionsByNames.put(position.name, position);
//                this.positionsBySides.get(position.screenSide()).add(position);
//            });
//        }
//        finally {
//            this.writeLock.unlock();
//        }
//
//        store.subscribe(createdListener);
//        store.subscribe(removedListener);
//        store.subscribe(changedListener);
//    }
//
//    private void addInternally(StoredPosition position) {
//        this.writeLock.lock();
//        try {
//            this.positionsByNames.put(position.name, position);
//            this.positionsBySides.get(position.screenSide()).add(position);
//        }
//        finally {
//            this.writeLock.unlock();
//        }
//    }
//
//    private void removeInternally(String name) {
//        this.writeLock.lock();
//        try {
//            StoredPosition position = this.positionsByNames.remove(name);
//            if ( position == null ) {
//                return;
//            }
//            else {
//                List<StoredPosition> positionsOnSide = this.positionsBySides.get(position.screenSide());
//
//                Optional<StoredPosition> positionOnSide = positionsOnSide
//                        .stream()
//                        .filter(pos -> pos.name.equals(name))
//                        .findFirst();
//
//                if ( positionOnSide.isEmpty() ) {
//                    return;
//                }
//
//                positionsOnSide.remove(positionOnSide.get());
//            }
//        }
//        finally {
//            this.writeLock.unlock();
//        }
//    }
//
//    private void changeInternally(StoredPosition position) {
//        this.writeLock.lock();
//        try {
//            this.positionsByNames.put(position.name, position);
//            List<StoredPosition> positionsOnSide = this.positionsBySides.get(position.screenSide());
//
//            positionsOnSide
//                    .stream()
//                    .filter(pos -> pos.name.equals(position.name))
//                    .findFirst()
//                    .ifPresent(positionsOnSide::remove);
//
//            positionsOnSide.add(position);
//        }
//        finally {
//            this.writeLock.unlock();
//        }
//    }
//
//    @Override
//    public Optional<StoredPosition> find(String name) {
//        this.readLock.lock();
//        try {
//            StoredPosition position = this.positionsByNames.get(name);
//            return Optional.ofNullable(position);
//        }
//        finally {
//            this.readLock.unlock();
//        }
//    }
//
//    @Override
//    public List<StoredPosition> findIntersected(Screen.Side screenSide, double min, double max) {
//        this.readLock.lock();
//        try {
//            return this.positionsBySides
//                    .get(screenSide)
//                    .stream()
//                    .filter(position -> position.doesIntersect(min, max))
//                    .collect(toList());
//        }
//        finally {
//            this.readLock.unlock();
//        }
//    }
//
//    @Override
//    public List<StoredPosition> getAllFromTo(Screen.Side screenSide, double min, double max) {
//        this.readLock.lock();
//        try {
//            return this.positionsBySides
//                    .get(screenSide)
//                    .stream()
//                    .filter(position -> position.doesIntersect(min, max))
//                    .collect(toList());
//        }
//        finally {
//            this.readLock.unlock();
//        }
//    }
//
//    @Override
//    public List<StoredPosition> getAll(Screen.Side screenSide) {
//        this.readLock.lock();
//        try {
//            return new ArrayList<>(this.positionsBySides.get(screenSide));
//        }
//        finally {
//            this.readLock.unlock();
//        }
//    }
//
//    @Override
//    public boolean save(StoredPosition position) {
//        this.writeLock.lock();
//        try {
//            List<StoredPosition> positionsOnSide = this.positionsBySides.get(position.screenSide());
//
//            Optional<StoredPosition> existingPosition = positionsOnSide
//                    .stream()
//                    .filter(pos -> pos.name.equals(position.name))
//                    .findFirst();
//
//            boolean newPositionIntersects;
//
//            if ( existingPosition.isPresent() ) {
//                newPositionIntersects = positionsOnSide
//                        .stream()
//                        .anyMatch(pos -> {
//                            return ! pos.name.equals(existingPosition.get().name) &&
//                            pos.doesIntersect(position);
//                        });
//
//                if ( ! newPositionIntersects ) {
//                    positionsOnSide.add(position);
//                    store.save(position);
//                    return true;
//                }
//                else {
//                    return false;
//                }
//            }
//            else {
//                newPositionIntersects = positionsOnSide
//                        .stream()
//                        .anyMatch(pos -> pos.doesIntersect(position));
//
//                if ( ! newPositionIntersects ) {
//                    positionsOnSide.add(position);
//                    store.save(position);
//                    return true;
//                }
//                else {
//                    return false;
//                }
//            }
//        }
//        finally {
//            this.writeLock.unlock();
//        }
//    }
//
//    @Override
//    public void remove(StoredPosition position) {
//        this.writeLock.lock();
//        try {
//            this.positionsByNames.remove(position.name);
//            List<StoredPosition> positionsOnSide = this.positionsBySides.get(position.screenSide());
//
//            Optional<StoredPosition> existingPosition = positionsOnSide
//                    .stream()
//                    .filter(pos -> pos.name.equals(position.name))
//                    .findFirst();
//
//            if ( existingPosition.isEmpty() ) {
//                return;
//            }
//
//            positionsOnSide.remove(existingPosition.get());
//            store.remove(position.name);
//        }
//        finally {
//            this.writeLock.unlock();
//        }
//    }
//
//    public static ObjectStore<String, StoredPosition> positionFileStore() {
//        Path storePath = Paths.get(System.getProperty("user.home"))
//                .resolve(".java-objects-store")
//                .resolve("desktop-bookmarks")
//                .resolve("positions");
//
//        if (Files.notExists(storePath)) {
//            try {
//                Files.createDirectories(storePath);
//            }
//            catch (IOException e) {
//                throw new IllegalStateException(e);
//            }
//        }
//
//        ObjectStore<String, StoredPosition> positionsStore = new FileObjectStore<>(storePath, StoredPosition.class);
//
//        return positionsStore;
//    }
//}
