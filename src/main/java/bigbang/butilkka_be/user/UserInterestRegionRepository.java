package bigbang.butilkka_be.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserInterestRegionRepository extends JpaRepository<UserInterestRegion, Long> {
    List<UserInterestRegion> findByUserId(Long userId);
}
