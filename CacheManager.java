import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class CacheManager {

    private final Cache<String, String> commandCache;
    private final Cache<String, Boolean> optionCache;
    private final Cache<String, Boolean> pathCache;

    public CacheManager() {
        this.commandCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats()
                .build();

        this.optionCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats()
                .build();

        this.pathCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(2000)
                .recordStats()
                .build();
    }

    // -------------------- GETTERS WITH LOADING --------------------
    public String getOrLoadCommand(String key, Supplier<String> loader) {
        return commandCache.get(key, k -> loader.get());
    }

    public Boolean getOrLoadOption(String key, Supplier<Boolean> loader) {
        return optionCache.get(key, k -> loader.get());
    }

    public Boolean getOrLoadPath(String key, Supplier<Boolean> loader) {
        return pathCache.get(key, k -> loader.get());
    }

    // -------------------- CACHE MANAGEMENT --------------------
    public void clearAll() {
        commandCache.invalidateAll();
        optionCache.invalidateAll();
        pathCache.invalidateAll();
    }

    public void clearCommandCache() {
        commandCache.invalidateAll();
    }

    public void clearOptionCache() {
        optionCache.invalidateAll();
    }

    public void clearPathCache() {
        pathCache.invalidateAll();
    }

    // -------------------- STATS --------------------
    public String stats() {
        return "CommandCache: hits=" + commandCache.stats().hitCount() + ", misses=" + commandCache.stats().missCount()
                + "\n" +
                "OptionCache: hits=" + optionCache.stats().hitCount() + ", misses=" + optionCache.stats().missCount()
                + "\n" +
                "PathCache: hits=" + pathCache.stats().hitCount() + ", misses=" + pathCache.stats().missCount();
    }
}
