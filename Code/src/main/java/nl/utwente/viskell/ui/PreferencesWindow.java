package nl.utwente.viskell.ui;

import java.util.prefs.Preferences;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import nl.utwente.viskell.ghcj.GhciSession;

import com.google.common.collect.ImmutableList;

/**
 * A window for setting preferences.
 */
public class PreferencesWindow extends BorderPane implements ComponentLoader {
    private Stage stage;
    private CustomUIPane pane;
    private Preferences preferences;

    @FXML private ComboBox<GhciSession.Backend> ghci;
    @FXML private ComboBox<String> theme;
    @FXML protected CheckBox debugOverlay;
    @FXML private Button reloadTheme;

    public PreferencesWindow(CustomUIPane customUIPane) {
        super();
        
        pane = customUIPane;
        preferences = Preferences.userNodeForPackage(Main.class);

        stage = new Stage();
        stage.setTitle("Preferences");
        stage.setScene(new Scene(this, 450, 450));

        loadFXML("PreferencesWindow");

        ghci.getItems().setAll(GhciSession.getBackends());
        ghci.getSelectionModel().select(GhciSession.pickBackend());
        ghci.valueProperty().addListener(event -> {
            preferences.put("ghci", ghci.getValue().toString());
            pane.restartBackend();
        });
        
        
        theme.getItems().setAll(ImmutableList.of("/ui/colours.css", "/ui/debugColours.css"));
        theme.getSelectionModel().select(preferences.get("theme", "/ui/colours.css"));
        theme.valueProperty().addListener(event -> {
            preferences.put("theme", theme.getValue());
            refreshTheme();
        });
        
        debugOverlay.setOnAction(event -> {
            Main.debug.setOverlayVisible(debugOverlay.isSelected());
        });
        
        reloadTheme.setOnAction(event -> refreshTheme());
        
        stage.focusedProperty().addListener(new ChangeListener<Boolean>() {
            public void changed(ObservableValue<? extends Boolean> observable, Boolean old, Boolean newVal) {
                if (!newVal) {
                    close();
                }
            }
        });

        refreshTheme();
    }

    protected void refreshTheme() {
        Main.primaryStage.getScene().getStylesheets().clear();
        Main.primaryStage.getScene().getStylesheets().addAll("/ui/layout.css", preferences.get("theme", "/ui/colours.css"));
        stage.getScene().getStylesheets().clear();
        stage.getScene().getStylesheets().addAll("/ui/layout.css", preferences.get("theme", "/ui/colours.css"));
    }

    public void show() {
        stage.show();
    }

    public void close() {
        stage.hide();
    }
}
