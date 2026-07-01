package bigbang.butilkka_be.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_interest_regions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInterestRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(length = 50)
    private String alias;

    @Column(name = "sort_order")
    private Integer sortOrder;

    public static UserInterestRegion create(Long userId, String regionCode, String alias, Integer sortOrder) {
        UserInterestRegion uir = new UserInterestRegion();
        uir.userId = userId;
        uir.regionCode = regionCode;
        uir.alias = alias;
        uir.sortOrder = sortOrder;
        return uir;
    }
}
