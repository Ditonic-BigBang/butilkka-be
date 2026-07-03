package bigbang.butilkka_be.report;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportDetailService {

    private final ReportRepository reportRepository;
    private final ReportCauseRepository reportCauseRepository;
    private final ReportSignalRepository reportSignalRepository;
    private final ReportSimilarCaseRepository reportSimilarCaseRepository;
    private final ReportAlternativeRegionRepository reportAlternativeRegionRepository;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final CategoryRepository categoryRepository;

    public ReportDetailResponse getLatest(Long userId) {
        Report latest = reportRepository.findByUserId(userId).stream()
                .max(Comparator.comparingInt(Report::getYear).thenComparingInt(Report::getQuarter))
                .orElseThrow(() -> AppException.notFound("생성된 리포트가 없습니다."));
        return buildDetail(latest);
    }

    public ReportDetailResponse getDetail(Long userId, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("존재하지 않는 리포트입니다."));
        return buildDetail(report);
    }

    private ReportDetailResponse buildDetail(Report report) {
        Region region = regionRepository.findById(report.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));
        String categoryName = report.getCategoryCode() == null ? null
                : categoryRepository.findById(report.getCategoryCode())
                        .map(Category::getCategoryName)
                        .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다."));

        List<ReportDetailResponse.Cause> causes = reportCauseRepository.findByReportId(report.getReportId()).stream()
                .map(c -> new ReportDetailResponse.Cause(c.getTitle(), c.getLevel(), c.getDescription()))
                .toList();

        List<ReportDetailResponse.Signal> signals = reportSignalRepository.findByReportId(report.getReportId()).stream()
                .map(s -> new ReportDetailResponse.Signal(s.getTitle(), s.getDescription()))
                .toList();

        List<ReportDetailResponse.SimilarCasePreview> similarCases = reportSimilarCaseRepository.findByReportId(report.getReportId()).stream()
                .limit(3)
                .map(this::toSimilarCasePreview)
                .toList();

        List<ReportAlternativeRegion> alternativeRegionEntities = reportAlternativeRegionRepository.findByReportId(report.getReportId());
        List<ReportDetailResponse.AlternativeRegion> alternativeRegions = new ArrayList<>();
        for (int i = 0; i < alternativeRegionEntities.size(); i++) {
            alternativeRegions.add(toAlternativeRegion(alternativeRegionEntities.get(i), i + 1));
        }

        ReportDetailResponse.Decision decision = new ReportDetailResponse.Decision(
                report.getDecisionRecommendation(), report.getDecisionTitle(), report.getDecisionDescription());

        return new ReportDetailResponse(
                report.getReportId(),
                report.getRegionCode(),
                district.getDistrictName(),
                region.getRegionName(),
                categoryName,
                report.getYear() + "Q" + report.getQuarter(),
                report.getGrade(),
                report.getDeclineType(),
                report.getScore(),
                report.getSummary(),
                report.getAiOutlook(),
                causes,
                signals,
                similarCases,
                decision,
                alternativeRegions
        );
    }

    private ReportDetailResponse.SimilarCasePreview toSimilarCasePreview(ReportSimilarCase c) {
        Region region = regionRepository.findById(c.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        return new ReportDetailResponse.SimilarCasePreview(
                c.getId(), c.getRegionCode(), region.getRegionName(), c.getSummary(),
                new ReportDetailResponse.Period(c.getStartYear(), c.getEndYear()));
    }

    private ReportDetailResponse.AlternativeRegion toAlternativeRegion(ReportAlternativeRegion a, int rank) {
        Region region = regionRepository.findById(a.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        return new ReportDetailResponse.AlternativeRegion(rank, a.getRegionCode(), region.getRegionName(), a.getReason(), a.getStat());
    }
}
