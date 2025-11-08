// src/client/SecureChatClient.java
package client;

import utils.CryptoUtils;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.security.PublicKey;
import java.util.Scanner;

public class SecureChatClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        // Déclarer les variables en dehors du try pour les rendre effectivement finales
        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;
        Scanner scanner = null;
        SecretKey aesKey = null;

        try {
            // Initialisation
            socket = new Socket(SERVER_IP, SERVER_PORT);
            System.out.println("Connecté au serveur SecureChat !\n");

            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            scanner = new Scanner(System.in);

            // Générer clé AES locale
            aesKey = CryptoUtils.generateAESKey();
            final SecretKey finalAesKey = aesKey; // Copie finale pour la lambda

            // Recevoir la clé publique du serveur
            String line = in.readLine();
            if (line == null || !line.startsWith("RSA_PUBLIC_KEY:")) {
                System.out.println("Erreur : clé publique non reçue.");
                return;
            }
            String publicKeyStr = line.substring(15);
            PublicKey serverPublicKey = CryptoUtils.stringToPublicKey(publicKeyStr);

            // Envoyer la clé AES chiffrée
            String encryptedAESKey = CryptoUtils.encryptAESKeyWithRSA(aesKey, serverPublicKey);
            out.println("AES_KEY:" + encryptedAESKey);
            out.flush();

            // Thread pour recevoir les messages
            final BufferedReader finalIn = in; // Copie finale pour la lambda
            new Thread(() -> {
                String encryptedMessage;
                try {
                    while ((encryptedMessage = finalIn.readLine()) != null) {
                        if (encryptedMessage.trim().isEmpty()) continue;
                        try {
                            String decrypted = CryptoUtils.decryptAES(encryptedMessage, finalAesKey);
                            System.out.println(decrypted);
                        } catch (Exception e) {
                            System.out.println("[Message ignoré]");
                        }
                    }
                } catch (Exception e) {
                    System.out.println("\n[Déconnecté du serveur]");
                }
            }).start();

            // Demande du pseudo
            System.out.print(in.readLine());
            String username = scanner.nextLine();
            out.println(username);
            out.flush();

            System.out.println("Tapez vos messages (ou /quit pour quitter) :\n");

            // Boucle d’envoi
            while (true) {
                String input = scanner.nextLine();
                if (input.trim().isEmpty()) continue;
                if (input.equalsIgnoreCase("/quit")) {
                    out.println("/quit");
                    out.flush();
                    break;
                }
                try {
                    String encrypted = CryptoUtils.encryptAES(input, aesKey);
                    out.println(encrypted);
                    out.flush();
                } catch (Exception e) {
                    System.out.println("Erreur chiffrement : " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.out.println("Erreur client : " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (scanner != null) scanner.close();
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
                System.out.println("Déconnexion.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}