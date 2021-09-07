package cn.zenliu.java.mimic;

import java.util.Map;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-08-19
 */
public interface TreeRouting<T> {
    Map<String, T> map();

    default void put(T val) {

    }
}
