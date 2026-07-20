package bigbang.butilkka_be.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ReportGenerateRequest(
        @JsonProperty("region_code") String regionCode,
        @JsonProperty("region_name") String regionName,
        @JsonProperty("district_name") String districtName,
        Integer year,
        Integer quarter,
        String grade,
        Integer score,
        @JsonProperty("decline_type") String declineType,
        ReportContext context,
        @JsonProperty("quarterly_history") QuarterlyHistory quarterlyHistory,
        @JsonProperty("outlook_instructions") String outlookInstructions,
        @JsonProperty("all_grades") List<GradeInfo> allGrades
) {
    public record GradeInfo(
            @JsonProperty("region_code") String regionCode,
            @JsonProperty("region_name") String regionName,
            String grade,
            Integer score,
            @JsonProperty("decline_type") String declineType
    ) {}
    public record ReportContext(
            @JsonProperty("sales_delta") Double salesDelta,
            @JsonProperty("foot_traffic_delta") Double footTrafficDelta,
            @JsonProperty("store_count_delta") Double storeCountDelta,
            @JsonProperty("closure_rate") Double closureRate,
            @JsonProperty("vacancy_rate") Double vacancyRate,
            @JsonProperty("top_age_group") String topAgeGroup,
            @JsonProperty("top_gender") String topGender
    ) {}

    public record QuarterlyHistory(
            @JsonProperty("sales_qoq") List<Double> salesQoq,
            @JsonProperty("foot_traffic") List<Double> footTraffic,
            @JsonProperty("store_count") List<Double> storeCount,
            @JsonProperty("closure_rate") List<Double> closureRate
    ) {}
}
