package com.yggdrasil.labs.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yggdrasil.labs.common.response.R;

class WebOptionsIntegrationTest {
    private static final long UNSAFE_INTEGER = 9_007_199_254_740_992L;

    private final WebApplicationContextRunner servletRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    JacksonAutoConfiguration.class,
                                    WebMvcAutoConfiguration.class,
                                    WebAutoConfiguration.class,
                                    MimirDateTimeWebMvcAutoConfiguration.class));

    @Test
    void threeSwitchesAreIndependentInServletApplications() {
        for (boolean date : new boolean[] {false, true}) {
            for (boolean numbers : new boolean[] {false, true}) {
                for (boolean legacy : new boolean[] {false, true}) {
                    servletRunner
                            .withPropertyValues(
                                    "mimir.boot.web.mvc.date-format-enabled=" + date,
                                    "mimir.boot.web.serialization.write-large-integers-as-string="
                                            + numbers,
                                    "mimir.boot.web.json.include-legacy-fail=" + legacy)
                            .run(
                                    context -> {
                                        assertThat(context).hasNotFailed();
                                        assertThat(
                                                        context.getBeansOfType(
                                                                MimirDateTimeWebMvcConfigurer
                                                                        .class))
                                                .hasSize(date ? 1 : 0);
                                        ObjectMapper mapper = context.getBean(ObjectMapper.class);
                                        JsonNode tree =
                                                mapper.readTree(
                                                        mapper.writeValueAsBytes(
                                                                R.success(
                                                                        new Payload(
                                                                                UNSAFE_INTEGER))));
                                        assertThat(tree.path("success").asBoolean()).isTrue();
                                        assertThat(tree.has("fail")).isEqualTo(legacy);
                                        assertThat(tree.path("data").path("value").isTextual())
                                                .isEqualTo(numbers);
                                        assertThat(tree.path("data").path("value").asText())
                                                .isEqualTo(Long.toString(UNSAFE_INTEGER));
                                    });
                }
            }
        }
    }

    @Test
    void disabledWebAndNonServletApplicationsKeepOriginalJacksonBehavior() {
        String[] properties = {
            "mimir.boot.web.mvc.date-format-enabled=true",
            "mimir.boot.web.serialization.write-large-integers-as-string=true",
            "mimir.boot.web.json.include-legacy-fail=false"
        };
        servletRunner
                .withPropertyValues(properties)
                .withPropertyValues("mimir.boot.web.enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBeansOfType(MimirDateTimeWebMvcConfigurer.class))
                                    .isEmpty();
                            assertOriginalMapper(context.getBean(ObjectMapper.class));
                        });

        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                JacksonAutoConfiguration.class,
                                WebAutoConfiguration.class,
                                MimirDateTimeWebMvcAutoConfiguration.class))
                .withPropertyValues(properties)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBeansOfType(MimirDateTimeWebMvcConfigurer.class))
                                    .isEmpty();
                            assertOriginalMapper(context.getBean(ObjectMapper.class));
                        });
    }

    private static void assertOriginalMapper(ObjectMapper mapper) throws Exception {
        JsonNode tree =
                mapper.readTree(mapper.writeValueAsBytes(R.success(new Payload(UNSAFE_INTEGER))));
        assertThat(tree.has("fail")).isTrue();
        assertThat(tree.path("data").path("value").isNumber()).isTrue();
    }

    record Payload(long value) implements Serializable {}
}
