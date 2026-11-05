package com.yggdrasil.labs.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class ExceptionHttpMetadataTest {
    @Test
    void connectionNamesExcludeFieldsAcrossBothHeaderSources() {
        HttpHeaders exceptionHeaders = new HttpHeaders();
        exceptionHeaders.add(HttpHeaders.CONNECTION, "X-From-Factory");
        exceptionHeaders.add("X-From-Exception", "exception-value");
        exceptionHeaders.add("X-Keep", "exception");
        HttpHeaders factoryHeaders = new HttpHeaders();
        factoryHeaders.add(HttpHeaders.CONNECTION, "X-From-Exception");
        factoryHeaders.add("X-From-Factory", "factory-value");
        factoryHeaders.add("X-Keep", "factory");

        HttpHeaders result =
                new ExceptionHttpMetadata(
                                HttpStatus.BAD_REQUEST, exceptionHeaders, "code", "message")
                        .mergeHeaders(factoryHeaders);

        assertThat(result)
                .doesNotContainKeys(HttpHeaders.CONNECTION, "X-From-Factory", "X-From-Exception");
        assertThat(result.get("X-Keep")).containsExactly("exception");
    }
}
