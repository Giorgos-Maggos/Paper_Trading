package com.trading.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit log and financial ledger tracking all moves and cash flow events on a portfolio.
 * (Account funding, BUY orders, SELL orders, Dividend payouts, Cash adjustments).
 */
@Entity
@Table(name = "portfolio_activity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioActivity {

    public enum ActivityType {
        INITIAL_DEPOSIT,
        BUY,
        SELL,
        DIVIDEND,
        CASH_ADJUSTMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @Column(length = 15)
    private String symbol;

    private Integer quantity;

    @Column(name = "unit_price", precision = 15, scale = 4)
    private BigDecimal unitPrice;

    // Positive for credit (+), Negative for debit (-)
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
