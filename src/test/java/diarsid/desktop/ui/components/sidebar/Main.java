package diarsid.desktop.ui.components.sidebar;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidebar.api.Sidebar;
import diarsid.desktop.ui.components.sidebar.api.RealPosition;
import diarsid.desktop.ui.mouse.watching.MouseWatcher;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.javafx.PlatformActions;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Items.Alignment.PARALLEL_TO_SIDE;
import static diarsid.desktop.ui.geometry.Rectangle.Side.TOP;
import static diarsid.support.concurrency.test.CurrentThread.blocking;

public class Main {

    private final static Logger log = LoggerFactory.getLogger(Main.class);

    static class LabelItem implements Sidebar.Item {

        private final String name;
        private final Label label;

        public LabelItem(String name) {
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
    }

    public static void main(String[] args) throws Exception {
        PlatformActions.awaitStartup();

        Sidebar.Position position = new RealPosition(TOP, 1798);

        NamedThreadSource namedThreadSource = new NamedThreadSource("sidebar-demo");
        MouseWatcher mouseWatcher = new MouseWatcher(10);

//        Sidebar.OnAction.Moved onMoved = (side, min, max) -> {
//            position.setCoordinates(side, min, max);
//            positions.save(position);
//            System.out.println("moved to: " + side.name() + " " + min);
//        };

        Sidebar.Session.TouchListener touchListener = (touchKind) -> {
            System.out.println("touched - " + touchKind);
        };

        BooleanProperty isPinned = new SimpleBooleanProperty(false);

        Sidebar sidebar = Sidebar.createInstance(
                "sidebar-demo",
                position,
                isPinned,
                namedThreadSource,
                PARALLEL_TO_SIDE,
                () -> {
                    List<Sidebar.Item> items = new ArrayList<>();
                    items.add(new LabelItem("A"));
                    items.add(new LabelItem("B"));
                    items.add(new LabelItem("C"));
                    return items;
                });

        mouseWatcher.add(sidebar);
        sidebar.position().addListener((ref, oldPosition, newPosition) -> {
            System.out.println("moved to: " + newPosition.side().name() + " " + newPosition.coordinate());
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
