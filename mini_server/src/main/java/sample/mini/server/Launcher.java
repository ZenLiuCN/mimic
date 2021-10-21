package sample.mini.server;

import cn.zenliu.java.mimic.Config;
import cn.zenliu.java.mimic.Mimic;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.tools.json.JSONArray;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import sample.mini.server.entity.Blog;

import java.time.Duration;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-10-21
 */
public class Launcher {
    public static void main(String[] args) {
        HttpServer.create()
            .port(8080)
            .accessLog(true)
            .compress(2048)
            .route(routes -> {
                routes.get("", (q, r) -> {
                    try {
                        return r.status(200)
                            .sendString(Mono.fromCallable(() ->
                                JSONArray.toJSONString(dao.list())
                            )).then();
                    } catch (IllegalStateException | NumberFormatException e) {
                        return r.status(403)
                            .sendString(Mono.just(e.getMessage()))
                            .then();
                    }
                });
                routes.get("{id}", (q, r) -> {
                    try {
                        final String id = q.params().get("id");
                        if (id == null || id.isEmpty()) {
                            throw new IllegalStateException("must have valid blog id");
                        }
                        long bid = Long.parseLong(id);
                        String json = dao.read(bid).map(Blog::outputJson).orElseThrow(() -> new IllegalStateException("not exists"));
                        return r.status(200)
                            .header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                            .sendString(Mono.just(json)).then();
                    } catch (IllegalStateException | NumberFormatException e) {
                        return r.status(403)
                            .header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                            .sendString(Mono.just(e.getMessage()))
                            .then();
                    }
                });
                routes.post("", (q, r) -> {
                    try {

                        return r.status(200)
                            .sendString(Mono.fromCallable(() ->
                                JSONArray.toJSONString(dao.list())
                            )).then();
                    } catch (IllegalStateException | NumberFormatException e) {
                        return r.status(400)
                            .header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                            .sendString(Mono.just(e.getMessage()))
                            .then();
                    }
                });
                routes.delete("{id}", (q, r) -> {
                    try {
                        final String id = q.params().get("id");
                        if (id == null || id.isEmpty()) {
                            throw new IllegalStateException("must have valid blog id");
                        }
                        dao.delete(Long.parseLong(id));
                    } catch (IllegalStateException | NumberFormatException e) {
                        return r.status(400)
                            .header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                            .sendString(Mono.just(e.getMessage()))
                            .then();
                    }
                    return r.status(200)
                        .send();
                });
            })
            .bindUntilJavaShutdown(Duration.ofSeconds(10), null);
    }

    final static Blog.BlogDao dao;

    static {
        Config.cacheSize.set(2);
        Mimic.ByteASM.enable();
        Mimic.Dao.ByteASM.enable();
        DefaultConfiguration cfg = new DefaultConfiguration();
        cfg.setSQLDialect(SQLDialect.H2);
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:test");
        cfg.setDataSource(new HikariDataSource(hc));
        Mimic.Dao.setConfiguration(cfg);
        dao = Blog.repository();
    }
}
