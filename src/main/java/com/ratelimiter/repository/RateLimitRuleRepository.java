package com.ratelimiter.repository;

import com.ratelimiter.entity.RateLimitRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RateLimitRuleRepository extends JpaRepository<RateLimitRule, Long> {

    @Query("SELECT r FROM RateLimitRule r WHERE r.enabled = true ORDER BY r.priority DESC")
    List<RateLimitRule> findAllEnabledOrderByPriorityDesc();
}
