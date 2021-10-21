package sample.mini.server.entity;

import cn.zenliu.java.mimic.Mimic;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;
import org.jooq.tools.json.JSONObject;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-10-21
 */
@Mimic.Dao.Entity
public interface Blog extends BlogSummary, Mimic {
    Blog author(String val);

    Blog title(String val);

    String content();

    Blog content(String val);

    Blog publishAt(Instant val);

    Blog read(int val);

    boolean removed();

    Blog removed(boolean val);

    default String outputJson() {
        final HashMap<String, Object> map = new HashMap<>(underlyingMap());
        return JSONObject.toJSONString(map);
    }


    @Override
    default void validate() throws IllegalStateException {
        if (author() == null || author().isEmpty()) {
            throw new IllegalStateException("author must not be empty!");
        }
        if (title() == null || title().isEmpty()) {
            throw new IllegalStateException("title must not be empty!");
        }
        if (content() == null || content().isEmpty()) {
            throw new IllegalStateException("content must not be empty!");
        }
    }

    default Blog readPlus() {
        return read(read() + 1);
    }

    interface BlogDao extends Mimic.Dao<Blog> {
        @As(typeProperty = TypeBigIntIdentity)
        Field<Long> id();

        @As(procProperty = ProcNotNull)
        Field<String> author();

        @As(procProperty = ProcNotNull)
        Field<String> title();

        @As(procProperty = ProcNotNull)
        Field<String> content();

        @As(typeProperty = TypeInstantAsTimestampDefaultNow)
        Field<Instant> publishAt();

        @As(procProperty = ProcNotNull)
        Field<Integer> read();

        DataType<Boolean> BooleanType = SQLDataType.BOOLEAN.defaultValue(false).nullable(false);

        @As(typeHolder = BlogDao.class, typeProperty = "BooleanType")
        Field<Boolean> removed();

        @Override
        default List<Field<?>> allFields() {
            return Arrays.asList(
                id(),
                author(),
                title(),
                content(),
                publishAt(),
                read(),
                removed()
            );
        }

        default List<Field<?>> summaryFields() {
            return Arrays.asList(
                id(),
                author(),
                title(),
                content(),
                publishAt(),
                read()
            );
        }

        default List<Field<?>> insertFields() {
            return Arrays.asList(
                author(),
                title(),
                content()
            );
        }

        default void write(Blog blog) {
            blog.validate();
            ctx().insertInto(table())
                .columns(insertFields())
                .values(
                    blog.author(),
                    blog.title(),
                    blog.content()
                ).execute();
        }

        default List<BlogSummary> list() {
            return ctx().select(summaryFields())
                .from(table())
                .where(removed().isFalse())
                .orderBy(publishAt().desc())
                .fetchStreamInto(BlogSummary.class)//use jooq delegate function
                .collect(Collectors.toList());
        }

        default boolean delete(long id) {
            return ctx().update(table())
                .set(removed(), true)
                .where(id().eq(id))
                .execute() == 1;
        }

        default void updateRead(long id) {
            ctx().update(table())
                .set(read(), read().plus(1))
                .where(id().eq(id))
                .execute();
        }

        default Optional<Blog> read(long id) {
            return ctx().select(allFields())
                .from(table())
                .where(id().eq(id))
                .and(removed().isFalse())
                .fetchOptional()
                .map(x -> {
                    final Blog blog = instance(x.intoMap());
                    updateRead(blog.id());
                    return blog;
                });
        }
    }

    static BlogDao repository() {
        return Mimic.Dao.newInstance(Blog.class, BlogDao.class, null);
    }
}
