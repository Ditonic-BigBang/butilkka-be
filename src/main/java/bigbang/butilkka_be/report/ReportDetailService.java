package bigbang.butilkka_be.report;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
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
    private final DistrictRepository districtRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ReportGenerateService reportGenerateService;

    public ReportDetailResponse getLatest(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다."));
        String currentRegion = user.getStoreRegion();

        if (currentRegion == null) {
            throw AppException.badRequest("등록된 가게 정보가 없습니다.");
        }

        // 10자리 행정동 코드에서 앞 5자리 구코드 추출
        String districtCode = currentRegion.substring(0, 5);

        // 현재 대표 위치(구코드)에 해당하는 리포트만 조회
        Report latest = reportRepository.findByUserId(userId).stream()
                .filter(r -> districtCode.equals(r.getRegionCode()))
                .max(Comparator.comparingInt(Report::getYear).thenComparingInt(Report::getQuarter))
                .orElseGet(() -> reportGenerateService.generateAndSave(userId));
        return buildDetail(latest);
    }

    public ReportDetailResponse getDetail(Long userId, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("존재하지 않는 리포트입니다."));
        return buildDetail(report);
    }

    private ReportDetailResponse buildDetail(Report report) {
        // report.getRegionCode()는 이제 구코드(5자리)
        String districtCode = report.getRegionCode();
        District district = districtRepository.findById(districtCode)
                .orElse(null);
        String districtName = district != null ? district.getDistrictName() : "알 수 없음";

        String categoryName = report.getCategoryCode() == null ? null
                : categoryRepository.findById(report.getCategoryCode())
                        .map(Category::getCategoryName)
                        .orElse(null);

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
                districtCode,        // regionCode (구코드)
                districtName,        // district (구명)
                districtName,        // regionName (구 기반이라 동일)
                categoryName,
                report.getYear() + "Q" + report.getQuarter(),
                report.getGrade(),
                report.getDeclineType(),
                report.getScore(),
                report.getSummary(),
                report.getAiOutlook(),
                report.getPredictedTrend(),
                report.getPredictedNextGrade(),
                causes,
                signals,
                similarCases,
                decision,
                alternativeRegions
        );
    }

    private ReportDetailResponse.SimilarCasePreview toSimilarCasePreview(ReportSimilarCase c) {
        // AI가 보내준 regionName 우선 사용
        String regionName = c.getRegionName();
        if (regionName == null || regionName.isBlank()) {
            // fallback: 구코드로 조회
            regionName = districtRepository.findById(c.getRegionCode())
                    .map(District::getDistrictName)
                    .orElse("알 수 없음");
        }
        return new ReportDetailResponse.SimilarCasePreview(
                c.getId(), c.getRegionCode(), regionName, c.getSummary(),
                new ReportDetailResponse.Period(toNullableInt(c.getStartYear()), toNullableInt(c.getEndYear())));
    }

    private static Integer toNullableInt(Short value) {
        return value == null ? null : value.intValue();
    }

    private ReportDetailResponse.AlternativeRegion toAlternativeRegion(ReportAlternativeRegion a, int rank) {
        // AI가 보내준 코드로 구명 조회
        String regionName = districtRepository.findById(a.getRegionCode())
                .map(District::getDistrictName)
                .orElse("알 수 없음");
        return new ReportDetailResponse.AlternativeRegion(rank, a.getRegionCode(), regionName, a.getReason(), a.getStat());
    }
}
