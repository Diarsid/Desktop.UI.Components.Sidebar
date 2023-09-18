package diarsid.desktop.ui.components.sidebar.impl;

import java.awt.MouseInfo;
import java.awt.Point;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidebar.api.Sidebar;
import diarsid.desktop.ui.components.sidebar.impl.contextmenu.SidebarContextMenu;
import diarsid.desktop.ui.components.sidebar.impl.items.SidebarItems;
import diarsid.desktop.ui.geometry.Anchor;
import diarsid.desktop.ui.geometry.PointToCorner;
import diarsid.desktop.ui.geometry.Rectangle;
import diarsid.desktop.ui.geometry.Size;
import diarsid.desktop.ui.mouse.watching.Watch;
import diarsid.support.concurrency.BlockingPermission;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.exceptions.UnsupportedLogicException;
import diarsid.support.javafx.geometry.Screen;
import diarsid.support.javafx.stage.HiddenStages;
import diarsid.support.javafx.stage.StageMoving;
import diarsid.support.objects.CommonEnum;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import static javafx.css.PseudoClass.getPseudoClass;
import static javafx.scene.input.MouseEvent.MOUSE_PRESSED;

import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Items.Alignment.PARALLEL_TO_SIDE;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.Session.Touch.Kind.MANUAL;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.State.IS_HIDDEN;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.State.IS_HIDING;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.State.IS_SHOWING;
import static diarsid.desktop.ui.components.sidebar.api.Sidebar.State.IS_SHOWN;
import static diarsid.desktop.ui.components.sidebar.impl.SidebarImpl.NamedTypedSessionAction.Type.BLOCK;
import static diarsid.desktop.ui.components.sidebar.impl.SidebarImpl.NamedTypedSessionAction.Type.TOUCH_AND_BLOCK;
import static diarsid.desktop.ui.components.sidebar.impl.SidebarImpl.NamedTypedSessionAction.Type.UNBLOCK;
import static diarsid.desktop.ui.geometry.Rectangle.Area.CENTRAL;
import static diarsid.desktop.ui.geometry.Rectangle.Side.BOTTOM;
import static diarsid.desktop.ui.geometry.Rectangle.Side.LEFT;
import static diarsid.desktop.ui.geometry.Rectangle.Side.Orientation.HORIZONTAL;
import static diarsid.desktop.ui.geometry.Rectangle.Side.Orientation.VERTICAL;
import static diarsid.desktop.ui.geometry.Rectangle.Side.RIGHT;
import static diarsid.desktop.ui.geometry.Rectangle.Side.TOP;
import static diarsid.support.javafx.geometry.Screen.Type.PHYSICAL;
import static diarsid.support.javafx.stage.StageMoving.MOVE_BY_MOUSE;

public class SidebarImpl implements
        Sidebar,
        Sidebar.Control,
        Sidebar.Items,
        Sidebar.Session,
        Anchor,
        Size {

    private static final Logger log = LoggerFactory.getLogger(SidebarImpl.class);

    private static final String PROGRAMMATIC_INSTANT_MOVE = "PROGRAMMATIC_MOVE";
    private static final String ADJUSTMENT_MOVE = "ADJUSTMENT_MOVE";

    private static final EnumMap<Side, Map<PseudoClass, Boolean>> PSEUDO_CLASS_ACTIVENESS_BY_SIDE;
    private static final PseudoClass CSS_LEFT = getPseudoClass(LEFT.name().toLowerCase());
    private static final PseudoClass CSS_TOP = getPseudoClass(TOP.name().toLowerCase());
    private static final PseudoClass CSS_RIGHT = getPseudoClass(RIGHT.name().toLowerCase());
    private static final PseudoClass CSS_BOTTOM = getPseudoClass(BOTTOM.name().toLowerCase());

    private static final PseudoClass CSS_ITEMS_VERTICAL = getPseudoClass(VERTICAL.name().toLowerCase());
    private static final PseudoClass CSS_ITEMS_HORIZONTAL = getPseudoClass(HORIZONTAL.name().toLowerCase());
    
    static {
        PSEUDO_CLASS_ACTIVENESS_BY_SIDE = new EnumMap<>(Side.class);

        PSEUDO_CLASS_ACTIVENESS_BY_SIDE.put(TOP, Map.of(
                CSS_LEFT, false,
                CSS_TOP, true,
                CSS_RIGHT, false,
                CSS_BOTTOM, false));

        PSEUDO_CLASS_ACTIVENESS_BY_SIDE.put(LEFT, Map.of(
                CSS_LEFT, true,
                CSS_TOP, false,
                CSS_RIGHT, false,
                CSS_BOTTOM, false));

        PSEUDO_CLASS_ACTIVENESS_BY_SIDE.put(RIGHT, Map.of(
                CSS_LEFT, false,
                CSS_TOP, false,
                CSS_RIGHT, true,
                CSS_BOTTOM, false));

        PSEUDO_CLASS_ACTIVENESS_BY_SIDE.put(BOTTOM, Map.of(
                CSS_LEFT, false,
                CSS_TOP, false,
                CSS_RIGHT, false,
                CSS_BOTTOM, true));
    }

    private final String name;
    private final ObjectProperty<State> state;
    private final ObjectProperty<Position.Current> position;
    private final Items.Alignment itemsAlignment;
    private final BooleanProperty isPinned;

    private final Stage stage;
    private final HiddenStages hiddenStages;
    private final StageMoving stageMoving;
    private final Screen screen;
    private final Pane sidebar;
    private final HBox sidebarMargin;

    private final ObjectProperty<Position.Relative> relative;

    public final ObjectProperty<Rectangle.Side> side;
    public final SidebarSession session;

    private final SidebarItems items;

    private Screen.Side initialSide;

    private final HBox itemsViewHorizontal;
    private final VBox itemsViewVertical;

    private final SidebarAreaForTouch touchArea;
    private final SidebarAreaWhenHidden hiddenArea;
    private final SidebarAreaWhenShown shownArea;

    private final SidebarContextMenu sidebarContextMenu;

    public final Watch watch;
    private final ShowHideAnimation showHideAnimation;

    private final BlockingQueue<QueuedAction> queuedActions;
    private final BlockingPermission queueActionBlockingPermission;
    private final ExecutorService asyncQueuedActions;
    private final Future<?> asyncQueuedActionsProcessing;

    public SidebarImpl(
            String name,
            Position position,
            BooleanProperty isPinned,
            NamedThreadSource namedThreadSource,
            Items.Alignment itemsAlignment,
            Supplier<List<Item>> initialItems,
            double showTime,
            double hideTime) {

        this.name = name;
        this.state = new SimpleObjectProperty<>();
        this.itemsAlignment = itemsAlignment;
        this.isPinned = isPinned;

        this.hiddenStages = new HiddenStages();

        this.stage = this.hiddenStages.newHiddenStage();
        this.stage.initStyle(StageStyle.TRANSPARENT);
        this.stage.setAlwaysOnTop(true);
        this.stage.setMinWidth(1);
        this.stage.setMinHeight(1);
        this.stage.setResizable(true);

        this.screen = Screen.screenOf(PHYSICAL);

        this.initialSide = position.side();
        this.side = new SimpleObjectProperty<>(this.initialSide);

        this.side.addListener((prop, oldSide, newSide) -> {
            if ( oldSide.is(newSide) ) {
                return;
            }
            this.applyPseudoClass(newSide);
        });

        this.sidebar = new HBox();
        this.sidebar.getStyleClass().add("sidebar");

        this.sidebarMargin = new HBox();
        this.sidebarMargin.getChildren().add(this.sidebar);
        this.sidebarMargin.getStyleClass().add("sidebar-margin");
        this.sidebarMargin.setAlignment(Pos.CENTER);

        this.position = new SimpleObjectProperty<>();
        this.relative = new SimpleObjectProperty<>(null);

        this.itemsViewHorizontal = new HBox();
        this.itemsViewHorizontal.getStyleClass().add("sidebar-items");
        this.itemsViewHorizontal.pseudoClassStateChanged(CSS_ITEMS_VERTICAL, false);
        this.itemsViewHorizontal.pseudoClassStateChanged(CSS_ITEMS_HORIZONTAL, true);

        this.itemsViewVertical = new VBox();
        this.itemsViewVertical.getStyleClass().add("sidebar-items");
        this.itemsViewVertical.pseudoClassStateChanged(CSS_ITEMS_VERTICAL, true);
        this.itemsViewVertical.pseudoClassStateChanged(CSS_ITEMS_HORIZONTAL, false);

        this.applyPseudoClass(this.initialSide);

        this.sidebarContextMenu = new SidebarContextMenu(this::moveTo);
        this.sidebarContextMenu.setAutoHide(true);

        this.sidebar.addEventHandler(MOUSE_PRESSED, event -> {
            this.sidebarContextMenu.removeSelectedItemSubmenu();
            if ( event.isPrimaryButtonDown() ) {
                this.sidebarContextMenu.hide();
            }
            else if ( event.isSecondaryButtonDown() ) {
                this.sidebarContextMenu.show(this.sidebar, event.getScreenX(), event.getScreenY());
            }
        });

        this.sidebarContextMenu.setOnShowing(this::blockSessionByContextMenu);
        this.sidebarContextMenu.setOnHiding(this::unblockSessionByContextMenu);

        this.items = new SidebarItems(
                initialItems,
                (prop, oldV, newV) -> {
                    Platform.runLater(this::adjustSizeAndPositioningAfterStageChange);
                },
                this.sidebarContextMenu::hide,
                (event, itemSubMenu) -> {
                    this.sidebarContextMenu.addSelectedItemSubmenu(itemSubMenu);
                    this.sidebarContextMenu.show(this.sidebar, event.getScreenX(), event.getScreenY());
                });

        this.arrangeView(this.initialSide);

        this.session = new SidebarSession(
                this.name,
                500,
                namedThreadSource,
                (touckKind) -> this.showSidebar(),
                this::tryHideSidebar,
                this::canFinishSession);

        this.sidebar.setOnMouseEntered(event -> {
            this.session.touch(MANUAL);
        });

        this.sidebar.setOnMouseMoved(event -> {
            this.session.touch(MANUAL);
        });

        Scene scene = new Scene(this.sidebarMargin);

        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add("file:./sidebar.css");

        this.stageMoving = new StageMoving(this.stage);

        this.stageMoving.intercept(
                (behavior, stageMove, pointerMove) -> {
                    if ( this.isPinned.get() && ! behavior.equals(ADJUSTMENT_MOVE) ) {
                        stageMove.setIgnore(true);
                        return;
                    }

                    boolean stageIsOnScreen = this.screen.contains(
                            this.stage.getX(),
                            this.stage.getY(),
                            this.stage.getWidth(),
                            this.stage.getHeight());

                    if ( stageIsOnScreen ) {
                        this.interceptOnScreenMove(behavior, stageMove, pointerMove);
                    }
                    else {
                        this.interceptOutOfScreenMove(behavior, stageMove, pointerMove);
                    }
                },
                PROGRAMMATIC_INSTANT_MOVE,
                ADJUSTMENT_MOVE);

        this.stageMoving.intercept(
                this::interceptOnScreenMove,
                MOVE_BY_MOUSE);

        this.stageMoving.bindTo(this.sidebar);

        this.stageMoving.beforeMove((behavior, stageMove, mouseMove) -> {
            this.session.block("MOVING");
            if ( behavior.equals(MOVE_BY_MOUSE) ) {
                this.relative.set(null);
            }
        });

        this.stageMoving.afterMove((behavior, stageMove, pointerMove) -> {
            this.session.unblock("MOVING");

            if ( this.isPinned.get()  && ! behavior.equals(ADJUSTMENT_MOVE) ) {
                return;
            }

            double min;
            double max;

            Side side = this.side.get();
            Side.Orientation orientation = side.orientation;

            switch ( orientation ) {
                case VERTICAL: {
                    min = this.stage.getY();
                    max = min + this.stage.getHeight();
                    break;
                }
                case HORIZONTAL: {
                    min = this.stage.getX();
                    max = min + this.stage.getWidth();
                    break;
                }
                default:{
                    throw orientation.unsupported();
                }
            }

            Position.Relative currentRelative = this.relative.get();
            if ( isNull(currentRelative) ) {
                this.position.set(new CurrentPosition(side, min));
            }
            else {
                this.position.set(new CurrentPosition(side, min, currentRelative));
            }
        });

        this.hiddenArea = new SidebarAreaWhenHidden(this.screen, this.stage, this.side);

        this.stage.setScene(scene);
        this.stage.sizeToScene();

        this.stage.setX(this.hiddenArea.x.get());
        this.stage.setY(this.hiddenArea.y.get());

        this.stage.show();

        Runnable manualTouchSession = () -> {
            this.session.touch(MANUAL);
        };

        double coordinate;
        if ( position instanceof Position.Absolute) {
            coordinate = calculateShownCoordinateOf(this.initialSide, ((Position.Absolute) position).coordinate());
            log.info("[POSITION] relative - NONE");
            this.relative.set(null);
            this.position.set(new CurrentPosition(this.initialSide, coordinate));
        }
        else {
            Position.Relative relativePosition = (Position.Relative) position;
            coordinate = calculateInCenterCoordinateOf(relativePosition);
            log.info("[POSITION] relative - " + relativePosition);
            this.relative.set(relativePosition);
            this.position.set(new CurrentPosition(this.initialSide, coordinate, relativePosition));
        }

        switch ( this.initialSide ) {
            case TOP:
                stage.setY(0);
                stage.setX(coordinate);
                break;
            case LEFT:
                stage.setX(0);
                stage.setY(coordinate);
                break;
            case RIGHT:
                stage.setX(this.calculateRightShownX());
                stage.setY(coordinate);
                break;
            case BOTTOM:
                stage.setY(this.calculateBottomShownY());
                stage.setX(coordinate);
                break;
            default:
                throw this.initialSide.unsupported();
        }

        this.touchArea = new SidebarAreaForTouch(this.screen, this.stage, this.side);
        this.shownArea = new SidebarAreaWhenShown(this.screen, this.stage, this.side);

        this.watch = new Watch(
                "Dock:" + this.name,
                (point) -> {
                    return this.touchArea.contains(point.x, point.y);
                },
                (point, isActive) -> {
                    if ( isActive ) {
                        try {
                            Platform.runLater(manualTouchSession);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

        CyclicBarrier actionOnPlatformExecution = new CyclicBarrier(2);
        this.queuedActions = new ArrayBlockingQueue<>(10, true);

        this.queueActionBlockingPermission = new BlockingPermission();

        this.asyncQueuedActions = namedThreadSource.newNamedFixedThreadPool(
                this.getClass().getCanonicalName() + "[" + name + "].queuedActions",
                1);

        Runnable processQueuedActionsInLoop = () -> {
            while ( true ) {
                try {
                    QueuedAction queuedAction = this.queuedActions.take();
                    this.queueActionBlockingPermission.awaitIfForbidden();
                    Platform.runLater(() -> {
                        try {
                            this.process(queuedAction);
                        }
                        finally {
                            try {
                                actionOnPlatformExecution.await();
                            }
                            catch (InterruptedException | BrokenBarrierException e) {
                                log.error("interrupted", e);
                            }
                        }
                    });
                }
                catch (Throwable t) {
                    log.error(t.getMessage(), t);
                }

                try {
                    actionOnPlatformExecution.await();
                }
                catch (InterruptedException | BrokenBarrierException e) {
                    log.error("interrupted", e);
                }

                actionOnPlatformExecution.reset();
            }
        };

        this.asyncQueuedActionsProcessing = this.asyncQueuedActions.submit(processQueuedActionsInLoop);

        this.showHideAnimation = new ShowHideAnimation(
                hideTime,
                showTime,
                /* get hidden coordinate */ () -> {
                    if ( this.side.get().orientation.is(VERTICAL) ) {
                        return this.hiddenArea.anchor().x();
                    }
                    else {
                        return this.hiddenArea.anchor().y();
                    }
                },
                /* get shown coordinate */ () -> {
                    if ( this.side.get().orientation.is(VERTICAL) ) {
                        return this.shownArea.anchor().x();
                    }
                    else {
                        return this.shownArea.anchor().y();
                    }
                },
                /* accept mutated coordinate */ (newValue) -> {
                    if ( this.side.get().orientation.is(VERTICAL) ) {
                        this.stage.setX(newValue);
                    }
                    else {
                        this.stage.setY(newValue);
                    }
                },
                /* on hiding begins */ () -> {
                    this.queueActionBlockingPermission.forbid();
                    this.stageMoving.isMovable().set(false);
                    this.state.set(IS_HIDING);
                },
                /* on hiding finished */ () -> {
                    this.queueActionBlockingPermission.allow();
                    this.stageMoving.isMovable().set(true);
                    this.state.set(IS_HIDDEN);
                    this.sidebar.setVisible(false);
                    Platform.requestNextPulse();
                },
                /* on showing begins */ () -> {
                    this.queueActionBlockingPermission.forbid();
                    this.stageMoving.isMovable().set(false);
                    this.state.set(IS_SHOWING);
                },
                /* on showing finished */ () -> {
                    this.queueActionBlockingPermission.allow();
                    this.stageMoving.isMovable().set(true);
                    this.state.set(IS_SHOWN);
                });

        this.stage.setX(this.hiddenArea.anchor().x());
        this.stage.setY(this.hiddenArea.anchor().y());

        this.state.set(IS_HIDDEN);

//        this.session.touch(PROGRAMMATICAL);
    }

    private void blockSessionByContextMenu(WindowEvent event) {
        this.session.block("CONTEXT_MENU");
    }

    private void unblockSessionByContextMenu(WindowEvent event) {
        this.session.unblock("CONTEXT_MENU");
    }

    private void interceptOutOfScreenMove(String behavior, StageMoving.Move.Changeable stageMove, StageMoving.Move pointerMove) {
        if ( this.isPinned.get()  && ! behavior.equals(ADJUSTMENT_MOVE) ) {
            stageMove.setIgnore(true);
            return;
        }

        Rectangle.Side currentSide = this.side.get();
        Rectangle.Side newSide;

        Area area = this.screen.areaOf(pointerMove);

        if ( area instanceof Rectangle.Side ) {
            newSide = (Side) area;
        }
        else if ( area instanceof Rectangle.Area.Inside ) {
            throw new IllegalStateException();
        }
        else if ( area instanceof OutsideToSide ) {
            newSide = ((OutsideToSide) area).side;
        }
        else if ( area instanceof OutsideToCorner ) {
            OutsideToCorner outsideToCorner = (OutsideToCorner) area;
            newSide = this.screen.closerSideToOuterPointOf(outsideToCorner, pointerMove);
        }
        else {
            throw new UnsupportedLogicException();
        }

        if ( newSide.is(currentSide) ) {
            this.makeOutOfScreenMoveOnGivenSide(currentSide, stageMove);
        }
        else {
            this.applyPseudoClass(newSide);
            this.arrangeView(newSide);
            this.makeOutOfScreenMoveOnGivenSide(newSide, stageMove);
            this.changeCurrentSideTo(newSide);
        }
    }

    private void makeOutOfScreenMoveOnGivenSide(Side side, StageMoving.Move.Changeable stageMove) {
        double x = stageMove.x();
        double y = stageMove.y();

        double width = this.stage.getWidth();
        double height = this.stage.getHeight();

        double freeWidth = this.screen.width() - width;
        double freeHeight = this.screen.height() - height;

        double fixedX;
        double fixedY;
        switch ( side ) {
            case TOP:
                fixedY = this.calculateTopHiddenY();

                if ( y != fixedY ) {
                    stageMove.changeY(fixedY);
                }

                if ( x < 0 ) {
                    stageMove.changeX(0);
                }
                else if ( x > freeWidth) {
                    stageMove.changeX(freeWidth);
                }

                break;
            case LEFT:
                fixedX = this.calculateLeftHiddenX();

                if ( x != fixedX ) {
                    stageMove.changeX(fixedX);
                }

                if ( y < 0 ) {
                    stageMove.changeY(0);
                }
                else if ( y > freeHeight) {
                    stageMove.changeY(freeHeight);
                }

                break;
            case RIGHT:
                fixedX = this.calculateRightHiddenX();

                if ( x != fixedX ) {
                    stageMove.changeX(fixedX);
                }

                if ( y < 0 ) {
                    stageMove.changeY(0);
                }
                else if ( y > freeHeight) {
                    stageMove.changeY(freeHeight);
                }

                break;
            case BOTTOM:
                fixedY = this.calculateBottomHiddenY();

                if ( y != fixedY ) {
                    stageMove.changeY(fixedY);
                }

                if ( x < 0 ) {
                    stageMove.changeX(0);
                }
                else if ( x > freeWidth) {
                    stageMove.changeX(freeWidth);
                }

                break;
            default:
                throw side.unsupported();
        }
    }

    private void interceptOnScreenMove(String behavior, StageMoving.Move.Changeable stageMove, StageMoving.Move pointerMove) {
        if ( this.isPinned.get() && ! behavior.equals(ADJUSTMENT_MOVE) ) {
            stageMove.setIgnore(true);
            return;
        }

        double height = this.stage.getHeight();
        double width = this.stage.getWidth();

        double y = stageMove.y();
        if ( y < 0 ) {
            y = 0;
            stageMove.changeY(y);
        }
        if ( y > this.screen.height() ) {
            y = this.screen.height();
            stageMove.changeY(y);
        }

        if ( y + height > this.screen.height() ) {
            y = this.screen.height() - height;
            stageMove.changeY(y);
        }

        double x = stageMove.x();
        if ( x < 0 ) {
            x = 0;
            stageMove.changeX(x);
        }
        if ( x > this.screen.width() ) {
            x = this.screen.width();
            stageMove.changeX(x);
        }

        if ( x + width > this.screen.width() ) {
            x = this.screen.width() - width;
            stageMove.changeX(x);
        }

        Rectangle.Area area = this.screen.areaOf(pointerMove, Rectangle.Corner.TOP_RIGHT);

        if ( area != CENTRAL ) {
            Rectangle.Side currentSide = this.side.get();
            if ( ! area.equals(currentSide) ) {
                if ( area instanceof Rectangle.Corner ) {
                    PointToCorner distance = this.screen.pointToCorner(
                            (Rectangle.Corner) area, pointerMove);
                    Rectangle.Side closerSide = distance.closerSide();
                    if ( closerSide.is(currentSide) ) {
                        this.makeMoveOnCurrentSide(stageMove);
                    }
                    else {
                        this.makeMoveTo(closerSide, stageMove);
                    }
                }
                else if ( area instanceof Rectangle.Side ) {
                    Rectangle.Side nextSide = (Rectangle.Side) area;
                    this.makeMoveTo(nextSide, stageMove);
                }
                else if ( area instanceof OutsideToSide ) {
                    Rectangle.Side nextSide = ((OutsideToSide) area).side;
                    this.makeMoveTo(nextSide, stageMove);
                }
                else if ( area instanceof OutsideToCorner ) {
                    throw new UnsupportedLogicException();
                }
                else {
                    throw new UnsupportedLogicException();
                }
            }
            else {
                this.makeMoveOnCurrentSide(stageMove);
            }
        }
        else {
            stageMove.setIgnore(true);
        }
    }

    private double calculateInCenterCoordinateOf(Position.Relative relativePosition) {
        if ( relativePosition.side().orientation.is(VERTICAL) ) {
            return this.calculateRelativeY(relativePosition.place());
        }
        else {
            return this.calculateRelativeX(relativePosition.place());
        }
    }

    private double calculateShownCoordinateOf(Side side, double coordinate) {
        double validCoordinate = coordinate;
        double maxCoordinate;

        Side.Orientation orientation = side.orientation;
        switch ( orientation ) {
            case VERTICAL:
                maxCoordinate = this.calculateBottomShownY();
                break;
            case HORIZONTAL:
                maxCoordinate = this.calculateRightShownX();
                break;
            default:
                throw orientation.unsupported();
        }

        if ( validCoordinate > maxCoordinate ) {
            validCoordinate = maxCoordinate;
        }

        return validCoordinate;
    }

    private void changeCurrentSideTo(Screen.Side newSide) {
        this.side.setValue(newSide);
    }

    private void makeMoveOnGivenSide(Screen.Side givenSide, StageMoving.Move.Changeable stageMove) {
        switch ( givenSide ) {
            case TOP:
                stageMove.changeY(0);
                break;
            case LEFT:
                stageMove.changeX(0);
                break;
            case RIGHT:
                stageMove.changeX(this.calculateRightShownX());
                break;
            case BOTTOM:
                stageMove.changeY(this.calculateBottomShownY());
                break;
            default:
                throw givenSide.unsupported();
        }

        this.stage.sizeToScene();
    }

    private void makeMoveOnCurrentSide(StageMoving.Move.Changeable stageMove) {
        this.makeMoveOnGivenSide(this.side.get(), stageMove);
    }

    private void makeMoveTo(Screen.Side newSide, StageMoving.Move.Changeable stageMove) {
        this.applyPseudoClass(newSide);
        this.arrangeView(newSide);
        this.makeMoveOnGivenSide(newSide, stageMove);
        this.changeCurrentSideTo(newSide);
    }

    private void arrangeView(Screen.Side newSide) {
        if ( this.sidebar.getChildren().size() > 0 ) {
            Pane view = (Pane) this.sidebar.getChildren().get(0);
            view.getChildren().clear();
            this.sidebar.getChildren().clear();
        }
        Pane itemsView;

        switch ( newSide.orientation ) {
            case VERTICAL:
                if ( this.itemsAlignment.is(PARALLEL_TO_SIDE) ) {
                    itemsView = this.itemsViewVertical;
                }
                else {
                    itemsView = this.itemsViewHorizontal;
                }
                itemsView.getChildren().addAll(this.items.nodes());

                this.sidebar.getChildren().setAll(itemsView);
                break;
            case HORIZONTAL:
                if ( this.itemsAlignment.is(PARALLEL_TO_SIDE) ) {
                    itemsView = this.itemsViewHorizontal;
                }
                else {
                    itemsView = this.itemsViewVertical;
                }
                itemsView.getChildren().addAll(this.items.nodes());

                this.sidebar.getChildren().setAll(itemsView);
                break;
            default:
                throw newSide.orientation.unsupported();
        }

        this.stage.sizeToScene();
    }

    private void applyPseudoClass(Screen.Side side) {
        PSEUDO_CLASS_ACTIVENESS_BY_SIDE
                .get(side)
                .forEach((pseudoClass, active) -> {
                    this.sidebar.pseudoClassStateChanged(pseudoClass, active);
                    this.sidebarMargin.pseudoClassStateChanged(pseudoClass, active);
                    this.itemsViewHorizontal.pseudoClassStateChanged(pseudoClass, active);
                    this.itemsViewVertical.pseudoClassStateChanged(pseudoClass, active);
                });
    }

    private void showSidebar() {
        this.sidebar.setVisible(true);
        this.stage.sizeToScene();
        this.showHideAnimation.show();
    }

    private void tryHideSidebar() {
        if ( this.sidebar.hoverProperty().get() ) {
            this.session.touch(MANUAL);
        }
        else {
            this.hideSidebar();
        }
    }

    private void hideSidebar() {
        Platform.requestNextPulse();
        this.sidebarContextMenu.hide();
        this.stage.sizeToScene();
        this.showHideAnimation.hide();
    }

    private boolean canFinishSession() {
        double x = this.stage.getX();
        double y = this.stage.getY();
        double x2 = x + this.stage.getWidth();
        double y2 = y + this.stage.getHeight();
        Point mouse = MouseInfo.getPointerInfo().getLocation();
        double mX = mouse.getX();
        double mY = mouse.getY();

        boolean isHover =
                x <= mX && mX <= x2
                &&
                y <= mY && mY <= y2;

        return ! isHover;
    }

    @Override
    public double x() {
        return this.stage.getX();
    }

    @Override
    public double y() {
        return this.stage.getY();
    }

    @Override
    public double width() {
        return this.stage.getWidth();
    }

    @Override
    public double height() {
        return this.stage.getHeight();
    }

    @Override
    public ReadOnlyObjectProperty<Position.Current> position() {
        return this.position;
    }

    @Override
    public ReadOnlyObjectProperty<State> state() {
        return this.state;
    }

    @Override
    public Alignment itemsAlignment() {
        return this.itemsAlignment;
    }

    @Override
    public Control control() {
        return this;
    }

    @Override
    public void close() {
        this.session.dispose();
    }

    @Override
    public Items items() {
        return this;
    }

    @Override
    public Anchor anchor() {
        return this;
    }

    @Override
    public Size size() {
        return this;
    }

    @Override
    public BooleanProperty movePermission() {
        return this.isPinned;
    }

    @Override
    public Node node() {
        return this.sidebarMargin;
    }

    @Override
    public String name() {
        return this.name;
    }

    private static class QueuedAction {
    }

    private static class ItemsChange extends QueuedAction {

        private final Consumer<List<Item>> mutation;

        public ItemsChange(Consumer<List<Item>> mutation) {
            this.mutation = mutation;
        }
    }

    private static class SessionAction extends QueuedAction {

        static final SessionAction TOUCH = new SessionAction();
        static final SessionAction UNBLOCK_ALL = new SessionAction();
    }

    static class NamedTypedSessionAction extends SessionAction {

        static enum Type implements CommonEnum<Type> {
            TOUCH_AND_BLOCK,
            BLOCK,
            UNBLOCK;
        }

        final Type type;
        final String name;

        NamedTypedSessionAction(Type type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    private static class ProgrammaticMove extends QueuedAction {

        final Position.Relative relativePosition;
        final Side to;
        final double coordinate;

        ProgrammaticMove(Side to, double coordinate) {
            this.to = to;
            this.coordinate = coordinate;
            this.relativePosition = null;
        }

        ProgrammaticMove(double coordinate) {
            this.to = null;
            this.coordinate = coordinate;
            this.relativePosition = null;
        }

        ProgrammaticMove(Position.Relative relativePosition) {
            this.coordinate = Double.MIN_VALUE;
            this.relativePosition = relativePosition;
            this.to = relativePosition.side();
        }

        boolean sideIsSameAs(Side side) {
            return isNull(this.to) || this.to.is(side);
        }

        boolean isRelative() {
            return nonNull(this.relativePosition);
        }
    }

    @Override
    public void moveTo(Side offeredSide, double coordinateOnSide) {
        ProgrammaticMove programmaticMove = new ProgrammaticMove(offeredSide, coordinateOnSide);
        queueAction(programmaticMove);
    }

    @Override
    public void moveTo(Position.Relative relativePosition) {
        ProgrammaticMove programmaticMove = new ProgrammaticMove(relativePosition);
        queueAction(programmaticMove);
    }

    @Override
    public void moveTo(double coordinateOnCurrentSide) {
        ProgrammaticMove programmaticMove = new ProgrammaticMove(coordinateOnCurrentSide);
        queueAction(programmaticMove);
    }

    private void queueAction(QueuedAction queuedAction) {
        try {
            this.queuedActions.put(queuedAction);
        }
        catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void process(QueuedAction queuedAction) {
        if ( queuedAction instanceof ItemsChange ) {
            this.items.apply(((ItemsChange) queuedAction).mutation);
            this.rebuildItemsView();
        }
        else if ( queuedAction instanceof ProgrammaticMove) {
            this.process((ProgrammaticMove) queuedAction);
        }
        else {
            if ( queuedAction instanceof NamedTypedSessionAction ) {
                NamedTypedSessionAction namedSessionAction = (NamedTypedSessionAction) queuedAction;
                String name = namedSessionAction.name;
                switch ( namedSessionAction.type ) {
                    case TOUCH_AND_BLOCK:
                        this.session.touchAndBlock(name);
                        break;
                    case BLOCK:
                        this.session.block(name);
                        break;
                    case UNBLOCK:
                        this.session.unblock(name);
                        break;
                    default:
                        throw namedSessionAction.type.unsupported();
                }
            }
            else {
                SessionAction sessionAction = (SessionAction) queuedAction;
                if ( sessionAction == SessionAction.TOUCH ) {
                    this.session.touch();
                }
                else if ( sessionAction == SessionAction.UNBLOCK_ALL ) {
                    this.session.unblock();
                }
                else {
                    throw new UnsupportedLogicException(format("Unknown %s : %s",
                            QueuedAction.class.getSimpleName(),
                            queuedAction.toString()));
                }
            }
        }
    }

    private void rebuildItemsView() {
        Pane childrenView = (Pane) this.sidebar.getChildren().get(0);
        List<Node> currentItemNodes = childrenView.getChildren();
        currentItemNodes.clear();
        currentItemNodes.addAll(this.items.nodes());

        this.adjustSizeAndPositioningAfterStageChange();
    }

    private void adjustSizeAndPositioningAfterStageChange() {
        this.stage.sizeToScene();

        Position.Relative relativePosition = this.relative.get();
        boolean isShown = this.state.get().is(IS_SHOWN);

        double x;
        double y;

        Side currentSide = this.side.get();

        if ( nonNull(relativePosition) ) {
            if ( isShown ) {
                switch ( currentSide ) {
                    case TOP:
                        y = 0;
                        x = this.calculateRelativeX(relativePosition.place());
                        break;
                    case LEFT:
                        x = 0;
                        y = this.calculateRelativeY(relativePosition.place());
                        break;
                    case RIGHT:
                        x = this.calculateRightShownX();
                        y = this.calculateRelativeY(relativePosition.place());
                        break;
                    case BOTTOM:
                        x = this.calculateRelativeX(relativePosition.place());
                        y = this.calculateBottomShownY();
                        break;
                    default:
                        throw currentSide.unsupported();
                }
            }
            else {
                switch ( currentSide ) {
                    case TOP:
                        x = this.calculateRelativeX(relativePosition.place());
                        y = this.calculateTopHiddenY();
                        break;
                    case LEFT:
                        x = this.calculateLeftHiddenX();
                        y = this.calculateRelativeY(relativePosition.place());
                        break;
                    case RIGHT:
                        x = this.calculateRightHiddenX();
                        y = this.calculateRelativeY(relativePosition.place());
                        break;
                    case BOTTOM:
                        x = this.calculateRelativeX(relativePosition.place());
                        y = this.calculateBottomHiddenY();
                        break;
                    default:
                        throw currentSide.unsupported();
                }
            }
        }
        else {
            x = this.stage.getX();
            y = this.stage.getY();
        }

        this.doInternalMove(x, y, ADJUSTMENT_MOVE);
    }

    private void process(ProgrammaticMove move) {
        if ( this.isPinned.get() ) {
            return;
        }

        State state = this.state.get();
        if ( state.isInMove ) {
            this.queueAction(move);
            return;
        }

        boolean isRelativeMove = move.isRelative();
        double coordinate = move.coordinate;

        if ( isRelativeMove ) {
            log.info("[POSITION] relative - " + move.relativePosition);
            this.relative.set(move.relativePosition);
        }
        else {
            log.info("[POSITION] relative - NONE");
            this.relative.set(null);
        }

        double newX;
        double newY;

        double oldX = this.stage.getX();
        double oldY = this.stage.getY();

        Side currentSide = this.side.get();

        if ( move.sideIsSameAs(currentSide) ) {
            Side.Orientation orientation = currentSide.orientation;

            switch ( orientation ) {
                case VERTICAL:
                    if ( isRelativeMove ) {
                        newY = this.calculateRelativeY(move.relativePosition.place());
                    }
                    else {
                        newY = coordinate;
                    }
                    newX = oldX;
                    break;
                case HORIZONTAL:
                    if ( isRelativeMove ) {
                        newX = this.calculateRelativeX(move.relativePosition.place());
                    }
                    else {
                        newX = coordinate;
                    }
                    newY = oldY;
                    break;
                default:
                    throw orientation.unsupported();
            }
        }
        else {
            Side newSide = move.to;
            boolean isShown = state.is(IS_SHOWN);
            boolean areOldNewOrientationSame = currentSide.orientation.is(newSide.orientation);

            switch ( newSide ) {
                case TOP:
                    if ( isRelativeMove ) {
                        if ( areOldNewOrientationSame ) {
                            newX = this.calculateRelativeX(move.relativePosition.place());
                        }
                        else {
                            newX = this.calculateRelativeXOppositeOrientation(move.relativePosition.place());
                        }
                    }
                    else {
                        newX = coordinate;
                    }

                    if ( isShown ) {
                        newY = 0;
                    }
                    else {
                        if ( areOldNewOrientationSame ) {
                            newY = this.calculateTopHiddenY();
                        }
                        else {
                            newY = this.calculateTopHiddenYOppositeOrientation();
                        }
                    }

                    break;
                case LEFT:
                    if ( isRelativeMove ) {
                        if ( areOldNewOrientationSame ) {
                            newY = this.calculateRelativeY(move.relativePosition.place());
                        }
                        else {
                            newY = this.calculateRelativeYOppositeOrientation(move.relativePosition.place());
                        }
                    }
                    else {
                        newY = coordinate;
                    }

                    if ( isShown ) {
                        newX = 0;
                    }
                    else {
                        if ( areOldNewOrientationSame ) {
                            newX = this.calculateLeftHiddenX();
                        }
                        else {
                            newX = this.calculateLeftHiddenXOppositeOrientation();
                        }
                    }
                    break;
                case RIGHT:
                    if ( isRelativeMove ) {
                        if ( areOldNewOrientationSame ) {
                            newY = this.calculateRelativeY(move.relativePosition.place());
                        }
                        else {
                            newY = this.calculateRelativeYOppositeOrientation(move.relativePosition.place());
                        }
                    }
                    else {
                        newY = coordinate;
                    }

                    if ( isShown ) {
                        if ( areOldNewOrientationSame ) {
                            newX = this.calculateRightShownX();
                        }
                        else {
                            newX = this.calculateRightShownXOppositeOrientation();
                        }
                    }
                    else {
                        if ( areOldNewOrientationSame ) {
                            newX = this.calculateRightHiddenX();
                        }
                        else {
                            newX = this.calculateRightHiddenXOppositeOrientation();
                        }
                    }
                    break;
                case BOTTOM:
                    if ( isRelativeMove ) {
                        if ( areOldNewOrientationSame ) {
                            newX = this.calculateRelativeX(move.relativePosition.place());
                        }
                        else {
                            newX = this.calculateRelativeXOppositeOrientation(move.relativePosition.place());
                        }
                    }
                    else {
                        newX = coordinate;
                    }

                    if ( isShown ) {
                        if ( areOldNewOrientationSame ) {
                            newY = this.calculateBottomShownY();
                        }
                        else {
                            newY = this.calculateBottomShownYOppositeOrientation();
                        }
                    }
                    else {
                        if ( areOldNewOrientationSame ) {
                            newY = this.calculateBottomHiddenY();
                        }
                        else {
                            newY = this.calculateBottomHiddenYOppositeOrientation();
                        }
                    }
                    break;
                default:
                    throw newSide.unsupported();
            }
        }

        this.doInternalMove(newX, newY, PROGRAMMATIC_INSTANT_MOVE);
    }

    private double calculateRightShownX() {
        return this.screen.width() - this.stage.getWidth();
    }

    private double calculateRightShownXOppositeOrientation() {
        return this.screen.width() - this.stage.getHeight();
    }

    private double calculateBottomShownY() {
        return this.screen.height() - this.stage.getHeight();
    }

    private double calculateBottomShownYOppositeOrientation() {
        return this.screen.height() - this.stage.getWidth();
    }

    private double calculateRelativeX(Sidebar.Position.Relative.Place relativePlace) {
        switch ( relativePlace ) {
            case CENTER: return (this.screen.width() - this.stage.getWidth()) / 2;
            default: throw  relativePlace.unsupported();
        }
    }

    private double calculateRelativeXOppositeOrientation(Sidebar.Position.Relative.Place relativePlace) {
        switch ( relativePlace ) {
            case CENTER: return (this.screen.width() - this.stage.getHeight()) / 2;
            default: throw  relativePlace.unsupported();
        }
    }

    private double calculateRelativeY(Sidebar.Position.Relative.Place relativePlace) {
        switch ( relativePlace ) {
            case CENTER: return (this.screen.height() - this.stage.getHeight()) / 2;
            default: throw  relativePlace.unsupported();
        }
    }

    private double calculateRelativeYOppositeOrientation(Sidebar.Position.Relative.Place relativePlace) {
        switch ( relativePlace ) {
            case CENTER: return (this.screen.height() - this.stage.getWidth()) / 2;
            default: throw  relativePlace.unsupported();
        }
    }

    private void doInternalMove(double newX, double newY, String move) {
        boolean movable = this.stageMoving.isMovable().get();
        try {
            if ( ! movable ) {
                this.stageMoving.isMovable().set(true);
            }
            this.stageMoving.move(newX, newY, move);
        }
        finally {
            this.stageMoving.isMovable().set(movable);
        }
    }

    private double calculateTopHiddenY() {
        return 0 + this.hiddenArea.insetOf(TOP) - this.stage.getHeight();
    }

    private double calculateTopHiddenYOppositeOrientation() {
        return 0 + this.hiddenArea.insetOf(TOP) - this.stage.getWidth();
    }

    private double calculateBottomHiddenY() {
        return this.screen.height() + this.hiddenArea.insetOf(BOTTOM);
    }

    private double calculateBottomHiddenYOppositeOrientation() {
        return this.screen.height() + this.hiddenArea.insetOf(BOTTOM);
    }

    private double calculateLeftHiddenX() {
        return 0 + this.hiddenArea.insetOf(LEFT) - this.stage.getWidth();
    }

    private double calculateLeftHiddenXOppositeOrientation() {
        return 0 + this.hiddenArea.insetOf(LEFT) - this.stage.getHeight();
    }

    private double calculateRightHiddenX() {
        return this.screen.width() + this.hiddenArea.insetOf(RIGHT);
    }

    private double calculateRightHiddenXOppositeOrientation() {
        return this.screen.width() + this.hiddenArea.insetOf(RIGHT);
    }

    @Override
    public Session session() {
        return this;
    }

    @Override
    public List<Item> all() {
        return this.items.unmodifiableList();
    }

    @Override
    public void change(Consumer<List<Item>> allItemsToChange) {
        this.queueAction(new ItemsChange(allItemsToChange));
    }

    @Override
    public Watch watch() {
        return this.watch;
    }

    @Override
    public boolean isActive() {
        return this.session.isActive();
    }

    @Override
    public void touch() {
        this.queueAction(SessionAction.TOUCH);
    }

    @Override
    public void touchAndBlock(String block) {
        this.queueAction(new NamedTypedSessionAction(TOUCH_AND_BLOCK, block));
    }

    @Override
    public void block(String block) {
        this.queueAction(new NamedTypedSessionAction(BLOCK, block));
    }

    @Override
    public void unblock(String block) {
        this.queueAction(new NamedTypedSessionAction(UNBLOCK, block));
    }

    @Override
    public void unblock() {
        this.queueAction(SessionAction.UNBLOCK_ALL);
    }

    @Override
    public boolean isBlocked() {
        return this.session.isBlocked();
    }

    @Override
    public boolean hasBlock(String name) {
        return this.session.hasBlock(name);
    }

    @Override
    public void add(Touch.Listener touchListener) {
        this.session.add(touchListener);
    }

    @Override
    public boolean remove(Touch.Listener touchListener) {
        return this.session.remove(touchListener);
    }
}
