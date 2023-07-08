package server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Main {

    private static Map<String, String> database;
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final String dbPath = "C:\\Users\\mikis\\eclipse-workspace\\JSON Database (Java)1\\JSON Database (Java)\\task\\src\\server\\data\\db.json";
    private static ExecutorService executorService;
    private static volatile boolean isExitCommandIssued = false;

    public static void initializeDatabase() throws IOException {
        try {
            // Read from the file
            lock.readLock().lock();
            String dbContent = new String(Files.readAllBytes(Paths.get(dbPath)));
            database = new Gson().fromJson(dbContent, HashMap.class);
        } finally {
            lock.readLock().unlock();
        }
    }

    public static JsonObject setValue(String key, String value) throws IOException {
        try {
            lock.writeLock().lock();
            database.put(key, value);
            Files.write(Paths.get(dbPath), new Gson().toJson(database).getBytes());
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("response", "OK");
            return responseJson;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static JsonObject getValue(String key) {
        try {
            lock.readLock().lock();
            JsonObject responseJson = new JsonObject();
            if (database.containsKey(key)) {
                responseJson.addProperty("response", "OK");
                responseJson.addProperty("value", database.get(key));
            } else {
                responseJson.addProperty("response", "ERROR");
                responseJson.addProperty("reason", "No such key");
            }
            return responseJson;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static JsonObject deleteValue(String key) throws IOException {
        try {
            lock.writeLock().lock();
            JsonObject responseJson = new JsonObject();
            if (database.containsKey(key)) {
                database.remove(key);
                Files.write(Paths.get(dbPath), new Gson().toJson(database).getBytes());
                responseJson.addProperty("response", "OK");
            } else {
                responseJson.addProperty("response", "ERROR");
                responseJson.addProperty("reason", "No such key");
            }
            return responseJson;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static JsonObject exitServer() {
        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("response", "OK");
        isExitCommandIssued = true; // Ustaw flagę, aby serwer zatrzymał oczekiwanie na nowe połączenia
        return responseJson;
    }

    private static ServerSocket server;

    public static void createServerSocket() throws IOException {

        initializeDatabase();
        String address = "127.0.0.1";
        int port = 12345;
        Gson gson = new Gson();

        // Initialize the ExecutorService
        executorService = Executors.newFixedThreadPool(10);

        server = new ServerSocket(port, 50, InetAddress.getByName(address));
        System.out.println("Server started!");

        // Use a new Thread for accepting new connections
        Thread acceptThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !server.isClosed()) {
                try {
                    Socket socket = server.accept();
                    // Handle each client in a new thread
                    executorService.submit(() -> {
                        handleClient(socket, gson);
                    });
                } catch (IOException e) {
                    if (!server.isClosed()) {
                        e.printStackTrace();
                    }
                    Thread.currentThread().interrupt();
                }
            }
        });

        acceptThread.start();

        // Wait until the exit command is issued
        while (!isExitCommandIssued) {
            try {
                Thread.sleep(1000);  // wait for a second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Interrupt the accept thread and close the server
        acceptThread.interrupt();
        server.close();
        executorService.shutdownNow();

    }

    public static void handleClient(Socket socket, Gson gson) {
        try (
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())
        ) {

            String receivedMsg = input.readUTF();
            JsonObject receivedJson = gson.fromJson(receivedMsg, JsonObject.class);
            String command = receivedJson.get("type").getAsString();
            String key = receivedJson.get("key").getAsString();
            JsonObject reply;

            switch (command) {
                case "set" -> {
                    String value = receivedJson.get("value").getAsString();
                    reply = setValue(key, value);
                }
                case "get" -> reply = getValue(key);
                case "delete" -> reply = deleteValue(key);
                case "exit" -> {
                    reply = exitServer();
                    output.writeUTF(gson.toJson(reply));
                    socket.close();

                    if(isExitCommandIssued) {
                        server.close();
                        executorService.shutdownNow();
                        System.exit(0); // exit program
                    }

                    return;
                }
                default -> {
                    reply = new JsonObject();
                    reply.addProperty("response", "ERROR");
                    reply.addProperty("reason", "Invalid Command");
                    output.writeUTF(gson.toJson(reply));
                }
            }
            output.writeUTF(gson.toJson(reply));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        if (server == null || server.isClosed()) {
            createServerSocket();
        }
    }
}