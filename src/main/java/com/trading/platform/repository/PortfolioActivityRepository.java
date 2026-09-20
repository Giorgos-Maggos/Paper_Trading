package com.trading.platform.repository;

import com.trading.platform.model.PortfolioActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioActivityRepository extends JpaRepository<PortfolioActivity, Long> {

    List<PortfolioActivity> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);

    List<PortfolioActivity> findByPortfolioIdAndActivityTypeOrderByCreatedAtDesc(
            Long portfolioId, PortfolioActivity.ActivityType activityType);

    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM PortfolioActivity a WHERE a.portfolio.id = :portfolioId AND a.activityType = com.trading.platform.model.PortfolioActivity$ActivityType.DIVIDEND")
    BigDecimal sumDividendsByPortfolioId(Long portfolioId);

    Optional<PortfolioActivity> findFirstByPortfolioIdAndSymbolAndActivityTypeOrderByCreatedAtDesc(
            Long portfolioId, String symbol, PortfolioActivity.ActivityType activityType);
}

