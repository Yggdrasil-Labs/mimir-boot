# Mimir Boot Parent

Mimir Boot 的父 POM 模块，提供统一的 Maven 插件版本管理、构建配置和代码质量工具。

## 📋 功能特性

- 🔧 **统一插件版本管理**：集中管理所有 Maven 插件的版本，确保构建一致性
- 🏗️ **标准化构建配置**：提供统一的编译器、测试、打包等配置
- 📦 **依赖版本管理**：通过引入 `mimir-boot-bom` 统一管理依赖版本
- ✅ **代码质量保证**：集成 JaCoCo 代码覆盖率、Spotless 代码格式化、Maven Enforcer 依赖约束
- 🎯 **多环境支持**：提供 dev、precheck、ci、prod 等 Maven profiles，支持不同场景的构建需求
- 📝 **代码格式化**：基于 Google Java Format 的自动代码格式化

## 🚀 快速开始

### 1. 继承 Parent POM

在您的项目 `pom.xml` 中继承 `mimir-boot-parent`：

```xml
<parent>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-parent</artifactId>
    <version>1.0.0</version>
</parent>
```

### 2. 引入 BOM（可选）

如果需要统一管理依赖版本，可以在 `dependencyManagement` 中引入 BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.yggdrasil.labs</groupId>
            <artifactId>mimir-boot-bom</artifactId>
            <version>${project.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 🔧 配置说明

### Java 版本

- **Java 版本**：17
- **编译目标**：Java 17
- **编码**：UTF-8

### 已配置的 Maven 插件

#### 核心插件

- **maven-compiler-plugin** (3.14.1)：Java 编译插件，支持参数名称保留
- **maven-surefire-plugin** (3.5.4)：单元测试插件
- **maven-failsafe-plugin** (3.5.4)：集成测试插件
- **spring-boot-maven-plugin** (3.3.13)：Spring Boot 打包插件

#### 代码质量插件

- **jacoco-maven-plugin** (0.8.14)：代码覆盖率检查
  - 指令覆盖率要求：≥ 80%
  - 分支覆盖率要求：≥ 50%
  - 排除配置类、实体类、DTO、VO 等
  
- **spotless-maven-plugin** (2.43.0)：代码格式化
  - 使用 Google Java Format 1.22.0
  - 自动移除未使用的导入
  - 自动去除行尾空格
  - 文件末尾自动换行

- **maven-enforcer-plugin** (3.6.2)：依赖约束检查
  - 要求 Java 版本 ≥ 17
  - 要求 Maven 版本 ≥ 3.8.0
  - 禁止使用 `commons-logging` 和 `log4j`

#### 版本管理插件

- **flatten-maven-plugin** (1.7.3)：POM 扁平化，支持 `${revision}` 版本占位符
- **versions-maven-plugin** (2.19.1)：版本管理工具

### 测试配置

#### 单元测试（Surefire）

- 测试类命名模式：`**/*Test.java`、`**/*Tests.java`
- 排除集成测试：`**/*IT.java`、`**/*IntegrationTest.java`

#### 集成测试（Failsafe）

- 测试类命名模式：`**/*IT.java`、`**/*IntegrationTest.java`
- 在 `verify` 阶段执行

## 📦 Maven Profiles

本项目提供了 4 个 Maven profiles，用于不同场景的构建需求。所有 profiles 都会自动继承到基于 `mimir-boot-parent` 的子项目中。

### 1. dev（默认）

**用途**：日常开发，快速构建

```bash
mvn clean install
# 或显式指定
mvn clean install -Pdev
```

**特性**：
- ✅ 执行单元测试
- ❌ 不检查代码覆盖率（快速构建）
- ❌ 不检查代码格式
- 🚀 适合日常开发迭代

**配置说明**：
- 默认激活（`activeByDefault: true`）
- 禁用 JaCoCo 覆盖率检查以加快构建速度
- 跳过 Spotless 格式检查

### 2. precheck

**用途**：提交代码前的本地检查

```bash
mvn clean verify -Pprecheck
```

**特性**：
- ✅ 执行单元测试
- ✅ 检查代码覆盖率（指令覆盖率 ≥ 80%，分支覆盖率 ≥ 50%）
- ❌ 不检查代码格式
- 🔍 适合提交前验证代码质量

**配置说明**：
- 启用 JaCoCo 覆盖率检查
- 如果覆盖率不达标，构建会失败

### 3. ci

**用途**：CI/CD 流水线严格门禁

```bash
mvn clean verify -Pci
```

**特性**：
- ✅ 执行单元测试
- ✅ 检查代码覆盖率（指令覆盖率 ≥ 80%，分支覆盖率 ≥ 50%）
- ✅ 检查代码格式（Spotless）
- ✅ 检查依赖约束（Maven Enforcer）
- 🛡️ 最严格的检查，确保代码质量

**配置说明**：
- 启用 JaCoCo 覆盖率检查
- 启用 Spotless 代码格式检查（在 `validate` 阶段）
- 启用 Maven Enforcer 依赖和规则检查
- 任何检查失败都会导致构建失败

### 4. prod

**用途**：生产环境发布

```bash
mvn clean deploy -Pprod
```

**特性**：
- ❌ 跳过测试（加快构建速度）
- ❌ 不检查代码覆盖率
- 📦 仅打包和部署
- 🚀 适合生产发布场景

**配置说明**：
- 跳过所有测试（`maven.test.skip=true`）
- 禁用 JaCoCo 覆盖率检查
- 专注于快速打包和部署

### 组合使用

可以同时激活多个 profiles：

```bash
# 生产发布（跳过测试）
mvn clean deploy -Pprod

# CI 检查（最严格）
mvn clean verify -Pci

# 提交前检查（测试 + 覆盖率）
mvn clean verify -Pprecheck
```

### 发布到 Maven Central

继承 `mimir-boot-parent` 的项目**默认不会**发布到 Maven Central（`maven-central` profile 未设 `activeByDefault`）。若需发布到 Maven Central，请在 `~/.m2/settings.xml` 中配置 Central 凭证（`<server id="central">`），并显式激活 profile。

**mimir-boot 本仓库**发布命令（在仓库根目录执行）：

- **正式版**（需 GPG 签名）：
  ```bash
  ./mvnw -P maven-central -Dmaven.deploy.skip=false -Dgpg.skip=false -Dgpg.passphrase=你的GPG密码 deploy
  ```
- **开发版 / SNAPSHOT**（可跳过 GPG，仅发到 Central Snapshots）：
  ```bash
  ./mvnw -P maven-central -Dmaven.deploy.skip=false -Dgpg.skip=true deploy
  ```

### 子项目继承

所有基于 `mimir-boot-parent` 的项目都会自动继承这些 profiles，无需额外配置：

```xml
<parent>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-parent</artifactId>
    <version>1.0.1-SNAPSHOT</version>
</parent>
```

子项目可以直接使用这些 profiles：

```bash
# 在子项目中使用
mvn clean install -Pprecheck
mvn clean verify -Pci
mvn clean deploy -Pprod
```

## 📊 代码覆盖率

JaCoCo 插件用于代码覆盖率检查，通过不同的 profiles 控制是否启用。

### 启用覆盖率检查

覆盖率检查在以下 profiles 中启用：
- **precheck**：提交前检查
- **ci**：CI 流水线检查

在 **dev** 和 **prod** profiles 中，覆盖率检查被禁用以加快构建速度。

### 覆盖率报告

启用覆盖率检查时，JaCoCo 会在 `test` 阶段生成覆盖率报告：
- **报告位置**：`target/site/jacoco/index.html`
- **XML 报告**：`target/site/jacoco/jacoco.xml`（用于 CI 集成）

### 覆盖率要求

- **指令覆盖率**：≥ 80%
- **分支覆盖率**：≥ 50%

如果覆盖率不达标，构建会失败。

### 排除规则

以下类型的类不计入覆盖率统计：

- `**/config/**`：配置类
- `**/entity/**`：实体类
- `**/dto/**`：DTO 类
- `**/vo/**`：VO 类
- `**/Application.class`：主应用类

### 查看覆盖率报告

```bash
# 使用 precheck profile 生成覆盖率报告
mvn clean test -Pprecheck

# 查看 HTML 报告
open target/site/jacoco/index.html
```

## 🎨 代码格式化

项目使用 Spotless 插件进行代码格式化，基于 Google Java Format。

### 格式化检查

格式化检查在以下 profiles 中启用：
- **ci**：CI 流水线中自动检查代码格式（在 `validate` 阶段）

在 **dev**、**precheck** 和 **prod** profiles 中，格式化检查被跳过。

### 格式化规则

- **代码风格**：Google Java Format 1.22.0
- **缩进**：4 个空格（不使用 Tab）
- **导入**：自动移除未使用的导入
- **空白**：自动去除行尾空格
- **换行**：文件末尾自动换行

### 格式化命令

```bash
# 检查代码格式（不修改文件）
mvn spotless:check

# 自动格式化代码（修改文件）
mvn spotless:apply

# 在 CI profile 中，格式化检查会自动执行
mvn clean verify -Pci
```

### IDE 集成

建议在 IDE 中安装 Google Java Format 插件，并配置为保存时自动格式化，避免在 CI 中格式检查失败。

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

## 📝 版本管理

### 使用 `${revision}` 占位符

项目使用 `flatten-maven-plugin` 支持 `${revision}` 版本占位符，便于统一管理版本号。

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

# 部署到远程仓库
mvn deploy

# 验证（包括集成测试）
mvn verify
```

## 📚 相关文档

- [Maven 官方文档](https://maven.apache.org/guides/)
- [Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/html/)
- [JaCoCo 文档](https://www.jacoco.org/jacoco/trunk/doc/)
- [Spotless 文档](https://github.com/diffplug/spotless)
- [Google Java Format](https://github.com/google/google-java-format)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

