package com.trading.platform.repository;

import com.trading.platform.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByPortfolioIdOrderByExecutedAtDesc(Long portfolioId);

    @Query("SELECT COALESCE(SUM(t.realizedPnl), 0) FROM Trade t WHERE t.portfolio.id = :portfolioId AND t.side = com.trading.platform.model.Trade$TradeSide.SELL")
    BigDecimal sumRealizedPnlByPortfolioId(Long portfolioId);
}
