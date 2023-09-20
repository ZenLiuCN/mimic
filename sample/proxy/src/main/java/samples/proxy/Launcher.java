package samples.proxy;

import cn.zenliu.java.mimic.Mimic;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class Launcher {
    static {
        System.setProperty("mimic.cache", "500");
        Mimic.DynamicProxy.enable();
    }

    public static void main(String[] args) {
        val v = Fluent.of(null);
        log.info("create {}", v);
        v.id(12L);
        log.info("add id: {}", v.identity(8L));
        log.info("class is : {}", v.getClass());
        v.validate();
    }
}
