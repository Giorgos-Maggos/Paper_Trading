package com.trading.platform.service;

import com.trading.platform.dto.OrderRequest;
import com.trading.platform.model.Order;
import com.trading.platform.model.Portfolio;
import com.trading.platform.model.PortfolioActivity;
import com.trading.platform.model.Position;
import com.trading.platform.model.Trade;
import com.trading.platform.repository.OrderRepository;
import com.trading.platform.repository.PortfolioActivityRepository;
import com.trading.platform.repository.PortfolioRepository;
import com.trading.platform.repository.PositionRepository;
import com.trading.platform.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioActivityRepository activityRepository;
    private final MarketDataService marketDataService;
    private final PortfolioService portfolioService;

    /**
     * Places and immediately executes a market order.
     * For BUY: validates sufficient cash, deducts cash, updates position.
     * For SELL: validates sufficient shares, credits cash, records realized P&L.
     */
    @Transactional
    public Order placeOrder(OrderRequest request) {
        // Validate that at least one of quantity or amount is provided
        if (request.getQuantity() == null && request.getAmount() == null) {
            throw new IllegalArgumentException(
                    "Either 'quantity' or 'amount' must be provided.");
        }

        Portfolio portfolio = portfolioRepository.findById(request.getPortfolioId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found with ID: " + request.getPortfolioId()));

        BigDecimal marketPrice = marketDataService.getPrice(request.getSymbol());

        // If amount is provided (and quantity is not), auto-calculate quantity
        int resolvedQuantity;
        if (request.getQuantity() != null) {
            resolvedQuantity = request.getQuantity();
        } else {
            // Calculate how many whole shares can be bought/sold with the given amount
            resolvedQuantity = request.getAmount()
                    .divide(marketPrice, 0, RoundingMode.DOWN)
                    .intValue();
            if (resolvedQuantity < 1) {
                throw new IllegalArgumentException(String.format(
                        "Amount $%.2f is not enough to buy even 1 share of %s at $%.2f per share.",
                        request.getAmount(), request.getSymbol(), marketPrice));
            }
            // Update the request quantity so downstream logic is consistent
            request.setQuantity(resolvedQuantity);
        }

        BigDecimal totalValue = marketPrice
                .multiply(BigDecimal.valueOf(resolvedQuantity))
                .setScale(2, RoundingMode.HALF_UP);

        // Build the order
        Order order = Order.builder()
                .portfolio(portfolio)
                .symbol(request.getSymbol().toUpperCase())
                .quantity(resolvedQuantity)
                .side(request.getSide())
                .type(Order.OrderType.MARKET)
                .status(Order.OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        // Execute based on side
        if (request.getSide() == Order.OrderSide.BUY) {
            executeBuyOrder(order, portfolio, marketPrice, totalValue);
        } else {
            executeSellOrder(order, portfolio, marketPrice, totalValue);
        }

        Order saved = orderRepository.save(order);
        if (saved.getStatus() == Order.OrderStatus.EXECUTED) {
            portfolioService.recordSnapshot(portfolio.getId());
        }
        return saved;
    }

    private void executeBuyOrder(Order order, Portfolio portfolio,
                                  BigDecimal price, BigDecimal totalValue) {
        // Validate sufficient cash
        if (portfolio.getCashBalance().compareTo(totalValue) < 0) {
            order.setStatus(Order.OrderStatus.REJECTED);
            order.setRejectReason(String.format(
                    "Insufficient cash. Required: $%.2f, Available: $%.2f",
                    totalValue, portfolio.getCashBalance()));
            log.warn("BUY order rejected for {}: {}", order.getSymbol(), order.getRejectReason());
            return;
        }

        // Deduct cash
        portfolio.setCashBalance(portfolio.getCashBalance().subtract(totalValue));
        portfolioRepository.save(portfolio);

        // Update or create position
        Position position = positionRepository
                .findByPortfolioIdAndSymbol(portfolio.getId(), order.getSymbol())
                .orElse(Position.builder()
                        .portfolio(portfolio)
                        .symbol(order.getSymbol())
                        .quantity(0)
                        .averageCostPrice(BigDecimal.ZERO)
                        .build());

        // Recalculate weighted average cost
        BigDecimal existingValue = position.getAverageCostPrice()
                .multiply(BigDecimal.valueOf(position.getQuantity()));
        int newTotalQty = position.getQuantity() + order.getQuantity();
        BigDecimal newTotalValue = existingValue.add(totalValue);
        BigDecimal newAvgCost = newTotalValue
                .divide(BigDecimal.valueOf(newTotalQty), 4, RoundingMode.HALF_UP);

        position.setQuantity(newTotalQty);
        position.setAverageCostPrice(newAvgCost);
        positionRepository.save(position);

        // Mark order executed
        order.setStatus(Order.OrderStatus.EXECUTED);
        order.setExecutedPrice(price);
        order.setTotalValue(totalValue);
        order.setExecutedAt(LocalDateTime.now());

        // Record trade
        tradeRepository.save(Trade.builder()
                .portfolio(portfolio)
                .orderId(order.getId() != null ? order.getId() : 0L)
                .symbol(order.getSymbol())
                .quantity(order.getQuantity())
                .side(Trade.TradeSide.BUY)
                .executionPrice(price)
                .totalValue(totalValue)
                .executedAt(LocalDateTime.now())
                .build());

        // Record activity ledger entry
        activityRepository.save(PortfolioActivity.builder()
                .portfolio(portfolio)
                .activityType(PortfolioActivity.ActivityType.BUY)
                .symbol(order.getSymbol())
                .quantity(order.getQuantity())
                .unitPrice(price)
                .amount(totalValue.negate()) // negative cash impact
                .balanceAfter(portfolio.getCashBalance())
                .description(String.format("Bought %d shares of %s @ $%.2f",
                        order.getQuantity(), order.getSymbol(), price))
                .createdAt(LocalDateTime.now())
                .build());

        log.info("BUY order executed: {} x {} @ ${}", order.getQuantity(), order.getSymbol(), price);
    }

    private void executeSellOrder(Order order, Portfolio portfolio,
                                   BigDecimal price, BigDecimal totalValue) {
        Position position = positionRepository
                .findByPortfolioIdAndSymbol(portfolio.getId(), order.getSymbol())
                .orElse(null);

        // Validate position exists and has enough shares
        if (position == null || position.getQuantity() < order.getQuantity()) {
            int available = position == null ? 0 : position.getQuantity();
            order.setStatus(Order.OrderStatus.REJECTED);
            order.setRejectReason(String.format(
                    "Insufficient shares. Required: %d, Available: %d",
                    order.getQuantity(), available));
            log.warn("SELL order rejected for {}: {}", order.getSymbol(), order.getRejectReason());
            return;
        }

        // Calculate realized P&L
        BigDecimal costBasis = position.getAverageCostPrice()
                .multiply(BigDecimal.valueOf(order.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal realizedPnl = totalValue.subtract(costBasis).setScale(2, RoundingMode.HALF_UP);

        // Credit cash
        portfolio.setCashBalance(portfolio.getCashBalance().add(totalValue));
        portfolioRepository.save(portfolio);

        // Reduce or remove position
        int remainingQty = position.getQuantity() - order.getQuantity();
        if (remainingQty == 0) {
            positionRepository.delete(position);
        } else {
            position.setQuantity(remainingQty);
            positionRepository.save(position);
        }

        // Mark order executed
        order.setStatus(Order.OrderStatus.EXECUTED);
        order.setExecutedPrice(price);
        order.setTotalValue(totalValue);
        order.setExecutedAt(LocalDateTime.now());

        // Record trade with realized P&L
        tradeRepository.save(Trade.builder()
                .portfolio(portfolio)
                .orderId(order.getId() != null ? order.getId() : 0L)
                .symbol(order.getSymbol())
                .quantity(order.getQuantity())
                .side(Trade.TradeSide.SELL)
                .executionPrice(price)
                .totalValue(totalValue)
                .realizedPnl(realizedPnl)
                .executedAt(LocalDateTime.now())
                .build());

        // Record activity ledger entry
        String pnlStr = realizedPnl.compareTo(BigDecimal.ZERO) >= 0
                ? String.format("+$%.2f profit", realizedPnl)
                : String.format("-$%.2f loss", realizedPnl.abs());
        activityRepository.save(PortfolioActivity.builder()
                .portfolio(portfolio)
                .activityType(PortfolioActivity.ActivityType.SELL)
                .symbol(order.getSymbol())
                .quantity(order.getQuantity())
                .unitPrice(price)
                .amount(totalValue) // positive cash impact
                .balanceAfter(portfolio.getCashBalance())
                .description(String.format("Sold %d shares of %s @ $%.2f (%s)",
                        order.getQuantity(), order.getSymbol(), price, pnlStr))
                .createdAt(LocalDateTime.now())
                .build());

        log.info("SELL order executed: {} x {} @ ${} | Realized P&L: ${}",
                order.getQuantity(), order.getSymbol(), price, realizedPnl);
    }

    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    public List<Order> getOrdersByPortfolio(Long portfolioId) {
        return orderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioId);
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }
}
