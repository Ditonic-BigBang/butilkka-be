package bigbang.butilkka_be.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

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

    @Column(name = "region_name", length = 50)
    private String regionName;

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

    public static ReportSimilarCase create(Long reportId, String regionCode, String regionName, String summary, String description,
                                            Integer startYear, Integer endYear,
                                            String tag1, String tag2, String tag3, String tag4) {
        ReportSimilarCase c = new ReportSimilarCase();
        c.id = UUID.randomUUID().toString();
        c.reportId = reportId;
        c.regionCode = regionCode;
        c.regionName = regionName;
        c.summary = summary;
        c.description = description;
        c.startYear = startYear != null ? startYear.shortValue() : null;
        c.endYear = endYear != null ? endYear.shortValue() : null;
        c.tag1 = tag1;
        c.tag2 = tag2;
        c.tag3 = tag3;
        c.tag4 = tag4;
        return c;
    }
}
