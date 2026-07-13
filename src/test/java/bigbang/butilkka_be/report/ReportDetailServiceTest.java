package bigbang.butilkka_be.report;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private ReportDetailService service;

    @BeforeEach
    void setUp() {
        service = new ReportDetailService(
                reportRepository, reportCauseRepository, reportSignalRepository,
                reportSimilarCaseRepository, reportAlternativeRegionRepository,
                regionRepository, districtRepository, categoryRepository);
    }

    private static Report reportOf(Long reportId, Long userId, int year, int quarter, String grade, int score) {
        Report report = mock(Report.class);
        lenient().when(report.getReportId()).thenReturn(reportId);
        lenient().when(report.getUserId()).thenReturn(userId);
        lenient().when(report.getRegionCode()).thenReturn("1168064000");
        lenient().when(report.getCategoryCode()).thenReturn("CS100001");
        lenient().when(report.getYear()).thenReturn(year);
        lenient().when(report.getQuarter()).thenReturn(quarter);
        lenient().when(report.getGrade()).thenReturn(grade);
        lenient().when(report.getDeclineType()).thenReturn("성장형");
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

    private void stubRegionAndCategory() {
        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("역삼1동");
        when(region.getDistrictCode()).thenReturn("11680");
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));

        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));
    }

    @Test
    void getLatest_picksHighestYearQuarter() {
        Report older = reportOf(5L, 1L, 2026, 1, "B", 70);
        Report newer = reportOf(1L, 1L, 2026, 4, "A", 90);
        when(reportRepository.findByUserId(1L)).thenReturn(List.of(older, newer));
        stubRegionAndCategory();
        when(reportCauseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSignalRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportAlternativeRegionRepository.findByReportId(1L)).thenReturn(List.of());

        ReportDetailResponse response = service.getLatest(1L);

        assertThat(response.reportId()).isEqualTo(1L);
        assertThat(response.quarter()).isEqualTo("2026Q4");
        assertThat(response.regionName()).isEqualTo("역삼1동");
        assertThat(response.districtName()).isEqualTo("강남구");
        assertThat(response.categoryName()).isEqualTo("한식음식점");
        assertThat(response.score()).isEqualTo(90);
        assertThat(response.decision().recommendation()).isEqualTo("버티기");
    }

    @Test
    void getLatest_withNoReports_throwsNotFound() {
        when(reportRepository.findByUserId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getLatest(1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getDetail_withOwnedReport_returnsDetail() {
        Report report = reportOf(1L, 1L, 2026, 4, "A", 90);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        stubRegionAndCategory();
        when(reportCauseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSignalRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportAlternativeRegionRepository.findByReportId(1L)).thenReturn(List.of());

        ReportDetailResponse response = service.getDetail(1L, 1L);

        assertThat(response.reportId()).isEqualTo(1L);
    }

    @Test
    void getDetail_withOtherUsersReport_throwsNotFound() {
        Report report = reportOf(1L, 2L, 2026, 4, "A", 90);
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

    @Test
    void getDetail_buildsCausesSignalsAndAlternativeRegionsWithRank() {
        Report report = reportOf(3L, 1L, 2026, 4, "C", 50);
        when(reportRepository.findById(3L)).thenReturn(Optional.of(report));
        stubRegionAndCategory();

        ReportCause cause = mock(ReportCause.class);
        when(cause.getTitle()).thenReturn("경기 침체 영향");
        when(cause.getLevel()).thenReturn("높음");
        when(cause.getDescription()).thenReturn("전반적인 소비 심리 위축으로 매출이 감소하고 있습니다");
        when(reportCauseRepository.findByReportId(3L)).thenReturn(List.of(cause));

        ReportSignal signal = mock(ReportSignal.class);
        when(signal.getTitle()).thenReturn("폐업률 고위험");
        when(signal.getDescription()).thenReturn("폐업률 6.8%로 여전히 높은 수준");
        when(reportSignalRepository.findByReportId(3L)).thenReturn(List.of(signal));

        when(reportSimilarCaseRepository.findByReportId(3L)).thenReturn(List.of());

        ReportAlternativeRegion alt1 = mock(ReportAlternativeRegion.class);
        when(alt1.getRegionCode()).thenReturn("1120065000");
        when(alt1.getReason()).thenReturn("성수동: 성장세 지속 중, 젊은 고객층 유입 활발");
        when(alt1.getStat()).thenReturn("유동인구 +12.8%");
        Region altRegion1 = mock(Region.class);
        when(altRegion1.getRegionName()).thenReturn("성수1가1동");
        when(regionRepository.findById("1120065000")).thenReturn(Optional.of(altRegion1));

        ReportAlternativeRegion alt2 = mock(ReportAlternativeRegion.class);
        when(alt2.getRegionCode()).thenReturn("1171058000");
        when(alt2.getReason()).thenReturn("송리단길: 신흥 상권으로 임대료 합리적, 성장 가능성 높음");
        when(alt2.getStat()).thenReturn("임대료 -15.3%");
        Region altRegion2 = mock(Region.class);
        when(altRegion2.getRegionName()).thenReturn("잠실본동");
        when(regionRepository.findById("1171058000")).thenReturn(Optional.of(altRegion2));

        when(reportAlternativeRegionRepository.findByReportId(3L)).thenReturn(List.of(alt1, alt2));

        ReportDetailResponse response = service.getDetail(1L, 3L);

        assertThat(response.causes()).hasSize(1);
        assertThat(response.causes().get(0).description()).isEqualTo("전반적인 소비 심리 위축으로 매출이 감소하고 있습니다");
        assertThat(response.leadingSignals()).hasSize(1);
        assertThat(response.alternativeRegions()).hasSize(2);
        assertThat(response.alternativeRegions().get(0).rank()).isEqualTo(1);
        assertThat(response.alternativeRegions().get(0).regionName()).isEqualTo("성수1가1동");
        assertThat(response.alternativeRegions().get(1).rank()).isEqualTo(2);
        assertThat(response.alternativeRegions().get(1).stat()).isEqualTo("임대료 -15.3%");
    }

    @Test
    void getDetail_limitsSimilarCasesToThree() {
        Report report = reportOf(1L, 1L, 2026, 4, "A", 90);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        stubRegionAndCategory();
        when(reportCauseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSignalRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportAlternativeRegionRepository.findByReportId(1L)).thenReturn(List.of());

        ReportSimilarCase case1 = similarCaseOf("case-1", "1168051000", "사례1");
        ReportSimilarCase case2 = similarCaseOf("case-2", "1168051000", "사례2");
        ReportSimilarCase case3 = similarCaseOf("case-3", "1168051000", "사례3");
        ReportSimilarCase case4 = similarCaseOf("case-4", "1168051000", "사례4");
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of(case1, case2, case3, case4));

        Region caseRegion = mock(Region.class);
        when(caseRegion.getRegionName()).thenReturn("신사동");
        when(regionRepository.findById("1168051000")).thenReturn(Optional.of(caseRegion));

        ReportDetailResponse response = service.getDetail(1L, 1L);

        assertThat(response.similarCases()).hasSize(3);
        assertThat(response.similarCases().get(0).caseId()).isEqualTo("case-1");
    }

    private static ReportSimilarCase similarCaseOf(String id, String regionCode, String summary) {
        ReportSimilarCase c = mock(ReportSimilarCase.class);
        lenient().when(c.getId()).thenReturn(id);
        lenient().when(c.getRegionCode()).thenReturn(regionCode);
        lenient().when(c.getSummary()).thenReturn(summary);
        lenient().when(c.getStartYear()).thenReturn((short) 2019);
        lenient().when(c.getEndYear()).thenReturn((short) 2022);
        return c;
    }

    @Test
    void getDetail_withNullStartAndEndYear_returnsNullPeriodFields() {
        Report report = reportOf(1L, 1L, 2026, 4, "A", 90);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        stubRegionAndCategory();
        when(reportCauseRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportSignalRepository.findByReportId(1L)).thenReturn(List.of());
        when(reportAlternativeRegionRepository.findByReportId(1L)).thenReturn(List.of());

        ReportSimilarCase c = mock(ReportSimilarCase.class);
        lenient().when(c.getId()).thenReturn("case-1");
        lenient().when(c.getRegionCode()).thenReturn("1168051000");
        lenient().when(c.getSummary()).thenReturn("사례1");
        lenient().when(c.getStartYear()).thenReturn(null);
        lenient().when(c.getEndYear()).thenReturn(null);
        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of(c));

        Region caseRegion = mock(Region.class);
        when(caseRegion.getRegionName()).thenReturn("신사동");
        when(regionRepository.findById("1168051000")).thenReturn(Optional.of(caseRegion));

        ReportDetailResponse response = service.getDetail(1L, 1L);

        assertThat(response.similarCases()).hasSize(1);
        assertThat(response.similarCases().get(0).period().startYear()).isNull();
        assertThat(response.similarCases().get(0).period().endYear()).isNull();
    }
}
