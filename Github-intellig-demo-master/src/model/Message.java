// src/model/Message.java
package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {
    private String sender;
    private String content;
    private LocalDateTime timestamp;

    public Message(String sender, String content) {
        this.sender = sender;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    @Override
    public String toString() {
        String time = timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return "[" + time + "] " + sender + ": " + content;
    }

    public String getSender() { return sender; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }
}