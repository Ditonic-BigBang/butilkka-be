package bigbang.butilkka_be.notification;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.notification.dto.NotificationListResponse;
import bigbang.butilkka_be.notification.dto.NotificationReadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationListResponse getNotifications(Long userId, int offset, int limit) {
        if (offset < 0 || limit < 0) {
            throw AppException.badRequest("offset과 limit은 0 이상이어야 합니다.");
        }

        List<Notification> all = notificationRepository.findByUserIdOrderBySentAtDesc(userId);

        List<NotificationListResponse.NotificationItem> page = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toItem)
                .toList();

        boolean hasNext = offset + page.size() < all.size();

        return new NotificationListResponse(all.size(), hasNext, page);
    }

    @Transactional
    public NotificationReadResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("존재하지 않는 알림입니다."));

        notification.markAsRead();

        return new NotificationReadResponse(notification.getNotificationId(), true);
    }

    private NotificationListResponse.NotificationItem toItem(Notification notification) {
        return new NotificationListResponse.NotificationItem(
                notification.getNotificationId(),
                notification.getCategory().name(),
                notification.getTitle(),
                notification.getContent(),
                notification.isRead(),
                notification.getSentAt());
    }
}
