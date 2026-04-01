package net.uku3lig.tiertagger.model;

import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonObject;
import net.uku3lig.tiertagger.TierCache;
import net.uku3lig.tiertagger.TierTagger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public record PlayerInfo(String uuid, String name, Map<String, Ranking> rankings, String region, int points,
                         int overall, List<Badge> badges, @SerializedName("combat_master") boolean combatMaster) {
    private static final String VOID_TIERLIST_URL = "https://raw.githubusercontent.com/Voidtierlist/Voidtierlist.github.io/main/player_points.json";
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private static volatile long lastFetchEpochMillis = 0L;
    private static volatile CompletableFuture<Map<String, Map<String, Ranking>>> cachedRankingsFuture =
            CompletableFuture.completedFuture(Collections.emptyMap());

    public record Ranking(int tier, int pos, @Nullable @SerializedName("peak_tier") Integer peakTier,
                          @Nullable @SerializedName("peak_pos") Integer peakPos, long attained,
                          boolean retired) {

        /**
         * Lower is better.
         */
        public int comparableTier() {
            return tier * 2 + pos;
        }

        /**
         * Lower is better.
         */
        public int comparablePeak() {
            if (peakTier == null || peakPos == null) {
                return Integer.MAX_VALUE;
            } else {
                return peakTier * 2 + peakPos;
            }
        }

        public NamedRanking asNamed(GameMode mode) {
            return new NamedRanking(mode, this);
        }
    }

    public record NamedRanking(@Nullable GameMode mode, Ranking ranking) {
    }

    public record Badge(String title, String desc) {
    }

    private static final Map<String, Integer> REGION_COLORS = Map.of(
            "NA", 0xff6a6e,
            "EU", 0x6aff6e,
            "SA", 0xff9900,
            "AU", 0xf6b26b,
            "ME", 0xffd966,
            "AS", 0xc27ba0,
            "AF", 0x674ea7
    );

    public static CompletableFuture<PlayerInfo> get(HttpClient client, UUID uuid) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/v2/profile/" + uuid;
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(s -> TierTagger.GSON.fromJson(s, PlayerInfo.class))
                .whenComplete((_, t) -> {
                    if (t != null) TierTagger.getLogger().warn("Error getting player info ({})", uuid, t);
                });
    }

    public static CompletableFuture<Map<String, Ranking>> getRankings(HttpClient client, String username) {
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        String normalized = normalizeUsername(username);
        return getUsernameToRankings(client)
                .thenApply(rankings -> rankings.getOrDefault(normalized, Collections.emptyMap()))
                .exceptionally(t -> {
                    TierTagger.getLogger().warn("Error getting player rankings ({})", username, t);
                    return Collections.emptyMap();
                });
    }

    public static CompletableFuture<PlayerInfo> search(HttpClient client, String query) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/v2/profile/by-name/" + query;
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(s -> TierTagger.GSON.fromJson(s, PlayerInfo.class))
                .whenComplete((_, t) -> {
                    if (t != null) TierTagger.getLogger().warn("Error searching player {}", query, t);
                });
    }

    private static CompletableFuture<Map<String, Map<String, Ranking>>> getUsernameToRankings(HttpClient client) {
        long now = System.currentTimeMillis();
        CompletableFuture<Map<String, Map<String, Ranking>>> current = cachedRankingsFuture;
        if (now - lastFetchEpochMillis < CACHE_TTL_MILLIS && !current.isCompletedExceptionally()) {
            return current;
        }

        synchronized (PlayerInfo.class) {
            now = System.currentTimeMillis();
            current = cachedRankingsFuture;
            if (now - lastFetchEpochMillis < CACHE_TTL_MILLIS && !current.isCompletedExceptionally()) {
                return current;
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(VOID_TIERLIST_URL)).GET().build();
            CompletableFuture<Map<String, Map<String, Ranking>>> previous = current;
            cachedRankingsFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(PlayerInfo::parseVoidTierlistPayload)
                    .whenComplete((_, t) -> {
                        if (t != null) {
                            TierTagger.getLogger().warn("Error fetching Void Tierlist dataset", t);
                        } else {
                            lastFetchEpochMillis = System.currentTimeMillis();
                        }
                    })
                    .exceptionally(t -> {
                        TierTagger.getLogger().warn("Using cached/empty rankings after fetch failure", t);
                        if (!previous.isCompletedExceptionally()) {
                            try {
                                return previous.getNow(Collections.emptyMap());
                            } catch (CompletionException e) {
                                return Collections.emptyMap();
                            }
                        }

                        return Collections.emptyMap();
                    });
            return cachedRankingsFuture;
        }
    }

    private static Map<String, Map<String, Ranking>> parseVoidTierlistPayload(String payload) {
        JsonObject root = TierTagger.GSON.fromJson(payload, JsonObject.class);
        if (root == null) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, Ranking>> rankingsByUsername = new HashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
            JsonObject playerObj = entry.getValue().getAsJsonObject();
            if (playerObj == null || !playerObj.has("mc_username")) continue;

            String username = normalizeUsername(playerObj.get("mc_username").getAsString());
            if (username.isBlank()) continue;

            JsonObject gamemodes = playerObj.has("gamemodes") ? playerObj.getAsJsonObject("gamemodes") : null;
            if (gamemodes == null) {
                rankingsByUsername.put(username, Collections.emptyMap());
                continue;
            }

            Map<String, Ranking> rankings = new HashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> modeEntry : gamemodes.entrySet()) {
                JsonObject modeObj = modeEntry.getValue().getAsJsonObject();
                if (modeObj == null || !modeObj.has("tier")) continue;

                String modeId = normalizeGamemodeId(modeEntry.getKey());
                Ranking ranking = parseRankingTier(modeObj.get("tier").getAsString());
                if (ranking != null) {
                    rankings.put(modeId, ranking);
                }
            }

            rankingsByUsername.put(username, rankings);
        }

        return rankingsByUsername;
    }

    private static Ranking parseRankingTier(String tierValue) {
        if (tierValue == null) return null;
        String normalized = tierValue.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.charAt(1) != 'T') return null;

        char prefix = normalized.charAt(0);
        int pos = switch (prefix) {
            case 'H' -> 0;
            case 'L' -> 1;
            default -> -1;
        };
        if (pos == -1) return null;

        try {
            int tier = Integer.parseInt(normalized.substring(2));
            return new Ranking(tier, pos, null, null, 0L, false);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String normalizeGamemodeId(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("nethpot")) {
            return "nethop";
        }

        return normalized;
    }

    private static String normalizeUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public int getRegionColor() {
        return REGION_COLORS.getOrDefault(this.region.toUpperCase(Locale.ROOT), 0xffffff);
    }

    public static Optional<NamedRanking> getHighestRanking(Map<String, Ranking> rankings) {
        return rankings.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .min(Comparator.comparingInt(e -> e.getValue().comparableTier()))
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(e.getKey())));
    }

    @Getter
    @AllArgsConstructor
    public enum PointInfo {
        COMBAT_GRANDMASTER("Combat Grandmaster", 0xE6C622, 0xFDE047),
        COMBAT_MASTER("Combat Master", 0xFBB03B, 0xFFD13A),
        COMBAT_ACE("Combat Ace", 0xCD285C, 0xD65474),
        COMBAT_SPECIALIST("Combat Specialist", 0xAD78D8, 0xC7A3E8),
        COMBAT_CADET("Combat Cadet", 0x9291D9, 0xADACE2),
        COMBAT_NOVICE("Combat Novice", 0x9291D9, 0xFFFFFF),
        ROOKIE("Rookie", 0x6C7178, 0x8B979C),
        UNRANKED("Unranked", 0xFFFFFF, 0xFFFFFF);

        private final String title;
        private final int color;
        private final int accentColor;
    }

    public PointInfo getPointInfo() {
        if (this.points >= 400) {
            return PointInfo.COMBAT_GRANDMASTER;
        } else if (this.points >= 250) {
            return PointInfo.COMBAT_MASTER;
        } else if (this.points >= 100) {
            return PointInfo.COMBAT_ACE;
        } else if (this.points >= 50) {
            return PointInfo.COMBAT_SPECIALIST;
        } else if (this.points >= 20) {
            return PointInfo.COMBAT_CADET;
        } else if (this.points >= 10) {
            return PointInfo.COMBAT_NOVICE;
        } else if (this.points >= 1) {
            return PointInfo.ROOKIE;
        } else {
            return PointInfo.UNRANKED;
        }
    }

    public List<NamedRanking> getSortedTiers() {
        List<NamedRanking> tiers = new ArrayList<>(this.rankings.entrySet().stream()
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(e.getKey())))
                .toList());

        tiers.sort(Comparator.comparing((NamedRanking a) -> a.ranking.retired, Boolean::compare)
                .thenComparingInt(a -> a.ranking.tier)
                .thenComparingInt(a -> a.ranking.pos));

        return tiers;
    }
}
