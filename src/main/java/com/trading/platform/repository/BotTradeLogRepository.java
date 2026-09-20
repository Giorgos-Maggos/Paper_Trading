package com.trading.platform.repository;

import com.trading.platform.model.BotTradeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BotTradeLogRepository extends JpaRepository<BotTradeLog, Long> {
    List<BotTradeLog> findByPortfolioIdOrderByExecutedAtDesc(Long portfolioId);
}
