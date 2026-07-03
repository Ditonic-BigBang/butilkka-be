package bigbang.butilkka_be.report.dto;

import java.util.List;

public record ReportCaseListResponse(
        int totalCount,
        List<ReportCaseItem> cases
) {
    public record ReportCaseItem(
            String caseId,
            String regionCode,
            String regionName,
            String summary,
            String description,
            String tag1,
            String tag2,
            String tag3,
            String tag4,
            Period period
    ) {}

    public record Period(int startYear, int endYear) {}
}
