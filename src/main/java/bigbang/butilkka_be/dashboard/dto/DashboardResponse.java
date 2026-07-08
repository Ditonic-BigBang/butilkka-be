package bigbang.butilkka_be.dashboard.dto;

import java.util.List;

public record DashboardResponse(
        StoreInfo store,
        Grade grade,
        String briefing,
        Metrics metrics
) {
    public record StoreInfo(String regionCode, String regionName, String categoryName, String district) {}

    public record Grade(String current, String previous, int gaugeValue) {}

    public record Metrics(MetricTrend footTraffic, MetricTrend storeCount, MetricTrend closureRate) {}

    public record MetricTrend(String direction, Double delta, Long gap, String gapText, List<Point> points) {}

    public record Point(String quarter, Double value) {}
}
