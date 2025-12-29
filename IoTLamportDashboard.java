import com.google.gson.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class IoTLamportDashboard extends Application {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;
    private Gson gson = new Gson();

    private TextArea logArea;
    private Label statusLabel;
    private Map<String, Label> processStatusLabels = new HashMap<>();
    private Map<String, Label> processHiLabels = new HashMap<>();
    private Map<String, Label> processFAiLabels = new HashMap<>();
    private VBox processStatusContainer;
    private Label csIndicator;
    private Label csCurrentProcessLabel;
    private Label totalProcessesLabel;
    
    private Label tempValueLabel;
    private Label humValueLabel;
    private Label pressValueLabel;
    private Label tempTimestampLabel;
    private Label humTimestampLabel;
    private Label pressTimestampLabel;

    private String currentUser = null;
    private String currentProcessInCS = null;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("🌐 IoT Distribué - Lamport");

        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #1e1e2e;");

        mainLayout.setTop(createHeader());
        
        ScrollPane centerScrollPane = new ScrollPane(createMainPanel());
        centerScrollPane.setFitToWidth(true);
        centerScrollPane.setFitToHeight(true);
        centerScrollPane.setStyle("-fx-background: #1e1e2e; -fx-background-color: #1e1e2e;");
        centerScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        centerScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        mainLayout.setCenter(centerScrollPane);
        
        ScrollPane leftScrollPane = new ScrollPane(createAuthPanel());
        leftScrollPane.setFitToWidth(true);
        leftScrollPane.setStyle("-fx-background: #2d2d44; -fx-background-color: #2d2d44;");
        leftScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        mainLayout.setLeft(leftScrollPane);

        Scene scene = new Scene(mainLayout, 1700, 950);
        primaryStage.setScene(scene);
        primaryStage.show();

        addLog("✓ Interface initialisée");
        addLog("⚙️ En attente de connexion...");
        
        primaryStage.setOnCloseRequest(e -> disconnect());
    }

    // ===================== Connexion Socket =====================
    private void connectToServer() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            addLog("✅ Connecté au serveur " + SERVER_HOST + ":" + SERVER_PORT);
            Platform.runLater(() -> {
                statusLabel.setText("● Système connecté");
                statusLabel.setTextFill(Color.web("#51cf66"));
            });
            
            startListening();
            
        } catch (IOException e) {
            addLog("❌ Erreur de connexion: " + e.getMessage());
            Platform.runLater(() -> {
                statusLabel.setText("● Erreur connexion");
                statusLabel.setTextFill(Color.web("#ff6b6b"));
            });
        }
    }

    private void startListening() {
        listenerThread = new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    handleServerMessage(message);
                }
            } catch (IOException e) {
                addLog("❌ Connexion perdue avec le serveur");
                Platform.runLater(() -> {
                    statusLabel.setText("● Système arrêté");
                    statusLabel.setTextFill(Color.web("#ff6b6b"));
                });
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void handleServerMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String action = json.has("action") ? json.get("action").getAsString() : "";
            String status = json.has("status") ? json.get("status").getAsString() : "";

            if (action.equals("lamport_logs")) {
                JsonArray logs = json.getAsJsonArray("logs");
                for (JsonElement log : logs) {
                    addLog(log.getAsString());
                }
            } else if (action.equals("capteurs_data")) {
                JsonArray capteurs = json.getAsJsonArray("data");
                addLog("📊 Données capteurs reçues: " + capteurs.size() + " éléments");
            } else if (status.equals("ok") || status.equals("error")) {
                String msg = json.has("message") ? json.get("message").getAsString() : status;
                addLog("📩 Serveur: " + msg);
            }
        } catch (Exception e) {
            addLog("❌ Erreur parsing message: " + e.getMessage());
        }
    }

    // ===================== Envoi de requêtes =====================
    private void sendRequest(String action, JsonObject data) {
        if (socket == null || socket.isClosed()) {
            addLog("⚠️ Pas de connexion au serveur");
            return;
        }
        
        data.addProperty("action", action);
        out.println(gson.toJson(data));
    }

    // ===================== UI =====================
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20));
        header.setAlignment(Pos.CENTER);
        header.setStyle("-fx-background-color: #1e1e2e;");
        
        Label title = new Label("🌐 Système Distribué IoT - Algorithme de Lamport");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.WHITE);
        
        statusLabel = new Label("● Système arrêté");
        statusLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        statusLabel.setTextFill(Color.web("#ff6b6b"));
        
        header.getChildren().addAll(title, statusLabel);
        return header;
    }

    private VBox createMainPanel() {
        VBox mainPanel = new VBox(15);
        mainPanel.setPadding(new Insets(20));
        mainPanel.setMinWidth(900);
        
        mainPanel.getChildren().add(createSensorDataPanel());
        mainPanel.getChildren().add(createProcessStatusPanel());
        
        return mainPanel;
    }

    private VBox createSensorDataPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: #2d2d44; -fx-background-radius: 10;");

        Label title = new Label("📊 Données des Capteurs (Section Critique)");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);
        
        HBox sensorsBox = new HBox(20);
        sensorsBox.setAlignment(Pos.CENTER);
        sensorsBox.setPadding(new Insets(20, 0, 10, 0));

        VBox tempBox = createSensorBox("🌡️ Température", "°C", "#ff6b6b");
        tempValueLabel = (Label) ((VBox) tempBox.getChildren().get(0)).getChildren().get(0);
        tempTimestampLabel = (Label) ((VBox) tempBox.getChildren().get(0)).getChildren().get(2);

        VBox humBox = createSensorBox("💧 Humidité", "%", "#51cf66");
        humValueLabel = (Label) ((VBox) humBox.getChildren().get(0)).getChildren().get(0);
        humTimestampLabel = (Label) ((VBox) humBox.getChildren().get(0)).getChildren().get(2);

        VBox pressBox = createSensorBox("🌡️ Pression", "hPa", "#339af0");
        pressValueLabel = (Label) ((VBox) pressBox.getChildren().get(0)).getChildren().get(0);
        pressTimestampLabel = (Label) ((VBox) pressBox.getChildren().get(0)).getChildren().get(2);

        sensorsBox.getChildren().addAll(tempBox, humBox, pressBox);
        
        Label note = new Label("💡 Les processus capteurs écrivent les données | Les clients lisent les données");
        note.setFont(Font.font("System", FontWeight.NORMAL, 12));
        note.setTextFill(Color.web("#aaa"));
        note.setAlignment(Pos.CENTER);
        note.setMaxWidth(Double.MAX_VALUE);

        panel.getChildren().addAll(title, sensorsBox, note);
        return panel;
    }

    private VBox createSensorBox(String name, String unit, String color) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: #1e1e2e; -fx-background-radius: 8;");
        box.setPrefWidth(280);
        box.setMinWidth(250);

        VBox valueBox = new VBox(5);
        valueBox.setAlignment(Pos.CENTER);
        
        Label valueLabel = new Label("--");
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 36));
        valueLabel.setTextFill(Color.web(color));

        Label unitLabel = new Label(unit);
        unitLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        unitLabel.setTextFill(Color.web("#aaa"));

        Label timestampLabel = new Label("Dernière mise à jour: --");
        timestampLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        timestampLabel.setTextFill(Color.web("#666"));

        valueBox.getChildren().addAll(valueLabel, unitLabel, timestampLabel);
        
        Label nameLabel = new Label(name);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        nameLabel.setTextFill(Color.WHITE);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        box.getChildren().addAll(valueBox, nameLabel);
        return box;
    }

    private VBox createProcessStatusPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: #2d2d44; -fx-background-radius: 10;");

        Label titleLabel = new Label("⚙️ État des Processus Lamport");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        totalProcessesLabel = new Label("Total: 0 processus");
        totalProcessesLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        totalProcessesLabel.setTextFill(Color.web("#aaa"));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        headerBox.getChildren().addAll(spacer, totalProcessesLabel);

        processStatusContainer = new VBox(10);
        processStatusContainer.setPadding(new Insets(10, 0, 0, 0));
        
        ScrollPane processScrollPane = new ScrollPane(processStatusContainer);
        processScrollPane.setFitToWidth(true);
        processScrollPane.setPrefHeight(300);
        processScrollPane.setMaxHeight(400);
        processScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        processScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        processScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        HBox csBox = new HBox(10);
        csBox.setAlignment(Pos.CENTER);
        csBox.setPadding(new Insets(15));
        csBox.setStyle("-fx-background-color: #1e1e2e; -fx-background-radius: 8;");
        
        Label csLabel = new Label("Section Critique:");
        csLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        csLabel.setTextFill(Color.WHITE);
        
        csIndicator = new Label("○ Libre");
        csIndicator.setFont(Font.font("System", FontWeight.BOLD, 16));
        csIndicator.setTextFill(Color.web("#555"));
        
        csCurrentProcessLabel = new Label("");
        csCurrentProcessLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        csCurrentProcessLabel.setTextFill(Color.web("#aaa"));
        
        csBox.getChildren().addAll(csLabel, csIndicator, csCurrentProcessLabel);

        initializeSensorProcesses();

        panel.getChildren().addAll(titleLabel, headerBox, processScrollPane, csBox);
        return panel;
    }

    private void initializeSensorProcesses() {
        addProcessToUI("Process 0", "Capteur-Temperature", "#ff6b6b");
        addProcessToUI("Process 1", "Capteur-Humidite", "#51cf66");
        addProcessToUI("Process 2", "Capteur-Pression", "#339af0");
        updateTotalProcessCount();
    }

    private void addProcessToUI(String processId, String name, String color) {
        VBox processBox = new VBox(8);
        processBox.setPadding(new Insets(12));
        processBox.setStyle("-fx-background-color: #1e1e2e; -fx-background-radius: 5;");
        processBox.setMinWidth(600);

        HBox headerRow = new HBox(15);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label idLabel = new Label(processId);
        idLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        idLabel.setTextFill(Color.web(color));
        idLabel.setPrefWidth(90);
        idLabel.setMinWidth(90);

        Label nameLabel = new Label(name);
        nameLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        nameLabel.setTextFill(Color.web("#ccc"));
        nameLabel.setPrefWidth(200);
        nameLabel.setMinWidth(200);

        Label statusLabel = new Label("○ En attente");
        statusLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        statusLabel.setTextFill(Color.web("#666"));

        headerRow.getChildren().addAll(idLabel, nameLabel, statusLabel);

        HBox detailsRow = new HBox(20);
        detailsRow.setAlignment(Pos.CENTER_LEFT);
        detailsRow.setPadding(new Insets(5, 0, 0, 10));

        Label hiLabel = new Label("Hi: 0");
        hiLabel.setFont(Font.font("Courier New", FontWeight.NORMAL, 10));
        hiLabel.setTextFill(Color.web("#888"));
        hiLabel.setMinWidth(60);

        Label faiLabel = new Label("FAi: []");
        faiLabel.setFont(Font.font("Courier New", FontWeight.NORMAL, 10));
        faiLabel.setTextFill(Color.web("#888"));
        faiLabel.setWrapText(true);
        faiLabel.setMaxWidth(450);

        detailsRow.getChildren().addAll(hiLabel, faiLabel);

        processBox.getChildren().addAll(headerRow, detailsRow);
        processStatusContainer.getChildren().add(processBox);
        
        processStatusLabels.put(processId, statusLabel);
        processHiLabels.put(processId, hiLabel);
        processFAiLabels.put(processId, faiLabel);
    }

    private void updateTotalProcessCount() {
        int count = processStatusLabels.size();
        Platform.runLater(() -> {
            totalProcessesLabel.setText("Total: " + count + " processus");
        });
    }

    private VBox createAuthPanel() {
        VBox authPanel = new VBox(10);
        authPanel.setPadding(new Insets(20));
        authPanel.setStyle("-fx-background-color: #2d2d44;");
        authPanel.setPrefWidth(450);
        authPanel.setMinWidth(400);

        Label authTitle = new Label("👤 Connexion / Inscription");
        authTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        authTitle.setTextFill(Color.WHITE);

        Button connectBtn = new Button("🔌 Connecter au serveur");
        connectBtn.setStyle("-fx-background-color: #339af0; -fx-text-fill: white; -fx-font-weight: bold;");
        connectBtn.setPrefWidth(200);
        connectBtn.setOnAction(e -> connectToServer());

        TextField usernameField = new TextField();
        usernameField.setPromptText("Nom d'utilisateur");
        usernameField.setStyle("-fx-background-color: #1e1e2e; -fx-text-fill: white; -fx-prompt-text-fill: #666;");
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Mot de passe");
        passwordField.setStyle("-fx-background-color: #1e1e2e; -fx-text-fill: white; -fx-prompt-text-fill: #666;");

        Button loginButton = new Button("Se connecter");
        loginButton.setStyle("-fx-background-color: #51cf66; -fx-text-fill: white; -fx-font-weight: bold;");
        loginButton.setPrefWidth(200);
        
        Button signupButton = new Button("S'inscrire");
        signupButton.setStyle("-fx-background-color: #339af0; -fx-text-fill: white; -fx-font-weight: bold;");
        signupButton.setPrefWidth(200);

        Label authMessage = new Label("");
        authMessage.setTextFill(Color.web("#ff6b6b"));
        authMessage.setWrapText(true);

        loginButton.setOnAction(e -> {
            JsonObject data = new JsonObject();
            data.addProperty("username", usernameField.getText());
            data.addProperty("password", passwordField.getText());
            sendRequest("login", data);
            
            currentUser = usernameField.getText();
            authMessage.setText("Requête de connexion envoyée...");
            authMessage.setTextFill(Color.web("#51cf66"));
            statusLabel.setText("● Système actif - Lamport en cours");
            statusLabel.setTextFill(Color.web("#51cf66"));
        });

        signupButton.setOnAction(e -> {
            JsonObject data = new JsonObject();
            data.addProperty("username", usernameField.getText());
            data.addProperty("password", passwordField.getText());
            sendRequest("signup", data);
            
            authMessage.setText("Requête d'inscription envoyée...");
            authMessage.setTextFill(Color.web("#51cf66"));
        });

        authPanel.getChildren().addAll(authTitle, connectBtn, new Separator(), usernameField, passwordField, loginButton, signupButton, authMessage);

        Label logTitle = new Label("📜 Logs Lamport en Temps Réel");
        logTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        logTitle.setTextFill(Color.WHITE);
        logTitle.setPadding(new Insets(10, 0, 5, 0));
        
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #00ff00; -fx-font-family: 'Courier New'; -fx-font-size: 10px;");
        logArea.setPrefHeight(600);
        logArea.setMinHeight(400);
        
        authPanel.getChildren().addAll(logTitle, logArea);

        return authPanel;
    }

    // ===================== Logs =====================
    private void addLog(String message) {
        Platform.runLater(() -> {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
            logArea.appendText("[" + timestamp + "] " + message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
            
            updateProcessStatus(message);
            updateSensorData(message);
            updateProcessDetails(message);
        });
    }

    private void updateSensorData(String message) {
        if (message.contains("écrit:") || message.contains("lit:")) {
            try {
                if (message.contains("écrit:")) {
                    String[] parts = message.split("écrit:");
                    if (parts.length > 1) {
                        String data = parts[1].trim();
                        
                        if (data.contains("temperature=")) {
                            String value = data.split("=")[1].trim();
                            tempValueLabel.setText(value);
                            tempTimestampLabel.setText("Mis à jour: " + 
                                new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()));
                        } else if (data.contains("humidite=")) {
                            String value = data.split("=")[1].trim();
                            humValueLabel.setText(value);
                            humTimestampLabel.setText("Mis à jour: " + 
                                new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()));
                        } else if (data.contains("pression=")) {
                            String value = data.split("=")[1].trim();
                            pressValueLabel.setText(value);
                            pressTimestampLabel.setText("Mis à jour: " + 
                                new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()));
                        }
                    }
                } else if (message.contains("lit:")) {
                    String data = message.split("lit:")[1].trim();
                    
                    if (data.contains("temp=")) {
                        String temp = data.split("temp=")[1].split("°C")[0].trim();
                        tempValueLabel.setText(temp);
                        tempTimestampLabel.setText("Lu: " + 
                            new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()));
                    }
                    if (data.contains("hum=")) {
                        String hum = data.split("hum=")[1].split("%")[0].trim();
                        humValueLabel.setText(hum);
                        humTimestampLabel.setText("Lu: " + 
                            new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()));
                    }
                    if (data.contains("press=")) {
                        String press = data.split("press=")[1].split("hPa")[0].trim();
                        pressValueLabel.setText(press);
                        pressTimestampLabel.setText("Lu: " + 
                            new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()));
                    }
                }
            } catch (Exception e) {
                // Ignore parse errors
            }
        }
    }

    private void updateProcessDetails(String message) {
        for (String processKey : processStatusLabels.keySet()) {
            if (message.contains(processKey) || message.contains(processKey.replace("Process ", ""))) {
                if (message.contains("Hi=")) {
                    try {
                        String[] hiParts = message.split("Hi=");
                        if (hiParts.length > 1) {
                            String hiValue = hiParts[1].split("[|\\s]")[0].trim();
                            Label hiLabel = processHiLabels.get(processKey);
                            if (hiLabel != null) {
                                hiLabel.setText("Hi: " + hiValue);
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                
                if (message.contains("FAi=") || message.contains("FAi après")) {
                    try {
                        String[] faiParts = message.split("FAi[=:]\\s*");
                        if (faiParts.length > 1) {
                            String faiValue = faiParts[1].split("\\|")[0].trim();
                            Label faiLabel = processFAiLabels.get(processKey);
                            if (faiLabel != null) {
                                faiLabel.setText("FAi: " + faiValue);
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                break;
            }
        }
    }

    private void updateProcessStatus(String message) {
        if (message.contains("Nouveau processus client ajouté: Process")) {
            String[] parts = message.split("Process ");
            if (parts.length > 1) {
                String[] idAndName = parts[1].split(" \\(");
                if (idAndName.length > 1) {
                    String processId = "Process " + idAndName[0].trim();
                    String clientName = idAndName[1].replace(")", "").trim();
                    
                    Platform.runLater(() -> {
                        addProcessToUI(processId, clientName, "#ffd43b");
                        updateTotalProcessCount();
                    });
                }
            }
        }

        for (String processKey : processStatusLabels.keySet()) {
            Label statusLabel = processStatusLabels.get(processKey);
            
            if (message.contains(processKey) && message.contains("ENTRE dans la section critique")) {
                statusLabel.setText("● Dans SC");
                statusLabel.setTextFill(Color.web("#ff6b6b"));
                csIndicator.setText("● Occupée");
                csIndicator.setTextFill(Color.web("#ff6b6b"));
                
                String processName = extractProcessName(message, processKey);
                csCurrentProcessLabel.setText("(" + processName + ")");
                currentProcessInCS = processKey;
                
            } else if (message.contains(processKey) && message.contains("QUITTE la section critique")) {
                statusLabel.setText("○ En attente");
                statusLabel.setTextFill(Color.web("#666"));
                
                if (processKey.equals(currentProcessInCS)) {
                    csIndicator.setText("○ Libre");
                    csIndicator.setTextFill(Color.web("#555"));
                    csCurrentProcessLabel.setText("");
                    currentProcessInCS = null;
                }
                
            } else if (message.contains(processKey) && (message.contains("FAi après REQUEST") || message.contains("demande"))) {
                statusLabel.setText("◐ Demande SC");
                statusLabel.setTextFill(Color.web("#ffd43b"));
                
            } else if (message.contains(processKey) && message.contains("terminé")) {
                statusLabel.setText("✓ Terminé");
                statusLabel.setTextFill(Color.web("#51cf66"));
            }
        }
    }

    private String extractProcessName(String message, String processKey) {
        try {
            String[] parts = message.split(processKey);
            if (parts.length > 1) {
                String remaining = parts[1].trim();
                if (remaining.startsWith("(")) {
                    return remaining.substring(1, remaining.indexOf(")"));
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return processKey;
    }

    private void disconnect() {
        try {
            if (listenerThread != null) listenerThread.interrupt();
            if (socket != null) socket.close();
            addLog("👋 Déconnecté du serveur");
        } catch (IOException e) {
            addLog("❌ Erreur lors de la déconnexion");
        }
    }
}