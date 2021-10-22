package sample.mini.server.entity;

import java.time.Instant;

public interface BlogSummary {
    long id();

    String author();

    String title();

    Instant publishAt();

    int read();

}