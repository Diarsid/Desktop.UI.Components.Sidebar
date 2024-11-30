package diarsid.desktop.ui.components.sidebar.impl.items;

import java.io.File;

import javafx.scene.image.Image;

import diarsid.files.Extensions;
import diarsid.files.FileInvoker;
import diarsid.filesystem.api.InvokeException;
import diarsid.support.javafx.images.FilesNativeIconImageExtractor;

import static java.util.UUID.randomUUID;

public class ItemWithIconAndFile extends ItemWithIcon {

    private static final FileInvoker FILE_INVOKER = new FileInvoker();
    private static final FilesNativeIconImageExtractor IMAGE_EXTRACTOR = new FilesNativeIconImageExtractor(Extensions.DEFAULT.get());

    public ItemWithIconAndFile(String name, String path, IconsSettings iconsSettings) {
        super(
                randomUUID(),
                name,
                () -> {
                    try {
                        FileInvoker.Invocation invocation = FILE_INVOKER.invoke(path);
                        if ( invocation.fail ) {
                            return invocation + " " + path;
                        }
                        return null;
                    }
                    catch (InvokeException e) {
                        e.printStackTrace();
                        return e.getMessage();
                    }
                },
                IMAGE_EXTRACTOR.getFrom(
                        new File(path),
                        FilesNativeIconImageExtractor.PathCache.USE,
                        FilesNativeIconImageExtractor.ExtensionCache.NO_USE),
                iconsSettings);
    }

    public ItemWithIconAndFile(String name, String path, String imageFile, IconsSettings iconsSettings) {
        super(
                randomUUID(),
                name,
                () -> {
                    try {
                        FileInvoker.Invocation invocation = FILE_INVOKER.invoke(path);
                        if ( invocation.fail ) {
                            return invocation + " " + path;
                        }
                        return null;
                    }
                    catch (InvokeException e) {
                        e.printStackTrace();
                        return e.getMessage();
                    }
                },
                new Image("file:" + imageFile, false),
                iconsSettings);
    }
}
