package com.yggdrasil.labs.web.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.web.config.JacksonConfig;
import com.yggdrasil.labs.web.config.WebAutoConfiguration;
import com.yggdrasil.labs.web.config.WebProperties;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class RSuccessMixinTest {
    @Test
    void managedMapperHidesLegacyFailWhilePlainJacksonKeepsIt() throws Exception {
        R<String> response = R.success("payload");
        ObjectMapper plain = new ObjectMapper();
        assertThat(plain.readTree(plain.writeValueAsBytes(response)).has("fail")).isTrue();

        JacksonConfig config =
                new JacksonConfig(
                        new WebProperties(),
                        new DefaultListableBeanFactory().getBeanProvider(Module.class));
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        config.jackson2ObjectMapperBuilderCustomizer().customize(builder);
        ObjectMapper managed = builder.build();
        var tree = managed.readTree(managed.writeValueAsBytes(response));
        assertThat(tree.path("success").asBoolean()).isTrue();
        assertThat(tree.has("fail")).isFalse();
    }

    @Test
    void compatibilitySwitchKeepsLegacyFail() throws Exception {
        WebProperties properties = new WebProperties();
        properties.getJson().setIncludeLegacyFail(true);
        ObjectMapper mapper = build(customizer(properties), new Jackson2ObjectMapperBuilder());
        var tree = mapper.readTree(mapper.writeValueAsBytes(R.success("payload")));
        assertThat(tree.path("success").asBoolean()).isTrue();
        assertThat(tree.path("fail").asBoolean()).isFalse();

        Jackson2ObjectMapperBuilder business = new Jackson2ObjectMapperBuilder();
        business.mixIn(R.class, BusinessMixin.class);
        assertBusinessMixin(build(customizer(properties), business));
    }

    @Test
    void existingBusinessMixinsTakePrecedenceAndLaterMixinsCanReplaceDefault() throws Exception {
        Jackson2ObjectMapperBuilderCustomizer customizer = customizer(new WebProperties());
        Jackson2ObjectMapperBuilder declared = new Jackson2ObjectMapperBuilder();
        declared.mixIn(R.class, BusinessMixin.class);
        assertBusinessMixin(build(customizer, declared));

        SimpleModule module = new SimpleModule("business-r-mixin");
        module.setMixInAnnotation(R.class, BusinessMixin.class);
        Jackson2ObjectMapperBuilder fromModule = new Jackson2ObjectMapperBuilder();
        customizer.customize(fromModule);
        fromModule.modulesToInstall(module);
        assertBusinessMixin(fromModule.build());

        Jackson2ObjectMapperBuilder early = new Jackson2ObjectMapperBuilder();
        early.postConfigurer(mapper -> mapper.addMixIn(R.class, BusinessMixin.class));
        assertBusinessMixin(build(customizer, early));

        Jackson2ObjectMapperBuilder late = new Jackson2ObjectMapperBuilder();
        customizer.customize(late);
        late.postConfigurer(mapper -> mapper.addMixIn(R.class, BusinessMixin.class));
        assertBusinessMixin(late.build());

        ObjectMapper built = build(customizer, new Jackson2ObjectMapperBuilder());
        built.addMixIn(R.class, BusinessMixin.class);
        assertBusinessMixin(built);
    }

    @Test
    void sharedBuilderCanBuildTwiceAndLateMixinsDoNotEmitSkipMessages() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(JacksonConfig.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
            customizer(new WebProperties()).customize(builder);
            ObjectMapper first = builder.build();
            ObjectMapper second = builder.build();
            assertThat(first.readTree(first.writeValueAsBytes(R.success())).has("fail")).isFalse();
            assertThat(second.readTree(second.writeValueAsBytes(R.success())).has("fail"))
                    .isFalse();

            Jackson2ObjectMapperBuilder late = new Jackson2ObjectMapperBuilder();
            customizer(new WebProperties()).customize(late);
            late.postConfigurer(mapper -> mapper.addMixIn(R.class, BusinessMixin.class));
            assertBusinessMixin(late.build());

            ObjectMapper afterBuild =
                    build(customizer(new WebProperties()), new Jackson2ObjectMapperBuilder());
            afterBuild.addMixIn(R.class, BusinessMixin.class);
            assertBusinessMixin(afterBuild);
            assertThat(skipMessages(events)).isEmpty();
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test
    void subclassMixinDoesNotPreventBaseMixin() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        builder.mixIn(ChildResponse.class, BusinessMixin.class);
        ObjectMapper mapper = build(customizer(new WebProperties()), builder);
        assertThat(mapper.findMixInClassFor(R.class)).isEqualTo(RSuccessMixin.class);
        assertThat(mapper.findMixInClassFor(ChildResponse.class)).isEqualTo(BusinessMixin.class);
        assertThat(mapper.readTree(mapper.writeValueAsBytes(new ChildResponse())).has("fail"))
                .isFalse();
    }

    @Test
    void separateApplicationContextsEachReportBusinessMixinOnce() {
        Logger logger = (Logger) LoggerFactory.getLogger(JacksonConfig.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            WebApplicationContextRunner runner =
                    new WebApplicationContextRunner()
                            .withConfiguration(
                                    AutoConfigurations.of(
                                            JacksonAutoConfiguration.class,
                                            WebAutoConfiguration.class))
                            .withBean(
                                    "businessMixinModule",
                                    Module.class,
                                    () -> {
                                        SimpleModule module = new SimpleModule("business-r-mixin");
                                        module.setMixInAnnotation(R.class, BusinessMixin.class);
                                        return module;
                                    });
            for (int i = 1; i <= 2; i++) {
                int expected = i;
                runner.run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertBusinessMixin(context.getBean(ObjectMapper.class));
                            assertThat(skipMessages(events)).hasSize(expected);
                        });
            }
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test
    void businessMixinSkipIsReportedOncePerCustomizerEvenForConcurrentBuilders() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(JacksonConfig.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        var executor = Executors.newFixedThreadPool(4);
        try {
            Jackson2ObjectMapperBuilderCustomizer first = customizer(new WebProperties());
            var tasks =
                    java.util.stream.IntStream.range(0, 8)
                            .<java.util.concurrent.Callable<ObjectMapper>>mapToObj(
                                    ignored ->
                                            () -> {
                                                Jackson2ObjectMapperBuilder builder =
                                                        new Jackson2ObjectMapperBuilder();
                                                builder.mixIn(R.class, BusinessMixin.class);
                                                return build(first, builder);
                                            })
                            .toList();
            for (var result : executor.invokeAll(tasks)) {
                assertBusinessMixin(result.get());
            }
            assertThat(skipMessages(events)).hasSize(1);

            Jackson2ObjectMapperBuilder nextContext = new Jackson2ObjectMapperBuilder();
            nextContext.mixIn(R.class, BusinessMixin.class);
            assertBusinessMixin(build(customizer(new WebProperties()), nextContext));
            assertThat(skipMessages(events)).hasSize(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            logger.detachAppender(events);
            events.stop();
        }
    }

    private static List<ILoggingEvent> skipMessages(ListAppender<ILoggingEvent> events) {
        return events.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .filter(
                        event ->
                                event.getFormattedMessage().contains(BusinessMixin.class.getName()))
                .toList();
    }

    private static void assertBusinessMixin(ObjectMapper mapper) throws Exception {
        assertThat(mapper.findMixInClassFor(R.class)).isEqualTo(BusinessMixin.class);
        var tree = mapper.readTree(mapper.writeValueAsBytes(R.success("payload")));
        assertThat(tree.has("fail")).isTrue();
        assertThat(tree.has("message")).isFalse();
    }

    private static Jackson2ObjectMapperBuilderCustomizer customizer(WebProperties properties) {
        return new JacksonConfig(
                        properties, new DefaultListableBeanFactory().getBeanProvider(Module.class))
                .jackson2ObjectMapperBuilderCustomizer();
    }

    private static ObjectMapper build(
            Jackson2ObjectMapperBuilderCustomizer customizer, Jackson2ObjectMapperBuilder builder) {
        customizer.customize(builder);
        return builder.build();
    }

    abstract static class BusinessMixin {
        @JsonIgnore
        abstract String getMessage();
    }

    static class ChildResponse extends R<String> {}
}
