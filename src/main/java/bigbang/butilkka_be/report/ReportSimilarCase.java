package bigbang.butilkka_be.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "report_similar_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportSimilarCase {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(length = 255)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_year")
    private Short startYear;

    @Column(name = "end_year")
    private Short endYear;

    @Column(length = 20)
    private String tag1;

    @Column(length = 20)
    private String tag2;

    @Column(length = 20)
    private String tag3;

    @Column(length = 20)
    private String tag4;
}
