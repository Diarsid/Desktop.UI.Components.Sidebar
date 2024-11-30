package diarsid.desktop.ui.components.sidebar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidebar.api.Item;
import diarsid.desktop.ui.components.sidepane.api.Sidepane;
import diarsid.desktop.ui.components.sidebar.impl.items.ItemWithIconAndFile;
import diarsid.desktop.ui.components.sidebar.impl.items.IconsSettings;
import diarsid.desktop.ui.components.sidebar.impl.items.ItemsView;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.javafx.PlatformActions;

import static java.util.UUID.randomUUID;

import static diarsid.desktop.ui.components.sidebar.api.Items.Alignment.PARALLEL_TO_SIDE;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Position.Relative.TOP_CENTER;
import static diarsid.support.tests.concurrency.CurrentThread.blocking;

public class Main {

    private final static Logger log = LoggerFactory.getLogger(Main.class);

    static class LabelItem implements Item {

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
        public String run() {
            return null;
        }

        @Override
        public List<MenuItem> itemContextMenuItems() {
            return List.of(new MenuItem(name + " - A"), new MenuItem(name + " - B"));
        }
    }

    public static void main(String[] args) throws Exception {
        ItemsView itemsView = PlatformActions.doGet(() -> {
            return new ItemsView(
                    PARALLEL_TO_SIDE,
                    () -> {
                        List<Item> initialItems = new ArrayList<>();
                        initialItems.add(new LabelItem("A"));
                        initialItems.add(new LabelItem("B"));
                        initialItems.add(new LabelItem("C"));
                        return initialItems;
                    });
        });


        ///// ----------------------------------------

        PlatformActions.awaitStartup();

        IconsSettings iconsSettings = new IconsSettings(new IconsSettings.BrightnessChange(0.2, -0.2), 50);

        ItemWithIconAndFile notepadPP = new ItemWithIconAndFile("Notepad++", "D:/SOUL/Programs/Links/Dev/Notepad++.lnk", iconsSettings);
        ItemWithIconAndFile projects = new ItemWithIconAndFile("Projects", "D:/DEV/1__Projects", "D:/SOUL/Icons/Projects2.bmp", iconsSettings);

        String name = "sidepane-demo";

//        Sidebar.Position position = new RealManualPosition(TOP, 1798);

        NamedThreadSource namedThreadSource = new NamedThreadSource("sidepane-demo");

//        Sidebar.OnAction.Moved onMoved = (side, min, max) -> {
//            position.setCoordinates(side, min, max);
//            positions.save(position);
//            System.out.println("moved to: " + side.name() + " " + min);
//        };


        Sidepane<List<Item>> sidepane = Sidepane.Builder
                .<List<Item>>create()
                .name(name)
                .initialPosition(TOP_CENTER)
                .isPinned(false)
                .saveState(true)
                .async(namedThreadSource)
                .contentView(itemsView)
                .show(Sidepane.Behavior.Show.seconds(0.15))
                .hide(Sidepane.Behavior.Hide.seconds(0.15))
                .done();

        sidepane.position().addListener((ref, oldPosition, newPosition) -> {
            if ( newPosition.hasRelative() ) {
                System.out.println("moved to: " + newPosition.relativeOrThrow());
            }
            else {
                System.out.println("moved to: " + newPosition.side().name() + " " + newPosition.coordinate());
            }
        });

        sidepane.control().movePermission().addListener((ref, oldValue, newValue) -> {
            System.out.println("is pinned: " + newValue);
        });

        sidepane.control().session().add((touchKind) -> {
            System.out.println("touched - " + touchKind);
        });
        sidepane.control().onTouchDelay().set(300);

        blocking()
                .sleep(5_000)
                .afterSleepNothing();

//        sidebar.control().items().change((items) -> {
//            items.add(new LabelItem("D"));
//            items.add(new LabelItem("E"));
//            items.add(new LabelItem("F"));
//            items.add(new LabelItem("H"));
//            items.add(notepadPP);
//            items.add(projects);
//        });
//        System.out.println("added");

        blocking()
                .sleep(5_000)
                .afterSleepNothing();

//        Item item = sidebar.control().items().all().get(2);
//        ((LabelItem) item).setSize(60);
//        System.out.println("resized");

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
