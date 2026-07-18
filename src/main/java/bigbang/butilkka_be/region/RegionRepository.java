package bigbang.butilkka_be.region;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, String> {
    List<Region> findByRegionNameContaining(String keyword);
    Optional<Region> findFirstByDistrictCode(String districtCode);
    List<Region> findByDistrictCode(String districtCode);
}
