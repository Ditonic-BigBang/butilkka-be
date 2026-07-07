package bigbang.butilkka_be.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataLoadService {

    private final CommercialStatsRepository statsRepository;

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
        // 각 CSV를 regionCode+quarter 기준으로 Map으로 읽어서 merge
        var salesMap    = readSales();
        var flpopMap    = readFlpop();
        var storeMap    = readStore();
        var rentMap     = readRent();
        var vacancyMap  = readVacancy();

        List<CommercialStats> result = new ArrayList<>();

        for (var entry : salesMap.entrySet()) {
            String key = entry.getKey(); // "regionCode|quarter"
            CommercialStats stats = entry.getValue();

            // flpop 병합
            CommercialStats flpop = flpopMap.get(key);
            if (flpop != null) {
                stats.setFootTraffic(flpop.getFootTraffic());
                stats.setFootTrafficDelta(flpop.getFootTrafficDelta());
                stats.setFootTrafficGap(flpop.getFootTrafficGap());
                stats.setTopAgeGroup(flpop.getTopAgeGroup());
                stats.setTopGender(flpop.getTopGender());
            }

            // store 병합
            CommercialStats store = storeMap.get(key);
            if (store != null) {
                stats.setStoreCount(store.getStoreCount());
                stats.setStoreCountDelta(store.getStoreCountDelta());
                stats.setStoreCountGap(store.getStoreCountGap());
                stats.setClosureRate(store.getClosureRate());
                stats.setClosureRateDelta(store.getClosureRateDelta());
                stats.setClosureRateGap(store.getClosureRateGap());
            }

            // rent 병합
            CommercialStats rent = rentMap.get(key);
            if (rent != null) {
                stats.setRentAmount(rent.getRentAmount());
                stats.setRentDelta(rent.getRentDelta());
                stats.setRentGap(rent.getRentGap());
            }

            // vacancy 병합
            CommercialStats vacancy = vacancyMap.get(key);
            if (vacancy != null) {
                stats.setVacancyRate(vacancy.getVacancyRate());
                stats.setVacancyRateDelta(vacancy.getVacancyRateDelta());
                stats.setVacancyRateGap(vacancy.getVacancyRateGap());
            }

            result.add(stats);
        }

        return result;
    }

    // ──────────────────────────────────────────
    // CSV 파서 (resources/data/ 아래 파일)
    // ──────────────────────────────────────────

    private java.util.Map<String, CommercialStats> readSales() {
        var map = new java.util.HashMap<String, CommercialStats>();
        try (Reader reader = new InputStreamReader(
                new ClassPathResource("data/sales_by_dong.csv").getInputStream(), "UTF-8");
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            for (CSVRecord r : parser) {
                String regionCode = r.get("행정동코드") + "00"; // 8자리 → 10자리
                String quarter    = r.get("분기코드");          // 2023Q1
                String key        = regionCode + "|" + quarter;

                CommercialStats stats = new CommercialStats();
                stats.setRegionCode(regionCode);
                stats.setQuarter(parseQuarter(quarter));
                stats.setYear(parseYear(quarter));
                stats.setSalesAmount(parseLong(r.get("매출금액")));
                stats.setSalesDelta(parseDecimal(r.get("매출_QoQ")));
                stats.setSalesGap(parseLong(r.get("매출_gap")));

                map.put(key, stats);
            }
        } catch (Exception e) {
            log.error("sales CSV 읽기 실패", e);
        }
        return map;
    }

    private java.util.Map<String, CommercialStats> readFlpop() {
        var map = new java.util.HashMap<String, CommercialStats>();
        try (Reader reader = new InputStreamReader(
                new ClassPathResource("data/flpop_by_dong.csv").getInputStream(), "UTF-8");
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            for (CSVRecord r : parser) {
                String regionCode = r.get("행정동코드") + "00";
                String quarter    = r.get("분기코드");
                String key        = regionCode + "|" + quarter;

                CommercialStats stats = new CommercialStats();
                stats.setFootTraffic(parseInt(r.get("총유동인구")));
                stats.setFootTrafficDelta(parseDecimal(r.get("유동인구_delta")));
                stats.setFootTrafficGap(parseLong(r.get("유동인구_gap")));
                stats.setTopAgeGroup(r.get("최다연령대"));
                stats.setTopGender(r.get("최다성별"));

                map.put(key, stats);
            }
        } catch (Exception e) {
            log.error("flpop CSV 읽기 실패", e);
        }
        return map;
    }

    private java.util.Map<String, CommercialStats> readStore() {
        var map = new java.util.HashMap<String, CommercialStats>();
        try (Reader reader = new InputStreamReader(
                new ClassPathResource("data/store_by_dong.csv").getInputStream(), "UTF-8");
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            for (CSVRecord r : parser) {
                // ALL(전체)만 사용
                String category = r.get("카테고리");
                if (!"전체".equals(category)) continue;

                String regionCode = r.get("행정동코드") + "00";
                String quarter    = r.get("분기코드");
                String key        = regionCode + "|" + quarter;

                CommercialStats stats = new CommercialStats();
                stats.setStoreCount(parseInt(r.get("점포수")));
                stats.setStoreCountDelta(parseDecimal(r.get("점포수_증감률")));
                stats.setStoreCountGap(parseLong(r.get("점포수_gap")));
                stats.setClosureRate(parseDecimal(r.get("폐업률")));
                stats.setClosureRateDelta(parseDecimal(r.get("폐업률_delta")));
                stats.setClosureRateGap(parseLong(r.get("폐업률_gap")));

                map.put(key, stats);
            }
        } catch (Exception e) {
            log.error("store CSV 읽기 실패", e);
        }
        return map;
    }

    private java.util.Map<String, CommercialStats> readRent() {
        var map = new java.util.HashMap<String, CommercialStats>();
        try (Reader reader = new InputStreamReader(
                new ClassPathResource("data/rent_by_dong_full.csv").getInputStream(), "UTF-8");
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            for (CSVRecord r : parser) {
                String regionCode = r.get("행정동코드") + "00";
                String quarter    = r.get("분기코드");
                String key        = regionCode + "|" + quarter;

                CommercialStats stats = new CommercialStats();
                stats.setRentAmount(parseLong(r.get("임대료")));
                stats.setRentDelta(parseDecimal(r.get("임대료_delta")));
                stats.setRentGap(parseLong(r.get("임대료_gap")));

                map.put(key, stats);
            }
        } catch (Exception e) {
            log.error("rent CSV 읽기 실패", e);
        }
        return map;
    }

    private java.util.Map<String, CommercialStats> readVacancy() {
        var map = new java.util.HashMap<String, CommercialStats>();
        try (Reader reader = new InputStreamReader(
                new ClassPathResource("data/vacancy_by_dong_full.csv").getInputStream(), "UTF-8");
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            for (CSVRecord r : parser) {
                String regionCode = r.get("행정동코드") + "00";
                String quarter    = r.get("분기코드");
                String key        = regionCode + "|" + quarter;

                CommercialStats stats = new CommercialStats();
                stats.setVacancyRate(parseDecimal(r.get("공실률")));
                stats.setVacancyRateDelta(parseDecimal(r.get("공실률_delta")));
                stats.setVacancyRateGap(parseLong(r.get("공실률_gap")));

                map.put(key, stats);
            }
        } catch (Exception e) {
            log.error("vacancy CSV 읽기 실패", e);
        }
        return map;
    }

    // ──────────────────────────────────────────
    // 파싱 유틸
    // ──────────────────────────────────────────

    private int parseQuarter(String q) {
        // "2023Q1" → 1
        return Integer.parseInt(q.substring(5));
    }

    private int parseYear(String q) {
        // "2023Q1" → 2023
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
}
