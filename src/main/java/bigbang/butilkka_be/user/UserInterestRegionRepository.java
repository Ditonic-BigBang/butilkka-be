package bigbang.butilkka_be.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserInterestRegionRepository extends JpaRepository<UserInterestRegion, Long> {
    List<UserInterestRegion> findByUserId(Long userId);
    Optional<UserInterestRegion> findByUserIdAndRegionCode(Long userId, String regionCode);
    Optional<UserInterestRegion> findByUserIdAndRegionCodeStartingWith(Long userId, String districtCode);
}
