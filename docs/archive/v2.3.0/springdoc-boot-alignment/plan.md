# Springdoc 与 Boot 基线兼容修复计划

## 目标与依据

修复 TD-041：保持 Java 17、Spring Boot 3.3.13 和 Spring Framework 6.1.21，将 BOM 托管的 `springdoc-openapi-starter-webmvc-ui` 从 2.9.1 调整为 2.6.0，恢复消费者的 OpenAPI 文档与 Swagger UI。

本需求沿用 v2.3.0 活跃文档目录，不改变根 POM 的 `revision` 或发布承诺。官方 [v2 兼容矩阵](https://springdoc.org/v2/faq.html#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot) 将 Boot 3.3.x 对应到 Springdoc 2.6.x。本地已用 Maven Central 的 2.9.1 制品复现 `SwaggerResourceResolver` 加载时缺失 `LiteWebJarsResourceResolver`；同一 Spring MVC 6.1.21 下，2.6.0 能通过类加载对照，但尚需消费者运行验收。

## 范围与兼容边界

- 修改 `mimir-boot-bom/pom.xml`：仅调整 `springdoc.version`，保留公开属性和依赖坐标。
- 修改 `.github/dependabot.yml`：忽略 Springdoc 的 major/minor 版本更新，保留 2.6.x 的 patch；升级 Boot 基线时重新评估该限制。
- 修改 `mimir-boot-bom/README.md`：说明默认版本回退、消费者迁移影响及已验证范围。
- 修改 `ARCHITECTURE.md`、`docs/active/tech-debt-tracker.md`、`docs/active/v2.3.0/index.md`：同步当前边界、债务处置与需求导航。
- 新增本计划；专项消费者 fixture 与日志仅放在 `/tmp/`，不加入 `tools/` 或新增 Reactor 模块。
- 不调整 Boot、Spring、Parent、Java 实现、公共配置、制品布局或版本号；提交、推送或发布需另获用户授权。

依赖 Springdoc 2.9.1 特有 API 或行为的消费者需自行评估迁移；显式固定 2.9.1 的消费者不会因 BOM 默认值变化而自动修复。不得单独覆盖 Spring MVC 到 6.2 来规避缺类。

## 实施顺序

1. 准备一次性消费者验证：BOM 直接导入及与 Mimir Web Starter 联用两种模式，保留 2.9.1 的负向证据。
2. 调整 BOM 属性为 2.6.0，随后生成并安装候选发布 POM 与所需 Starter 制品到隔离 Maven 仓库。消费者必须通过候选发布 BOM 管理 Springdoc 版本，不在测试 POM 中硬编码修复版本；记录实际选中依赖和发布 BOM 的 SHA-256。
3. 运行消费者行为验证并同步文档，随后运行仓库完整验收。最后将实际命令、报告、结果和未验证范围记录在本计划中。

仓库构建串行执行；临时 fixture 编写和只读审查可独立并行，不能并行清理同一工作树产物。

## 验收

- 负向对照：2.9.1 配 Spring MVC 6.1.21 加载 Swagger 资源解析器抛出 `NoClassDefFoundError`，缺失类为 `LiteWebJarsResourceResolver`。
- Dependabot 的 Springdoc 忽略规则包含 major/minor、不包含 patch；不改变其他依赖的更新规则。
- 两种消费者模式实际选中 Springdoc 2.6.0、Boot 3.3.13、Spring MVC 6.1.21；发布 BOM 与隔离消费者缓存中的 BOM 哈希相同，避免误用既有本地制品。
- 两种模式启动真实嵌入式服务器；以下请求均返回 HTTP 200：`/v3/api-docs` 返回可解析 JSON，`openapi` 为 3.x 且 `paths` 包含样例业务接口；`/v3/api-docs/swagger-config` 的 `url` 为 `/v3/api-docs`；`/swagger-ui/index.html` 包含 `swagger-ui-bundle.js`；对应 JS 包含 `SwaggerUIBundle`，CSS 包含 `.swagger-ui`，不能以错误页或统一响应体代替静态资源。
- 与 Mimir Web Starter 联用时，验证业务接口、文档生成和静态资源可同时使用；不通过关闭 Mimir 自动配置来获得通过。
- `bash scripts/engineering.sh quality --mode full --source worktree` 与 `git diff --check` 通过，读取完整质量报告的必需阶段结果。
- 只有上述适用验收通过后才将 TD-041 移出活跃清单；本地结果不代表 CI、Maven Central 发布或发布后消费者验证。

## 决策边界

临时消费者的测试结构、端口和隔离目录可自行选择。出现其他 Springdoc 版本需求、必须修改 Starter 行为或升级框架基线时，先说明证据与新范围再决定方案，不能将这些变化静默并入本次修复。

## 本地验收记录（2026-09-30）

TD-041 本地实施及完整验收完成，尚未发布。运行依赖仅调整 BOM 的 `springdoc.version`，另补 Dependabot 防复发规则，其余仓库改动为本计划、导航、兼容说明和债务记录。没有新增或修改 `tools/`，一次性消费者源码位于 `/tmp/mimir-td041-consumer-fixture/`。

### 候选制品与专项消费者

环境：Temurin Java 17.0.19、Maven Wrapper 3.9.16。隔离 Maven 仓库为 `/tmp/mimir-td041.G6LcHi/repository`，从已有第三方缓存复制并由本次构建安装候选制品；消费者离线运行，不从既有用户仓库解析 Mimir 候选制品。

实际命令（仓库根目录，以 `mise exec --` 提供运行环境）：

```bash
./mvnw -B -o -Dmaven.repo.local=/tmp/mimir-td041.G6LcHi/repository \
  -pl mimir-boot-bom,mimir-boot-starters/mimir-boot-starter-web -am install

./mvnw -B -o -f /tmp/mimir-td041-consumer-fixture/pom.xml \
  -Dmaven.repo.local=/tmp/mimir-td041.G6LcHi/repository \
  -Dsurefire.reportNameSuffix=direct test \
  org.apache.maven.plugins:maven-dependency-plugin:3.11.0:list \
  -DoutputFile=/tmp/mimir-td041.G6LcHi/direct-dependencies.txt

./mvnw -B -o -f /tmp/mimir-td041-consumer-fixture/pom.xml \
  -Dmaven.repo.local=/tmp/mimir-td041.G6LcHi/repository \
  -Pmimir-web -Dsurefire.reportNameSuffix=mimir test \
  org.apache.maven.plugins:maven-dependency-plugin:3.11.0:list \
  -DoutputFile=/tmp/mimir-td041.G6LcHi/mimir-dependencies.txt
```

上述三条命令在沙箱外运行，退出码均为 0。首次沙箱内构建因 Mockito / Byte Buddy 不能自附加 JVM 而失败；同一实现沙箱外重跑通过，未修改测试或降低门禁。日志分别为证据目录下的 `candidate-install-unsandboxed.log`、`direct-consumer.log`、`mimir-consumer.log`。

发布 BOM 生成后与隔离仓库中的安装 POM 当场对比，SHA-256 均为 `d98793687d5ac910660db74bfe84e660e27a3fde6bc10f83a17b53c54f7b8429`。副本 `/tmp/mimir-td041.G6LcHi/candidate-bom.pom.xml` 与缓存 POM、fixture 输入的哈希保存在 `consumer-input-sha256.txt`，避免完整门禁重建产物后混用发布 POM。

| 模式 | 实际选中版本 | 行为与测试证据 |
|---|---|---|
| 独立 BOM + Spring Boot Web | Springdoc 三个 Starter 均为 2.6.0；Boot 3.3.13；Spring MVC 6.1.21 | `direct-dependencies.txt`；Surefire `ConsumerWebIntegrationTest-direct.xml`，tests=1、failures/errors/skipped=0 |
| 独立 BOM + Mimir Web Starter | 同上；Mimir Web Starter 2.2.2-SNAPSHOT | `mimir-dependencies.txt`；Surefire `ConsumerWebIntegrationTest-mimir.xml`，tests=1、failures/errors/skipped=0 |

两份完整 Surefire XML 位于 `/tmp/mimir-td041-consumer-fixture/target/surefire-reports/`，文件名前缀为 `TEST-io.github.yggdrasil.labs.fixture.`。每份测试均验证本计划列出的六项真实 HTTP 请求；联用模式额外确认 `WebAutoConfiguration` Bean 存在、Web/响应增强默认启用、业务响应含非空 `X-Trace-Id`。消费者 Springdoc 依赖未声明版本，也未关闭 Mimir 自动配置。

负向对照 `/tmp/mimir-td041.G6LcHi/springdoc-2.9.1-negative.log`：Java 17 加载 Maven Central 的 2.9.1 `SwaggerResourceResolver`，使用同一隔离仓库的 Spring MVC 6.1.21 时退出码为 1，原因是缺少 `LiteWebJarsResourceResolver` 的 `NoClassDefFoundError`。该对照只验证类加载，不冒充旧版消费者的完整启动验收。

### 完整质量验收

```bash
mise exec -- bash scripts/engineering.sh quality --mode full --source worktree \
  --report /tmp/mimir-td041.G6LcHi/quality-report.json
```

本地沙箱外执行，退出码 0，`overall=passed`，无 findings。报告 `runId=quality-1790751864385-1024759`，输入为工作区，`tree=worktree-1039d23bf662edcb5fc7ad973d280d619941db14d2982dc65418aaa75a164104`；2026-09-30 15:04:24 至 15:10:33（Asia/Shanghai）运行。

必需检查 `engineering-bootstrap`、`docs-full`、`verify-build-model`、`release-contracts`、`release-consumer`、`release-signing`、`java-quality`、`java-tests`、`java-coverage` 均 passed；`java-sonar` 为 not_applicable（`RUN_SONAR=false`）。详细产物位于 `/tmp/mimir-td041.G6LcHi/quality-artifacts.eulBzO/`。完整门禁后回写本节和债务/需求状态，并运行最终文档 full 与差异检查；运行代码及 BOM 依赖版本未再修改。后续 Dependabot 配置补丁的核验范围见下文。

专项与完整验收均为本地证据；不覆盖目标应用自定义安全、代理、分组文档、真实发布及发布后消费者。临时证据目录不会自动成为 CI artifact 或长期仓库存档。默认版本回退与显式覆盖的迁移责任已在 BOM README 中说明。

### Dependabot 防复发补丁

依用户后续授权，将 Springdoc 忽略规则从仅 major 改为 major/minor，保留 2.6.x 的 patch 更新；升级 Boot 基线时重新评估。此前完整 quality 报告不包含这项后续配置补丁；该补丁单独进行 YAML 解析、Springdoc 更新类型核对、文档 full 与差异检查，不重复运行未受影响的 Java/消费者测试。远端 Dependabot 是否按新规则生成 PR，需要配置提交到默认分支后观察，本地检查不代表远端执行验证。
