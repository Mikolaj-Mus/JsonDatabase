package client;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {

    @Parameter(names = {"--type", "-t"})
    String type;
    @Parameter(names = {"--key", "-k"})
    String key;
    @Parameter(names = {"--value", "-v"})
    String value;
    @Parameter(names = {"--input", "-in"})
    String fileName;

    private Thread communicationThread;

    public void connectToServer() {
        String address = "127.0.0.1";
        int port = 12345;
        Gson gson = new Gson();

        communicationThread = new Thread(() -> {
            try (
                    Socket socket = new Socket(InetAddress.getByName(address), port);
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream())
            ) {
                System.out.println("Client started!");

                JsonObject requestJson;

                if (fileName != null) {
                    // Read the entire contents of the file
                    String contents = new String(Files.readAllBytes(Paths.get("C:\\Users\\mikis\\eclipse-workspace\\JSON Database (Java)1\\JSON Database (Java)\\task\\src\\client\\data\\" + fileName)));
                    requestJson = gson.fromJson(contents, JsonObject.class);
                } else {
                    requestJson = new JsonObject();
                    requestJson.addProperty("type", type);
                    requestJson.addProperty("key", key);

                    if (type.equals("set")) {
                        requestJson.addProperty("value", value);
                    }
                }

                String msg = gson.toJson(requestJson);
                output.writeUTF(msg); // send a message to the server
                String receivedMsg = input.readUTF(); // read the reply from the server

                System.out.println("Sent: " + msg);
                System.out.println("Received: " + receivedMsg);

            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        communicationThread.start();
    }

    public static void main(String... args) {

        Main main = new Main();
        JCommander.newBuilder()
                .addObject(main)
                .build()
                .parse(args);

        main.connectToServer();

        try {
            main.communicationThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}