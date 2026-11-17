package com.yggdrasil.labs.log.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.core.ContextBase;

class StructuredMaskSupportTest {

    @BeforeEach
    @AfterEach
    void reset() {
        SensitiveDataConverter.clearCustomPatterns();
        SensitiveDataConverterInternals.resetForTest(null);
    }

    @Test
    void bindsContextRulesAndRetainsThemWhenProgrammaticRulesInvalidateSnapshot() {
        ContextBase context = new ContextBase();
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        context.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, "\\d{3}");
        StructuredMaskSupport.bindContext(context);

        StructuredMaskSupport.MaskRules first = StructuredMaskSupport.currentRules();
        assertThat(first.isSensitiveKey("userPassword")).isTrue();
        assertThat(first.maskValue("abc123xyz")).isEqualTo("abc****xyz");
        assertThat(first.maskText("password=alpha")).isEqualTo("password=****");

        SensitiveDataConverter.addCustomPattern("xyz");
        StructuredMaskSupport.MaskRules second = StructuredMaskSupport.currentRules();
        assertThat(second).isNotSameAs(first);
        assertThat(second.isSensitiveKey("password")).isTrue();
        assertThat(second.maskValue("abc123xyz")).isEqualTo("abc********");
        assertThat(second.replacement()).isEqualTo("****");
        assertThat(first.maskValue("abc123xyz")).isEqualTo("abc****xyz");
    }

    @Test
    void supportsEmptyRulesAndDoesNotTreatOldTextRegexAsAFieldNameRule() {
        ContextBase context = new ContextBase();
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "");
        context.putProperty(
                SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, "accessCode=\\w+");
        StructuredMaskSupport.bindContext(context);

        StructuredMaskSupport.MaskRules rules = StructuredMaskSupport.currentRules();
        assertThat(rules.isSensitiveKey("password")).isFalse();
        assertThat(rules.maskText("accessCode=XYZ")).isEqualTo("accessCode=****");
        assertThat(rules.maskValue("XYZ")).isEqualTo("XYZ");
        assertThat(rules.isSensitiveKey("accessCode")).isFalse();
    }

    @Test
    void keepsPresetFieldSuffixesCaseInsensitive() {
        SensitiveDataConverter.publishConfiguration(List.of("token"), List.of(), "[redacted]");

        assertThat(StructuredMaskSupport.currentRules().isSensitiveKey("refreshTOKEN")).isTrue();
        assertThat(StructuredMaskSupport.currentRules().isSensitiveKey("tokenValue")).isFalse();
    }
}
