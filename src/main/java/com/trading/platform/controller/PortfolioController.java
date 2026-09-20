package com.trading.platform.controller;

import com.trading.platform.dto.CreatePortfolioRequest;
import com.trading.platform.dto.PortfolioResponse;
import com.trading.platform.dto.TradeResponse;
import com.trading.platform.model.PortfolioActivity;
import com.trading.platform.service.DividendService;
import com.trading.platform.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "View portfolio positions, P&L, trade history, activities, dividends, and create named portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final DividendService dividendService;

    @PostMapping
    @Operation(summary = "Create a new named portfolio",
               description = "Creates a new named portfolio with $100,000 starting cash. A user can own up to 10 portfolios max.")
    public ResponseEntity<?> createPortfolio(@Valid @RequestBody CreatePortfolioRequest request) {
        try {
            PortfolioResponse portfolio = portfolioService.createPortfolio(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(portfolio);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/user/{username}")
    @Operation(summary = "Get all portfolios for a user",
               description = "Returns a list of all portfolios owned by the specified user.")
    public ResponseEntity<?> getUserPortfolios(
            @Parameter(description = "Username (e.g. trader1)", example = "trader1")
            @PathVariable String username) {
        try {
            List<PortfolioResponse> portfolios = portfolioService.getUserPortfolios(username);
            return ResponseEntity.ok(portfolios);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{portfolioId}")
    @Operation(summary = "Get full portfolio snapshot",
               description = "Returns cash balance, all positions with live P&L, total portfolio value, and overall return.")
    public ResponseEntity<?> getPortfolio(
            @Parameter(description = "Portfolio ID", example = "1")
            @PathVariable Long portfolioId) {
        try {
            PortfolioResponse response = portfolioService.getPortfolioSnapshot(portfolioId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{portfolioId}/trades")
    @Operation(summary = "Get trade history",
               description = "Returns all executed trades ordered by most recent first, including realized P&L on SELL trades.")
    public ResponseEntity<List<TradeResponse>> getTradeHistory(
            @Parameter(description = "Portfolio ID", example = "1")
            @PathVariable Long portfolioId) {
        return ResponseEntity.ok(portfolioService.getTradeHistory(portfolioId));
    }

    @GetMapping("/{portfolioId}/activities")
    @Operation(summary = "Get portfolio activity and transaction audit log",
               description = "Returns complete chronological audit trail of all moves (deposits, trades, dividends, etc.) on the portfolio.")
    public ResponseEntity<List<PortfolioActivity>> getActivityLog(
            @Parameter(description = "Portfolio ID", example = "1")
            @PathVariable Long portfolioId) {
        return ResponseEntity.ok(portfolioService.getActivityLog(portfolioId));
    }

    @PostMapping("/{portfolioId}/dividends/distribute")
    @Operation(summary = "Process dividend payout",
               description = "Calculates and deposits dividend payouts for all eligible distributing open positions directly into the portfolio cash balance.")
    public ResponseEntity<?> distributeDividends(
            @Parameter(description = "Portfolio ID", example = "1")
            @PathVariable Long portfolioId) {
        try {
            Map<String, Object> result = dividendService.distributeDueDividendsForPortfolio(portfolioId, true);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{portfolioId}/dividends")
    @Operation(summary = "Get dividend projections and holdings overview",
               description = "Returns estimated annual & monthly dividend income and position-by-position dividend rates.")
    public ResponseEntity<?> getDividendSummary(
            @Parameter(description = "Portfolio ID", example = "1")
            @PathVariable Long portfolioId) {
        try {
            return ResponseEntity.ok(dividendService.getDividendSummary(portfolioId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{portfolioId}/dividends/next/{symbol}")
    @Operation(summary = "Get next upcoming dividend distribution details for a specific stock",
               description = "Returns exact next pay date, ex-dividend date, cash per share, your total estimated cash payout, yield, and frequency.")
    public ResponseEntity<?> getNextDividendForSymbol(
            @Parameter(description = "Portfolio ID", example = "1")
            @PathVariable Long portfolioId,
            @Parameter(description = "Stock Symbol (e.g. O, PFE, VUAA)", example = "O")
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "0") Integer quantity) {
        try {
            int qty = quantity;
            if (qty <= 0) {
                PortfolioResponse p = portfolioService.getPortfolioSnapshot(portfolioId);
                qty = p.getPositions().stream()
                        .filter(pos -> pos.getSymbol().equalsIgnoreCase(symbol))
                        .mapToInt(com.trading.platform.dto.PositionResponse::getQuantity)
                        .findFirst()
                        .orElse(1);
            }
            return ResponseEntity.ok(dividendService.getNextDividendDetails(symbol, qty));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Get portfolio 1 (quick access)")
    public ResponseEntity<PortfolioResponse> getDefaultPortfolio() {
        return ResponseEntity.ok(portfolioService.getPortfolioSnapshot(1L));
    }

    @GetMapping("/summary")
    @Operation(summary = "Quick portfolio summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        PortfolioResponse p = portfolioService.getPortfolioSnapshot(1L);
        return ResponseEntity.ok(Map.of(
                "portfolioId", p.getId(),
                "name", p.getName(),
                "cashBalance", p.getCashBalance(),
                "positionsValue", p.getPositionsValue(),
                "totalValue", p.getTotalValue(),
                "unrealizedPnl", p.getUnrealizedPnl(),
                "realizedPnl", p.getRealizedPnl(),
                "returnPercent", p.getReturnPercent(),
                "openPositions", p.getPositions().size()
        ));
    }

    @GetMapping("/{portfolioId}/performance")
    @Operation(summary = "Get portfolio historical performance",
               description = "Returns historical snapshots of portfolio value and P&L over time for charting.")
    public ResponseEntity<List<Map<String, Object>>> getPerformanceHistory(
            @Parameter(description = "Portfolio ID", example = "1")
            @PathVariable Long portfolioId) {
        return ResponseEntity.ok(portfolioService.getPerformanceHistory(portfolioId));
    }

    @GetMapping("/{portfolioId}/allocation")
    @Operation(summary = "Get portfolio asset allocation breakdown",
               description = "Returns weight percentages, market values, and cash balance for donut/pie diversification charts.")
    public ResponseEntity<Map<String, Object>> getAllocation(
            @Parameter(description = "Portfolio ID", example = "1")
            @PathVariable Long portfolioId) {
        return ResponseEntity.ok(portfolioService.getAllocationBreakdown(portfolioId));
    }

    @GetMapping("/{portfolioId}/trading-stats")
    @Operation(summary = "Get gamified trading stats and performance metrics",
               description = "Returns win rate %, profit factor, best/worst trade, and trader rank badge.")
    public ResponseEntity<Map<String, Object>> getTradingStats(
            @Parameter(description = "Portfolio ID", example = "1")
            @PathVariable Long portfolioId) {
        return ResponseEntity.ok(portfolioService.getTradingStats(portfolioId));
    }
}
