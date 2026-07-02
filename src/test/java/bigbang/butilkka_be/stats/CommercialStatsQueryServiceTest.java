package bigbang.butilkka_be.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialStatsQueryServiceTest {

    @Mock
    private CommercialStatsRepository commercialStatsRepository;

    private CommercialStatsQueryService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new CommercialStatsQueryService(commercialStatsRepository);
    }

    private static CommercialStats statsOf(String regionCode, int year, int quarter) {
        CommercialStats stats = mock(CommercialStats.class);
        when(stats.getRegionCode()).thenReturn(regionCode);
        when(stats.getYear()).thenReturn(year);
        when(stats.getQuarter()).thenReturn(quarter);
        return stats;
    }

    @Test
    void latestPerRegion_picksMaxYearQuarterPerRegion() {
        CommercialStats a1 = statsOf("A", 2026, 1);
        CommercialStats a2 = statsOf("A", 2026, 4);
        CommercialStats b1 = mock(CommercialStats.class);
        when(b1.getRegionCode()).thenReturn("B");
        when(commercialStatsRepository.findAll()).thenReturn(List.of(a1, a2, b1));

        List<CommercialStats> result = service.latestPerRegion();

        assertThat(result).containsExactlyInAnyOrder(a2, b1);
    }

    @Test
    void latestForRegion_returnsMostRecentRow() {
        CommercialStats older = mock(CommercialStats.class);
        CommercialStats newer = mock(CommercialStats.class);
        when(commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc("A"))
                .thenReturn(List.of(older, newer));

        Optional<CommercialStats> result = service.latestForRegion("A");

        assertThat(result).contains(newer);
    }

    @Test
    void latestForRegion_withNoData_returnsEmpty() {
        when(commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc("A"))
                .thenReturn(List.of());

        assertThat(service.latestForRegion("A")).isEmpty();
    }

    @Test
    void historyForRegion_delegatesToRepository() {
        List<CommercialStats> expected = List.of(mock(CommercialStats.class));
        when(commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc("A")).thenReturn(expected);

        assertThat(service.historyForRegion("A")).isEqualTo(expected);
    }

    @Test
    void parseQuarterLabel_withValidLabel_returnsYearAndQuarter() {
        Optional<int[]> result = CommercialStatsQueryService.parseQuarterLabel("2026Q1");

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(2026, 1);
    }

    @Test
    void parseQuarterLabel_withInvalidLabel_returnsEmpty() {
        assertThat(CommercialStatsQueryService.parseQuarterLabel("invalid")).isEmpty();
        assertThat(CommercialStatsQueryService.parseQuarterLabel(null)).isEmpty();
    }
}
