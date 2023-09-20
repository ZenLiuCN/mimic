# Mimic
![v](https://img.shields.io/maven-central/v/io.github.zenliucn.java/mimic?style=flat-square)
![lic](https://img.shields.io/badge/license-LGPL-GREEN)
![size](https://img.shields.io/badge/size-75.2k-GREEN)

Jvm runtime interface Pojo and Repository generator

## Introduction

### Mimic

 <p>  is a protocol defined to use Interface as Pojo.
 <p> <b>Note:</b> this implement by JDK dynamic proxy, and will decrement performance for about 10 times, may never use for performance award condition.
 It's best use for slow business logic implement that coding time is more expensive than performance requirement.
 <p> there includes new implement with ByteBuddy, performance improved,see benchmark to choose.

 <p> <h3>Introduce</h3>
 <p> <b>Property</b>: defined by interface Getter&Setter methods.
 <p> <b>Getter&Setter Strategy:</b>
 <p> <b>Common Strategy</b>:
 <p> <b>1. getter</b> must be PUBLIC, NONE DEFAULT, NO PARAMETER and Returns NONE VOID type.
 <p> <b>2. setter</b> must be PUBLIC, NONE DEFAULT, HAVE ONLY ONE PARAMETER and Returns VOID type Or SELF.
 <p> <b>Fluent Strategy</b> (The default strategy): getter's name and setter's name are the property name;
 <p> <b>Java Bean Strategy</b>: getter's name is prefixed with 'get' or 'is', setter's name is prefixed with 'set';
 this would be enabled by annotate with {@link JavaBean} and set {@link JavaBean#value()} as false
 <p> <b>Mix Strategy</b>: both Java Bean Strategy and Fluent Strategy are allowed;
 this would be enabled by annotate with {@link JavaBean} and set {@link JavaBean#value()} as true
 <p> <b>default methods</b>
 <p> <b>underlyingMap</b>: fetch this underlying storage, which is mutable, but should careful to change its values,
 cause of this will ignore any annotated validation or convection methods on interface.
 <p><b>underlyingChangedProperties</b>: recoding the set action took on  properties. only effect by using {@link Dao},
 under other conditions it always returns null.
 <p><b>Misc</b></p>
 <p><b>Inherit</b>: Mimic can inherit from other Mimic </p>
 <p><b>Conversion</b>: Mimic can annotate with {@link AsString} on getter or setter to enable single property conversion.
 <p> for Collections(LIST,SET and ARRAY), there is {@link Array} to support nested Mimicked properties. but current {@link Map} is not been supported.
 <p><b>Validation</b>: Mimic can annotate with {@link Validation} on getter or setter to enable single property validation.
 <p> Mimic also use overrideable method {@link Mimic#validate()} to active a Pojo validation.</p>
 <p><b>Extension</b>: {@link Dao} is extension for use {@link Mimic} as easy Jooq repository.</p>

### Dao

<p> is a Jooq repository interface for {@link Mimic}.
<p> <h3>Introduce</h3>
<p> Dao extends with Jooq Dynamic api to create Repository interface for a {@link Mimic}.
<p> <b>Note:</b>
<p>  Dao can not be inherited. that means a Dao must directly extended {@link Dao}.
<p>  {@link Mimic} used by Dao must directly annotate with {@link Dao.Entity}.
<p>  {@link Mimic} will enable Property Change Recording, which store changed Property Name in {@link Mimic#underlyingChangedProperties()}.

## Benchmark

+ simple benchmark:

```
      I7-4790K@4.00GHz 16G WIN7 x64

      Benchmark                                   Mode  Cnt      Score      Error  Units
      ----------------------------------------------------------------------------------
      MimicBenchmark.mimicBenchPojoBuildOneShot     ss   50   1399.364 ±  250.398  ns/op
      MimicBenchmark.mimicBenchAsmBuildOneShot      ss   50  24174.228 ± 4984.977  ns/op
      MimicBenchmark.mimicBenchProxyBuildOneShot    ss   50  20351.224 ± 3494.686  ns/op
      
      MimicBenchmark.mimicBenchPojoBuild          avgt   50     71.325 ±   11.814  ns/op
      MimicBenchmark.mimicBenchAsmBuild**         avgt   50    792.614 ±  109.759  ns/op
      MimicBenchmark.mimicBenchProxyBuild**       avgt   50    385.281 ±   71.375  ns/op
      
      MimicBenchmark.mimicBenchPojoGet            avgt   50      3.179 ±    0.168  ns/op
      MimicBenchmark.mimicBenchAsmGet             avgt   50      3.190 ±    0.156  ns/op
      MimicBenchmark.mimicBenchProxyGet           avgt   50     21.378 ±    1.250  ns/op
      
      MimicBenchmark.mimicBenchPojoGetConv*       avgt   50     14.621 ±    0.500  ns/op
      MimicBenchmark.mimicBenchAsmGetConv         avgt   50      3.818 ±    0.185  ns/op
      MimicBenchmark.mimicBenchProxyGetConv       avgt   50     35.102 ±    1.934  ns/op
      
      MimicBenchmark.mimicBenchPojoSet            avgt   50      9.926 ±    0.495  ns/op
      MimicBenchmark.mimicBenchAsmSet             avgt   50     15.776 ±    0.665  ns/op
      MimicBenchmark.mimicBenchProxySet           avgt   50     55.353 ±    4.208  ns/op
      
      MimicBenchmark.mimicBenchPojoSetConv        avgt   50     55.036 ±    6.174  ns/op
      MimicBenchmark.mimicBenchAsmSetConv*        avgt   50     18.637 ±    0.825  ns/op
      MimicBenchmark.mimicBenchProxySetConv       avgt   50     90.640 ±    6.061  ns/op
      
      Intel(R) Xeon(R) CPU E5-2678 v3 @ 2.50GHz X2 Linux X64
      
      MimicBenchmark.mimicBenchPojoBuildOneShot     ss   50   7557.820 ±  7871.119  ns/op
      MimicBenchmark.mimicBenchAsmBuildOneShot      ss   50  91394.300 ± 50905.101  ns/op
      MimicBenchmark.mimicBenchProxyBuildOneShot    ss   50  62893.256 ± 30947.078  ns/op
      
      MimicBenchmark.mimicBenchPojoBuild          avgt   50     55.184 ±    34.656  ns/op
      MimicBenchmark.mimicBenchAsmBuild **        avgt   50   2729.979 ±   489.367  ns/op
      MimicBenchmark.mimicBenchProxyBuild **      avgt   50   4643.816 ±  9103.532  ns/op
      
      MimicBenchmark.mimicBenchPojoGet            avgt   50      3.630 ±     0.159  ns/op
      MimicBenchmark.mimicBenchAsmGet             avgt   50      3.820 ±     0.193  ns/op
      MimicBenchmark.mimicBenchProxyGet           avgt   50     25.273 ±     1.166  ns/op
      
      MimicBenchmark.mimicBenchPojoGetConv *      avgt   50    325.344 ±   215.961  ns/op
      MimicBenchmark.mimicBenchAsmGetConv *       avgt   50      4.523 ±     0.221  ns/op
      MimicBenchmark.mimicBenchProxyGetConv *     avgt   50    358.351 ±   239.475  ns/op
      
      MimicBenchmark.mimicBenchPojoSet            avgt   50     12.567 ±     0.432  ns/op
      MimicBenchmark.mimicBenchAsmSet             avgt   50     22.138 ±     1.440  ns/op
      MimicBenchmark.mimicBenchProxySet           avgt   50   2467.734 ±   271.575  ns/op
      
      MimicBenchmark.mimicBenchPojoSetConv        avgt   50     92.628 ±    43.897  ns/op
      MimicBenchmark.mimicBenchAsmSetConv         avgt   50     25.559 ±     1.064  ns/op
      MimicBenchmark.mimicBenchProxySetConv       avgt   50    168.255 ±    90.126  ns/op

      ----------------------------------------------------------------------------------
      * ASM setter use a lazy convert when generate map, but pojo do validate and convert when set or get.
      ** Average build time is the cost without interface analysis and internal Objects creation,those are cached with Caffeine

```

## dependency

1. `org.slf4j:slf4j-api:2.x` use for logging
2. `org.jooq:jool:0.9.x` use for tuples and Seq, maybe removed future.
3. `com.github.ben-manes.caffeine:caffeine:3.x` use for cache.

## optional dependency

1. `net.bytebuddy:byte-buddy:1.x`: needed to generate ASM by use `ByteASM` mode.
2. `org.jooq:jooq:3.x`: needed when use `DAO` extension.

## usage

```xml

<dependencies>
    <!-- only requirement for just use JDK proxy mode  -->
    <dependency>
        <groupId>io.github.zenliucn.java</groupId>
        <artifactId>mimic</artifactId>
        <version>${mimic.version}</version>
    </dependency>
    <!-- needed to use ByteASM mode  -->
    <dependency>
        <groupId>net.bytebuddy</groupId>
        <artifactId>byte-buddy</artifactId>
        <version>1.10.17</version>
    </dependency>
    <!-- needed to use DAO extension  -->
    <dependency>
        <groupId>org.jooq</groupId>
        <artifactId>jooq</artifactId>
        <version>3.14.7</version>
    </dependency>
    <!-- use as database  -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <version>1.4.200</version>
    </dependency>
    <!-- use as connection pool  -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <version>2.7.9</version>
    </dependency>
</dependencies>
```

define interface as data structure

```java
//define a interface 

import cn.zenliu.java.mimic.Mimic;

import java.util.Map;

@Mimic.Dao.Entity //only want to use a Jooq DAO
public interface Fluent extends Mimic {
    long id();

    void id(long val);

    @Validation(property = "notNull")
    Long identity();

    Fluent identity(Long val);

    @Dao.AsString
        // this field will store as String
    Long idOfUser();

    Fluent idOfUser(Long val);

    @Override
    default void validate() throws IllegalStateException {
        if (identity() > 10) {
            throw new IllegalStateException("identity must less than 10");
        }
    }

    static Fluent of(Map<String, Object> value) {
        return Mimic.newInstance(Fluent.class, value);
    }
}
```

define the JOOQ DAO

```java
interface FluentDao extends Mimic.Dao<Fluent> {
    //the static factory method
    static FluentDao of(Configuration cfg) {
        return Dao.newInstance(Fluent.class, FluentDao.class, cfg);
    }

    //define the Identity column
    @As(typeHolder = Dao.class, typeProperty = "BigIntIdentity")
    Field<Long> id();

    //define store as BIGINT, not really need this, common type will guess by JOOQ
    @As(typeHolder = SQLDataType.class, typeProperty = "BIGINT")
    Field<Long> identity();

    @As(typeHolder = SQLDataType.class, typeProperty = "VARCHAR")
    Field<String> idOfUser();

    //optional override to supply all fields, default maybe not with supposed order
    @Override 
    default List<Field<?>> allFields() {
        return Arrays.asList(id(), identity(), idOfUser());
    }

    //extend DAO actions
    default int insert(Fluent i) {
        return ctx().insertInto(table()).set(toDatabase(i.underlyingMap())).execute();
    }

    default Fluent fetchById(long id) {
        return instance(ctx().selectFrom(table()).where(id().eq(id)).fetchOne().intoMap());
    }

    default Fluent fetchByIdentity(long identity) {
        return instance(ctx().selectFrom(table()).where(identity().eq(identity)).fetchOne().intoMap());
    }

    default Fluent update(Fluent i) {
        if (i.id() == 0) {
            throw new IllegalStateException("can't update entity have no id");
        }
        val und = i.underlyingMap();
        val changes = i.underlyingChangedProperties();
        if (changes == null || changes.isEmpty()) throw new IllegalStateException("nothing to update entity");
        val m = new HashMap<String, Object>();
        for (String property : changes) {
            m.put(property, und.get(property));
        }
        if (ctx().update(table()).set(toDatabase(m)).where(id().eq(i.id())).execute() < 1) {
            throw new IllegalStateException("update failure");
        }
        return i;
    }

    default void deleteAll() {
        ctx().delete(table()).execute();
    }
}

```

use them

```java

@Slf4j
public class Launcher {
    static final DefaultConfiguration cfg;

    static {
        Config.cacheSize.set(500);
        cfg = new DefaultConfiguration();
        cfg.setSQLDialect(SQLDialect.H2);
        val hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:test");
        cfg.setDataSource(new HikariDataSource(hc));
    }

    public static void main(String[] args) {
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
    }
}

```

## misc

More sample just check out maven project in `sample` directory. There may be a small example Project to
use `mimic+h2+jooq+reactor-netty` as micro webservice, if got time.
