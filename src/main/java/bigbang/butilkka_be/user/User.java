package bigbang.butilkka_be.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kakao_id", nullable = false, unique = true)
    private Long kakaoId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "is_onboarded", nullable = false)
    private boolean isOnboarded = false;

    @Column(name = "store_name", length = 50)
    private String storeName;

    @Column(name = "store_address", length = 100)
    private String storeAddress;

    @Column(name = "store_open_date")
    private LocalDate storeOpenDate;

    @Column(name = "store_region", length = 20)
    private String storeRegion;

    @Column(name = "store_lat")
    private Double storeLat;

    @Column(name = "store_lng")
    private Double storeLng;

    @Column(name = "category_code", length = 30)
    private String categoryCode;

    @Column(name = "sms_alert", nullable = false)
    private boolean smsAlert = false;

    @Column(name = "auto_report", nullable = false)
    private boolean autoReport = false;

    @Column(name = "urgent_alert", nullable = false)
    private boolean urgentAlert = false;

    @Column(name = "is_report_pro", nullable = false)
    private boolean isReportPro = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static User create(Long kakaoId, String name) {
        User user = new User();
        user.kakaoId = kakaoId;
        user.name = name;
        user.isOnboarded = false;
        user.createdAt = LocalDateTime.now();
        return user;
    }

    public void updateStore(
            String regionCode,
            String categoryCode,
            Double lat,
            Double lng,
            String storeName,
            String storeAddress,
            LocalDate storeOpenDate) {
        this.storeRegion = regionCode;
        this.categoryCode = categoryCode;
        this.storeLat = lat;
        this.storeLng = lng;
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.storeOpenDate = storeOpenDate;
        this.isOnboarded = true;
    }

    public void updateProfile(String name, String regionCode, String categoryCode, Double lat, Double lng) {
        if (name != null) {
            this.name = name;
        }
        if (regionCode != null) {
            this.storeRegion = regionCode;
            this.categoryCode = categoryCode;
            this.storeLat = lat;
            this.storeLng = lng;
        }
    }

    public void updateNotificationSettings(Boolean smsAlert, Boolean autoReport, Boolean urgentAlert) {
        if (smsAlert != null) {
            this.smsAlert = smsAlert;
        }
        if (autoReport != null) {
            this.autoReport = autoReport;
        }
        if (urgentAlert != null) {
            this.urgentAlert = urgentAlert;
        }
    }

    public void completeOnboarding() {
        this.isOnboarded = true;
    }

    public void activateReportPro() {
        this.isReportPro = true;
    }
}
