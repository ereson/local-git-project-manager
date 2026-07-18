module com.localprojectmanager.app {
    requires com.fasterxml.jackson.databind;
    requires java.net.http;
    requires java.sql;
    requires java.desktop;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.xerial.sqlitejdbc;
    requires org.slf4j;

    exports com.localprojectmanager.bootstrap;
    opens com.localprojectmanager.ui to javafx.fxml;
}
