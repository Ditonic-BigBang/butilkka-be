package bigbang.butilkka_be.report;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportDetailServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportCauseRepository reportCauseRepository;
    @Mock
    private ReportSignalRepository reportSignalRepository;
    @Mock
    private ReportSimilarCaseRepository reportSimilarCaseRepository;
    @Mock
    private ReportAlternativeRegionRepository reportAlternativeRegionRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReportGenerateService reportGenerateService;
    @Mock
    private DistrictStatsQueryService districtStatsQueryService;

    private ReportDetailService service;

    @BeforeEach
    void setUp() {
        service = new ReportDetailService(
                reportRepository, reportCauseRepository, reportSignalRepository,
                reportSimilarCaseRepository, reportAlternativeRegionRepository,
                districtRepository, categoryRepository, userRepository, reportGenerateService,
                districtStatsQueryService);
    }

    private static Report reportOf(Long reportId, Long userId, String districtCode, int year, int quarter, String grade, int score) {
        Report report = mock(Report.class);
        lenient().when(report.getReportId()).thenReturn(reportId);
        lenient().when(report.getUserId()).thenReturn(userId);
        lenient().when(report.getRegionCode()).thenReturn(districtCode);
        lenient().when(report.getCategoryCode()).thenReturn("CS100001");
        lenient().when(report.getYear()).thenReturn(year);
        lenient().when(report.getQuarter()).thenReturn(quarter);
        lenient().when(report.getGrade()).thenReturn(grade);
        lenient().when(report.getDeclineType()).thenReturn("성장");
        lenient().when(report.getScore()).thenReturn(score);
        lenient().when(report.getSummary()).thenReturn("한 줄 브리핑");
        lenient().when(report.getAiOutlook()).thenReturn("AI 전망");
        lenient().when(report.getPredictedTrend()).thenReturn(null);
        lenient().when(report.getPredictedNextGrade()).thenReturn(null);
        lenient().when(report.getDecisionRecommendation()).thenReturn("버티기");
        lenient().when(report.getDecisionTitle()).thenReturn("현 위치 유지 권장");
        lenient().when(report.getDecisionDescription()).thenReturn("의사결정 설명");
        return report;
    }

    private void stubDistrictAndCategory(String districtCode) {
        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById(districtCode)).thenReturn(Optional.of(district));

        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));
    }

    @Test
    void getLatest_returnsCurrentQuarterReport() {
        // 현재 분기 계산
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentQuarter = (now.getMonthValue() - 1) / 3 + 1;

        User user = mock(User.class);
        when(user.getStoreRegion()).thenReturn("1168064000");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Report currentQuarterReport = reportOf(1L, 1L, "11680", currentYear, currentQuarter, "A", 90);
        when(reportRepository.findByUserIdAndRegionCodeAndYearAndQuarter(1L, "11680", currentYear, currentQuarter))
                .thenReturn(Optional.of(currentQuarterReport));
        stubDistrictAndCategory("11680");
        when(reportCauseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSignalRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportAlternativeRegionRepository.findByReportId(1L)).thenReturn(List.of());

        ReportDetailResponse response = service.getLatest(1L);

        assertThat(response.reportId()).isEqualTo(1L);
        assertThat(response.quarter()).isEqualTo(currentYear + "Q" + currentQuarter);
        assertThat(response.districtName()).isEqualTo("강남구");
        assertThat(response.score()).isEqualTo(90);
        assertThat(response.generated()).isFalse();
    }

    @Test
    void getLatest_generatesNewReportWhenNoCurrentQuarter() {
        // 현재 분기 계산
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentQuarter = (now.getMonthValue() - 1) / 3 + 1;

        User user = mock(User.class);
        when(user.getStoreRegion()).thenReturn("1168064000");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // 현재 분기 리포트 없음
        when(reportRepository.findByUserIdAndRegionCodeAndYearAndQuarter(1L, "11680", currentYear, currentQuarter))
                .thenReturn(Optional.empty());

        // 새로 생성된 리포트
        Report newReport = reportOf(2L, 1L, "11680", currentYear, currentQuarter, "B", 70);
        when(reportGenerateService.generateAndSave(1L)).thenReturn(newReport);
        stubDistrictAndCategory("11680");
        when(reportCauseRepository.findByReportId(2L)).thenReturn(List.of());
        when(reportSignalRepository.findByReportId(2L)).thenReturn(List.of());
        when(reportSimilarCaseRepository.findByReportId(2L)).thenReturn(List.of());
        when(reportAlternativeRegionRepository.findByReportId(2L)).thenReturn(List.of());

        ReportDetailResponse response = service.getLatest(1L);

        assertThat(response.reportId()).isEqualTo(2L);
        assertThat(response.generated()).isTrue();
    }

    @Test
    void getDetail_withOwnedReport_returnsDetail() {
        Report report = reportOf(1L, 1L, "11680", 2026, 4, "A", 90);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        stubDistrictAndCategory("11680");
        when(reportCauseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSignalRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportAlternativeRegionRepository.findByReportId(1L)).thenReturn(List.of());

        ReportDetailResponse response = service.getDetail(1L, 1L);

        assertThat(response.reportId()).isEqualTo(1L);
    }

    @Test
    void getDetail_withOtherUsersReport_throwsNotFound() {
        Report report = reportOf(1L, 2L, "11680", 2026, 4, "A", 90);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getDetail(1L, 1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getDetail_withUnknownReportId_throwsNotFound() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(1L, 99L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
