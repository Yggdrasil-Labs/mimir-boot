package com.yggdrasil.labs.common.page;

import static com.yggdrasil.labs.common.CompilationSupport.compile;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.yggdrasil.labs.common.CompilationSupport.CompilationResult;

class PageMappingCompilationTest {

    @Test
    void map_accepts_wildcard_function_with_serializable_output(@TempDir Path outputDirectory)
            throws Exception {
        CompilationResult result =
                compile(
                        "example.WildcardMapConsumer",
                        """
package example;

import com.yggdrasil.labs.common.page.PageResult;
import java.util.function.Function;

final class WildcardMapConsumer {
    void use(PageResult<Integer> page) {
        Function<Number, String> mapper = String::valueOf;
        PageResult<String> mapped = page.map(mapper);
        PageResult<CharSequenceHolder> holders = page.map(v -> new CharSequenceHolder());
    }
}

final class CharSequenceHolder implements java.io.Serializable {}
""",
                        outputDirectory);

        assertTrue(result.success(), result::diagnostics);
    }

    @Test
    void map_to_non_serializable_output_does_not_compile(@TempDir Path outputDirectory)
            throws Exception {
        CompilationResult result =
                compile(
                        "example.NonSerializableMapConsumer",
                        """
package example;

import com.yggdrasil.labs.common.page.PageResult;

final class Plain {}

final class NonSerializableMapConsumer {
    void use(PageResult<Integer> page) {
        page.map(v -> new Plain());
    }
}
""",
                        outputDirectory);

        assertFalse(result.success(), "PageResult.map 输出必须保持 Serializable 上界");
    }

    @Test
    void cola_conversion_allows_non_serializable_output(@TempDir Path outputDirectory)
            throws Exception {
        CompilationResult result =
                compile(
                        "example.ColaConsumer",
                        """
package example;

import com.alibaba.cola.dto.PageResponse;
import com.yggdrasil.labs.common.page.ColaPageConverters;
import com.yggdrasil.labs.common.page.PageResult;
import java.util.function.Function;

final class Plain {}

final class ColaConsumer {
    void use(PageResult<Integer> page) {
        Function<Object, Plain> mapper = v -> new Plain();
        PageResponse<Plain> plain = ColaPageConverters.toCola(page, mapper);
        PageResponse<Integer> same = ColaPageConverters.toCola(page);
    }
}
""",
                        outputDirectory);

        assertTrue(result.success(), result::diagnostics);
    }
}
