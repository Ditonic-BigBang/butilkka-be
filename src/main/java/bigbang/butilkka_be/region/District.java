package bigbang.butilkka_be.region;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "districts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class District {

    @Id
    @Column(name = "district_code", length = 50)
    private String districtCode;

    @Column(name = "district_name", length = 50)
    private String districtName;
}
