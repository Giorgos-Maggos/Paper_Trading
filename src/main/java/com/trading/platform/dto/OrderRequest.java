package com.trading.platform.dto;

import com.trading.platform.model.Order;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request body for placing a new order. Specify either 'quantity' (number of shares) or 'amount' (dollar value to spend). If 'amount' is provided, quantity is auto-calculated from the current market price.")
public class OrderRequest {

    @NotNull(message = "Portfolio ID is required")
    @Schema(description = "ID of the portfolio", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long portfolioId;

    @NotBlank(message = "Symbol is required")
    @Pattern(regexp = "^[A-Z0-9.\\-]{1,15}$", message = "Symbol must be 1-15 characters (letters, numbers, dots, hyphens)")
    @Schema(description = "Stock ticker symbol", example = "AAPL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(description = "Number of shares to trade. Optional if 'amount' is provided.", example = "10")
    private Integer quantity;

    @DecimalMin(value = "0.01", message = "Amount must be at least $0.01")
    @Schema(description = "Dollar amount to spend (BUY) or sell worth (SELL). Optional if 'quantity' is provided. Quantity will be auto-calculated from the current market price.", example = "500.00")
    private BigDecimal amount;

    @NotNull(message = "Side is required (BUY or SELL)")
    @Schema(description = "Order side: BUY or SELL", example = "BUY", requiredMode = Schema.RequiredMode.REQUIRED)
    private Order.OrderSide side;
}
