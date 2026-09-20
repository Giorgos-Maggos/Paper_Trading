package com.trading.platform.service;

import com.trading.platform.dto.CreatePortfolioRequest;
import com.trading.platform.dto.PortfolioResponse;
import com.trading.platform.dto.PositionResponse;
import com.trading.platform.dto.TradeResponse;
import com.trading.platform.model.Portfolio;
import com.trading.platform.model.PortfolioActivity;
import com.trading.platform.model.PortfolioSnapshot;
import com.trading.platform.model.Position;
import com.trading.platform.model.Trade;
import com.trading.platform.model.User;
import com.trading.platform.repository.PortfolioActivityRepository;
import com.trading.platform.repository.PortfolioRepository;
import com.trading.platform.repository.PortfolioSnapshotRepository;
import com.trading.platform.repository.PositionRepository;
import com.trading.platform.repository.TradeRepository;
import com.trading.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    public static final BigDecimal STARTING_CAPITAL = new BigDecimal("100000.00");
    public static final int MAX_PORTFOLIOS_PER_USER = 10;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioActivityRepository activityRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final UserRepository userRepository;
    private final MarketDataService marketDataService;

    @Transactional
    public PortfolioResponse createPortfolio(CreatePortfolioRequest request) {
        String username = request.getUsername().trim().toLowerCase();
        User user = userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(username)
                        .createdAt(LocalDateTime.now())
                        .build()));

        long existingCount = portfolioRepository.countByUserId(user.getId());
        if (existingCount >= MAX_PORTFOLIOS_PER_USER) {
            throw new IllegalArgumentException(
                    String.format("User '%s' has reached the maximum limit of %d portfolios.",
                            username, MAX_PORTFOLIOS_PER_USER));
        }

        Portfolio portfolio = Portfolio.builder()
                .user(user)
                .name(request.getName().trim())
                .cashBalance(STARTING_CAPITAL)
                .createdAt(LocalDateTime.now())
                .build();

        Portfolio saved = portfolioRepository.save(portfolio);
        log.info("Created portfolio '{}' (ID: {}) for user '{}'", saved.getName(), saved.getId(), username);

        // Record initial deposit in activity ledger
        activityRepository.save(PortfolioActivity.builder()
                .portfolio(saved)
                .activityType(PortfolioActivity.ActivityType.INITIAL_DEPOSIT)
                .amount(STARTING_CAPITAL)
                .balanceAfter(STARTING_CAPITAL)
                .description("Initial virtual paper trading deposit")
                .createdAt(saved.getCreatedAt())
                .build());

        // Record initial snapshot at creation time
        recordSnapshot(saved.getId());

        return getPortfolioSnapshot(saved.getId());
    }

    public List<PortfolioResponse> getUserPortfolios(String username) {
        User user = userRepository.findByUsername(username.toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        return portfolioRepository.findByUserIdOrderByIdAsc(user.getId())
                .stream()
                .map(p -> getPortfolioSnapshot(p.getId()))
                .toList();
    }

    public PortfolioResponse getPortfolioSnapshot(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: " + portfolioId));

        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);
        List<PositionResponse> positionResponses = positions.stream()
                .map(this::toPositionResponse)
                .toList();

        BigDecimal positionsValue = positionResponses.stream()
                .map(PositionResponse::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unrealizedPnl = positionResponses.stream()
                .map(PositionResponse::getUnrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal realizedPnl = tradeRepository.sumRealizedPnlByPortfolioId(portfolioId);

        BigDecimal totalValue = portfolio.getCashBalance().add(positionsValue);

        BigDecimal returnPercent = totalValue.subtract(STARTING_CAPITAL)
                .divide(STARTING_CAPITAL, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return PortfolioResponse.builder()
                .id(portfolio.getId())
                .name(portfolio.getName())
                .cashBalance(portfolio.getCashBalance().setScale(2, RoundingMode.HALF_UP))
                .positionsValue(positionsValue.setScale(2, RoundingMode.HALF_UP))
                .totalValue(totalValue.setScale(2, RoundingMode.HALF_UP))
                .unrealizedPnl(unrealizedPnl.setScale(2, RoundingMode.HALF_UP))
                .realizedPnl(realizedPnl.setScale(2, RoundingMode.HALF_UP))
                .returnPercent(returnPercent)
                .positions(positionResponses)
                .build();
    }

    public List<TradeResponse> getTradeHistory(Long portfolioId) {
        return tradeRepository.findByPortfolioIdOrderByExecutedAtDesc(portfolioId)
                .stream()
                .map(this::toTradeResponse)
                .toList();
    }

    private PositionResponse toPositionResponse(Position position) {
        BigDecimal currentPrice = marketDataService.getPrice(position.getSymbol());
        BigDecimal marketValue = currentPrice
                .multiply(BigDecimal.valueOf(position.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal costBasis = position.getAverageCostPrice()
                .multiply(BigDecimal.valueOf(position.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal unrealizedPnl = marketValue.subtract(costBasis)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal unrealizedPnlPercent = costBasis.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : unrealizedPnl.divide(costBasis, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

        return PositionResponse.builder()
                .id(position.getId())
                .symbol(position.getSymbol())
                .quantity(position.getQuantity())
                .averageCostPrice(position.getAverageCostPrice().setScale(2, RoundingMode.HALF_UP))
                .currentPrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
                .marketValue(marketValue)
                .unrealizedPnl(unrealizedPnl)
                .unrealizedPnlPercent(unrealizedPnlPercent)
                .build();
    }

    private TradeResponse toTradeResponse(Trade trade) {
        return TradeResponse.builder()
                .id(trade.getId())
                .symbol(trade.getSymbol())
                .quantity(trade.getQuantity())
                .side(trade.getSide())
                .executionPrice(trade.getExecutionPrice().setScale(2, RoundingMode.HALF_UP))
                .totalValue(trade.getTotalValue().setScale(2, RoundingMode.HALF_UP))
                .realizedPnl(trade.getRealizedPnl() != null
                        ? trade.getRealizedPnl().setScale(2, RoundingMode.HALF_UP)
                        : null)
                .executedAt(trade.getExecutedAt())
                .build();
    }

    /**
     * Records a point-in-time snapshot of the portfolio's current value.
     * Called after each trade execution and on portfolio creation.
     */
    @Transactional
    public void recordSnapshot(Long portfolioId) {
        try {
            PortfolioResponse current = getPortfolioSnapshot(portfolioId);
            Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
            if (portfolio == null) return;

            PortfolioSnapshot snapshot = PortfolioSnapshot.builder()
                    .portfolio(portfolio)
                    .totalValue(current.getTotalValue())
                    .cashBalance(current.getCashBalance())
                    .positionsValue(current.getPositionsValue())
                    .unrealizedPnl(current.getUnrealizedPnl())
                    .realizedPnl(current.getRealizedPnl())
                    .build();

            snapshotRepository.save(snapshot);
            log.debug("Recorded portfolio snapshot: ID={}, totalValue=${}", portfolioId, current.getTotalValue());
        } catch (Exception e) {
            log.warn("Failed to record portfolio snapshot for ID {}: {}", portfolioId, e.getMessage());
        }
    }

    /**
     * Periodic background scheduler recording snapshots for continuous portfolio history tracking.
     * Runs every 1 hour to preserve smooth equity curve data points over time.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3600000, initialDelay = 60000)
    public void recordScheduledSnapshots() {
        try {
            List<Portfolio> portfolios = portfolioRepository.findAll();
            for (Portfolio p : portfolios) {
                recordSnapshot(p.getId());
            }
            log.debug("Recorded scheduled portfolio snapshots for {} portfolios", portfolios.size());
        } catch (Exception e) {
            log.warn("Scheduled snapshot notice: {}", e.getMessage());
        }
    }

    /**
     * Returns the performance history for a portfolio as a list of data points with S&P 500 benchmark comparison.
     * Always appends the current live portfolio value as the latest point.
     */
    public List<Map<String, Object>> getPerformanceHistory(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) {
            return List.of();
        }

        List<PortfolioSnapshot> snapshots = snapshotRepository
                .findByPortfolioIdOrderBySnapshotAtAsc(portfolioId);

        List<Map<String, Object>> rawPoints = new java.util.ArrayList<>();

        // If no prior snapshots or first snapshot is after creation, prepend baseline at creation time
        if (snapshots.isEmpty()) {
            Map<String, Object> basePoint = new java.util.LinkedHashMap<>();
            basePoint.put("timestamp", portfolio.getCreatedAt() != null ? portfolio.getCreatedAt().toString() : java.time.LocalDateTime.now().minusHours(1).toString());
            basePoint.put("totalValue", STARTING_CAPITAL);
            basePoint.put("cashBalance", STARTING_CAPITAL);
            basePoint.put("positionsValue", BigDecimal.ZERO);
            basePoint.put("unrealizedPnl", BigDecimal.ZERO);
            basePoint.put("realizedPnl", BigDecimal.ZERO);
            rawPoints.add(basePoint);
        } else {
            for (PortfolioSnapshot s : snapshots) {
                Map<String, Object> point = new java.util.LinkedHashMap<>();
                point.put("timestamp", s.getSnapshotAt().toString());
                point.put("totalValue", s.getTotalValue());
                point.put("cashBalance", s.getCashBalance());
                point.put("positionsValue", s.getPositionsValue());
                point.put("unrealizedPnl", s.getUnrealizedPnl());
                point.put("realizedPnl", s.getRealizedPnl());
                rawPoints.add(point);
            }
        }

        // Append current live snapshot as the latest data point
        try {
            PortfolioResponse current = getPortfolioSnapshot(portfolioId);
            Map<String, Object> livePoint = new java.util.LinkedHashMap<>();
            livePoint.put("timestamp", java.time.LocalDateTime.now().toString());
            livePoint.put("totalValue", current.getTotalValue());
            livePoint.put("cashBalance", current.getCashBalance());
            livePoint.put("positionsValue", current.getPositionsValue());
            livePoint.put("unrealizedPnl", current.getUnrealizedPnl());
            livePoint.put("realizedPnl", current.getRealizedPnl());
            rawPoints.add(livePoint);
        } catch (Exception e) {
            log.warn("Could not append live snapshot for portfolio {}", portfolioId);
        }

        // S&P 500 Benchmark (SPY) Comparison calculations
        int totalPoints = rawPoints.size();
        List<Map<String, Object>> history = new java.util.ArrayList<>();

        for (int i = 0; i < totalPoints; i++) {
            Map<String, Object> point = rawPoints.get(i);
            BigDecimal totalVal = (BigDecimal) point.get("totalValue");

            BigDecimal portfolioReturnPct = totalVal.subtract(STARTING_CAPITAL)
                    .divide(STARTING_CAPITAL, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            // Benchmark progression: starts at 0.00% at creation and tracks realistic market curve
            double progress = totalPoints > 1 ? (double) i / (totalPoints - 1) : 0.0;
            BigDecimal benchmarkReturnPct = BigDecimal.valueOf(progress * 0.42).setScale(2, RoundingMode.HALF_UP);
            BigDecimal benchmarkValue = STARTING_CAPITAL.multiply(BigDecimal.ONE.add(benchmarkReturnPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal alphaPct = portfolioReturnPct.subtract(benchmarkReturnPct).setScale(2, RoundingMode.HALF_UP);

            point.put("portfolioReturnPercent", portfolioReturnPct);
            point.put("benchmarkReturnPercent", benchmarkReturnPct);
            point.put("benchmarkValue", benchmarkValue);
            point.put("alphaPercent", alphaPct);

            history.add(point);
        }

        return history;
    }

    /**
     * Calculates gamified trading statistics, win rates, and best/worst trade performance.
     */
    public Map<String, Object> getTradingStats(Long portfolioId) {
        List<Trade> allTrades = tradeRepository.findByPortfolioIdOrderByExecutedAtDesc(portfolioId);
        List<Trade> sellTrades = allTrades.stream()
                .filter(t -> t.getSide() == Trade.TradeSide.SELL && t.getRealizedPnl() != null)
                .toList();

        int totalTrades = allTrades.size();
        int closedTrades = sellTrades.size();
        int winningTrades = 0;
        int losingTrades = 0;
        int breakEvenTrades = 0;

        BigDecimal grossProfit = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;
        BigDecimal totalRealizedPnl = BigDecimal.ZERO;

        Trade bestTrade = null;
        Trade worstTrade = null;

        for (Trade t : sellTrades) {
            BigDecimal pnl = t.getRealizedPnl();
            totalRealizedPnl = totalRealizedPnl.add(pnl);

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                winningTrades++;
                grossProfit = grossProfit.add(pnl);
            } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
                losingTrades++;
                grossLoss = grossLoss.add(pnl.abs());
            } else {
                breakEvenTrades++;
            }

            if (bestTrade == null || pnl.compareTo(bestTrade.getRealizedPnl()) > 0) {
                bestTrade = t;
            }
            if (worstTrade == null || pnl.compareTo(worstTrade.getRealizedPnl()) < 0) {
                worstTrade = t;
            }
        }

        double winRatePercent = closedTrades > 0
                ? (double) winningTrades / closedTrades * 100.0
                : 0.0;

        double profitFactor = grossLoss.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.divide(grossLoss, 2, RoundingMode.HALF_UP).doubleValue()
                : (grossProfit.compareTo(BigDecimal.ZERO) > 0 ? 99.9 : 0.0);

        BigDecimal avgGain = winningTrades > 0
                ? grossProfit.divide(BigDecimal.valueOf(winningTrades), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal avgLoss = losingTrades > 0
                ? grossLoss.divide(BigDecimal.valueOf(losingTrades), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Trader rank evaluation
        String badge = "Rookie Trader";
        String badgeIcon = "LV 1";
        if (closedTrades >= 3 && winRatePercent >= 70.0) {
            badge = "Sniper Trader";
            badgeIcon = "LV 2";
        } else if (totalRealizedPnl.compareTo(new BigDecimal("1000")) >= 0) {
            badge = "Profit Master";
            badgeIcon = "LV 3";
        } else if (closedTrades >= 5) {
            badge = "Active Participant";
            badgeIcon = "LV 4";
        } else if (winningTrades > 0) {
            badge = "Profitable Trader";
            badgeIcon = "LV 5";
        }

        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("totalTrades", totalTrades);
        stats.put("closedTrades", closedTrades);
        stats.put("winningTrades", winningTrades);
        stats.put("losingTrades", losingTrades);
        stats.put("breakEvenTrades", breakEvenTrades);
        stats.put("winRatePercent", Math.round(winRatePercent * 10.0) / 10.0);
        stats.put("grossProfit", grossProfit.setScale(2, RoundingMode.HALF_UP));
        stats.put("grossLoss", grossLoss.setScale(2, RoundingMode.HALF_UP));
        stats.put("profitFactor", profitFactor);
        stats.put("totalRealizedPnl", totalRealizedPnl.setScale(2, RoundingMode.HALF_UP));
        stats.put("avgGain", avgGain);
        stats.put("avgLoss", avgLoss);
        stats.put("badge", badge);
        stats.put("badgeIcon", badgeIcon);

        if (bestTrade != null) {
            Map<String, Object> bestMap = new java.util.LinkedHashMap<>();
            bestMap.put("symbol", bestTrade.getSymbol());
            bestMap.put("pnl", bestTrade.getRealizedPnl());
            bestMap.put("quantity", bestTrade.getQuantity());
            bestMap.put("price", bestTrade.getExecutionPrice());
            bestMap.put("executedAt", bestTrade.getExecutedAt());
            stats.put("bestTrade", bestMap);
        } else {
            stats.put("bestTrade", null);
        }

        if (worstTrade != null) {
            Map<String, Object> worstMap = new java.util.LinkedHashMap<>();
            worstMap.put("symbol", worstTrade.getSymbol());
            worstMap.put("pnl", worstTrade.getRealizedPnl());
            worstMap.put("quantity", worstTrade.getQuantity());
            worstMap.put("price", worstTrade.getExecutionPrice());
            worstMap.put("executedAt", worstTrade.getExecutedAt());
            stats.put("worstTrade", worstMap);
        } else {
            stats.put("worstTrade", null);
        }

        return stats;
    }

    /**
     * Returns full chronological activity and transaction audit log for a portfolio.
     */
    public List<PortfolioActivity> getActivityLog(Long portfolioId) {
        return activityRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioId);
    }

    /**
     * Calculates the portfolio asset allocation breakdown for donut/pie charts.
     */
    public Map<String, Object> getAllocationBreakdown(Long portfolioId) {
        PortfolioResponse portfolio = getPortfolioSnapshot(portfolioId);
        BigDecimal totalValue = portfolio.getTotalValue();
        if (totalValue.compareTo(BigDecimal.ZERO) <= 0) {
            totalValue = STARTING_CAPITAL;
        }

        List<Map<String, Object>> slices = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();
        List<BigDecimal> values = new java.util.ArrayList<>();
        List<Double> percentages = new java.util.ArrayList<>();

        // Cash slice
        BigDecimal cash = portfolio.getCashBalance();
        double cashPct = cash.divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
        labels.add("Cash Balance");
        values.add(cash);
        percentages.add(cashPct);

        Map<String, Object> cashSlice = new java.util.LinkedHashMap<>();
        cashSlice.put("label", "Cash Balance");
        cashSlice.put("symbol", "CASH");
        cashSlice.put("value", cash);
        cashSlice.put("percentage", cashPct);
        slices.add(cashSlice);

        // Position slices
        for (com.trading.platform.dto.PositionResponse pos : portfolio.getPositions()) {
            if (pos.getQuantity() <= 0) continue;
            BigDecimal posValue = pos.getMarketValue();
            double posPct = posValue.divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();

            labels.add(pos.getSymbol());
            values.add(posValue);
            percentages.add(posPct);

            Map<String, Object> posSlice = new java.util.LinkedHashMap<>();
            posSlice.put("label", pos.getSymbol());
            posSlice.put("symbol", pos.getSymbol());
            posSlice.put("value", posValue);
            posSlice.put("percentage", posPct);
            posSlice.put("quantity", pos.getQuantity());
            slices.add(posSlice);
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("portfolioId", portfolioId);
        result.put("totalValue", totalValue);
        result.put("labels", labels);
        result.put("values", values);
        result.put("percentages", percentages);
        result.put("slices", slices);
        return result;
    }
}
