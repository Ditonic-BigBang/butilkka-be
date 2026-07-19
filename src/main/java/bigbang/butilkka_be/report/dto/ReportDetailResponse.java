package bigbang.butilkka_be.report.dto;

import java.util.List;

public record ReportDetailResponse(
        Long reportId,
        String regionCode,
        String districtName,
        String regionName,
        String categoryName,
        String quarter,
        String grade,
        String declineType,
        Integer score,
        String briefing,
        String aiOutlook,
        String predictedTrend,
        String predictedNextGrade,
        List<Cause> causes,
        List<Signal> leadingSignals,
        List<SimilarCasePreview> similarCases,
        Decision decision,
        List<AlternativeRegion> alternativeRegions,
        AiRecommendation aiRecommendation
) {
    public record Cause(String title, String level, String description) {}

    public record Signal(String title, String description) {}

    public record Period(Integer startYear, Integer endYear) {}

    public record SimilarCasePreview(String caseId, String regionCode, String regionName, String summary, Period period) {}

    public record Decision(String recommendation, String title, String description) {}

    public record AlternativeRegion(
            int rank,
            String regionCode,
            String regionName,
            String aiMessage,
            Integer storeCount,
            Long floatingPopulation,
            Double vacancy,
            String baseDate
    ) {}

    public record AiRecommendation(
            String badgeType,
            String title,
            String reasonTitle,
            String reasonDetail
    ) {}
}
