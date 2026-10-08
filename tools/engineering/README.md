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

Java 子检查通过本轮 `-Pci help:effective-pom` 建立完整 reactor 期望矩阵，记录有效测试选择器、报告路径、覆盖率范围和最低阈值，并明确纯 POM/无源码豁免。模型获取失败或检测到范围退化会返回执行错误。当前解析支持本项目的 POM/JAR 生命周期和 Ant 选择器；测试规则独立按 Surefire/Failsafe 归一化，支持裸类名、全限定类名、路径、`.java`/`.class` 及省略扩展名，只有整段 `**` 跨目录。空 includes/excludes 回退插件默认规则，显式非空 excludes 替代默认排除。测试规则中的 `./`、反斜杠、重复斜杠、目录尾斜杠及空逗号项会明确拒绝，不静默修正。正则/方法/分组过滤、不同范围的重复执行、Spotless 二次范围限制等无法可靠表示的配置也会明确拒绝。显式测试 `argLine` 只支持无引号/转义的简单参数，agent 属性引用必须是独立参数；默认未配置的 `argLine` 仍由测试插件读取 JaCoCo 属性。修改相关插件配置后，应先运行 Java 子检查，不能把模型拒绝当作检查豁免。

构建模型通过 JDK 语法树读取全部测试源码的顶层及命名成员声明，按编译后的二进制类名应用有效选择器，源码文件名与类名不同时也纳入清单。默认 `$` 排除仅在插件未配置非空 excludes 时适用；显式选中的静态成员类按 `Outer$InnerTest` 建模，局部及匿名类不纳入源码清单。报告核验再用本次 XML 的实际测试类路径执行 JUnit Jupiter 发现，逐个核对可运行测试类的 `testcase@classname`；辅助类和抽象类不按源码文件数计算，JUnit `@Nested` 按所属外层测试类聚合，独立选中的静态成员测试类保留自身二进制名称。该清单不证明类内所有方法或动态实例均执行，零用例报告仍不通过。缺失编译类、发现失败、额外引擎发现选中测试，以及改变发现语义的插件参数均明确拒绝。`JavaSourceInventory.java`、`java-source-inventory.mjs` 和 `TestInventory.java` 是长期构建模型与报告门禁的支持文件，Java 子检查与 full 共用，不是专项验收脚本。

维护 Java 门禁时，可用 `bash scripts/lib/engineering-tool.sh src/quality/java-build-model.test.mjs` 运行模型与报告契约回归，覆盖选择器、成员类型、skip 拒绝及缺报告失败；该通用测试夹具长期维护，临时项目和报告在运行后清理。它不替代 Java 子检查的真实 Maven 构建。

模型建立的是保守的扫描候选，不承诺所有候选都会执行。当前 Parent 对应的 Surefire/Failsafe Jupiter provider 对 `.class` includes 另有二次类名过滤，本地验证会得到零用例；静态成员类验收使用 `.java` 或省略扩展名。原始路径及带空白的逗号项也可能受二次过滤影响；若实际执行范围小于候选范围，报告完整性检查会保守拒绝，需要收紧选择器。扫描层匹配不能作为执行证据，这类零用例或缺报告构建不会转为无测试豁免。

扫描发生在构建前，当前模型不支持动态追加源码根；有效 build-helper `add-source`/`add-test-source`、已知源码生成插件及已知源码生成注解处理器会明确拒绝。未知插件扩展不能据此获得完整性保证，接入前需补充模型支持。JaCoCo 比率最低值支持小数或百分比（如 `0.60` 与 `60%`），越界、降低基线或无法可靠表示的阈值拒绝。

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
