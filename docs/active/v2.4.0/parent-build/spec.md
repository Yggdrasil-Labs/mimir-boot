---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Parent 构建契约规格

> 推荐方案，未实施。D-82 至 D-89、D-97 等已确认约束来自 [brainstorm](./brainstorm.md)；新增细节是本轮推荐，不代表历史用户确认、构建成功或发布验收。

导航：[版本入口](../brainstorm.md) · [需求大纲](./brainstorm.md) · [设计](./design.md) · [实施计划](./plan.md)

## 概述与范围

下游继承 Parent 后，使用一个编译目标入口和彼此独立的构建开关；在源代码构建与发布制品消费中保持相同默认值和覆盖能力。覆盖 P-01 至 P-05、PC-01 至 PC-03；P-03、P-05 为已有能力的说明与验证。本规格描述实施后的目标，不声明当前 POM 已满足。

配置名是消费者公开输入，属于行为契约；插件组织、Flatten 策略和执行绑定见设计。

## 行为 PB-01：选择编译目标

### 场景：较新构建 JDK 保持目标 17

Given 下游使用 JDK 21 或 25，未设置 `java.version`，代码只使用 Java 17 API
When 执行编译
Then 编译成功的类文件主版本为 61
And 方法参数名继续可供反射读取，构建 JDK 不自动提高目标版本。

### 场景：显式提高目标版本

Given 下游使用 JDK 21，设置 `java.version=21`
When 编译只使用 Java 21 及以下 API 的代码
Then 类文件主版本为 65
And `java.version` 是 Parent 唯一承诺的编译目标属性入口。

### 场景：目标版本与代码或构建 JDK 不兼容

Given 目标仍为 17，但代码调用 Java 21 才提供的标准 API；或构建 JDK 为 17 而目标为 21
When 执行编译
Then 构建失败并显示编译器诊断
And 不静默改用构建 JDK 的 API 集或降低目标版本。

## 行为 PB-02：按构建 JDK 发现注解处理器

### 场景：高版本构建 JDK 自动发现

Given JDK 21 或 25，消费者已在编译 classpath 声明 Lombok、MapStruct 和所需处理器依赖
When 编译带注解的输入代码
Then 处理器运行并生成预期成员、映射类或配置元数据
And Parent 不要求消费者改成单独的处理器路径。

### 场景：旧构建 JDK 与消费者关闭处理

Given JDK 17 至 20，或消费者显式选择 `maven.compiler.proc=none`
When 编译无需生成代码即可通过的输入
Then 前者不被 Parent 默认传入 `proc=full`
And 后者不产生处理器生成文件；Processor 模块自身显式关闭处理的配置继续有效。

### 场景：消费者关闭必需的处理器

Given 输入代码依赖生成的映射类，消费者显式关闭注解处理
When 编译该输入
Then 因缺少所需生成类而编译失败
And Parent 不为消除错误偷偷重新启用处理器。

## 行为 PB-03：选择构建模式及自由组合

### 场景：未指定模式的公共开发默认

Given JDK 17、21 或 25，未指定 `dev/precheck/ci/prod`
When 对含一个单测和一个集成测试的独立消费者执行 `verify`
Then 两类测试均执行，覆盖率与格式检查默认跳过，签名与部署默认跳过
And JDK 适配 profile 的自动激活不改变这些结果。

### 场景：单独选择构建模式或组合

Given 消费者单独选择 `precheck`、`ci` 或 `prod`
When 执行相应生命周期
Then `precheck` 执行测试、覆盖率与格式检查；`ci` 还执行既有 Enforcer 约束
And `prod` 默认跳过测试编译及执行、跳过覆盖率、保留格式检查，仍跳过签名和部署。
And 多模式组合不被 Parent 强制拒绝，相同属性冲突按设计列明的声明顺序及有效模型解释，不以 `-P` 参数顺序承诺优先级。

### 场景：门禁真实失败

Given `ci` 默认检查开启，消费者存在格式错误、失败测试或覆盖率不达标
When 执行 `verify`
Then 对应门禁使构建以非零退出码失败
And Parent 不因 JDK 适配或其他能力的跳过值而隐藏该失败。

## 行为 PB-04：独立控制测试、覆盖率和格式

### 场景：只跳过一类测试

Given 单测和集成测试各有可观察的执行记录
When 设置 `skipTests=true` 执行 `verify`
Then 单测不执行，集成测试继续执行，测试代码仍编译
And 换成仅 `skipITs=true` 时，单测继续执行、集成测试不执行。

### 场景：覆盖与组合全部测试开关

Given `dev` 默认跳过覆盖率和格式检查，消费者显式设置 `jacoco.skip=false`、`spotless.check.skip=false`
When 对测试完整的输入执行 `verify`
Then 覆盖率和格式检查均执行
And 同时设置 `skipTests=true`、`skipITs=true` 仅跳过两类测试执行；`maven.test.skip=true` 才同时跳过测试编译。

### 场景：无测试数据时不制造覆盖率成功

Given 清洁构建中两类测试均跳过，`jacoco.skip=false`
When 执行 `verify`
Then 测试开关不改变 `jacoco.skip` 的有效值
And 如实呈现“未产生执行数据、覆盖率未检查”的插件结果，不能把它作为覆盖率达标证据。

## 行为 PB-05：按需启用 GPG 签名

### 场景：无私钥的默认消费者

Given 独立消费者没有 GPG 私钥，也未传 `gpg.skip`
When 分别执行普通 `verify` 和 `-Pci verify`
Then 不尝试签名，不因缺少私钥失败，且不产生 `.asc` 签名附件。

### 场景：显式开启和关闭

Given 消费者具有隔离测试密钥，显式设置 `gpg.skip=false`
When 执行至签名阶段
Then 产生可校验的签名附件
And 对同一输入显式设置 `gpg.skip=true` 时不签名；测试、覆盖率、格式与部署开关不随之改变。

### 场景：已开启签名但签名失败

Given 消费者显式开启签名，但密钥不可用或签名命令失败
When 执行至签名阶段
Then 构建以非零退出码失败并报告签名错误
And 不回退为无签名成功；本仓库显式选择的发布签名也保持该失败边界。

### 场景：框架自身发布须显式开启签名

Given 本仓库 Parent 公共 `gpg.skip=true`，激活 `maven-central` profile（D-97）
When 未传 `-Dgpg.skip=false` 与传入该参数分别生成有效模型
Then 前者 Parent、common 及全部 Starter 的有效签名均跳过，仅根与 BOM 由 profile 开启签名
And 后者全部 Reactor 模块的 GPG 插件及 `sign-artifacts` execution 有效 skip 为 false；正式发布命令保留该显式参数。
And 前者是实施时一次性观察记录，用于证明显式参数必要，不作为持续检查契约（见设计 PB-05）。

## 行为 PB-06：每模块一次默认格式检查并允许扩展

### 场景：ci 正常生命周期

Given 格式检查开启，消费者未增加额外格式检查执行
When 完成一次 `verify`
Then 每个适用模块由 Parent 安排的格式检查只执行 1 次
And 格式规则、扫描范围与检查阶段保持现状。

### 场景：关闭与下游扩展

Given 消费者显式设置 `spotless.check.skip=true`，或自行增加另一个执行
When 执行生命周期
Then 前者跳过 Parent 默认检查；后者保留消费者增加的执行
And 下游仍可直接调用 `spotless:check` 或 `spotless:apply`，直接调用不计入 Parent 默认调度次数。

### 场景：移除重复执行后的旧 ID 定制

Given 消费者曾按旧 `check` ID 定制 Parent 的重复检查
When 迁移到只保留默认 `spotless-check` 的版本
Then 使用说明明确旧定制可能变成新增执行或不再覆盖默认执行
And 迁移样例改用 `spotless-check` 后可控制默认执行；不承诺旧 ID 的覆盖语义无变化。

## 行为 PB-07：使用并覆盖基础生命周期插件版本

### 场景：使用已有默认版本

Given 消费者未指定 Clean、Resources、Install 插件版本
When 执行 `clean process-resources install`
Then 分别使用 Parent 已声明的 3.4.0、3.3.1、3.1.1
And 不因接通版本管理额外增加生命周期执行。

### 场景：消费者指定版本

Given 消费者覆盖 `maven.clean.plugin.version`、`maven.resources.plugin.version` 或 `maven.install.plugin.version` 为可解析且兼容的版本
When 执行对应目标
Then 实际执行消费者选择的版本，资源处理及本地安装结果可检查。

### 场景：消费者选择不可解析的插件版本

Given 消费者把其中一个版本设置为不存在的版本
When 执行对应目标
Then 版本解析失败且构建返回非零退出码
And 不静默回退 Maven 自带版本或 Parent 默认版本。

## 行为 PB-08：从发布 Parent 继承并准备产物与仓库

验收约束：消费者源码、生产者本地仓库、文件发布仓库、消费者本地仓库及需保留的验收证据，必须位于所有被测项目 `clean` 会清理的构建目录之外。消费者执行自身 `clean` 时，也不得删除其 POM、输入或持久证据。

### 场景：独立项目消费已 Flatten 的 Parent

Given 独立项目禁用 Parent 的源码相对路径回退，仅从隔离仓库解析本次制品
When 重复 PB-01 至 PB-07 的默认与覆盖场景
Then 与源码构建契约一致，消费者可按自己的 JDK 激活处理策略
And 不继承框架发布时临时开启签名、跳过测试等命令行值。

### 场景：分别关闭附件和显式选仓库

Given 默认生成源码包和 Javadoc，默认跳过部署
When 分别设置 `maven.source.skip=true`、`maven.javadoc.skip=true`，或显式开启部署并设置自己的快照/正式仓库
Then 各附件可分别关闭，其余产物不受联动影响
And 仅显式开启部署后才向所选隔离仓库写入对应版本；凭据不进入 POM。

### 场景：仓库通道或解析失败

Given 仓库不可访问、认证失败，或把 Central Portal Publisher 接口当作标准 Maven 仓库使用
When 尝试解析或显式部署
Then 保留实际错误与非零结果，不声称发布成功
And 使用说明区分 Central 发布扩展与标准私服部署；未执行的远程场景标明未验证。

## 全局约束

- 默认编译目标为 Java 17；验证构建 JDK 为 17、21、25 共 3 组，不提高 Spring Boot 3.3.13 基线（D-07、D-41、D-83）。
- 保留 4 个构建模式名称；测试、覆盖率、格式、签名、部署开关之间新增隐式联动数为 0（D-82、D-84 至 D-87）。
- 每个适用模块、每次正常生命周期的 Parent 默认格式检查调度数为 1；消费者主动扩展不受该上限约束（D-88）。
- 不新增 Mimir 专属目标版本、附件或仓库开关；Clean、Resources、Install 仅接通 3 个现有版本属性（D-42、D-43、D-89）。
- 本轮仅交付推荐 SDD；源码构建、发布前隔离消费者、CI、公开仓库消费者结果分别记录，不能互相替代。
