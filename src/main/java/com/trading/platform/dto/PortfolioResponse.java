package com.trading.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "Full portfolio snapshot including positions and P&L")
public class PortfolioResponse {

    @Schema(description = "Portfolio ID", example = "1")
    private Long id;

    @Schema(description = "Portfolio name", example = "My Paper Portfolio")
    private String name;

    @Schema(description = "Available cash balance in USD", example = "85000.00")
    private BigDecimal cashBalance;

    @Schema(description = "Current market value of all positions", example = "17500.00")
    private BigDecimal positionsValue;

    @Schema(description = "Total portfolio value (cash + positions)", example = "102500.00")
    private BigDecimal totalValue;

    @Schema(description = "Total unrealized P&L across all positions", example = "2500.00")
    private BigDecimal unrealizedPnl;

    @Schema(description = "Total realized P&L from closed trades", example = "500.00")
    private BigDecimal realizedPnl;

    @Schema(description = "Overall return % from starting $100,000", example = "2.50")
    private BigDecimal returnPercent;

    @Schema(description = "List of current positions")
    private List<PositionResponse> positions;
}
