---
id: mongodb-driver-alignment-plan
version: v2.3.0
status: draft
owner: YoungerYang-Y
created: 2026-09-22
updated: 2026-09-22
---

# MongoDB 驱动族兼容 — 实施计划

**Branch:** [待填充]
**Baseline SHA:** [待填充]
**Plan Schema Version:** 2
**Worktree Path:** [待填充]
**Started At:** [待填充]
**Updated At:** [待填充]
**Effective Execution Mode:** [待填充]
**Resolved Path:** docs/active/v2.3.0/mongodb-driver-alignment/
**Goal:** 消除默认发布消费者的 MongoDB 驱动混版，建立版本解析与同步/Spring Data 离线消费证据。
**Architecture:** 删除 sync 单项覆盖，委托 Boot 基线；3 类消费者进入现有发布隔离和完整质量链。
**Tech Stack:** Java 17、Boot 3.3.13、Spring Data MongoDB 4.3.13、MongoDB 5.0.1、受管 Node 22.22.3+、Maven Wrapper。

**Plan Verdict:**

- **Status:** pending
- **Verified At:** null
- **Evidence:** null
- **Blocked Tasks:** none
- **Concerns:** none

**Accepted Risks:**

| Risk ID | Risk | Accepted By | Accepted At | Source |
|---|---|---|---|---|
| none | none | none | none | none |

## Dispatch Ledger

**Budget:** uninitialized; owner=start-execution; elapsed_budget=unknown; token_budget=unknown; budget_revision=initial

| objective | scope | actor_kind | role | model | started_at | finished_at | status | attempt | attempt_limit | elapsed | stop_reason | strategy_change | evidence | budget_revision |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Global Constraints

- 本文仅规划，不能把 SDD 文档检查作为实施验收。用户确认执行后使用隔离 worktree；controller 填充元信息、建立 baseline manifest，保留已有日志脱敏 SDD 与其他用户修改。
- Java 17、Boot 3.3.13、Spring Data MongoDB 4.3.13 不升级；当前驱动族契约固定 5.0.1，不从待测输出推导期望。
- 新增生产模块/API/业务直接依赖均为 0；不改根 revision、发布坐标、profile、BOM 导入顺序、全局 Enforcer 或支持等级定义。
- 删除 Mimir mongodb.version 与 sync 显式管理项，不新增 MongoDB BOM；公开披露 4.11.5→5.0.1 迁移和旧 API/ABI 风险。
- 8 项 org.mongodb 坐标：bson、bson-kotlin、bson-record-codec、mongodb-driver-core、mongodb-driver-kotlin-coroutine、mongodb-driver-legacy、mongodb-driver-reactivestreams、mongodb-driver-sync。
- 3 类独立消费者：bom、parent、spring-data；仅 from candidate repository，无相对父 POM 或预先缓存 Mimir 制品。
- 离线表示不需要 MongoDB 服务端；Maven 预热可联网，最终验证禁外部仓库。测试不执行数据库操作、不连接业务库、不添加 Docker 前置。
- 每个测试用独占 127.0.0.1 临时 ServerSocket 占住端口，不运行 Mongo 协议；URI 数据库 td040，连接/套接字/选择超时各 200ms。关闭所有客户端、上下文、端口，不把后台连接告警误判为失败。
- 变更权限按任务 Files 限定；执行者不是独占代码库，不得回退他人改动或再次委派。默认有界工作交 luna-worker，任务串行，避免共享契约/consumer 文件冲突。
- controller 独占本文执行记录和 `docs/active/v2.3.0/mongodb-driver-alignment/plan.md.snapshots.json`；每项执行前后记录 task input/output 快照，不在 SDD 阶段创建执行快照。
- 不自动提交、推送、合并或发布。遇到无关失败记录并停在准确状态，不扩大到其他技术债修复。

## Dependency Graph

| Task | Depends on | 可并行组 |
|---|---|---|
| T1 消费者 fixture 与检查器 | 无 | A（单任务） |
| T2 驱动对齐与门禁接入 | T1 | B（单任务） |
| T3 接入说明和兼容边界同步 | T2 | C（单任务） |
| T4 完整验收与技术债收口 | T3 | D（单任务） |

无并行实施组；独立只读审查可与作者自检并行。

```mermaid
flowchart LR
    T1 --> T2 --> T3 --> T4
```

### T1: 消费者 fixture 与检查器

**Depends on:** 无

**Files:**

- Create: `tools/engineering/src/release/fixture-mongodb.mjs`
- Modify/Test: `tools/engineering/src/release/verify-contracts.mjs`

**Interfaces:**

- Consumes: none
- Produces: `writeMongoConsumerFixture(directory: string, revision: string, repositoryDir: string, mode: 'bom' | 'parent' | 'spring-data'): Promise<void>`
- Produces: `assertMongoDependencyList(source: string, expectedArtifacts: readonly string[], expectedVersion: string): void`

**Behavior:**

生成 3 种独立消费者的 POM/Java 测试源，并提供严格的选中依赖列表校验。正常清单接受，失配、缺项、重复、空输入必须拒绝；测试产物只在唯一临时目录，不成为生产模块。

expectedArtifacts 元素仅是 artifactId，groupId 固定 org.mongodb；同步模式固定调用 `assertMongoDependencyList(source, ['mongodb-driver-sync', 'mongodb-driver-core', 'bson'], '5.0.1')`，family 传 Global Constraints 的 8 个 artifactId。

**Acceptance Criteria:**

- [ ] AC1: 清单正负例均有断言，sync=4.11.5/core=bson=5.0.1 抛错且包含失配坐标与两版本。
- [ ] AC2: 3 种生成 POM 的接入方式、候选 revision/仓库、无 Mongo 显式版本、固定源路径/插件配置经结构断言成立；非法 mode/空参数拒绝；同目录同参数重复生成的路径集合与文件字节完全相同。
- [ ] AC3: contracts 命令通过，新 fixture 在临时目录生成/清理，无源工作区输出；保留现有契约测试。

**Execution:**

- **Status:** pending
- **Attempts:** 0
- **Blocked Reason:** null
- **Red Result:** null
- **Verify Result:** null
- **AC Result:** null
- **Changed Files:** []
- **Concerns:** none

**Task Completion Gate:**

- [ ] Red Result 存在且证明预期失败或前置状态。
- [ ] Verify Result 存在且通过。
- [ ] AC Result 中所有未延期 AC 均有通过证据，延期必须有用户风险接受记录。
- [ ] Changed Files 与 task input/output 快照差集相等且全部在声明范围。
- [ ] Per-task AC checkbox synced。

**Step 1: Red**

在 verify-contracts 增加清单解析和 fixture 内容断言，运行 `bash scripts/engineering.sh contracts`。先确认新增行为失败；若首先是模块不存在，建立仅满足导出签名的最小骨架后重跑，Red 证据必须是目标断言失败而非 import/语法错误。

**Step 2: Green**

新建 JS ESM 模块，使用 JSDoc 表达签名，不新增 npm 依赖：

1. 解析 dependency:list：剥离行首空白/可选 [INFO] 前缀和尾部 空白加 `-- module ...`，接受 `org.mongodb:artifactId:jar:version:scope` 或 `org.mongodb:artifactId:jar:classifier:version:scope`；冒号字段非空，scope 为 compile/runtime/test/provided/system。忽略空行、标准标题和其他非 Mongo 日志，含 org.mongodb 的格式错误行拒绝；零记录拒绝，expectedArtifacts 非空且无重复。按 groupId:artifactId 判重复（不同 classifier/scope 也拒绝）；每个期望项恰好 1 条，所有 Mongo 选中版本为 5.0.1。不消费 dependency:tree omitted 文本。Error.message 为 `MONGO_DEPENDENCY_CONTRACT coordinate=<GA或input> actual=<实际版本或missing/duplicate/malformed> expected=<目标版本或格式/唯一性>`。
2. 生成 bom（只 import Mimir BOM）、parent（只继承候选 Parent，空 relativePath）、spring-data（只 import BOM + data-mongodb/test starter）三个 POM。测试依赖无版本。BOM-only 固定 compiler=3.16.0（release=17、parameters=true）、Surefire=3.6.0（failIfNoTests=true）、dependency=3.11.0，parent 继承当前版本。输出固定为 directory/pom.xml 与 directory/src/test/java/io/github/yggdrasil/labs/fixture/MongoClientCompatibilityTest.java（bom/parent）或 MongoSpringDataCompatibilityTest.java（spring-data）；Sample 为后者的静态内部类。revision/repositoryDir 参数写 POM，repository id=fixture，源文件不注入 revision；Spring runner 用 withPropertyValues 注入动态 spring.data.mongodb.uri 和 spring.data.mongodb.auto-index-creation=false。
3. bom 的 mongo-family profile 额外声明其余 7 项族坐标，正常模式只引入 sync。POM 中仓库与 revision 正确 XML 转义。
4. 两种同步模式生成 MongoClientCompatibilityTest：初始化/数据库名 td040、无 Mongo 服务端仍可创建关闭、非法 URI 参数异常，至少 3 个测试。同步 getDatabase 不发数据库请求。
5. Spring 模式生成 MongoSpringDataCompatibilityTest：ApplicationContextRunner 加载 MongoAutoConfiguration/MongoDataAutoConfiguration，禁自动索引，MongoClient/MongoDatabaseFactory/MongoTemplate 各 1 个；converter 对字符串 id=sample-1/name=alice 写入 Document 再读回，检查 _id/name；非法 URI 上下文失败且原因链含 IllegalArgumentException、无 LinkageError。至少 3 个测试。
6. 合法 URI 使用独占 loopback 端口，不提供 Mongo 协议，三个连接相关超时各 200ms；try/finally 关闭上下文/客户端/ServerSocket。不执行 ping、索引、repository 或健康检查。
7. 在现有 verifyContracts 中运行检查器/生成器断言，使用现有 runtime 临时目录/清理与断言惯例；不得为测试放宽生产发布隔离。串行重复生成相同 fixture 两次，断言相对路径集合及每个文件字节相等；并发同目录由调用方禁止，不新增锁或承诺并发拒绝。
8. verifyContracts 用 process.execPath 启动临时 Node ESM 子进程，导入同一 assertMongoDependencyList，经现有 finishMain 执行；正常清单 exit=0，混版清单 exit=1，stderr 必须包含 MONGO_DEPENDENCY_CONTRACT、sync 坐标、4.11.5 和 5.0.1。不新增 CLI/生产脚本或下载错误制品。

**Step 3: Verify**

运行 `bash scripts/engineering.sh contracts`，期望 PASS。本任务仅证明检查器和生成内容，Java 编译/运行必须在 T2 实际消费候选制品后证明。

**AC Verification:**

- AC1: 清单单元样本覆盖 3 项/8 项成功、4.11.5 混版、遗漏 bson、重复 sync、空输入、多余同族异版、可选 classifier/module 附注；子进程正常 exit=0、混版 exit=1 且 stderr 含坐标/两版本。
- AC2: XML 结构断言各 mode 的 parent/import/dependencies/profile/plugins 及精确版本；固定路径/目标 suite、未知 mode/空参数错误均有断言；重复写入比较路径集合与逐文件字节相等。
- AC3: 命令退出 0、现有契约测试未删除，临时目录处理及真实 Changed Files 符合声明。

### T2: 驱动对齐与门禁接入

**Depends on:** T1

**Files:**

- Modify: `mimir-boot-bom/pom.xml`
- Modify: `tools/engineering/src/release/consumer.mjs`
- Modify/Test: `tools/engineering/src/release/verify-contracts.mjs`
- Modify（编译纠正限原契约）: `tools/engineering/src/release/fixture-mongodb.mjs`

**Interfaces:**

- Consumes: `writeMongoConsumerFixture(directory: string, revision: string, repositoryDir: string, mode: 'bom' | 'parent' | 'spring-data'): Promise<void>` from T1
- Consumes: `assertMongoDependencyList(source: string, expectedArtifacts: readonly string[], expectedVersion: string): void` from T1
- Produces: `mimir-boot-bom/pom.xml` § dependencyManagement 的 Boot 委托管理
- Produces: `bash scripts/engineering.sh consumer`（现有入口，新增 Mongo 必需阶段，0=通过、非零=失败）

**Behavior:**

先接入新消费者证明旧 sync 覆盖引发失配/初始化失败，再删除单项版本来源使整个族回归 Boot 5.0.1。3 种消费路径都必须经过真实临时发布、online 预热、isolated 复跑、版本和新鲜 Surefire 报告校验，不能仅验证源码 POM。

**Acceptance Criteria:**

- [ ] AC1: S01–S04 通过；bom/parent 三项均 5.0.1，bom profile 八项同版，负例仍拒绝；Spring Data 路径 core/bson/sync 同版。
- [ ] AC2: S05–S10 在真实依赖上通过，两个同步 suite 和一个 Spring Data suite 各测试数至少 3、失败/错误/跳过均 0；无数据库操作。
- [ ] AC3: 新目录/cache 在预热、隔离、来源标记、成功/失败缓存回填、finally 清理、证据复制链全部覆盖；原有 consumers 和故意失败 Failsafe 检查仍通过。

**Execution:**

- **Status:** pending
- **Attempts:** 0
- **Blocked Reason:** null
- **Red Result:** null
- **Verify Result:** null
- **AC Result:** null
- **Changed Files:** []
- **Concerns:** none

**Task Completion Gate:**

- [ ] Red Result 存在且证明预期失败或前置状态。
- [ ] Verify Result 存在且通过。
- [ ] AC Result 中所有未延期 AC 均有通过证据，延期必须有用户风险接受记录。
- [ ] Changed Files 与 task input/output 快照差集相等且全部在声明范围。
- [ ] Per-task AC checkbox synced。

**Step 1: Red**

先修改 consumer/契约接入 Mongo 阶段，暂不删除 BOM 覆盖；运行 `bash scripts/engineering.sh consumer`，记录实际失配 sync=4.11.5 与 core/bson=5.0.1 或 StreamFactory 链接错误。缓存/网络/插件下载失败不能当 Red，须先恢复执行条件。另外为新增来源/报告门禁补工具负例：错误来源、缺失 XML、tests=0 或 skipped>0 都必须拒绝。

**Step 2: Green**

1. 删除 BOM `<mongodb.version>4.11.5</mongodb.version>` 以及 org.mongodb:mongodb-driver-sync 的完整显式管理条目，其他项保持不变；不替换成新的重复版本属性。
2. 在 consumer 为 bom/parent/spring-data 分配各自唯一目录/cache；首次解析前缓存不得含 Mimir 制品，只从本次候选 file 仓库取得它们。
3. 各模式 online 执行 dependency:list 和 clean verify，预热新插件/测试依赖；bom family profile online 单独解析。检查候选仓库来源标记后，以 blocked settings + offline 独立缓存再次 list/clean verify，family profile 再 isolated list。
4. 正常 list 输出精确写新文件，includeGroupIds=org.mongodb、appendOutput=false；期望 3 项，profile 期望 8 项。所有已选 Mongo 项版本也必须统一，不放过传递的其他异版模块。
5. 每个 isolated suite 检查精确类名、tests≥3、failures=errors=skipped=0；非法 URI 是成功执行的负例，不应让 suite 本身失败。缺报告/零测试不算成功。
6. 增加适用 online 下载阶段到既有有限重试名单，但测试/版本断言失败不得重试。新增 cache 同时进入 shared/seed backfill 与 finalizeConsumer；同步 verifyConsumerCacheFlow 的覆盖。
7. 将每个模式的清单、Surefire XML 及阶段日志复制到 logsDirectory/mongo-模式名 下，记录 revision/source 状态指纹、发布 BOM 与该模式缓存 BOM 的原始路径/SHA-256，供最终同轮溯源；遵循 MIMIR_KEEP_WORKDIR、MIMIR_RELEASE_LOG_DIRECTORY。异常时不丢原始诊断。
8. 保持原有 RocketMQ/Elasticsearch、Parent 生命周期与 AlwaysFailIT 检查，不改 FULL_PLAN 或 CLI。

**Step 3: Verify**

先运行 `bash scripts/engineering.sh contracts`，再用本次独立证据目录运行：

```bash
mongo_evidence_dir=$(mktemp -d /tmp/mimir-mongo-evidence.XXXXXX)
MIMIR_RELEASE_LOG_DIRECTORY="$mongo_evidence_dir" bash scripts/engineering.sh consumer
```

两者均期望退出 0；将实际目录写入 Execution，不保留未展开变量作为证据。不需运行仅验证 pom packaging 的 Maven test 来替代此流程。

**AC Verification:**

- AC1: 保存 bom/parent/spring-data 选中清单与 profile 清单，逐项比对固定 5.0.1；contracts 的失配负例继续拒绝。
- AC2: 检查 MongoClientCompatibilityTest（两份）与 MongoSpringDataCompatibilityTest 的新鲜报告、测试名和计数；映射 _id/name、非法配置原因链均有断言。
- AC3: 来源标记与隔离日志成立；新 cache 回填/清理的工具正负例通过，旧消费者报告仍可追踪。

### T3: 接入说明和兼容边界同步

**Depends on:** T2

**Files:**

- Modify: `mimir-boot-bom/README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `tools/engineering/README.md`
- Modify: `docs/active/v2.3.0/release.md`

**Interfaces:**

- Consumes: T2 的 `mimir-boot-bom/pom.xml` § dependencyManagement 的 Boot 委托管理及 consumer 证据
- Produces: `mimir-boot-bom/README.md` § 支持等级、当前兼容性边界
- Produces: `ARCHITECTURE.md` § 当前能力边界
- Produces: `tools/engineering/README.md` § 独立诊断命令
- Produces: `docs/active/v2.3.0/release.md` § TD-040 变更说明

**Behavior:**

仅在 T2 通过后同步真实能力：由上游 BOM 管理 MongoDB，已有同步初始化和 Spring Data 离线 smoke，不承诺 CRUD 或旧 4.x ABI。披露单项属性移除、升级影响及显式覆盖需整族验证，不提升 Reactor“已验证”等级。

**Acceptance Criteria:**

- [ ] AC1: BOM 直接“仅管理”表删除 sync 行，40→39，“已验证”15 项不变；用 XML 读取 BOM 直接 dependencyManagement 的 GA 集合，与两表合并的 GA 集合做相等比较，差集为空。
- [ ] AC2: README/发布说明明确 4.11.5→5.0.1、旧属性/旧 API 迁移、验证边界；文档 full 与示例核对通过。

**Execution:**

- **Status:** pending
- **Attempts:** 0
- **Blocked Reason:** null
- **Red Result:** null
- **Verify Result:** null
- **AC Result:** null
- **Changed Files:** []
- **Concerns:** none

**Task Completion Gate:**

- [ ] Red Result 存在且证明预期失败或前置状态。
- [ ] Verify Result 存在且通过。
- [ ] AC Result 中所有未延期 AC 均有通过证据，延期必须有用户风险接受记录。
- [ ] Changed Files 与 task input/output 快照差集相等且全部在声明范围。
- [ ] Per-task AC checkbox synced。

**Step 1: Red**

运行 `rg -n 'mongodb|MongoDB|TD-040|仅管理（40' mimir-boot-bom/README.md ARCHITECTURE.md tools/engineering/README.md docs/active/v2.3.0/release.md`，保存旧风险和直接表条目。现状若因他人编辑不同，先核对，不覆盖无关债务内容。

**Step 2: Green**

删除仅管理表的 Mongo sync 直接项、修正计数；增加“由上游管理，外部消费者仅验证同步初始化与 Spring Data 离线映射”的说明。替换“存在版本不一致风险”的当前断言，保留历史迁移版本数字；列出重新编译、检查上游破坏性变更、不要只覆盖 sync、无真实数据库验证。架构只移除 TD-040 当前缺口，保留其他编号；工具说明写 consumer 新覆盖，release 不宣称已发布。

**Step 3: Verify**

运行 `bash scripts/engineering.sh docs --root "$PWD" --mode full --self-test`，期望退出 0；人工核对 README 支持集合与 POM，确认不误升等级。

**AC Verification:**

- AC1: 提取 README 两表 GA 与 POM 直接管理 GA（含导入 BOM 本身，不展开传递项）做集合比较，差集为空，基数 15+39=54；记录比较结果，原 15 项集合不变。
- AC2: 逐项比对迁移/范围声明与 T2 证据，保存文档检查汇总和退出码。

### T4: 完整验收与技术债收口

**Depends on:** T3

**Files:**

- Modify: `docs/active/tech-debt-tracker.md`
- Modify: `docs/active/v2.3.0/index.md`
- Modify（controller）: `docs/active/v2.3.0/mongodb-driver-alignment/plan.md`
- Create/Update（controller）: `docs/active/v2.3.0/mongodb-driver-alignment/plan.md.snapshots.json`

**Interfaces:**

- Consumes: T3 的 README/架构/工具/发布说明章节及 T2 consumer 证据
- Produces: `docs/active/v2.3.0/mongodb-driver-alignment/plan.md` § Plan Verdict、Acceptance Criteria

**Behavior:**

以最终完整 worktree 验证依赖变更和既有模块未回归，并核对实际变更范围。证据全部齐全才更新 TD-040 状态和版本索引，保留历史锚点，不把初始化 smoke 描述成完整 MongoDB 功能验收。

**Acceptance Criteria:**

- [ ] AC1: quality-report 中 docs-full、verify-build-model、release-contracts、release-consumer、release-signing、java-quality 六阶段均 passed；TD-040 证据链接指向本文 Execution 中实际存在的报告路径。
- [ ] AC2: baseline/final manifest 差集覆盖所有新增、修改、删除、未跟踪、二进制和文件模式，实际 Changed Files 均获授权，已有用户改动未丢失。

**Execution:**

- **Status:** pending
- **Attempts:** 0
- **Blocked Reason:** null
- **Red Result:** null
- **Verify Result:** null
- **AC Result:** null
- **Changed Files:** []
- **Concerns:** none

**Task Completion Gate:**

- [ ] Red Result 存在且证明预期失败或前置状态。
- [ ] Verify Result 存在且通过。
- [ ] AC Result 中所有未延期 AC 均有通过证据，延期必须有用户风险接受记录。
- [ ] Changed Files 与 task input/output 快照差集相等且全部在声明范围。
- [ ] Per-task AC checkbox synced。

**Step 1: Red**

记录 `git status --short` 与 baseline manifest；检查 T1–T3 执行字段与 AC。缺证据或适用阶段未运行时，不允许完成状态。

**Step 2: Green**

运行完整门禁并保留新证据：

```bash
mongo_quality_dir=$(mktemp -d /tmp/mimir-mongo-quality.XXXXXX)
bash scripts/engineering.sh quality --mode full --source worktree --report "$mongo_quality_dir/quality-report.json"
```

该入口已包括 contracts、consumer、Java 和 docs，不机械重复完整子构建。全部适用阶段通过才将技术债标记已处理并保留 TD-040 锚点和证据链接；索引同步，POM revision/发布状态不变。

从 T2 留存证据构建 G1 溯源链，记录各 mode 的源状态指纹、候选 revision、发布 BOM 与该模式隔离缓存 BOM 的 SHA-256、selected-list/Surefire 路径及 README/台账链接。为避免临时目录被清理导致链断裂，T2 的证据复制必须同时保留这两份 BOM 或其带原始路径的哈希记录；比较不相等或证据跨轮则阻止完成。

**Step 3: Verify**

状态回写后运行 `bash scripts/engineering.sh docs --root "$PWD" --mode full --self-test` 与 `git diff --check`，生成最终 manifest。对照 baseline，T1–T4 Files 联集及 controller 元数据之外的新增变更不得放行；原有修改单列，不能回退。

**AC Verification:**

- AC1: 核对 quality-report 六个固定阶段 ID 的状态均 passed，测试失败/错误/跳过 0，新 Mongo suites 存在；未启用 Sonar 如实标不适用，不能称远端通过。读取每个证据路径确认非空，检查 TD-040 链接最终可达本文 Execution。
- AC2: manifest 差集、git 状态、任务 Changed Files 逐项相符；controller 写 Plan Verdict=completed。未通过则 blocked；只有用户明确接受逐项剩余风险并记录 Accepted Risks 的稳定 ID、主体、ISO-8601 时间和 Source 证据，才可 completed_with_concerns。不自动提交。

## 场景到断言映射

| Spec | Task | 必需断言/证据 |
|---|---|---|
| S01 | T2 | 无 Parent 的 bom fixture；sync/core/bson 选中版本均 5.0.1；发布来源与隔离 list。 |
| S02 | T2 | parent fixture 空 relativePath、无额外 BOM；三项同版，发布来源与隔离 list。 |
| S03 | T1、T2 | mongo-family profile 的 8 项均存在且 5.0.1；仅解析，不冒充其他驱动运行证据。 |
| S04 | T1、T2 | 人工 sync=4.11.5 清单被检查器拒绝，真实 Node 子进程 exit=1，stderr 有坐标/实际/期望；正常对照 exit=0。 |
| S05 | T2 | 两种同步 fixture 客户端/数据库非空、getName()=td040，无 LinkageError，关闭资源。 |
| S06 | T2 | 本地独占端口无 Mongo 服务端，初始化/关闭成功；无 ping/数据库操作。 |
| S07 | T2 | create("not-a-mongodb-uri") 抛 IllegalArgumentException，而非链接错误。 |
| S08 | T2 | runner 无 startupFailure，3 类 bean 各 1 个，工厂数据库名 td040，上下文关闭。 |
| S09 | T2 | 字符串 id/name 样本→Document→对象；_id=sample-1、name=alice，读回相同。 |
| S10 | T2 | 非法 URI 上下文有 startupFailure，原因链含 IllegalArgumentException、无 LinkageError。 |

## 作者自检

- [x] IC-01→T2，IC-02→T1/T2，IC-03→T1/T2，IC-04→T2/T4；S01–S10 全部可追踪。
- [x] 每任务均有 Red/Green/Verify、至少 2 项 AC、初始执行状态、范围核验及依赖。
- [x] 消费者保留预热/隔离/来源/回填/清理全链，文档不会扩大验证能力。

## Acceptance Criteria

- [ ] G1: 完成一条跨产物溯源链：对每个 mode 记录“baseline/source 状态指纹 → 候选 revision 与发布 BOM 的 SHA-256 → 同次隔离缓存 BOM 的相同 SHA-256 → 对应 selected-list 与 Surefire 报告路径 → README 验证范围 → TD-040 完成入口”；全部节点可读取、哈希相等、无跨轮证据混用。此为交付级一致性验收，不替代各任务局部通过条件。
