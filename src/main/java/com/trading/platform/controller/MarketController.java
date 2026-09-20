package com.trading.platform.controller;

import com.trading.platform.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Simulated & real-time market prices and stock ticker autocomplete search")
public class MarketController {

    private final MarketDataService marketDataService;

    @GetMapping("/search")
    @Operation(summary = "Search tickers and company names",
               description = "Searches for stock symbols and company names matching a search query (e.g. 'Apple', 'Pfizer', 'PFE').")
    public ResponseEntity<List<Map<String, String>>> search(
            @Parameter(description = "Search query (ticker or company name)", example = "Pfizer")
            @RequestParam(value = "q", required = false, defaultValue = "") String query) {
        return ResponseEntity.ok(marketDataService.searchSymbols(query));
    }

    @GetMapping("/price/{symbol}")
    @Operation(summary = "Get current price for a symbol",
               description = "Returns the current live market price from Yahoo Finance API.")
    public ResponseEntity<Map<String, Object>> getPrice(
            @Parameter(description = "Stock ticker symbol (e.g. AAPL, PFE, NVDA)", example = "AAPL")
            @PathVariable String symbol) {
        BigDecimal price = marketDataService.getPrice(symbol.toUpperCase());
        return ResponseEntity.ok(Map.of(
                "symbol", symbol.toUpperCase(),
                "price", price,
                "currency", "USD",
                "source", "YAHOO_FINANCE",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/prices")
    @Operation(summary = "Get prices for all pre-loaded symbols")
    public ResponseEntity<Map<String, Object>> getAllPrices() {
        Map<String, BigDecimal> prices = marketDataService.getAllPrices();
        return ResponseEntity.ok(Map.of(
                "prices", prices,
                "currency", "USD",
                "source", "YAHOO_FINANCE",
                "timestamp", LocalDateTime.now().toString(),
                "count", prices.size()
        ));
    }

    @GetMapping("/symbols")
    @Operation(summary = "Get all supported symbols")
    public ResponseEntity<Map<String, Object>> getSupportedSymbols() {
        Set<String> symbols = MarketDataService.SUPPORTED_SYMBOLS;
        return ResponseEntity.ok(Map.of(
                "symbols", symbols,
                "count", symbols.size()
        ));
    }
    @GetMapping("/chart/{symbol}")
    @Operation(summary = "Get historical chart data for a symbol",
               description = "Returns historical price data from Yahoo Finance for chart rendering. Supported ranges: 1d, 3mo, 6mo, 1y, 3y, 5y.")
    public ResponseEntity<Map<String, Object>> getChartData(
            @Parameter(description = "Stock ticker symbol", example = "AAPL")
            @PathVariable String symbol,
            @Parameter(description = "Time range: 1d, 3mo, 6mo, 1y, 3y, 5y", example = "1d")
            @RequestParam(value = "range", required = false, defaultValue = "1d") String range) {
        Map<String, Object> chartData = marketDataService.getChartData(symbol, range);
        return ResponseEntity.ok(chartData);
    }
}
