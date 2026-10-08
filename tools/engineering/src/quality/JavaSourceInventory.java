import com.sun.source.tree.ClassTree;
import com.sun.source.util.JavacTask;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** 使用纯 JDK 语法树建立顶层及成员类型的二进制名称清单，不加载或执行测试。 */
class JavaSourceInventory {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("源码清单需要输入和输出 XML 路径");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var request = factory.newDocumentBuilder().parse(Path.of(args[0]).toFile());
        if (!request.getDocumentElement().getTagName().equals("request")) {
            throw new IllegalArgumentException("源码清单输入缺少 request 根元素");
        }
        var files = new ArrayList<String>();
        var sources = request.getElementsByTagName("source");
        for (int i = 0; i < sources.getLength(); i++) files.add(sources.item(i).getTextContent());
        var output = factory.newDocumentBuilder().newDocument();
        var root = output.createElement("source-inventory");
        output.appendChild(root);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("源码声明清单需要完整 JDK");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "-encoding", "UTF-8"), null,
                    manager.getJavaFileObjectsFromStrings(files));
            for (var unit : task.parse()) {
                var prefix = unit.getPackageName() == null ? "" : unit.getPackageName() + ".";
                var source = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize().toString();
                for (var declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree type) {
                        appendType(output, root, source, prefix + type.getSimpleName(), type);
                    }
                }
            }
        }
        if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
            throw new IllegalStateException("无法解析 Java 源码声明：" + diagnostics.getDiagnostics());
        }
        var transformers = TransformerFactory.newInstance();
        transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        transformers.newTransformer().transform(new DOMSource(output), new StreamResult(Path.of(args[1]).toFile()));
    }

    private static void appendType(org.w3c.dom.Document output, org.w3c.dom.Element root,
            String source, String binaryName, ClassTree type) {
        var element = output.createElement("declaration");
        element.setAttribute("source", source);
        element.setAttribute("binaryName", binaryName);
        root.appendChild(element);
        // 只遍历命名成员声明；局部类和匿名类的编译编号不能从源码可靠推导。
        for (var member : type.getMembers()) {
            if (member instanceof ClassTree nested) {
                appendType(output, root, source, binaryName + "$" + nested.getSimpleName(), nested);
            }
        }
    }
}
