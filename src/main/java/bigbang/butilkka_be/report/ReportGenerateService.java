package bigbang.butilkka_be.report;

import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.report.dto.ReportGenerateRequest;
import bigbang.butilkka_be.report.dto.ReportGenerateResponse;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsRepository;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerateService {

    private final AiServerClient aiServerClient;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final CategoryRepository categoryRepository;
    private final CommercialStatsRepository commercialStatsRepository;
    private final ReportRepository reportRepository;
    private final ReportCauseRepository reportCauseRepository;
    private final ReportSignalRepository reportSignalRepository;
    private final ReportSimilarCaseRepository reportSimilarCaseRepository;
    private final ReportAlternativeRegionRepository reportAlternativeRegionRepository;
    private final ReportDecisionReasonsRepository reportDecisionReasonsRepository;

    @Transactional
    public Report generateAndSave(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다."));

        if (user.getStoreRegion() == null) {
            throw AppException.badRequest("등록된 가게 정보가 없습니다.");
        }

        String regionCode = user.getStoreRegion();
        Region region = regionRepository.findById(regionCode)
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));

        // 현재 분기 계산
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int quarter = (now.getMonthValue() - 1) / 3 + 1;

        // CommercialStats에서 최신 데이터 조회
        List<CommercialStats> history = commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc(regionCode);
        if (history.isEmpty()) {
            throw AppException.notFound("해당 지역의 상권 통계가 없습니다.");
        }

        CommercialStats latest = history.get(history.size() - 1);
        String grade = latest.getDeclineGrade() != null ? latest.getDeclineGrade() : "C";
        int score = gradeToScore(grade);
        String declineType = determineDeclineType(history);

        // AI 요청 생성
        ReportGenerateRequest.ReportContext context = new ReportGenerateRequest.ReportContext(
                toDouble(latest.getSalesDelta()),
                toDouble(latest.getFootTrafficDelta()),
                toDouble(latest.getStoreCountDelta()),
                toDouble(latest.getClosureRate()),
                toDouble(latest.getVacancyRate()),
                latest.getTopAgeGroup(),
                latest.getTopGender()
        );

        // 8분기 이력 (선택)
        ReportGenerateRequest.QuarterlyHistory quarterlyHistory = null;
        if (history.size() >= 8) {
            List<CommercialStats> last8 = history.subList(history.size() - 8, history.size());
            quarterlyHistory = new ReportGenerateRequest.QuarterlyHistory(
                    last8.stream().map(s -> toDouble(s.getSalesDelta())).toList(),
                    last8.stream().map(s -> s.getFootTraffic() != null ? s.getFootTraffic().doubleValue() : 0.0).toList(),
                    last8.stream().map(s -> s.getStoreCount() != null ? s.getStoreCount().doubleValue() : 0.0).toList(),
                    last8.stream().map(s -> toDouble(s.getClosureRate())).toList()
            );
        }

        ReportGenerateRequest request = new ReportGenerateRequest(
                regionCode, region.getRegionName(), district.getDistrictName(),
                year, quarter, grade, score, declineType, context, quarterlyHistory
        );

        log.info("리포트 생성 요청 - region: {}, year: {}, quarter: {}", regionCode, year, quarter);

        // AI 서버 호출
        ReportGenerateResponse aiResponse = aiServerClient.generateReport(request);

        // Report 저장
        Report report = Report.create(userId, regionCode, user.getCategoryCode(), year, quarter, grade, score, declineType);
        report.applyAiResponse(
                aiResponse.summary(),
                aiResponse.aiOutlook(),
                aiResponse.predictedTrend(),
                aiResponse.predictedNextGrade(),
                aiResponse.decisionRecommendation(),
                aiResponse.decisionTitle(),
                aiResponse.decisionDescription()
        );
        reportRepository.save(report);

        Long reportId = report.getReportId();

        // Causes 저장
        for (var cause : aiResponse.causes()) {
            reportCauseRepository.save(ReportCause.create(reportId, cause.title(), cause.level(), cause.description()));
        }

        // Signals 저장
        for (var signal : aiResponse.signals()) {
            reportSignalRepository.save(ReportSignal.create(reportId, signal.title(), signal.description()));
        }

        // Similar Cases 저장
        for (var sc : aiResponse.similarCases()) {
            reportSimilarCaseRepository.save(ReportSimilarCase.create(
                    reportId, sc.regionCode(), sc.regionName(), sc.summary(), sc.description(),
                    sc.startYear(), sc.endYear(), sc.tag1(), sc.tag2(), sc.tag3(), sc.tag4()
            ));
        }

        // Alternative Regions 저장
        for (var ar : aiResponse.alternativeRegions()) {
            reportAlternativeRegionRepository.save(ReportAlternativeRegion.create(
                    reportId, ar.regionCode(), ar.reason(), ar.stat()
            ));
        }

        // Decision Reasons 저장
        if (aiResponse.decisionReasons() != null) {
            reportDecisionReasonsRepository.save(ReportDecisionReasons.create(
                    reportId,
                    aiResponse.decisionReasons().reason1(),
                    aiResponse.decisionReasons().reason2(),
                    aiResponse.decisionReasons().reason3()
            ));
        }

        log.info("리포트 생성 완료 - reportId: {}", reportId);
        return report;
    }

    private int gradeToScore(String grade) {
        return switch (grade) {
            case "A" -> 90;
            case "B" -> 70;
            case "C" -> 50;
            case "D" -> 30;
            case "E" -> 10;
            default -> 50;
        };
    }

    private String determineDeclineType(List<CommercialStats> history) {
        if (history.size() < 2) return "정체";

        CommercialStats latest = history.get(history.size() - 1);
        CommercialStats prev = history.get(history.size() - 2);

        BigDecimal latestDelta = latest.getSalesDelta();
        BigDecimal prevDelta = prev.getSalesDelta();

        if (latestDelta == null) return "정체";

        if (latestDelta.compareTo(BigDecimal.ZERO) > 0) {
            return "성장";
        } else if (latestDelta.compareTo(new BigDecimal("-0.05")) < 0) {
            return "쇠퇴";
        } else {
            return "정체";
        }
    }

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }
}
