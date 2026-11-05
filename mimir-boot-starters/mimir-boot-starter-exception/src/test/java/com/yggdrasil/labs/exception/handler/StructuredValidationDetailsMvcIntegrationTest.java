package com.yggdrasil.labs.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@SpringBootTest(
        classes = ValidationDetailsMvcIntegrationTest.Application.class,
        properties = "mimir.boot.exception.validation.structured-details-enabled=true")
@AutoConfigureMockMvc
@Import(StructuredValidationDetailsMvcIntegrationTest.PathController.class)
class StructuredValidationDetailsMvcIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void structuredBindingHidesMapKeysThatImitateDeclaredListPaths() throws Exception {
        String body =
                mvc.perform(
                                post("/validation/path-body")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"mapping\":{\"x].items[987654321\":{\"name\":\"\"}}}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.data.totalCount").value(1))
                        .andExpect(jsonPath("$.data.errors[0].source").value("FIELD"))
                        .andExpect(jsonPath("$.data.errors[0].path").value("mapping[*].name"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain("987654321");
        mvc.perform(
                        post("/validation/path-body")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mapping\":{\"ordinary\":{\"name\":\"valid\",\"items\":[{\"name\":\"\"}]}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].path").value("mapping[*].items[0].name"));
    }

    @Test
    void structuredExecutableValidationUsesUnknownFieldLocationForObjectDeclarations()
            throws Exception {
        mvc.perform(get("/validation/path-polymorphic"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].source").value("FIELD"))
                .andExpect(jsonPath("$.data.errors[0].path").value("$"))
                .andExpect(jsonPath("$.data.errors[0].parameterIndex").value(0));
    }

    @Test
    void structuredNestedObjectFieldsUseUnknownLocationsAcrossExecutableAndMethodValidation()
            throws Exception {
        mvc.perform(get("/validation/path-nested-executable"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        result ->
                                assertThat(result.getResolvedException())
                                        .isInstanceOf(ConstraintViolationException.class))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].source").value("FIELD"))
                .andExpect(jsonPath("$.data.errors[0].path").value("$"))
                .andExpect(jsonPath("$.data.errors[0].parameterIndex").value(0));
        mvc.perform(
                        post("/validation/path-nested-method")
                                .param("count", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        result ->
                                assertThat(result.getResolvedException())
                                        .isInstanceOf(HandlerMethodValidationException.class))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].source").value("FIELD"))
                .andExpect(jsonPath("$.data.errors[0].path").value("$"))
                .andExpect(jsonPath("$.data.errors[0].parameterIndex").value(0));
    }

    @Test
    void structuredDetailsCoverAllInputEntrancesWithoutInterpolatedMessages() throws Exception {
        String body =
                mvc.perform(
                                post("/validation/body")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"\"}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.data.totalCount").value(1))
                        .andExpect(jsonPath("$.data.truncated").value(false))
                        .andExpect(jsonPath("$.data.errors[0].path").value("name"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain("must not be blank");
        mvc.perform(post("/validation/form").param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].path").value("name"));
        mvc.perform(get("/validation/method").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.totalCount").value(1));
        mvc.perform(get("/validation/service"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.totalCount").value(1));
        mvc.perform(get("/validation/list").param("tags", "one"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errors[0].path").value("tags"));
        String map =
                mvc.perform(get("/validation/map").param("secretToken123", ""))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.data.errors[0].path").value("values[*]"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(map).doesNotContain("secretToken123");
        mvc.perform(get("/validation/cross"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errors[0].source").value("OBJECT"))
                .andExpect(jsonPath("$.data.errors[0].path").value("$"));
        String dynamic =
                mvc.perform(get("/validation/dynamic-key"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.data.totalCount").value(1))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(dynamic).doesNotContain("secretToken123");
        ValidationDetailsMvcIntegrationTest.assertServerErrors(mvc);
    }

    @RestController
    static class PathController {
        private final Validator validator;

        PathController(Validator validator) {
            this.validator = validator;
        }

        @PostMapping("/validation/path-body")
        String body(@Valid @RequestBody MappedInput input) {
            return "ok";
        }

        @GetMapping("/validation/path-polymorphic")
        String polymorphic() throws NoSuchMethodException {
            throw new ConstraintViolationException(
                    validator
                            .forExecutables()
                            .validateParameters(
                                    this,
                                    PathController.class.getDeclaredMethod("accept", Object.class),
                                    new Object[] {new MappedItem()}));
        }

        @GetMapping("/validation/path-nested-executable")
        String nestedExecutable() throws NoSuchMethodException {
            throw new ConstraintViolationException(
                    validator
                            .forExecutables()
                            .validateParameters(
                                    this,
                                    PathController.class.getDeclaredMethod(
                                            "acceptNested", OpaqueInput.class),
                                    new Object[] {new OpaqueInput()}));
        }

        @PostMapping("/validation/path-nested-method")
        String nestedMethod(
                @Valid @RequestBody OpaqueInput input, @RequestParam @Min(1) int count) {
            return "ok";
        }

        public void accept(@Valid Object input) {}

        public void acceptNested(@Valid OpaqueInput input) {}
    }

    static class OpaqueInput {
        @Valid public Object payload = new MappedItem();

        public Object getPayload() {
            return payload;
        }
    }

    static class MappedInput {
        @Valid public Map<String, MappedItem> mapping;

        public Map<String, MappedItem> getMapping() {
            return mapping;
        }
    }

    static class MappedItem {
        @NotBlank public String name;
        @Valid public List<Leaf> items;

        public String getName() {
            return name;
        }

        public List<Leaf> getItems() {
            return items;
        }
    }

    static class Leaf {
        @NotBlank public String name;

        public String getName() {
            return name;
        }
    }
}
