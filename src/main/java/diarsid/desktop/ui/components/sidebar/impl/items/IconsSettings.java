package diarsid.desktop.ui.components.sidebar.impl.items;

import java.util.Optional;

public class IconsSettings {

    public static class BrightnessChange {

        public final double onHoverBrighter;
        public final double onPressedDarker;

        public BrightnessChange(double onHoverBrighter, double onPressedDarker) {
            this.onHoverBrighter = onHoverBrighter;
            this.onPressedDarker = onPressedDarker;
        }
    }

    public final Optional<BrightnessChange> brightnessChange;
    public final double size;

    public IconsSettings(double size) {
        this.brightnessChange = Optional.empty();
        this.size = size;
    }

    public IconsSettings(BrightnessChange brightnessChange, double size) {
        this.brightnessChange = Optional.of(brightnessChange);
        this.size = size;
    }
}
