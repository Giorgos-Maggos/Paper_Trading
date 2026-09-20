package com.trading.platform.repository;

import com.trading.platform.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByUsernameOrderByCreatedAtDesc(String username);

    Optional<WatchlistItem> findByUsernameAndSymbolIgnoreCase(String username, String symbol);

    boolean existsByUsernameAndSymbolIgnoreCase(String username, String symbol);

    void deleteByUsernameAndSymbolIgnoreCase(String username, String symbol);
}
