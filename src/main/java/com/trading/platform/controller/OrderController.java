package com.trading.platform.controller;

import com.trading.platform.dto.OrderRequest;
import com.trading.platform.model.Order;
import com.trading.platform.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Place and manage trading orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place a market order",
               description = "Places a BUY or SELL market order. Executed immediately at the current simulated price.")
    public ResponseEntity<?> placeOrder(@Valid @RequestBody OrderRequest request) {
        try {
            Order order = orderService.placeOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(toOrderResponse(order));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/portfolio/{portfolioId}")
    @Operation(summary = "Get all orders for a portfolio")
    public ResponseEntity<List<Map<String, Object>>> getOrdersByPortfolio(
            @Parameter(description = "Portfolio ID", example = "1")
            @PathVariable Long portfolioId) {
        List<Map<String, Object>> orders = orderService.getOrdersByPortfolio(portfolioId)
                .stream()
                .map(this::toOrderResponse)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get a specific order by ID")
    public ResponseEntity<?> getOrder(@PathVariable Long orderId) {
        try {
            Order order = orderService.getOrderById(orderId);
            return ResponseEntity.ok(toOrderResponse(order));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{orderId}")
    @Operation(summary = "Cancel a pending order",
               description = "Only PENDING orders can be cancelled. Since all orders are market orders (executed immediately), this is mainly for edge cases.")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId) {
        try {
            Order order = orderService.cancelOrder(orderId);
            return ResponseEntity.ok(toOrderResponse(order));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toOrderResponse(Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", order.getId());
        map.put("symbol", order.getSymbol());
        map.put("quantity", order.getQuantity());
        map.put("side", order.getSide());
        map.put("type", order.getType());
        map.put("status", order.getStatus());
        map.put("executedPrice", order.getExecutedPrice() != null ? order.getExecutedPrice() : "N/A");
        map.put("totalValue", order.getTotalValue() != null ? order.getTotalValue() : "N/A");
        map.put("rejectReason", order.getRejectReason() != null ? order.getRejectReason() : "");
        map.put("createdAt", order.getCreatedAt().toString());
        map.put("executedAt", order.getExecutedAt() != null ? order.getExecutedAt().toString() : "N/A");
        return map;
    }
}
