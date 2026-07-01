package bigbang.butilkka_be.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(name = "category_code", length = 30)
    private String categoryCode;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer quarter;

    @Column(nullable = false, length = 1)
    private String grade;

    @Column(name = "decline_type", length = 20)
    private String declineType;

    @Column(length = 255)
    private String summary;

    @Column(name = "ai_outlook", columnDefinition = "TEXT")
    private String aiOutlook;

    @Column(name = "decision_recommendation", length = 10)
    private String decisionRecommendation;

    @Column(name = "decision_title", length = 100)
    private String decisionTitle;

    @Column(name = "decision_description", columnDefinition = "TEXT")
    private String decisionDescription;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static Report create(Long userId, String regionCode, String categoryCode, Integer quarter, String grade) {
        Report report = new Report();
        report.userId = userId;
        report.regionCode = regionCode;
        report.categoryCode = categoryCode;
        report.quarter = quarter;
        report.grade = grade;
        report.createdAt = LocalDateTime.now();
        return report;
    }
}
