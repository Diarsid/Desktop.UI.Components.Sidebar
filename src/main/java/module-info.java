module diarsid.desktop.ui.components.sidebar {

    requires java.desktop;
    requires javafx.controls;
    requires javafx.swing;
    requires org.slf4j;
    requires diarsid.filesystem;
    requires diarsid.support;
    requires diarsid.support.javafx;
    requires diarsid.desktop.ui;

    exports diarsid.desktop.ui.components.sidebar.api;
}
