package bigbang.butilkka_be.store;

import bigbang.butilkka_be.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "store_name", nullable = false, length = 50)
    private String storeName;

    @Column(name = "store_address", length = 100)
    private String storeAddress;

    @Column(name = "store_open_date")
    private LocalDate storeOpenDate;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(name = "category_code", nullable = false, length = 30)
    private String categoryCode;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Store create(
            User user,
            String storeName,
            String storeAddress,
            LocalDate storeOpenDate,
            String regionCode,
            String categoryCode,
            Double lat,
            Double lng,
            boolean isPrimary) {
        Store store = new Store();
        store.user = user;
        store.storeName = storeName;
        store.storeAddress = storeAddress;
        store.storeOpenDate = storeOpenDate;
        store.regionCode = regionCode;
        store.categoryCode = categoryCode;
        store.lat = lat;
        store.lng = lng;
        store.isPrimary = isPrimary;
        store.createdAt = LocalDateTime.now();
        store.updatedAt = LocalDateTime.now();
        return store;
    }

    public void update(
            String storeName,
            String storeAddress,
            LocalDate storeOpenDate,
            String regionCode,
            String categoryCode,
            Double lat,
            Double lng) {
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.storeOpenDate = storeOpenDate;
        this.regionCode = regionCode;
        this.categoryCode = categoryCode;
        this.lat = lat;
        this.lng = lng;
        this.updatedAt = LocalDateTime.now();
    }

    public void setPrimary(boolean primary) {
        this.isPrimary = primary;
        this.updatedAt = LocalDateTime.now();
    }
}
