package com.yggdrasil.labs.log.converter;

import ch.qos.logback.core.Context;

/** JSON 结构化字段使用的脱敏入口，属于内部实现，不是稳定公共 API。 */
public final class StructuredMaskSupport {

    private StructuredMaskSupport() {}

    /** 与 %mask 转换器 start() 共用 Context 绑定与快照作废逻辑。 */
    public static void bindContext(Context context) {
        SensitiveDataConverter.bindContextConfiguration(context);
    }

    /** 返回当前不可变规则代次；持有者可继续使用已捕获的旧代次。 */
    public static MaskRules currentRules() {
        return SensitiveDataConverter.currentRules();
    }

    /** 一代不可变脱敏规则，允许异步事件保留捕获时的规则。 */
    public interface MaskRules {
        /** 按既有文本转换规则处理完整消息。 */
        String maskText(String text);

        /** 判断字段名是否命中启用的敏感别名后缀。 */
        boolean isSensitiveKey(String key);

        /** 仅应用值正则，不把键值文本正则改写为字段规则。 */
        String maskValue(String value);

        /** 返回本代规则的替换串。 */
        String replacement();
    }
}
