package com.ratelimiter.repository;

import com.ratelimiter.entity.ThrottleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ThrottleLogRepository extends JpaRepository<ThrottleLog, Long> {

    List<ThrottleLog> findByRejectedAtBetweenOrderByRejectedAtDesc(Instant from, Instant to);

    List<ThrottleLog> findByUserIdAndRejectedAtAfter(String userId, Instant after);

    long countByRejectedAtAfter(Instant after);
}
