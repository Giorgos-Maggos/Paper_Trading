package com.trading.platform.controller;

import com.trading.platform.dto.PortfolioResponse;
import com.trading.platform.dto.TradeResponse;
import com.trading.platform.model.PortfolioActivity;
import com.trading.platform.service.BotTradingService;
import com.trading.platform.service.DividendService;
import com.trading.platform.service.MarketDataService;
import com.trading.platform.service.PortfolioService;
import com.trading.platform.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final DividendService dividendService;
    private final WatchlistService watchlistService;
    private final BotTradingService botTradingService;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@RequestParam(value = "portfolioId", required = false, defaultValue = "1") Long portfolioId,
                            Model model) {
        String username = "trader1"; // Default demo user

        List<PortfolioResponse> userPortfolios;
        try {
            userPortfolios = portfolioService.getUserPortfolios(username);
        } catch (Exception e) {
            userPortfolios = List.of();
        }

        PortfolioResponse portfolio;
        try {
            portfolio = portfolioService.getPortfolioSnapshot(portfolioId);
        } catch (Exception e) {
            portfolio = !userPortfolios.isEmpty() ? userPortfolios.get(0) : portfolioService.getPortfolioSnapshot(1L);
        }

        List<TradeResponse> recentTrades = portfolioService.getTradeHistory(portfolio.getId())
                .stream()
                .limit(10)
                .toList();

        List<PortfolioActivity> activities = portfolioService.getActivityLog(portfolio.getId())
                .stream()
                .limit(25)
                .toList();

        Map<String, Object> dividendSummary = dividendService.getDividendSummary(portfolio.getId());
        Map<String, BigDecimal> marketPrices = marketDataService.getAllPrices();
        List<Map<String, Object>> watchlist = watchlistService.getWatchlist(username);
        Set<String> watchlistSymbols = watchlistService.getWatchlistSymbols(username);
        Map<String, Object> tradingStats = portfolioService.getTradingStats(portfolio.getId());
        Map<String, Object> botStatus = botTradingService.getBotStatus(portfolio.getId());
        List<Map<String, Object>> aiSuggestions = botTradingService.getPortfolioSuggestions(portfolio.getId());

        model.addAttribute("username", username);
        model.addAttribute("portfolio", portfolio);
        model.addAttribute("userPortfolios", userPortfolios);
        model.addAttribute("recentTrades", recentTrades);
        model.addAttribute("activities", activities);
        model.addAttribute("dividendSummary", dividendSummary);
        model.addAttribute("marketPrices", marketPrices);
        model.addAttribute("symbols", MarketDataService.SUPPORTED_SYMBOLS);
        model.addAttribute("watchlist", watchlist);
        model.addAttribute("watchlistSymbols", watchlistSymbols);
        model.addAttribute("tradingStats", tradingStats);
        model.addAttribute("botStatus", botStatus);
        model.addAttribute("aiSuggestions", aiSuggestions);

        boolean isProfit = portfolio.getReturnPercent().compareTo(BigDecimal.ZERO) >= 0;
        model.addAttribute("isProfit", isProfit);

        return "dashboard";
    }
}
