package com.yggdrasil.labs.web.advice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.common.response.R;

/**
 * 验证下游定制 WebMvcRegistrations 时响应增强器仍会接入默认 MVC 处理器。
 *
 * @author Yggdrasil Labs
 * @since 2.2.1
 */
@SpringBootTest(
        classes = ResponseBodyEnhancerWebMvcRegistrationsMvcIntegrationTest.TestApplication.class,
        properties = "logging.level.root=OFF")
@AutoConfigureMockMvc
class ResponseBodyEnhancerWebMvcRegistrationsMvcIntegrationTest {

    private static final String TRACE_ID = "mvc-registrations-trace";

    @Autowired private MockMvc mockMvc;

    @Test
    void preservesResponseEnhancementWhenApplicationSuppliesWebMvcRegistrations() throws Exception {
        mockMvc.perform(
                        get("/response-enhancer-registrations/success")
                                .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ResponseController.class, CustomWebMvcRegistrationsConfiguration.class})
    static class TestApplication {}

    @RestController
    static class ResponseController {

        @GetMapping("/response-enhancer-registrations/success")
        R<String> success() {
            return R.success("payload");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomWebMvcRegistrationsConfiguration {

        @Bean
        @Primary
        WebMvcRegistrations applicationWebMvcRegistrations() {
            return new WebMvcRegistrations() {};
        }
    }
}
