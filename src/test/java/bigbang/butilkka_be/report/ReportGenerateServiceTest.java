package bigbang.butilkka_be.report;

import bigbang.butilkka_be.report.dto.ReportGenerateRequest;
import bigbang.butilkka_be.report.dto.ReportGenerateResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGenerateServiceTest {

    @Mock
    private AiServerClient aiServerClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DistrictStatsQueryService districtStatsQueryService;
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
    private ReportDecisionReasonsRepository reportDecisionReasonsRepository;

    @Test
    void generateAndSave_whenAiResponseOmitsOptionalLists_doesNotThrowNpe() {
        ReportGenerateService service = new ReportGenerateService(
                aiServerClient, userRepository, districtStatsQueryService, reportRepository,
                reportCauseRepository, reportSignalRepository, reportSimilarCaseRepository,
                reportAlternativeRegionRepository, reportDecisionReasonsRepository);

        User user = mock(User.class);
        lenient().when(user.getStoreRegion()).thenReturn("1168064000");
        lenient().when(user.getCategoryCode()).thenReturn("CS100001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        DistrictStats stats = mock(DistrictStats.class);
        lenient().when(stats.getDeclineGrade()).thenReturn("B");
        lenient().when(stats.getDirection()).thenReturn("정체");
        lenient().when(stats.getDistrictName()).thenReturn("강남구");
        when(districtStatsQueryService.historyForDistrict("11680")).thenReturn(List.of(stats));

        // Simulates the AI server returning a response body that omits the
        // optional causes/signals/similarCases/alternativeRegions fields
        // (Jackson deserializes missing record components to null).
        ReportGenerateResponse aiResponse = new ReportGenerateResponse(
                "summary", "outlook", "버티기", "title", "description",
                null, null, null, null, null, null, null);
        when(aiServerClient.generateReport(org.mockito.ArgumentMatchers.any(ReportGenerateRequest.class)))
                .thenReturn(aiResponse);

        Report saved = mock(Report.class);
        lenient().when(reportRepository.save(org.mockito.ArgumentMatchers.any(Report.class)))
                .thenReturn(saved);

        service.generateAndSave(1L);
    }
}
