---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Parent 构建契约实施计划

> 推荐方案，未实施。本计划是静态实施输入，本轮只补文档；任务列表不表示已执行、已获代码实施/发布授权或消费者验收通过。

导航：[版本入口](../brainstorm.md) · [需求大纲](./brainstorm.md) · [规格](./spec.md) · [设计](./design.md)

## 目标与必要上下文

落实 P-01 至 P-05、PC-01 至 PC-03，对应规格 PB-01 至 PB-08。P-03、P-05 仅说明与验证已有能力。本仓库默认 Wrapper 为 Maven 3.10.0；Central 发布任务按[发布规程](../../../engineering/release.md)另用 Maven 3.9.16。当前源码版本仍由根 POM `revision` 管理，文档版本 v2.4.0 不代表根版本已切换。

源码入口：[Parent POM](../../../../mimir-boot-parent/pom.xml)、[根 POM](../../../../pom.xml)、[Parent README](../../../../mimir-boot-parent/README.md)、[Processor POM](../../../../mimir-boot-starters/mimir-boot-starter-mybatis-processor/pom.xml)。目前硬编码 skip、重复 Spotless、Flatten profiles 解析问题尚未修改；本计划不得把有效 POM 检查当作运行完成。

## 固定约束与推荐选择

- D-82：公共属性承载开发默认；四模式保留并各显式设置本模式质量值，JDK profile 不写质量属性。
- D-83：Compiler `release=${java.version}`，唯一目标属性，默认 17；删除 Parent 未用 source/target 属性和重复 `-parameters`。
- D-41：JDK≥21 通过推荐 `jdk-annotation-processing` profile 设置 `maven.compiler.proc=full`，17至20不设 full；classpath发现、消费者none与Processor自身none保留。
- D-84/D-85/D-86：原生 UT、IT、测试编译、覆盖率、格式开关独立，配置预设不限制下游组合，不承诺任意命令行属性都能覆盖下游插件字面量。
- D-87：Parent `gpg.skip=true`；推荐公共同 ID 签名执行，显式 false 开启；框架自身发布 `-Dgpg.skip=false` 保留；发布 Parent 不固化生产者开关。
- D-97：同步调整构建模型检查，发布场景按 `-Pmaven-central -Dgpg.skip=false` 校验插件与 execution 有效 skip=false；不在 Parent 增加同 ID 发布 profile。
- D-88：保留 `spotless-check@validate`，删 ci 的重复 `check`，说明旧ID迁移，保留消费者自定义执行。
- D-89：仅在管理中接通 Clean 3.4.0、Resources 3.3.1、Install 3.1.1 的现有版本属性，无新增execution、无升级。
- 推荐 Parent Flatten `profiles=keep`，其余现有保留/解析策略不变；这是需实际验证的技术选择，不是已确认成功事实。

## 行为契约摘要

| 场景 | 必须观察到的结果 |
|---|---|
| 未选模式或dev，JDK17/21/25 | 默认目标17；UT/IT执行；覆盖率/格式跳过；签名/标准部署跳过 |
| precheck | UT/IT、覆盖率、格式执行，不额外激活Enforcer |
| ci | precheck能力 + 既有Enforcer，默认不签名 |
| prod | 默认跳过测试编译和执行、跳过覆盖率，保留格式检查；默认不部署/签名 |
| 多模式组合 | 按设计声明顺序解释冲突属性；JDK profile不改变质量默认；交换-P顺序不定义不同优先级 |
| skipTests / skipITs | 仅控制对应测试执行，两者可组合；maven.test.skip控制测试编译和执行 |
| dev/prod显式打开jacoco/Spotless | 能力实际进入执行；prod还需maven.test.skip=false才能产生测试数据 |
| 发布后的独立消费者 | 默认/覆盖结果与源码契约一致，无相对源码回退、不泄漏生产者签名及测试参数 |

## 任务依赖与文件归属

| 任务 | 依赖 | 文件归属及并行边界 |
|---|---|---|
| T1 编译、公共默认及独立能力 | 无 | 独占 `mimir-boot-parent/pom.xml`、`verify-build-model.mjs`、`tools/engineering/README.md` 第 73 行及根 POM 第 54、250-251 行注释 |
| T2 Flatten及版本管理收口 | T1 | 同一POM，必须串行，不另起并行写入者 |
| T3 源码行为与发布前消费者验收 | T2 | 不改正式文件；全部验收输入、隔离仓库及持久证据置于仓库外唯一临时工作根，按场景分目录；不得放入任何被测项目的构建目录 |
| T4 使用说明与最终回归 | T3 | 独占 `mimir-boot-parent/README.md` 及 `docs/engineering/release.md` 一句签名参数说明；正式POM问题返回T1/T2修改后重验受影响项 |

与BOM/Processor的联合样例在其版本和生成契约可用后运行；文档入口：[BOM](../bom-dependencies/plan.md)、[Processor](../mybatis-processor/plan.md)。Parent不改这些模块的生产代码或POM；协调验收输入，不产生新的Starter依赖。

## T1：编译、公共默认及独立能力

目的：修复源模型公共契约，保持模式实际职责。

文件：

- Modify: `mimir-boot-parent/pom.xml` — 编译配置、公共开关、模式预设、JDK profile、JaCoCo/GPG组织、Spotless调度。
- Modify: `tools/engineering/src/quality/verify-build-model.mjs` — 按 D-97 把发布场景改为 `-Pmaven-central -Dgpg.skip=false`，断言插件与 execution 有效 skip=false 且存在 `sign-artifacts`；发布场景必须跳过 `properties/gpg.skip` 断言（有效 POM 中该属性仍为模型值 true，现有 `requireGpgConfiguration` 第 43-46 行先断言属性，沿用会失败），只有默认场景断言属性唯一且为 true；默认场景其余断言不变。同步第 124 行汇总输出文案。变更说明须写明设计中的 `tools/` 准入理由。
- Modify: `tools/engineering/README.md` — 第 73 行 `build-model` 用途“default/maven-central 签名开关”同步为默认场景与 `-Pmaven-central -Dgpg.skip=false` 发布场景（依据工具准入约束第 16 行的文档引用同步要求）。
- Modify: 根 `pom.xml` — 仅修正第 54 行 `gpg.skip` 注释与第 250-251 行 `maven-central` profile 注释：说明发布命令须显式传 `-Dgpg.skip=false`，并把不存在的 Parent README 章节引用改为发布规程。
- 不创建正式脚本或持久化专项测试目录；不修改根POM发布profile、workflow、BOM或Processor。

实施约束：

1. Compiler用release直接引用java.version；保留parameters=true，清理Parent两个旧属性和重复compilerArgs。不增加maven.compiler.release别名，不修改Processor的proc=none。
2. 新JDK profile仅设置原生maven.compiler.proc，默认不加处理器路径；核实编译与测试编译均受该入口控制。
3. 公共布尔默认：skipTests/skipITs/maven.test.skip=false，jacoco.skip/spotless.check.skip/gpg.skip/maven.deploy.skip=true。dev的activeByDefault保留；precheck/ci显式覆盖测试编译、覆盖率、格式为false；prod为true/true/false。
4. Surefire删除skipTests字面量；Failsafe不引入skipTests；JaCoCo公共激活和原生skip引用，移除dev/prod硬编码与冗余模式激活，保持原有阈值和exec/report路径。
5. GPG把Parent的ci签名能力移到公共路径，用相同sign-artifacts@verify与根合并，所有skip均引用属性；在Parent公共声明保留根既有batch/loopback参数，不能在Flatten后丢失非交互配置。根发布显式签名保留，不修改真实凭据或用户GPG环境。
6. Spotless只保留spotless-check默认调度，移除ci重复check；格式规则、validate阶段和下游扩展能力不变。

验收：

- 生成源码有效POM：默认/四模式/JDK激活和组合都符合设计矩阵，默认值不依赖dev实际激活。
- 使用清洁临时消费者做快速编译冒烟：当前 JDK 下默认类版本61、`java.version=21` 时65，UT/IT 执行标记可观察；JDK17/21/25 完整矩阵、高版本API负向输入、处理器关闭负向样例及 Processor 自身 none 统一归 T3，T1 不重复。
- gpg.skip默认不调用私钥；false路径使用隔离测试密钥或留待T3，不使用发布凭据。
- `bash scripts/engineering.sh build-model` 在 JDK 17 下通过：全部 Reactor POM 的默认场景为 true、发布命令场景为 false；同时以未修改断言的旧脚本复现"仅 `-Pmaven-central` 失败"作为反例证据，证明调整必要。

## T2：Flatten及版本管理收口

目的：让发布POM保留消费者可覆盖契约，并接通三个已有插件版本。

文件：

- Modify: `mimir-boot-parent/pom.xml` — Flatten `profiles: resolve → keep`，增加Clean/Resources/Install管理条目。

实施约束：

- 保留properties/build/pluginManagement=keep、parent/dependencies/dependencyManagement=resolve及oss模式；不全仓库统一更换Flatten策略。
- 新管理条目引用现有版本属性，保留3.4.0/3.3.1/3.1.1；不额外激活或绑定执行。
- 检查整个profile集合与继承，不仅检查GPG；若keep不能满足契约，停在本任务修正设计，不用消费者临时参数作为补丁，不转为删除签名能力。

验收：

- 生成的实际发布Parent保留四模式、JDK激活、公共默认与属性引用；Parent/BOM实际坐标无未解析revision。
- 默认和自定义三个插件属性的有效模型对应实际插件版本；不存在版本必须失败。
- 使用签名开启、ci、不同生产者JDK生成制品后，消费方默认值不被生产者临时参数污染；最终结论以T3消费实际上传POM为准。

## T3：源码与发布前隔离消费者验收

目的：验证配置流最终行为，覆盖默认、覆盖、边界与错误输入。

文件范围：

- 不修改正式文件。
- 在仓库外通过 `mktemp -d /tmp/parent-build-acceptance.XXXXXX` 创建唯一临时验收工作根；临时 Create: 其下的场景目录、消费者 POM/Java 输入、生产者本地库、文件发布库、消费者本地库及 evidence。全部均须位于参与 reactor 的项目 `target` 之外，且消费者自身 `clean` 不得删除源码/POM或持久证据；不提交为项目测试目录或一次性工具。第三方缓存、生产者库、文件发布库、消费者本地库和证据分开。

验收实施顺序：

1. 首先创建仓库外唯一临时工作根，固定其绝对路径；随后创建消费者目录、POM/源码、分离的生产者本地库、文件发布库、消费者本地库及 evidence 目录。确认这些输入和持久证据均不位于任何参与 reactor 的项目 `target` 下，也不位于消费者 `target` 下。再固定源码状态、待验版本、Wrapper/JDK，准备一个有可观察UT、IT、分支被测类和处理器输入的消费者。先核对当前Java可执行文件与版本，不把安装JDK等同实际使用JDK；缺少17/21/25任一环境标明未覆盖，不自行升级/安装。
2. 按设计“验证策略”逐项运行PB-01至PB-07；每个场景可清理项目构建输出以避免遗留生成文件、测试报告、jacoco.exec与.asc，但不得将验收输入或持久证据置于该 clean 会删除的路径。保存命令、退出码、有效模型、日志及产物至 evidence。
3. 按设计“发布前隔离消费者验证步骤”生成并向文件仓库部署本次产物；生产者 `-am clean deploy` 会清理所有参与 reactor 项目的 `target`，因此消费者 POM/源码和仓库/证据必须在该命令前准备于仓库外工作根。测试密钥生产者开启签名与默认不签名分开。maven-central只用于模型或执行至verify的回归，禁止触发远程deploy。
4. 独立消费者relativePath为空，固定版本，确认解析缓存POM哈希等于本次发布POM；不能从reactor或旧本地Mimir缓存兜底。以实际发布Parent重复三JDK、四模式及默认值，关键组合与原生覆盖均验证。
5. 默认无GPG密钥、未传gpg.skip的普通verify和ci verify均不签名；显式false可验证附件，显式true不签名；签名命令失败阻断。回归根/Parent/真实JAR自身显式签名，每构件只签一次。
6. 覆盖skipTests/skipITs四种组合、maven.test.skip、prod重新开启、全部测试跳过但jacoco.skip=false；无覆盖率数据只能报告未检查。
7. Spotless按模块统计一次默认调度；覆盖开/关、旧check ID迁移和额外execution保留。不能因多模块或手动check产生多次日志就判断Parent重复。
8. 单独关闭源码/Javadoc附件，正式/快照分别部署到不同file仓库并核对路径；标准部署默认无写入。协议/认证线上结果不在本地证明范围。
9. 本仓库 reactor 在 JDK 21/25 下的构建回归（审查补充）：框架自身模块继承 Parent，JaCoCo/GPG 公共路径、skip 默认值与 JDK profile 会改变自身构建；在 JDK 21、25 下各运行一次本仓库 `./mvnw -B clean verify`（不传 gpg.skip），确认与 JDK 17 结果一致、默认不签名、覆盖率与格式默认值不变，处理器 full 不导致既有模块编译差异。

JDK 完整矩阵归属：PB-01/PB-02 的 JDK17/21/25 编译、类版本、高版本API负向和处理器开关验收只在 T3 执行，T1 仅做快速冒烟。

已知缺口（审查补充）：CI 固定 JDK 17，构建模型检查也要求 Java 17，JDK≥21 的 `proc=full` 与 JDK profile 行为只由本任务一次性矩阵覆盖，无持续回归。已登记为 [TD-048](../../tech-debt-tracker.md#td-048-jdk-高版本构建回归)，本需求不改 CI、不新增工具。

通过标准：规格每项Then有对应报告、产物或可重现失败证据；只看到有效POM不算运行通过；环境不足、解析失败或未执行的场景明确保留未验证。已有[工程验收入口](../../../engineering/testing.md)可用于基础回归，但其已有消费者命令若显式传gpg.skip=true，不能替代这里的默认签名场景。

## T4：说明与最终回归

目的：把已验证的实际行为写成下游可用的接入说明。

文件：

- Modify: `mimir-boot-parent/README.md` — 唯一目标入口、JDK/处理器、模式矩阵、独立开关、组合优先级、Spotless ID迁移、插件版本覆盖、签名可选、附件/仓库用法。
- Modify: `docs/engineering/release.md` — 补一句：Central 发布必须显式传 `-Dgpg.skip=false`，按 D-97 仅 `-Pmaven-central` 已不再为 Parent 及其子孙模块开启签名（只对根与 BOM 生效）。当前该规程未写此参数，依据仅为 `release.yml` 第 282 行。

说明必须包含：

- java.version迁移及自行覆盖Compiler/argLine的边界；模式组合是属性合并而非强制互斥。
- 消费者设置 `maven.compiler.release` 会被静默忽略：Parent 写入字面 `<release>${java.version}</release>`，须改用 `java.version`。
- skipTests不关闭IT、skipITs不关闭UT，prod重新开启测试还需maven.test.skip=false；覆盖率无数据与通过不同。
- `-DskipTests` 不跳过 IT 依赖默认 Failsafe 3.6.0；降级或改写 `maven.failsafe.plugin.version` 可能改变该边界，需自行验证。
- precheck和prod均保留实际格式检查，覆盖率报告在verify汇总，不继续写“不含集成测试”。
- GPG默认跳过且独立，发布Parent仍可覆盖；本仓库自身正式发布保持原workflow的显式签名参数。
- 两个附件skip独立，仓库使用原生覆盖和settings认证；file仓库示例不混为Central Publisher命令，真实远程未验证如实标记。

验收：

- README命令与T3实际证据逐项一致，不把推荐、配置存在、阶段执行和结果通过混写；T3 的验收根不处于 reactor/消费者 clean 路径，消费者自身 clean 后输入和持久证据仍在。
- 实施全部完成且多人文档写入稳定后，按项目规程运行一次 `bash scripts/engineering.sh quality --mode full --source worktree`；该入口已包含 Maven `clean verify`，无需在同一状态重复执行。开发中可按需单独运行 Maven 定向排查；T3 的隔离发布消费者验证仍按其契约执行。这是将来代码变更的回归，本轮仅文档不执行。
- 最终说明分别列源码检查、隔离发布消费者、CI和公开仓库消费者状态。未发布时不声称线上制品可用。

## 决策边界

实施者可选择临时样例组织、测试依赖的已兼容版本和证据保存方式，可清理同一Parent配置的空plugin块；前提是规格输入、默认值、执行ID、原生入口和独立性不变。不得借此升级插件、改变覆盖率阈值、扩大扫描范围、增加测试开关联动、修改处理器发现方式或新增工具脚本。

D-97 列明的构建模型检查（含工具 README 与输出文案同步）、根 POM 注释及发布规程签名参数说明不属于扩大范围。除此之外，实际发现Flatten keep与现有发布结构冲突、组合矩阵不成立、根属性导致编译冲突或需要修改根/BOM/workflow/tools时，先返回确切有效模型与最小反例，修订设计及文件范围后处理；不静默扩大实施范围。本次只写SDD，不进入上述实施任务，不commit/push或执行外部发布。
