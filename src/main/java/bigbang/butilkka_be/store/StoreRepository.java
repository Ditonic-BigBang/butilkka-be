package bigbang.butilkka_be.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByUserIdOrderByIsPrimaryDescCreatedAtDesc(Long userId);

    Optional<Store> findByIdAndUserId(Long id, Long userId);

    Optional<Store> findByUserIdAndIsPrimaryTrue(Long userId);

    long countByUserId(Long userId);

    @Modifying
    @Query("UPDATE Store s SET s.isPrimary = false WHERE s.user.id = :userId AND s.isPrimary = true")
    void clearPrimaryByUserId(@Param("userId") Long userId);
}
