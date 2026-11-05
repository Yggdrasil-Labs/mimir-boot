package com.yggdrasil.labs.exception.handler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通过真实 MVC 及校验器验证两种明细模式。 */
@SpringBootTest(classes = ValidationDetailsMvcIntegrationTest.Application.class)
@AutoConfigureMockMvc
class ValidationDetailsMvcIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void defaultModePreservesLegacyDataAndClassifiesOrigins() throws Exception {
        mvc.perform(
                        post("/validation/body")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0]").value("name: must not be blank"));
        mvc.perform(post("/validation/form").param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0]").value("must not be blank"));
        mvc.perform(get("/validation/method").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/validation/service"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/validation/cross"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data").doesNotExist());
        assertServerErrors(mvc);
    }

    static void assertServerErrors(MockMvc mvc) throws Exception {
        for (String path :
                new String[] {
                    "/validation/return",
                    "/validation/empty",
                    "/validation/bean",
                    "/validation/mixed"
                }) {
            mvc.perform(get(path))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("10000"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({Controller.class, Service.class})
    static class Application {}

    static class Input {
        @NotBlank(message = "must not be blank")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @RestController
    static class Controller {
        private final Service service;
        private final Validator validator;

        Controller(Service service, Validator validator) {
            this.service = service;
            this.validator = validator;
        }

        @PostMapping("/validation/body")
        String body(@Valid @RequestBody Input input) {
            return input.getName();
        }

        @PostMapping("/validation/form")
        String form(@Valid @ModelAttribute Input input, BindingResult binding)
                throws BindException {
            if (binding.hasErrors()) {
                throw new BindException(binding);
            }
            return input.getName();
        }

        @GetMapping("/validation/method")
        int method(@RequestParam("count") @Min(1) int count) {
            return count;
        }

        @GetMapping("/validation/service")
        int service() {
            return service.check(0);
        }

        @GetMapping("/validation/list")
        int list(@RequestParam("tags") @Size(min = 2) List<String> tags) {
            return tags.size();
        }

        @GetMapping("/validation/map")
        int map(@RequestParam @NotNull Map<String, @NotBlank String> values) {
            return values.size();
        }

        @GetMapping("/validation/dynamic-key")
        int dynamicKey() {
            return service.checkMap(Map.of("secretToken123", "value"));
        }

        @GetMapping("/validation/cross")
        int cross() {
            return service.checkPair(1, 2);
        }

        @GetMapping("/validation/return")
        @Min(1)
        int invalidReturn() {
            return 0;
        }

        @GetMapping("/validation/empty")
        int empty() {
            throw new ConstraintViolationException(Collections.emptySet());
        }

        @GetMapping("/validation/bean")
        int bean() {
            throw new ConstraintViolationException(validator.validate(new Input()));
        }

        @GetMapping("/validation/mixed")
        int mixed() throws NoSuchMethodException {
            Set<ConstraintViolation<?>> violations = new HashSet<>(validator.validate(new Input()));
            violations.addAll(
                    validator
                            .forExecutables()
                            .validateParameters(
                                    service,
                                    Service.class.getMethod("check", int.class),
                                    new Object[] {0}));
            throw new ConstraintViolationException(violations);
        }
    }

    @Validated
    static class Service {
        public int check(@Min(1) int count) {
            return count;
        }

        public int checkMap(@DynamicKey Map<String, String> values) {
            return values.size();
        }

        @CrossArgs
        public int checkPair(int first, int second) {
            return first + second;
        }
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = DynamicKeyValidator.class)
    @interface DynamicKey {
        String message() default "dynamic";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class DynamicKeyValidator
            implements ConstraintValidator<DynamicKey, Map<String, String>> {
        @Override
        public boolean isValid(Map<String, String> value, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("dynamic")
                    .addPropertyNode(value.keySet().iterator().next())
                    .addConstraintViolation();
            return false;
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CrossArgsValidator.class)
    @interface CrossArgs {
        String message() default "cross";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    @SupportedValidationTarget(ValidationTarget.PARAMETERS)
    public static class CrossArgsValidator implements ConstraintValidator<CrossArgs, Object[]> {
        @Override
        public boolean isValid(Object[] value, ConstraintValidatorContext context) {
            return false;
        }
    }
}
