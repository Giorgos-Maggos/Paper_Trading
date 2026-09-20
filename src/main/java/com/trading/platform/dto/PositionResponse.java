package com.trading.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "A single stock position with live P&L")
public class PositionResponse {

    @Schema(description = "Position ID", example = "1")
    private Long id;

    @Schema(description = "Stock ticker symbol", example = "AAPL")
    private String symbol;

    @Schema(description = "Number of shares held", example = "10")
    private Integer quantity;

    @Schema(description = "Average cost price per share", example = "172.50")
    private BigDecimal averageCostPrice;

    @Schema(description = "Current simulated market price", example = "185.00")
    private BigDecimal currentPrice;

    @Schema(description = "Current market value of position", example = "1850.00")
    private BigDecimal marketValue;

    @Schema(description = "Unrealized P&L for this position", example = "125.00")
    private BigDecimal unrealizedPnl;

    @Schema(description = "Unrealized P&L as percentage", example = "7.25")
    private BigDecimal unrealizedPnlPercent;
}
