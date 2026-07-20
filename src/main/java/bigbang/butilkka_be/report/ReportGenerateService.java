package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.report.dto.ReportGenerateRequest;
import bigbang.butilkka_be.report.dto.ReportGenerateResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
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
    private final DistrictStatsQueryService districtStatsQueryService;
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

        if (user.getStoreRegion() == null || user.getStoreRegion().length() < 5) {
            throw AppException.badRequest("등록된 가게 정보가 없거나 지역 코드가 올바르지 않습니다.");
        }

        // 10자리 행정동 코드에서 앞 5자리 구코드 추출
        String districtCode = user.getStoreRegion().substring(0, 5);

        // 현재 분기 계산
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int quarter = (now.getMonthValue() - 1) / 3 + 1;

        // DistrictStats에서 최신 데이터 조회
        List<DistrictStats> history = districtStatsQueryService.historyForDistrict(districtCode);
        if (history.isEmpty()) {
            throw AppException.notFound("해당 지역의 상권 통계가 없습니다.");
        }

        DistrictStats latest = history.get(history.size() - 1);
        String grade = latest.getDeclineGrade() != null ? latest.getDeclineGrade() : "C";
        int score = gradeToScore(grade);
        String declineType = latest.getDirection() != null ? latest.getDirection() : "정체";

        // AI 요청 생성
        ReportGenerateRequest.ReportContext context = new ReportGenerateRequest.ReportContext(
                toDouble(latest.getSalesDelta()),
                toDouble(latest.getFootTrafficDelta()),
                toDouble(latest.getStoreCountDelta()),
                toDouble(latest.getClosureRate()),
                toDouble(latest.getVacancyRate()),
                null,  // topAgeGroup - 구 기반에는 없음
                null   // topGender - 구 기반에는 없음
        );

        // 8분기 이력 (선택)
        ReportGenerateRequest.QuarterlyHistory quarterlyHistory = null;
        if (history.size() >= 8) {
            List<DistrictStats> last8 = history.subList(history.size() - 8, history.size());
            quarterlyHistory = new ReportGenerateRequest.QuarterlyHistory(
                    last8.stream().map(s -> toDouble(s.getSalesDelta())).toList(),
                    last8.stream().map(s -> s.getFootTraffic() != null ? s.getFootTraffic().doubleValue() : 0.0).toList(),
                    last8.stream().map(s -> s.getStoreCount() != null ? s.getStoreCount().doubleValue() : 0.0).toList(),
                    last8.stream().map(s -> toDouble(s.getClosureRate())).toList()
            );
        }

        // AI 종합 전망 생성 시 추가 조건
        String outlookInstructions = "1. 지역명을 반복하지 말 것 (예: '명동 명동' 금지). 2. 구체적인 수치(%, 숫자)를 포함하지 말 것.";

        ReportGenerateRequest request = new ReportGenerateRequest(
                districtCode, latest.getDistrictName(), latest.getDistrictName(),
                year, quarter, grade, score, declineType, context, quarterlyHistory, outlookInstructions, null
        );

        log.info("리포트 생성 요청 - district: {}, year: {}, quarter: {}", districtCode, year, quarter);

        // 같은 가게+분기 기존 리포트가 있으면 그대로 반환 (재생성 안 함)
        var existingReport = reportRepository.findByUserIdAndRegionCodeAndYearAndQuarter(userId, districtCode, year, quarter);
        if (existingReport.isPresent()) {
            log.info("기존 리포트 존재 - reportId: {}", existingReport.get().getReportId());
            return existingReport.get();
        }

        // AI 서버 호출
        ReportGenerateResponse aiResponse = aiServerClient.generateReport(request);
        if (aiResponse == null) {
            throw AppException.badRequest("AI 서버 응답이 비어 있습니다.");
        }

        // 새 리포트 생성
        Report report = Report.create(userId, districtCode, user.getCategoryCode(), year, quarter, grade, score, declineType);

        // AI 추천 카드 데이터 추출
        var aiRec = aiResponse.aiRecommendation();
        String aiRecBadgeType = aiRec != null ? aiRec.badgeType() : "AI 추천";
        String aiRecTitle = aiRec != null ? aiRec.title() : null;
        String aiRecReasonTitle = aiRec != null ? aiRec.reasonTitle() : null;
        String aiRecReasonDetail = aiRec != null ? aiRec.reasonDetail() : null;

        report.applyAiResponse(
                aiResponse.summary(),
                aiResponse.aiOutlook(),
                aiResponse.predictedTrend(),
                aiResponse.predictedNextGrade(),
                aiResponse.decisionRecommendation(),
                aiResponse.decisionTitle(),
                aiResponse.decisionDescription(),
                aiRecBadgeType,
                aiRecTitle,
                aiRecReasonTitle,
                aiRecReasonDetail
        );
        reportRepository.save(report);

        Long reportId = report.getReportId();

        // Causes 저장
        for (var cause : orEmpty(aiResponse.causes())) {
            reportCauseRepository.save(ReportCause.create(reportId, cause.title(), cause.level(), cause.description()));
        }

        // Signals 저장
        for (var signal : orEmpty(aiResponse.signals())) {
            reportSignalRepository.save(ReportSignal.create(reportId, signal.title(), signal.description()));
        }

        // Similar Cases 저장
        for (var sc : orEmpty(aiResponse.similarCases())) {
            reportSimilarCaseRepository.save(ReportSimilarCase.create(
                    reportId, sc.regionCode(), sc.regionName(), sc.summary(), sc.description(),
                    sc.startYear(), sc.endYear(), sc.tag1(), sc.tag2(), sc.tag3(), sc.tag4()
            ));
        }

        // Alternative Regions 저장
        for (var ar : orEmpty(aiResponse.alternativeRegions())) {
            reportAlternativeRegionRepository.save(ReportAlternativeRegion.create(
                    reportId, ar.regionCode(), ar.rank(), ar.aiMessage()
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

    /**
     * 과거 분기 리포트 생성 (테스트/시딩용)
     */
    @Transactional
    public Report generateForPastQuarter(Long userId, int year, int quarter) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다."));

        if (user.getStoreRegion() == null || user.getStoreRegion().length() < 5) {
            throw AppException.badRequest("등록된 가게 정보가 없거나 지역 코드가 올바르지 않습니다.");
        }

        String districtCode = user.getStoreRegion().substring(0, 5);

        // 해당 분기의 district_stats 조회
        List<DistrictStats> quarterStats = districtStatsQueryService.forQuarter(year, quarter);
        DistrictStats stats = quarterStats.stream()
                .filter(s -> s.getDistrictCode().equals(districtCode))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("해당 분기의 상권 통계가 없습니다."));

        String grade = stats.getDeclineGrade() != null ? stats.getDeclineGrade() : "C";
        int score = gradeToScore(grade);
        String declineType = stats.getDirection() != null ? stats.getDirection() : "정체";

        ReportGenerateRequest.ReportContext context = new ReportGenerateRequest.ReportContext(
                toDouble(stats.getSalesDelta()),
                toDouble(stats.getFootTrafficDelta()),
                toDouble(stats.getStoreCountDelta()),
                toDouble(stats.getClosureRate()),
                toDouble(stats.getVacancyRate()),
                null, null
        );

        String outlookInstructions = "1. 지역명을 반복하지 말 것 (예: '명동 명동' 금지). 2. 구체적인 수치(%, 숫자)를 포함하지 말 것.";

        // 해당 분기의 모든 구 등급 정보 (대안 상권 추천용)
        List<ReportGenerateRequest.GradeInfo> allGrades = quarterStats.stream()
                .map(s -> new ReportGenerateRequest.GradeInfo(
                        s.getDistrictCode(),
                        s.getDistrictName(),
                        s.getDeclineGrade() != null ? s.getDeclineGrade() : "C",
                        gradeToScore(s.getDeclineGrade() != null ? s.getDeclineGrade() : "C"),
                        s.getDirection() != null ? s.getDirection() : "정체"
                ))
                .toList();

        ReportGenerateRequest request = new ReportGenerateRequest(
                districtCode, stats.getDistrictName(), stats.getDistrictName(),
                year, quarter, grade, score, declineType, context, null, outlookInstructions, allGrades
        );

        log.info("과거 리포트 생성 요청 - district: {}, year: {}, quarter: {}", districtCode, year, quarter);

        // 기존 리포트 체크
        var existingReport = reportRepository.findByUserIdAndRegionCodeAndYearAndQuarter(userId, districtCode, year, quarter);
        if (existingReport.isPresent()) {
            log.info("기존 리포트 존재 - reportId: {}", existingReport.get().getReportId());
            return existingReport.get();
        }

        // AI 서버 호출
        ReportGenerateResponse aiResponse = aiServerClient.generateReport(request);
        if (aiResponse == null) {
            throw AppException.badRequest("AI 서버 응답이 비어 있습니다.");
        }

        // 리포트 생성
        Report report = Report.create(userId, districtCode, user.getCategoryCode(), year, quarter, grade, score, declineType);

        var aiRec = aiResponse.aiRecommendation();
        String aiRecBadgeType = aiRec != null ? aiRec.badgeType() : "AI 추천";
        String aiRecTitle = aiRec != null ? aiRec.title() : null;
        String aiRecReasonTitle = aiRec != null ? aiRec.reasonTitle() : null;
        String aiRecReasonDetail = aiRec != null ? aiRec.reasonDetail() : null;

        report.applyAiResponse(
                aiResponse.summary(),
                aiResponse.aiOutlook(),
                aiResponse.predictedTrend(),
                aiResponse.predictedNextGrade(),
                aiResponse.decisionRecommendation(),
                aiResponse.decisionTitle(),
                aiResponse.decisionDescription(),
                aiRecBadgeType,
                aiRecTitle,
                aiRecReasonTitle,
                aiRecReasonDetail
        );
        reportRepository.save(report);

        Long reportId = report.getReportId();

        for (var cause : orEmpty(aiResponse.causes())) {
            reportCauseRepository.save(ReportCause.create(reportId, cause.title(), cause.level(), cause.description()));
        }
        for (var signal : orEmpty(aiResponse.signals())) {
            reportSignalRepository.save(ReportSignal.create(reportId, signal.title(), signal.description()));
        }
        for (var sc : orEmpty(aiResponse.similarCases())) {
            reportSimilarCaseRepository.save(ReportSimilarCase.create(
                    reportId, sc.regionCode(), sc.regionName(), sc.summary(), sc.description(),
                    sc.startYear(), sc.endYear(), sc.tag1(), sc.tag2(), sc.tag3(), sc.tag4()
            ));
        }
        for (var ar : orEmpty(aiResponse.alternativeRegions())) {
            reportAlternativeRegionRepository.save(ReportAlternativeRegion.create(
                    reportId, ar.regionCode(), ar.rank(), ar.aiMessage()
            ));
        }
        if (aiResponse.decisionReasons() != null) {
            reportDecisionReasonsRepository.save(ReportDecisionReasons.create(
                    reportId,
                    aiResponse.decisionReasons().reason1(),
                    aiResponse.decisionReasons().reason2(),
                    aiResponse.decisionReasons().reason3()
            ));
        }

        log.info("과거 리포트 생성 완료 - reportId: {}", reportId);
        return report;
    }

    /**
     * 특정 지역의 과거 분기 리포트 생성 (테스트/시딩용)
     */
    @Transactional
    public Report generateForPastQuarterWithRegion(Long userId, String districtCode, int year, int quarter) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다."));

        // 해당 분기의 district_stats 조회
        List<DistrictStats> quarterStats = districtStatsQueryService.forQuarter(year, quarter);
        DistrictStats stats = quarterStats.stream()
                .filter(s -> s.getDistrictCode().equals(districtCode))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("해당 분기의 상권 통계가 없습니다: " + districtCode));

        String grade = stats.getDeclineGrade() != null ? stats.getDeclineGrade() : "C";
        int score = gradeToScore(grade);
        String declineType = stats.getDirection() != null ? stats.getDirection() : "정체";

        ReportGenerateRequest.ReportContext context = new ReportGenerateRequest.ReportContext(
                toDouble(stats.getSalesDelta()),
                toDouble(stats.getFootTrafficDelta()),
                toDouble(stats.getStoreCountDelta()),
                toDouble(stats.getClosureRate()),
                toDouble(stats.getVacancyRate()),
                null, null
        );

        String outlookInstructions = "1. 지역명을 반복하지 말 것 (예: '명동 명동' 금지). 2. 구체적인 수치(%, 숫자)를 포함하지 말 것.";

        List<ReportGenerateRequest.GradeInfo> allGrades = quarterStats.stream()
                .map(s -> new ReportGenerateRequest.GradeInfo(
                        s.getDistrictCode(),
                        s.getDistrictName(),
                        s.getDeclineGrade() != null ? s.getDeclineGrade() : "C",
                        gradeToScore(s.getDeclineGrade() != null ? s.getDeclineGrade() : "C"),
                        s.getDirection() != null ? s.getDirection() : "정체"
                ))
                .toList();

        ReportGenerateRequest request = new ReportGenerateRequest(
                districtCode, stats.getDistrictName(), stats.getDistrictName(),
                year, quarter, grade, score, declineType, context, null, outlookInstructions, allGrades
        );

        log.info("과거 리포트 생성 요청 (지역 지정) - district: {}, year: {}, quarter: {}", districtCode, year, quarter);

        // 기존 리포트 체크
        var existingReport = reportRepository.findByUserIdAndRegionCodeAndYearAndQuarter(userId, districtCode, year, quarter);
        if (existingReport.isPresent()) {
            log.info("기존 리포트 존재 - reportId: {}", existingReport.get().getReportId());
            return existingReport.get();
        }

        // AI 서버 호출
        ReportGenerateResponse aiResponse = aiServerClient.generateReport(request);
        if (aiResponse == null) {
            throw AppException.badRequest("AI 서버 응답이 비어 있습니다.");
        }

        // 리포트 생성
        Report report = Report.create(userId, districtCode, user.getCategoryCode(), year, quarter, grade, score, declineType);

        var aiRec = aiResponse.aiRecommendation();
        String aiRecBadgeType = aiRec != null ? aiRec.badgeType() : "AI 추천";
        String aiRecTitle = aiRec != null ? aiRec.title() : null;
        String aiRecReasonTitle = aiRec != null ? aiRec.reasonTitle() : null;
        String aiRecReasonDetail = aiRec != null ? aiRec.reasonDetail() : null;

        report.applyAiResponse(
                aiResponse.summary(),
                aiResponse.aiOutlook(),
                aiResponse.predictedTrend(),
                aiResponse.predictedNextGrade(),
                aiResponse.decisionRecommendation(),
                aiResponse.decisionTitle(),
                aiResponse.decisionDescription(),
                aiRecBadgeType,
                aiRecTitle,
                aiRecReasonTitle,
                aiRecReasonDetail
        );
        reportRepository.save(report);

        Long reportId = report.getReportId();

        for (var cause : orEmpty(aiResponse.causes())) {
            reportCauseRepository.save(ReportCause.create(reportId, cause.title(), cause.level(), cause.description()));
        }
        for (var signal : orEmpty(aiResponse.signals())) {
            reportSignalRepository.save(ReportSignal.create(reportId, signal.title(), signal.description()));
        }
        for (var sc : orEmpty(aiResponse.similarCases())) {
            reportSimilarCaseRepository.save(ReportSimilarCase.create(
                    reportId, sc.regionCode(), sc.regionName(), sc.summary(), sc.description(),
                    sc.startYear(), sc.endYear(), sc.tag1(), sc.tag2(), sc.tag3(), sc.tag4()
            ));
        }
        for (var ar : orEmpty(aiResponse.alternativeRegions())) {
            reportAlternativeRegionRepository.save(ReportAlternativeRegion.create(
                    reportId, ar.regionCode(), ar.rank(), ar.aiMessage()
            ));
        }
        if (aiResponse.decisionReasons() != null) {
            reportDecisionReasonsRepository.save(ReportDecisionReasons.create(
                    reportId,
                    aiResponse.decisionReasons().reason1(),
                    aiResponse.decisionReasons().reason2(),
                    aiResponse.decisionReasons().reason3()
            ));
        }

        log.info("과거 리포트 생성 완료 (지역 지정) - reportId: {}", reportId);
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

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
    }
}
