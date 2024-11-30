package diarsid.desktop.ui.components.sidebar.api;

import java.util.List;
import java.util.function.Consumer;

import diarsid.support.objects.CommonEnum;

public interface Items {

    enum Alignment implements CommonEnum<Alignment> {

        PARALLEL_TO_SIDE,
        PERPENDICULAR_TO_SIDE;
    }

    Alignment itemsAlignment();

    /* Intended to be read-only.
     * Returns unmodifiable List or copy of original List though it's changes will be ignored. In order
     * to change items list use change(Consumer<List<Item>>) method.
     */
    List<Item> all();

    /*
     * Passes actual modifiable list of Items.
     * All changes made to passed list will be reflected on sidebar when method returns.
     * It is possible to change items completely or even remove all ot them using this method.         *
     */
    void change(Consumer<List<Item>> allItemsToChange);

}
