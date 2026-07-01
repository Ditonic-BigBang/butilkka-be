package bigbang.butilkka_be.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "report_signal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "signal_id")
    private Long signalId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 255)
    private String description;
}
