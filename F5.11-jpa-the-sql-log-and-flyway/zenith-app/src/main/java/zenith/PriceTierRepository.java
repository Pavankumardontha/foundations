package zenith;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// F5.11 Lesson 8: repository for the child entity.
public interface PriceTierRepository extends JpaRepository<PriceTier, UUID> {
}
