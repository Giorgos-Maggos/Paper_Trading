package com.trading.platform.controller;

import com.trading.platform.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
@Tag(name = "Watchlist", description = "Manage user's stock watchlist and bookmarked tickers")
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    @Operation(summary = "Get user watchlist", description = "Returns all watchlisted stocks with live market prices and dividend details.")
    public ResponseEntity<List<Map<String, Object>>> getWatchlist(
            @Parameter(description = "Username", example = "trader1")
            @RequestParam(defaultValue = "trader1") String username) {
        return ResponseEntity.ok(watchlistService.getWatchlist(username));
    }

    @GetMapping("/symbols")
    @Operation(summary = "Get watchlisted symbols set", description = "Returns an array of watchlisted symbol strings.")
    public ResponseEntity<?> getWatchlistSymbols(
            @Parameter(description = "Username", example = "trader1")
            @RequestParam(defaultValue = "trader1") String username) {
        return ResponseEntity.ok(watchlistService.getWatchlistSymbols(username));
    }

    @PostMapping("/toggle")
    @Operation(summary = "Toggle watchlist status", description = "Adds the stock to watchlist if not present, removes it if present.")
    public ResponseEntity<Map<String, Object>> toggleWatchlist(
            @Parameter(description = "Stock symbol (e.g. AAPL)", example = "AAPL")
            @RequestParam String symbol,
            @Parameter(description = "Username", example = "trader1")
            @RequestParam(defaultValue = "trader1") String username) {
        boolean isWatchlisted = watchlistService.toggleWatchlist(username, symbol);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol.toUpperCase().trim(),
                "isWatchlisted", isWatchlisted,
                "message", isWatchlisted ? "Added to watchlist" : "Removed from watchlist"
        ));
    }

    @PostMapping
    @Operation(summary = "Add symbol to watchlist")
    public ResponseEntity<?> addToWatchlist(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "trader1") String username) {
        watchlistService.addToWatchlist(username, symbol);
        return ResponseEntity.ok(Map.of("symbol", symbol.toUpperCase().trim(), "status", "ADDED"));
    }

    @DeleteMapping("/{symbol}")
    @Operation(summary = "Remove symbol from watchlist")
    public ResponseEntity<?> removeFromWatchlist(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "trader1") String username) {
        watchlistService.removeFromWatchlist(username, symbol);
        return ResponseEntity.ok(Map.of("symbol", symbol.toUpperCase().trim(), "status", "REMOVED"));
    }
}
