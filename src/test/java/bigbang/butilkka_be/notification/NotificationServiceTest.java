package bigbang.butilkka_be.notification;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.notification.dto.NotificationListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository);
    }

    private static Notification notificationOf(
            Long id, NotificationCategory category, String title, String content, boolean isRead, LocalDateTime sentAt) {
        Notification notification = mock(Notification.class);
        lenient().when(notification.getNotificationId()).thenReturn(id);
        lenient().when(notification.getCategory()).thenReturn(category);
        lenient().when(notification.getTitle()).thenReturn(title);
        lenient().when(notification.getContent()).thenReturn(content);
        lenient().when(notification.isRead()).thenReturn(isRead);
        lenient().when(notification.getSentAt()).thenReturn(sentAt);
        return notification;
    }

    @Test
    void getNotifications_returnsItemsInRepositoryOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 20, 9, 0);
        Notification n1 = notificationOf(301L, NotificationCategory.EMERGENCY, "제목1", "내용1", false, now);
        Notification n2 = notificationOf(300L, NotificationCategory.SYSTEM, "제목2", "내용2", true, now.minusDays(1));
        when(notificationRepository.findByUserIdOrderBySentAtDesc(1L)).thenReturn(List.of(n1, n2));

        NotificationListResponse response = service.getNotifications(1L, 0, 20);

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.notifications()).hasSize(2);
        assertThat(response.notifications().get(0).notificationId()).isEqualTo(301L);
        assertThat(response.notifications().get(0).category()).isEqualTo("EMERGENCY");
        assertThat(response.notifications().get(0).isRead()).isFalse();
        assertThat(response.notifications().get(1).notificationId()).isEqualTo(300L);
        assertThat(response.notifications().get(1).isRead()).isTrue();
    }

    @Test
    void getNotifications_appliesOffsetAndLimit() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 20, 9, 0);
        Notification n1 = notificationOf(4L, NotificationCategory.SYSTEM, "t4", "c4", false, now);
        Notification n2 = notificationOf(3L, NotificationCategory.SYSTEM, "t3", "c3", false, now);
        Notification n3 = notificationOf(2L, NotificationCategory.SYSTEM, "t2", "c2", false, now);
        Notification n4 = notificationOf(1L, NotificationCategory.SYSTEM, "t1", "c1", false, now);
        when(notificationRepository.findByUserIdOrderBySentAtDesc(1L)).thenReturn(List.of(n1, n2, n3, n4));

        NotificationListResponse response = service.getNotifications(1L, 1, 2);

        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.notifications()).hasSize(2);
        assertThat(response.notifications().get(0).notificationId()).isEqualTo(3L);
        assertThat(response.notifications().get(1).notificationId()).isEqualTo(2L);
    }

    @Test
    void getNotifications_withNoNotifications_returnsEmptyList() {
        when(notificationRepository.findByUserIdOrderBySentAtDesc(1L)).thenReturn(List.of());

        NotificationListResponse response = service.getNotifications(1L, 0, 20);

        assertThat(response.totalCount()).isEqualTo(0);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.notifications()).isEmpty();
    }

    @Test
    void getNotifications_withNegativeOffset_throwsBadRequest() {
        assertThatThrownBy(() -> service.getNotifications(1L, -1, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void getNotifications_withNegativeLimit_throwsBadRequest() {
        assertThatThrownBy(() -> service.getNotifications(1L, 0, -1))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
