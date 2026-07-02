package bigbang.butilkka_be.region;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, String> {
    List<Region> findByRegionNameContaining(String keyword);
}
