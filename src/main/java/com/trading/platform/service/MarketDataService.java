package com.trading.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches real-time stock market data and search autocompletion from Yahoo Finance API.
 * Uses an in-memory cache (30-second TTL) to minimize external API calls
 * and provides a fallback mechanism if external network calls fail.
 */
@Slf4j
@Service
public class MarketDataService {

    private static final Random RANDOM = new Random();
    private static final long CACHE_TTL_SECONDS = 30; // 30s cache TTL

    private static final Map<String, BigDecimal> SEED_PRICES = Map.of(
            "AAPL",  new BigDecimal("182.50"),
            "GOOGL", new BigDecimal("175.30"),
            "MSFT",  new BigDecimal("415.80"),
            "TSLA",  new BigDecimal("248.60"),
            "AMZN",  new BigDecimal("195.40"),
            "META",  new BigDecimal("512.70"),
            "NVDA",  new BigDecimal("875.20"),
            "JPM",   new BigDecimal("198.90"),
            "BRK",   new BigDecimal("430.00"),
            "SPY",   new BigDecimal("548.00")
    );

    // Curated offline search index for fallback
    private static final List<Map<String, String>> STATIC_SEARCH_INDEX = List.of(
            Map.of("symbol", "AAPL", "name", "Apple Inc.", "exchange", "NASDAQ"),
            Map.of("symbol", "NVDA", "name", "NVIDIA Corporation", "exchange", "NASDAQ"),
            Map.of("symbol", "GOOGL", "name", "Alphabet Inc. (Google)", "exchange", "NASDAQ"),
            Map.of("symbol", "MSFT", "name", "Microsoft Corporation", "exchange", "NASDAQ"),
            Map.of("symbol", "AMZN", "name", "Amazon.com Inc.", "exchange", "NASDAQ"),
            Map.of("symbol", "TSLA", "name", "Tesla Inc.", "exchange", "NASDAQ"),
            Map.of("symbol", "META", "name", "Meta Platforms Inc. (Facebook)", "exchange", "NASDAQ"),
            Map.of("symbol", "SPY", "name", "SPDR S&P 500 ETF", "exchange", "NYSE"),
            Map.of("symbol", "JPM", "name", "JPMorgan Chase & Co.", "exchange", "NYSE"),
            Map.of("symbol", "PFE", "name", "Pfizer Inc.", "exchange", "NYSE"),
            Map.of("symbol", "AMD", "name", "Advanced Micro Devices", "exchange", "NASDAQ"),
            Map.of("symbol", "PLTR", "name", "Palantir Technologies", "exchange", "NYSE"),
            Map.of("symbol", "NFLX", "name", "Netflix Inc.", "exchange", "NASDAQ"),
            Map.of("symbol", "DIS", "name", "The Walt Disney Company", "exchange", "NYSE"),
            Map.of("symbol", "COIN", "name", "Coinbase Global Inc.", "exchange", "NASDAQ")
    );

    public static final Set<String> SUPPORTED_SYMBOLS = SEED_PRICES.keySet();

    private final Map<String, PriceCacheEntry> priceCache = new ConcurrentHashMap<>();
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MarketDataService() {
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build();
        this.objectMapper = new ObjectMapper();

        // Initialize cache with seed values
        SEED_PRICES.forEach((sym, price) ->
                priceCache.put(sym, new PriceCacheEntry(price, 0L)));
    }

    private record PriceCacheEntry(BigDecimal price, long timestamp) {}

    /**
     * Gets the price for a symbol. First checks 30s cache,
     * then attempts live fetch from Yahoo Finance, falling back to cache or seed.
     */
    public BigDecimal getPrice(String symbol) {
        String upperSymbol = symbol.toUpperCase().trim();
        long now = Instant.now().getEpochSecond();

        PriceCacheEntry entry = priceCache.get(upperSymbol);
        if (entry != null && (now - entry.timestamp() < CACHE_TTL_SECONDS)) {
            return entry.price();
        }

        // Try live fetch from Yahoo Finance
        try {
            BigDecimal livePrice = fetchPriceFromYahoo(upperSymbol);
            if (livePrice != null && livePrice.compareTo(BigDecimal.ZERO) > 0) {
                priceCache.put(upperSymbol, new PriceCacheEntry(livePrice, now));
                log.info("Live market price for {}: ${}", upperSymbol, livePrice);
                return livePrice;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch live price for {} from Yahoo Finance: {}. Using fallback.",
                    upperSymbol, e.getMessage());
        }

        // Fallback: return cached or drift price
        if (entry != null) {
            return entry.price();
        }

        BigDecimal fallbackPrice = SEED_PRICES.getOrDefault(
                upperSymbol,
                BigDecimal.valueOf(10 + RANDOM.nextDouble() * 490).setScale(2, RoundingMode.HALF_UP)
        );
        priceCache.put(upperSymbol, new PriceCacheEntry(fallbackPrice, now));
        return fallbackPrice;
    }

    /**
     * Searches Yahoo Finance API for tickers and company names matching query.
     */
    public List<Map<String, String>> searchSymbols(String query) {
        if (query == null || query.trim().length() < 1) {
            return STATIC_SEARCH_INDEX;
        }

        String q = query.trim();
        try {
            String url = "https://query1.finance.yahoo.com/v1/finance/search?q=" + q + "&quotesCount=8&newsCount=0";
            String json = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (json != null && !json.isBlank()) {
                JsonNode root = objectMapper.readTree(json);
                JsonNode quotesNode = root.path("quotes");
                if (quotesNode.isArray() && !quotesNode.isEmpty()) {
                    List<Map<String, String>> results = new ArrayList<>();
                    for (JsonNode quote : quotesNode) {
                        String symbol = quote.path("symbol").asText("");
                        String shortName = quote.path("shortname").asText(quote.path("longname").asText(symbol));
                        String exchange = quote.path("exchDisp").asText(quote.path("exchange").asText(""));

                        if (!symbol.isBlank() && !symbol.contains("=")) {
                            results.add(Map.of(
                                    "symbol", symbol,
                                    "name", shortName.isBlank() ? symbol : shortName,
                                    "exchange", exchange
                            ));
                        }
                    }
                    if (!results.isEmpty()) {
                        return results;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Yahoo search API failed for query '{}': {}", q, e.getMessage());
        }

        // Fallback: filter static index by ticker or company name
        String lowerQuery = q.toLowerCase();
        return STATIC_SEARCH_INDEX.stream()
                .filter(item -> item.get("symbol").toLowerCase().contains(lowerQuery) ||
                        item.get("name").toLowerCase().contains(lowerQuery))
                .toList();
    }

    /**
     * Fetches real market price from Yahoo Finance v8 chart API.
     */
    private BigDecimal fetchPriceFromYahoo(String symbol) throws Exception {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol + "?interval=1m&range=1d";

        String jsonResponse = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

        if (jsonResponse == null || jsonResponse.isBlank()) {
            return null;
        }

        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode resultNode = root.path("chart").path("result");

        if (resultNode.isArray() && !resultNode.isEmpty()) {
            JsonNode meta = resultNode.get(0).path("meta");
            if (meta.has("regularMarketPrice")) {
                double priceDouble = meta.path("regularMarketPrice").asDouble();
                if (priceDouble > 0) {
                    return BigDecimal.valueOf(priceDouble).setScale(2, RoundingMode.HALF_UP);
                }
            }
            if (meta.has("previousClose")) {
                double prevClose = meta.path("previousClose").asDouble();
                if (prevClose > 0) {
                    return BigDecimal.valueOf(prevClose).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }
        return null;
    }

    /**
     * Returns current market prices for all pre-loaded symbols.
     */
    public Map<String, BigDecimal> getAllPrices() {
        SUPPORTED_SYMBOLS.forEach(this::getPrice);
        Map<String, BigDecimal> result = new ConcurrentHashMap<>();
        priceCache.forEach((k, v) -> result.put(k, v.price()));
        return Map.copyOf(result);
    }

    public boolean isSymbolSupported(String symbol) {
        return true; // Supports any valid ticker symbol
    }

    /**
     * Fetches historical chart data from Yahoo Finance for the given symbol and range.
     * Returns a map with "timestamps" (epoch seconds), "prices" (close prices),
     * "previousClose", and metadata.
     *
     * Supported ranges: 1d, 5d, 1mo, 3mo, 6mo, 1y, 3y, 5y, max
     */
    public Map<String, Object> getChartData(String symbol, String range) {
        String upperSymbol = symbol.toUpperCase().trim();

        // Map range to appropriate interval
        String interval = switch (range) {
            case "1d" -> "5m";
            case "5d" -> "15m";
            case "1mo" -> "1h";
            case "3mo", "6mo" -> "1d";
            case "1y" -> "1d";
            case "3y", "5y", "max" -> "1wk";
            default -> "1d";
        };

        try {
            String url = String.format(
                    "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=%s&range=%s",
                    upperSymbol, interval, range);

            String jsonResponse = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (jsonResponse == null || jsonResponse.isBlank()) {
                return Map.of("error", "No data returned from Yahoo Finance");
            }

            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode resultNode = root.path("chart").path("result");

            if (!resultNode.isArray() || resultNode.isEmpty()) {
                return Map.of("error", "No chart data available for " + upperSymbol);
            }

            JsonNode result = resultNode.get(0);
            JsonNode meta = result.path("meta");
            JsonNode timestampsNode = result.path("timestamp");
            JsonNode indicatorsNode = result.path("indicators").path("quote");

            if (!timestampsNode.isArray() || timestampsNode.isEmpty()
                    || !indicatorsNode.isArray() || indicatorsNode.isEmpty()) {
                return Map.of("error", "Incomplete chart data for " + upperSymbol);
            }

            JsonNode closesNode = indicatorsNode.get(0).path("close");

            List<Long> timestamps = new ArrayList<>();
            List<Double> prices = new ArrayList<>();

            for (int i = 0; i < timestampsNode.size(); i++) {
                long ts = timestampsNode.get(i).asLong();
                JsonNode closeVal = closesNode.get(i);
                if (closeVal != null && !closeVal.isNull()) {
                    timestamps.add(ts);
                    prices.add(Math.round(closeVal.asDouble() * 100.0) / 100.0);
                }
            }

            double previousClose = meta.path("chartPreviousClose").asDouble(
                    meta.path("previousClose").asDouble(0));
            double currentPrice = meta.path("regularMarketPrice").asDouble(0);
            String currency = meta.path("currency").asText("USD");

            Map<String, Object> chartData = new java.util.LinkedHashMap<>();
            chartData.put("symbol", upperSymbol);
            chartData.put("range", range);
            chartData.put("interval", interval);
            chartData.put("currency", currency);
            chartData.put("previousClose", Math.round(previousClose * 100.0) / 100.0);
            chartData.put("currentPrice", Math.round(currentPrice * 100.0) / 100.0);
            chartData.put("timestamps", timestamps);
            chartData.put("prices", prices);
            chartData.put("dataPoints", prices.size());

            return chartData;

        } catch (Exception e) {
            log.warn("Failed to fetch chart data for {} (range={}): {}", upperSymbol, range, e.getMessage());
            return Map.of("error", "Failed to fetch chart data: " + e.getMessage());
        }
    }
}
