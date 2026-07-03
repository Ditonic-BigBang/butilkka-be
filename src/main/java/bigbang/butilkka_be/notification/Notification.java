package bigbang.butilkka_be.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('EMERGENCY','REPORT','SYSTEM')")
    private NotificationCategory category;

    @Column(length = 255)
    private String title;

    @Column(length = 255)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public static Notification create(Long userId, NotificationCategory category, String title, String content) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.category = category;
        notification.title = title;
        notification.content = content;
        notification.isRead = false;
        notification.sentAt = LocalDateTime.now();
        return notification;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
