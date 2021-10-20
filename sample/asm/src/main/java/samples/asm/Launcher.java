package samples.asm;

import cn.zenliu.java.mimic.Config;
import cn.zenliu.java.mimic.Mimic;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class Launcher {
    static {
        Config.cacheSize.set(500);
        Mimic.ByteASM.enable();
    }

    public static void main(String[] args) {
        val v = Fluent.of(null);
        log.info("create {}", v);
        v.id(12L);
        log.info("add id: {}", v);
        log.info("class is : {}", v.getClass());
    }
}
