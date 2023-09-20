package samples.asm.dao;

import cn.zenliu.java.mimic.Mimic;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;

import java.math.BigDecimal;
import java.sql.SQLException;

@Slf4j
public class Launcher {
    static final DefaultConfiguration cfg;

    static {
        System.setProperty("mimic.cache", "500");
        Mimic.ByteASM.enable();
        Mimic.Dao.ByteASM.enable();
        cfg = new DefaultConfiguration();
        cfg.setSQLDialect(SQLDialect.H2);
        val hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:test");
        cfg.setDataSource(new HikariDataSource(hc));
    }

    public static void main(String[] args) throws SQLException {
        val v = Fluent.of(null);
        log.info("create {}", v);
        v.id(12L);
        v.identity(12L);
        log.info("add id: {}", v);
        log.info("class is : {}", v.getClass());
        val dao = Fluent.FluentDao.of(cfg);
        log.info("dao {}", dao);
        log.info("dao class {}", dao.getClass());
        log.info("ddl result: {}", dao.DDL());
        log.info("insert result: {}", dao.insert(v));
        log.info("select result: {}", dao.fetchById(12L));
        log.info("select result: {}", dao.fetchByIdentity(12L));
        try {
            dao.ctx().transaction(c -> {
                val trans = Fluent.FlueDao.of(c);
                trans.DDL();
                val flue = Fluent.Flue.of(null);
                flue.id(10);
                flue.identity(10L);
                flue.user(BigDecimal.TEN);
                trans.insert(flue);
                throw new IllegalStateException("rollback");// transaction won't commit
            });
        } catch (Exception e) {
            //e.printStackTrace();
        }
        val fdao = Fluent.FlueDao.of(null);
        fdao.DDL();
        System.out.println(fdao.fetchById(10));//here no such record.
    }
}
