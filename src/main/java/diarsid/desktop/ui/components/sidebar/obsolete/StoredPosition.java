//package diarsid.desktop.ui.components.sidebar.obsolete;
//
//import java.time.LocalDateTime;
//
//import diarsid.desktop.ui.components.sidebar.api.Sidebar;
//import diarsid.desktop.ui.geometry.Rectangle;
//import diarsid.support.javafx.geometry.Screen;
//import diarsid.support.model.Identity;
//
//public class StoredPosition implements Identity<String>, Sidebar.Position {
//
//    public final String name;
//    public LocalDateTime time;
//    private Rectangle.Side side;
//    private double coordinateMin;
//    private double coordinateMax;
//    private boolean pinned;
//
//    public StoredPosition(String name, Rectangle.Side side, double min, double max, boolean pinned) {
//        this.name = name;
//
//        if ( min > max ) {
//            double swap = min;
//            min = max;
//            max = swap;
//        }
//
//        this.side = side;
//        this.coordinateMin = min;
//        this.coordinateMax = max;
//        this.pinned = pinned;
//    }
//
//    public double coordinateMin() {
//        return this.coordinateMin;
//    }
//
//    public double coordinateMax() {
//        return this.coordinateMax;
//    }
//
//    public Screen.Side screenSide() {
//        return side;
//    }
//
//    public void setCoordinates(Rectangle.Side side, double min, double max) {
//        if ( min > max ) {
//            double swap = min;
//            min = max;
//            max = swap;
//        }
//
//        this.side = side;
//        this.coordinateMin = min;
//        this.coordinateMax = max;
//    }
//
//    public boolean doesIntersect(StoredPosition other) {
//        return this.doesIntersect(other.coordinateMin, other.coordinateMax);
//    }
//
//    public boolean doesIntersect(double min, double max) {
//        if ( min > max ) {
//            double swap = min;
//            min = max;
//            max = swap;
//        }
//
//        if ( max <= this.coordinateMin ) {
//            return false;
//        }
//
//        if ( this.coordinateMax <= min ) {
//            return false;
//        }
//
//        return true;
//    }
//
//    @Override
//    public String id() {
//        return this.name;
//    }
//
//    @Override
//    public String toString() {
//        return "Position{" +
//                "name=" + name +
//                ", time=" + time +
//                ", coordinateMin=" + coordinateMin +
//                ", coordinateMax=" + coordinateMax +
//                '}';
//    }
//
//    @Override
//    public Rectangle.Side side() {
//        return this.side;
//    }
//
//    @Override
//    public double coordinate() {
//        return this.coordinateMin;
//    }
//}
