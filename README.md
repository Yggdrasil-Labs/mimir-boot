# Mimir Boot

<div align="center">

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.13-brightgreen.svg)
![Maven](https://img.shields.io/badge/Maven-3.9.9-blue.svg)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

> Yggdrasil-Labs 的 Java 企业级基础框架  
> 统一依赖版本、公共组件、自定义 Starter 与编译期代码生成工具。

[快速开始](#-快速开始) • [模块说明](#-模块说明) • [使用指南](#-使用指南) • [开发工具](#-开发工具)

</div>

## ✨ 特性

- 🎯 **企业级基座**: 统一技术栈，减少重复配置
- 📦 **依赖管理**: Parent POM + BOM 模式，版本统一管理
- 🚀 **Spring Boot Starters**: Web、DB、Security 等可插拔组件
- 🛠️ **开发工具**: 代码生成、工具类、异常处理
- 📋 **质量保证**: Checkstyle、SonarQube、测试框架集成
- 🔧 **CI/CD**: GitHub Actions 自动化构建和发布

## 📦 模块说明

| 模块 | 描述 | 状态 |
|------|------|------|
| `mimir-boot-parent` | 父 POM 与插件集中管理 | ✅ 已完成 |
| `mimir-boot-bom` | 依赖版本统一管理 | ✅ 已完成 |
| `mimir-boot-common` | 公共模型与工具 | 🚧 计划中 |
| `mimir-boot-web` | Web Starter（异常、日志、拦截器） | 🚧 计划中 |
| `mimir-boot-db` | MyBatis-Plus Starter | 🚧 计划中 |
| `mimir-boot-samples` | 示例项目 | 🚧 计划中 |

## 🚀 快速开始

### 1. 使用 Parent POM

```xml
<parent>
  <groupId>com.yggdrasil.labs</groupId>
  <artifactId>mimir-boot-parent</artifactId>
  <version>1.0-SNAPSHOT</version>
</parent>
```

### 2. 使用 BOM 依赖管理

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.yggdrasil.labs</groupId>
      <artifactId>mimir-boot-bom</artifactId>
      <version>1.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### 3. 添加需要的依赖

```xml
<dependencies>
  <!-- Spring Boot Web -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  
  <!-- MyBatis Plus -->
  <dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
  </dependency>
  
  <!-- 工具类 -->
  <dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
  </dependency>
</dependencies>
```

## 🛠️ Maven Profiles

| Profile | 描述 | 用途 |
|---------|------|------|
| `dev` | 开发环境（默认） | 本地开发 |
| `test` | 测试环境 | 测试部署 |
| `prod` | 生产环境 | 生产部署 |
| `skip-tests` | 跳过测试 | 快速构建 |
| `quality` | 代码质量检查 | 代码审查 |
| `release` | 发布配置 | Maven 发布 |
| `sonar` | SonarQube 分析 | 代码质量分析 |
| `ci` | CI/CD 集成 | 持续集成 |
| `docs` | 文档生成 | 项目文档 |

## 📋 技术栈

### 🌸 Spring 生态
- **Spring Boot** 3.3.13 - 应用框架 (最新稳定版)
- **Spring Cloud** 2023.0.6 - 微服务框架 (Leyton)
- **Spring Cloud Alibaba** 2023.0.3.4 - 阿里云组件 (最新稳定版)
- **Spring Security** 6.3.4 - 安全框架

### 🗄️ 数据库相关
- **MyBatis-Plus** 3.5.14 - ORM 框架
- **MyBatis** 3.5.19 - ORM 核心
- **MySQL Connector** 8.2.0 - MySQL 驱动
- **PostgreSQL** 42.7.8 - PostgreSQL 驱动
- **HikariCP** 7.0.2 - 连接池

### 🛠️ 工具类库
- **MapStruct** 1.5.5.Final - 对象映射
- **Lombok** 1.18.36 - 代码生成
- **Hutool** 5.8.34 - 工具类库
- **FastJSON2** 2.0.59 - JSON 处理
- **Jackson** 2.18.2 - JSON 序列化 (Spring Boot 管理)

### 🧪 测试框架
- **JUnit 5** 11.1 - 单元测试 (Spring Boot 管理)
- **Mockito** 5.12.0 - Mock 框架 (Spring Boot 管理)
- **TestContainers** 1.21.3 - 集成测试

### 📊 监控与安全
- **Micrometer** 1.13.2 - 指标收集 (Spring Boot 管理)
- **Prometheus** 1.13.2 - 监控系统 (Spring Boot 管理)
- **JWT** 0.12.6 - 身份认证

### 📄 文档与API
- **SpringDoc** 2.7.0 - OpenAPI 文档
- **Knife4j** 4.5.0 - API 文档界面

### 🔧 其他工具
- **Apache POI** 5.4.1 - Office 文档处理
- **iText** 8.0.2 - PDF 处理
- **Apache Curator** 5.9.0 - 分布式协调
- **Kryo** 5.5.0 - 序列化框架
- **Protobuf** 3.25.5 - 序列化协议
- **OkHttp** 4.12.0 - HTTP 客户端
- **Retrofit** 2.11.0 - REST 客户端
- **Commons Lang3** 3.17.0 - Apache 工具库
- **Commons Collections4** 4.4 - Apache 集合库
- **Commons Pool2** 2.12.0 - Apache 对象池
- **Guava** 33.3.1-jre - Google 工具库

### 📨 消息队列
- **Apache Kafka** 3.7.0 - 分布式流处理平台
- **Apache RocketMQ** 5.2.0 - 阿里云消息队列

### ⏰ 定时任务
- **XXL-Job** 2.4.1 - 分布式任务调度平台

### 🚀 分布式缓存
- **JetCache** 2.7.4 - 多级缓存框架
- **Redisson** 3.32.0 - Redis 客户端

### 🔍 搜索引擎和NoSQL
- **Elasticsearch** 8.11.0 - 分布式搜索引擎
- **MongoDB** 4.11.1 - 文档数据库

## 🔧 开发工具

### 代码质量检查

```bash
# Checkstyle 代码风格检查
mvn checkstyle:check -Pquality

# SonarQube 代码质量分析
mvn clean verify sonar:sonar -Psonar

# 生成代码质量报告
mvn site -Pdocs
```

### 构建和测试

```bash
# 完整构建（包含测试）
mvn clean verify

# 跳过测试快速构建
mvn clean package -DskipTests

# 运行特定 Profile
mvn clean package -Pprod
```

### 发布管理

```bash
# 发布到 Maven Central
mvn clean deploy -Prelease

# 更新依赖版本
mvn versions:display-dependency-updates

# 更新插件版本
mvn versions:display-plugin-updates
```

## 🚀 Maven Wrapper

项目已集成 Maven Wrapper，无需本地安装 Maven：

```bash
# Windows
./mvnw.cmd clean install

# Linux/macOS
./mvnw clean install

# 查看 Maven 版本
./mvnw --version
```

## 📝 完整使用示例

### 创建新项目

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.yggdrasil.labs</groupId>
        <artifactId>mimir-boot-parent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>my-application</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.yggdrasil.labs</groupId>
                <artifactId>mimir-boot-bom</artifactId>
                <version>1.0-SNAPSHOT</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <!-- MyBatis Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
        
        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
        
        <!-- 工具类 -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
        
        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

## 🎯 项目定位

**Mimir Boot** 是一个**企业级基础框架基座**，提供：

- 📦 **统一依赖管理**: 避免版本冲突，统一技术栈
- 🛠️ **开发工具集成**: 开箱即用的代码质量检查
- 🚀 **快速开发**: 减少重复配置，专注业务开发
- 📋 **最佳实践**: 遵循企业级开发规范

## 🔄 版本更新说明

### 最新版本更新 (2024)
- ✅ **JDK版本**: Java 17 (LTS版本，官方全面支持)
- ✅ **Spring Boot**: 3.3.13 (最新稳定版)
- ✅ **Spring Cloud**: 2023.0.6 (Leyton，最新版本)
- ✅ **Spring Cloud Alibaba**: 2023.0.3.4 (最新稳定版)
- ✅ **数据库驱动**: MySQL 8.2.0, PostgreSQL 42.7.8, HikariCP 7.0.2
- ✅ **ORM框架**: MyBatis 3.5.19, MyBatis-Plus 3.5.14
- ✅ **工具库**: FastJSON2 2.0.59, Apache POI 5.4.1
- ✅ **测试框架**: TestContainers 1.21.3
- ✅ **分布式组件**: Apache Curator 5.9.0
- ✅ **技术栈共识**: Kafka + RocketMQ, XXL-Job, JetCache, Elasticsearch + MongoDB

### 技术栈选择原则
- 🎯 **现代化**: 选择最新稳定版本
- 🔒 **安全性**: 优先考虑安全修复
- ⚡ **性能**: 关注性能优化
- 🔄 **兼容性**: 确保与JDK 17和Spring Boot 3.3.13兼容

## 📊 版本兼容性矩阵

| 组件                       | 推荐版本                        | 稳定性       | 兼容说明                           |
| ------------------------ | --------------------------- | --------- | ------------------------------ |
| **JDK**                  | **17 (LTS)**                | ✅ 长期支持    | 官方全面支持 Spring Boot 3.x         |
| **Spring Boot**          | **3.3.13**                  | ✅ 最新稳定版  | 官方最新版本，兼容 JDK 17、21            |
| **Spring Cloud**         | **2023.0.6（代号 Leyton）**     | ✅ 最新版本 | 与 Boot 3.3.x 完全兼容              |
| **Spring Cloud Alibaba** | **2023.0.3.4**              | ✅ 最新稳定版 | 与 Boot 3.3.x、Cloud 2023.0.x 兼容 |

## ⚠️ 重要说明

- ❌ **不是独立服务**: 这是一个基础框架，不是可运行的应用
- ❌ **不需要容器化**: 基座项目不需要 Docker 化
- ✅ **作为依赖使用**: 被其他项目继承和引用
- ✅ **统一技术栈**: 为企业项目提供统一的技术基础

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 📞 联系我们

- **组织**: Yggdrasil Labs
- **邮箱**: contact@yggdrasil-labs.com
- **网站**: https://yggdrasil-labs.com
- **GitHub**: https://github.com/Yggdrasil-Labs/mimir-boot

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给它一个 Star！**

Made with ❤️ by Yggdrasil Labs

</div>