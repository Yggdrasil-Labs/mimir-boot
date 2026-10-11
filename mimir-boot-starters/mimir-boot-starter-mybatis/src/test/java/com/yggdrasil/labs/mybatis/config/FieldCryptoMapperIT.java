package com.yggdrasil.labs.mybatis.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yggdrasil.labs.mybatis.crypto.CryptoUtils;
import com.yggdrasil.labs.mybatis.typehandler.IntegerCryptoTypeHandler;
import com.yggdrasil.labs.mybatis.typehandler.LongCryptoTypeHandler;
import com.yggdrasil.labs.mybatis.typehandler.StringCryptoTypeHandler;

class FieldCryptoMapperIT {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZg==";
    private static final String CONTEXT = "orders";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MybatisPlusCryptoConfiguration.class,
                                    com.baomidou.mybatisplus.autoconfigure
                                            .MybatisPlusAutoConfiguration.class))
                    .withUserConfiguration(MapperConfiguration.class)
                    .withPropertyValues(
                            "mimir.boot.mybatis.crypto-enabled=true",
                            "mimir.boot.mybatis.crypto-key=" + KEY);

    @Test
    void defaultV1Writes_keepOrdinaryColumnsPlain_andDecryptExplicitCryptoColumns() {
        runner.run(
                context -> {
                    JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                    createTable(jdbc);
                    FieldCryptoMapper mapper = context.getBean(FieldCryptoMapper.class);
                    FieldCryptoEntity expected = entity(101L);

                    assertEquals(1, mapper.insert(expected));
                    assertStoredValues(jdbc, expected, false);
                    assertEntity(expected, mapper.selectById(expected.id));
                    assertOrdinaryQueryParameters(mapper, expected);
                    assertExplicitHandlersUseSpringBeans(
                            context.getBean(SqlSessionFactory.class),
                            context.getBean(StringCryptoTypeHandler.class),
                            context.getBean(LongCryptoTypeHandler.class),
                            context.getBean(IntegerCryptoTypeHandler.class));
                });
    }

    @Test
    void explicitV2Writes_keepOrdinaryColumnsPlain_andReadHistoricalV1Rows() {
        runner.withPropertyValues(
                        "mimir.boot.mybatis.crypto-context=" + CONTEXT,
                        "mimir.boot.mybatis.crypto-v2-write-enabled=true")
                .run(
                        context -> {
                            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                            createTable(jdbc);
                            FieldCryptoMapper mapper = context.getBean(FieldCryptoMapper.class);
                            FieldCryptoEntity expected = entity(101L);

                            assertEquals(1, mapper.insert(expected));
                            assertStoredValues(jdbc, expected, true);
                            assertEntity(expected, mapper.selectById(expected.id));
                            assertOrdinaryQueryParameters(mapper, expected);

                            FieldCryptoEntity historical = entity(202L);
                            assertEquals(
                                    1,
                                    jdbc.update(
                                            "INSERT INTO field_crypto "
                                                    + "(id, plain_text, plain_long, plain_integer, "
                                                    + "secret_text, secret_long, secret_integer) "
                                                    + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                                            historical.id,
                                            historical.plainText,
                                            historical.plainLong,
                                            historical.plainInteger,
                                            CryptoUtils.encrypt(historical.secretText, KEY),
                                            CryptoUtils.encrypt(
                                                    historical.secretLong.toString(), KEY),
                                            CryptoUtils.encrypt(
                                                    historical.secretInteger.toString(), KEY)));
                            assertStoredValues(jdbc, historical, false);
                            assertEntity(historical, mapper.selectById(historical.id));
                        });
    }

    private static void createTable(JdbcTemplate jdbc) {
        jdbc.execute(
                """
                CREATE TABLE field_crypto (
                    id BIGINT PRIMARY KEY,
                    plain_text VARCHAR(100),
                    plain_long BIGINT,
                    plain_integer INTEGER,
                    secret_text VARCHAR(512),
                    secret_long VARCHAR(512),
                    secret_integer VARCHAR(512)
                )
                """);
    }

    private static FieldCryptoEntity entity(long id) {
        FieldCryptoEntity entity = new FieldCryptoEntity();
        entity.id = id;
        entity.plainText = "ordinary-" + id;
        entity.plainLong = 42L;
        entity.plainInteger = 7;
        entity.secretText = "secret-" + id;
        entity.secretLong = 123456789L;
        entity.secretInteger = 987;
        return entity;
    }

    private static void assertStoredValues(
            JdbcTemplate jdbc, FieldCryptoEntity expected, boolean v2) {
        Map<String, Object> raw =
                jdbc.queryForMap("SELECT * FROM field_crypto WHERE id = ?", expected.id);
        String secretText = (String) raw.get("secret_text");
        String secretLong = (String) raw.get("secret_long");
        String secretInteger = (String) raw.get("secret_integer");

        assertAll(
                () -> assertEquals(expected.plainText, raw.get("plain_text")),
                () -> assertEquals(expected.plainLong, raw.get("plain_long")),
                () -> assertEquals(expected.plainInteger, raw.get("plain_integer")),
                () -> assertNotEquals(expected.secretText, secretText),
                () -> assertNotEquals(expected.secretLong.toString(), secretLong),
                () -> assertNotEquals(expected.secretInteger.toString(), secretInteger),
                () -> assertEquals(v2, secretText.startsWith("v2:")),
                () -> assertEquals(v2, secretLong.startsWith("v2:")),
                () -> assertEquals(v2, secretInteger.startsWith("v2:")),
                () ->
                        assertEquals(
                                expected.secretText, CryptoUtils.decrypt(secretText, KEY, CONTEXT)),
                () ->
                        assertEquals(
                                expected.secretLong.toString(),
                                CryptoUtils.decrypt(secretLong, KEY, CONTEXT)),
                () ->
                        assertEquals(
                                expected.secretInteger.toString(),
                                CryptoUtils.decrypt(secretInteger, KEY, CONTEXT)));
    }

    private static void assertEntity(FieldCryptoEntity expected, FieldCryptoEntity actual) {
        assertAll(
                () -> assertEquals(expected.id, actual.id),
                () -> assertEquals(expected.plainText, actual.plainText),
                () -> assertEquals(expected.plainLong, actual.plainLong),
                () -> assertEquals(expected.plainInteger, actual.plainInteger),
                () -> assertEquals(expected.secretText, actual.secretText),
                () -> assertEquals(expected.secretLong, actual.secretLong),
                () -> assertEquals(expected.secretInteger, actual.secretInteger));
    }

    private static void assertOrdinaryQueryParameters(
            FieldCryptoMapper mapper, FieldCryptoEntity expected) {
        var results =
                mapper.selectList(
                        Wrappers.<FieldCryptoEntity>query()
                                .eq("plain_text", expected.plainText)
                                .eq("plain_long", expected.plainLong)
                                .eq("plain_integer", expected.plainInteger));
        assertEquals(1, results.size());
        assertEntity(expected, results.get(0));
    }

    private static void assertExplicitHandlersUseSpringBeans(
            SqlSessionFactory factory,
            StringCryptoTypeHandler stringHandler,
            LongCryptoTypeHandler longHandler,
            IntegerCryptoTypeHandler integerHandler) {
        var registry = factory.getConfiguration().getTypeHandlerRegistry();
        assertAll(
                () ->
                        assertSame(
                                stringHandler,
                                registry.getMappingTypeHandler(StringCryptoTypeHandler.class)),
                () ->
                        assertSame(
                                longHandler,
                                registry.getMappingTypeHandler(LongCryptoTypeHandler.class)),
                () ->
                        assertSame(
                                integerHandler,
                                registry.getMappingTypeHandler(IntegerCryptoTypeHandler.class)));
    }

    @Configuration(proxyBeanMethods = false)
    static class MapperConfiguration {

        @Bean
        SingleConnectionDataSource dataSource() {
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:field_crypto_" + UUID.randomUUID());
            dataSource.setSuppressClose(true);
            return dataSource;
        }

        @Bean
        MapperFactoryBean<FieldCryptoMapper> fieldCryptoMapper(SqlSessionFactory factory) {
            MapperFactoryBean<FieldCryptoMapper> mapper =
                    new MapperFactoryBean<>(FieldCryptoMapper.class);
            mapper.setSqlSessionFactory(factory);
            return mapper;
        }
    }

    public interface FieldCryptoMapper extends BaseMapper<FieldCryptoEntity> {}

    @TableName(value = "field_crypto", autoResultMap = true)
    public static class FieldCryptoEntity {

        @TableId(type = IdType.INPUT)
        private Long id;

        private String plainText;
        private Long plainLong;
        private Integer plainInteger;

        @TableField(typeHandler = StringCryptoTypeHandler.class)
        private String secretText;

        @TableField(typeHandler = LongCryptoTypeHandler.class)
        private Long secretLong;

        @TableField(typeHandler = IntegerCryptoTypeHandler.class)
        private Integer secretInteger;
    }
}
