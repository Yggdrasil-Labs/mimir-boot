---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Parent 构建契约方案大纲

> 推荐方案，未实施。既有用户确认决定继续有效；新增设计推荐不冒充历史确认，运行与发布继承结果仍须验收。

本稿从[版本级 brainstorm](../brainstorm.md)拆出 Parent 需求，保留讨论与决定来源。根据本轮“不要再问，先给一些推荐的方案；把每个需求的 SDD 补齐”的要求，剩余实现选择已形成推荐设计，不再以逐项提问作为文档前置；仅补文档，不实施 POM 或发布操作。

产物导航：[行为规格](./spec.md) · [详细设计](./design.md) · [静态实施计划](./plan.md) · [版本入口](../brainstorm.md)。规格覆盖 P-01 至 P-05、PC-01 至 PC-03；下文仍保留“建议”“后续验证”的历史讨论语气，具体推荐与验收以设计、计划为准。

## 需求范围

| 需求 | 版本级决策 | 期望结果 |
|---|---|---|
| P-01 编译目标约束 | D-07 | 保留 `java.version` 默认 17，使语言、字节码和标准 API 同受目标版本约束 |
| P-02 注解处理器发现 | D-41 | 保留 classpath 发现；构建 JDK 21 及以上显式启用处理，允许消费者关闭 |
| P-03 构建产物说明 | D-09、D-42 | 说明源码包和 Javadoc 的原生关闭入口，验证发布 Parent 的继承行为 |
| P-04 测试与质量开关 | D-11、D-13 | 保留四个 profile 的职责与默认行为，消除写死配置造成的覆盖障碍 |
| P-05 私有仓库说明 | D-12、D-14、D-43 | 保留默认跳过部署，说明消费者覆盖仓库的方式 |
| PC-01 可选 GPG 签名 | D-87 | 下游可完全不启用签名；发布 Parent 保留可覆盖的签名开关 |
| PC-02 合并格式检查 | D-88 | 合并 Parent 重复声明的 Spotless 检查，保留独立开关及下游扩展能力 |
| PC-03 接通插件版本属性 | D-89 | 在插件管理中引用 Clean、Resources、Install 的现有版本属性，允许下游覆盖 |

P-03、P-05 属于已有能力的说明与验证。PC-01 至 PC-03 已分别按 D-87 至 D-89 纳入本轮设计；这表示需求范围确认，尚未实施或完成消费者验证。

设计遵循用户明确的核心目标（D-86）：Mimir Boot 提供能力，让下游自由组合。Parent 的 profile 提供构建预设，各项能力仍可独立控制与覆盖；框架说明组合的实际依赖和冲突，不用预设替代消费者的工程选择。

## 已核对的代码事实

入口是 [Parent POM](../../../../mimir-boot-parent/pom.xml)、[Parent README](../../../../mimir-boot-parent/README.md)及[根 POM](../../../../pom.xml)。本轮仅静态阅读，未生成有效 POM、执行构建或验证发布制品。

- Compiler 当前直接把 `java.version` 用于 `source`、`target`，同时配置 `parameters=true` 与 `-parameters`；另声明了未被该配置引用的 `maven.compiler.source`、`maven.compiler.target`。
- Parent 当前没有默认处理器路径；Processor 模块为构建自身显式设置 `proc=none`，不能被 Parent 新默认值覆盖。
- Flatten 保留 `properties`、`pluginManagement`、`build`，但对 `profiles` 使用 `resolve`；源码引用可覆盖不等于发布 POM 仍可覆盖。
- `dev` 使用 `activeByDefault`，设置 JaCoCo、Spotless 跳过；`dev`、`prod` 又在 JaCoCo 插件配置中写死 `skip=true`。
- Surefire 写死 `skipTests=false`；Failsafe 已绑定 `integration-test` 和 `verify`，两者的测试选择器不同。
- `precheck` 没有显式关闭 Spotless，不能把它描述为“只跑测试和覆盖率”。
- 源码包和 Javadoc 默认绑定 `package`；部署默认跳过，正式仓库地址为 Central Portal 发布接口。

## 编译目标与处理器发现

### 已确认：编译目标只承诺 java.version 入口（D-83）

2026-10-29 用户选择方案 A：Parent 只承诺 `java.version` 作为编译目标配置入口，默认 17；编译插件的 `release` 直接引用该值，替代 Parent 当前的 `source`、`target` 配置。

- 构建 JDK 由消费者选择，`java.version` 约束语言、字节码与标准 API；使用较新 JDK 不自动提高目标版本。
- 不新增 `maven.compiler.release` 与 `java.version` 的双入口覆盖契约；消费者仍可自行配置编译插件，但自行承担偏离 Parent 默认契约的合并与兼容验证。
- 沿用 P-01 已记录的清理方向：删除 Parent 中未被使用的 `maven.compiler.source`、`maven.compiler.target` 属性，去掉重复的 `-parameters` 参数并保留 `parameters=true`。
- 旧消费者显式配置 `source`、`target` 的迁移，以及 Flatten 发布后 `java.version` 是否仍可覆盖，需要后续验证；本轮未修改 POM。

未选择的方案 B 是让 `maven.compiler.release` 默认跟随 `java.version` 并允许独立覆盖。它便于直接使用原生属性，但新增两个目标值之间的优先级和影响范围，当前采用单入口方案。

### 注解处理器接入

P-02 继续遵循已确认边界：JDK 17 至 20 不默认传 `proc=full`，21 及以上启用；不默认配置 `annotationProcessorPaths`，保留 `maven.compiler.proc` 的消费者入口。

按构建 JDK 激活专用 profile 时，需要处理它与默认 profile 的关系。按版本级核对结论，同一 POM 其他 profile 激活可使 `activeByDefault` 的 `dev` 失效；必须验证 JDK profile 是否因此改变默认 JaCoCo、Spotless 行为。

### 已确认：默认行为与 JDK 适配分离（D-82）

2026-10-29 用户选择方案 A：开发默认值放到 Parent 公共属性层；保留 `dev`、`precheck`、`ci`、`prod`，各构建 profile 显式覆盖自身需要的值；JDK profile 只负责注解处理配置。

| 场景 | 必须保持的结果 |
|---|---|
| 未指定构建模式 | 执行现有开发默认行为，不依赖 `dev` 是否仍被自动激活 |
| 指定 `dev/precheck/ci/prod` | 保留该模式现有职责及默认行为，不因公共默认值下沉而改变有效配置 |
| 构建 JDK 21 及以上 | 默认设置 `proc=full`，继续允许消费者覆盖 |
| 构建 JDK 17 至 20 | 不默认传入 `proc=full` |

未选择的方案 B 是在 `dev` 和 JDK profile 中分别维护等价的开发默认值：原配置位置调整较少，但会重复维护并耦合 JDK 与质量检查配置。

各模式的显式覆盖项已补入[详细设计](./design.md)：`precheck`、`prod` 显式保留格式检查，公共默认与 JDK 适配分工，属性优先级与组合矩阵均有推荐。它们及 Flatten 发布后的继承行为尚待实际验证；D-82 不代表结果已验证。签名可选契约按 D-87 展开。

## 测试与质量开关

建议先按插件原生语义整理开关，再消除字面量配置；不引入一组与插件开关重复的 Mimir 属性。

### 已确认：原生测试开关独立控制（D-84）

2026-10-29 用户选择方案 A：单元测试和集成测试沿用各自原生跳过开关，不建立 `skipTests` 到 `skipITs` 的默认联动。

| 场景 | 拟定使用契约 |
|---|---|
| 仅跳过单测执行 | `skipTests=true`，不联动集成测试开关 |
| 仅跳过集成测试执行 | `skipITs=true`，不联动单测开关 |
| 跳过两类测试执行 | 同时设置 `skipTests=true` 与 `skipITs=true`；不由这两个开关关闭测试编译 |
| 连测试编译一起跳过 | 使用 `maven.test.skip=true`，保留该原生入口及 `prod` 的既有默认行为 |

本地 Surefire/Failsafe 3.6.0 插件描述符已核对：Surefire 的 test 目标提供 `skipTests`，Failsafe 的 integration-test 目标提供 `skipITs` 且没有 `skipTests` 参数；两者均提供 `maven.test.skip` 对应入口。这是参数声明依据，不代表当前 Parent 写死的 `skipTests=false` 已修复或发布消费者已经验证。

未选择的方案 B 是让 `skipITs` 默认跟随 `skipTests`，显式设置后再单独覆盖。它能缩短常用命令，但会增加框架特有的联动与优先级规则。

### 已确认：测试与质量检查独立控制（D-85）

2026-10-29 用户选择方案 A：测试开关不改变 `jacoco.skip`；需要跳过覆盖率时由下游显式设置。格式检查继续由 `spotless.check.skip` 独立控制，各 profile 已确认的默认行为保留。

只关闭单测时，集成测试仍可能提供覆盖率数据；全部测试关闭也不触发框架额外关闭覆盖率的联动。未选择的方案 B 是检测全部测试关闭后自动跳过覆盖率，因为它会增加隐式规则与显式覆盖之间的优先级。

能力可以组合，但覆盖率采集与校验仍依赖真实执行数据；开关配置本身不能证明覆盖率检查已执行或通过，需如实报告插件的实际结果。

### 覆盖关系与组合边界

| 场景 | 需要讲清的入口与边界 |
|---|---|
| 单独调整覆盖率或格式 | `jacoco.skip`、`spotless.check.skip` 与 profile 默认值的覆盖关系 |
| 显式打开 `dev`、`prod` 的覆盖率 | 去除硬编码后，需要同时具备插件执行与未跳过的测试输入 |

按 D-86 收敛的设计方向是保留原生属性覆盖与组合能力，去除 Parent 写死开关造成的障碍；具体实现仍需区分命令行属性、消费者属性、消费者插件配置和 Parent 默认值的有效优先级，不笼统承诺任意消费者配置都能被 `-D` 覆盖。

四个 profile 单独启用与常用组合都需有行为表，优先说明 Maven 原生合并结果，不额外引入强制互斥流程。多 profile 同时设置相同属性时，需以有效 POM 和实际执行结果解释，不能把命令行书写顺序当作可靠优先级。发布后的 Parent 也需保留相同的可覆盖入口。

## 已确认：GPG 签名为可选能力（D-87、PC-01）

2026-10-29 用户明确：实际项目可以不要求 GPG 签名。PC-01 据此纳入本轮设计，目标是保证下游可完全不启用签名，需要时再显式开启。

- Parent 默认跳过签名，普通构建和质量检查无需配置 GPG 密钥；仅选择 `dev`、`precheck`、`ci` 或 `prod` 不应强制启用签名。
- 沿用原生 `gpg.skip` 入口：默认 `true`，下游需要签名时显式设置为 `false`，也可在自己的发布 profile 中配置。
- 签名与测试、覆盖率、格式检查、部署开关独立。下游根据目标仓库及自身发布要求选择签名配置；Mimir 不替项目统一要求所有发布都签名。
- 发布后的 Parent 必须保留可覆盖的签名配置，不能因框架发布时启用了签名，就把相同要求固化到下游的 `ci` 中。
- 本仓库自身发布流程的显式签名与对外 Parent 的默认行为分别核对，避免修复消费者契约时关闭框架自身已选择的发布签名。
- 2026-10-31 用户确认（D-97）：Parent 公共 `gpg.skip=true` 会覆盖根 `maven-central` profile 注入的 false，现有构建模型检查对 `-Pmaven-central` 断言 false 将失败；采用调整 `tools/engineering/src/quality/verify-build-model.mjs` 断言的方案，不在 Parent 内增加同 ID 发布 profile。

具体推荐见[详细设计](./design.md)：Parent 明确公共 `gpg.skip=true`，签名能力移入公共路径并保留 `sign-artifacts` ID，Flatten 的 `profiles` 推荐使用 `keep`。该选择必须验证全部模式及框架自身显式签名，不通过删除整个 GPG 能力回避问题，也不表示 `keep` 已获运行验证。

后续验证应覆盖发布 Parent 的独立消费者：未配置 GPG 密钥时普通 `verify`、`-Pci verify` 不尝试签名；显式开启时进入签名路径；显式关闭时不进入。签名开启用例使用隔离测试凭据，分别观察实际产物与默认无签名产物，不以有效 POM 检查代替运行验证。本轮仅确定设计契约，尚未修改 POM 或执行签名验证。

## 已确认：合并 Spotless 重复检查（D-88、PC-02）

2026-10-29 用户确认“合并，纳入”。本轮合并 Parent 引入的重复格式检查；每个适用模块在一次正常构建生命周期中，启用检查时仅执行一次由 Parent 默认配置安排的 Spotless 检查，显式关闭时跳过。

- 当前通用 `spotless-check` 与 `ci` 内额外的 `check` 均绑定 `validate`。候选实现优先保留通用 execution，移除 `ci` 的重复调度，profile 仅控制默认开关。
- 保留 `spotless.check.skip` 独立控制，不与单测、集成测试、覆盖率或签名联动；各构建 profile 的原有默认检查行为保持。
- 保持格式规则、扫描范围和检查阶段；合并不改变为自动格式化，也不取消下游主动调用 `spotless:check`、`spotless:apply` 的能力。
- 下游可以自行扩展或覆盖 execution；本轮只消除 Parent 自身造成的重复，不删除下游自定义执行。消费者若按旧 `check` ID 覆盖配置，迁移影响需单独核对并说明，不能把 execution ID 变动视为必然无影响。

后续验证包括源码与发布 Parent 的有效执行配置、实际构建日志中的检查次数、显式关闭/重新开启、各 profile 默认行为，以及下游按 execution ID 定制的场景。按模块统计检查次数，不能把多模块各执行一次误判为重复。本轮尚未修改 POM 或运行 Spotless 行为验证。

## 已确认：接通插件版本属性（D-89、PC-03）

2026-10-29 用户确认纳入 PC-03。在 Parent 的 `pluginManagement` 中声明 Clean、Resources、Install，并引用现有版本属性；不因这项调整额外添加 execution 或改变生命周期绑定。

| 插件 | 沿用属性 | 现有声明值 |
|---|---|---|
| `maven-clean-plugin` | `maven.clean.plugin.version` | 3.4.0 |
| `maven-resources-plugin` | `maven.resources.plugin.version` | 3.3.1 |
| `maven-install-plugin` | `maven.install.plugin.version` | 3.1.1 |

上述值来自本轮静态核对的 Parent POM，本需求接通已有属性，不另做插件升级。下游可在继承 Parent 后覆盖属性，或按 Maven 原生方式自行配置插件；发布后的 Parent 需保留这些覆盖入口。

对未显式配置插件版本的消费者，生效默认值可能由 Maven 自带值变为 Parent 声明值，因此需要对比有效 POM 并验证 clean、资源处理、install 的实际行为。验收同时覆盖默认值与下游自定义值，不把属性存在当作插件已使用该版本的证据。本轮仅纳入设计，未改 POM 或执行构建。

## 发布 Parent 的继承与说明

建议把发布制品消费者作为统一验收入口：先验证 Flatten 产物，再从独立项目继承该 Parent；不能只在源码 reactor 中验证成功。

- 对 P-01、P-02、P-04，检查编译属性、JDK 激活条件和可覆盖开关是否在发布 POM 中保留。
- 尽量将消费者可覆盖的插件配置保留在已有 `build`、`pluginManagement` 路径；是否需要调整 `profiles=resolve`，应以最小样例判断。
- PC-01 已按 D-87 纳入；若改用 `profiles=keep`，仍需分析全部 profile 的属性解析与激活行为，不能只验证签名后就认定该变更无其他影响。
- P-03 只说明 `maven.source.skip`、`maven.javadoc.skip`；默认继续生成附加产物，不另设开关。
- P-05 说明 `distributionManagement` 与 `altReleaseDeploymentRepository`、`altSnapshotDeploymentRepository`；仓库认证仍在 settings 或 CI。
- Central 正式地址与标准 `deploy` 是否兼容仍需验证。示例应明确发布通道，避免把私服部署命令当作 Central 发布流程。

## 插件问题的设计范围

| 问题 | 当前依据 | 本轮处理边界 |
|---|---|---|
| PC-01 `ci` 意外签名 | 版本级记录曾核对发布 Parent 的签名值固化；源码仍存在相关引用 | 已按 D-87 纳入设计：默认跳过，按需启用，发布 Parent 保留覆盖能力 |
| PC-02 Spotless 重复调度 | `spotless-check` 与 `check` 两个 execution 均绑定检查 | 已按 D-88 纳入：合并 Parent 重复检查，保留开关并核对下游覆盖兼容性 |
| PC-03 插件版本属性未接入 | Clean、Resources、Install 属性未接到插件管理 | 已按 D-89 纳入：引用现有版本属性，保留下游覆盖，不新增执行绑定 |

PC-01 的验证必须包含未额外传签名跳过参数的默认消费者场景，不能仅靠命令行临时绕过签名，就声称默认可选契约已经成立。

## 关联需求与验收方向

- 与 [BOM 配套依赖](../bom-dependencies/brainstorm.md)联合验证 Lombok、MapStruct、binding；Parent 决定处理器是否运行，BOM 决定依赖版本。
- 与 [MyBatis Processor](../mybatis-processor/brainstorm.md)联合验证 classpath 发现与生成结果，避免仅消除版本告警却未运行处理器。
- JDK 17、21、25 分别验证默认目标 17；可支持的较高目标另测。检查生成源码、配置元数据和类文件版本，并加入使用高版本标准 API 的负向样例。
- 按 profile 检查单测、集成测试、JaCoCo、Spotless 的实际执行；开关关闭与重新开启都应有可观察结果。
- 独立消费者通过仓库解析 Parent，禁用本地源码 `relativePath` 回退；核对它消费的是本次 Flatten 产物。
- 源码包、Javadoc 与快照/正式版路由先在隔离本地仓库验证。真实远程部署及凭据使用不属于本稿授权。

## 本轮推荐与待验证事项

Parent 的需求范围与主要设计方向已按 D-82 至 D-89 收敛：单一编译目标入口、JDK 与构建模式分工、原生能力独立控制，以及三个插件问题的处理。按 D-86 的自由组合目标，本轮直接补齐[规格](./spec.md)、[设计](./design.md)和[计划](./plan.md)；推荐细节不增加用户确认的 D 编号。

推荐采用公共默认、独立 JDK profile、公共 JaCoCo/GPG 激活、原生 skip 属性、仅保留 `spotless-check` 默认调度，以及 Parent `profiles=keep`。详细设计给出默认值、profile 单独及组合矩阵、旧 execution ID 迁移和发布消费者步骤。Flatten 行为、属性优先级与组合、签名产物、实际执行次数及 JDK 17/21/25 处理器结果仍未验证；不得将 SDD 完整等同 POM 已修复或消费者已通过。统一审查由主流程组织，本轮不执行代码实施或真实发布。
