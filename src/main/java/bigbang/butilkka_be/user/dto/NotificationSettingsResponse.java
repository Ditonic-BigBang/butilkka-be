package bigbang.butilkka_be.user.dto;

import bigbang.butilkka_be.user.User;

public record NotificationSettingsResponse(
        boolean smsAlert,
        boolean autoReport,
        boolean urgentAlert
) {
    public static NotificationSettingsResponse from(User user) {
        return new NotificationSettingsResponse(user.isSmsAlert(), user.isAutoReport(), user.isUrgentAlert());
    }
}
