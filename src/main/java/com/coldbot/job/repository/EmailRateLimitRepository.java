package com.coldbot.job.repository;

import com.coldbot.job.entity.EmailRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface EmailRateLimitRepository extends JpaRepository<EmailRateLimit, Long> {

    Optional<EmailRateLimit> findByUserIdAndRateDate(Long userId, LocalDate rateDate);
}
