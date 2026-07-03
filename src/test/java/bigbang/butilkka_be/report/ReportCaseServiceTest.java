package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.report.dto.ReportCaseListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class ReportCaseServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportSimilarCaseRepository reportSimilarCaseRepository;
    @Mock
    private RegionRepository regionRepository;

    private ReportCaseService service;

    @BeforeEach
    void setUp() {
        service = new ReportCaseService(reportRepository, reportSimilarCaseRepository, regionRepository);
    }

    @Test
    void getCases_returnsAllFieldsWithRegionName() {
        Report report = mock(Report.class, withSettings().lenient());
        doReturn(1L).when(report).getReportId();
        doReturn(1L).when(report).getUserId();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        ReportSimilarCase c = mock(ReportSimilarCase.class, withSettings().lenient());
        doReturn("case-1").when(c).getId();
        doReturn("1168051000").when(c).getRegionCode();
        doReturn("요약").when(c).getSummary();
        doReturn("상세 설명").when(c).getDescription();
        doReturn("태그1").when(c).getTag1();
        doReturn("태그2").when(c).getTag2();
        doReturn("태그3").when(c).getTag3();
        doReturn("태그4").when(c).getTag4();
        doReturn((short) 2019).when(c).getStartYear();
        doReturn((short) 2022).when(c).getEndYear();

        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of(c));

        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("신사동");
        when(regionRepository.findById("1168051000")).thenReturn(Optional.of(region));

        ReportCaseListResponse response = service.getCases(1L, 1L, 0, 20);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().get(0).regionName()).isEqualTo("신사동");
        assertThat(response.cases().get(0).tag1()).isEqualTo("태그1");
        assertThat(response.cases().get(0).period().startYear()).isEqualTo(2019);
    }

    @Test
    void getCases_appliesOffsetAndLimit() {
        Report report = mock(Report.class, withSettings().lenient());
        doReturn(1L).when(report).getReportId();
        doReturn(1L).when(report).getUserId();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        ReportSimilarCase c1 = mock(ReportSimilarCase.class, withSettings().lenient());
        doReturn("case-1").when(c1).getId();
        doReturn("1168051000").when(c1).getRegionCode();
        doReturn("요약").when(c1).getSummary();
        doReturn("상세 설명").when(c1).getDescription();
        doReturn("태그1").when(c1).getTag1();
        doReturn("태그2").when(c1).getTag2();
        doReturn("태그3").when(c1).getTag3();
        doReturn("태그4").when(c1).getTag4();
        doReturn((short) 2019).when(c1).getStartYear();
        doReturn((short) 2022).when(c1).getEndYear();

        ReportSimilarCase c2 = mock(ReportSimilarCase.class, withSettings().lenient());
        doReturn("case-2").when(c2).getId();
        doReturn("1168051000").when(c2).getRegionCode();
        doReturn("요약").when(c2).getSummary();
        doReturn("상세 설명").when(c2).getDescription();
        doReturn("태그1").when(c2).getTag1();
        doReturn("태그2").when(c2).getTag2();
        doReturn("태그3").when(c2).getTag3();
        doReturn("태그4").when(c2).getTag4();
        doReturn((short) 2019).when(c2).getStartYear();
        doReturn((short) 2022).when(c2).getEndYear();

        ReportSimilarCase c3 = mock(ReportSimilarCase.class, withSettings().lenient());
        doReturn("case-3").when(c3).getId();
        doReturn("1168051000").when(c3).getRegionCode();
        doReturn("요약").when(c3).getSummary();
        doReturn("상세 설명").when(c3).getDescription();
        doReturn("태그1").when(c3).getTag1();
        doReturn("태그2").when(c3).getTag2();
        doReturn("태그3").when(c3).getTag3();
        doReturn("태그4").when(c3).getTag4();
        doReturn((short) 2019).when(c3).getStartYear();
        doReturn((short) 2022).when(c3).getEndYear();

        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of(c1, c2, c3));

        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("신사동");
        when(regionRepository.findById("1168051000")).thenReturn(Optional.of(region));

        ReportCaseListResponse response = service.getCases(1L, 1L, 1, 1);

        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().get(0).caseId()).isEqualTo("case-2");
    }

    @Test
    void getCases_withOtherUsersReport_throwsNotFound() {
        Report report = mock(Report.class, withSettings().lenient());
        doReturn(1L).when(report).getReportId();
        doReturn(2L).when(report).getUserId();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getCases(1L, 1L, 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getCases_withUnknownReportId_throwsNotFound() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCases(1L, 99L, 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getCases_withNegativeOffset_throwsBadRequest() {
        assertThatThrownBy(() -> service.getCases(1L, 1L, -1, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void getCases_withNegativeLimit_throwsBadRequest() {
        assertThatThrownBy(() -> service.getCases(1L, 1L, 0, -1))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void getCases_withNullStartAndEndYear_returnsNullPeriodFields() {
        Report report = mock(Report.class, withSettings().lenient());
        doReturn(1L).when(report).getReportId();
        doReturn(1L).when(report).getUserId();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        ReportSimilarCase c = mock(ReportSimilarCase.class, withSettings().lenient());
        doReturn("case-1").when(c).getId();
        doReturn("1168051000").when(c).getRegionCode();
        doReturn("요약").when(c).getSummary();
        doReturn("상세 설명").when(c).getDescription();
        doReturn("태그1").when(c).getTag1();
        doReturn("태그2").when(c).getTag2();
        doReturn("태그3").when(c).getTag3();
        doReturn("태그4").when(c).getTag4();
        doReturn(null).when(c).getStartYear();
        doReturn(null).when(c).getEndYear();

        when(reportSimilarCaseRepository.findByReportId(1L)).thenReturn(List.of(c));

        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("신사동");
        when(regionRepository.findById("1168051000")).thenReturn(Optional.of(region));

        ReportCaseListResponse response = service.getCases(1L, 1L, 0, 20);

        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().get(0).period().startYear()).isNull();
        assertThat(response.cases().get(0).period().endYear()).isNull();
    }
}
