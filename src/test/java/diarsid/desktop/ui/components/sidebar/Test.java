package diarsid.desktop.ui.components.sidebar;

import diarsid.desktop.ui.components.sidebar.api.AbsolutePosition;
import diarsid.desktop.ui.components.sidebar.api.Sidebar;
import diarsid.desktop.ui.components.sidebar.impl.storedpositions.SidebarStoredPosition;
import diarsid.desktop.ui.components.sidebar.impl.storedpositions.SidebarStoredPositions;
import diarsid.files.objects.InFile;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.LEFT_CENTER;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.TOP_CENTER;
import static diarsid.desktop.ui.geometry.Rectangle.Side.TOP;

public class Test {

    public static void main(String[] args) {
        String name = "sidebar-demo-test-position";

//        Sidebar.Position position = new RealManualPosition(TOP, 1798);
        Sidebar.Position.Relative initialPosition = TOP_CENTER;

        SidebarStoredPositions sidebarStoredPositions = new SidebarStoredPositions();

        InFile<SidebarStoredPosition> storedPositionInFile = sidebarStoredPositions.readOrWriteIfAbsent(name, initialPosition);

        storedPositionInFile.modifyIfPresent(oldP -> {
            return oldP.newWith(new AbsolutePosition(TOP, 300));
        });

        storedPositionInFile.modifyIfPresent(oldP -> {
            return oldP.newWith(LEFT_CENTER);
        });

        var pos = storedPositionInFile.read();
        int a = 5;
    }
}
