package bigbang.butilkka_be.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "report_cause")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportCause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cause_id")
    private Long causeId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 10)
    private String level;

    @Column(nullable = false, length = 255)
    private String description;

    public static ReportCause create(Long reportId, String title, String level, String description) {
        ReportCause cause = new ReportCause();
        cause.reportId = reportId;
        cause.title = title;
        cause.level = level;
        cause.description = description;
        return cause;
    }
}
