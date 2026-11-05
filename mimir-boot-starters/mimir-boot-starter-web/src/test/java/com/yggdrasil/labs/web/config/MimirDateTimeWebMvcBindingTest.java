package com.yggdrasil.labs.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 验证日期参数通过真实 MVC 参数绑定链使用缺省格式。 */
class MimirDateTimeWebMvcBindingTest {

    private final WebApplicationContextRunner runner =
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MimirDateTimeWebMvcAutoConfiguration.class,
                                    WebMvcAutoConfiguration.class))
                    .withUserConfiguration(BindingController.class);

    @Test
    void bindsDefaultDateTimeThroughDiscoveredAutoConfiguration() {
        new WebApplicationContextRunner()
                .withUserConfiguration(TestApplication.class)
                .run(
                        context -> {
                            MockMvc mvc =
                                    MockMvcBuilders.webAppContextSetup(
                                                    context.getSourceApplicationContext())
                                            .build();
                            mvc.perform(
                                            get("/binding/date-time")
                                                    .param("value", "2026-10-29 13:14:15"))
                                    .andExpect(status().isOk())
                                    .andExpect(content().string("2026-10-29T13:14:15"));
                        });
    }

    @Test
    void bindsDefaultDateAndTimeAndRetainsIsoFallback() {
        runner.run(
                context -> {
                    MockMvc mvc = mvc(context.getSourceApplicationContext());
                    expectValue(mvc, "date", "2026-10-29", "2026-10-29");
                    expectValue(mvc, "time", "13:14:15", "13:14:15");
                    expectValue(mvc, "date-time", "2026-10-29T13:14:15", "2026-10-29T13:14:15");
                    expectValue(mvc, "time", "13:14:15.123", "13:14:15.123");
                });
    }

    @Test
    void usesSerializationPatternsOnlyAsMissingMvcDefaults() {
        runner.withPropertyValues(
                        "mimir.boot.web.serialization.date-format=yyyy/MM/dd",
                        "mimir.boot.web.serialization.time-format=HH_mm_ss",
                        "mimir.boot.web.serialization.date-time-format=yyyy/MM/dd HH_mm_ss")
                .run(
                        context -> {
                            MockMvc mvc = mvc(context.getSourceApplicationContext());
                            expectValue(mvc, "date", "2026/10/29", "2026-10-29");
                            expectValue(mvc, "time", "13_14_15", "13:14:15");
                            expectValue(
                                    mvc, "date-time", "2026/10/29 13_14_15", "2026-10-29T13:14:15");
                            expectValue(
                                    mvc, "date-time", "2026-10-29T13:14:15", "2026-10-29T13:14:15");
                        });
    }

    @Test
    void preservesEachExplicitBootPattern() {
        runner.withPropertyValues(
                        "spring.mvc.format.date=dd/MM/yyyy",
                        "spring.mvc.format.time=HH_mm_ss",
                        "spring.mvc.format.date-time=dd/MM/yyyy HH_mm_ss")
                .run(
                        context -> {
                            MockMvc mvc = mvc(context.getSourceApplicationContext());
                            expectValue(mvc, "date", "29/10/2026", "2026-10-29");
                            expectValue(mvc, "time", "13_14_15", "13:14:15");
                            expectValue(
                                    mvc, "date-time", "29/10/2026 13_14_15", "2026-10-29T13:14:15");
                        });
    }

    @Test
    void overridesPerTypeAndTreatsBlankBootPatternsAsMissing() {
        runner.withPropertyValues(
                        "spring.mvc.format.date=dd/MM/yyyy",
                        "spring.mvc.format.time= ",
                        "spring.mvc.format.date-time=")
                .run(
                        context -> {
                            MockMvc mvc = mvc(context.getSourceApplicationContext());
                            expectValue(mvc, "date", "29/10/2026", "2026-10-29");
                            expectValue(mvc, "time", "13:14:15", "13:14:15");
                            expectValue(
                                    mvc, "date-time", "2026-10-29 13:14:15", "2026-10-29T13:14:15");
                        });
    }

    @Test
    void preservesBootIsoAliasAndSkipsUnusedMimirPattern() {
        runner.withPropertyValues(
                        "spring.mvc.format.date-time=iso",
                        "mimir.boot.web.serialization.date-time-format=invalid[")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            FormattingConversionService service =
                                    context.getBean(
                                            "mvcConversionService",
                                            FormattingConversionService.class);
                            assertThat(
                                            service.convert(
                                                    LocalDateTime.of(2026, 10, 29, 13, 14, 15),
                                                    String.class))
                                    .isEqualTo("2026-10-29T13:14:15");
                        });
    }

    @Test
    void preservesParameterAndModelAttributeDateTimeFormat() {
        runner.run(
                context -> {
                    MockMvc mvc = mvc(context.getSourceApplicationContext());
                    expectValue(mvc, "annotated", "29.10.2026 13_14_15", "2026-10-29T13:14:15");
                    expectValue(mvc, "form", "29.10.2026", "2026-10-29");
                });
    }

    @Test
    void consumerConverterBeanOverridesMimirDefault() {
        runner.withUserConfiguration(ConverterConfiguration.class)
                .run(
                        context ->
                                expectValue(
                                        mvc(context.getSourceApplicationContext()),
                                        "date-time",
                                        "consumer",
                                        "2030-01-02T03:04:05"));
    }

    @Test
    void consumerConfigurerWithoutOrderOverridesMimirDefault() {
        runner.withUserConfiguration(ConsumerMvcConfiguration.class)
                .run(
                        context ->
                                expectValue(
                                        mvc(context.getSourceApplicationContext()),
                                        "date-time",
                                        "consumer",
                                        "2030-01-02T03:04:05"));
    }

    @Test
    void invalidAndRequiredEmptyInputsUseStandardMvcErrors() {
        runner.run(
                context -> {
                    MockMvc mvc = mvc(context.getSourceApplicationContext());
                    for (String path : new String[] {"date", "time", "date-time"}) {
                        mvc.perform(get("/binding/" + path).param("value", "not-a-date"))
                                .andExpect(status().isBadRequest());
                        mvc.perform(get("/binding/" + path).param("value", ""))
                                .andExpect(status().isBadRequest());
                    }
                    expectValue(mvc, "optional", "", "empty");
                    mvc.perform(get("/binding/date-time")).andExpect(status().isBadRequest());
                });
    }

    @Test
    void disabledFeatureAndWebSwitchRetainBootBinding() {
        for (String property :
                new String[] {
                    "mimir.boot.web.mvc.date-format-enabled=false", "mimir.boot.web.enabled=false"
                }) {
            runner.withPropertyValues(property)
                    .run(
                            context -> {
                                MockMvc mvc = mvc(context.getSourceApplicationContext());
                                mvc.perform(
                                                get("/binding/date-time")
                                                        .param("value", "2026-10-29 13:14:15"))
                                        .andExpect(status().isBadRequest());
                                expectValue(
                                        mvc,
                                        "date-time",
                                        "2026-10-29T13:14:15",
                                        "2026-10-29T13:14:15");
                            });
        }
    }

    @Test
    void enableWebMvcRetainsBusinessBinding() {
        runner.withUserConfiguration(OwnedMvcConfiguration.class)
                .run(
                        context -> {
                            MockMvc mvc = mvc(context.getSourceApplicationContext());
                            mvc.perform(
                                            get("/binding/date-time")
                                                    .param("value", "2026-10-29 13:14:15"))
                                    .andExpect(status().isBadRequest());
                            expectValue(
                                    mvc, "date-time", "2026-10-29T13:14:15", "2026-10-29T13:14:15");
                        });
    }

    @Test
    void traditionalDateAndCalendarMatchBootBaseline() {
        for (String[] properties : new String[][] {{}, {"spring.mvc.format.date=yyyy/MM/dd"}}) {
            runner.withPropertyValues(properties)
                    .run(
                            enabled -> {
                                List<String> actual =
                                        legacyResponses(mvc(enabled.getSourceApplicationContext()));
                                runner.withPropertyValues(properties)
                                        .withPropertyValues(
                                                "mimir.boot.web.mvc.date-format-enabled=false")
                                        .run(
                                                baseline ->
                                                        assertThat(actual)
                                                                .isEqualTo(
                                                                        legacyResponses(
                                                                                mvc(
                                                                                        baseline
                                                                                                .getSourceApplicationContext()))));
                            });
        }
    }

    private static List<String> legacyResponses(MockMvc mvc) throws Exception {
        List<String> responses = new ArrayList<>();
        for (String type : new String[] {"legacy-date", "legacy-calendar"}) {
            for (String value : new String[] {"2026/10/29", "2026-10-29", "not-a-date"}) {
                var response =
                        mvc.perform(get("/binding/" + type).locale(Locale.US).param("value", value))
                                .andReturn()
                                .getResponse();
                responses.add(response.getStatus() + ":" + response.getContentAsString());
            }
        }
        return responses;
    }

    private static MockMvc mvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static void expectValue(MockMvc mvc, String path, String value, String expected)
            throws Exception {
        mvc.perform(get("/binding/" + path).param("value", value))
                .andExpect(status().isOk())
                .andExpect(content().string(expected));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(BindingController.class)
    static class TestApplication {}

    @Configuration(proxyBeanMethods = false)
    static class ConverterConfiguration {

        @Bean
        Converter<String, LocalDateTime> businessDateTimeConverter() {
            return new BusinessDateTimeConverter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerMvcConfiguration implements WebMvcConfigurer {

        @Override
        public void addFormatters(FormatterRegistry registry) {
            registry.addConverter(new BusinessDateTimeConverter());
        }
    }

    static class BusinessDateTimeConverter implements Converter<String, LocalDateTime> {

        @Override
        public LocalDateTime convert(String source) {
            if (!"consumer".equals(source)) {
                throw new IllegalArgumentException("业务日期仅接受 consumer");
            }
            return LocalDateTime.of(2030, 1, 2, 3, 4, 5);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class OwnedMvcConfiguration {}

    @RestController
    static class BindingController {

        @GetMapping("/binding/date-time")
        String dateTime(@RequestParam("value") LocalDateTime value) {
            return value.toString();
        }

        @GetMapping("/binding/date")
        String date(@RequestParam("value") LocalDate value) {
            return value.toString();
        }

        @GetMapping("/binding/time")
        String time(@RequestParam("value") LocalTime value) {
            return value.toString();
        }

        @GetMapping("/binding/optional")
        String optional(@RequestParam(value = "value", required = false) LocalDateTime value) {
            return value == null ? "empty" : value.toString();
        }

        @GetMapping("/binding/annotated")
        String annotated(
                @RequestParam("value") @DateTimeFormat(pattern = "dd.MM.yyyy HH_mm_ss")
                        LocalDateTime value) {
            return value.toString();
        }

        @GetMapping("/binding/form")
        String form(@ModelAttribute DateForm form) {
            return form.getValue().toString();
        }

        @GetMapping("/binding/legacy-date")
        String legacyDate(@RequestParam("value") Date value) {
            return Long.toString(value.getTime());
        }

        @GetMapping("/binding/legacy-calendar")
        String legacyCalendar(@RequestParam("value") Calendar value) {
            return Long.toString(value.getTimeInMillis());
        }
    }

    static class DateForm {

        @DateTimeFormat(pattern = "dd.MM.yyyy")
        private LocalDate value;

        public LocalDate getValue() {
            return value;
        }

        public void setValue(LocalDate value) {
            this.value = value;
        }
    }
}
