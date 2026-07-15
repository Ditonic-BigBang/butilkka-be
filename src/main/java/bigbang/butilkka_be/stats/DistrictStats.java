package bigbang.butilkka_be.stats;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "district_stats")
@Getter
@Setter
@NoArgsConstructor
public class DistrictStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "district_code", nullable = false, length = 10)
    private String districtCode;

    @Column(name = "district_name", length = 20)
    private String districtName;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer quarter;

    @Column(nullable = false, columnDefinition = "SMALLINT")
    private Integer year;

    @Column(name = "foot_traffic")
    private Long footTraffic;

    @Column(name = "foot_traffic_delta", precision = 12, scale = 6)
    private BigDecimal footTrafficDelta;

    @Column(name = "foot_traffic_gap")
    private Long footTrafficGap;

    @Column(name = "sales_amount")
    private Long salesAmount;

    @Column(name = "sales_delta", precision = 12, scale = 6)
    private BigDecimal salesDelta;

    @Column(name = "sales_gap")
    private Long salesGap;

    @Column(name = "store_count")
    private Integer storeCount;

    @Column(name = "store_count_delta", precision = 12, scale = 6)
    private BigDecimal storeCountDelta;

    @Column(name = "store_count_gap")
    private Long storeCountGap;

    @Column(name = "closure_rate", precision = 12, scale = 6)
    private BigDecimal closureRate;

    @Column(name = "closure_rate_delta", precision = 12, scale = 6)
    private BigDecimal closureRateDelta;

    @Column(name = "closure_rate_gap")
    private Long closureRateGap;

    @Column(name = "rent_amount", precision = 12, scale = 2)
    private BigDecimal rentAmount;

    @Column(name = "rent_delta", precision = 12, scale = 6)
    private BigDecimal rentDelta;

    @Column(name = "rent_gap", precision = 12, scale = 2)
    private BigDecimal rentGap;

    @Column(name = "vacancy_rate", precision = 12, scale = 6)
    private BigDecimal vacancyRate;

    @Column(name = "vacancy_rate_delta", precision = 12, scale = 6)
    private BigDecimal vacancyRateDelta;

    @Column(name = "vacancy_rate_gap", precision = 12, scale = 2)
    private BigDecimal vacancyRateGap;

    @Column(name = "decline_grade", length = 1)
    private String declineGrade;

    @Column(name = "direction", length = 10)
    private String direction;

    @Column(name = "composite_score", precision = 12, scale = 6)
    private BigDecimal compositeScore;

    @Column(name = "avg_operating_years", precision = 5, scale = 2)
    private BigDecimal avgOperatingYears;

    @Builder
    public DistrictStats(String districtCode, String districtName, Integer quarter, Integer year,
                         Long footTraffic, BigDecimal footTrafficDelta, Long footTrafficGap,
                         Long salesAmount, BigDecimal salesDelta, Long salesGap,
                         Integer storeCount, BigDecimal storeCountDelta, Long storeCountGap,
                         BigDecimal closureRate, BigDecimal closureRateDelta, Long closureRateGap,
                         BigDecimal rentAmount, BigDecimal rentDelta, BigDecimal rentGap,
                         BigDecimal vacancyRate, BigDecimal vacancyRateDelta, BigDecimal vacancyRateGap,
                         String declineGrade, String direction, BigDecimal compositeScore,
                         BigDecimal avgOperatingYears) {
        this.districtCode = districtCode;
        this.districtName = districtName;
        this.quarter = quarter;
        this.year = year;
        this.footTraffic = footTraffic;
        this.footTrafficDelta = footTrafficDelta;
        this.footTrafficGap = footTrafficGap;
        this.salesAmount = salesAmount;
        this.salesDelta = salesDelta;
        this.salesGap = salesGap;
        this.storeCount = storeCount;
        this.storeCountDelta = storeCountDelta;
        this.storeCountGap = storeCountGap;
        this.closureRate = closureRate;
        this.closureRateDelta = closureRateDelta;
        this.closureRateGap = closureRateGap;
        this.rentAmount = rentAmount;
        this.rentDelta = rentDelta;
        this.rentGap = rentGap;
        this.vacancyRate = vacancyRate;
        this.vacancyRateDelta = vacancyRateDelta;
        this.vacancyRateGap = vacancyRateGap;
        this.declineGrade = declineGrade;
        this.direction = direction;
        this.compositeScore = compositeScore;
        this.avgOperatingYears = avgOperatingYears;
    }
}
