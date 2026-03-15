package com.distributed.teamai.intelligence_service.repository;

import com.distributed.teamai.intelligence_service.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface UsageRepository extends JpaRepository<UsageLog, Long> {

    Optional<UsageLog> findByUserIdAndDate(Long userId, LocalDate date);

}
