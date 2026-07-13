package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.report.dto.ReportCaseListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportCaseService {

    private final ReportRepository reportRepository;
    private final ReportSimilarCaseRepository reportSimilarCaseRepository;
    private final RegionRepository regionRepository;

    public ReportCaseListResponse getCases(Long userId, Long reportId, int offset, int limit) {
        if (offset < 0 || limit < 0) {
            throw AppException.badRequest("offset과 limit은 0 이상이어야 합니다.");
        }

        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("존재하지 않는 리포트입니다."));

        List<ReportSimilarCase> all = reportSimilarCaseRepository.findByReportId(report.getReportId());

        List<ReportCaseListResponse.ReportCaseItem> page = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toCaseItem)
                .toList();

        return new ReportCaseListResponse(all.size(), page);
    }

    private ReportCaseListResponse.ReportCaseItem toCaseItem(ReportSimilarCase c) {
        // AI가 보내준 regionName 우선 사용, 없으면 DB 조회 fallback
        String regionName = c.getRegionName();
        if (regionName == null || regionName.isBlank()) {
            regionName = regionRepository.findById(c.getRegionCode())
                    .map(Region::getRegionName)
                    .orElse("알 수 없음");
        }
        return new ReportCaseListResponse.ReportCaseItem(
                c.getId(), c.getRegionCode(), regionName, c.getSummary(), c.getDescription(),
                c.getTag1(), c.getTag2(), c.getTag3(), c.getTag4(),
                new ReportCaseListResponse.Period(toNullableInt(c.getStartYear()), toNullableInt(c.getEndYear())));
    }

    private static Integer toNullableInt(Short value) {
        return value == null ? null : value.intValue();
    }
}
