package bigbang.butilkka_be.notification.dto;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationListResponse(
        int totalCount,
        boolean hasNext,
        List<NotificationItem> notifications
) {
    public record NotificationItem(
            Long notificationId,
            String category,
            String title,
            String content,
            boolean isRead,
            LocalDateTime sentAt
    ) {}
}
