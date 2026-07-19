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

    @Column(nullable = false, columnDefinition = "SMALLINT")
    private Integer year;

    @Column(nullable = false, length = 1)
    private String grade;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer score;

    @Column(name = "decline_type", length = 20)
    private String declineType;

    @Column(length = 255)
    private String summary;

    @Column(name = "ai_outlook", columnDefinition = "TEXT")
    private String aiOutlook;

    @Column(name = "predicted_trend", length = 10)
    private String predictedTrend;

    @Column(name = "predicted_next_grade", length = 1)
    private String predictedNextGrade;

    @Column(name = "decision_recommendation", length = 10)
    private String decisionRecommendation;

    @Column(name = "decision_title", length = 100)
    private String decisionTitle;

    @Column(name = "decision_description", columnDefinition = "TEXT")
    private String decisionDescription;

    @Column(name = "ai_rec_badge_type", length = 20)
    private String aiRecBadgeType;

    @Column(name = "ai_rec_title", length = 100)
    private String aiRecTitle;

    @Column(name = "ai_rec_reason_title", length = 100)
    private String aiRecReasonTitle;

    @Column(name = "ai_rec_reason_detail", columnDefinition = "TEXT")
    private String aiRecReasonDetail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static Report create(Long userId, String regionCode, String categoryCode,
                                 Integer year, Integer quarter, String grade, Integer score, String declineType) {
        Report report = new Report();
        report.userId = userId;
        report.regionCode = regionCode;
        report.categoryCode = categoryCode;
        report.year = year;
        report.quarter = quarter;
        report.grade = grade;
        report.score = score;
        report.declineType = declineType;
        report.createdAt = LocalDateTime.now();
        return report;
    }

    public void applyAiResponse(String summary, String aiOutlook, String predictedTrend, String predictedNextGrade,
                                 String decisionRecommendation, String decisionTitle, String decisionDescription,
                                 String aiRecBadgeType, String aiRecTitle, String aiRecReasonTitle, String aiRecReasonDetail) {
        this.summary = summary;
        this.aiOutlook = aiOutlook;
        this.predictedTrend = predictedTrend;
        this.predictedNextGrade = predictedNextGrade;
        this.decisionRecommendation = decisionRecommendation;
        this.decisionTitle = decisionTitle;
        this.decisionDescription = decisionDescription;
        this.aiRecBadgeType = aiRecBadgeType;
        this.aiRecTitle = aiRecTitle;
        this.aiRecReasonTitle = aiRecReasonTitle;
        this.aiRecReasonDetail = aiRecReasonDetail;
    }
}
