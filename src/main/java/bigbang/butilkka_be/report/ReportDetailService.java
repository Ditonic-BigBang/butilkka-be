package bigbang.butilkka_be.report;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final DistrictStatsQueryService districtStatsQueryService;

    public ReportDetailResponse getLatest(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다."));
        String currentRegion = user.getStoreRegion();

        if (currentRegion == null || currentRegion.length() < 5) {
            throw AppException.badRequest("등록된 가게 정보가 없거나 지역 코드가 올바르지 않습니다.");
        }

        // 10자리 행정동 코드에서 앞 5자리 구코드 추출
        String districtCode = currentRegion.substring(0, 5);

        // 현재 분기 계산
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentQuarter = (now.getMonthValue() - 1) / 3 + 1;

        // 현재 분기의 리포트가 있는지 확인
        Optional<Report> currentQuarterReport = reportRepository.findByUserIdAndRegionCodeAndYearAndQuarter(
                userId, districtCode, currentYear, currentQuarter);

        if (currentQuarterReport.isPresent()) {
            // 현재 분기 리포트가 있으면 반환
            return buildDetail(currentQuarterReport.get(), false);
        } else {
            // 현재 분기 리포트가 없으면 새로 생성
            Report newReport = reportGenerateService.generateAndSave(userId);
            return buildDetail(newReport, true);
        }
    }

    public ReportDetailResponse getDetail(Long userId, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("존재하지 않는 리포트입니다."));
        return buildDetail(report, false);
    }

    private ReportDetailResponse buildDetail(Report report, boolean generated) {
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
        List<ReportDetailResponse.AlternativeRegion> alternativeRegions = enrichAlternativeRegions(alternativeRegionEntities);

        ReportDetailResponse.Decision decision = new ReportDetailResponse.Decision(
                report.getDecisionRecommendation(), report.getDecisionTitle(), report.getDecisionDescription());

        // AI 추천 카드
        ReportDetailResponse.AiRecommendation aiRecommendation = new ReportDetailResponse.AiRecommendation(
                report.getAiRecBadgeType() != null ? report.getAiRecBadgeType() : "AI 추천",
                report.getAiRecTitle(),
                report.getAiRecReasonTitle(),
                report.getAiRecReasonDetail()
        );

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
                alternativeRegions,
                aiRecommendation,
                generated
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

    /**
     * 대안 상권 목록에 BE DB의 통계 데이터를 추가하여 반환
     */
    private List<ReportDetailResponse.AlternativeRegion> enrichAlternativeRegions(List<ReportAlternativeRegion> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        // 모든 구의 최신 통계를 한 번에 조회
        Map<String, DistrictStats> latestStatsMap = districtStatsQueryService.latestPerDistrict()
                .stream()
                .collect(Collectors.toMap(DistrictStats::getDistrictCode, Function.identity()));

        // 최신 분기 라벨 (예: "2025Q1")
        String baseDate = districtStatsQueryService.getLatestQuarterLabel();

        List<ReportDetailResponse.AlternativeRegion> result = new ArrayList<>();
        for (ReportAlternativeRegion a : entities) {
            String regionName = districtRepository.findById(a.getRegionCode())
                    .map(District::getDistrictName)
                    .orElse("알 수 없음");

            // 해당 구의 최신 통계
            DistrictStats stats = latestStatsMap.get(a.getRegionCode());
            Integer storeCount = stats != null ? stats.getStoreCount() : null;
            Long floatingPopulation = stats != null ? stats.getFootTraffic() : null;
            Double vacancy = stats != null && stats.getVacancyRate() != null
                    ? stats.getVacancyRate().doubleValue()
                    : null;

            result.add(new ReportDetailResponse.AlternativeRegion(
                    a.getRankOrder(),
                    a.getRegionCode(),
                    regionName,
                    a.getAiMessage(),
                    storeCount,
                    floatingPopulation,
                    vacancy,
                    baseDate
            ));
        }

        // rank 순으로 정렬
        result.sort(Comparator.comparingInt(ReportDetailResponse.AlternativeRegion::rank));
        return result;
    }
}
