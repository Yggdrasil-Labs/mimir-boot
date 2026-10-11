# Mimir Boot Starter Nacos

基于 Spring Cloud Alibaba Nacos Config 的配置加密脱敏启动器，提供开箱即用的配置加密解密功能。

## 用途

在 Spring Cloud Alibaba Nacos Config 基础上增强配置安全能力：

- ✅ **配置加密脱敏**：支持 `ENC(encrypted_value)` 格式的配置值自动解密
- ✅ **自动解密处理**：应用启动时和配置刷新时自动处理加密配置
- ✅ **认证加密**：新密文使用带随机 IV 的 AES-GCM
- ✅ **工具类支持**：提供密钥生成、加密解密等便捷工具
- ✅ **动态刷新支持**：配置动态刷新时自动重新解密

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-starter-nacos</artifactId>
    <version>YOUR_RELEASE_VERSION</version>
</dependency>
```

**注意**：需要同时引入 `spring-cloud-starter-alibaba-nacos-config` 依赖（已自动传递）。

### 基础配置

#### 1. Nacos 配置中心配置

在 `bootstrap.yml` 或 `application.yml` 中配置 Nacos：

```yaml
spring:
  application:
    name: your-application-name
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        file-extension: yaml
        namespace: dev
        group: DEFAULT_GROUP
```

#### 2. 配置加密密钥

在 `application.yml` 中配置加密密钥：

```yaml
mimir:
  boot:
    nacos:
      encrypt:
        enabled: true              # 是否启用配置加密脱敏功能，默认 true
        key: ${NACOS_ENCRYPT_KEY}  # Base64 编码的 AES 密钥（存在加密配置值时必填）
        prefix: ENC                # 加密前缀，默认 ENC
```

#### 3. 在 Nacos 配置中心使用加密值

在 Nacos 配置中心，使用 `ENC(encrypted_value)` 格式配置敏感信息：

```yaml
# 示例：在 Nacos 配置中心的配置内容
database:
  password: ENC(xxxxxxxx)        # 加密的数据库密码
  username: admin

api:
  secret-key: ENC(yyyyyyyy)      # 加密的 API 密钥

redis:
  password: ENC(zzzzzzzz)        # 加密的 Redis 密码
```

应用启动时会自动检测并解密所有符合已配置加密前缀（默认 `ENC`）格式的配置值。

只有显式绑定 `mimir.boot.nacos.encrypt` 或兼容的 `mimir.nacos.encrypt` 配置前缀时才会扫描配置；没有绑定前缀时，普通配置中的匹配前缀文本不会触发密钥校验。

## 核心功能

### 1. 自动配置解密

Starter 通过 Spring Boot 的 `EnvironmentPostProcessor` 在 ApplicationContext 刷新前自动处理配置解密：

```yaml
# Nacos 中的配置（加密后）
app:
  secret: ENC(encrypted_value_here)
  api-key: ENC(another_encrypted_value)

# 应用运行时（自动解密后）
# app.secret = "decrypted_plaintext"
# app.api-key = "another_decrypted_value"
```

### 2. 配置动态刷新支持

支持 Spring Cloud 配置刷新功能，配置变更时自动重新解密：

```java
@RestController
@RefreshScope  // Spring Cloud 配置刷新
public class ConfigController {
    
    @Value("${app.secret}")
    private String secret;  // 配置刷新时会自动重新解密
    
    @GetMapping("/secret")
    public String getSecret() {
        return secret;  // 始终返回解密后的值
    }
}
```

刷新时如果 `mimir.boot.nacos.encrypt` 和兼容的 `mimir.nacos.encrypt` 前缀均不再绑定，监听器会移除旧的 `decryptedProperties:*` 解密覆盖层，原始属性源中的明文或下层配置随后生效；重新配置加密前缀后，会继续处理密文。

默认 ConfigData 模式中，Starter 委托原 Nacos resolver/loader 处理 `spring.config.import=nacos:...`，为成功加载的标准枚举属性源记录归属；成功空配置保留不提供配置值的标记。在原 Cloud 刷新链计算差异前，同步这些 Nacos 源并重建解密层。因此单键删除、语义空配置、DataId 删除和 YAML 文档减少不会继续使用这些源中的旧配置，缺失值可回退到下层源。原 import 格式、group、preference、refreshEnabled 等参数继续生效。

满足此前提后，若业务属性被完全删除且没有下层值，Environment 不再返回旧值；Spring Cloud 对已存在的 `@ConfigurationProperties` Bean 重新绑定时，缺失的字段仍可能保留原值。业务需要移除旧值时，须按字段类型选择处置方式：

- 对 `String` 字段，如果业务语义和校验规则允许空字符串，可显式配置 `app.secret: ""`，重新绑定后的值为 `""`。空字符串不等于 `null`，此方式也不适用于所有字段类型。
- 需要恢复 `null` 或字段默认值时，可显式选择下述刷新作用域重建方式，或由应用按业务规则重置字段。删除配置或配置 `null` 不能作为普通 Bean 的通用字段清空保证。
- 再次发布 `EnvironmentChangeEvent` 或调用 `ConfigurationPropertiesRebinder.rebind()` 只会对原 Bean 实例重新绑定，不属于创建新实例，也不会自动重置缺失字段。

#### 显式重建配置 Bean

需要删除属性后恢复初始化值的可变配置 Bean，可以在工厂方法上同时声明 `@Bean`、`@RefreshScope` 和 `@ConfigurationProperties`。业务通过注入的代理访问配置；完整刷新成功后，下次访问会创建并重新绑定目标实例：

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AppConfiguration {
    @Bean
    @RefreshScope
    @ConfigurationProperties("app")
    public AppSettings appSettings() {
        return new AppSettings();
    }

    public static class AppSettings {
        private String secret;
        private int timeout = 30;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
    }
}
```

不要再通过 `@EnableConfigurationProperties(AppSettings.class)` 或组件扫描重复注册同一配置类型。此方式适用于可代理、可重新创建的 JavaBean；新目标重新执行初始化与绑定，业务需确认销毁、初始化及默认值符合预期。

需要走 Spring Cloud 的完整刷新流程，例如 `ContextRefresher.refresh()`，其顺序是更新 Environment、发布 `EnvironmentChangeEvent`、使刷新作用域失效；单独发布环境事件不会重建目标。Nacos 的配置刷新事件会接入该流程，仍需在应用启用相应动态刷新。除可变属性源测试外，`NacosRefreshEventIT` 通过实际 Alibaba 事件监听器与 Boot 文件 ConfigData 重载验证该流程；测试未连接 Nacos 服务端，不能替代其网络加载、删除和推送验收。

上层属性从实时 Environment 删除且有下层值时，新目标绑定下层值；完全缺失时恢复字段初始化值，例如 `secret=null`、`timeout=30`。默认刷新器在发布环境事件前遇到传播出的加载或解密异常时，恢复原属性源引用和顺序，保留已创建目标；修正后需再次完整刷新。此恢复不回滚外部提前修改的可变 Map 或 SDK 内部快照，不提供任意并发 Environment 读写的事务隔离。业务不能缓存裸目标或字段快照来代替代理，否则仍可能使用旧值；重建也不保证擦除其他对象持有的历史明文。

构造注入的业务服务应保存代理，每次使用时读取当前值，例如：

```java
@Service
public class AppService {
    private final AppConfiguration.AppSettings settings;

    public AppService(AppConfiguration.AppSettings settings) {
        this.settings = settings;
    }

    public String currentSecret() {
        return settings.getSecret();
    }
}
```

成功刷新会销毁已创建的旧目标；下一次代理访问才创建、绑定并初始化新目标。初始化、绑定或校验失败可能在这次访问时暴露，不属于保留旧目标的解密失败情形，业务应按自身校验和可用性要求验收。销毁回调不能假定新目标已经创建。

默认 `NacosConfigDataContextRefresher` 在差异计算前完成解密层更新，删除不再被旧明文覆盖层遮挡。`EnvironmentChangeEvent.keys` 沿用 Cloud Context 4.1.6 的可枚举属性原始值差异：它枚举 `EnumerablePropertySource` 提供的属性名并比较这些名称对应的值，不保证枚举占位符依赖导致的派生属性有效值变化。例如 `app.secret=${x:fallback}` 中删除 `x` 后，事件可能只包含 `x`，而不包含解析值已改变的 `app.secret`；即使派生值改变，也不能据此断言其键必在事件中。按键缓存不能只依赖派生键通知：应在每次完整刷新时整体失效并重读配置，或通过 `@RefreshScope` 代理读取当前配置。

`optional:nacos:...` 的加载异常被上游吞掉时，不生成成功空标记，该资源的旧源保留；其他成功结果仍应用，完整刷新仍使 RefreshScope 失效。非 optional 的传播异常才使本次刷新失败。SDK 的 snapshot/failover 行为沿用原客户端，成功加载不等同于服务端线性一致读取。

源清理只覆盖本 Starter 标记、且本次成功加载的标准 Nacos 源；自定义非枚举 parser 源、移除 import、改变资源身份和本地文件源消失不提供相同保证。`NacosRefreshEventIT` 保留空本地文件仍残留旧原始源的上游负向对照；`NacosManagedRefreshIT` 通过真实 Boot/Nacos ConfigData SPI、受控远程边界和注入消费者验证 Nacos 删除、多文档、失败及恢复，不代表服务端推送已验收。仅增强 Cloud 实际注册的默认 ConfigData 刷新器；用户自定义 `ContextRefresher`、legacy bootstrap、关闭 `spring.cloud.refresh.enabled` 或排除 `RefreshAutoConfiguration` 时退让。排除可通过 `@EnableAutoConfiguration(exclude = RefreshAutoConfiguration.class)` 或 `spring.autoconfigure.exclude` 配置，不要求额外排除本 Starter；自定义刷新接入需应用自行满足对应契约。

#### 真实 Nacos 服务验收

常规测试不依赖外部服务，只排除新增的 `NacosLiveRefreshIT`；显式启用 `nacos-live` 后，它与原全部测试一起执行，缺少服务参数或凭据直接失败，不使用条件跳过。先配置 `NACOS_TEST_SERVER_ADDR`、`NACOS_TEST_USERNAME`、`NACOS_TEST_PASSWORD` 和可选 `NACOS_TEST_NAMESPACE`，再运行：

```bash
./mvnw -Pci,nacos-live -pl mimir-boot-starters/mimir-boot-starter-nacos -am clean verify
```

测试创建唯一 group/DataId，经真实客户端发布和监听推送，验证 Environment、变更键、构造注入代理和生命周期，最后仅清理本轮配置。错误密钥场景要求本轮预期配置键的解密认证异常，网络或无关监听器异常不能作为成功验收依据。客户端不允许空字符串发布，注释 properties 验证的是语义空配置。框架的指定真实服务验收及关闭范围见[验收记录](../../docs/active/v2.3.0/runtime-debt-followup/plan.md#第九阶段td-039-框架关闭与消费者迁移拆分)；现有业务应用迁移由 [TD-047](../../docs/active/tech-debt-tracker.md#td-047-nacos-消费者迁移)独立跟踪，框架测试不替代应用验收。

### 3. 加密工具类

提供便捷的工具类用于生成密钥和加密配置值：

```java
import com.yggdrasil.labs.nacos.util.NacosEncryptUtil;

// 1. 生成加密密钥
String key = NacosEncryptUtil.generateKey();
System.out.println("密钥: " + key);  // Base64 编码的密钥

// 2. 加密配置值
String plaintext = "my-secret-password";
String encrypted = NacosEncryptUtil.encrypt(plaintext, key);
System.out.println("加密后: " + encrypted);

// 3. 生成 ENC() 格式（用于 Nacos 配置）
String encValue = NacosEncryptUtil.wrapWithEnc(encrypted);
System.out.println("配置值: " + encValue);  // 输出: ENC(encrypted_value)
```

### 4. 自定义加密前缀

支持自定义加密前缀，默认使用 `ENC`：

```yaml
mimir:
  boot:
    nacos:
      encrypt:
        prefix: SECRET  # 自定义前缀
```

配置示例：

```yaml
# Nacos 中的配置
password: SECRET(encrypted_value)  # 使用自定义前缀
```

## 配置参数

默认配置项前缀为 `mimir.boot.nacos.encrypt`；仍兼容已弃用的 `mimir.nacos.encrypt`：

```yaml
mimir:
  boot:
    nacos:
      encrypt:
        # 是否启用配置加密脱敏功能
        enabled: true
      
        # 加密密钥（Base64 编码；存在匹配配置前缀的加密值时必填）
        # 可以通过工具类生成：NacosEncryptUtil.generateKey()
        key: YOUR_BASE64_ENCODED_KEY
      
        # 加密前缀，默认 ENC
        # 配置值格式：prefix(encrypted_value)
        prefix: ENC
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|------|------|
| `enabled` | Boolean | `true` | 否 | 是否启用配置加密脱敏功能 |
| `key` | String | - | 有加密值时是 | Base64 编码的加密密钥，用于解密配置值 |
| `algorithm` | String | `AES/GCM/NoPadding` | 否 | 已弃用；应用配置仅支持 AES-GCM |
| `prefix` | String | `ENC` | 否 | 加密配置值的前缀，格式为 `prefix(encrypted_value)` |

## 使用示例

### 完整示例

#### 1. 生成密钥并配置

```java
import com.yggdrasil.labs.nacos.util.NacosEncryptUtil;

public class KeyGenerator {
    public static void main(String[] args) {
        // 生成密钥
        String key = NacosEncryptUtil.generateKey();
        System.out.println("请将此密钥配置到 application.yml:");
        System.out.println("mimir.boot.nacos.encrypt.key: " + key);
    }
}
```

#### 2. 加密敏感配置

```java
import com.yggdrasil.labs.nacos.util.NacosEncryptUtil;

public class ConfigEncryptor {
    public static void main(String[] args) {
        String key = "YOUR_BASE64_KEY";  // 从配置文件中读取
        
        // 加密数据库密码
        String dbPassword = "MySecret123!";
        String encrypted = NacosEncryptUtil.encrypt(dbPassword, key);
        String encValue = NacosEncryptUtil.wrapWithEnc(encrypted);
        System.out.println("Nacos 配置值: " + encValue);
        // 输出: ENC(encrypted_base64_string)
    }
}
```

#### 3. 在 Nacos 中配置

登录 Nacos 控制台，创建或编辑配置文件（如 `application-dev.yaml`）：

```yaml
# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: ENC(encrypted_password_here)  # 加密后的密码

# API 配置
app:
  api:
    secret-key: ENC(encrypted_secret_key_here)
    access-token: ENC(encrypted_token_here)

# 其他敏感配置
payment:
  merchant-id: ENC(encrypted_merchant_id)
  api-key: ENC(encrypted_api_key)
```

#### 4. 应用中使用

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConfig {
    
    @Value("${spring.datasource.password}")
    private String dbPassword;  // 自动解密后的值
    
    @Value("${app.api.secret-key}")
    private String apiSecretKey;  // 自动解密后的值
    
    public void connect() {
        // dbPassword 和 apiSecretKey 已经是解密后的明文
        System.out.println("数据库密码: " + dbPassword);
    }
}
```

### 配置刷新示例

```java
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RefreshScope  // 启用配置刷新
public class DynamicConfig {
    
    @Value("${app.api.secret-key}")
    private String apiSecretKey;
    
    public String getApiSecretKey() {
        // 配置刷新时，如果 Nacos 中的值更新了，
        // 会自动重新解密并更新此值
        return apiSecretKey;
    }
}
```

## 最佳实践

### 1. 密钥管理

**生产环境密钥安全建议**：

```yaml
# ❌ 不推荐：直接写在配置文件中
mimir:
  boot:
    nacos:
      encrypt:
        key: base64_key_in_plaintext

# ✅ 推荐：使用环境变量
mimir:
  boot:
    nacos:
      encrypt:
        key: ${NACOS_ENCRYPT_KEY}  # 从环境变量读取
```

或者使用密钥管理服务（如 Vault、KMS）：

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NacosEncryptConfig {
    
    @Bean
    public NacosEncryptProperties nacosEncryptProperties(
            @Value("${vault.nacos.encrypt.key}") String key) {
        NacosEncryptProperties properties = new NacosEncryptProperties();
        properties.setKey(key);  // 从密钥管理服务获取
        return properties;
    }
}
```

### 2. 密钥生成

使用工具类生成密钥，并妥善保存：

```java
import com.yggdrasil.labs.nacos.util.NacosEncryptUtil;
import java.util.Base64;

public class SecureKeyGenerator {
    public static void main(String[] args) {
        // 生成密钥
        String key = NacosEncryptUtil.generateKey();
        
        // 保存到密钥管理服务或安全存储
        System.out.println("生成的密钥（Base64）: " + key);
        
        // 注意：生产环境请使用密钥管理服务，不要硬编码
    }
}
```

### 3. 配置加密流程

1. **生成密钥**：使用 `NacosEncryptUtil.generateKey()` 生成密钥
2. **配置密钥**：将密钥配置到应用配置中（建议使用环境变量）
3. **加密敏感值**：使用 `NacosEncryptUtil.encrypt()` 加密敏感配置
4. **配置到 Nacos**：将 `ENC(encrypted_value)` 格式的值配置到 Nacos
5. **应用自动解密**：应用启动时自动解密配置值

### 4. 混合使用加密和非加密配置

```yaml
# Nacos 配置示例
database:
  host: localhost              # 非敏感配置，不需要加密
  port: 3306                  # 非敏感配置，不需要加密
  username: root              # 非敏感配置，不需要加密
  password: ENC(xxxxxx)       # 敏感配置，使用加密

api:
  base-url: https://api.example.com  # 非敏感配置
  secret-key: ENC(yyyyyy)            # 敏感配置，使用加密
```

### 5. 不同环境使用不同密钥

```yaml
# application-dev.yml
mimir:
  boot:
    nacos:
      encrypt:
        key: ${DEV_ENCRYPT_KEY}

# application-prod.yml
mimir:
  boot:
    nacos:
      encrypt:
        key: ${PROD_ENCRYPT_KEY}  # 生产环境使用不同的密钥
```

## 常见问题

### 问题1：配置未解密

**症状**：配置值仍然是 `ENC(...)` 格式，未自动解密

**可能原因**：

1. 未配置加密密钥或密钥配置错误
2. 功能被禁用（`mimir.boot.nacos.encrypt.enabled: false`）
3. 配置值格式不正确

**解决方案**：

```yaml
# 检查配置
mimir:
  boot:
    nacos:
      encrypt:
        enabled: true      # 确保已启用
        key: YOUR_KEY      # 确保密钥正确配置

# 检查配置值格式（Nacos 中）
password: ENC(encrypted_value)  # ✅ 正确格式
password: enc(encrypted_value)  # ✅ 大小写不敏感，也支持
password: ENC encrypted_value   # ❌ 错误格式，缺少括号
```

### 问题2：解密失败

**症状**：应用启动时报解密异常或配置值仍为加密格式

**可能原因**：

1. 使用了错误的密钥解密
2. 密文被篡改或格式不正确
3. 密钥不是有效的 Base64 AES 密钥（128、192 或 256 位）

**解决方案**：

```java
// 验证加密解密流程
String key = "YOUR_KEY";
String plaintext = "test";
String encrypted = NacosEncryptUtil.encrypt(plaintext, key);
String decrypted = NacosEncryptUtil.decrypt(encrypted, key);
System.out.println("原文: " + plaintext);
System.out.println("解密: " + decrypted);
// 应该输出相同的值
```

### 问题3：配置刷新后未重新解密

**症状**：配置刷新后，加密配置值未更新

**解决方案**：

1. 确认配置类使用了 `@RefreshScope` 注解
2. 检查日志中是否有配置变更事件的输出
3. 确认 `EnvironmentChangeEvent` 正常触发

```java
@RefreshScope  // 确保添加此注解
@Component
public class ConfigBean {
    @Value("${app.secret}")
    private String secret;
}
```

### 问题4：如何查看解密后的配置值

**调试方法**：

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigDebugger {
    
    @Value("${app.secret}")
    private String secret;
    
    @EventListener(ContextRefreshedEvent.class)
    public void debugConfig() {
        System.out.println("解密后的配置值: " + secret);
        // 注意：生产环境请移除此调试代码
    }
}
```

### 问题5：自定义加密前缀不生效

**症状**：配置了自定义前缀，但未识别

**解决方案**：

1. 确认前缀配置正确
2. 确认 Nacos 中的配置值使用新前缀

```yaml
# application.yml
mimir:
  boot:
    nacos:
      encrypt:
        prefix: SECRET  # 自定义前缀

# Nacos 配置
password: SECRET(encrypted_value)  # 使用新前缀
```

## 技术实现

### 工作原理

1. **配置加载阶段**：`EnvironmentPostProcessor` 在 ApplicationContext 刷新前处理已加载的配置属性源
2. **配置扫描**：遍历所有配置属性源，查找包含已配置加密前缀（默认 `ENC`）格式的值
3. **自动解密**：提取加密内容，使用配置的密钥进行解密
4. **配置替换**：将解密后的值紧邻原属性源之前加入，只覆盖对应密文键并保持其他属性源的优先级
5. **动态刷新**：监听 `EnvironmentChangeEvent`，配置刷新时重新解密

### 安全说明

- **默认实现**：使用 `AES/GCM/NoPadding`，密文格式为 `v1:<iv-base64>:<ciphertext-base64>`；每次加密都会生成随机 IV，并通过 GCM 标签校验完整性。
- **严格失败**：显式绑定加密配置前缀、启用解密（`enabled=true`）且存在匹配配置前缀的加密值时，密钥缺失、密钥格式非法、密文被篡改或错误密钥都会使启动或刷新失败；日志仅记录属性名和解密数量。
- **密钥管理**：生产环境请使用密钥管理服务（Vault、KMS）动态获取密钥
- **密钥轮换**：定期轮换加密密钥，重新加密所有配置值

### 迁移说明

- 将 `mimir.nacos.encrypt` 迁移为 `mimir.boot.nacos.encrypt`。旧前缀仅在未配置新前缀时兼容，并会输出弃用警告；新旧同时存在时以新前缀为准。
- 使用工具类重新生成的密文为 `v1` AES-GCM 格式。旧版不带版本的 AES 密文不会再由应用自动读取；请在隔离的离线迁移流程中通过已弃用的三参数 `decrypt` API 读取后重新加密并替换。
- 移除旧的 `algorithm: AES` 配置；应用配置仅支持 AES-GCM。

## 技术栈

- **Spring Cloud Alibaba Nacos**: 配置中心
- **Spring Boot**: 3.3.13+
- **Spring Cloud**: 2023.0.6+
- **Java**: 17+

## 📚 相关文档

- [Mimir Boot 项目主页](../../README.md) - 项目总体说明
- [Mimir Boot Common](../../mimir-boot-common/README.md) - 公共组件说明
- [Mimir Boot BOM](../../mimir-boot-bom/README.md) - 依赖版本管理

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs
