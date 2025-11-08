// src/client/ChatGUI.java
package client;

import utils.CryptoUtils;
import com.formdev.flatlaf.FlatDarkLaf; // Thème sombre
import net.miginfocom.swing.MigLayout; // Layout flexible
import javax.crypto.SecretKey;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.security.PublicKey;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatGUI {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    private JFrame frame; // Fenêtre principale
    private JPanel chatPanel; // Contient les bulles de messages
    private JTextField messageField; // Champ pour taper
    private JButton sendButton, emojiButton; // Boutons Envoyer et Émoticône
    private JList<String> contactList; // Liste des contacts
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private SecretKey aesKey;
    private String username;

    public ChatGUI() {
        // Étape 3.1 : Appliquer le thème sombre (comme Messenger)
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Étape 3.2 : Créer la fenêtre principale
        frame = new JFrame("SecureChat - Mode Moderne");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 700);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH); // Responsive : plein écran
        frame.setLayout(new BorderLayout());

        // Étape 3.3 : Sidebar contacts (comme Facebook)
        JPanel sidebar = new JPanel(new MigLayout("fill"));
        sidebar.setPreferredSize(new Dimension(200, 0));
        sidebar.setBackground(new Color(30, 30, 30)); // Fond sombre
        JLabel sidebarTitle = new JLabel("Contacts");
        sidebarTitle.setForeground(Color.WHITE);
        sidebar.add(sidebarTitle, "wrap");
        String[] contacts = {"Alice", "Bob", "Charlie"}; // Simulé
        contactList = new JList<>(contacts);
        contactList.setBackground(new Color(50, 50, 50));
        contactList.setForeground(Color.WHITE);
        contactList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sidebar.add(new JScrollPane(contactList), "grow, wrap");
        frame.add(sidebar, BorderLayout.WEST);

        // Étape 3.4 : Zone des messages (bulles)
        chatPanel = new JPanel(new MigLayout("fill, wrap, insets 10"));
        chatPanel.setBackground(new Color(45, 45, 45)); // Fond sombre
        JScrollPane scrollPane = new JScrollPane(chatPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Étape 3.5 : Panneau inférieur (champ + boutons, comme WhatsApp)
        JPanel bottomPanel = new JPanel(new MigLayout("fill, insets 5"));
        bottomPanel.setBackground(new Color(60, 60, 60));
        emojiButton = new JButton("😊");
        emojiButton.addActionListener(e -> addEmoji());
        messageField = new JTextField();
        messageField.addActionListener(e -> sendMessage());
        sendButton = new JButton("Envoyer");
        sendButton.addActionListener(e -> sendMessage());
        bottomPanel.add(emojiButton, "gapleft 5");
        bottomPanel.add(messageField, "grow, gapright 5");
        bottomPanel.add(sendButton);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Étape 3.6 : Gestion fermeture fenêtre
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeConnection();
            }
        });

        // Étape 3.7 : Afficher la fenêtre
        frame.setVisible(true);

        // Étape 3.8 : Connexion au serveur
        connectToServer();
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            appendMessage("Connecté au serveur SecureChat !", "system", true);

            // Étape 3.9 : Échange de clés RSA/AES
            aesKey = CryptoUtils.generateAESKey();
            String line = in.readLine();
            if (line == null || !line.startsWith("RSA_PUBLIC_KEY:")) {
                appendMessage("Erreur : clé publique non reçue.", "system", true);
                return;
            }
            String publicKeyStr = line.substring(15);
            PublicKey serverPublicKey = CryptoUtils.stringToPublicKey(publicKeyStr);
            String encryptedAESKey = CryptoUtils.encryptAESKeyWithRSA(aesKey, serverPublicKey);
            out.println("AES_KEY:" + encryptedAESKey);
            out.flush();

            // Étape 3.10 : Demander le pseudo
            String serverPrompt = in.readLine();
            username = JOptionPane.showInputDialog(frame, serverPrompt != null ? serverPrompt : "Entrez votre pseudo :");
            if (username == null || username.trim().isEmpty()) username = "Anonyme";
            out.println(username);
            out.flush();

            appendMessage("Tapez vos messages ci-dessous (ou /quit pour quitter) :", "system", true);

            // Étape 3.11 : Thread pour recevoir les messages
            final SecretKey finalAesKey = aesKey;
            final BufferedReader finalIn = in;
            new Thread(() -> {
                String encryptedMessage;
                try {
                    while ((encryptedMessage = finalIn.readLine()) != null) {
                        if (encryptedMessage.trim().isEmpty()) continue;
                        if (encryptedMessage.equalsIgnoreCase("/quit")) {
                            appendMessage("Déconnecté du serveur", "system", true);
                            closeConnection();
                            return;
                        }
                        try {
                            String decrypted = CryptoUtils.decryptAES(encryptedMessage, finalAesKey);
                            boolean isSent = decrypted.startsWith(username + ":");
                            appendMessage(decrypted, isSent ? "sent" : "received", false);
                        } catch (Exception e) {
                            appendMessage("[Message ignoré]", "system", true);
                        }
                    }
                } catch (Exception e) {
                    appendMessage("\n[Déconnecté du serveur]", "system", true);
                }
            }).start();

        } catch (Exception e) {
            appendMessage("Erreur client : " + e.getMessage(), "system", true);
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String input = messageField.getText().trim();
        if (input.isEmpty()) return;
        messageField.setText("");

        if (input.equalsIgnoreCase("/quit")) {
            out.println("/quit");
            out.flush();
            closeConnection();
            return;
        }

        try {
            String encrypted = CryptoUtils.encryptAES(input, aesKey);
            out.println(encrypted);
            out.flush();
            appendMessage(username + ": " + input, "sent", false); // Afficher message envoyé
        } catch (Exception e) {
            appendMessage("Erreur chiffrement : " + e.getMessage(), "system", true);
        }
    }

    private void addEmoji() {
        messageField.setText(messageField.getText() + "😊"); // Ajoute un smiley
        messageField.requestFocus();
    }

    private void appendMessage(String message, String type, boolean isSystem) {
        JPanel messagePanel = new JPanel(new MigLayout("insets 5"));
        messagePanel.setOpaque(false);

        JLabel messageLabel = new JLabel("<html>" + message.replace("\n", "<br>") + "</html>");
        messageLabel.setOpaque(true);
        messageLabel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel timeLabel = new JLabel(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        timeLabel.setForeground(Color.LIGHT_GRAY);
        timeLabel.setFont(new Font("Arial", Font.PLAIN, 10));

        if (isSystem) {
            messageLabel.setBackground(new Color(100, 100, 100));
            messageLabel.setForeground(Color.WHITE);
            messagePanel.add(messageLabel, "align center");
        } else if (type.equals("sent")) {
            messageLabel.setBackground(new Color(0, 168, 107)); // Vert WhatsApp
            messageLabel.setForeground(Color.WHITE);
            messagePanel.add(messageLabel, "align right");
            messagePanel.add(timeLabel, "align right, wrap");
        } else {
            messageLabel.setBackground(new Color(200, 200, 200)); // Gris
            messageLabel.setForeground(Color.BLACK);
            messagePanel.add(messageLabel, "align left");
            messagePanel.add(timeLabel, "align left, wrap");
        }

        chatPanel.add(messagePanel, "growx");
        chatPanel.revalidate();
        chatPanel.repaint();
        JScrollBar vertical = ((JScrollPane) chatPanel.getParent().getParent()).getVerticalScrollBar();
        vertical.setValue(vertical.getMaximum());
    }

    private void closeConnection() {
        try {
            if (out != null) {
                out.println("/quit");
                out.flush();
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            appendMessage("Déconnexion.", "system", true);
            frame.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatGUI::new);
    }
}