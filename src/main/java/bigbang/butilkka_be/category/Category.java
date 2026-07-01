package bigbang.butilkka_be.category;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @Column(name = "category_code", length = 30)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName;
}
