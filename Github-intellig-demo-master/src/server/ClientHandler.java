// src/server/ClientHandler.java
package server;

import utils.CryptoUtils;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.security.KeyPair;
import java.util.ArrayList;

public class ClientHandler extends Thread {
    private final Socket socket;
    private final ArrayList<ClientHandler> clients;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private SecretKey aesKey;
    private final KeyPair serverKeyPair;

    public ClientHandler(Socket socket, ArrayList<ClientHandler> clients, KeyPair serverKeyPair) {
        this.socket = socket;
        this.clients = clients;
        this.serverKeyPair = serverKeyPair;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // Étape 1 : Envoyer la clé publique RSA
            String publicKeyStr = CryptoUtils.publicKeyToString(serverKeyPair.getPublic());
            out.println("RSA_PUBLIC_KEY:" + publicKeyStr);
            out.flush();

            // Étape 2 : Recevoir la clé AES chiffrée
            String encryptedAESKeyLine = in.readLine();
            if (encryptedAESKeyLine == null || !encryptedAESKeyLine.startsWith("AES_KEY:")) {
                closeConnection();
                return;
            }
            String encryptedAESKey = encryptedAESKeyLine.substring(8);
            this.aesKey = CryptoUtils.decryptAESKeyWithRSA(encryptedAESKey, serverKeyPair.getPrivate());

            // Étape 3 : Demander le pseudo
            out.println("Entrez votre pseudo :");
            out.flush();
            username = in.readLine();
            if (username == null || username.trim().isEmpty()) {
                username = "Anonyme";
            }
            username = username.trim();

            // Notifier les autres
            String joinMsg = username + " a rejoint le chat !";
            broadcastToAll(joinMsg);

            // Boucle de réception
            String encryptedMessage;
            while ((encryptedMessage = in.readLine()) != null) {
                if (encryptedMessage.trim().isEmpty()) {
                    continue;
                }
                if (encryptedMessage.equalsIgnoreCase("/quit")) {
                    break; // Déclenche closeConnection()
                }
                try {
                    String decrypted = CryptoUtils.decryptAES(encryptedMessage, aesKey);
                    String fullMessage = username + ": " + decrypted;
                    broadcastToAll(fullMessage);
                } catch (Exception e) {
                    System.out.println("Message corrompu ignoré de " + username + " : " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.out.println(username + " a rencontré une erreur : " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    public void sendEncryptedMessage(String encryptedMessage) {
        if (out != null) {
            out.println(encryptedMessage);
            out.flush();
        }
    }

    private void broadcastToAll(String plainMessage) {
        for (ClientHandler client : clients) {
            if (client != this && client.aesKey != null) {
                try {
                    String encryptedForClient = CryptoUtils.encryptAES(plainMessage, client.aesKey);
                    client.sendEncryptedMessage(encryptedForClient);
                } catch (Exception e) {
                    System.out.println("Erreur envoi à " + client.username);
                }
            }
        }
    }

    private void closeConnection() {
        try {
            SecureChatServer.removeClient(this);
            String leaveMsg = username + " a quitté le chat.";
            broadcastToAll(leaveMsg);
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println(username + " déconnecté.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}