import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * CN5004 - Part 2 (JavaFX GUI)
 *
 * IMPORTANT:
 * - This single class REPLACES the Part 1 console menu.
 * - It CONNECTS to the Part 1 classes you already submitted:
 *   CarPark, Car, Stay, DiskStore, Config
 * - Do NOT re-submit any other classes. Submit ONLY this file for Part 2.
 *
 * JavaFX design:
 * - Top bar: live capacity banner (Capacity | Occupied | Free).
 * - Left column: Authorisation panel (Register/Unregister).
 * - Center: TabPane with two tables: Authorised list & Parked list.
 * - Right column: Gate operations (Enter/Exit) + status checks.
 * - Bottom: Refresh + Quit.
 *
 * No FXML is used; everything is built in code for a single-file submission.
 */
public class CarParkFX extends Application {

    // ---- Wiring to Part 1 (these classes must exist in your Part 1) ----
    private CarPark carPark; // business logic
    private DiskStore store; // persistence

    // ---- UI model lists ----
    private final ObservableList<CarRow> authorisedRows = FXCollections.observableArrayList();
    private final ObservableList<StayRow> parkedRows     = FXCollections.observableArrayList();

    // ---- UI components we update dynamically ----
    private Label capacityLabel;
    private Label occupiedLabel;
    private Label freeLabel;

    // Left (authorisation)
    private TextField tfPlateRegister;
    private TextField tfOwnerRegister;
    private TextField tfPlateUnregister;

    // Right (gates + check)
    private TextField tfPlateEnter;
    private TextField tfPlateExit;
    private TextField tfPlateCheck;
    private Label     lblCheckAuth;
    private Label     lblCheckInside;

    // Date/time display
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public void start(Stage stage) {
        try {
            // Initialise Part 1 services using your existing Config
            store = new DiskStore(Config.REGISTERED_CSV, Config.PARKED_CSV);
            carPark = new CarPark(Config.CAPACITY, store);
        } catch (IOException e) {
            showError("Failed to initialise storage:\n" + e.getMessage());
            Platform.exit();
            return;
        } catch (Exception e) {
            showError("Initialisation error:\n" + e.getMessage());
            Platform.exit();
            return;
        }

        // ---------- Top capacity banner ----------
        HBox top = buildTopBanner();

        // ---------- Left: Authorisation panel ----------
        VBox left = buildLeftAuthorisationPanel();

        // ---------- Center: Tabbed tables (Authorised / Parked) ----------
        TabPane center = buildCenterTables();

        // ---------- Right: Gate operations + status check ----------
        VBox right = buildRightGatePanel();

        // ---------- Bottom: Refresh + Quit ----------
        HBox bottom = buildBottomBar();

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setLeft(left);
        root.setCenter(center);
        root.setRight(right);
        root.setBottom(bottom);

        BorderPane.setMargin(left, new Insets(10));
        BorderPane.setMargin(center, new Insets(10));
        BorderPane.setMargin(right, new Insets(10));
        BorderPane.setMargin(top, new Insets(10,10,0,10));
        BorderPane.setMargin(bottom, new Insets(0,10,10,10));

        Scene scene = new Scene(root, 950, 560);
        stage.setTitle("CarParkPart1 — JavaFX GUI (Part 2)");
        stage.setScene(scene);
        stage.show();

        // Initial population
        refreshAll();
    }

    // ---------------------- UI builders ----------------------

    private HBox buildTopBanner() {
        capacityLabel = new Label();
        occupiedLabel = new Label();
        freeLabel     = new Label();

        HBox banner = new HBox(20, capacityLabel, occupiedLabel, freeLabel);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(10));
        banner.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #dddddd; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label title = new Label("CAR PARK SYSTEM");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAbout = new Button("About");
        btnAbout.setOnAction(e ->
            showInfo("""
                     CN5004 Part 2
                     • Replaces menu with JavaFX GUI
                     • Uses your Part 1 classes unchanged
                     """));

        HBox top = new HBox(20, title, spacer, btnAbout);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox wrapper = new VBox(6, top, banner);
        return new HBox(wrapper);
    }

    private VBox buildLeftAuthorisationPanel() {
        // Register
        tfPlateRegister = new TextField();
        tfOwnerRegister = new TextField();
        Button btnRegister = new Button("Register");
        btnRegister.setMaxWidth(Double.MAX_VALUE);
        btnRegister.setOnAction(e -> doRegister());

        GridPane reg = new GridPane();
        reg.setHgap(8); reg.setVgap(8);
        reg.add(new Label("Plate:"),   0, 0);
        reg.add(tfPlateRegister,       1, 0);
        reg.add(new Label("Owner:"),   0, 1);
        reg.add(tfOwnerRegister,       1, 1);
        reg.add(btnRegister,           0, 2, 2, 1);

        // Unregister
        tfPlateUnregister = new TextField();
        Button btnUnregister = new Button("Unregister");
        btnUnregister.setMaxWidth(Double.MAX_VALUE);
        btnUnregister.setOnAction(e -> doUnregister());

        GridPane unreg = new GridPane();
        unreg.setHgap(8); unreg.setVgap(8);
        unreg.add(new Label("Plate:"),     0, 0);
        unreg.add(tfPlateUnregister,       1, 0);
        unreg.add(btnUnregister,           0, 1, 2, 1);

        TitledPane tpReg   = new TitledPane("Authorise Vehicle", reg);
        TitledPane tpUnreg = new TitledPane("Remove Authorisation", unreg);
        tpReg.setCollapsible(false);
        tpUnreg.setCollapsible(false);

        VBox left = new VBox(12, tpReg, tpUnreg);
        left.setPrefWidth(280);
        return left;
    }

    private TabPane buildCenterTables() {
        // ---- Authorised table ----
        TableView<CarRow> tblAuth = new TableView<>(authorisedRows);
        TableColumn<CarRow, String> cPlate = new TableColumn<>("Plate");
        cPlate.setCellValueFactory(new PropertyValueFactory<>("plate"));
        cPlate.setPrefWidth(130);

        TableColumn<CarRow, String> cOwner = new TableColumn<>("Owner");
        cOwner.setCellValueFactory(new PropertyValueFactory<>("owner"));
        cOwner.setPrefWidth(200);

        tblAuth.getColumns().addAll(cPlate, cOwner);
        tblAuth.setPlaceholder(new Label("No authorised vehicles"));

        // ---- Parked table ----
        TableView<StayRow> tblParked = new TableView<>(parkedRows);
        TableColumn<StayRow, String> pPlate = new TableColumn<>("Plate");
        pPlate.setCellValueFactory(new PropertyValueFactory<>("plate"));
        pPlate.setPrefWidth(130);

        TableColumn<StayRow, String> pEntry = new TableColumn<>("Entry (ISO)");
        pEntry.setCellValueFactory(new PropertyValueFactory<>("entryIso"));
        pEntry.setPrefWidth(220);

        tblParked.getColumns().addAll(pPlate, pEntry);
        tblParked.setPlaceholder(new Label("No vehicles currently parked"));

        Tab tabAuth   = new Tab("Authorised", tblAuth);
        Tab tabParked = new Tab("Parked", tblParked);
        tabAuth.setClosable(false);
        tabParked.setClosable(false);

        TabPane tabs = new TabPane(tabAuth, tabParked);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }

    private VBox buildRightGatePanel() {
        // Enter
        tfPlateEnter = new TextField();
        Button btnEnter = new Button("ENTER");
        btnEnter.setMaxWidth(Double.MAX_VALUE);
        btnEnter.setOnAction(e -> doEnter());

        GridPane enter = new GridPane();
        enter.setHgap(8); enter.setVgap(8);
        enter.add(new Label("Plate:"), 0, 0);
        enter.add(tfPlateEnter,        1, 0);
        enter.add(btnEnter,            0, 1, 2, 1);

        // Exit
        tfPlateExit = new TextField();
        Button btnExit = new Button("EXIT");
        btnExit.setMaxWidth(Double.MAX_VALUE);
        btnExit.setOnAction(e -> doExit());

        GridPane exit = new GridPane();
        exit.setHgap(8); exit.setVgap(8);
        exit.add(new Label("Plate:"), 0, 0);
        exit.add(tfPlateExit,         1, 0);
        exit.add(btnExit,             0, 1, 2, 1);

        // Check
        tfPlateCheck = new TextField();
        lblCheckAuth = new Label("Authorised: —");
        lblCheckInside = new Label("Currently parked: —");
        Button btnCheck = new Button("Check");
        btnCheck.setOnAction(e -> doCheck());

        GridPane check = new GridPane();
        check.setHgap(8); check.setVgap(8);
        check.add(new Label("Plate:"),   0, 0);
        check.add(tfPlateCheck,          1, 0);
        check.add(btnCheck,              2, 0);
        check.add(lblCheckAuth,          0, 1, 3, 1);
        check.add(lblCheckInside,        0, 2, 3, 1);

        TitledPane tpEnter = new TitledPane("Gate: Enter", enter);
        TitledPane tpExit  = new TitledPane("Gate: Exit",  exit);
        TitledPane tpCheck = new TitledPane("Quick Status", check);
        tpEnter.setCollapsible(false);
        tpExit.setCollapsible(false);
        tpCheck.setCollapsible(false);

        VBox right = new VBox(12, tpEnter, tpExit, tpCheck);
        right.setPrefWidth(320);
        return right;
    }

    private HBox buildBottomBar() {
        Button btnRefresh = new Button("Refresh");
        btnRefresh.setOnAction(e -> refreshAll());

        Button btnExport = new Button("Copy Authorised → Clipboard");
        btnExport.setOnAction(e -> copyAuthorisedToClipboard());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnQuit = new Button("Quit");
        btnQuit.setOnAction(e -> Platform.exit());

        HBox bottom = new HBox(10, btnRefresh, btnExport, spacer, btnQuit);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(8, 0, 0, 0));
        return bottom;
    }

    // ---------------------- Actions ----------------------

    private void doRegister() {
        String plate = upper(tfPlateRegister.getText());
        String owner = safe(tfOwnerRegister.getText());
        if (plate.isEmpty() || owner.isEmpty()) {
            showWarn("Please provide both Plate and Owner.");
            return;
        }
        try {
            carPark.register(plate, owner);
            showInfo("Registered " + plate + " for " + owner + ".");
            tfPlateRegister.clear();
            tfOwnerRegister.clear();
            refreshAll();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void doUnregister() {
        String plate = upper(tfPlateUnregister.getText());
        if (plate.isEmpty()) {
            showWarn("Please provide a Plate to unregister.");
            return;
        }
        try {
            carPark.unregister(plate);
            showInfo("Unregistered " + plate + ".");
            tfPlateUnregister.clear();
            refreshAll();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void doEnter() {
        String plate = upper(tfPlateEnter.getText());
        if (plate.isEmpty()) {
            showWarn("Please provide a Plate to enter.");
            return;
        }
        try {
            carPark.enter(plate);
            showInfo("Vehicle " + plate + " entered.");
            tfPlateEnter.clear();
            refreshAll();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void doExit() {
        String plate = upper(tfPlateExit.getText());
        if (plate.isEmpty()) {
            showWarn("Please provide a Plate to exit.");
            return;
        }
        try {
            Stay s = carPark.exit(plate);
            showInfo("Vehicle " + s.getPlate() + " exited (was parked since " + s.getEntry().format(ISO) + ").");
            tfPlateExit.clear();
            refreshAll();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void doCheck() {
        String plate = upper(tfPlateCheck.getText());
        if (plate.isEmpty()) {
            showWarn("Please provide a Plate to check.");
            return;
        }
        boolean auth = carPark.isAuthorised(plate);
        boolean inside = carPark.isInside(plate);
        lblCheckAuth.setText("Authorised: " + (auth ? "YES" : "NO"));
        lblCheckInside.setText("Currently parked: " + (inside ? "YES" : "NO"));
    }

    private void copyAuthorisedToClipboard() {
        var str = carPark.viewRegistered().stream()
                .map(c -> c.getPlate() + " — " + c.getOwner())
                .collect(Collectors.joining("\n"));
        final var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        final var content = new javafx.scene.input.ClipboardContent();
        content.putString(str);
        clipboard.setContent(content);
        showInfo("Authorised list copied to clipboard.");
    }

    // ---------------------- Refresh helpers ----------------------

    private void refreshAll() {
        // Top banner
        capacityLabel.setText("Capacity: " + carPark.getCapacity());
        occupiedLabel.setText("Occupied: " + carPark.getOccupied());
        freeLabel.setText("Free: " + carPark.getFree());

        // Tables
        authorisedRows.setAll(
                carPark.viewRegistered().stream()
                        .map(c -> new CarRow(c.getPlate(), c.getOwner()))
                        .collect(Collectors.toList())
        );
        parkedRows.setAll(
                carPark.viewParked().stream()
                        .map(s -> new StayRow(s.getPlate(), s.getEntry().format(ISO)))
                        .collect(Collectors.toList())
        );
    }

    // ---------------------- Small DTOs for TableView ----------------------

    /** Simple table row for Authorised list (Plate, Owner). */
    public static class CarRow {
        private final String plate;
        private final String owner;
        public CarRow(String plate, String owner) {
            this.plate = plate; this.owner = owner;
        }
        public String getPlate() { return plate; }
        public String getOwner() { return owner; }
    }

    /** Simple table row for Parked list (Plate, Entry ISO). */
    public static class StayRow {
        private final String plate;
        private final String entryIso;
        public StayRow(String plate, String entryIso) {
            this.plate = plate; this.entryIso = entryIso;
        }
        public String getPlate() { return plate; }
        public String getEntryIso() { return entryIso; }
    }

    // ---------------------- Utilities & Alerts ----------------------

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle("Info");
        a.showAndWait();
    }

    private void showWarn(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle("Warning");
        a.showAndWait();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Operation failed");
        a.setTitle("Error");
        a.setContentText(msg);
        a.showAndWait();
    }

    // Standard JavaFX entry point when running this file directly
    public static void main(String[] args) {
        launch(args);
    }
}
