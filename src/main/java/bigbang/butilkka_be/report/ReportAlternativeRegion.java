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

    @Column(length = 255)
    private String reason;

    @Column(nullable = false, length = 50)
    private String stat;
}
