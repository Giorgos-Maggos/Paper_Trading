package com.trading.platform.controller;

import com.trading.platform.model.BotStrategy;
import com.trading.platform.service.BotTradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
@Tag(name = "AI Trading Bot & Copilot", description = "Endpoints for AI Quant Bot simulation, strategies, and portfolio suggestions")
public class BotController {

    private final BotTradingService botTradingService;

    @GetMapping("/status")
    @Operation(summary = "Get AI Bot status, comparative human-vs-bot performance, and recent algorithmic trades")
    public ResponseEntity<Map<String, Object>> getBotStatus(@RequestParam(defaultValue = "1") Long portfolioId) {
        return ResponseEntity.ok(botTradingService.getBotStatus(portfolioId));
    }

    @PostMapping("/strategy")
    @Operation(summary = "Switch the active AI Bot algorithmic trading strategy")
    public ResponseEntity<Map<String, Object>> setStrategy(@RequestParam BotStrategy strategy) {
        botTradingService.setStrategy(strategy);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "strategy", strategy.name(),
                "displayName", strategy.getDisplayName()
        ));
    }

    @PostMapping("/tick")
    @Operation(summary = "Trigger an immediate AI simulation step/trade")
    public ResponseEntity<Map<String, Object>> executeTick() {
        return ResponseEntity.ok(botTradingService.executeStrategyTick());
    }

    @PostMapping("/toggle")
    @Operation(summary = "Toggle continuous auto-trading on or off")
    public ResponseEntity<Map<String, Object>> toggleAutoTrading() {
        boolean enabled = botTradingService.toggleAutoTrading();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "autoTradingEnabled", enabled
        ));
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset AI Bot portfolio capital back to initial deposit")
    public ResponseEntity<Map<String, Object>> resetBot() {
        botTradingService.resetBotPortfolio();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "AI Bot portfolio reset to $100,000"
        ));
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Get smart AI Copilot portfolio optimization recommendations")
    public ResponseEntity<List<Map<String, Object>>> getSuggestions(@RequestParam(defaultValue = "1") Long portfolioId) {
        return ResponseEntity.ok(botTradingService.getPortfolioSuggestions(portfolioId));
    }
}
