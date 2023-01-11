//package diarsid.desktop.ui.components.sidebar.obsolete;
//
//import java.util.List;
//import java.util.Optional;
//
//import diarsid.desktop.ui.geometry.Rectangle;
//
//public interface Positions {
//
//    static Positions INSTANCE = new CachedPositionsImpl(CachedPositionsImpl.positionFileStore());
//
//    Optional<StoredPosition> find(String name);
//
//    List<StoredPosition> findIntersected(Rectangle.Side screenSide, double min, double max);
//
//    List<StoredPosition> getAllFromTo(Rectangle.Side screenSide, double min, double max);
//
//    List<StoredPosition> getAll(Rectangle.Side screenSide);
//
//    boolean save(StoredPosition position);
//
//    void remove(StoredPosition position);
//}
