package com.trading.platform.service;

import com.trading.platform.model.Portfolio;
import com.trading.platform.model.PortfolioActivity;
import com.trading.platform.model.Position;
import com.trading.platform.repository.PortfolioActivityRepository;
import com.trading.platform.repository.PortfolioRepository;
import com.trading.platform.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service managing real-time corporate dividend schedules, payouts, and next distribution projections.
 * - Accumulating ETFs (e.g. VUAA, VWCE): Dividends are automatically reinvested into fund NAV (0 cash distribution).
 * - Distributing Payers (e.g. O, MAIN, PFE, JPM, SPY, AAPL): Cash dividends credited automatically on calendar pay dates.
 * - Deduplication: Payouts are keyed by calendar period (e.g. O_2026_08) and executed only once per period.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final PortfolioActivityRepository activityRepository;
    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;

    public enum DividendTreatment {
        ACCUMULATING,          // e.g. VUAA — Reinvested into fund NAV, 0 cash payout
        DISTRIBUTING_MONTHLY,  // e.g. O (Realty Income) — Cash credited monthly
        DISTRIBUTING_QUARTERLY,// e.g. PFE, JPM, SPY, AAPL — Cash credited quarterly
        NONE                   // e.g. AMZN, TSLA — No dividend
    }

    public record AssetDividendInfo(
            String symbol,
            String name,
            DividendTreatment treatment,
            BigDecimal annualPerShare,
            int payoutDayOfMonth,
            List<Integer> payoutMonths,
            String description
    ) {}

    public record NextDividendDetails(
            String symbol,
            String name,
            DividendTreatment treatment,
            String statusLabel,
            int quantity,
            BigDecimal currentPrice,
            BigDecimal payoutPerShare,
            BigDecimal annualPerShare,
            LocalDate nextPayDate,
            LocalDate nextExDivDate,
            long daysUntilPayDate,
            BigDecimal estimatedPayout,
            BigDecimal projectedAnnualPayout,
            BigDecimal dividendYieldPercent,
            String frequency,
            String formattedPayDate,
            String formattedExDivDate,
            String explanation
    ) {}

    private static final List<Integer> ALL_MONTHS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    private static final List<Integer> Q_MAR_JUN_SEP_DEC = List.of(3, 6, 9, 12);
    private static final List<Integer> Q_JAN_APR_JUL_OCT = List.of(1, 4, 7, 10);
    private static final List<Integer> Q_FEB_MAY_AUG_NOV = List.of(2, 5, 8, 11);

    // Curated real-world corporate dividend schedules
    private static final Map<String, AssetDividendInfo> DIVIDEND_REGISTRY = Map.ofEntries(
            // ── Accumulating UCITS ETFs (Internal NAV reinvestment, 0 cash distribution) ──
            Map.entry("VUAA", new AssetDividendInfo("VUAA", "Vanguard S&P 500 UCITS ETF (Acc)", DividendTreatment.ACCUMULATING, BigDecimal.ZERO, 0, List.of(), "Accumulating ETF — All dividends are automatically reinvested inside the fund NAV without cash distributions")),
            Map.entry("VUAA.DU", new AssetDividendInfo("VUAA.DU", "Vanguard S&P 500 UCITS ETF (Acc)", DividendTreatment.ACCUMULATING, BigDecimal.ZERO, 0, List.of(), "Accumulating ETF — All dividends are automatically reinvested inside the fund NAV without cash distributions")),
            Map.entry("VUAA.DE", new AssetDividendInfo("VUAA.DE", "Vanguard S&P 500 UCITS ETF (Acc)", DividendTreatment.ACCUMULATING, BigDecimal.ZERO, 0, List.of(), "Accumulating ETF — All dividends are automatically reinvested inside the fund NAV without cash distributions")),
            Map.entry("VUAA.L", new AssetDividendInfo("VUAA.L", "Vanguard S&P 500 UCITS ETF (Acc)", DividendTreatment.ACCUMULATING, BigDecimal.ZERO, 0, List.of(), "Accumulating ETF — All dividends are automatically reinvested inside the fund NAV without cash distributions")),
            Map.entry("VWCE", new AssetDividendInfo("VWCE", "Vanguard FTSE All-World UCITS ETF (Acc)", DividendTreatment.ACCUMULATING, BigDecimal.ZERO, 0, List.of(), "Accumulating ETF — All dividends are automatically reinvested inside the fund NAV without cash distributions")),
            Map.entry("CSPX", new AssetDividendInfo("CSPX", "iShares Core S&P 500 UCITS ETF (Acc)", DividendTreatment.ACCUMULATING, BigDecimal.ZERO, 0, List.of(), "Accumulating ETF — All dividends are automatically reinvested inside the fund NAV without cash distributions")),

            // ── Monthly Distributing Dividend Payers ──
            Map.entry("O", new AssetDividendInfo("O", "Realty Income Corporation", DividendTreatment.DISTRIBUTING_MONTHLY, new BigDecimal("3.15"), 15, ALL_MONTHS, "Monthly Dividend Payer — The Monthly Dividend Company®")),
            Map.entry("MAIN", new AssetDividendInfo("MAIN", "Main Street Capital", DividendTreatment.DISTRIBUTING_MONTHLY, new BigDecimal("2.94"), 15, ALL_MONTHS, "Monthly Dividend Payer — BDC Income")),

            // ── Quarterly Distributing Dividend Payers ──
            Map.entry("PFE", new AssetDividendInfo("PFE", "Pfizer Inc.", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("1.68"), 5, Q_MAR_JUN_SEP_DEC, "Quarterly Healthcare Dividend Payer")),
            Map.entry("JPM", new AssetDividendInfo("JPM", "JPMorgan Chase & Co.", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("4.60"), 30, Q_JAN_APR_JUL_OCT, "Quarterly Banking Dividend Payer")),
            Map.entry("SPY", new AssetDividendInfo("SPY", "SPDR S&P 500 ETF Trust", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("6.80"), 30, Q_JAN_APR_JUL_OCT, "Quarterly Distributing S&P 500 Index ETF")),
            Map.entry("XOM", new AssetDividendInfo("XOM", "Exxon Mobil Corporation", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("3.80"), 10, Q_MAR_JUN_SEP_DEC, "Quarterly Energy Dividend Aristocrat")),
            Map.entry("AAPL", new AssetDividendInfo("AAPL", "Apple Inc.", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("1.00"), 15, Q_FEB_MAY_AUG_NOV, "Quarterly Tech Dividend Payer")),
            Map.entry("MSFT", new AssetDividendInfo("MSFT", "Microsoft Corporation", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("3.00"), 12, Q_MAR_JUN_SEP_DEC, "Quarterly Tech Dividend Payer")),
            Map.entry("V", new AssetDividendInfo("V", "Visa Inc.", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("2.08"), 1, Q_MAR_JUN_SEP_DEC, "Quarterly Payment Tech Dividend Payer")),
            Map.entry("NVDA", new AssetDividendInfo("NVDA", "NVIDIA Corporation", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("0.40"), 27, Q_MAR_JUN_SEP_DEC, "Quarterly Semiconductor Dividend Payer")),

            Map.entry("GOOGL", new AssetDividendInfo("GOOGL", "Alphabet Inc. Class A", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("0.88"), 17, Q_MAR_JUN_SEP_DEC, "Quarterly Dividend Payer ($0.22/quarter, ~0.26% yield)")),
            Map.entry("GOOG", new AssetDividendInfo("GOOG", "Alphabet Inc. Class C", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("0.88"), 17, Q_MAR_JUN_SEP_DEC, "Quarterly Dividend Payer ($0.22/quarter, ~0.26% yield)")),
            Map.entry("META", new AssetDividendInfo("META", "Meta Platforms Inc.", DividendTreatment.DISTRIBUTING_QUARTERLY, new BigDecimal("2.00"), 26, Q_MAR_JUN_SEP_DEC, "Quarterly Dividend Payer ($0.50/quarter, ~0.35% yield)")),

            // ── Non-Dividend / Growth Stocks ──
            Map.entry("AMZN", new AssetDividendInfo("AMZN", "Amazon.com Inc.", DividendTreatment.NONE, BigDecimal.ZERO, 0, List.of(), "Growth Asset — Reinvests all cashflows, no dividend")),
            Map.entry("TSLA", new AssetDividendInfo("TSLA", "Tesla Inc.", DividendTreatment.NONE, BigDecimal.ZERO, 0, List.of(), "Growth Asset — No dividend")),
            Map.entry("PLTR", new AssetDividendInfo("PLTR", "Palantir Technologies", DividendTreatment.NONE, BigDecimal.ZERO, 0, List.of(), "Growth Asset — No dividend")),
            Map.entry("COIN", new AssetDividendInfo("COIN", "Coinbase Global", DividendTreatment.NONE, BigDecimal.ZERO, 0, List.of(), "Growth Asset — No dividend")),
            Map.entry("NFLX", new AssetDividendInfo("NFLX", "Netflix Inc.", DividendTreatment.NONE, BigDecimal.ZERO, 0, List.of(), "Growth Asset — No dividend")),
            Map.entry("AMD", new AssetDividendInfo("AMD", "Advanced Micro Devices", DividendTreatment.NONE, BigDecimal.ZERO, 0, List.of(), "Growth Asset — No dividend"))
    );

    /**
     * Resolves dividend information for any symbol.
     */
    public AssetDividendInfo getAssetDividendInfo(String symbol) {
        String sym = symbol.toUpperCase().trim();
        if (DIVIDEND_REGISTRY.containsKey(sym)) {
            return DIVIDEND_REGISTRY.get(sym);
        }

        // Detect accumulating ETF patterns in symbol name (e.g. VUAA.*, *ACC*)
        if (sym.startsWith("VUAA") || sym.startsWith("VWCE") || sym.startsWith("CSPX") || sym.contains("ACC")) {
            return new AssetDividendInfo(sym, sym, DividendTreatment.ACCUMULATING, BigDecimal.ZERO, 0, List.of(),
                    "Accumulating ETF — All dividends are automatically reinvested into fund NAV");
        }

        // Default: standard modest quarterly dividend payer (~1.2% yield)
        BigDecimal price = marketDataService.getPrice(sym);
        BigDecimal annual = price.multiply(new BigDecimal("0.012")).setScale(2, RoundingMode.HALF_UP);
        return new AssetDividendInfo(sym, sym, DividendTreatment.DISTRIBUTING_QUARTERLY, annual, 15, Q_MAR_JUN_SEP_DEC, "Quarterly Dividend Payer (~1.2% yield)");
    }

    /**
     * Calculates next upcoming dividend distribution dates, amounts, and yield for a position.
     */
    public NextDividendDetails getNextDividendDetails(String symbol, int quantity) {
        AssetDividendInfo info = getAssetDividendInfo(symbol);
        BigDecimal price = marketDataService.getPrice(symbol);
        LocalDate today = LocalDate.now();

        if (info.treatment() == DividendTreatment.ACCUMULATING) {
            return new NextDividendDetails(
                    info.symbol(),
                    info.name(),
                    info.treatment(),
                    "Accumulating (Reinvested in NAV)",
                    quantity,
                    price,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    null,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "Accumulating (NAV Reinvested)",
                    "Continuous Reinvestment",
                    "—",
                    "This is an Accumulating UCITS ETF. All dividends generated by underlying assets are automatically reinvested internally into the fund NAV by the fund manager. No cash distribution is deposited to cash balance, but your share price reflects full compounding."
            );
        }

        if (info.treatment() == DividendTreatment.NONE) {
            return new NextDividendDetails(
                    info.symbol(),
                    info.name(),
                    info.treatment(),
                    "Growth Asset (No Dividend)",
                    quantity,
                    price,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    null,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "None",
                    "—",
                    "—",
                    "This company does not pay dividends. Capital return is realized through stock price appreciation."
            );
        }

        // Distributing stock: find next pay date
        LocalDate nextPayDate = calculateNextPayDate(info, today);
        LocalDate nextExDivDate = nextPayDate.minusDays(14);
        long daysUntil = ChronoUnit.DAYS.between(today, nextPayDate);
        if (daysUntil < 0) daysUntil = 0;

        BigDecimal payoutPerShare = getPayoutPerShare(info);
        BigDecimal estimatedPayout = payoutPerShare.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal projectedAnnual = info.annualPerShare().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal yieldPercent = price.compareTo(BigDecimal.ZERO) > 0
                ? info.annualPerShare().divide(price, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String frequencyStr = info.treatment() == DividendTreatment.DISTRIBUTING_MONTHLY ? "Monthly" : "Quarterly";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US);

        String explanation = String.format("Pays %s cash dividends directly into your portfolio balance. Holding %d shares will generate $%.2f on %s.",
                frequencyStr.toLowerCase(), quantity, estimatedPayout, nextPayDate.format(fmt));

        return new NextDividendDetails(
                info.symbol(),
                info.name(),
                info.treatment(),
                frequencyStr + " Cash Payout",
                quantity,
                price,
                payoutPerShare,
                info.annualPerShare(),
                nextPayDate,
                nextExDivDate,
                daysUntil,
                estimatedPayout,
                projectedAnnual,
                yieldPercent,
                frequencyStr,
                nextPayDate.format(fmt),
                nextExDivDate.format(fmt),
                explanation
        );
    }

    private LocalDate calculateNextPayDate(AssetDividendInfo info, LocalDate fromDate) {
        int day = info.payoutDayOfMonth() > 0 ? info.payoutDayOfMonth() : 15;
        List<Integer> months = info.payoutMonths();
        if (months == null || months.isEmpty()) months = ALL_MONTHS;

        // Check if there is an upcoming pay date in the current year
        for (int m : months) {
            LocalDate candidate = LocalDate.of(fromDate.getYear(), m, Math.min(day, LocalDate.of(fromDate.getYear(), m, 1).lengthOfMonth()));
            if (!candidate.isBefore(fromDate)) {
                return candidate;
            }
        }

        // Otherwise wrap to the first payout month of next year
        int firstMonth = months.get(0);
        int nextYear = fromDate.getYear() + 1;
        return LocalDate.of(nextYear, firstMonth, Math.min(day, LocalDate.of(nextYear, firstMonth, 1).lengthOfMonth()));
    }

    /**
     * Calculates the per-payout distribution amount per share.
     */
    public BigDecimal getPayoutPerShare(AssetDividendInfo info) {
        if (info.treatment() == DividendTreatment.ACCUMULATING || info.treatment() == DividendTreatment.NONE) {
            return BigDecimal.ZERO;
        }
        if (info.treatment() == DividendTreatment.DISTRIBUTING_MONTHLY) {
            return info.annualPerShare().divide(new BigDecimal("12"), 4, RoundingMode.HALF_UP);
        }
        // Quarterly
        return info.annualPerShare().divide(new BigDecimal("4"), 4, RoundingMode.HALF_UP);
    }

    /**
     * Generates a unique deduplication period key for calendar payouts (e.g. O_2026_08 or PFE_2026_Q3).
     */
    public String getPeriodKey(AssetDividendInfo info, LocalDate date) {
        if (info.treatment() == DividendTreatment.DISTRIBUTING_MONTHLY) {
            return String.format("%s_%d_%02d", info.symbol(), date.getYear(), date.getMonthValue());
        } else if (info.treatment() == DividendTreatment.DISTRIBUTING_QUARTERLY) {
            int quarter = (date.getMonthValue() - 1) / 3 + 1;
            return String.format("%s_%d_Q%d", info.symbol(), date.getYear(), quarter);
        }
        return String.format("%s_%d", info.symbol(), date.getYear());
    }

    @jakarta.annotation.PostConstruct
    @Transactional
    public void cleanupOldTestDividends() {
        try {
            List<PortfolioActivity> divs = activityRepository.findAll().stream()
                    .filter(a -> a.getActivityType() == PortfolioActivity.ActivityType.DIVIDEND)
                    .toList();
            Set<String> seen = new HashSet<>();
            List<PortfolioActivity> toDelete = new ArrayList<>();
            for (PortfolioActivity act : divs) {
                if (act.getDescription() != null && !act.getDescription().contains("[KEY:")) {
                    String sym = act.getSymbol() != null ? act.getSymbol() : "UNKNOWN";
                    Long pid = act.getPortfolio() != null ? act.getPortfolio().getId() : 0L;
                    String dedupeKey = pid + "_" + sym;
                    if (seen.contains(dedupeKey)) {
                        toDelete.add(act);
                    } else {
                        seen.add(dedupeKey);
                    }
                }
            }
            if (!toDelete.isEmpty()) {
                activityRepository.deleteAll(toDelete);
                log.info("Cleaned up {} duplicate test dividend records", toDelete.size());
            }
        } catch (Exception e) {
            log.warn("Dividend cleanup notice: {}", e.getMessage());
        }
    }

    /**
     * Real-time automated background scheduler that processes due calendar dividends.
     * Runs every 5 minutes and only executes payouts that have reached their exact calendar date
     * and have not already been credited for the current period.
     */
    @Scheduled(fixedRate = 300000, initialDelay = 10000)
    public void processRealTimeDividends() {
        try {
            List<Portfolio> portfolios = portfolioRepository.findAll();
            for (Portfolio portfolio : portfolios) {
                distributeDueDividendsForPortfolio(portfolio.getId(), false);
            }
        } catch (Exception e) {
            log.warn("Error in real-time dividend scheduler: {}", e.getMessage());
        }
    }

    /**
     * Evaluates open positions and deposits due dividend cash into the portfolio if on/past pay date
     * and not already credited for this calendar period.
     */
    @Transactional
    public Map<String, Object> distributeDueDividendsForPortfolio(Long portfolioId, boolean forceSimulation) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return Map.of("error", "Portfolio not found");

        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty()) {
            return Map.of("totalCredited", BigDecimal.ZERO, "payouts", List.of());
        }

        BigDecimal totalCredited = BigDecimal.ZERO;
        List<Map<String, Object>> payoutDetails = new ArrayList<>();
        BigDecimal currentCash = portfolio.getCashBalance();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // Load all existing dividend activities for deduplication
        List<PortfolioActivity> existingDivs = activityRepository
                .findByPortfolioIdAndActivityTypeOrderByCreatedAtDesc(portfolioId, PortfolioActivity.ActivityType.DIVIDEND);

        Set<String> alreadyPaidPeriodKeys = new HashSet<>();
        for (PortfolioActivity act : existingDivs) {
            if (act.getDescription() != null && act.getDescription().contains("[KEY:")) {
                int start = act.getDescription().indexOf("[KEY:") + 5;
                int end = act.getDescription().indexOf("]", start);
                if (start > 4 && end > start) {
                    alreadyPaidPeriodKeys.add(act.getDescription().substring(start, end));
                }
            }
        }

        for (Position pos : positions) {
            if (pos.getQuantity() <= 0) continue;

            AssetDividendInfo info = getAssetDividendInfo(pos.getSymbol());
            // Accumulating and Non-paying assets DO NOT pay cash dividends
            if (info.treatment() == DividendTreatment.ACCUMULATING || info.treatment() == DividendTreatment.NONE) {
                continue;
            }

            String periodKey = getPeriodKey(info, today);

            // Deduplication: Has this portfolio already received this period's dividend?
            if (!forceSimulation && alreadyPaidPeriodKeys.contains(periodKey)) {
                continue;
            }

            // Real-time Calendar check: Is today on or past the scheduled payout date for this period?
            if (!forceSimulation) {
                boolean isPayoutMonth = info.payoutMonths().contains(today.getMonthValue());
                boolean isPastPayDay = today.getDayOfMonth() >= info.payoutDayOfMonth();
                if (!isPayoutMonth || !isPastPayDay) {
                    // Not due yet according to real market calendar
                    continue;
                }
            }

            BigDecimal payoutPerShare = getPayoutPerShare(info);
            if (payoutPerShare.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal positionDividend = payoutPerShare
                    .multiply(BigDecimal.valueOf(pos.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            if (positionDividend.compareTo(new BigDecimal("0.01")) < 0) continue;

            totalCredited = totalCredited.add(positionDividend);
            currentCash = currentCash.add(positionDividend);

            String frequencyName = info.treatment() == DividendTreatment.DISTRIBUTING_MONTHLY ? "Monthly" : "Quarterly";
            String periodLabel = today.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US));
            String description = String.format("%s %s Dividend: %d shares of %s @ $%.4f/share (+$%.2f) [KEY:%s]",
                    periodLabel, frequencyName, pos.getQuantity(), pos.getSymbol(), payoutPerShare, positionDividend, periodKey);

            // Save activity ledger entry with period key tag
            activityRepository.save(PortfolioActivity.builder()
                    .portfolio(portfolio)
                    .activityType(PortfolioActivity.ActivityType.DIVIDEND)
                    .symbol(pos.getSymbol())
                    .quantity(pos.getQuantity())
                    .unitPrice(payoutPerShare)
                    .amount(positionDividend)
                    .balanceAfter(currentCash)
                    .description(description)
                    .createdAt(now)
                    .build());

            alreadyPaidPeriodKeys.add(periodKey);

            payoutDetails.add(Map.of(
                    "symbol", pos.getSymbol(),
                    "shares", pos.getQuantity(),
                    "dividendPerShare", payoutPerShare,
                    "payoutAmount", positionDividend,
                    "frequency", frequencyName,
                    "periodKey", periodKey
            ));

            log.info("Auto-credited real dividend to portfolio #{}: {} (+$%)", portfolioId, description, positionDividend);
        }

        if (totalCredited.compareTo(BigDecimal.ZERO) > 0) {
            portfolio.setCashBalance(currentCash);
            portfolioRepository.save(portfolio);
            portfolioService.recordSnapshot(portfolioId);
        }

        return Map.of(
                "portfolioId", portfolioId,
                "totalCredited", totalCredited,
                "newCashBalance", currentCash,
                "payoutsCount", payoutDetails.size(),
                "payouts", payoutDetails
        );
    }

    /**
     * Returns comprehensive dividend breakdown and next payout projections for all owned positions.
     */
    public Map<String, Object> getDividendSummary(Long portfolioId) {
        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);
        BigDecimal projectedAnnual = BigDecimal.ZERO;
        BigDecimal totalDividendsReceived = activityRepository.sumDividendsByPortfolioId(portfolioId);
        List<Map<String, Object>> holdings = new ArrayList<>();

        for (Position pos : positions) {
            if (pos.getQuantity() <= 0) continue;

            NextDividendDetails nextDetails = getNextDividendDetails(pos.getSymbol(), pos.getQuantity());

            if (nextDetails.treatment() == DividendTreatment.DISTRIBUTING_MONTHLY || nextDetails.treatment() == DividendTreatment.DISTRIBUTING_QUARTERLY) {
                projectedAnnual = projectedAnnual.add(nextDetails.projectedAnnualPayout());
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("symbol", pos.getSymbol());
            item.put("name", nextDetails.name());
            item.put("shares", pos.getQuantity());
            item.put("treatment", nextDetails.treatment().name());
            item.put("statusLabel", nextDetails.statusLabel());
            item.put("annualPerShare", nextDetails.annualPerShare());
            item.put("payoutPerShare", nextDetails.payoutPerShare());
            item.put("estimatedNextPayout", nextDetails.estimatedPayout());
            item.put("projectedAnnual", nextDetails.projectedAnnualPayout());
            item.put("yieldPercent", nextDetails.dividendYieldPercent());
            item.put("frequency", nextDetails.frequency());
            item.put("formattedPayDate", nextDetails.formattedPayDate());
            item.put("formattedExDivDate", nextDetails.formattedExDivDate());
            item.put("daysUntilPayDate", nextDetails.daysUntilPayDate());
            item.put("explanation", nextDetails.explanation());
            item.put("isCashPayer", nextDetails.treatment() != DividendTreatment.ACCUMULATING && nextDetails.treatment() != DividendTreatment.NONE);
            holdings.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("portfolioId", portfolioId);
        result.put("projectedAnnualIncome", projectedAnnual);
        result.put("projectedMonthlyIncome", projectedAnnual.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP));
        result.put("totalDividendsReceived", totalDividendsReceived != null ? totalDividendsReceived : BigDecimal.ZERO);
        result.put("holdings", holdings);
        return result;
    }
}
