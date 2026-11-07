# Mimir Boot Code Quality

本模块提供 PMD 7.17.0 的 Java 质量规则，资源路径为 `mimir-boot/pmd/main-ruleset.xml` 和 `mimir-boot/pmd/test-ruleset.xml`。它采用 `jar` 打包，不含 Java 类，发布 POM 不声明依赖，不进入 BOM。普通依赖不会引入 PMD。

同一个 jar 还提供独立 Maven 插件入口：从官方 `org.apache.maven.plugins:maven-pmd-plugin:3.28.0` 生成描述符，保留原四个目标，并派生 `test-pmd`、`test-check`、`test-cpd`、`test-cpd-check`，使用 `mimir-quality` 前缀。测试入口将分析根绑定到 Maven 实际测试源码根，并保留测试类路径；所有目标仍复用官方实现。请使用生命周期绑定或完整坐标，不依赖短前缀自动解析。

## 继承 Parent

继承同版本 `mimir-boot-parent` 后执行：

```bash
mvn -Pprecheck verify

# 单独运行，并选中已配置的 execution
mvn io.github.yggdrasil-labs:mimir-boot-code-quality:YOUR_RELEASE_VERSION:check@pmd -Dpmd.skip=false
mvn io.github.yggdrasil-labs:mimir-boot-code-quality:YOUR_RELEASE_VERSION:test-check@test-pmd -Dpmd.skip=false
mvn io.github.yggdrasil-labs:mimir-boot-code-quality:YOUR_RELEASE_VERSION:cpd-check@cpd -Dcpd.skip=false
mvn io.github.yggdrasil-labs:mimir-boot-code-quality:YOUR_RELEASE_VERSION:test-cpd-check@test-cpd -Dcpd.skip=false
```

默认和 `dev`、`prod` 模式跳过两类检查；`precheck`、`ci` 模式运行并逐条报告，默认不因违规失败。设置 `pmd.failOnViolation=true`、`cpd.failOnViolation=true` 可分别阻断。PMD 分析错误是否阻断由 `pmd.skipPmdError` 控制，严格模式应设为 `false`。

XML 报告在 `target/mimir-quality/pmd.xml`（主源码）、`target/mimir-quality/tests/pmd.xml`（测试）、`target/mimir-quality/cpd.xml`（主源码）和 `target/mimir-quality/tests/cpd.xml`（测试）。下游已有官方插件使用各自报告目录；不要将两套插件指向相同位置。开关及已有官方插件的迁移方式见 [Parent 说明](../mimir-boot-parent/README.md#pmdcpd-检查与升级)。

## 只导入 BOM

BOM 不管理插件依赖版本，也不自动启用检查。以下为仅检查主源码的独立接入示例，自行声明官方插件并显式指定规则 jar 版本：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <version>3.28.0</version>
    <dependencies>
        <dependency>
            <groupId>io.github.yggdrasil-labs</groupId>
            <artifactId>mimir-boot-code-quality</artifactId>
            <version>YOUR_RELEASE_VERSION</version>
        </dependency>
    </dependencies>
    <configuration>
        <rulesets><ruleset>mimir-boot/pmd/main-ruleset.xml</ruleset></rulesets>
        <targetJdk>17</targetJdk>
        <includeTests>false</includeTests>
        <excludeRoots><excludeRoot>${project.build.directory}/generated-sources</excludeRoot></excludeRoots>
        <linkXRef>false</linkXRef>
        <analysisCache>true</analysisCache>
        <printFailingErrors>true</printFailingErrors>
        <failurePriority>5</failurePriority>
        <minimumTokens>100</minimumTokens>
    </configuration>
    <executions>
        <execution>
            <id>pmd</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
        </execution>
        <execution>
            <id>cpd</id>
            <phase>verify</phase>
            <goals><goal>cpd-check</goal></goals>
        </execution>
    </executions>
</plugin>
```

此例只使用官方插件，插件级参数供其分叉目标共享；报告默认为 `target/pmd.xml`、`target/cpd.xml`。是否跳过或阻断使用官方原生属性自行设置，例如 `-Dpmd.failOnViolation=false -Dcpd.failOnViolation=false`。

## 规则边界与替换

`main-ruleset.xml` 直接定义 41 条生产规则，检查公开类型和方法的 Javadoc、Java 命名和常见坏味道。扫描范围由 Maven 实际源码根决定，支持自定义目录与追加源码根；规则 XML 不过滤路径。默认排除 `target/generated-sources`，CPD 阈值为 100 tokens。

`test-ruleset.xml` 通过 `test-pmd` 入口检查 Maven 实际测试源码根，排除 `target/generated-test-sources`，启用 8 条规则：

- 测试断言与声明：`UnnecessaryBooleanAssertion`、`DetachedTestCase`、`SimplifiableTestAssertion`、`JUnit5TestShouldBePackagePrivate`。
- 通用错误：`UnusedLocalVariable`、`BrokenNullCheck`、`EmptyCatchBlock`、`ImplicitSwitchFallThrough`。

Parent 的两个 PMD 执行共用 `pmd.skip` 与 `pmd.failOnViolation`：默认跳过，启用后默认只报告；CPD 的主、测试检查也共用 `cpd.skip` 与 `cpd.failOnViolation`，分别比较各自源码，阈值均为 100 tokens。仅关闭测试检查时，分别将 `test-pmd`、`test-cpd` execution 的 `phase` 设为 `none`。

Javadoc 要求不覆盖构造器、`@Override` 方法、getter/setter、protected 方法、字段及有效可见性非 public 的类型或方法。record 和注解类型本身暂无类型级检查；不核对 `@param` 等标签与签名的一致性、不检查首句或标签描述是否为空，也没有 record 组件命名规则。

替换主源码规则时覆盖独立插件的 `pmd` execution；替换测试规则时覆盖 `test-pmd`。以下为主源码示例：

```xml
<plugin>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-code-quality</artifactId>
    <executions>
        <execution>
            <id>pmd</id>
            <configuration>
                <rulesets combine.self="override"><ruleset>config/pmd.xml</ruleset></rulesets>
            </configuration>
        </execution>
    </executions>
</plugin>
```

只使用官方插件时覆盖其 `<rulesets>`。IDE 的 PMD 插件可从本 jar 提取规则文件，或将 jar 加入插件的规则 classpath；IDE 必须支持 PMD 7.17.0 的规则和 XPath，具体接入入口以对应 IDE 插件为准。

## 版本、仓库与构建

Parent 发布时把 `mimir-boot-code-quality.version` 固定为 Parent 的实际版本。下游只可覆盖为**描述符来源版本同为 3.28.0** 的规则制品；跨来源升级必须升级 Parent 与整套质量组件。不支持单独覆盖官方实现版本，也不支持在插件依赖中替换 PMD 版本。

内网镜像需同步 `io.github.yggdrasil-labs:mimir-boot-code-quality`、官方 `org.apache.maven.plugins:maven-pmd-plugin:3.28.0` 及其传递依赖。插件解析使用插件仓库，只有 `<repositories>` 不够；请配置镜像或对应的 `<pluginRepositories>`。

```bash
# 不带 -am 构建其他单模块前，先安装规则制品
./mvnw -pl mimir-boot-code-quality clean install
```

构建会校验官方描述符 SHA-256、来源 POM 元信息与根节点坐标，并检查生成坐标。来源版本与摘要在本模块 POM 中，生成元信息位于 `META-INF/mimir-boot/pmd-source.properties`；升级时必须同步 Parent 官方实现。生成过程保留 Apache LICENSE、NOTICE 并注明修改。主 jar 不包含官方实现类；源码与[Javadoc 说明](./src/main/javadoc/README.md)附件随模块发布。
