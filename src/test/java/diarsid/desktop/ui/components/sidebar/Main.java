package diarsid.desktop.ui.components.sidebar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidebar.api.Sidebar;
import diarsid.desktop.ui.components.sidebar.impl.storedpositions.SidebarStoredPosition;
import diarsid.desktop.ui.components.sidebar.impl.storedpositions.SidebarStoredPositions;
import diarsid.desktop.ui.mouse.watching.MouseWatcher;
import diarsid.files.objects.InFile;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.javafx.PlatformActions;

import static java.util.UUID.randomUUID;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Items.Alignment.PARALLEL_TO_SIDE;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Items.Alignment.PERPENDICULAR_TO_SIDE;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.BOTTOM_CENTER;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.RIGHT_CENTER;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Position.Relative.TOP_CENTER;
import static diarsid.support.concurrency.test.CurrentThread.blocking;

public class Main {

    private final static Logger log = LoggerFactory.getLogger(Main.class);

    static class LabelItem implements Sidebar.Item {

        private final UUID uuid;
        private final String name;
        private final Label label;

        public LabelItem(String name) {
            this.uuid = randomUUID();
            this.name = name;

            this.label = new Label();
            this.label.setMinHeight(30);
            this.label.setMinWidth(30);
            this.label.setMaxHeight(30);
            this.label.setMaxWidth(30);
        }

        public void setSize(double size) {
            this.label.setMinHeight(size);
            this.label.setMinWidth(size);
            this.label.setMaxHeight(size);
            this.label.setMaxWidth(size);
        }

        @Override
        public Node node() {
            return this.label;
        }

        @Override
        public String name() {
            return "Label-" + this.name;
        }

        @Override
        public UUID uuid() {
            return this.uuid;
        }

        @Override
        public void run() {

        }

        @Override
        public List<MenuItem> itemsContextMenuItems() {
            return List.of(new MenuItem(name + " - A"), new MenuItem(name + " - B"));
        }
    }

    public static void main(String[] args) throws Exception {
        PlatformActions.awaitStartup();

        String name = "sidebar-demo";

//        Sidebar.Position position = new RealManualPosition(TOP, 1798);
        Sidebar.Position.Relative initialPosition = TOP_CENTER;

        SidebarStoredPositions sidebarStoredPositions = new SidebarStoredPositions();

        InFile<SidebarStoredPosition> storedPositionInFile = sidebarStoredPositions.readOrWriteIfAbsent(name, initialPosition);

        NamedThreadSource namedThreadSource = new NamedThreadSource("sidebar-demo");
        MouseWatcher mouseWatcher = new MouseWatcher(10);

//        Sidebar.OnAction.Moved onMoved = (side, min, max) -> {
//            position.setCoordinates(side, min, max);
//            positions.save(position);
//            System.out.println("moved to: " + side.name() + " " + min);
//        };

        Sidebar.Session.Touch.Listener touchListener = (touchKind) -> {
            System.out.println("touched - " + touchKind);
        };

        BooleanProperty isPinned = new SimpleBooleanProperty(false);

        Sidebar sidebar = Sidebar.createInstance(
                name,
                storedPositionInFile.read().position(),
                isPinned,
                namedThreadSource,
                PARALLEL_TO_SIDE,
                () -> {
                    List<Sidebar.Item> items = new ArrayList<>();
                    items.add(new LabelItem("A"));
                    items.add(new LabelItem("B"));
                    items.add(new LabelItem("C"));
                    return items;
                },
                2.0, 2.0);

        mouseWatcher.add(sidebar);
        sidebar.position().addListener((ref, oldPosition, newPosition) -> {
            if ( newPosition.hasRelative() ) {
                System.out.println("moved to: " + newPosition.relativeOrThrow());
            }
            else {
                System.out.println("moved to: " + newPosition.side().name() + " " + newPosition.coordinate());
            }

            storedPositionInFile.modifyIfPresent((oldStoredPosition) -> {
                return oldStoredPosition.newWith(newPosition);
            });
        });
        sidebar.control().session().add(touchListener);

        mouseWatcher.startWork();

        blocking()
                .sleep(5_000)
                .afterSleepNothing();

        sidebar.control().items().change((items) -> {
            items.add(new LabelItem("D"));
            items.add(new LabelItem("E"));
            items.add(new LabelItem("F"));
            items.add(new LabelItem("H"));
        });
        System.out.println("added");

        blocking()
                .sleep(5_000)
                .afterSleepNothing();

        Sidebar.Item item = sidebar.control().items().all().get(2);
        ((LabelItem) item).setSize(60);
        System.out.println("resized");

//        sidebar.control().show();
//        sidebar.control().moveTo(sidebar.position().coordinate() - 50);
//        blocking()
//                .sleep(5000)
//                .afterSleepNothing();
//        sidebar.control().hide();

//        async("move_in_loop")
//                .loopExact(5)
//                .eachTimeSleep(2000)
//                .eachTimeDo(() -> {
//                    double coordinate = 500;
//                    Rectangle.Side currentSide = sidebar.position().get().side();
//                    sidebar.control().moveTo(currentSide.nextByClock(), coordinate);
//                    sidebar.control().session().touch();
//                    System.out.println("moving!!!");
//                });
//
//        awaitFor("move_in_loop");
//
//        sidebar.control().session().unblock("A BLOCK");
//        System.out.println("after unblock");
//
//        blocking()
//                .sleep(10_000)
//                .afterSleepNothing();
//
//        sidebar.control().session().unblock("A BLOCK");
//        System.out.println("after unblock");
    }
}
