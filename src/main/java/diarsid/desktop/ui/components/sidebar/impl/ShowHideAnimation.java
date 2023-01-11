package diarsid.desktop.ui.components.sidebar.impl;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.util.Duration;

import diarsid.support.objects.references.Possible;
import diarsid.support.objects.references.References;

import static javafx.animation.Animation.Status.RUNNING;

public class ShowHideAnimation {

    private final double hidingTime;
    private final double showingTime;
    private final DoubleProperty mutableValue;
    private final ChangeListener<? super Number> mutableValueListener;
    private final DoubleSupplier getHiddenValue;
    private final DoubleSupplier getShownValue;
    private final DoubleConsumer valueChange;
    private final Runnable onHidingBegins;
    private final Runnable onHidingFinished;
    private final Runnable onShowingBegins;
    private final Runnable onShowingFinished;

    private final Possible<Animation> showing;
    private final Possible<Animation> hiding;

    public ShowHideAnimation(
            double hidingTime,
            double showingTime,
            DoubleSupplier getHiddenValue,
            DoubleSupplier getShownValue,
            DoubleConsumer valueChange,
            Runnable onHidingBegins,
            Runnable onHidingFinished,
            Runnable onShowingBegins,
            Runnable onShowingFinished) {
        this.hidingTime = hidingTime;
        this.showingTime = showingTime;
        this.getHiddenValue = getHiddenValue;
        this.getShownValue = getShownValue;
        this.valueChange = valueChange;
        this.onHidingBegins = onHidingBegins;
        this.onHidingFinished = onHidingFinished;
        this.onShowingBegins = onShowingBegins;
        this.onShowingFinished = onShowingFinished;

        this.mutableValue = new SimpleDoubleProperty(this.getHiddenValue.getAsDouble());
        this.mutableValueListener = (prop, oldV, newV) -> {
            this.valueChange.accept(newV.doubleValue());
        };

        this.showing = References.simplePossibleButEmpty();
        this.hiding = References.simplePossibleButEmpty();
    }

    public void show() {
        if ( this.isShowingNow() ) {
            return;
        }

        if ( this.isHidingNow() ) {
            this.stopHiding();
            this.beginShowingFromLastHidingValue();
        }
        else {
            this.onShowingBegins.run();
            this.beginShowingFromStart();
        }
    }

    public void hide() {
        if ( this.isHidingNow() ) {
            return;
        }

        if ( this.isShowingNow() ) {
            this.stopShowing();
            this.beginHidingFromLastShowingValue();
        }
        else {
            this.onHidingBegins.run();
            this.beginHidingFromStart();
        }
    }

    public boolean isMovingNow() {
        return this.isShowingNow() || this.isHidingNow();
    }

    private boolean isShowingNow() {
        Animation showingNow = this.showing.or(null);
        if ( showingNow == null ) {
            return false;
        }
        else {
            return showingNow.getStatus() == RUNNING;
        }
    }

    private boolean isHidingNow() {
        Animation hidingNow = this.hiding.or(null);
        if ( hidingNow == null ) {
            return false;
        }
        else {
            return hidingNow.getStatus() == RUNNING;
        }
    }

    private void stopShowing() {
        Animation showingNow = this.showing.or(null);

        if ( showingNow == null ) {
            return;
        }

        showingNow.stop();
        this.mutableValue.removeListener(this.mutableValueListener);
        this.showing.nullify();
    }

    private void stopHiding() {
        Animation hidingNow = this.hiding.or(null);

        if ( hidingNow == null ) {
            return;
        }

        hidingNow.stop();
        this.mutableValue.removeListener(this.mutableValueListener);
        this.showing.nullify();
    }

    private void beginShowingFromStart() {
        Timeline newShowing = new Timeline();
        Duration duration = Duration.seconds(this.showingTime);

        double initialValue = this.getHiddenValue.getAsDouble();
        double targetValue = this.getShownValue.getAsDouble();

        this.mutableValue.set(initialValue);

        Interpolator interpolator = Interpolator.EASE_IN;

        KeyValue key = new KeyValue(this.mutableValue, targetValue, interpolator);
        KeyFrame finalPosition = new KeyFrame(duration, key);
        newShowing.getKeyFrames().add(finalPosition);

        newShowing.setOnFinished(event -> {
            this.onShowingFinished.run();
            this.mutableValue.removeListener(this.mutableValueListener);
            this.showing.nullify();
        });

        this.mutableValue.addListener(this.mutableValueListener);
        this.showing.resetTo(newShowing);
        newShowing.playFromStart();
    }

    private void beginShowingFromLastHidingValue() {
        Timeline newShowing = new Timeline();
        Duration duration = Duration.seconds(this.showingTime / 2);

        double targetValue = this.getShownValue.getAsDouble();

        Interpolator interpolator = Interpolator.EASE_IN;

        KeyValue key = new KeyValue(this.mutableValue, targetValue, interpolator);
        KeyFrame finalPosition = new KeyFrame(duration, key);
        newShowing.getKeyFrames().add(finalPosition);

        newShowing.setOnFinished(event -> {
            this.onShowingFinished.run();
            this.mutableValue.removeListener(this.mutableValueListener);
            this.showing.nullify();
        });

        this.mutableValue.addListener(this.mutableValueListener);
        this.showing.resetTo(newShowing);
        newShowing.playFromStart();
    }

    private void beginHidingFromStart() {
        Timeline newHiding = new Timeline();
        Duration duration = Duration.seconds(this.hidingTime);

        double initialValue = this.getShownValue.getAsDouble();
        double targetValue = this.getHiddenValue.getAsDouble();

        this.mutableValue.set(initialValue);

        Interpolator interpolator = Interpolator.EASE_OUT;

        KeyValue key = new KeyValue(this.mutableValue, targetValue, interpolator);
        KeyFrame finalPosition = new KeyFrame(duration, key);
        newHiding.getKeyFrames().add(finalPosition);

        newHiding.setOnFinished(event -> {
            this.onHidingFinished.run();
            this.mutableValue.removeListener(this.mutableValueListener);
            this.hiding.nullify();
        });

        this.mutableValue.addListener(this.mutableValueListener);
        this.hiding.resetTo(newHiding);
        newHiding.playFromStart();
    }

    private void beginHidingFromLastShowingValue() {
        Timeline newHiding = new Timeline();
        Duration duration = Duration.seconds(this.hidingTime);

        double targetValue = this.getHiddenValue.getAsDouble();

        Interpolator interpolator = Interpolator.EASE_OUT;

        KeyValue key = new KeyValue(this.mutableValue, targetValue, interpolator);
        KeyFrame finalPosition = new KeyFrame(duration, key);
        newHiding.getKeyFrames().add(finalPosition);

        newHiding.setOnFinished(event -> {
            this.onHidingFinished.run();
            this.mutableValue.removeListener(this.mutableValueListener);
            this.hiding.nullify();
        });

        this.mutableValue.addListener(this.mutableValueListener);
        this.hiding.resetTo(newHiding);
        newHiding.playFromStart();
    }
}
