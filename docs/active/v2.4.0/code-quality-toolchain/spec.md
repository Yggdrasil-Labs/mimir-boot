---
version: v2.4.0
status: draft
created: 2026-11-02
updated: 2026-11-06
---

# 代码质量工具链规格

> 已确认的实施目标；D-110 确认独立插件入口和隔离报告目录。本规格不声明当前构建已满足，交付以实际验收为准。

导航：[版本入口](../brainstorm.md) · [方案大纲](./brainstorm.md) · [设计](./design.md) · [实施计划](./plan.md)

## 概述与范围

开发者在本地构建时就能发现注释、命名、坏味道、重复代码和依赖方向问题；本仓库违规即失败，下游默认只看到报告。覆盖 Q-01～Q-06。

属性名、模式名、规则 jar 坐标、报告路径和 ArchUnit 规则库的公开类型属于消费者可见契约；插件组织、规则清单与执行绑定见设计。下文“下游”指继承发布后 Parent 的消费者，“本仓库模块”指 `mimir-boot-common` 及 `mimir-boot-starters` 下的模块。

## 行为 CQ-01：注释与命名检查

注释与命名由 PMD 规则集中选定的 documentation、codestyle 规则负责（D-109），违规与坏味道一起出现在 `target/mimir-quality/pmd.xml`。

### 场景：公开方法缺少 Javadoc

Given 下游 `src/main/java` 中一个 public 类的 public 方法 `findById` 没有 Javadoc，且未设置任何新属性
When 执行 `./mvnw -Pprecheck verify`
Then 构建成功
And 控制台输出一条指向该文件与行号的 `CommentRequired` 违规，`target/mimir-quality/pmd.xml` 中包含同一违规。

### 场景：中文 Javadoc 与豁免范围

Given 公开方法的 Javadoc 首句为“根据主键查询用户。”且无英文句号；另有带 `@Override` 且无 Javadoc 的方法、无 Javadoc 的 getter/setter、protected 方法、private 字段、public 构造器，以及一个 package-private 类及其中的 public 方法，均无 Javadoc
When 执行 PMD 检查
Then 以上各处均不产生违规。

### 场景：命名违规

Given 公开类中有方法 `Get_User` 与局部变量 `user_name`
When 执行 PMD 检查
Then 分别报告 `MethodNamingConventions` 与 `LocalVariableNamingConventions` 违规。

### 场景：不检查的 Javadoc 内容

Given 公开方法签名为 `save(User user)`，Javadoc 含 `@param account 账户`，且无 `@return`、`@author`
When 执行 PMD 检查
Then 不产生违规：标签与签名一致性、缺少 `@param`/`@return`/`@author` 均不在检查范围内（D-109 接受的缺口）。

### 场景：格式问题不由 PMD 报告

Given 一个文件有 160 字符长行、制表符缩进和未使用的 import
When 执行 PMD 检查
Then PMD 不报告这三类问题
And 启用 Spotless 检查时由 Spotless 报告格式问题。

## 行为 CQ-02：坏味道检查

### 场景：空 catch 块

Given 下游 main 源码中有 `catch (IOException e) {}`，未设置新属性
When 执行 `./mvnw -Pprecheck verify`
Then 构建成功，控制台与 `target/mimir-quality/pmd.xml` 均包含 `EmptyCatchBlock` 违规。

### 场景：不报告未选用的规则

Given 源码存在未使用的 import、超过 6 行的普通注释，以及返回 `int` 的 `isEmptyCount()` 方法
When 执行 PMD 检查
Then PMD 不报告这三类问题（`UnnecessaryImport`、`CommentSize`、`LinguisticNaming` 未启用）。

### 场景：测试与生成源码不检查

Given `src/test/java` 中有空 catch 块，注解处理器生成的源码中有未使用的私有字段
When 执行 PMD 检查
Then 两处均不产生违规。

## 行为 CQ-03：重复代码检查

### 场景：超过阈值的重复

Given 两个 main 源文件含有长度不少于 100 个 token 的相同代码片段，未设置新属性
When 执行 `./mvnw -Pprecheck verify`
Then 构建成功，`target/mimir-quality/cpd.xml` 记录这一处重复及两个文件位置。

### 场景：低于阈值

Given 两个文件的相同片段为 60 个 token
When 执行 CPD 检查
Then 不报告重复。

### 场景：测试与生成源码重复

Given 两个测试类含 300 个 token 的相同片段；注解处理器生成的两个类也含 300 个 token 的相同片段
When 执行 CPD 检查
Then 两处均不报告重复。

## 行为 CQ-04：模式与独立开关

### 场景：各模式默认

Given 下游未设置新属性
When 分别以默认模式、`dev`、`precheck`、`ci`、`prod` 执行 `verify`
Then PMD、CPD 在默认模式、`dev`、`prod` 下均跳过，在 `precheck`、`ci` 下执行
And 所有模式下两类新检查的违规都不导致构建失败（Spotless、JaCoCo 按 CQ-06 保持原有行为）。

### 场景：单独开启或阻断

Given 下游在 `dev` 下设置 `-Dpmd.skip=false -Dpmd.failOnViolation=true`，源码含一处 PMD 违规与一处超过阈值的重复
When 执行 `verify`
Then 构建因 PMD 违规失败
And CPD 仍跳过，不参与本次结果。

### 场景：单独关闭

Given 下游在 `ci` 下设置 `-Dcpd.skip=true`
When 执行 `verify`
Then CPD 不执行，PMD、Spotless、JaCoCo 与 Enforcer 照常执行。

### 场景：设置覆盖顺序

Given 下游 POM 属性设置 `cpd.failOnViolation=true`，命令行传 `-Dcpd.failOnViolation=false`
When 以 `precheck` 执行且存在重复
Then 构建成功，命令行属性优先。

## 行为 CQ-05：本仓库严格执行

### 场景：违规阻断

Given 本仓库任一模块 main 源码新增一个缺少 Javadoc 的公开方法
When 执行 `./mvnw -Pci clean verify`
Then 构建失败并指出该文件与行号。

### 场景：完整验收纳入新检查

Given 本仓库无违规
When 执行 `bash scripts/engineering.sh quality --mode full --source worktree`
Then 质量报告中 PMD、CPD 两项均为 `passed` 并绑定同一次构建的报告文件
And 依赖方向测试作为各模块单元测试计入 Java 测试结果，失败、错误、跳过均为 0。

### 场景：门禁拒绝被放宽

Given 某 Starter 的 POM 把 `pmd.failOnViolation` 设为 `false`、把 `cpd.skip` 设为 `true`、设置 `pmd.maxAllowedViolations=10`，或删除了严格属性；或者在 `MAVEN_OPTS` 中注入 `-Dpmd.skip=true`
When 执行完整验收
Then 完整验收失败，Java 子检查报告指出模块与被放宽的参数或被拒绝的构建输入。

### 场景：日常默认构建不受影响

Given 本仓库开发者在执行过 `clean` 的工作区执行 `./mvnw clean install`
When 构建完成
Then 构建成功，各模块 `target/` 下没有 `pmd.xml`、`cpd.xml`，日志中没有两类工具的违规行。

## 行为 CQ-06：既有工具语义不变

### 场景：Spotless 与 JaCoCo 保持阻断

Given 下游在 `precheck` 下存在格式错误或覆盖率低于 60%
When 执行 `verify`
Then 构建与引入前一样失败。

### 场景：同一问题只有一个工具报告

Given 一个文件只有一处未使用的 import
When 以 `precheck` 执行
Then 只有 Spotless 报告，PMD 报告中没有该问题。

### 场景：既有开关与模式

Given 下游使用 `spotless.check.skip`、`jacoco.skip` 及阈值属性的既有组合
When 升级到本版本 Parent
Then 这些属性的名称、默认值和效果不变。

## 行为 CQ-07：依赖方向规则库

### 场景：COLA 分层违规

Given 下游根包为 `com.acme.order`，`domain` 包中一个类引用 `app` 包中的类；测试类路径含 starter-test 与 ArchUnit
When 运行以 COLA 预设编写的架构测试
Then 测试失败，失败信息列出该引用的源类与目标类。

### 场景：可选层缺失

Given 下游项目只有 `adapter`、`app`、`domain` 三层，没有 `client`、`infrastructure`
When 运行 COLA 预设测试
Then 测试通过。

### 场景：多模块 COLA 工程

Given 下游把 `adapter`、`app`、`domain`、`infrastructure` 拆成四个 Maven 模块，架构测试位于依赖全部四个模块的 `start` 模块，`domain` 模块中一个类引用 `app` 包
When 分别在 reactor 中执行 `test` 与 `verify`（兄弟模块分别以目录和 jar 出现在类路径上）
Then 两种方式下测试都失败并列出同一违规。

### 场景：导入结果为空

Given 下游根包写错为 `com.acme.ordr`，类路径上没有该包的类；或根包下只存在一个 COLA 层
When 运行 COLA 预设测试
Then 测试失败，信息说明没有导入到类或可识别的层少于 2 个，不会空跑通过。

### 场景：自定义分层与包循环

Given 下游以 `controller`、`service`、`repository` 自定义三层并声明 `controller→service→repository`，`repository` 引用 `controller`；另有两个子包相互引用
When 分别运行自定义分层规则与包循环规则
Then 两条规则均失败并列出违规。

### 场景：规则参数错误

Given 下游以空白根包创建 COLA 预设，或自定义分层只声明一层、或两层同名
When 创建规则
Then 立即抛出 `IllegalArgumentException`，信息指出错误参数，不生成可运行的规则。

### 场景：未引入 ArchUnit

Given 下游依赖 starter-test 但未声明 ArchUnit，也不使用规则库
When 执行测试
Then 测试类路径中没有 ArchUnit，既有测试行为不变。

### 场景：存量违规冻结

Given 已有项目存在 20 处分层违规，用 ArchUnit 原生冻结机制包装规则，并在测试资源 `archunit.properties` 中设置 `freeze.store.default.allowStoreCreation=true`
When 首次运行后新增一处违规
Then 首次通过并记录 20 处基线，新增后失败且只报告新增的一处。

## 行为 CQ-08：本仓库依赖方向

### 场景：Common 引用 Starter 或 Spring

Given `mimir-boot-common` 中一个类引用 `com.yggdrasil.labs.log` 包，或临时加入 Spring 依赖后引用 `org.springframework` 包
When 执行 Common 模块测试
Then 测试失败并指出该引用。

### 场景：未登记的 Starter 横向引用

Given `starter-log` 中一个类引用 `com.yggdrasil.labs.web` 包
When 执行该模块测试
Then 测试失败；`starter-web` 引用 `com.yggdrasil.labs.exception`、`starter-dubbo` 引用 `com.yggdrasil.labs.rpc.core` 不失败。

### 场景：同名包分属不同模块

Given `starter-mybatis` 与 `starter-mybatis-processor` 都有 `com.yggdrasil.labs.mybatis.annotation` 包
When 各自执行依赖方向测试
Then 每个模块只检查自身编译产物，不因对方的类误报。

## 行为 CQ-09：接入路径与发布

### 场景：继承发布 Parent 的独立消费者

Given 消费者 `relativePath` 为空，只从制品仓库解析发布后的 Parent、BOM 与规则 jar
When 以 `precheck` 执行 `verify`
Then 两类检查使用规则 jar 中的规则执行，规则 jar 版本等于 Parent 版本，而不是消费者自身版本。

### 场景：仅导入 BOM 的消费者

Given 消费者使用其他父 POM 并导入 BOM，自行声明 `maven-pmd-plugin` 3.28.0，插件依赖为 `io.github.yggdrasil-labs:mimir-boot-code-quality`（版本与导入的 BOM 相同），`rulesets` 为 `mimir-boot/pmd/ruleset.xml`，并设置 `failOnViolation=true`
When 对含一处缺少 Javadoc 的公开方法的源码执行 PMD 检查
Then 报告的违规与继承 Parent 时相同，构建失败。

### 场景：仅导入 BOM 时省略规则 jar 版本

Given 同上消费者，但规则 jar 插件依赖不写版本
When 执行构建
Then 构建失败并提示缺少版本（插件依赖不受 BOM 的 `dependencyManagement` 管理）。

### 场景：替换规则

Given 下游在自身 POM 中为 `mimir-boot-code-quality` 的 `pmd` execution 配置 `rulesets` 为 `config/pmd.xml`
When 以 `precheck` 执行 PMD 检查
Then 使用下游规则，Mimir 规则不生效。

### 场景：单独运行检查

Given 下游开启对应工具，且默认 `target/pmd.xml` / `target/cpd.xml` 留有自身官方插件报告
When 用独立插件的完整坐标调用 `check@pmd` 或 `cpd-check@cpd`
Then 只判定本次生成的 `target/mimir-quality/` 下对应报告；不把默认目录报告当作 Mimir 检查结果。

### 场景：覆盖规则制品版本

Given 下游需要覆盖 `mimir-boot-code-quality.version`
When 选择同一官方插件来源版本生成的规则制品
Then 可以使用其规则；跨来源版本的制品不属于独立覆盖的支持范围，须同时升级 Parent 与规则制品。

### 场景：规则 jar 缺失

Given 消费者仓库中无法解析规则 jar
When 以任意模式执行到 `verify` 及以后的阶段
Then 构建失败并指出缺失的规则 jar 坐标，不静默跳过检查。

### 场景：只配置了依赖仓库

Given 消费者只在 `<repositories>` 中声明可访问 Mimir 制品的私有仓库，没有镜像与 `<pluginRepositories>`
When 执行 `verify`
Then 构建失败并指出规则 jar 坐标；补充同源的 `<pluginRepositories>` 后构建成功。

## 行为 CQ-10：已自行配置同名插件的下游升级

本行为是用户确认的向后兼容显式例外（D-108），需要在升级说明中列出，不能静默生效。Mimir 通过独立插件坐标运行，下游官方插件的规则、范围与参数不合并；两套插件共享原生开关属性，且构建新增解析独立规则制品的要求。

### 场景：依赖插件原生默认值的既有 PMD/CPD 检查

Given 下游升级前已在自身 `build/plugins` 声明 `maven-pmd-plugin` 的 `check` 与 `cpd-check` execution，使用插件默认规则集与默认 `minimumTokens`，未设置 `skip`、`failOnViolation`，依赖插件原生默认值（执行且失败）
When 升级到本版本 Parent 后以默认模式执行 `verify`
Then 两个 execution 被 Parent 的 `pmd.skip=true`、`cpd.skip=true` 跳过
And 其规则集、`minimumTokens` 等其余有效配置与升级前相同。

### 场景：恢复升级前行为

Given 同上下游，在 POM 中设置 `pmd.skip=false`、`cpd.skip=false`、`pmd.failOnViolation=true`、`cpd.failOnViolation=true`，并把独立 `mimir-boot-code-quality` 插件的 `pmd`、`cpd` execution 的 `phase` 设为 `none`
When 以默认模式执行 `verify`，源码含一处违反下游自身规则的问题
Then 只有下游 execution 运行，并且构建失败，与升级前一致
And 若只设置属性、不关闭 Mimir execution，Mimir execution 也会执行，并按严格模式报告 Mimir 规则的违规。Parent README 与版本升级说明须给出完整配置。

### 场景：既有 Checkstyle 检查不受影响

Given 下游升级前已自行配置 `maven-checkstyle-plugin`
When 升级到本版本 Parent
Then 其开关、引擎版本与执行结果与升级前相同（Parent 不声明 Checkstyle，D-109）。

## Constraints

- 运行时基线：全部检查在 JDK 17 上可运行；本规格不要求更高构建 JDK。
- 下游默认模式不执行两类新检查；但 PMD 插件在 `build/plugins` 中声明并绑定 `verify`，执行到该阶段的任何构建（含 `install`、`deploy`）都需要能从插件仓库（镜像或 `<pluginRepositories>`）解析 PMD 插件与规则 jar。内网镜像未同步时，日常 `install` 也会失败。
- 本仓库 `./mvnw -Pci clean verify` 的耗时增幅目标 ≤ 20%（推荐值，以实施前后同机测量为准）。
- 报告路径固定为模块 `target/mimir-quality/pmd.xml`、`target/mimir-quality/cpd.xml`。
- 新规则 jar 不含 Java 类，不向下游运行时传递任何依赖。
