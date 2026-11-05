---
version: v2.4.0
status: draft
created: 2026-11-02
updated: 2026-11-02
---

# 代码质量工具链实施计划

> 推荐方案，未实施。本计划是静态实施输入；任务列表不表示已执行或已通过验收。

导航：[版本入口](../brainstorm.md) · [方案大纲](./brainstorm.md) · [规格](./spec.md) · [设计](./design.md) · [架构 RFC](../../../design-docs/arch-code-quality-toolchain.md)

## 目标

落实 Q-01～Q-06，满足规格 CQ-01～CQ-10：

- 新增规则 jar 模块；
- Parent 接入 PMD（含注释与命名规则）、CPD；
- 本仓库严格执行并完成存量治理；
- starter-test 提供 ArchUnit 规则库，各模块落地依赖方向测试；
- 同步质量门禁、发布契约、升级说明与文档。

## 必要上下文

- 基线分支 `feat/v2.4.0-parent-build`（`7cd4684`），在 worktree 分支 `feat/v2.4.0-quality-tooling` 实施。Parent 需求对 Parent POM 的改动是本计划的前提；两边都改 `mimir-boot-parent/pom.xml`，合并时由本需求负责整合。
- 当前开发版本以根 POM `revision` 为准（`2.3.1-SNAPSHOT`）；本计划不修改 `revision`。
- 验证使用 JDK 17；本机无 JDK 25。
- `tools/` 改动须遵守[工具准入约束](../../../../tools/engineering/README.md#保留范围)：只扩展既有全局门禁与发布契约，不新增脚本。临时消费者、违规样例放在仓库外临时目录，不提交。
- 两轮文档审查已从插件 `plugin.xml` 核实主要属性名，并用最小工程验证了 Flatten 各模式的行为（见设计“背景与事实边界”）。T2 只需补核其余参数，并在真实 Parent 上复验。
- Common 与 10 个 Starter 都直接继承 Parent；`mimir-boot-starters` 只是聚合器，它的 `properties` 不会被继承。
- 引入后，执行到 `verify` 的任何构建都要从插件仓库解析 PMD 插件与规则 jar。本仓库不带 `-am` 构建单个模块前，需先 `install` 规则 jar；下游的内网镜像需同步相关坐标，或在 POM 中配置 `pluginRepositories`。
- 已自行配置 PMD 的下游受 CQ-10 影响，须写入升级说明；下游自有的 Checkstyle 不受影响。

## 关键决策

- D-102：并入 v2.4.0，新增唯一模块 `mimir-boot-code-quality`。
- D-103（经 D-109 修订）：工具为 PMD、CPD、ArchUnit；不做 doclint、`dependency:analyze`、Enforcer 模块方向规则，不引入 SpotBugs、Error Prone。
- D-104：本仓库两个 `*.failOnViolation=true`；Parent 公共默认为 `false`，各工具 `*.skip` 独立。
- D-105：Spotless、JaCoCo 的配置、开关、语义一律不动。
- D-106：规则按定位矩阵配置，不得启用与 Spotless、其他工具重叠的规则。
- D-107：规则库在 starter-test，提供通用与 COLA 预设；Javadoc 只要求公开类型与方法，不要求 `@author`。
- D-108：开关保持插件原生属性；CQ-10 的兼容影响写入升级说明，不改用 Mimir 私有开关。
- D-109：不引入 Checkstyle，注释与命名由 PMD 选定规则负责；接受不检查 Javadoc 标签与签名一致性。
- 推荐方案：
  - PMD 插件 3.28.0，使用内置 PMD 7.17.0；`CommentRequired` 以 `violationSuppressXPath` 排除构造器与非公开可见性；本仓库设 `pmd.skipPmdError=false`；
  - ArchUnit 1.5.1 核心库，以 optional 方式进入 starter-test；
  - CPD `minimumTokens=100`；
  - Parent Flatten 的 `properties` 改为 `extended_interpolate`；
  - 检查参数只写在 Mimir 自己的 execution 内，插件级只放版本与插件依赖。

## 行为契约摘要

| 场景 | 必须观察到的结果 |
|---|---|
| 下游默认模式、dev、prod | 两类新检查跳过，没有违规输出 |
| 下游 precheck、ci | 两类检查执行；违规只输出到控制台与 XML 报告，构建成功 |
| 下游单独设置 `*.skip` / `*.failOnViolation` | 只影响对应工具；命令行优先于 POM 属性 |
| 本仓库 `-Pci clean verify` | 任一违规或 PMD 处理错误都使构建失败 |
| 本仓库完整验收 | 构建模型检查拒绝任何放宽配置；两类报告与同次构建绑定 |
| Spotless、JaCoCo | 名称、默认值、失败语义不变 |
| 发布后的独立消费者 | 规则 jar 版本等于 Parent 版本；precheck 成功并生成两类报告 |
| 已自行配置同名插件的下游 | 下游 execution 的规则、范围与参数不变；开关的变化与 CQ-10 一致，按升级说明可以恢复 |
| ArchUnit 规则库 | 违规时失败并列出源类与目标类；导入为空或可识别的层少于 2 个时失败；多模块下 `test` 与 `verify` 结果一致；未声明 ArchUnit 的下游不受影响 |

## 任务依赖与文件归属

| 任务 | 依赖 | 文件归属 |
|---|---|---|
| T1 规则 jar 模块 | 无 | `mimir-boot-code-quality/**`、根 `pom.xml` 的 `<modules>` |
| T2 Parent 接入 | T1 | `mimir-boot-parent/pom.xml`、`mimir-boot-parent/README.md` |
| T3 本仓库严格模式与存量治理 | T2 | Common 与 10 个 Starter POM 的 `properties` 段、各模块 `src/main/java` |
| T4 ArchUnit 规则库 | 无（可与 T1～T3 并行） | `mimir-boot-bom/pom.xml` 中的 ArchUnit 条目、`mimir-boot-bom/README.md`、`mimir-boot-starters/mimir-boot-starter-test/**`（含其自身的依赖方向测试） |
| T5 本仓库依赖方向测试 | T3、T4 | `mimir-boot-common/pom.xml` 的 `dependencies` 段、其余 9 个 Starter POM 的 `dependencies` 段、这 10 个模块的 `src/test/java/**/architecture/` |
| T6 门禁与发布契约 | T2、T3 | `tools/engineering/src/quality/verify-build-model.mjs`、`java-build-model.mjs`、`java.mjs`、`verify-java-reports.mjs`、`runner.mjs`、`results.mjs`（按需）、`tools/engineering/src/release/consumer.mjs`、`fixture-consumer.mjs`、`public.mjs`、`tools/engineering/README.md` |
| T7 文档与升级说明 | T1～T6 | 见 T7 |

并行边界：

- T3 与 T5 都要修改 Common 与 9 个 Starter 的 POM（分别是 `properties` 段与 `dependencies` 段），为避免并行冲突，T5 在 T3 之后执行。
- T5 发现的依赖违规需要改 main 源码时，交回 T3 的负责人处理。
- starter-test 的 POM 由 T3（`properties` 段）与 T4（`dependencies` 段）分段修改；T4 与 T3 并行时，合并须注意冲突。
- starter-test 的其余改动（含依赖方向测试）都归 T4。

## 任务

### T1：规则 jar 模块

Purpose：提供 PMD 规则集文件，可发布到 Central。

Files：

- Create：`mimir-boot-code-quality/pom.xml`
  - 父为根 `mimir-boot`，`packaging=jar`，不声明依赖；
  - 显式设置 `maven.deploy.skip=false`，与 Common 一致；
  - 绑定 source 附件和 `javadoc` 分类器的说明 jar。
- Create：`mimir-boot-code-quality/src/main/resources/mimir-boot/pmd/ruleset.xml`：按设计的 PMD 规则表，含 `CommentRequired` 参数与 `violationSuppressXPath`。
- Create：`mimir-boot-code-quality/src/main/javadoc/README.md`（或同等说明文件）：`javadoc` 分类器 jar 的内容。
- Create：`mimir-boot-code-quality/README.md`，说明以下内容：
  - 资源路径；
  - 继承 Parent 的接入方式；
  - 仅导入 BOM 的完整插件配置：插件 3.28.0、带版本的规则 jar；
  - 注释检查的范围与已知缺口（不检查标签与签名一致性等）；
  - 替换规则的方法；
  - IDE 插件的引用方式；
  - 内网镜像需同步的坐标。
- Modify：根 `pom.xml`：`<modules>` 加入新模块。

Behavior and constraints：

- 不含 Java 源码，不进入 BOM。
- 规则内容只能来自设计表；任何新增规则须先对照定位矩阵。

Acceptance：

- `./mvnw -pl mimir-boot-code-quality clean install` 产出主 jar、`-sources.jar`、`-javadoc.jar`，主 jar 内资源路径正确。
- 用插件内置的 PMD 加载规则集，在一个临时样例上运行，没有配置错误（未知规则名会直接报错）。
- 用规格 CQ-01 的各场景样例验证 `CommentRequired` 与 `violationSuppressXPath`：public 类的 public 方法缺 Javadoc 报告；构造器、`@Override`、getter/setter、protected 方法、字段、package-private 类及其中的 public 方法均不报告。XPath 属性无法表达这一范围时**暂停对齐**。
- 确认 `ClassNamingConventions` 的 `utilityClassPattern` 默认值；若要求工具类后缀，按设计改为与 `classPattern` 相同。

### T2：Parent 接入

Purpose：继承 Parent 的项目获得两类检查，默认只报告，各工具独立开关。

Files：

- Modify：`mimir-boot-parent/pom.xml`
  - 新增设计中的属性；
  - PMD 插件的 `pluginManagement` 配置：插件级只放版本与插件依赖，全部检查参数写在 `pmd-check`、`cpd-check` 两个 execution 内；
  - 插件在 `build/plugins` 中公开声明；
  - 四种模式补充两个 skip；
  - Flatten 的 `properties` 由 `keep` 改为 `extended_interpolate`，并加注释说明：以后新增的引用型属性可能在生产者侧被固定；
  - 更新 `description` 中的工具列表。
- Modify：`mimir-boot-parent/README.md`
  - 属性表、模式表；
  - 只报告与严格两种模式的切换方式；
  - 仅导入 BOM 的接入入口；
  - 插件仓库要求：需配置镜像或 `pluginRepositories`；
  - “已自行配置 PMD 的升级”一节：逐条给出 CQ-10 的影响与恢复配置（包括关闭 Mimir execution）。

Behavior and constraints：

- 第一步：读取 `maven-pmd-plugin` 3.28.0 的 `META-INF/maven/plugin.xml`，补核 `excludeRoots`、`includeTests`、`minimumTokens`、`targetJdk`、`linkXRef`、`failurePriority` 存在且含义一致。

  再确认以下两点：`failOnViolation=false` 时 PMD/CPD 仍在控制台逐条输出违规；`check`、`cpd-check` 分叉出的报告目标能拿到 execution 级配置。任一项不符时**暂停并重新对齐**。
- 不改 Spotless、JaCoCo、Enforcer、Sonar 的任何配置。
- `*.failOnViolation` 不进入模式。
- 规则 jar 版本只通过 `mimir-boot-code-quality.version` 引用。

Acceptance：

- JDK 17 下，以下各种模式与组合的有效配置都与设计的模式表一致：默认、`dev`、`precheck`、`ci`、`prod`、`-Pdev -Dpmd.skip=false`、`-Pci -Dcpd.skip=true`。
- 在 reactor 中执行 `./mvnw -pl mimir-boot-starters/mimir-boot-starter-log -am clean install`，规则 jar 排在 Parent 之前构建，证明插件依赖参与 reactor 排序。否则暂停对齐。
- 用与 release workflow 相同的方式（带 `-Drevision`）部署到临时文件仓库后，检查发布的 Parent：
  - `mimir-boot-code-quality.version` 为字面版本；
  - 其余属性与改动前逐项相同，没有 `revision`，也不含生产者路径；
  - `pluginManagement` 中的 `${...}` 引用仍然保留。

  任一项不成立时暂停，并回到设计中的备选方案对齐。
- 临时消费者已有自己的 PMD execution 时，升级前后这些 execution 的有效配置除开关属性外完全相同。
- Parent 需求已有的四模式验证与发布 Parent 验证，在本改动后复跑仍然成立。

### T3：本仓库严格模式与存量治理

Purpose：本仓库在 `ci` 下零违规，此后违规即失败。

Files：

- Modify：`mimir-boot-common/pom.xml` 与 `mimir-boot-starters/` 下 10 个 Starter 的 `pom.xml`：在各自的 `properties` 中设置两个 `*.failOnViolation=true` 与 `pmd.skipPmdError=false`。不改 `mimir-boot-starters/pom.xml`，它只是聚合器。
- Modify：各模块 `src/main/java/**`：补充 Javadoc，修正命名与其他 PMD 问题，消除重复。

Behavior and constraints：

- 先运行一次 `./mvnw -Pci clean verify`，此时暂时保持只报告。按模块与规则统计存量违规数，写入会话和最终交付摘要。
- 修复不得改变公开 API（类名、包名、公开方法签名）或运行时行为。重命名公开成员属于这种情况，遇到时**暂停对齐**；可选做法是加带理由的 `@SuppressWarnings`。
- 抑制只能加在具体元素上，并写明原因。禁止文件级、模块级的整体排除，禁止修改规则 jar 来迁就个别代码。
- 某条规则在本仓库普遍误报时：复杂度阈值可以在 ±25% 内自行调整，并在交付摘要中说明；移除规则或改变类目须先对齐，涉及工具定位时同步 RFC 定位表。
- 消除重复代码只能在同一模块内抽取私有方法或包内工具类，不新增跨模块的公共 API。

Acceptance：

- `./mvnw -Pci clean verify` 通过。
- 分别在 Common 和某个 Starter 中临时加一个缺少 Javadoc 的公开方法（不提交），构建失败并指出文件与行号。
- 执行 `./mvnw clean install`（默认模式）后，各模块 `target/` 下没有两类报告文件，日志中没有两类工具的违规行。
- 各模块既有测试全部通过。

### T4：ArchUnit 规则库

Purpose：提供下游与本仓库都能复用的依赖方向规则。

Files：

- Modify：`mimir-boot-bom/pom.xml`：托管 `com.tngtech.archunit:archunit` 1.5.1，版本属性为 `archunit.version`。
- Modify：`mimir-boot-bom/README.md`：补充受管坐标。
- Modify：`mimir-boot-starters/mimir-boot-starter-test/pom.xml`：以 optional 方式依赖 `archunit`。
- Create：`mimir-boot-starters/mimir-boot-starter-test/src/main/java/com/yggdrasil/labs/test/arch/` 下的 `MimirArchClasses.java`、`MimirArchRules.java`、`LayerSpec.java`、`ColaArchRules.java`。
- Create：`mimir-boot-starters/mimir-boot-starter-test/src/test/java/com/yggdrasil/labs/test/arch/**`：规则测试与夹具包。
- Create：`mimir-boot-starters/mimir-boot-starter-test/src/test/java/com/yggdrasil/labs/test/architecture/TestStarterArchitectureTest.java`：starter-test 自身的依赖方向测试，允许关系按设计表。
- Modify：`mimir-boot-starters/mimir-boot-starter-test/README.md`，包括：
  - 接入方式：声明 ArchUnit 依赖，编写普通 JUnit 测试；
  - 包参数为完整包名；
  - COLA 预设说明、自定义分层示例；
  - 多模块工程使用 `importMain`；
  - 用 `FreezingArchRule` 冻结存量违规的示例，以及 `allowStoreCreation` 配置。

Behavior and constraints：

- 公开类型与方法签名以设计为准。新增或修改签名属于公共接口变化，须先对齐。
- 规则库不引用 `archunit-junit5`，也不自动执行。
- 校验 `archunit` 1.5.1 的传递依赖只有 `slf4j-api`，经 Boot 管理后版本兼容。

Acceptance：

- 每个公开方法至少有通过、违规、参数错误三类测试；违规测试断言失败信息中含源类与目标类的全限定名。
- COLA 预设覆盖以下情况：
  - `domain→app` 违规失败；
  - 只有三层时通过；
  - 只有一层时失败；
  - `domain` 引用 Spring 时失败。
- `importMain`、`importDirectory` 在包或目录为空时抛出 `IllegalStateException`。
- 冻结测试在临时存储目录中先记录基线，新增违规后只报告新增的那一条。
- 仓库外的临时消费者只依赖 starter-test 时，`dependency:tree` 中没有 ArchUnit，测试通过。
- 仓库外的临时四模块 COLA 工程中，`start` 模块的架构测试在 reactor `test` 与 `verify` 下报告同一处违规。
- starter-test 自身的依赖方向测试通过。

### T5：本仓库依赖方向测试

Purpose：把 ARCHITECTURE.md 中的依赖规则写成各模块的测试。

Files：

- Modify：`mimir-boot-common/pom.xml` 的 `dependencies` 段：以 test 范围依赖 `archunit`。
- Modify：其余 9 个 Starter 的 POM（log、exception、web、mybatis、mybatis-processor、nacos、rpc-core、dubbo、feign）的 `dependencies` 段：以 test 范围依赖 `archunit`。ArchUnit 在 starter-test 中是 optional，不会传递过来。
- Create：`mimir-boot-common/src/test/java/com/yggdrasil/labs/common/architecture/CommonArchitectureTest.java`：直接使用 ArchUnit。
- Create：上述 9 个 Starter 各自的 `src/test/java/<模块包路径>/architecture/<Module>ArchitectureTest.java`：使用 `MimirArchClasses.importDirectory` 与 `MimirArchRules`，允许关系按设计表。

Behavior and constraints：

- 只导入本模块的 `target/classes`，避免 `com.yggdrasil.labs.mybatis.annotation` 同名包误报。
- 允许关系以设计表为准，不得为了让测试通过而放宽。
- 发现既有违规时交给 T3 修复。修复需要移动公开类时**暂停对齐**；可选做法是在规则中逐条忽略该依赖，并登记技术债。

Acceptance：

- 加上 T4 中 starter-test 的测试，11 个模块的依赖方向测试全部通过，并计入 Java 测试结果。
- 临时在 starter-log 中引用 `com.yggdrasil.labs.web` 的类（不提交），对应测试失败并指出该引用。

### T6：门禁与发布契约

Purpose：完整验收能证明严格模式真正生效，发布契约覆盖新制品与新的 Parent 属性。

Files：

- Modify：`tools/engineering/src/quality/verify-build-model.mjs`：在隔离缓存中先安装规则 jar，再安装 Parent。
- Modify：`tools/engineering/src/quality/java-build-model.mjs`：登记 `pmd` 插件，按设计的白名单方式核对两个 execution：有效配置必须与 Parent 声明逐项相等；同时核对规则 jar 版本。
- Modify：`tools/engineering/src/quality/java.mjs`、`verify-java-reports.mjs`：
  - 两类报告纳入构建前清理、同次构建产物登记与报告核验，必须产出报告的模块按设计确定；
  - Java 子报告新增 `java-pmd`、`java-cpd` 检查项与对应规则 id；
  - `validateBuildInputs` 拒绝 `pmd.*`、`cpd.*`、`minimumTokens` 的环境注入。
- Modify：`tools/engineering/src/quality/runner.mjs`：Java 子报告的必需项与复制列表加入两项新检查。
- Modify：`tools/engineering/src/quality/results.mjs`：仅在需要时修改。不升级任何 `schemaVersion`；若实现必须改变已有字段语义，按设计的 schemaVersion 约定同步全部读取方。
- Modify：`tools/engineering/src/release/consumer.mjs`、`tools/engineering/src/release/fixture-consumer.mjs`：
  - 已发布制品、紧凑 POM、生产者选择器加入新模块；
  - 断言发布 Parent 的版本属性为字面值，没有 `revision`，也不含生产者路径；
  - 两个继承 Parent 的夹具 POM 增加同源的 `<pluginRepositories>`；
  - 新增离线运行的 `consumer-quality-precheck` 阶段和夹具 main 源文件；
  - 原有阶段的命令与预期结果不变。
- Modify：`tools/engineering/src/release/public.mjs`：Central 公开验证的抽查清单加入规则 jar 的 POM 与 jar。
- Modify：`tools/engineering/README.md`：更新用途说明。

Behavior and constraints：

- 准入说明（写入变更说明）：本任务扩展既有的全局构建模型检查、Java 报告核验与发布消费者契约，长期职责是校验 Parent 质量契约与发布形态，复用入口为 `quality --mode full`、`ci.yml`、`release.yml`；不新增脚本，不新增工具自测文件。
- 只读取当前项目配置，不固定某次需求的版本号。

Acceptance：

- JDK 17 下 `bash scripts/engineering.sh quality --mode full --source worktree` 通过，报告中两类检查为 `passed`。
- 逐一临时做以下修改（不提交），每种都使完整验收失败，并指出模块与参数：
  - `pmd.failOnViolation=false`
  - `cpd.skip=true`
  - `pmd.maxAllowedViolations=10`
  - `pmd.skipPmdError=true`
  - 自定义 `excludes`
  - CPD `ignoreLiterals=true`
  - 删除某个 Starter 的严格属性
  - 在 `MAVEN_OPTS` 中注入 `-Dpmd.skip=true`
- 临时删除某模块的 `target/pmd.xml` 后核验报告，结果失败并给出 `quality-report-missing`。
- 发布消费者契约通过：新阶段成功并生成两类报告，原有阶段的命令与预期结果不变。
- 临时去掉夹具 POM 的 `<pluginRepositories>`（不提交）时，消费者在 `verify` 阶段失败并给出规则 jar 坐标，证明新增配置是必要的。

### T7：文档与升级说明

Files：

- Modify：`docs/engineering/testing.md`
  - 加入定位矩阵（引用 RFC）；
  - 按目标选择检查的命令；
  - 在失败处理表中加入两类检查的处置方式。
- Modify：`docs/engineering/new-starter.md`：新增 Starter 必须在自身 POM 中设置严格属性，并编写依赖方向测试。
- Modify：`docs/engineering/development.md`：
  - 本地入口 `./mvnw -Pprecheck verify` 及单独运行方式；
  - 不带 `-am` 构建单个模块前需先安装规则 jar。
- Modify：`ARCHITECTURE.md`：项目结构、模块职责表、技术栈的“代码质量”行。
- Modify：`docs/design-docs/arch-module-dependencies.md`：新模块的边界。
- Modify：`AGENTS.md`：开发命令，以及决策地图中“修改质量规则 → 规则 jar”一项。
- Modify：根 `README.md`：模块列表（如有）。
- Modify：`docs/design-docs/arch-code-quality-toolchain.md`：验证完成后状态改为 `verified`，并链接验证结论。
- Modify：`docs/active/v2.4.0/release.md`：
  - 交付状态与最终验证摘要；
  - “升级注意”条目：CQ-10 的兼容影响与恢复配置；内网镜像需同步的坐标；需要配置 `pluginRepositories` 或镜像。

Acceptance：

- 文档检查 full 通过，内部链接有效。
- 文档中的属性名、路径、命令与实际 POM 一致（逐项 grep 核对）。
- 升级说明中的恢复配置，已在 CQ-10 集成验收中实际验证过。

## 集成验收

- JDK 17 下 `bash scripts/engineering.sh quality --mode full --source worktree` 全部通过。
- 仓库外的临时消费者（继承发布的 Parent，`relativePath` 为空，只经文件仓库解析）覆盖规格 CQ-01～CQ-04、CQ-06、CQ-09 的各场景，包括：
  - 只报告、单独阻断、单独关闭、命令行覆盖；
  - 替换规则、规则 jar 缺失。
- 仓库外仅导入 BOM 的临时消费者按模块 README 接入：违规结果与继承 Parent 时一致；省略规则 jar 版本时构建失败。
- 仓库外的临时消费者覆盖 CQ-10 的三个场景及 README 给出的恢复方式。
- 仓库外只配置 `<repositories>` 的临时消费者在 `verify` 阶段失败；补充 `<pluginRepositories>` 后成功。
- 在同一台机器上测量 `./mvnw -Pci clean verify` 引入前后的耗时。增幅超过 20% 时，报告各插件耗时并对齐处理方式。

## 决策边界

- 可自行决定：
  - 规则文件的组织方式与注释；
  - 测试类命名和夹具结构；
  - 存量违规的具体修复方式（不改公开 API 与行为）；
  - PMD 复杂度阈值在 ±25% 内调整，在交付摘要中说明；
  - 质量报告新规则 id 的最终命名。
- 必须暂停对齐：
  - 插件参数名与设计不符，或 `violationSuppressXPath` 无法表达 D-107 的检查范围；
  - 规则 jar 不能参与 reactor 排序；
  - execution 级配置无法传给 `check`、`cpd-check` 分叉的报告目标，或无法避免影响下游已有 execution；
  - Flatten 插值改变了已有属性或模式，或未能插值出版本；
  - 需要改公开 API 才能消除违规；
  - 需要新增或删除规则、类目；
  - 需要新增 Mimir 私有属性；
  - CQ-10 实测影响超出规格所列；
  - 耗时增幅超过 20%；
  - 需要修改 Spotless、JaCoCo、Enforcer、Sonar、Git hook 或 CI workflow。
