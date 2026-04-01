package net.uku3lig.tiertagger.model;

import java.util.Arrays;
import java.util.Optional;

public enum TierList {
    MCTIERS("MCTiers", "https://mctiers.com/api", '\uE901'),
    SUBTIERS("SubTiers", "https://subtiers.net/api", '\uE902'),
    ;

    private final String name;
    private final String url;
    private final char icon;

    TierList(String name, String url, char icon) {
        this.name = name;
        this.url = url;
        this.icon = icon;
    }

    public String getName() {
        return this.name;
    }

    public String getUrl() {
        return this.url;
    }

    public char getIcon() {
        return this.icon;
    }

    public String styledName(boolean current) {
        String s = icon + " " + name;
        if (current) s += " (selected)";
        return s;
    }

    public static Optional<TierList> findByUrl(String url) {
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

        final String finalUrl = url; // i :heart: java
        return Arrays.stream(values()).filter(list -> list.url.equals(finalUrl)).findFirst();
    }
}
