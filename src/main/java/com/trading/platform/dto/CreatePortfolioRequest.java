package com.trading.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body for creating a new named portfolio")
public class CreatePortfolioRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "Owner's username", example = "trader1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "Portfolio name is required")
    @Size(min = 2, max = 50, message = "Portfolio name must be between 2 and 50 characters")
    @Schema(description = "Name of the new portfolio", example = "Tech Growth", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}
