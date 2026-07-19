package bigbang.butilkka_be.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ReportGenerateResponse(
        String summary,
        String aiOutlook,
        String decisionRecommendation,
        String decisionTitle,
        String decisionDescription,
        List<Cause> causes,
        List<Signal> signals,
        DecisionReasons decisionReasons,
        List<AiSimilarCase> similarCases,
        List<AiAlternativeRegion> alternativeRegions,
        AiRecommendation aiRecommendation,
        String predictedTrend,
        String predictedNextGrade
) {
    public record Cause(
            String title,
            String level,
            String description
    ) {}

    public record Signal(
            String title,
            String description
    ) {}

    public record DecisionReasons(
            @JsonProperty("reason_1") String reason1,
            @JsonProperty("reason_2") String reason2,
            @JsonProperty("reason_3") String reason3
    ) {}

    public record AiSimilarCase(
            String regionCode,
            String regionName,
            String summary,
            String description,
            @JsonProperty("start_year") Integer startYear,
            @JsonProperty("end_year") Integer endYear,
            String tag1,
            String tag2,
            String tag3,
            String tag4
    ) {}

    public record AiAlternativeRegion(
            int rank,
            String regionCode,
            String dongName,
            String aiMessage
    ) {}

    public record AiRecommendation(
            String badgeType,
            String title,
            String reasonTitle,
            String reasonDetail
    ) {}
}
