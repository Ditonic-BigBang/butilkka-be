package bigbang.butilkka_be.report.dto;

import java.util.List;

public record ReportHistoryResponse(
        int totalCount,
        boolean hasNext,
        List<ReportHistoryItem> reports
) {
    public record ReportHistoryItem(Long reportId, String quarter, String grade, String briefing) {}
}
