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
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-parent</artifactId>
    <version>1.0.0</version>
</parent>
```

### 2. 引入 BOM

在 `dependencyManagement` 中引入 `mimir-boot-bom`：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.yggdrasil.labs</groupId>
            <artifactId>mimir-boot-bom</artifactId>
            <version>1.0.0</version>
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

## 📦 管理的依赖版本

BOM 管理以下类别的依赖版本：

### 核心框架

- **Spring Boot**: 3.3.13
- **Spring Cloud**: 2023.0.6 (Leyton)
- **Spring Cloud Alibaba**: 2023.0.3.4

### 数据持久化

- **MyBatis-Plus**: 3.5.14

### 工具类库

- **MapStruct**: 1.6.3 - 对象映射
- **Lombok**: 1.18.42 - 减少样板代码
- **Hutool**: 5.8.41 - Java 工具类库
- **FastJSON2**: 2.0.60 - JSON 处理
- **JavaPoet**: 1.13.0 - 代码生成

### 日志

- **SLF4J**: 2.0.17 - 日志门面

### 测试

- **Testcontainers**: 1.21.3 - 集成测试容器
- **Compile Testing**: 0.21.0 - 编译期测试
- **Truth**: 1.4.2 - 断言库

### 安全

- **JWT (JJWT)**: 0.13.0 - JSON Web Token

### 文档

- **SpringDoc OpenAPI**: 2.8.13 - API 文档
- **Knife4j**: 4.5.0 - API 文档增强

### 缓存

- **Redisson**: 3.52.0 - Redis 客户端

### 消息队列

- **RocketMQ**: 5.2.0 - 消息队列
- **Kafka**: 3.7.0 - 消息队列

### 定时任务

- **XXL-Job**: 3.2.0 - 分布式任务调度

### 分布式缓存

- **JetCache**: 2.7.8 - 多级缓存框架

### 搜索引擎和 NoSQL

- **Elasticsearch**: 8.11.0 - 搜索引擎
- **MongoDB**: 4.11.5 - NoSQL 数据库

### 其他工具

- **Apache Commons Lang3**: 3.20.0
- **Apache Commons Collections4**: 4.5.0
- **Guava**: 33.5.0-jre
- **Kryo**: 5.6.2 - 序列化
- **Protobuf**: 4.33.1 - 序列化
- **OkHttp**: 5.3.2 - HTTP 客户端
- **Retrofit**: 3.0.0 - HTTP 客户端
- **Apache POI**: 5.5.0 - Office 文档处理
- **iText**: 8.0.2 - PDF 处理
- **Apache Curator**: 5.9.0 - 分布式锁

### Mimir Boot 模块

BOM 还管理 Mimir Boot 自身模块的版本：

- `mimir-boot-common`
- `mimir-boot-starter-log`
- `mimir-boot-starter-exception`
- `mimir-boot-starter-web`
- `mimir-boot-starter-nacos`
- `mimir-boot-starter-mybatis`
- `mimir-boot-starter-mybatis-processor`
- `mimir-boot-starter-test`

## 🔧 配置说明

### 版本继承

BOM 通过 `dependencyManagement` 管理版本，子项目继承父 POM 时自动生效：

```xml
<parent>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-parent</artifactId>
    <version>1.0.0</version>
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
            <groupId>com.yggdrasil.labs</groupId>
            <artifactId>mimir-boot-bom</artifactId>
            <version>1.0.0</version>
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
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-parent</artifactId>
    <version>1.0.0</version>
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

