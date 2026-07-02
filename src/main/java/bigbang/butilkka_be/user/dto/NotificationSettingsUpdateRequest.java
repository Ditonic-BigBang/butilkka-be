package bigbang.butilkka_be.user.dto;

public record NotificationSettingsUpdateRequest(
        Boolean smsAlert,
        Boolean autoReport,
        Boolean urgentAlert
) {}
