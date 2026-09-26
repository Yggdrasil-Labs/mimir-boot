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
- `src/quality/`：统一调度、Java 核验、结构化结果。
- `src/release/`：发布消费者、签名、公开制品与验收 fixture；fixture 运行时写入唯一临时目录。
- `scripts/lib/`：受管运行时准备、缓存锁、Git 快照及启动失败处理。

报告记录检查状态、命令、日志位置、源 tree/commit 和配置摘要。退出码 0 表示通过，1 表示验收失败，2 表示参数或工具错误；工具准备失败不会放行。使用 `--report <绝对 JSON 路径>` 指定完整门禁的报告位置。

隔离发布仅保留本轮预热得到的 Maven 版本元数据，供 GPG 插件等版本范围依赖解析；不会将第三方制品加入隔离镜像，也不会开放外网。该处理遵循 [Maven 元数据规则](https://maven.apache.org/repositories/metadata.html)，本地镜像仍为临时 `file://` 仓库。
