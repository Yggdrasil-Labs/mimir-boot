# Mimir Boot Parent

Mimir Boot 的父 POM 模块，提供统一的 Maven 插件版本管理、构建配置和代码质量工具。

## 📋 功能特性

- 🔧 **统一插件版本管理**：集中管理所有 Maven 插件的版本，确保构建一致性
- 🏗️ **标准化构建配置**：提供统一的编译器、测试、打包等配置
- 📦 **依赖版本管理**：通过引入 `mimir-boot-bom` 统一管理依赖版本
- ✅ **代码质量保证**：集成 JaCoCo 代码覆盖率、Spotless 代码格式化、PMD/CPD 静态检查、Maven Enforcer 依赖约束
- 🎯 **多环境支持**：提供 dev、precheck、ci、prod 等 Maven profiles，支持不同场景的构建需求
- 🎛️ **独立开关**：测试、覆盖率、格式、PMD/CPD、签名、部署均使用 Maven/插件原生属性，彼此不联动
- 📝 **代码格式化**：基于 Google Java Format 的自动代码格式化

## 🚀 快速开始

### 1. 继承 Parent POM

在您的项目 `pom.xml` 中继承 `mimir-boot-parent`：

```xml
<parent>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-parent</artifactId>
    <version>YOUR_RELEASE_VERSION</version>
</parent>
```

### 2. 独立使用 BOM（不继承 Parent 时）

继承 Parent 后已自动引入 BOM，无需重复配置。若项目使用其他父 POM，仅需依赖版本管理，可在 `dependencyManagement` 中单独引入 BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.yggdrasil-labs</groupId>
            <artifactId>mimir-boot-bom</artifactId>
            <version>YOUR_RELEASE_VERSION</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

只导入 BOM 时不会获得下文的 JDK 适配 profile；在 JDK 23 及以上构建且依赖 Lombok、MapStruct 等注解处理器时，需自行设置 `maven.compiler.proc=full`。

## 🔧 配置说明

### 编译目标

- **唯一入口**：`java.version`，默认 `17`，对应 Compiler 的 `release` 参数
- **编码**：UTF-8；保留 `parameters=true`，反射可读取方法参数名

构建 JDK 不会自动提高目标版本：在 JDK 21 上默认仍产出 Java 17 字节码（class major 61）。需要更高目标时覆盖 `java.version`：

```xml
<properties>
    <java.version>21</java.version>
</properties>
```

或命令行 `-Djava.version=21`。目标高于构建 JDK、或代码调用目标版本没有的 API 时，编译直接失败，不会降级。

迁移注意：

- Parent 写入的是字面配置 `<release>${java.version}</release>`，消费者设置 `maven.compiler.release`、`maven.compiler.source/target` 不会改变目标，须改用 `java.version`。
- 若自行覆盖 Compiler 的 `source/target/release` 字面配置，需自行管理完整的 Compiler 配置，Parent 属性不保证能覆盖消费者字面量。

### 注解处理器（JDK 适配）

Parent 内置 `jdk-annotation-processing` profile：Maven 运行在 JDK 21 及以上时自动激活，只设置 `maven.compiler.proc=full`，继续使用 classpath 发现处理器，不声明 `annotationProcessorPaths`。JDK 17 至 20 不传入该参数。

- 关闭：`-Dmaven.compiler.proc=none`、同名属性或 Compiler `<proc>none</proc>`；代码依赖生成类时会因缺类而编译失败，Parent 不会重新开启。
- 该 profile 依据运行 Maven 的 JVM 判断；使用 toolchain 或 fork 让 javac 与 Maven JDK 不一致时需自行配置并验证。
- 该 profile 不改变任何测试、覆盖率或格式默认值。

### 已配置的 Maven 插件

插件版本以 [Parent POM](./pom.xml) 为准，以下仅列出名称与用途。

#### 核心插件

- **maven-compiler-plugin**：Java 编译插件，支持参数名称保留
- **maven-surefire-plugin**：单元测试插件
- **maven-failsafe-plugin**：集成测试插件
- **maven-clean-plugin / maven-resources-plugin / maven-install-plugin**：仅管理版本，不新增执行
- **spring-boot-maven-plugin**：Spring Boot 打包插件

#### 代码质量插件

- **jacoco-maven-plugin**：代码覆盖率检查
  - 指令覆盖率要求：≥ 60%
  - 分支覆盖率要求：≥ 50%
  - 排除实体类、DTO、VO 和主应用类等

- **spotless-maven-plugin**：代码格式化
  - 使用 Google Java Format
  - 自动移除未使用的导入
  - 自动去除行尾空格
  - 文件末尾自动换行

- **mimir-boot-code-quality**：独立的 PMD/CPD 入口，复用官方 Maven PMD Plugin 3.28.0
  - 在 `verify` 检查 main 源码，规则来自同名资源 jar
  - PMD 检查 Javadoc、命名与常见坏味道，CPD 检查重复代码
  - 报告位于 `target/mimir-quality/`，与下游自己的官方插件隔离

- **maven-enforcer-plugin**：依赖约束检查
  - 要求 Java 版本 ≥ 17
  - 要求 Maven 版本 ≥ 3.8.0
  - 禁止使用 `commons-logging` 和 `log4j`

#### 发布相关插件

- **maven-source-plugin / maven-javadoc-plugin**：`package` 阶段生成源码包与 Javadoc 包
- **maven-gpg-plugin**：可选签名，默认跳过
- **maven-deploy-plugin**：标准部署，默认跳过

#### 版本管理插件

- **flatten-maven-plugin**：POM 扁平化，支持 `${revision}` 版本占位符
- **versions-maven-plugin**：版本管理工具

### 插件版本覆盖

Clean、Resources、Install 插件版本已接入 Parent 管理（3.4.0、3.3.1、3.1.1）。这会替换 Maven 自带的默认版本（例如 Maven 3.10 自带 Resources 3.5.0），属于可观察的默认值变化。需要其他版本时覆盖同名属性：

```xml
<properties>
    <maven.clean.plugin.version>3.4.0</maven.clean.plugin.version>
    <maven.resources.plugin.version>3.3.1</maven.resources.plugin.version>
    <maven.install.plugin.version>3.1.1</maven.install.plugin.version>
</properties>
```

版本不存在或与环境不兼容时，按插件解析或执行失败处理。

### 测试配置

#### 单元测试（Surefire）

- 测试类命名模式：`**/*Test.java`、`**/*Tests.java`
- 排除集成测试：`**/*IT.java`、`**/*IntegrationTest.java`

#### 集成测试（Failsafe）

- 测试类命名模式：`**/*IT.java`、`**/*IntegrationTest.java`
- 在 `integration-test` 阶段执行测试，在 `verify` 阶段检查结果

## 🎛️ 构建开关

Parent 在公共属性中声明以下默认值，不依赖 `dev` profile 是否激活。各开关彼此独立，不存在“关闭测试即关闭覆盖率”之类的联动。

| 属性 | 默认 | 作用 |
| --- | --- | --- |
| `java.version` | `17` | 编译目标 |
| `skipTests` | `false` | 仅跳过 Surefire 单元测试执行 |
| `skipITs` | `false` | 仅跳过 Failsafe 集成测试执行与结果验证 |
| `maven.test.skip` | `false` | 跳过测试编译及两类测试执行 |
| `jacoco.skip` | `true` | 覆盖率采集、报告与阈值检查 |
| `spotless.check.skip` | `true` | Spotless 默认 `check`；不影响手动 `spotless:apply` |
| `pmd.skip` / `cpd.skip` | `true` | 分别跳过 PMD / CPD，原生属性也作用于已有官方插件 |
| `pmd.failOnViolation` / `cpd.failOnViolation` | `false` | 分别因 PMD / CPD 违规阻断 |
| `mimir-boot-code-quality.version` | Parent 实际版本 | 只支持覆盖为同一官方来源版本的规则制品 |
| `gpg.skip` | `true` | GPG 签名 |
| `maven.deploy.skip` | `true` | 标准 Deploy 插件 |
| `maven.source.skip` / `maven.javadoc.skip` | 插件默认 `false` | 源码包 / Javadoc 包，分别独立 |

常用组合：

```bash
# 只跑集成测试：-DskipTests 不会跳过 IT
mvn clean verify -DskipTests

# 只跑单元测试
mvn clean verify -DskipITs

# 两类测试都不执行（仍编译测试代码）
mvn clean verify -DskipTests -DskipITs

# 连测试编译一起跳过
mvn clean verify -Dmaven.test.skip=true
```

`-DskipTests` 不跳过 IT 依赖 Parent 默认的 Failsafe 3.6.0；自行降级或改写 `maven.failsafe.plugin.version` 可能改变该边界，需要自行验证。

## 📦 Maven Profiles

本项目提供 4 个构建模式 profile，它们只预设下表五个属性（`ci` 另外启用 Enforcer），签名与部署在所有模式下均默认跳过。所有 profiles 都会自动继承到基于 `mimir-boot-parent` 的子项目中。

| 模式 | `maven.test.skip` | `jacoco.skip` | `spotless.check.skip` | `pmd.skip` / `cpd.skip` | 单测/集测 | Enforcer |
| --- | --- | --- | --- | --- | --- | --- |
| 未指定或 `dev` | false | true | true | true | 执行 / 执行 | 无 |
| `precheck` | false | false | false | false | 执行 / 执行 | 无 |
| `ci` | false | false | false | false | 执行 / 执行 | 执行 |
| `prod` | true | true | false | true | 跳过编译及执行 | 无 |

`dev` 保留 `activeByDefault`；当其他 profile（包括 JDK 适配 profile）使其失活时，公共属性提供等价默认值。

### 1. dev（默认）

**用途**：日常开发，快速构建

```bash
mvn clean install
# 或显式指定
mvn clean install -Pdev
```

- ✅ 执行单元测试与集成测试
- ❌ 不检查代码覆盖率与格式
- 🚀 适合日常开发迭代

### 2. precheck

**用途**：提交代码前的本地检查

```bash
mvn clean verify -Pprecheck
```

- ✅ 执行单元测试与集成测试
- ✅ 检查代码覆盖率（指令覆盖率 ≥ 60%，分支覆盖率 ≥ 50%）
- ✅ 执行 Spotless 格式检查

### 3. ci

**用途**：CI/CD 流水线严格门禁

```bash
mvn clean verify -Pci
```

- ✅ precheck 的全部检查
- ✅ 检查依赖约束（Maven Enforcer）
- ❌ 不签名
- 任何检查失败（格式错误、测试失败、覆盖率不达标、Enforcer 拒绝）都会以非零退出码失败

### 4. prod

**用途**：生产环境打包与发布准备

```bash
mvn clean deploy -Pprod -Dmaven.deploy.skip=false
```

- ❌ 跳过测试编译与执行（`maven.test.skip=true`）
- ❌ 不检查代码覆盖率
- ✅ 保留 Spotless 格式检查
- 📦 默认仍跳过部署和签名；需要显式开启

prod 下重新开启测试和覆盖率需要同时覆盖两个属性，单独传 `-DskipTests=false` 不能抵消 `maven.test.skip=true`：

```bash
mvn clean verify -Pprod -Dmaven.test.skip=false -Djacoco.skip=false
```

### 组合使用

模式之间不互斥，可以同时激活。同一属性冲突时，按 Parent 中的声明顺序 `dev → precheck → ci → prod` 后者覆盖前者；交换 `-P` 的书写顺序不会改变结果。命令行 `-D` 属性优先于所有 profile 预设。

| 组合 | 测试 / 覆盖率 / 格式 | 附加行为 |
| --- | --- | --- |
| `dev,precheck` | 执行 / 开启 / 开启 | 无 Enforcer |
| `dev,ci` 或 `precheck,ci` | 执行 / 开启 / 开启 | 执行 Enforcer |
| `dev,prod` 或 `precheck,prod` | 跳过 / 跳过 / 开启 | 不自动部署 |
| `ci,prod` | 跳过 / 跳过 / 开启 | 执行 Enforcer |

任一模式在 dev 或 prod 下也可以单独打开某项能力，例如 `-Pdev -Djacoco.skip=false`、`-Pdev -Dspotless.check.skip=false`。

### 子项目继承

所有基于 `mimir-boot-parent` 的项目都会自动继承这些 profiles，无需额外配置：

```bash
# 在子项目中使用
mvn clean install -Pprecheck
mvn clean verify -Pci
mvn clean deploy -Pprod -Dmaven.deploy.skip=false
```

## 📊 代码覆盖率

JaCoCo 在所有项目中都已绑定生命周期，是否执行由 `jacoco.skip` 决定：`precheck`、`ci` 默认开启，未指定模式、`dev`、`prod` 默认跳过，也可以用 `-Djacoco.skip=false` 单独开启。

### 覆盖率报告

`prepare-agent` 为单元测试和集成测试注入采集代理，数据追加写入同一个 `target/jacoco.exec`；`verify` 阶段汇总生成报告并检查阈值，因此报告同时包含单元测试与集成测试：

- **报告位置**：`target/site/jacoco/index.html`
- **XML 报告**：`target/site/jacoco/jacoco.xml`（用于 CI 集成）

没有执行任何测试时，JaCoCo 会因缺少数据文件而跳过报告与检查，这表示“未检查”，不等于覆盖率达标。

若自行覆盖 Surefire/Failsafe 的 `argLine`，需保留 JaCoCo 注入的属性（例如 `@{argLine} 自定义参数`），否则不会采集数据。

### 覆盖率要求

- **指令覆盖率**：≥ 60%（`jacoco.instruction.minimum`）
- **分支覆盖率**：≥ 50%（`jacoco.branch.minimum`）

如果覆盖率不达标，构建会失败。

### 排除规则

以下类型的类不计入覆盖率统计：

- `**/entity/**`：实体类
- `**/dto/**`：DTO 类
- `**/vo/**`：VO 类
- `**/Application.class`：主应用类

### 查看覆盖率报告

```bash
# 使用 precheck profile 生成覆盖率报告
mvn clean verify -Pprecheck

# 查看 HTML 报告
open target/site/jacoco/index.html
```

## 🎨 代码格式化

项目使用 Spotless 插件进行代码格式化，基于 Google Java Format。

### 格式化检查

Parent 只保留一个默认检查执行 `spotless-check`，绑定在 `validate` 阶段，每个模块调度一次；是否实际检查由 `spotless.check.skip` 决定。`precheck`、`ci`、`prod` 默认执行检查，未指定模式和 `dev` 默认跳过。

**迁移说明**：旧版本 `ci` profile 中另有一个 ID 为 `check` 的重复执行，现已移除。若您曾在项目中覆写 `check` 执行，请改为覆写 `spotless-check`；保留旧 `check` 声明时，Maven 会把它视为您自定义的额外执行，从而每次构建多检查一次。

### 格式化规则

- **代码风格**：Google Java Format 的 AOSP 风格
- **缩进**：4 个空格（不使用 Tab）
- **导入**：自动移除未使用的导入
- **空白**：自动去除行尾空格
- **换行**：文件末尾自动换行

### 格式化命令

```bash
# 检查代码格式（不修改文件）
mvn -Pci spotless:check

# 自动格式化代码（修改文件）
mvn spotless:apply

# 在 CI profile 中，格式化检查会自动执行
mvn -Pci clean verify
```

### IDE 集成

建议在 IDE 中安装 Google Java Format 插件，选择 AOSP 风格并配置为保存时自动格式化，与 POM 的格式设置保持一致。

## PMD/CPD 检查与升级

默认模式跳过检查。`precheck` 和 `ci` 执行并逐条输出违规，默认只报告；本仓库 Java 模块另外启用严格属性。下游可以分别开启阻断或关闭检查：

```bash
mvn -Pprecheck verify
mvn -Pprecheck verify -Dpmd.failOnViolation=true -Dcpd.failOnViolation=true -Dpmd.skipPmdError=false
mvn -Pci verify -Dcpd.skip=true

# 直接调用必须选中对应 execution，使分叉目标取得规则与报告配置
mvn io.github.yggdrasil-labs:mimir-boot-code-quality:YOUR_RELEASE_VERSION:check@pmd -Dpmd.skip=false
mvn io.github.yggdrasil-labs:mimir-boot-code-quality:YOUR_RELEASE_VERSION:cpd-check@cpd -Dcpd.skip=false
```

规则、替换示例及仅导入 BOM 的完整配置见 [规则模块](../mimir-boot-code-quality/README.md)。继承 Parent 时，替换规则须覆盖 `io.github.yggdrasil-labs:mimir-boot-code-quality` 的 `pmd` execution，不能覆盖已有官方插件来修改 Mimir 规则。两类 XML 报告位于 `target/mimir-quality/pmd.xml`、`target/mimir-quality/cpd.xml`；HTML、缓存、基准及规则副本也在该目录下。

插件及实现依赖从插件仓库解析。内网镜像需同步规则制品、`org.apache.maven.plugins:maven-pmd-plugin:3.28.0` 与传递依赖；未配置镜像时，除普通 `<repositories>` 外还需配置相同来源的 `<pluginRepositories>`。规则制品版本只支持同官方描述符来源版本的覆盖；跨来源必须成套升级 Parent，不能单独替换官方实现或 PMD 依赖。

### 已自行配置 PMD 的升级

Mimir 入口使用独立插件坐标和类加载器。下游已有 `maven-pmd-plugin` execution 的规则、范围和参数继续来自下游配置；两套插件共用 `pmd.*`、`cpd.*` 等原生用户属性，因此原生属性仍会同时影响它们：

- 新增默认 `pmd.skip=true`、`cpd.skip=true`，可能使原先未设置 skip 的官方检查跳过；需要保留原检查时，在项目属性设为 `false`。
- 新增默认 `pmd.failOnViolation=false`、`cpd.failOnViolation=false`，可能使原先依赖官方默认阻断的检查只报告；需要恢复时，在项目属性设为 `true`。
- `precheck`、`ci` 预设两个 skip 为 `false`，`dev`、`prod` 为 `true`；命令行 `-D` 优先于这些属性。若下游在插件配置中写了字面开关，须自行核实其优先级。
- 官方插件依赖位于独立 Mimir 插件中，不与下游官方插件的依赖配置合并。两套报告应使用不同目录；下游主动指定同一目录时仍会互相覆盖。

只保留下游原检查时，关闭 Mimir 的两个 execution，再恢复需要的原生开关：

```xml
<properties>
    <pmd.skip>false</pmd.skip>
    <cpd.skip>false</cpd.skip>
    <pmd.failOnViolation>true</pmd.failOnViolation>
    <cpd.failOnViolation>true</cpd.failOnViolation>
</properties>
<build>
    <plugins>
        <plugin>
            <groupId>io.github.yggdrasil-labs</groupId>
            <artifactId>mimir-boot-code-quality</artifactId>
            <executions>
                <execution><id>pmd</id><phase>none</phase></execution>
                <execution><id>cpd</id><phase>none</phase></execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

此配置不关闭下游已有官方插件。若同时使用模式，可用 `-Dpmd.skip=false -Dcpd.skip=false` 明确开启原检查。

## 🔒 依赖约束

Maven Enforcer 插件用于检查依赖和构建环境约束。

### 启用依赖约束检查

依赖约束检查在以下 profiles 中启用：

- **ci**：CI 流水线中自动检查依赖约束

在 **dev**、**precheck** 和 **prod** profiles 中，依赖约束检查被跳过。

### 检查规则

- **Java 版本**：必须 ≥ 17
- **Maven 版本**：必须 ≥ 3.8.0
- **禁止依赖**：
  - `commons-logging:commons-logging`（应使用 SLF4J）
  - `log4j:log4j`（应使用 Logback）

如果检查失败，构建会失败。

### 手动检查

```bash
# 在 CI profile 中，依赖约束检查会自动执行
mvn clean verify -Pci

# 也可以手动执行
mvn enforcer:enforce
```

## ✍️ 签名（可选）

GPG 签名默认跳过，构建不需要本机私钥，任何构建模式都不会自动开启。需要签名时显式开启：

```bash
mvn clean verify -Dgpg.skip=false
```

签名绑定在 `verify` 阶段（执行 ID `sign-artifacts`），对主构件、POM 及源码/Javadoc 附件各签一次；使用非交互参数（`--batch --pinentry-mode loopback`），口令由受控环境传入，不要写入 POM 或仓库。显式开启后签名失败会阻断构建，不会静默回退。

本仓库自身发布到 Maven Central 的签名参数见[发布规程](../docs/engineering/release.md)。

## 🚚 附件与部署仓库

### 源码与 Javadoc 附件

默认在 `package` 阶段同时生成 `-sources.jar` 和 `-javadoc.jar`，可分别关闭：

```bash
mvn clean package -Dmaven.source.skip=true
mvn clean package -Dmaven.javadoc.skip=true
```

### 部署到自有仓库

标准部署默认跳过（`maven.deploy.skip=true`）。Parent 自带的 `distributionManagement` 指向 Maven Central Portal 的发布接口，供本仓库发布使用；Portal 上传由专用的 Central Publishing 扩展接管，与标准 Deploy 插件的 Maven 仓库协议不同，不能把下面的私服命令当作 Central 发布命令。

部署到自有仓库时，覆写自己的 `distributionManagement`，或使用 Deploy 插件原生属性分别指定正式与快照仓库：

```bash
mvn clean deploy -Dmaven.deploy.skip=false \
  -DaltReleaseDeploymentRepository=my-releases::https://repo.example.com/releases \
  -DaltSnapshotDeploymentRepository=my-snapshots::https://repo.example.com/snapshots
```

仓库 ID 对应 Maven `settings.xml` 中的 `<server>`，凭据保留在 settings 或 CI 密钥中。以上路由已在本地 `file://` 仓库验证；真实私服的协议与认证未在本仓库验证，请以实际仓库为准。

## 📝 版本管理

### 使用 `${revision}` 占位符

项目使用 `flatten-maven-plugin` 支持 `${revision}` 版本占位符，便于统一管理版本号。发布 Parent 时，flatten 会解析 Parent 版本与依赖管理，同时以 `extended_interpolate` 固定属性中的 `${project.version}` 引用，保留其余字面属性、`build`、`pluginManagement` 和全部 profiles（含 JDK 激活条件）。上述开关和插件版本属性在下游构建时才解析，发布时使用的生产者参数（如 `-Dgpg.skip=false`、`-Dmaven.test.skip=true`）不会写入下游默认值。新增引用型属性也可能在生产者侧固定，须在发布验证中核对。`mimir-boot-code-quality.version` 固定为发布 Parent 的版本；其覆盖仅限同一官方描述符来源版本。官方 PMD 实现版本固定为 `3.28.0`，`maven.pmd.plugin.version` 只记录配对版本，不是独立升级入口。跨来源升级须升级 Parent 和整套质量组件。下游项目可在自己的 `<properties>` 中覆盖 `java.version`、其他插件版本属性、`jacoco.instruction.minimum` 和 `jacoco.branch.minimum` 以及上文各开关。

发布根 POM 不保留属性，因此 Parent 单独声明 `spring.boot.version` 供 Spring Boot Maven Plugin 继承；升级 Spring Boot 基线时，需要同步修改根 POM 和 Parent 中的该属性。

在根 POM 中定义：

```xml
<properties>
    <revision>1.0.0</revision>
</properties>
```

子模块会自动继承并使用该版本。

### 版本更新

使用 `versions-maven-plugin` 可以检查和更新依赖版本：

```bash
# 检查可更新的依赖
mvn versions:display-dependency-updates

# 检查可更新的插件
mvn versions:display-plugin-updates
```

## 🏗️ 构建流程

标准的 Maven 构建流程：

```bash
# 清理
mvn clean

# 编译
mvn compile

# 运行单元测试
mvn test

# 打包
mvn package

# 安装到本地仓库
mvn install

# 部署到远程仓库（需显式开启部署）
mvn deploy -Dmaven.deploy.skip=false

# 验证（包括集成测试）
mvn verify
```

## 📚 相关文档

- [Maven 官方文档](https://maven.apache.org/guides/)
- [Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/html/)
- [JaCoCo 文档](https://www.jacoco.org/jacoco/trunk/doc/)
- [Spotless 文档](https://github.com/diffplug/spotless)
- [Google Java Format](https://github.com/google/google-java-format)
- [发布规程](../docs/engineering/release.md)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 Apache License 2.0 许可证。
