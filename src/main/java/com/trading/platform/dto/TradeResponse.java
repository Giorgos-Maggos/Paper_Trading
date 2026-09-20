package com.trading.platform.dto;

import com.trading.platform.model.Trade;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "An executed trade record")
public class TradeResponse {

    @Schema(description = "Trade ID", example = "1")
    private Long id;

    @Schema(description = "Stock ticker symbol", example = "AAPL")
    private String symbol;

    @Schema(description = "Number of shares traded", example = "10")
    private Integer quantity;

    @Schema(description = "Trade side: BUY or SELL", example = "BUY")
    private Trade.TradeSide side;

    @Schema(description = "Execution price per share", example = "172.50")
    private BigDecimal executionPrice;

    @Schema(description = "Total trade value", example = "1725.00")
    private BigDecimal totalValue;

    @Schema(description = "Realized P&L (only for SELL trades)", example = "125.00")
    private BigDecimal realizedPnl;

    @Schema(description = "Time the trade was executed")
    private LocalDateTime executedAt;
}
