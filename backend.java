import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class backend {

    private static final int SOCKET_PORT = 5000;
    private static final Map<String, String> users = new HashMap<>();
    private static final Map<String, Integer> userProcessIds = new HashMap<>();
    private static final Gson gson = new Gson();

    private static final List<JsonObject> capteursData = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> lamportLogs = Collections.synchronizedList(new ArrayList<>());

    private static ScheduledExecutorService dataGenerator;
    
    private static final List<LamportProcess> lamportProcesses = Collections.synchronizedList(new ArrayList<>());
    private static int nextProcessId = 0;
    private static boolean systemStarted = false;
    private static final Object lamportLock = new Object();
    
    // Liste des clients connectés
    private static final List<ClientHandler> connectedClients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Serveur Backend lancé sur le port " + SOCKET_PORT);
        System.out.println("En attente de connexion client...");

        startGeneratingData();
        initializeSensorProcesses();

        // Thread d'affichage des données
        new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(5000);
                    System.out.println("=== Données capteurs actuelles ===");
                    synchronized (capteursData) {
                        for (JsonObject d : capteursData) {
                            System.out.println(d.toString());
                        }
                    }
                    System.out.println("=================================");
                }
            } catch (InterruptedException e) {
                System.out.println("Thread d'affichage des données interrompu");
            }
        }).start();

        // Serveur Socket - accepte les connexions
        try (ServerSocket serverSocket = new ServerSocket(SOCKET_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("✅ Nouveau client connecté: " + clientSocket.getInetAddress());
                
                ClientHandler handler = new ClientHandler(clientSocket);
                connectedClients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("❌ Erreur serveur: " + e.getMessage());
        }
    }

    // ===================== Client Handler (Thread par client) =====================
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private boolean authenticated = false;
        private ScheduledExecutorService logSender;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                this.out = new PrintWriter(socket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                System.err.println("❌ Erreur création flux: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    handleMessage(message);
                }
            } catch (IOException e) {
                System.err.println("❌ Client déconnecté");
            } finally {
                disconnect();
            }
        }

        private void handleMessage(String message) {
            try {
                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                String action = json.get("action").getAsString();

                switch (action) {
                    case "signup":
                        handleSignup(json);
                        break;
                    case "login":
                        handleLogin(json);
                        break;
                    case "capteurs":
                        handleCapteurs(json);
                        break;
                    case "capteurs_ordered":
                        handleCapteursOrdered();
                        break;
                    default:
                        sendError("Action inconnue");
                }
            } catch (Exception e) {
                System.err.println("❌ Erreur parsing: " + e.getMessage());
                sendError("Message invalide");
            }
        }

        // ===================== Signup =====================
        private void handleSignup(JsonObject body) {
            String username = body.get("username").getAsString();
            String password = body.get("password").getAsString();

            JsonObject response = new JsonObject();
            if (users.containsKey(username)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Utilisateur déjà existant");
            } else {
                users.put(username, password);
                response.addProperty("status", "ok");
            }
            sendResponse(response);
        }

        // ===================== Login =====================
        private void handleLogin(JsonObject body) {
            String username = body.get("username").getAsString();
            String password = body.get("password").getAsString();

            JsonObject response = new JsonObject();
            if (users.containsKey(username) && users.get(username).equals(password)) {
                this.username = username;
                this.authenticated = true;
                response.addProperty("status", "ok");
                addClientProcess(username);
                startSendingLogs();
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Login failed");
            }
            sendResponse(response);
        }

        // ===================== Capteurs POST =====================
        private void handleCapteurs(JsonObject body) {
            capteursData.add(body);
            System.out.println("Nouvelle donnée capteur (POST) : " + gson.toJson(body));

            JsonObject response = new JsonObject();
            response.addProperty("status", "ok");
            sendResponse(response);
        }

        // ===================== Capteurs GET =====================
        private void handleCapteursOrdered() {
            JsonArray responseArray = new JsonArray();

            synchronized (capteursData) {
                for (String type : List.of("temperature", "humidite", "pression")) {
                    capteursData.stream()
                            .filter(d -> d.get("type").getAsString().equals(type))
                            .reduce((first, second) -> second)
                            .ifPresent(responseArray::add);
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("action", "capteurs_data");
            response.add("data", responseArray);
            sendResponse(response);
        }

        // ===================== Envoi automatique des logs =====================
        private void startSendingLogs() {
            logSender = Executors.newSingleThreadScheduledExecutor();
            logSender.scheduleAtFixedRate(() -> {
                JsonArray logsArray = new JsonArray();
                synchronized (lamportLogs) {
                    for (String log : lamportLogs) {
                        logsArray.add(log);
                    }
                    lamportLogs.clear();
                }

                if (logsArray.size() > 0) {
                    JsonObject response = new JsonObject();
                    response.addProperty("action", "lamport_logs");
                    response.add("logs", logsArray);
                    sendResponse(response);
                }
            }, 0, 400, TimeUnit.MILLISECONDS);
        }

        private void sendResponse(JsonObject response) {
            out.println(gson.toJson(response));
        }

        private void sendError(String message) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "error");
            response.addProperty("message", message);
            sendResponse(response);
        }

        private void disconnect() {
            try {
                authenticated = false;
                if (logSender != null) logSender.shutdownNow();
                connectedClients.remove(this);
                if (socket != null) socket.close();
                System.out.println("👋 Client déconnecté: " + username);
            } catch (IOException e) {
                System.err.println("Erreur déconnexion");
            }
        }
    }

    // ===================== Génération des données en temps réel =====================
    private static void startGeneratingData() {
        if (dataGenerator != null && !dataGenerator.isShutdown()) return;

        dataGenerator = Executors.newSingleThreadScheduledExecutor();
        Random rand = new Random();

        dataGenerator.scheduleAtFixedRate(() -> {
            JsonObject temp = createSensorData("temperature", 20 + rand.nextDouble() * 10);
            capteursData.add(temp);

            JsonObject hum = createSensorData("humidite", 40 + rand.nextDouble() * 20);
            capteursData.add(hum);

            JsonObject pres = createSensorData("pression", 990 + rand.nextDouble() * 20);
            capteursData.add(pres);

        }, 0, 1, TimeUnit.SECONDS);
    }

    private static JsonObject createSensorData(String type, double valeur) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.addProperty("valeur", Math.round(valeur * 100.0) / 100.0);
        obj.addProperty("timestamp", System.currentTimeMillis());
        return obj;
    }

    // ===================== Lamport System =====================
    private static void initializeSensorProcesses() {
        synchronized (lamportLock) {
            if (systemStarted) return;
            systemStarted = true;

            addLamportLog("🚀 Système Lamport initialisé avec 3 processus capteurs");
            
            LamportProcess p0 = new LamportProcess(nextProcessId++, "Capteur-Temperature", true);
            LamportProcess p1 = new LamportProcess(nextProcessId++, "Capteur-Humidite", true);
            LamportProcess p2 = new LamportProcess(nextProcessId++, "Capteur-Pression", true);

            lamportProcesses.add(p0);
            lamportProcesses.add(p1);
            lamportProcesses.add(p2);

            addLamportLog("   📡 Process 0: Capteur-Temperature");
            addLamportLog("   💧 Process 1: Capteur-Humidite");
            addLamportLog("   🌡️  Process 2: Capteur-Pression");

            updateAllProcessReferences();

            p0.start();
            p1.start();
            p2.start();
        }
    }

    public static double getLatestSensorValue(String sensorType) {
        synchronized (capteursData) {
            return capteursData.stream()
                    .filter(d -> d.get("type").getAsString().equals(sensorType))
                    .reduce((first, second) -> second)
                    .map(d -> d.get("valeur").getAsDouble())
                    .orElse(0.0);
        }
    }

    private static void addClientProcess(String username) {
        synchronized (lamportLock) {
            if (userProcessIds.containsKey(username)) {
                addLamportLog("👤 Client " + username + " reconnecté (Process " + userProcessIds.get(username) + ")");
                return;
            }

            int processId = nextProcessId++;
            LamportProcess clientProcess = new LamportProcess(processId, "Client-" + username, false);
            
            lamportProcesses.add(clientProcess);
            userProcessIds.put(username, processId);

            updateAllProcessReferences();

            clientProcess.start();

            addLamportLog("👤 Nouveau processus client ajouté: Process " + processId + " (Client-" + username + ")");
            addLamportLog("📊 Nombre total de processus: " + lamportProcesses.size());
        }
    }

    private static void updateAllProcessReferences() {
        List<LamportProcess> copy = new ArrayList<>(lamportProcesses);
        for (LamportProcess p : lamportProcesses) {
            p.setAllProcesses(copy);
        }
    }

    public static void addLamportLog(String log) {
        synchronized (lamportLogs) {
            lamportLogs.add(log);
        }
        System.out.println(log);
    }
}

// ===================== Original Lamport Implementation =====================
class LamportProcess extends Thread {
    int id;
    String role;
    boolean isSensor;
    int Hi = 0;
    List<int[]> FAi = new ArrayList<>();
    Set<Integer> Ati = new HashSet<>();
    List<LamportProcess> allProcesses;
    Semaphore sem = new Semaphore(0);

    LamportProcess(int id, String role, boolean isSensor) {
        this.id = id;
        this.role = role;
        this.isSensor = isSensor;
    }

    void setAllProcesses(List<LamportProcess> processes) {
        this.allProcesses = processes;
    }

    synchronized void receiveRequest(int senderId, int senderHi) {
        Hi = Math.max(Hi, senderHi) + 1;
        insertFAi(senderId, senderHi);
        backend.addLamportLog(role + " " + id + " reçoit REQUEST de " + senderId + " | Hi=" + Hi);
        saveFAiToFile();
        allProcesses.get(senderId).receiveReply(id);
    }

    synchronized void receiveReply(int senderId) {
        Hi++;
        Ati.remove(senderId);
        backend.addLamportLog(role + " " + id + " reçoit REPLY de " + senderId + " | Ati=" + Ati);
        saveAtiToFile();
        if (Ati.isEmpty() && isMyRequestFirst()) {
            sem.release();
        }
    }

    synchronized void receiveRelease(int senderId) {
        Hi++;
        removeFAi(senderId);
        backend.addLamportLog(role + " " + id + " reçoit RELEASE de " + senderId + " | FAi=" + formatFAi());
        saveFAiToFile();
        if (Ati.isEmpty() && isMyRequestFirst()) {
            sem.release();
        }
    }

    void insertFAi(int senderId, int senderHi) {
        FAi.add(new int[]{senderId, senderHi});
        FAi.sort(Comparator.comparingInt((int[] a) -> a[1]).thenComparingInt(a -> a[0]));
    }

    void removeFAi(int senderId) {
        FAi.removeIf(pair -> pair[0] == senderId);
    }

    boolean isMyRequestFirst() {
        return !FAi.isEmpty() && FAi.get(0)[0] == id;
    }

    String formatFAi() {
        StringBuilder sb = new StringBuilder("[");
        for (int[] pair : FAi) {
            sb.append("(").append(pair[0]).append(",").append(pair[1]).append(")");
        }
        sb.append("]");
        return sb.toString();
    }

    void requestCS() throws InterruptedException {
        Hi++;
        insertFAi(id, Hi);
        Ati.clear();
        for (LamportProcess p : allProcesses) {
            if (p.id != id) Ati.add(p.id);
        }
        backend.addLamportLog(role + " " + id + " FAi après REQUEST: " + formatFAi() + " | Ati=" + Ati);
        saveFAiToFile();
        saveAtiToFile();
        for (LamportProcess p : allProcesses) {
            if (p.id != id) p.receiveRequest(id, Hi);
        }
        if (!Ati.isEmpty() || !isMyRequestFirst()) {
            sem.acquire();
        }
    }

    void releaseCS() {
        Hi++;
        removeFAi(id);
        backend.addLamportLog(role + " " + id + " QUITTE la section critique | Hi=" + Hi + " | FAi=" + formatFAi());
        saveFAiToFile();
        for (LamportProcess p : allProcesses) {
            if (p.id != id) p.receiveRelease(id);
        }
    }

    void enterCS() throws InterruptedException {
        backend.addLamportLog(role + " " + id + " ENTRE dans la section critique | Hi=" + Hi + " | FAi=" + formatFAi() + " | Ati=" + Ati);
        
        if (isSensor) {
            String sensorType = role.contains("Temperature") ? "temperature" :
                              role.contains("Humidite") ? "humidite" : "pression";
            double value = backend.getLatestSensorValue(sensorType);
            backend.addLamportLog("   ✏️  " + role + " " + id + " écrit: " + sensorType + "=" + value);
            Thread.sleep(800);
        } else {
            double temp = backend.getLatestSensorValue("temperature");
            double hum = backend.getLatestSensorValue("humidite");
            double press = backend.getLatestSensorValue("pression");
            backend.addLamportLog("   📖 " + role + " " + id + " lit: temp=" + temp + "°C, hum=" + hum + "%, press=" + press + "hPa");
            Thread.sleep(500);
        }
    }

    void saveFAiToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("FAi_Process_" + id + ".txt"))) {
            writer.println("Process " + id + " (" + role + ") - FAi:");
            writer.println("Timestamp: " + System.currentTimeMillis());
            writer.println("Hi: " + Hi);
            writer.println("FAi: " + formatFAi());
            writer.println("---");
            for (int[] pair : FAi) {
                writer.println("  Process " + pair[0] + " with timestamp " + pair[1]);
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écriture de FAi pour Process " + id);
        }
    }

    void saveAtiToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("Ati_Process_" + id + ".txt"))) {
            writer.println("Process " + id + " (" + role + ") - Ati:");
            writer.println("Timestamp: " + System.currentTimeMillis());
            writer.println("Hi: " + Hi);
            writer.println("Ati: " + Ati);
            writer.println("---");
            writer.println("Waiting for replies from:");
            for (Integer processId : Ati) {
                writer.println("  Process " + processId);
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écriture de Ati pour Process " + id);
        }
    }

    @Override
    public void run() {
        try {
            int iterations = isSensor ? 3 : 2;
            
            for (int i = 0; i < iterations; i++) {
                Thread.sleep(new Random().nextInt(2000));
                requestCS();
                enterCS();
                releaseCS();
                Thread.sleep(500);
            }
            
            backend.addLamportLog("✓ " + role + " " + id + " terminé");
        } catch (InterruptedException e) {
            backend.addLamportLog("❌ " + role + " " + id + " interrompu");
            e.printStackTrace();
        }
    }
}