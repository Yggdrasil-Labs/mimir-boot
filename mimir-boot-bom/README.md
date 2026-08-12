# Mimir Boot BOM

Mimir Boot 依赖版本统一管理（BOM - Bill of Materials），集中管理所有第三方依赖版本，确保依赖兼容性和版本一致性。

## 📋 概述

`mimir-boot-bom` 是 Maven BOM（Bill of Materials）模块，通过 `dependencyManagement` 统一管理项目中所有第三方依赖的版本。使用 BOM 可以：

- ✅ **统一版本管理**：所有依赖版本集中在一个地方管理
- ✅ **避免版本冲突**：确保依赖版本之间的兼容性
- ✅ **简化配置**：引入依赖时无需指定版本号
- ✅ **版本同步**：与 Spring Boot BOM 版本保持一致

## 🚀 快速开始

### 1. 继承 Parent POM

首先，在您的项目 `pom.xml` 中继承 `mimir-boot-parent`：

```xml
<parent>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-parent</artifactId>
    <version>YOUR_RELEASE_VERSION</version> <!-- 替换为实际已发布版本 -->
</parent>
```

### 2. 引入 BOM

在 `dependencyManagement` 中引入 `mimir-boot-bom`：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.yggdrasil-labs</groupId>
            <artifactId>mimir-boot-bom</artifactId>
            <version>YOUR_RELEASE_VERSION</version> <!-- 替换为实际已发布版本 -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 3. 引入依赖（无需版本号）

引入依赖时，无需指定版本号，版本由 BOM 统一管理：

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <!-- 版本由 BOM 管理，无需指定 -->
    </dependency>

    <!-- MyBatis-Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <!-- 版本由 BOM 管理，无需指定 -->
    </dependency>

    <!-- Hutool -->
    <dependency>
        <groupId>cn.hutool</groupId>
        <artifactId>hutool-all</artifactId>
        <!-- 版本由 BOM 管理，无需指定 -->
    </dependency>
</dependencies>
```

## 📦 支持等级

`mimir-boot-bom/pom.xml` 是版本与坐标的唯一事实源；本页不复制版本号。下表只枚举其
`dependencyManagement` 中显式声明的直接条目，不把导入 BOM 的传递管理项当作本 BOM 的直接条目。

- **已验证**：该坐标在本 Reactor 的模块 POM 中被直接消费，且 Java 17 的 `clean verify`
  覆盖了该消费模块。
- **仅管理**：该坐标仍由 BOM 声明，但当前 Reactor 没有对该坐标的直接消费，或没有对应运行验证。

### 已验证（17 项）

| 类别 | 坐标 | Reactor 直接消费者 |
|------|------|-------------------|
| 工具与持久化 | `cn.hutool:hutool-all` | `mimir-boot-starter-nacos` |
| 工具与持久化 | `com.alibaba.fastjson2:fastjson2` | `mimir-boot-starter-mybatis` |
| 工具与持久化 | `com.baomidou:mybatis-plus-jsqlparser` | `mimir-boot-starter-mybatis` |
| 工具与持久化 | `com.squareup:javapoet` | `mimir-boot-starter-mybatis-processor` |
| 测试 | `com.google.testing.compile:compile-testing` | `mimir-boot-starter-mybatis-processor` |
| 测试 | `com.google.truth:truth` | `mimir-boot-starter-mybatis-processor` |
| 测试 | `org.testcontainers:testcontainers` | `mimir-boot-starter-test` |
| 测试 | `org.testcontainers:junit-jupiter` | `mimir-boot-starter-test` |
| RPC | `org.apache.dubbo:dubbo-spring-boot-starter` | `mimir-boot-starter-dubbo` |
| 编译 | `org.projectlombok:lombok` | `mimir-boot-common`、多个 Starter |
| 日志 | `org.slf4j:jcl-over-slf4j` | `mimir-boot-starter-log` |
| 日志 | `org.slf4j:jul-to-slf4j` | `mimir-boot-starter-log` |
| 日志 | `org.slf4j:log4j-over-slf4j` | `mimir-boot-starter-log` |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-common` | 多个 Starter |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-starter-exception` | `mimir-boot-starter-web` |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-starter-rpc-core` | `mimir-boot-starter-dubbo`、`mimir-boot-starter-feign` |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-starter-test` | 所有具测试的 Starter |

### 仅管理（38 项）

| 类别 | 坐标 |
|------|------|
| 导入 BOM | `org.springframework.boot:spring-boot-dependencies` |
| 导入 BOM | `org.springframework.cloud:spring-cloud-dependencies` |
| 导入 BOM | `com.alibaba.cloud:spring-cloud-alibaba-dependencies` |
| 导入 BOM | `com.baomidou:mybatis-plus-bom` |
| RPC | `org.apache.dubbo:dubbo` |
| 工具与持久化 | `org.mapstruct:mapstruct` |
| 工具与持久化 | `org.mapstruct:mapstruct-processor` |
| 安全 | `io.jsonwebtoken:jjwt-api` |
| 安全 | `io.jsonwebtoken:jjwt-impl` |
| 安全 | `io.jsonwebtoken:jjwt-jackson` |
| 文档 | `org.springdoc:springdoc-openapi-starter-webmvc-ui` |
| 文档 | `com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter` |
| 缓存与消息 | `org.redisson:redisson-spring-boot-starter` |
| 缓存与消息 | `org.apache.rocketmq:rocketmq-spring-boot-starter` |
| 缓存与消息 | `com.alicp.jetcache:jetcache-starter-redis` |
| 缓存与消息 | `com.alicp.jetcache:jetcache-starter-caffeine` |
| 任务与搜索 | `com.xuxueli:xxl-job-core` |
| 任务与搜索 | `org.elasticsearch.client:elasticsearch-java` |
| 任务与搜索 | `org.mongodb:mongodb-driver-sync` |
| 工具 | `org.apache.commons:commons-lang3` |
| 工具 | `org.apache.commons:commons-collections4` |
| 工具 | `com.google.guava:guava` |
| 序列化与网络 | `com.esotericsoftware:kryo` |
| 序列化与网络 | `com.google.protobuf:protobuf-java` |
| 序列化与网络 | `com.squareup.okhttp3:okhttp` |
| 序列化与网络 | `com.squareup.retrofit2:retrofit` |
| 文件与协调 | `org.apache.poi:poi` |
| 文件与协调 | `com.itextpdf:itext-core` |
| 文件与协调 | `org.apache.curator:curator-framework` |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-starter-log` |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-starter-web` |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-starter-nacos` |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-starter-mybatis` |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-starter-mybatis-processor` |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-starter-dubbo` |
| Mimir 模块 | `io.github.yggdrasil-labs:mimir-boot-starter-feign` |
| 测试 | `org.testcontainers:mysql` |
| 测试 | `org.testcontainers:postgresql` |

> 重新判定支持等级时，需以所有显式 `dependencyManagement` 条目与实际模块 POM 的直接依赖
> 做集合比对；完整 Java 17 `clean verify` 仅证明已消费模块的当前验证覆盖，不把未被直接消费的
> 托管条目提升为“已验证”。

## 🔧 配置说明

### 版本继承

BOM 通过 `dependencyManagement` 管理版本，子项目继承父 POM 时自动生效：

```xml
<parent>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-parent</artifactId>
    <version>YOUR_RELEASE_VERSION</version> <!-- 替换为实际已发布版本 -->
</parent>

<!-- Parent POM 已自动引入 BOM，无需额外配置 -->
```

### 版本覆盖（不推荐）

如果需要覆盖某个依赖的版本，可以在项目的 `pom.xml` 中显式指定：

```xml
<dependencyManagement>
    <dependencies>
        <!-- 覆盖 Hutool 版本（不推荐） -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>5.9.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**注意**：覆盖版本可能导致依赖冲突，建议使用 BOM 管理的版本。

## 💡 最佳实践

### 1. 统一使用 BOM 管理版本

✅ **推荐**：通过 BOM 管理所有依赖版本

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.yggdrasil-labs</groupId>
            <artifactId>mimir-boot-bom</artifactId>
            <version>YOUR_RELEASE_VERSION</version> <!-- 替换为实际已发布版本 -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

❌ **不推荐**：在依赖中显式指定版本

```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
    <version>5.8.41</version>  <!-- 不推荐：版本应由 BOM 管理 -->
</dependency>
```

### 2. 继承 Parent POM

继承 `mimir-boot-parent` 后，BOM 会自动引入，无需手动配置：

```xml
<parent>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-parent</artifactId>
    <version>YOUR_RELEASE_VERSION</version> <!-- 替换为实际已发布版本 -->
</parent>
```

### 3. 查看依赖版本

使用 Maven 命令查看依赖版本：

```bash
# 查看依赖树
mvn dependency:tree

# 查看依赖版本信息
mvn dependency:resolve

# 查看可更新的依赖
mvn versions:display-dependency-updates
```

## 📚 相关文档

- [Mimir Boot Parent](../mimir-boot-parent/README.md) - 父 POM 说明
- [Mimir Boot Common](../mimir-boot-common/README.md) - 公共组件说明
- [项目根目录 README](../README.md) - 项目总体说明

## 🔍 查看完整依赖列表

查看 `pom.xml` 文件获取完整的依赖版本列表：

```bash
cat mimir-boot-bom/pom.xml
```

或访问 [mimir-boot-bom/pom.xml](../mimir-boot-bom/pom.xml) 查看源码。

## 📝 版本更新

BOM 的版本与 Mimir Boot 主版本保持一致。当需要更新依赖版本时：

1. 修改 `mimir-boot-bom/pom.xml` 中的版本属性
2. 运行测试确保依赖兼容性
3. 发布新版本

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来改进 BOM 的依赖管理！

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
