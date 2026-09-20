package com.trading.platform.service;

import com.trading.platform.model.WatchlistItem;
import com.trading.platform.repository.WatchlistRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final MarketDataService marketDataService;
    private final DividendService dividendService;

    @PostConstruct
    @Transactional
    public void initDefaultWatchlist() {
        try {
            if (watchlistRepository.findByUsernameOrderByCreatedAtDesc("trader1").isEmpty()) {
                List<String> defaults = List.of("AAPL", "NVDA", "MSFT", "GOOGL", "VUAA.DU", "O", "SPY");
                for (String sym : defaults) {
                    watchlistRepository.save(WatchlistItem.builder()
                            .username("trader1")
                            .symbol(sym.toUpperCase())
                            .build());
                }
                log.info("Initialized default watchlist for trader1 with {} symbols", defaults.size());
            }
        } catch (Exception e) {
            log.warn("Watchlist initialization notice: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getWatchlist(String username) {
        List<WatchlistItem> items = watchlistRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Map<String, Object>> result = new ArrayList<>();

        for (WatchlistItem item : items) {
            String sym = item.getSymbol().toUpperCase();
            BigDecimal price = marketDataService.getPrice(sym);
            var divInfo = dividendService.getAssetDividendInfo(sym);

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", item.getId());
            map.put("symbol", sym);
            map.put("name", divInfo.name());
            map.put("price", price);
            map.put("treatment", divInfo.treatment().name());
            map.put("annualPerShare", divInfo.annualPerShare());
            map.put("description", divInfo.description());
            map.put("addedAt", item.getCreatedAt());
            result.add(map);
        }
        return result;
    }

    public Set<String> getWatchlistSymbols(String username) {
        return watchlistRepository.findByUsernameOrderByCreatedAtDesc(username).stream()
                .map(item -> item.getSymbol().toUpperCase())
                .collect(Collectors.toSet());
    }

    @Transactional
    public boolean toggleWatchlist(String username, String symbol) {
        String sym = symbol.toUpperCase().trim();
        Optional<WatchlistItem> existing = watchlistRepository.findByUsernameAndSymbolIgnoreCase(username, sym);
        if (existing.isPresent()) {
            watchlistRepository.delete(existing.get());
            return false;
        } else {
            watchlistRepository.save(WatchlistItem.builder()
                    .username(username)
                    .symbol(sym)
                    .build());
            return true;
        }
    }

    @Transactional
    public WatchlistItem addToWatchlist(String username, String symbol) {
        String sym = symbol.toUpperCase().trim();
        return watchlistRepository.findByUsernameAndSymbolIgnoreCase(username, sym)
                .orElseGet(() -> watchlistRepository.save(WatchlistItem.builder()
                        .username(username)
                        .symbol(sym)
                        .build()));
    }

    @Transactional
    public void removeFromWatchlist(String username, String symbol) {
        watchlistRepository.deleteByUsernameAndSymbolIgnoreCase(username, symbol.toUpperCase().trim());
    }
}
