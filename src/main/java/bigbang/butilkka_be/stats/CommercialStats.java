package bigbang.butilkka_be.stats;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "commercial_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stat_id")
    private Long statId;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(name = "category_code", length = 30)
    private String categoryCode;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer quarter;

    @Column(name = "foot_traffic")
    private Integer footTraffic;

    @Column(name = "foot_traffic_delta", precision = 5, scale = 2)
    private BigDecimal footTrafficDelta;

    @Column(name = "foot_traffic_gap")
    private Long footTrafficGap;

    @Column(name = "top_age_group", length = 10)
    private String topAgeGroup;

    @Column(name = "top_gender", length = 1)
    private String topGender;

    @Column(name = "store_count")
    private Integer storeCount;

    @Column(name = "store_count_delta", precision = 5, scale = 2)
    private BigDecimal storeCountDelta;

    @Column(name = "store_count_gap")
    private Long storeCountGap;

    @Column(name = "sales_amount")
    private Long salesAmount;

    @Column(name = "sales_delta", precision = 5, scale = 2)
    private BigDecimal salesDelta;

    @Column(name = "sales_gap")
    private Long salesGap;

    @Column(name = "rent_amount")
    private Long rentAmount;

    @Column(name = "rent_delta", precision = 5, scale = 2)
    private BigDecimal rentDelta;

    @Column(name = "rent_gap")
    private Long rentGap;

    @Column(name = "closure_rate", precision = 5, scale = 2)
    private BigDecimal closureRate;

    @Column(name = "closure_rate_delta", precision = 5, scale = 2)
    private BigDecimal closureRateDelta;

    @Column(name = "closure_rate_gap")
    private Long closureRateGap;

    @Column(name = "vacancy_rate", precision = 5, scale = 2)
    private BigDecimal vacancyRate;

    @Column(name = "vacancy_rate_delta", precision = 5, scale = 2)
    private BigDecimal vacancyRateDelta;

    @Column(name = "vacancy_rate_gap")
    private Long vacancyRateGap;

    @Column(name = "avg_business_period", precision = 5, scale = 2)
    private BigDecimal avgBusinessPeriod;

    @Column(name = "decline_grade", length = 1)
    private String declineGrade;

    @Column(length = 255)
    private String briefing;
}
