package com.trading.platform.repository;

import com.trading.platform.model.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

    List<PortfolioSnapshot> findByPortfolioIdOrderBySnapshotAtAsc(Long portfolioId);
}
