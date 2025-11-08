// src/server/SecureChatServer.java
package server;

import utils.CryptoUtils;

import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.ArrayList;

public class SecureChatServer {
    private static final int PORT = 12345;
    private static final ArrayList<ClientHandler> clients = new ArrayList<>();
    private static KeyPair serverKeyPair;

    public static void main(String[] args) {
        try {
            serverKeyPair = CryptoUtils.generateRSAKeyPair();
            System.out.println("Serveur démarré sur le port " + PORT);
            System.out.println("Clé publique du serveur prête pour échange sécurisé.\n");

            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nouveau client connecté depuis : " + clientSocket.getInetAddress());

                    ClientHandler clientHandler = new ClientHandler(clientSocket, clients, serverKeyPair);
                    clients.add(clientHandler);
                    clientHandler.start();
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur serveur : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void broadcast(String encryptedMessage, ClientHandler excludeClient) {
        for (ClientHandler client : clients) {
            if (client != excludeClient && client.isAlive()) {
                client.sendEncryptedMessage(encryptedMessage);
            }
        }
    }

    public static void removeClient(ClientHandler client) {
        clients.remove(client);
    }
}