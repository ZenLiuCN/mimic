package cn.zenliu.java.mimic;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-10-19
 */
public interface Config {
    /**
     * this effect on all internal Caffeine loading Caches. Must set before Load Mimic class.
     */
    AtomicInteger cacheSize = new AtomicInteger(1024);
}
