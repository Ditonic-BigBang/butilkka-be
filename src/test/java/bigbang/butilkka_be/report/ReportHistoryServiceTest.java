package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.report.dto.ReportHistoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportHistoryServiceTest {

    @Mock
    private ReportRepository reportRepository;

    private ReportHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ReportHistoryService(reportRepository);
    }

    private static Report reportOf(Long reportId, int year, int quarter, String grade, String summary) {
        Report report = mock(Report.class);
        when(report.getReportId()).thenReturn(reportId);
        when(report.getYear()).thenReturn(year);
        when(report.getQuarter()).thenReturn(quarter);
        when(report.getGrade()).thenReturn(grade);
        when(report.getSummary()).thenReturn(summary);
        return report;
    }

    @Test
    void getHistory_sortsNewestFirst() {
        Report q1 = reportOf(5L, 2026, 1, "B", "1분기 요약");
        Report q4 = reportOf(1L, 2026, 4, "A", "4분기 요약");
        Report q2 = reportOf(6L, 2026, 2, "A", "2분기 요약");
        when(reportRepository.findByUserId(1L)).thenReturn(List.of(q1, q4, q2));

        ReportHistoryResponse response = service.getHistory(1L, 0, 20);

        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.reports()).hasSize(3);
        assertThat(response.reports().get(0).reportId()).isEqualTo(1L);
        assertThat(response.reports().get(0).quarter()).isEqualTo("2026Q4");
        assertThat(response.reports().get(2).reportId()).isEqualTo(5L);
    }

    @Test
    void getHistory_appliesOffsetAndLimit() {
        // q1 and q4 are sorted but skipped by pagination, so only need year/quarter for sorting
        Report q1 = mock(Report.class);
        when(q1.getYear()).thenReturn(2026);
        when(q1.getQuarter()).thenReturn(1);

        Report q4 = mock(Report.class);
        when(q4.getYear()).thenReturn(2026);
        when(q4.getQuarter()).thenReturn(4);

        // q2 and q3 are in the result, so need full stubs
        Report q2 = reportOf(6L, 2026, 2, "A", "2분기 요약");
        Report q3 = reportOf(7L, 2026, 3, "C", "3분기 요약");
        when(reportRepository.findByUserId(1L)).thenReturn(List.of(q1, q2, q3, q4));

        ReportHistoryResponse response = service.getHistory(1L, 1, 2);

        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.reports()).hasSize(2);
        assertThat(response.reports().get(0).reportId()).isEqualTo(7L);
        assertThat(response.reports().get(1).reportId()).isEqualTo(6L);
    }

    @Test
    void getHistory_withNoReports_returnsEmptyList() {
        when(reportRepository.findByUserId(1L)).thenReturn(List.of());

        ReportHistoryResponse response = service.getHistory(1L, 0, 20);

        assertThat(response.totalCount()).isEqualTo(0);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.reports()).isEmpty();
    }

    @Test
    void getHistory_withNegativeOffset_throwsBadRequest() {
        assertThatThrownBy(() -> service.getHistory(1L, -1, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void getHistory_withNegativeLimit_throwsBadRequest() {
        assertThatThrownBy(() -> service.getHistory(1L, 0, -1))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
