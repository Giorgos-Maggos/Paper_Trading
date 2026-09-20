package com.trading.platform.repository;

import com.trading.platform.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByPortfolioId(Long portfolioId);

    Optional<Position> findByPortfolioIdAndSymbol(Long portfolioId, String symbol);
}
