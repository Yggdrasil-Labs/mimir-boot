import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.DiscoveryFilter;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryListener;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.SelectorResolutionResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.w3c.dom.Element;

/** 用项目测试引擎发现构建模型选中的测试类，不执行测试。 */
class TestInventory {
    public static void main(String[] args) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(Path.of(args[0]).toFile());
        var output = factory.newDocumentBuilder().newDocument();
        var root = output.createElement("inventory");
        output.appendChild(root);
        var selectors = new ArrayList<DiscoverySelector>();
        var candidates = document.getElementsByTagName("candidate");
        for (int i = 0; i < candidates.getLength(); i++) {
            var name = candidates.item(i).getTextContent();
            var file = Path.of(args[2]).resolve(name.replace('.', '/') + ".class");
            if (!Files.isRegularFile(file)) throw new IllegalStateException("缺少编译测试类：" + name);
            selectors.add(DiscoverySelectors.selectClass(name));
        }
        var defaults = new Properties();
        try (var resource = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("junit-platform.properties")) {
            if (resource != null) defaults.load(resource);
        }
        var parameters = new ConfigurationParameters() {
            public Optional<String> get(String key) {
                return Optional.ofNullable(System.getProperty(key, defaults.getProperty(key)));
            }
            public Optional<Boolean> getBoolean(String key) { return get(key).map(Boolean::parseBoolean); }
            public int size() { return defaults.size(); }
            public Set<String> keySet() { return defaults.stringPropertyNames(); }
        };
        var request = new EngineDiscoveryRequest() {
            public <T extends DiscoverySelector> List<T> getSelectorsByType(Class<T> type) {
                return selectors.stream().filter(type::isInstance).map(type::cast).toList();
            }
            public <T extends DiscoveryFilter<?>> List<T> getFiltersByType(Class<T> type) {
                return List.of();
            }
            public ConfigurationParameters getConfigurationParameters() { return parameters; }
            public EngineDiscoveryListener getDiscoveryListener() {
                return new EngineDiscoveryListener() {
                    public void selectorProcessed(UniqueId id, DiscoverySelector selector,
                            SelectorResolutionResult result) {
                        if (result.getStatus() == SelectorResolutionResult.Status.FAILED) {
                            throw new IllegalStateException("测试选择器解析失败：" + selector,
                                    result.getThrowable().orElse(null));
                        }
                    }
                };
            }
        };
        var found = new TreeSet<String>();
        int engines = 0;
        for (var engine : ServiceLoader.load(TestEngine.class)) {
            var tree = engine.discover(request, UniqueId.forEngine(engine.getId()));
            if (!engine.getId().equals("junit-jupiter")) {
                if (!tree.getChildren().isEmpty()) {
                    throw new IllegalStateException("暂不支持报告清单发现引擎：" + engine.getId());
                }
                continue;
            }
            engines++;
            collect(tree, found);
        }
        if (engines != 1) throw new IllegalStateException("需要唯一 JUnit Jupiter 测试引擎");
        for (var name : found) append(output, root, name);
        var transformers = TransformerFactory.newInstance();
        transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        transformers.newTransformer().transform(new DOMSource(output), new StreamResult(Path.of(args[1]).toFile()));
    }

    private static void collect(TestDescriptor descriptor, Set<String> classes) {
        // 工厂与参数化测试在发现阶段是方法容器，仍必须有执行报告。
        if (descriptor.getSource().orElse(null) instanceof MethodSource method) {
            var name = method.getJavaClass().getName();
            var current = descriptor;
            while (current != null) {
                if (current.getSource().orElse(null) instanceof ClassSource type) {
                    name = type.getJavaClass().getName();
                }
                current = current.getParent().orElse(null);
            }
            classes.add(name);
        }
        for (var child : descriptor.getChildren()) collect(child, classes);
    }

    private static void append(org.w3c.dom.Document document, Element root, String name) {
        var element = document.createElement("class");
        element.setTextContent(name);
        root.appendChild(element);
    }
}
