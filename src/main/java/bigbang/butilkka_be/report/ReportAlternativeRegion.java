package bigbang.butilkka_be.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "report_alternative_regions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportAlternativeRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(name = "rank_order", nullable = false)
    private Integer rankOrder;

    @Column(name = "ai_message", columnDefinition = "TEXT")
    private String aiMessage;

    public static ReportAlternativeRegion create(Long reportId, String regionCode, int rankOrder, String aiMessage) {
        ReportAlternativeRegion r = new ReportAlternativeRegion();
        r.reportId = reportId;
        r.regionCode = regionCode;
        r.rankOrder = rankOrder;
        r.aiMessage = aiMessage;
        return r;
    }
}
