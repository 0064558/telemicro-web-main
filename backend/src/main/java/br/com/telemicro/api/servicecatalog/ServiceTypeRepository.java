package br.com.telemicro.api.servicecatalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, UUID> {
    List<ServiceType> findByActiveTrueOrderByDisplayOrderAsc();

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<ServiceType> findByCodeAndActiveTrue(String code);
}
