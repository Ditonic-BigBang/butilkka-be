package bigbang.butilkka_be.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "report_decision_reasons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportDecisionReasons {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "reason_1", length = 100)
    private String reason1;

    @Column(name = "reason_2", length = 100)
    private String reason2;

    @Column(name = "reason_3", length = 100)
    private String reason3;

    public static ReportDecisionReasons create(Long reportId, String reason1, String reason2, String reason3) {
        ReportDecisionReasons r = new ReportDecisionReasons();
        r.id = UUID.randomUUID().toString();
        r.reportId = reportId;
        r.reason1 = reason1;
        r.reason2 = reason2;
        r.reason3 = reason3;
        return r;
    }
}
