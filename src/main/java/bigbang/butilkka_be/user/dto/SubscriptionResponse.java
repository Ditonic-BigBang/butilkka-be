package bigbang.butilkka_be.user.dto;

public record SubscriptionResponse(
        boolean isReportPro,
        String plan
) {}
