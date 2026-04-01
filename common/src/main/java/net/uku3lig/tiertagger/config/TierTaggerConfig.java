package net.uku3lig.tiertagger.config;

import com.google.gson.internal.LinkedTreeMap;
import net.uku3lig.tiertagger.TierCache;
import net.uku3lig.tiertagger.model.GameMode;
import net.uku3lig.ukulib.config.option.StringTranslatable;

import java.io.Serializable;
import java.util.Optional;

public class TierTaggerConfig implements Serializable {
    private boolean enabled = true;
    private String gameMode = "vanilla";
    private boolean showRetired = true;
    private HighestMode highestMode = HighestMode.NOT_FOUND;
    private boolean showIcons = true;
    private boolean playerList = true;
    private int retiredColor = 0xa2d6ff;
    // note: this is a GSON internal class. this *might* break in the future
    private LinkedTreeMap<String, Integer> tierColors = defaultColors();

    // === internal stuff ===

    /**
     * <p>the field was renamed to do a little trolling and force it setting to the default value in players' config</p>
     * <p>previous name(s): {@code baseUrl}</p>
     */
    private String apiUrl = "https://mctiers.com/api";

    public TierTaggerConfig() {
    }

    public TierTaggerConfig(boolean enabled, String gameMode, boolean showRetired, HighestMode highestMode, boolean showIcons,
                            boolean playerList, int retiredColor, LinkedTreeMap<String, Integer> tierColors, String apiUrl) {
        this.enabled = enabled;
        this.gameMode = gameMode;
        this.showRetired = showRetired;
        this.highestMode = highestMode;
        this.showIcons = showIcons;
        this.playerList = playerList;
        this.retiredColor = retiredColor;
        this.tierColors = tierColors;
        this.apiUrl = apiUrl;
    }

    public GameMode getGameMode() {
        Optional<GameMode> opt = TierCache.findMode(this.gameMode);
        if (opt.isPresent()) {
            return opt.get();
        } else {
            GameMode first = TierCache.getGamemodes().getFirst();
            if (!first.isNone()) this.gameMode = first.id();
            return first;
        }
    }

    private static LinkedTreeMap<String, Integer> defaultColors() {
        LinkedTreeMap<String, Integer> colors = new LinkedTreeMap<>();
        colors.put("HT1", 0xe8ba3a);
        colors.put("LT1", 0xd5b355);
        colors.put("HT2", 0xc4d3e7);
        colors.put("LT2", 0xa0a7b2);
        colors.put("HT3", 0xf89f5a);
        colors.put("LT3", 0xc67b42);
        colors.put("HT4", 0x81749a);
        colors.put("LT4", 0x655b79);
        colors.put("HT5", 0x8f82a8);
        colors.put("LT5", 0x655b79);

        return colors;
    }

    public enum HighestMode implements StringTranslatable {
        NEVER("never", "tiertagger.highest.never"),
        NOT_FOUND("not_found", "tiertagger.highest.not_found"),
        ALWAYS("always", "tiertagger.highest.always"),
        ;

        private final String name;
        private final String translationKey;

        HighestMode(String name, String translationKey) {
            this.name = name;
            this.translationKey = translationKey;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getTranslationKey() {
            return this.translationKey;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isShowRetired() {
        return showRetired;
    }

    public void setShowRetired(boolean showRetired) {
        this.showRetired = showRetired;
    }

    public HighestMode getHighestMode() {
        return highestMode;
    }

    public void setHighestMode(HighestMode highestMode) {
        this.highestMode = highestMode;
    }

    public boolean isShowIcons() {
        return showIcons;
    }

    public void setShowIcons(boolean showIcons) {
        this.showIcons = showIcons;
    }

    public boolean isPlayerList() {
        return playerList;
    }

    public void setPlayerList(boolean playerList) {
        this.playerList = playerList;
    }

    public int getRetiredColor() {
        return retiredColor;
    }

    public void setRetiredColor(int retiredColor) {
        this.retiredColor = retiredColor;
    }

    public LinkedTreeMap<String, Integer> getTierColors() {
        return tierColors;
    }

    public void setTierColors(LinkedTreeMap<String, Integer> tierColors) {
        this.tierColors = tierColors;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }
}
