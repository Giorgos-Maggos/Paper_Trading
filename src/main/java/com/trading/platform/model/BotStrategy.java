package com.trading.platform.model;

import lombok.Getter;

import java.util.List;

/**
 * Defines available quantitative algorithmic trading strategies for the AI Trading Bot.
 */
@Getter
public enum BotStrategy {

    MOMENTUM_TREND(
            "Momentum & Trend-Follower",
            "Scans for technical breakouts, MACD momentum surges, and volume spikes on large-cap leaders.",
            List.of("NVDA", "MSFT", "AAPL", "GOOGL"),
            "Medium-High",
            "20-day MA crossover & RSI < 40 bounce"
    ),

    DIVIDEND_COMPOUNDER(
            "High-Yield Dividend Compounder",
            "Focuses strictly on dividend-paying companies and automatically reinvests (DRIP) all payouts.",
            List.of("O", "PFE", "JPM", "SPY"),
            "Low-Medium",
            "Cash flow optimization & auto-compounding"
    ),

    VALUE_DEFENSIVE(
            "Value & Defensive Indexer",
            "Disciplined dollar-cost averaging into broad index ETFs and low-beta bedrock assets.",
            List.of("SPY", "VUAA.DU", "V", "XOM"),
            "Low",
            "Systematic DCA & risk minimization"
    ),

    TECH_GROWTH(
            "Aggressive Tech Growth Scalper",
            "High-frequency swing trading targeting volatility across high-beta semiconductor and tech innovators.",
            List.of("NVDA", "TSLA", "AMZN", "GOOGL"),
            "High",
            "Volatility breakout & fast profit targets"
    );

    private final String displayName;
    private final String description;
    private final List<String> targetSymbols;
    private final String riskProfile;
    private final String algorithmTrigger;

    BotStrategy(String displayName, String description, List<String> targetSymbols,
                String riskProfile, String algorithmTrigger) {
        this.displayName = displayName;
        this.description = description;
        this.targetSymbols = targetSymbols;
        this.riskProfile = riskProfile;
        this.algorithmTrigger = algorithmTrigger;
    }
}
