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
        @JsonProperty("similar_cases") List<SimilarCase> similarCases,
        @JsonProperty("alternative_regions") List<AlternativeRegion> alternativeRegions
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

    public record SimilarCase(
            @JsonProperty("region_code") String regionCode,
            String summary,
            String description,
            @JsonProperty("start_year") Integer startYear,
            @JsonProperty("end_year") Integer endYear,
            String tag1,
            String tag2,
            String tag3,
            String tag4
    ) {}

    public record AlternativeRegion(
            @JsonProperty("region_code") String regionCode,
            String reason,
            String stat
    ) {}
}
