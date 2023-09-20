package sample.mini.server;

import cn.zenliu.java.mimic.Mimic;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.lambda.Seq;
import org.jooq.tools.json.JSONArray;
import org.jooq.tools.json.JSONObject;
import org.jooq.tools.json.JSONParser;
import org.jooq.tools.json.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import sample.mini.server.entity.Blog;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-10-21
 */
public class Launcher {
    static final Logger log = LoggerFactory.getLogger(Launcher.class);
    public static void main(String[] args) {
        HttpServer.create()
            .port(8080)
            .accessLog(true)
            .compress(2048)
            .route(routes -> {
                routes.get("/", Launcher::getBlogList);
                routes.get("/{id}", Launcher::readBlog);
                routes.post("/", Launcher::publishBlog);
                routes.delete("/{id}", Launcher::removeBlog);
            })
            .bindUntilJavaShutdown(Duration.ofSeconds(10), null);
    }

    static Mono<Void> getBlogList(HttpServerRequest req, HttpServerResponse res) {
        return res
            .header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
            .sendByteArray(
                bytesWithError(() ->
                    JSONArray.toJSONString(Seq.seq(dao.list()).map(Blog::summaryMap).collect(Collectors.toList())).getBytes(StandardCharsets.UTF_8)
                )
            )
            .then();
    }

    static Mono<Void> readBlog(HttpServerRequest req, HttpServerResponse res) {
        return res.header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
            .sendByteArray(bytesWithError(() -> {
                final String id = req.param("id");
                if (id == null || id.isEmpty()) {
                    throw new IllegalStateException("must have valid blog id");
                }
                long bid = Long.parseLong(id);
                return dao.read(bid)
                    .map(Blog::outputJson)
                    .map(String::getBytes)
                    .orElseThrow(() -> new IllegalStateException("not exists"));
            }))
            .then();
    }

    static Mono<Void> publishBlog(HttpServerRequest req, HttpServerResponse res) {
        return req.receive().aggregate().asString().flatMap(x ->
            res.header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .sendByteArray(bytesWithError(() -> {
                    try {
                        Object out = new JSONParser().parse(x);
                        if (!(out instanceof Map)) throw new IllegalStateException("invalid request");
                        //noinspection unchecked
                        dao.write(Mimic.newInstance(Blog.class, (Map<String, Object>) out));
                        return new byte[0];
                    } catch (ParseException e) {
                        throw new IllegalStateException(e);
                    }
                })).then()
        );
    }

    static Mono<Void> removeBlog(HttpServerRequest req, HttpServerResponse res) {
        return res.header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
            .sendByteArray(bytesWithError(() -> {
                final String id = req.param("id");
                if (id == null || id.isEmpty()) {
                    throw new IllegalStateException("must have valid blog id");
                }
                long bid = Long.parseLong(id);
                final boolean result = dao.delete(bid);
                JSONObject json = new JSONObject();
                json.put("result", result);
                return json.toString().getBytes(StandardCharsets.UTF_8);
            }))
            .then();
    }

    @SuppressWarnings("unchecked")
    static Mono<String> stringWithError(Supplier<String> action) {
        return Mono.fromCallable(() -> {
            try {
                return action.get();
            } catch (Exception e) {
                log.error("process request ", e);
                final JSONObject json = new JSONObject();
                json.put("status", 500);
                json.put("message", e.getMessage());
                return json.toString();
            }
        });
    }

    @SuppressWarnings("unchecked")
    static Mono<byte[]> bytesWithError(Supplier<byte[]> action) {
        return Mono.fromCallable(() -> {
            try {
                return action.get();
            } catch (Exception e) {
                log.error("process request ", e);
                final JSONObject json = new JSONObject();
                json.put("status", 500);
                json.put("message", e.getMessage());
                return json.toString().getBytes(StandardCharsets.UTF_8);
            }
        });
    }

    final static Blog.BlogDao dao;

    static {
        System.setProperty("mimic.cache", "500");
        Mimic.ByteASM.enable();
        Mimic.Dao.ByteASM.enable();
        DefaultConfiguration cfg = new DefaultConfiguration();
        cfg.setSQLDialect(SQLDialect.H2);
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:test");
        cfg.setDataSource(new HikariDataSource(hc));
        Mimic.Dao.setConfiguration(cfg);
        dao = Blog.repository();
        dao.DDL();
    }
}
