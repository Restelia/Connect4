import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class NotificationManager {
    private final VBox notificationArea;
    private static final int MAX_NOTIFICATIONS = 3;
    private static final int NOTIFICATION_DURATION = 5000; // 5 seconds

    public NotificationManager(VBox notificationArea) {
        this.notificationArea = notificationArea;
    }

    public void showNotification(String message) {
        Platform.runLater(() -> {
            // Create notification card
            HBox notificationCard = new HBox(10);
            notificationCard.setAlignment(Pos.CENTER_LEFT);
            notificationCard.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-background-radius: 5; -fx-padding: 10;");
            notificationCard.setEffect(new DropShadow(10, Color.BLACK));

            Label messageLabel = new Label(message);
            messageLabel.setTextFill(Color.WHITE);
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(200);

            Button closeBtn = new Button("✕");
            closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
            closeBtn.setOnAction(e -> removeNotification(notificationCard));

            notificationCard.getChildren().addAll(messageLabel, closeBtn);
            notificationCard.setMouseTransparent(false);  // Only the card itself is clickable
            notificationCard.setOnMouseClicked(e -> {
                // Handle clicks on the notification if needed
                e.consume();  // Prevent event propagation
            });

            // Add to notification area
            if (notificationArea.getChildren().size() >= MAX_NOTIFICATIONS) {
                notificationArea.getChildren().remove(0);
            }
            notificationArea.getChildren().add(notificationCard);

            // Show notification area if hidden
            if (!notificationArea.isVisible()) {
                notificationArea.setVisible(true);
                TranslateTransition showTransition = new TranslateTransition(Duration.millis(300), notificationArea);
                showTransition.setToY(0);
                showTransition.play();
            }

            // Auto-hide after duration
            PauseTransition delay = new PauseTransition(Duration.millis(NOTIFICATION_DURATION));
            delay.setOnFinished(e -> removeNotification(notificationCard));
            delay.play();
        });
    }

    public void removeNotification(HBox notificationCard) {
        TranslateTransition hideTransition = new TranslateTransition(Duration.millis(300), notificationCard);
        hideTransition.setToY(notificationCard.getHeight());
        hideTransition.setOnFinished(e -> {
            notificationArea.getChildren().remove(notificationCard);
            if (notificationArea.getChildren().isEmpty()) {
                notificationArea.setVisible(false);
            }
        });
        hideTransition.play();
    }
}