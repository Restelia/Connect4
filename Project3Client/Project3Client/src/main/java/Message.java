import java.io.Serializable;

public class Message implements Serializable {
    static final long serialVersionUID = 42L;

    MessageType type;
    String message;
    String recipient;

    Message(MessageType type, String message, String recipient) {
        this.type = type;
        this.message = message;
        this.recipient = recipient;
    }

    public MessageType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getRecipient() {
        return recipient;
    }

    public String toString() {
        return message;
    }
}