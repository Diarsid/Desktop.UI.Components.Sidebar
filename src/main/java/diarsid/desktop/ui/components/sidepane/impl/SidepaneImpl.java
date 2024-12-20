package diarsid.desktop.ui.components.sidepane.impl;

import java.awt.MouseInfo;
import java.awt.Point;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.desktop.ui.components.sidepane.api.Sidepane;
import diarsid.desktop.ui.components.sidepane.impl.areas.SidepaneAreaForTouch;
import diarsid.desktop.ui.components.sidepane.impl.areas.SidepaneAreaWhenHidden;
import diarsid.desktop.ui.components.sidepane.impl.areas.SidepaneAreaWhenShown;
import diarsid.desktop.ui.components.sidepane.impl.contextmenu.SidebarContextMenu;
import diarsid.desktop.ui.geometry.Anchor;
import diarsid.desktop.ui.geometry.PointToSide;
import diarsid.desktop.ui.geometry.Rectangle;
import diarsid.desktop.ui.geometry.Size;
import diarsid.desktop.ui.mouse.watching.MouseWatcher;
import diarsid.desktop.ui.mouse.watching.Watch;
import diarsid.support.concurrency.BlockingPermission;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.exceptions.UnsupportedLogicException;
import diarsid.support.javafx.geometry.Screen;
import diarsid.support.javafx.stage.HiddenStages;
import diarsid.support.javafx.stage.StageMoving;
import diarsid.support.objects.CommonEnum;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import static javafx.css.PseudoClass.getPseudoClass;
import static javafx.scene.input.MouseEvent.MOUSE_PRESSED;

import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Behavior.Type.INSTANT;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Session.Touch.Kind.MANUAL;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.Session.Touch.Kind.PROGRAMMATICAL;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.State.IS_HIDDEN;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.State.IS_HIDING;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.State.IS_SHOWING;
import static diarsid.desktop.ui.components.sidepane.api.Sidepane.State.IS_SHOWN;
import static diarsid.desktop.ui.components.sidepane.impl.SidepaneImpl.NamedTypedSessionAction.Type.BLOCK;
import static diarsid.desktop.ui.components.sidepane.impl.SidepaneImpl.NamedTypedSessionAction.Type.TOUCH_AND_BLOCK;
import static diarsid.desktop.ui.components.sidepane.impl.SidepaneImpl.NamedTypedSessionAction.Type.UNBLOCK;
import static diarsid.desktop.ui.geometry.Rectangle.Side.BOTTOM;
import static diarsid.desktop.ui.geometry.Rectangle.Side.LEFT;
import static diarsid.desktop.ui.geometry.Rectangle.Side.Orientation.VERTICAL;
import static diarsid.desktop.ui.geometry.Rectangle.Side.RIGHT;
import static diarsid.desktop.ui.geometry.Rectangle.Side.TOP;
import static diarsid.support.javafx.geometry.Screen.Type.PHYSICAL;
import static diarsid.support.javafx.stage.StageMoving.MOVE_BY_MOUSE;

public class SidepaneImpl<T> implements
        Sidepane<T>,
        Sidepane.Control<T>,
        Sidepane.Content<T>,
        Sidepane.Session,
        Sidepane.OnTouchDelay,
        Anchor,
        Size {

    private static final Logger log = LoggerFactory.getLogger(SidepaneImpl.class);

    private static final String PROGRAMMATIC_INSTANT_MOVE = "PROGRAMMATIC_MOVE";
    private static final String ADJUSTMENT_MOVE = "ADJUSTMENT_MOVE";

    private static final EnumMap<Side, Map<PseudoClass, Boolean>> PSEUDO_CLASS_ACTIVENESS_BY_SIDE;
    private static final PseudoClass CSS_LEFT = getPseudoClass(LEFT.name().toLowerCase());
    private static final PseudoClass CSS_TOP = getPseudoClass(TOP.name().toLowerCase());
    private static final PseudoClass CSS_RIGHT = getPseudoClass(RIGHT.name().toLowerCase());
    private static final PseudoClass CSS_BOTTOM = getPseudoClass(BOTTOM.name().toLowerCase());
    
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
    private final BooleanProperty isPinned;

    private final Stage stage;
    private final HiddenStages hiddenStages;
    private final StageMoving stageMoving;
    private final Screen screen;
    private final Pane sidebar;
    private final HBox sidebarMargin;

    private final View<T> view;

    private final ObjectProperty<Position.Relative> relative;

    private final ObjectProperty<Rectangle.Side> side;
    private final SidepaneSession session;

    private final SidepaneAreaForTouch touchArea;
    private final SidepaneAreaWhenHidden hiddenArea;
    private final SidepaneAreaWhenShown shownArea;

    private final SidebarContextMenu sidebarContextMenu;

    private final MouseWatcher mouseWatcher;
    private final SidepaneMouseWatch watch;

    private final ShowHideBehavior showHide;

    private final BlockingQueue<QueuedAction> queuedActions;
    private final BlockingPermission queueActionBlockingPermission;
    private final ExecutorService asyncQueuedActions;
    private final Future<?> asyncQueuedActionsProcessing;

    private final IntegerProperty onTouchDelayMillis;

    public SidepaneImpl(
            String name,
            Position position,
            BooleanProperty isPinned,
            NamedThreadSource namedThreadSource,
            View<T> view,
            Behavior.Show show,
            Behavior.Hide hide,
            MouseWatcher mouseWatcher) {

        this.name = name;
        this.state = new SimpleObjectProperty<>();
        this.isPinned = isPinned;

        this.hiddenStages = new HiddenStages();

        this.stage = this.hiddenStages.newHiddenStage();
        this.stage.initStyle(StageStyle.TRANSPARENT);
        this.stage.setAlwaysOnTop(true);
        this.stage.setMinWidth(1);
        this.stage.setMinHeight(1);
        this.stage.setResizable(true);

        this.screen = Screen.screenOf(PHYSICAL);

        this.side = new SimpleObjectProperty<>(position.side());

        this.side.addListener((prop, oldSide, newSide) -> {
            if ( oldSide.is(newSide) ) {
                return;
            }
            this.applyPseudoClass(newSide);
        });

        this.sidebar = new HBox();
        this.sidebar.getStyleClass().add("sidepane");

        this.sidebarMargin = new HBox();
        this.sidebarMargin.getChildren().add(this.sidebar);
        this.sidebarMargin.getStyleClass().add("sidepane-margin");
        this.sidebarMargin.setAlignment(Pos.CENTER);

        this.position = new SimpleObjectProperty<>();
        this.relative = new SimpleObjectProperty<>(null);

        this.sidebarContextMenu = new SidebarContextMenu(this.isPinned, this::moveTo);
        this.sidebarContextMenu.setAutoHide(true);

        this.sidebar.addEventHandler(MOUSE_PRESSED, event -> {
            if ( event.isPrimaryButtonDown() ) {
                this.sidebarContextMenu.removeAdditionalSubmenu();
                this.sidebarContextMenu.hide();
            }
            else if ( event.isSecondaryButtonDown() ) {
                this.sidebarContextMenu.removeAdditionalSubmenu();
                this.sidebarContextMenu.show(this.sidebar, event.getScreenX(), event.getScreenY());
            }
        });

        this.sidebarContextMenu.setOnShowing(this::blockSessionByContextMenu);
        this.sidebarContextMenu.setOnHiding(this::unblockSessionByContextMenu);

        Runnable invokeResize = () -> {
            Platform.runLater(() -> this.adjustSizeAndPositioningAfterStageChange("RESIZE"));
        };

        Runnable hideContextMenu = this.sidebarContextMenu::hide;

        BiConsumer<MouseEvent, Menu> onMenuInvoked = (event, subMenu) -> {
            this.sidebarContextMenu.addSubmenu(subMenu);
            this.sidebarContextMenu.show(this.sidebar, event.getScreenX(), event.getScreenY());
        };

        this.view = view;
        this.view.initOnMount(invokeResize, hideContextMenu, onMenuInvoked);

        this.applyPseudoClass(this.side.get());

        this.arrangeView(this.side.get());

        this.session = new SidepaneSession(
                this.name,
                500,
                namedThreadSource,
                (touchKind) -> this.showSidebar(),
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
        scene.getStylesheets().add("file:./sidepane.css");

        this.stageMoving = new StageMoving(this.stage);

        this.stageMoving.intercept(
                (behavior, stageMove, pointerMove) -> {
                    if ( this.isPinned.get() && ! behavior.equals(ADJUSTMENT_MOVE) ) {
                        stageMove.setIgnore(true);
                        return;
                    }

                    if ( behavior.equals(ADJUSTMENT_MOVE) ) {
                        return;
                    }

                    boolean stageIsOnScreen = this.screen.contains(
                            this.stage.getX(),
                            this.stage.getY());

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

        this.stage.setScene(scene);
        this.stage.sizeToScene();

//        this.stage.setX(this.hiddenArea.x.get());
//        this.stage.setY(this.hiddenArea.y.get());

        this.stage.show();

        Runnable manualTouchSession = () -> {
            this.session.touch(MANUAL);
        };

        double coordinate;
        if ( position instanceof Position.Absolute) {
            coordinate = calculateShownCoordinateOf(this.side.get(), ((Position.Absolute) position).coordinate());
            log.info("[POSITION] relative - NONE");
            this.relative.set(null);
            this.position.set(new CurrentPosition(this.side.get(), coordinate));
        }
        else {
            Position.Relative relativePosition = (Position.Relative) position;
            coordinate = calculateInCenterCoordinateOf(relativePosition);
            log.info("[POSITION] relative - " + relativePosition);
            this.relative.set(relativePosition);
            this.position.set(new CurrentPosition(this.side.get(), coordinate, relativePosition));
        }

        switch ( this.side.get() ) {
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
                throw this.side.get().unsupported();
        }

        this.hiddenArea = new SidepaneAreaWhenHidden(this.screen, this.stage, this.side);
        this.touchArea = new SidepaneAreaForTouch(this.screen, this.stage, this.side);
        this.shownArea = new SidepaneAreaWhenShown(this.screen, this.stage, this.side);

        this.onTouchDelayMillis = new SimpleIntegerProperty(0);

        this.watch = new SidepaneMouseWatch(
                this.name, this.touchArea, manualTouchSession, namedThreadSource, this.onTouchDelayMillis);

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

        this.showHide = new ShowHideAnimation(
                show,
                hide,
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

        long initialTouchMillis;
        if ( show.is(INSTANT) ) {
            initialTouchMillis = 2000;
        }
        else {
            initialTouchMillis = (long) (show.seconds * 1000) + 1000;
        }

        this.session.touchAndBlock(PROGRAMMATICAL, "INITIAL_SHOW", initialTouchMillis);

        this.mouseWatcher = mouseWatcher;
        this.mouseWatcher.add(this);
    }

    private void blockSessionByContextMenu(WindowEvent event) {
        this.session.block("CONTEXT_MENU");
    }

    private void unblockSessionByContextMenu(WindowEvent event) {
        this.session.unblock("CONTEXT_MENU");
    }

    private void interceptOutOfScreenMove(
            String behavior,
            StageMoving.Move.Changeable stageMove,
            StageMoving.Move pointerMove) {
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

    private double distanceFromScreenSide() {
        if ( this.side.get().orientation.is(VERTICAL) ) {
            return this.stage.getWidth();
        }
        else {
            return this.stage.getHeight();
        }
    }

    private void interceptOnScreenMove(
            String behavior,
            StageMoving.Move.Changeable stageMove,
            StageMoving.Move pointerMove) {
        if ( this.isPinned.get() && ! behavior.equals(ADJUSTMENT_MOVE) ) {
            stageMove.setIgnore(true);
            return;
        }

        double height = this.stage.getHeight();
        double width = this.stage.getWidth();
        Rectangle.Side currentSide = this.side.get();

        double screenWidth = this.screen.width();
        double screenHeight = this.screen.height();

        double activeAreaAround = min(height, width);

        double distanceFromScreenSide;
        double activeDistanceFromScreenSide;

        double y = stageMove.y();
        if ( y < 0 ) {
            y = 0;
            stageMove.changeY(y);
        }
        if ( y > screenHeight ) {
            y = screenHeight;
            stageMove.changeY(y);
        }

        if ( y + height > screenHeight ) {
            y = screenHeight - height;
            stageMove.changeY(y);
        }

        double x = stageMove.x();
        if ( x < 0 ) {
            x = 0;
            stageMove.changeX(x);
        }
        if ( x > screenWidth ) {
            x = screenWidth;
            stageMove.changeX(x);
        }

        if ( x + width > screenWidth ) {
            x = screenWidth - width;
            stageMove.changeX(x);
        }

        if ( currentSide.orientation.is(VERTICAL) ) {
            distanceFromScreenSide = width;
            activeDistanceFromScreenSide = distanceFromScreenSide + activeAreaAround;
            if ( activeDistanceFromScreenSide > screenWidth/2 ) {
                activeDistanceFromScreenSide = screenWidth/2;
            }
        }
        else {
            distanceFromScreenSide = height;
            activeDistanceFromScreenSide = distanceFromScreenSide + activeAreaAround;
            if ( activeDistanceFromScreenSide > screenHeight/2 ) {
                activeDistanceFromScreenSide = screenHeight/2;
            }
        }

        PointToSide pointToSide = this.screen.pointToSideInside(pointerMove);

        if ( pointToSide.distance() > activeDistanceFromScreenSide ) {
            stageMove.setIgnore(true);
        }
        else {
            Rectangle.Side nextSide = pointToSide.side();
            if ( nextSide.is(currentSide) ) {
                this.makeMoveOnCurrentSide(stageMove);
            }
            else {
                this.makeMoveTo(nextSide, stageMove);
            }
        }

//        Rectangle.Area area = this.screen.areaOf(pointerMove, Rectangle.Corner.TOP_RIGHT);
//
//        if ( area != CENTRAL ) {
//            if ( ! area.equals(currentSide) ) {
//                if ( area instanceof Rectangle.Corner ) {
//                    PointToCorner distance = this.screen.pointToCorner(
//                            (Rectangle.Corner) area, pointerMove);
//                    Rectangle.Side closerSide = distance.closerSide();
//                    if ( closerSide.is(currentSide) ) {
//                        this.makeMoveOnCurrentSide(stageMove);
//                    }
//                    else {
//                        this.makeMoveTo(closerSide, stageMove);
//                    }
//                }
//                else if ( area instanceof Rectangle.Side ) {
//                    Rectangle.Side nextSide = (Rectangle.Side) area;
//                    this.makeMoveTo(nextSide, stageMove);
//                }
//                else if ( area instanceof OutsideToSide ) {
//                    Rectangle.Side nextSide = ((OutsideToSide) area).side;
//                    this.makeMoveTo(nextSide, stageMove);
//                }
//                else if ( area instanceof OutsideToCorner ) {
//                    throw new UnsupportedLogicException();
//                }
//                else {
//                    throw new UnsupportedLogicException();
//                }
//            }
//            else {
//                this.makeMoveOnCurrentSide(stageMove);
//            }
//        }
//        else {
//            stageMove.setIgnore(true);
//        }
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

        this.view.adoptChange(newSide);
        this.sidebar.getChildren().setAll(this.view.node());
        this.stage.sizeToScene();
    }

    private void applyPseudoClass(Screen.Side side) {
        PSEUDO_CLASS_ACTIVENESS_BY_SIDE
                .get(side)
                .forEach((pseudoClass, active) -> {
                    this.sidebar.pseudoClassStateChanged(pseudoClass, active);
                    this.sidebarMargin.pseudoClassStateChanged(pseudoClass, active);
                    this.view.pseudoClassStateChanged(pseudoClass, active);
                });
    }

    private void showSidebar() {
        this.sidebar.setVisible(true);
        this.stage.sizeToScene();
        this.showHide.show();
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
        this.showHide.hide();
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
    public Control<T> control() {
        return this;
    }

    @Override
    public void close() {
        this.session.dispose();
    }

    @Override
    public OnTouchDelay onTouchDelay() {
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

    @Override
    public void set(ReadOnlyIntegerProperty millis) {
        this.onTouchDelayMillis.unbind();
        this.onTouchDelayMillis.bind(millis);
    }

    @Override
    public long getOrZero() {
        return this.onTouchDelayMillis.get();
    }

    @Override
    public boolean isSet() {
        return this.onTouchDelayMillis.get() > 0;
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
        final long millisBlockToLive;

        NamedTypedSessionAction(Type type, String name) {
            this.type = type;
            this.name = name;
            this.millisBlockToLive = -1;
        }

        NamedTypedSessionAction(Type type, String name, long millisBlockToLive) {
            this.type = type;
            this.name = name;
            this.millisBlockToLive = millisBlockToLive;
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
        System.out.println("queue");
        try {
            this.queuedActions.put(queuedAction);
        }
        catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void process(QueuedAction queuedAction) {
        if ( queuedAction instanceof ContentChange) {
            this.view.adoptGivenChange(((ContentChange<T>) queuedAction).mutation);
            this.sidebar.getChildren().setAll(this.view.node());

            this.adjustSizeAndPositioningAfterStageChange("ITEMS CHANGE");
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
                        if ( namedSessionAction.millisBlockToLive > 0 ) {
                            this.session.touchAndBlock(name, namedSessionAction.millisBlockToLive);
                        }
                        else {
                            this.session.touchAndBlock(name);
                        }
                        break;
                    case BLOCK:
                        if ( namedSessionAction.millisBlockToLive > 0 ) {
                            this.session.block(name, namedSessionAction.millisBlockToLive);
                        }
                        else {
                            this.session.block(name);
                        }
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

    private void adjustSizeAndPositioningAfterStageChange(String reason) {
        this.stage.sizeToScene();

        Position.Relative relativePosition = this.relative.get();
        boolean isShown = this.state.get().is(IS_SHOWN);

        double x;
        double y;

        Side currentSide = this.side.get();

        boolean doMove = true;

        final int effectivelyIgnored = -1;
        final double existingX = this.stage.getX();
        final double existingY = this.stage.getY();

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
            if ( isShown ) {
                switch ( currentSide ) {
                    case TOP:
                        x = effectivelyIgnored;
                        y = effectivelyIgnored;
                        doMove = false;
                        break;
                    case LEFT:
                        x = effectivelyIgnored;
                        y = effectivelyIgnored;
                        doMove = false;
                        break;
                    case RIGHT:
                        x = this.calculateRightShownX();
                        y = existingY;
                        break;
                    case BOTTOM:
                        x = existingX;
                        y = this.calculateBottomShownY();
                        break;
                    default:
                        throw currentSide.unsupported();
                }
            }
            else {
                switch ( currentSide ) {
                    case TOP:
                        x = existingX;
                        y = this.calculateTopHiddenY();
                        break;
                    case LEFT:
                        x = this.calculateLeftHiddenX();
                        y = existingY;
                        break;
                    case RIGHT:
                        x = this.calculateRightHiddenX();
                        y = existingY;
                        break;
                    case BOTTOM:
                        x = effectivelyIgnored;
                        y = effectivelyIgnored;
                        doMove = false;
                        break;
                    default:
                        throw currentSide.unsupported();
                }
            }
        }

        if ( doMove ) {
            this.doInternalMove(x, y, ADJUSTMENT_MOVE);
        }
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

    private double calculateRelativeX(Sidepane.Position.Relative.Place relativePlace) {
        switch ( relativePlace ) {
            case CENTER: return (this.screen.width() - this.stage.getWidth()) / 2;
            default: throw  relativePlace.unsupported();
        }
    }

    private double calculateRelativeXOppositeOrientation(Sidepane.Position.Relative.Place relativePlace) {
        switch ( relativePlace ) {
            case CENTER: return (this.screen.width() - this.stage.getHeight()) / 2;
            default: throw  relativePlace.unsupported();
        }
    }

    private double calculateRelativeY(Sidepane.Position.Relative.Place relativePlace) {
        switch ( relativePlace ) {
            case CENTER: return (this.screen.height() - this.stage.getHeight()) / 2;
            default: throw  relativePlace.unsupported();
        }
    }

    private double calculateRelativeYOppositeOrientation(Sidepane.Position.Relative.Place relativePlace) {
        switch ( relativePlace ) {
            case CENTER: return (this.screen.height() - this.stage.getWidth()) / 2;
            default: throw  relativePlace.unsupported();
        }
    }

    private void doInternalMove(double newX, double newY, String move) {
        System.out.println("internal move");
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
    public Content<T> content() {
        return this;
    }

    @Override
    public void change(Consumer<T> allItemsToChange) {
        this.queueAction(new ContentChange<>(allItemsToChange));
    }

    @Override
    public Watch watch() {
        return this.watch.watch;
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
    public void touchAndBlock(String block, long millisForBlockToExist) {
        this.queueAction(new NamedTypedSessionAction(TOUCH_AND_BLOCK, block, millisForBlockToExist));
    }

    @Override
    public void block(String block) {
        this.queueAction(new NamedTypedSessionAction(BLOCK, block));
    }

    @Override
    public void block(String block, long millisForBlockToExist) {
        this.queueAction(new NamedTypedSessionAction(BLOCK, block, millisForBlockToExist));
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
