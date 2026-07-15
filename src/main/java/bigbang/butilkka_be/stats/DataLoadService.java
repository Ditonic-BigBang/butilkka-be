package bigbang.butilkka_be.stats;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataLoadService {

    private final CommercialStatsRepository statsRepository;
    private final DistrictStatsRepository districtStatsRepository;

    @Value("${app.data-load.enabled:true}")
    private boolean dataLoadEnabled;

    // ──────────────────────────────────────────
    // 중간 데이터 record
    // ──────────────────────────────────────────
    private record SalesData(String regionCode, Integer quarter, Integer year,
                             Long salesAmount, BigDecimal salesDelta, Long salesGap) {}

    private record FlpopData(Integer footTraffic, BigDecimal footTrafficDelta, Long footTrafficGap,
                             String topAgeGroup, String topGender) {}

    private record StoreData(Integer storeCount, BigDecimal storeCountDelta, Long storeCountGap,
                             BigDecimal closureRate, BigDecimal closureRateDelta, Long closureRateGap) {}

    private record RentData(Long rentAmount, BigDecimal rentDelta, Long rentGap) {}

    private record VacancyData(BigDecimal vacancyRate, BigDecimal vacancyRateDelta, Long vacancyRateGap) {}

    private record GradeData(String declineGrade) {}

    @PostConstruct
    public void init() {
        if (!dataLoadEnabled) {
            log.info("CSV 데이터 적재 비활성화됨");
            return;
        }
        // loadAll(); // 행정동 기반 → 구 기반으로 전환되어 비활성화
        loadAllDistrictStats();
    }

    /**
     * 전체 CSV 적재
     * 이미 데이터가 있으면 skip
     */
    @Transactional
    public void loadAll() {
        if (statsRepository.count() > 0) {
            log.info("데이터 이미 존재 → skip");
            return;
        }

        log.info("CSV 데이터 적재 시작");
        List<CommercialStats> statsList = buildStats();
        statsRepository.saveAll(statsList);
        log.info("CSV 데이터 적재 완료: {}건", statsList.size());
    }

    private List<CommercialStats> buildStats() {
        var salesMap = readSales();
        var flpopMap = readFlpop();
        var storeMap = readStore();
        var rentMap = readRent();
        var vacancyMap = readVacancy();
        var gradeMap = readGrade();

        List<CommercialStats> result = new ArrayList<>();

        for (var entry : salesMap.entrySet()) {
            String key = entry.getKey();
            SalesData sales = entry.getValue();

            FlpopData flpop = flpopMap.get(key);
            StoreData store = storeMap.get(key);
            RentData rent = rentMap.get(key);
            VacancyData vacancy = vacancyMap.get(key);
            GradeData grade = gradeMap.get(sales.regionCode());

            CommercialStats stats = CommercialStats.builder()
                    .regionCode(sales.regionCode())
                    .quarter(sales.quarter())
                    .year(sales.year())
                    .salesAmount(sales.salesAmount())
                    .salesDelta(sales.salesDelta())
                    .salesGap(sales.salesGap())
                    .footTraffic(flpop != null ? flpop.footTraffic() : null)
                    .footTrafficDelta(flpop != null ? flpop.footTrafficDelta() : null)
                    .footTrafficGap(flpop != null ? flpop.footTrafficGap() : null)
                    .topAgeGroup(flpop != null ? flpop.topAgeGroup() : null)
                    .topGender(flpop != null ? flpop.topGender() : null)
                    .storeCount(store != null ? store.storeCount() : null)
                    .storeCountDelta(store != null ? store.storeCountDelta() : null)
                    .storeCountGap(store != null ? store.storeCountGap() : null)
                    .closureRate(store != null ? store.closureRate() : null)
                    .closureRateDelta(store != null ? store.closureRateDelta() : null)
                    .closureRateGap(store != null ? store.closureRateGap() : null)
                    .rentAmount(rent != null ? rent.rentAmount() : null)
                    .rentDelta(rent != null ? rent.rentDelta() : null)
                    .rentGap(rent != null ? rent.rentGap() : null)
                    .vacancyRate(vacancy != null ? vacancy.vacancyRate() : null)
                    .vacancyRateDelta(vacancy != null ? vacancy.vacancyRateDelta() : null)
                    .vacancyRateGap(vacancy != null ? vacancy.vacancyRateGap() : null)
                    .declineGrade(grade != null ? grade.declineGrade() : null)
                    .build();

            result.add(stats);
        }

        return result;
    }

    // ──────────────────────────────────────────
    // CSV 파서 (resources/data/ 아래 파일)
    // ──────────────────────────────────────────

    private Reader createBomFreeReader(String path) throws Exception {
        var is = new ClassPathResource(path).getInputStream();
        var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        reader.mark(1);
        int firstChar = reader.read();
        if (firstChar != '\uFEFF') {
            reader.reset();
        }
        return reader;
    }

    private Map<String, SalesData> readSales() {
        var map = new HashMap<String, SalesData>();
        try (Reader reader = createBomFreeReader("data/sales_by_dong.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String category = r.get("카테고리");
                if (!"전체".equals(category)) continue;

                String regionCode = r.get("행정동코드") + "00";
                String quarter = r.get("분기코드");
                String key = regionCode + "|" + quarter;

                map.put(key, new SalesData(
                        regionCode,
                        parseQuarter(quarter),
                        parseYear(quarter),
                        parseLong(r.get("매출금액")),
                        parseDecimal(r.get("매출_QoQ")),
                        parseLong(r.get("매출_gap"))
                ));
            }
        } catch (Exception e) {
            log.error("sales CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, FlpopData> readFlpop() {
        var map = new HashMap<String, FlpopData>();
        try (Reader reader = createBomFreeReader("data/flpop_by_dong.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String regionCode = r.get("행정동코드") + "00";
                String quarter = r.get("분기코드");
                String key = regionCode + "|" + quarter;

                map.put(key, new FlpopData(
                        parseInt(r.get("총유동인구")),
                        parseDecimal(r.get("유동인구_delta")),
                        parseLong(r.get("유동인구_gap")),
                        r.get("최다연령대"),
                        r.get("최다성별")
                ));
            }
        } catch (Exception e) {
            log.error("flpop CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, StoreData> readStore() {
        var map = new HashMap<String, StoreData>();
        try (Reader reader = createBomFreeReader("data/store_by_dong.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String category = r.get("카테고리");
                if (!"전체".equals(category)) continue;

                String regionCode = r.get("행정동코드") + "00";
                String quarter = r.get("분기코드");
                String key = regionCode + "|" + quarter;

                map.put(key, new StoreData(
                        parseInt(r.get("점포수")),
                        parseDecimal(r.get("점포수_증감률")),
                        parseLong(r.get("점포수_gap")),
                        parseDecimal(r.get("폐업률")),
                        parseDecimal(r.get("폐업률_delta")),
                        parseLong(r.get("폐업률_gap"))
                ));
            }
        } catch (Exception e) {
            log.error("store CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, RentData> readRent() {
        var map = new HashMap<String, RentData>();
        try (Reader reader = createBomFreeReader("data/rent_by_dong_full.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String regionCode = r.get("행정동코드") + "00";
                String quarter = r.get("분기코드");
                String key = regionCode + "|" + quarter;

                map.put(key, new RentData(
                        parseLong(r.get("임대료")),
                        parseDecimal(r.get("임대료_delta")),
                        parseLong(r.get("임대료_gap"))
                ));
            }
        } catch (Exception e) {
            log.error("rent CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, VacancyData> readVacancy() {
        var map = new HashMap<String, VacancyData>();
        try (Reader reader = createBomFreeReader("data/vacancy_by_dong_full.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String regionCode = r.get("행정동코드") + "00";
                String quarter = r.get("분기코드");
                String key = regionCode + "|" + quarter;

                map.put(key, new VacancyData(
                        parseDecimal(r.get("공실률")),
                        parseDecimal(r.get("공실률_delta")),
                        parseLong(r.get("공실률_gap"))
                ));
            }
        } catch (Exception e) {
            log.error("vacancy CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, GradeData> readGrade() {
        var map = new HashMap<String, GradeData>();
        try (Reader reader = createBomFreeReader("data/final_grades.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String regionCode = r.get("행정동코드") + "00";
                String grade = r.get("등급");

                map.put(regionCode, new GradeData(grade));
            }
        } catch (Exception e) {
            log.error("grade CSV 읽기 실패", e);
        }
        return map;
    }

    // ──────────────────────────────────────────
    // 파싱 유틸
    // ──────────────────────────────────────────

    private int parseQuarter(String q) {
        return Integer.parseInt(q.substring(5));
    }

    private int parseYear(String q) {
        return Integer.parseInt(q.substring(0, 4));
    }

    private Long parseLong(String s) {
        try { return s == null || s.isBlank() ? null : (long) Double.parseDouble(s); }
        catch (Exception e) { return null; }
    }

    private Integer parseInt(String s) {
        try { return s == null || s.isBlank() ? null : (int) Double.parseDouble(s); }
        catch (Exception e) { return null; }
    }

    private BigDecimal parseDecimal(String s) {
        try { return s == null || s.isBlank() ? null : new BigDecimal(s); }
        catch (Exception e) { return null; }
    }

    // ──────────────────────────────────────────
    // 구별 데이터 중간 record
    // ──────────────────────────────────────────
    private record GuGradeData(Integer rank) {}  // gu_grade.csv - 최신 순위만
    private record GuGradeTimeseriesData(String grade, String direction, BigDecimal compositeScore) {}  // gu_grade_timeseries.csv - 분기별
    private record GuSalesData(String districtCode, String districtName, Integer year, Integer quarter,
                               Long salesAmount, BigDecimal salesDelta, Long salesGap) {}
    private record GuFlpopData(Long footTraffic, BigDecimal footTrafficDelta, Long footTrafficGap) {}
    private record GuStoreData(Integer storeCount, BigDecimal storeCountDelta, Long storeCountGap,
                               BigDecimal closureRate, BigDecimal closureRateDelta, Long closureRateGap) {}
    private record GuRentData(BigDecimal rentAmount, BigDecimal rentDelta, BigDecimal rentGap) {}
    private record GuVacancyData(BigDecimal vacancyRate, BigDecimal vacancyRateDelta, BigDecimal vacancyRateGap) {}

    @Transactional
    public void loadAllDistrictStats() {
        if (districtStatsRepository.count() > 0) {
            log.info("구별 데이터 이미 존재 → skip");
            return;
        }

        log.info("구별 CSV 데이터 적재 시작");
        List<DistrictStats> statsList = buildDistrictStats();
        districtStatsRepository.saveAll(statsList);
        log.info("구별 CSV 데이터 적재 완료: {}건", statsList.size());
    }

    private List<DistrictStats> buildDistrictStats() {
        var rankMap = readGuGrade();                    // 구코드 → 순위
        var gradeTimeseriesMap = readGuGradeTimeseries(); // 구코드|분기 → 등급/방향/점수
        var salesMap = readGuSales();
        var flpopMap = readGuFlpop();
        var storeMap = readGuStore();
        var rentMap = readGuRent();
        var vacancyMap = readGuVacancy();

        List<DistrictStats> result = new ArrayList<>();

        for (var entry : salesMap.entrySet()) {
            String key = entry.getKey();
            GuSalesData sales = entry.getValue();

            GuGradeData rankData = rankMap.get(sales.districtCode());
            GuGradeTimeseriesData gradeTs = gradeTimeseriesMap.get(key);
            GuFlpopData flpop = flpopMap.get(key);
            GuStoreData store = storeMap.get(key);
            GuRentData rent = rentMap.get(key);
            GuVacancyData vacancy = vacancyMap.get(key);

            DistrictStats stats = DistrictStats.builder()
                    .districtCode(sales.districtCode())
                    .districtName(sales.districtName())
                    .year(sales.year())
                    .quarter(sales.quarter())
                    .salesAmount(sales.salesAmount())
                    .salesDelta(sales.salesDelta())
                    .salesGap(sales.salesGap())
                    .footTraffic(flpop != null ? flpop.footTraffic() : null)
                    .footTrafficDelta(flpop != null ? flpop.footTrafficDelta() : null)
                    .footTrafficGap(flpop != null ? flpop.footTrafficGap() : null)
                    .storeCount(store != null ? store.storeCount() : null)
                    .storeCountDelta(store != null ? store.storeCountDelta() : null)
                    .storeCountGap(store != null ? store.storeCountGap() : null)
                    .closureRate(store != null ? store.closureRate() : null)
                    .closureRateDelta(store != null ? store.closureRateDelta() : null)
                    .closureRateGap(store != null ? store.closureRateGap() : null)
                    .rentAmount(rent != null ? rent.rentAmount() : null)
                    .rentDelta(rent != null ? rent.rentDelta() : null)
                    .rentGap(rent != null ? rent.rentGap() : null)
                    .vacancyRate(vacancy != null ? vacancy.vacancyRate() : null)
                    .vacancyRateDelta(vacancy != null ? vacancy.vacancyRateDelta() : null)
                    .vacancyRateGap(vacancy != null ? vacancy.vacancyRateGap() : null)
                    .declineGrade(gradeTs != null ? gradeTs.grade() : null)
                    .direction(gradeTs != null ? gradeTs.direction() : null)
                    .compositeScore(gradeTs != null ? gradeTs.compositeScore() : null)
                    .districtRank(rankData != null ? rankData.rank() : null)
                    .build();

            result.add(stats);
        }

        return result;
    }

    private Map<String, GuGradeData> readGuGrade() {
        var map = new HashMap<String, GuGradeData>();
        try (Reader reader = createBomFreeReader("data/gu_grade.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String districtCode = r.get("구코드");
                Integer rank = parseInt(r.get("구순위"));
                map.put(districtCode, new GuGradeData(rank));
            }
        } catch (Exception e) {
            log.error("gu_grade CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, GuGradeTimeseriesData> readGuGradeTimeseries() {
        var map = new HashMap<String, GuGradeTimeseriesData>();
        try (Reader reader = createBomFreeReader("data/gu_grade_timeseries.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String districtCode = r.get("구코드");
                String quarterStr = r.get("분기코드");
                String key = districtCode + "|" + quarterStr;

                String grade = r.get("구등급");
                String direction = r.get("구방향성");
                BigDecimal compositeScore = parseDecimal(r.get("구종합점수"));

                map.put(key, new GuGradeTimeseriesData(grade, direction, compositeScore));
            }
        } catch (Exception e) {
            log.error("gu_grade_timeseries CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, GuSalesData> readGuSales() {
        var map = new HashMap<String, GuSalesData>();
        try (Reader reader = createBomFreeReader("data/sales_by_gu.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String category = r.get("카테고리");
                if (!"분식전문점".equals(category)) continue;  // 기본 카테고리로 필터링

                String districtCode = r.get("구코드");
                String districtName = r.get("구명");
                String quarterStr = r.get("분기코드");
                String key = districtCode + "|" + quarterStr;

                map.put(key, new GuSalesData(
                        districtCode,
                        districtName,
                        parseYear(quarterStr),
                        parseQuarter(quarterStr),
                        parseLong(r.get("매출금액")),
                        parseDecimal(r.get("매출_QoQ")),
                        parseLong(r.get("매출_gap"))
                ));
            }
        } catch (Exception e) {
            log.error("sales_by_gu CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, GuFlpopData> readGuFlpop() {
        var map = new HashMap<String, GuFlpopData>();
        try (Reader reader = createBomFreeReader("data/flpop_by_gu.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String districtCode = r.get("구코드");
                String quarterStr = r.get("분기코드");
                String key = districtCode + "|" + quarterStr;

                map.put(key, new GuFlpopData(
                        parseLong(r.get("총유동인구")),
                        parseDecimal(r.get("유동인구_delta")),
                        parseLong(r.get("유동인구_gap"))
                ));
            }
        } catch (Exception e) {
            log.error("flpop_by_gu CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, GuStoreData> readGuStore() {
        var map = new HashMap<String, GuStoreData>();
        try (Reader reader = createBomFreeReader("data/store_by_gu.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String category = r.get("카테고리");
                if (!"분식전문점".equals(category)) continue;

                String districtCode = r.get("구코드");
                String quarterStr = r.get("분기코드");
                String key = districtCode + "|" + quarterStr;

                map.put(key, new GuStoreData(
                        parseInt(r.get("점포수")),
                        parseDecimal(r.get("점포수_증감률")),
                        parseLong(r.get("점포수_gap")),
                        parseDecimal(r.get("폐업률")),
                        parseDecimal(r.get("폐업률_delta")),
                        parseLong(r.get("폐업률_gap"))
                ));
            }
        } catch (Exception e) {
            log.error("store_by_gu CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, GuRentData> readGuRent() {
        var map = new HashMap<String, GuRentData>();
        try (Reader reader = createBomFreeReader("data/rent_by_gu_full.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String districtCode = r.get("구코드");
                String quarterStr = r.get("분기코드");
                String key = districtCode + "|" + quarterStr;

                map.put(key, new GuRentData(
                        parseDecimal(r.get("임대료")),
                        parseDecimal(r.get("임대료_delta")),
                        parseDecimal(r.get("임대료_gap"))
                ));
            }
        } catch (Exception e) {
            log.error("rent_by_gu_full CSV 읽기 실패", e);
        }
        return map;
    }

    private Map<String, GuVacancyData> readGuVacancy() {
        var map = new HashMap<String, GuVacancyData>();
        try (Reader reader = createBomFreeReader("data/vacancy_by_gu_full.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord r : parser) {
                String districtCode = r.get("구코드");
                String quarterStr = r.get("분기코드");
                String key = districtCode + "|" + quarterStr;

                map.put(key, new GuVacancyData(
                        parseDecimal(r.get("공실률")),
                        parseDecimal(r.get("공실률_delta")),
                        parseDecimal(r.get("공실률_gap"))
                ));
            }
        } catch (Exception e) {
            log.error("vacancy_by_gu_full CSV 읽기 실패", e);
        }
        return map;
    }
}
