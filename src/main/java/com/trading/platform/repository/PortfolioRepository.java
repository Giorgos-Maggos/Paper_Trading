package com.trading.platform.repository;

import com.trading.platform.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUserIdOrderByIdAsc(Long userId);
    long countByUserId(Long userId);
}
