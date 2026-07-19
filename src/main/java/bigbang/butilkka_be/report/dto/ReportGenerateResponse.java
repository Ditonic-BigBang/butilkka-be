package bigbang.butilkka_be.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ReportGenerateResponse(
        String summary,
        @JsonProperty("ai_outlook") String aiOutlook,
        @JsonProperty("decision_recommendation") String decisionRecommendation,
        @JsonProperty("decision_title") String decisionTitle,
        @JsonProperty("decision_description") String decisionDescription,
        List<Cause> causes,
        List<Signal> signals,
        @JsonProperty("decision_reasons") DecisionReasons decisionReasons,
        @JsonProperty("similar_cases") List<AiSimilarCase> similarCases,
        @JsonProperty("alternative_regions") List<AiAlternativeRegion> alternativeRegions,
        @JsonProperty("ai_recommendation") AiRecommendation aiRecommendation,
        @JsonProperty("predicted_trend") String predictedTrend,
        @JsonProperty("predicted_next_grade") String predictedNextGrade
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
            @JsonProperty("region_code") String regionCode,
            @JsonProperty("region_name") String regionName,
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
            @JsonProperty("region_code") String regionCode,
            @JsonProperty("dong_name") String dongName,
            @JsonProperty("ai_message") String aiMessage
    ) {}

    public record AiRecommendation(
            @JsonProperty("badge_type") String badgeType,
            String title,
            @JsonProperty("reason_title") String reasonTitle,
            @JsonProperty("reason_detail") String reasonDetail
    ) {}
}
