# 工程工具

本地与 CI 共用的检查实现，使用 Maven 管理的固定 Node/npm 版本和唯一依赖锁文件。需要 Java 17、Bash、Git、curl；完整发布验收还需要 JDK 的 jar 命令和 GPG。依赖准备和隔离消费者预热需要网络，制品发布仅指向临时 file 仓库。

## 保留范围

`tools/` 只允许存放服务于项目全局、跨需求和跨版本长期重复使用的工程工具及其必要支持文件。此约束覆盖新增文件、既有脚本内部新增逻辑，以及嵌入字符串或运行时生成的代码；不能只按文件名判断是否通用。

写入前必须在当次变更说明中明确长期职责、实际复用入口，以及当前需求结束后仍需持续维护和运行的理由。无法说明则不得写入 `tools/`；无需另建准入报告或过程文件。接入 CI 或完整门禁、增加调用次数、改名、参数化或包装成通用函数，均不能将专项内容变成全局工具。

- 全局文档检查、构建与测试调度、通用发布及消费者契约属于长期工具；其内部 fixture 只验证这些通用契约。
- 具体功能的回归测试放在所属模块测试目录；单次需求验收、专项功能验证、迁移、排障和实施过程脚本不得放入 `tools/`，也不得将其逻辑嵌入已有全局工具。一次性脚本使用临时目录。
- 临时 fixture 和报告写入运行时临时目录或 CI artifact。工具对发布版本、依赖版本的检查读取当前项目配置或显式输入，不额外固定某次需求的版本。

交付前须检查本次 `tools/` 差异及其调用入口：符合条件的保留，不符合条件的删除或归入所属模块，并同步清理导入、命令接线和文档引用。禁止通过增加新的全局调用来为专项逻辑补造保留理由。

## 日常入口

```bash
# 初始化工具并安装仓库 Git hooks
bash scripts/setup-dev.sh

# 修改工具清单或锁文件后，刷新目标树所需的工具缓存
bash scripts/engineering.sh prepare

# 统一完整验收（本地与 CI 基础检查共用；默认不运行 Sonar）
bash scripts/engineering.sh quality --mode full --source worktree

# 检查暂存区；提交 hook 自动调用
bash scripts/engineering.sh quality --mode quick --source index
```

pre-push 对待推送的提交快照执行离线 quick，检查该提交中的适用格式与工具缓存，不借用未提交修改；完整验收由 CI、发布工作流或显式本地 full 执行。工作区 full 包括未提交文件，不能替代不同输入的 CI 结果。任务选择与触发范围见[测试与质量指南](../../docs/engineering/testing.md)。

## 检查清单

| 模式 | 内容 |
| --- | --- |
| quick | 按变更检查文档与 Java 格式 |
| full | 文档格式、内部链接、导航；构建签名模型；发布工具契约；隔离 Parent/BOM/Starter 消费者；临时 GPG 签名与失败场景；Maven 单元/集成测试、覆盖率和报告核验 |

full 不根据 CI 环境或 Git 变更范围删减基础检查。发布 fixture 会清理构建产物，因此 Maven 质量构建在最后执行，保留最终测试和 JaCoCo 报告。Java 子检查可单独运行 `bash scripts/engineering.sh java`；其 Maven 基础步骤是 `./mvnw -B -Pci clean verify`，完整入口会继续核验报告。

Java 子检查通过本轮 `-Pci help:effective-pom` 获取完整 reactor，检查适用插件的执行阶段、扫描范围、报告路径和最低阈值，并明确纯 POM/无源码豁免。它只支持本仓库的 POM/JAR 模块、标准 `src/main/java`/`src/test/java` 和 `target` 输出目录，以及 Parent 的 `*Test`/`*Tests`、`*IT`/`*IntegrationTest` 规则；不模拟 Maven 通用选择器或额外执行 JUnit 发现。配置不在支持范围时明确失败，不能转成无测试豁免。

源码 reactor POM（含纯 POM 与 Parent）中声明 profile activation/file 一律拒绝，不论当前是否激活；不解析 Maven 通用 profile。

测试源码的包路径、顶层类名和文件名必须一致，测试命名后缀保留给实际运行的测试类，辅助类或抽象基类不使用这些后缀。构建前按源码路径建立期望清单；成功 `clean verify` 后，所选编译测试类必须与源码清单一致，每个期望类都要有 XML `testcase@classname` 执行证据。异名或独立成员测试类无法对应源码时明确失败；普通嵌套报告按外层类聚合，嵌套类不得另行匹配本阶段包括/排除规则成为独立编译候选。单元测试中的嵌套类可用 `Scenario`，避免 `ScenarioTest`；若进入独立编译候选，则因缺少同名源码而拒绝。参数化执行由 Maven/JUnit 负责。该检查不证明类内所有方法或动态实例都执行，零用例、失败、错误或跳过报告均不通过。清单不从 XML 反推，报告数量不能替代逐类核验。

Surefire 只允许 Parent 的标准包括/排除规则；Failsafe 默认排除成员类。仅当存在具体 IT 类文件排除时，才允许由模块最小 profile 清空插件级 excludes；完整 Parent 链每层的原始 `relativePath`（缺省 `../pom.xml`）解析后，其 realpath 必须与对应 reactor Parent 源码 POM 一致。空 `relativePath`、无法定位或同坐标异源均明确失败且不豁免；不额外读取 reactor 外的 Parent XML 或缓存。此条件不表示非 reactor Parent 一概禁用。执行级显式 IT 排除仍拒绝。该 profile 仅允许一个 Failsafe 插件，其配置只清空插件级 excludes，不含自动激活、属性覆盖、skip、过滤或额外执行。Surefire/Failsafe provider 的 `properties` 仅允许无子元素且文本为空的配置；包含子元素或非空文本时拒绝，不维护参数知识库。外部服务例外由模块 POM 管理，工具不包含功能专用分支。方法、标签、引擎过滤、关闭 XML、skip 和不同配置的重复执行均拒绝，包括经 POM 属性注入的参数。

compiler 源码过滤、自定义源码根、跳过主编译及 `proc:only` 会明确拒绝；自定义 compiler 参数不解释，`compilerArgs` 只保留现有 `-parameters`。不维护源码生成插件或注解处理器知识库；动态追加源码根不属于支持契约，未知生成插件接入前需另行评审，不能据此工具获得生成源码完整性保证。JaCoCo 继续要求默认 agent 属性（不支持自定义 argLine）、最终报告、check、相同 exec、数据追加及不降低 Parent 阈值，比例只支持 0 到 1、至多六位小数；排除仅限既有 entity/dto/vo/Application 范围。Spotless 覆盖标准主源码与测试源码，仅允许既有生成目录排除；插件级和执行级 `baseDir` 的实际路径必须为模块目录。

统一入口固定 Maven 命令，不接受 `MAVEN_ARGS` 或 `.mvn/maven.config` 注入额外参数；`MAVEN_BASEDIR`/`MAVEN_PROJECTBASEDIR` 不能切换配置目录。JVM 输入通道为 `.mvn/jvm.config` 及 `MAVEN_OPTS`、`MAVEN_DEBUG_OPTS`、`JAVA_TOOL_OPTIONS`、`JDK_JAVA_OPTIONS`、`_JAVA_OPTIONS`；门禁属性覆盖、`@` 参数文件和 `-XX:VMOptionsFile=` 入口均拒绝，不模拟通用 JVM/shell 参数解析，也不读取这些参数指向的文件。属性键包含引号的写法保守拒绝；属性值中的引号本身允许，但值中若出现空白分隔的门禁覆盖或参数文件文本，即使位于引号内也会被保守拒绝，不应视为支持输入。例如，`JAVA_TOOL_OPTIONS='-Dhttp.proxyPassword="ordinary -Dtest=sample"'` 会被门禁拒绝，尽管原生 JVM 会将该文本作为密码值。模型、清理、构建中断或核验失败均阻断；成功产物与失败诊断分开记录，清理前先使旧成功记录失效，XML/exec 的 SHA-256 绑定本轮构建。

Java 门禁的所有 Maven 子进程（版本、effective model、构建、Sonar 及独立 `generateExpected`）固定设置 `MAVEN_SKIP_RC=1`，禁用系统和用户 mavenrc 加载；父环境的其他输入仍按上述规则验证。该设置仅作用于门禁子进程，不改变用户手动运行 `mvnw` 的行为。

`java-build-model.mjs` 与报告核验器承担跨模块、跨版本的构建范围和报告完整性检查，长期由 Java 子检查与 full 复用。维护时运行 `bash scripts/lib/engineering-tool.sh src/quality/java-build-model.test.mjs` 验证标准编译类、缺单类报告、范围缩小、输入覆盖和旧产物等契约；临时项目与报告运行后清理。这些回归不替代真实 Maven 构建，不承载具体功能的专项验收。

本地 full 默认 `RUN_SONAR=false`，只等价 CI 基础检查，Sonar 在报告中标记为 `not_applicable`。只有已确认目标 Sonar 项目、分支和上传授权，并由受控环境提供 `RUN_SONAR=true`、`SONAR_TOKEN`、`SONAR_ORGANIZATION`、`SONAR_PROJECT_KEY` 时，Java 子检查才会在 `clean verify` 通过且报告核验通过后，独立运行 `./mvnw -B -Pci sonar:sonar` 并等待 Quality Gate；不在文档或日志中写入凭据。CI 的凭据与 push 事件策略由工作流传入。公网制品验证只能在发布后进行，本地 fixture 验证检查器的成功、损坏制品、404 与重试行为。

手动补偿迁移前的旧 tag 时，仅执行该 tag 自带的旧版 CI、消费者及已有签名契约；缺少必需旧入口则失败，不借用新分支代码。旧 tag 不具备新 Portal 解析器时跳过这项辅助观察，发布后的公开制品门禁仍必须通过；当前版本及自动发布不允许降级。

## 独立诊断命令

| 命令（均以 `bash scripts/engineering.sh` 开头） | 用途 |
| --- | --- |
| `docs --root <绝对仓库路径> --mode full --self-test` | 文档检查与工具自检 |
| `java` | Maven 构建及测试、覆盖率报告核验 |
| `build-model` | 所有 Reactor 模块 default/maven-central 签名开关 |
| `contracts` | 发布工作流、制品布局和本地 HTTP 故障场景 |
| `consumer` | 隔离发布及下游 Parent/BOM 消费者验收 |
| `signing --preheat` | 隔离缓存、一次性 GPG 密钥的签名验收 |
| `public <x.y.z>` | 验证 Maven Central 公开 POM 坐标和 JAR |
| `portal-state <JSON 文件>` | 解析 Portal 观察响应 |

## 实现与产物

- `src/docs/`：文档规则；`config/policy.json` 是 quick/full 共用的文档策略。
- `src/quality/`：统一调度、Java 有效构建模型与报告核验、结构化结果。构建清单区分成功产物与失败诊断；清理失败或构建中断不能复用旧成功记录。
- `src/release/`：发布消费者、签名、公开制品与验收 fixture；fixture 运行时写入唯一临时目录。
- `scripts/lib/`：受管运行时准备、缓存锁、Git 快照及启动失败处理。

报告记录检查状态、命令、日志位置、源 tree/commit 和配置摘要。退出码 0 表示通过，1 表示验收失败，2 表示参数或工具错误；工具准备失败不会放行。使用 `--report <绝对 JSON 路径>` 指定完整门禁的报告位置。

隔离发布仅保留本轮预热得到的 Maven 版本元数据，供 GPG 插件等版本范围依赖解析；不会将第三方制品加入隔离镜像，也不会开放外网。该处理遵循 [Maven 元数据规则](https://maven.apache.org/repositories/metadata.html)，本地镜像仍为临时 `file://` 仓库。
