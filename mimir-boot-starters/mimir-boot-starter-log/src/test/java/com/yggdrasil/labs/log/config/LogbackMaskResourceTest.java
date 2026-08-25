package com.yggdrasil.labs.log.config;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LogbackMaskResourceTest extends BaseUnitTest {

    @Test
    void accessAndSqlAppendersUseMaskConverter() throws Exception {
        try (InputStream resource = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertNotNull(resource);
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resource);
            Map<String, String> patterns = patternsByAppender(document);

            assertEquals(true, patterns.get("FILE_ACCESS").contains("%mask"));
            assertEquals(true, patterns.get("FILE_SQL").contains("%mask"));
        }
    }

    private Map<String, String> patternsByAppender(Document document) {
        Map<String, String> patterns = new HashMap<>();
        var appenders = document.getElementsByTagName("appender");
        for (int index = 0; index < appenders.getLength(); index++) {
            Element appender = (Element) appenders.item(index);
            var patternNodes = appender.getElementsByTagName("pattern");
            if (patternNodes.getLength() > 0) {
                patterns.put(appender.getAttribute("name"), patternNodes.item(0).getTextContent());
            }
        }
        return patterns;
    }
}
