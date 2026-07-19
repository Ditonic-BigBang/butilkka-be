package bigbang.butilkka_be.region;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, String> {
    List<Region> findByRegionNameContaining(String keyword);
    Optional<Region> findFirstByDistrictCode(String districtCode);
    List<Region> findByDistrictCode(String districtCode);

    /**
     * regionName 또는 districtName으로 검색 (정렬: 자치구 완전일치 > 자치구 시작일치 > 상권명 시작일치 > 나머지)
     */
    @Query("""
        SELECT r FROM Region r
        JOIN District d ON r.districtCode = d.districtCode
        WHERE r.regionName LIKE CONCAT('%', :keyword, '%')
           OR d.districtName LIKE CONCAT('%', :keyword, '%')
        ORDER BY
            CASE WHEN d.districtName = :keyword THEN 0
                 WHEN d.districtName LIKE CONCAT(:keyword, '%') THEN 1
                 WHEN r.regionName LIKE CONCAT(:keyword, '%') THEN 2
                 ELSE 3 END,
            d.districtName, r.regionName
        """)
    List<Region> searchByKeyword(@Param("keyword") String keyword);
}
