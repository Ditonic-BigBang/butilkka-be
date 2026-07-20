package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.report.dto.ReportHistoryResponse;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportHistoryService {

    private final ReportRepository reportRepository;
    private final DistrictRepository districtRepository;
    private final UserRepository userRepository;

    public ReportHistoryResponse getHistory(Long userId, int offset, int limit) {
        if (offset < 0 || limit < 0) {
            throw AppException.badRequest("offset과 limit은 0 이상이어야 합니다.");
        }

        // 사용자의 현재 지역(구코드) 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다."));
        String currentDistrictCode = user.getStoreRegion() != null && user.getStoreRegion().length() >= 5
                ? user.getStoreRegion().substring(0, 5)
                : null;

        // 현재 지역의 리포트만 필터링
        List<Report> sorted = reportRepository.findByUserId(userId).stream()
                .filter(r -> currentDistrictCode != null && currentDistrictCode.equals(r.getRegionCode()))
                .sorted(Comparator.comparingInt(Report::getYear).thenComparingInt(Report::getQuarter).reversed())
                .toList();

        // regionCode -> regionName 매핑
        Set<String> regionCodes = sorted.stream().map(Report::getRegionCode).collect(Collectors.toSet());
        Map<String, String> regionNameMap = districtRepository.findAllById(regionCodes).stream()
                .collect(Collectors.toMap(District::getDistrictCode, District::getDistrictName));

        List<ReportHistoryResponse.ReportHistoryItem> page = sorted.stream()
                .skip(offset)
                .limit(limit)
                .map(r -> new ReportHistoryResponse.ReportHistoryItem(
                        r.getReportId(),
                        r.getYear() + "Q" + r.getQuarter(),
                        r.getGrade(),
                        r.getSummary(),
                        r.getRegionCode(),
                        regionNameMap.getOrDefault(r.getRegionCode(), "알 수 없음")))
                .toList();

        boolean hasNext = offset + page.size() < sorted.size();

        return new ReportHistoryResponse(sorted.size(), hasNext, page);
    }
}
