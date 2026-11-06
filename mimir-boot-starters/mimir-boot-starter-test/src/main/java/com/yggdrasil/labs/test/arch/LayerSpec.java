package com.yggdrasil.labs.test.arch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按声明顺序记录自上而下的架构层。 */
public final class LayerSpec {
    private final String rootPackage;
    private final Map<String, List<String>> layers = new LinkedHashMap<>();

    private LayerSpec(String rootPackage) {
        this.rootPackage = rootPackage;
    }

    /**
     * 创建分层声明，随后依次声明上层与下层。
     *
     * @param rootPackage 默认层包所在的完整根包名
     * @return 尚未包含层的声明
     * @throws IllegalArgumentException 根包名为空或无效时抛出
     */
    public static LayerSpec of(String rootPackage) {
        return new LayerSpec(ArchPackages.requirePackage(rootPackage, "rootPackage"));
    }

    /**
     * 添加层，其包名为根包加层名，包含全部子包。
     *
     * @param name 层名，同时用作默认包名的最后一段
     * @return 当前声明，供继续添加下一层
     * @throws IllegalArgumentException 层名为空、重复或不能组成完整包名时抛出
     */
    public LayerSpec layer(String name) {
        requireName(name);
        return layer(name, rootPackage + "." + name);
    }

    /**
     * 添加层并指定一个或多个完整包名，每个包均包含全部子包。
     *
     * @param name 层名
     * @param packageNames 该层包含的完整包名列表，不使用 ArchUnit 通配标识
     * @return 当前声明，供继续添加下一层
     * @throws IllegalArgumentException 层名为空或重复，或包名列表为空、包含无效包名时抛出
     */
    public LayerSpec layer(String name, String... packageNames) {
        requireName(name);
        ArchPackages.patterns(packageNames, "packageNames", false);
        layers.put(name, List.of(packageNames));
        return this;
    }

    /**
     * 获取供规则保存的层定义快照。
     *
     * @return 保留声明顺序的不可修改映射
     * @throws IllegalArgumentException 声明层数少于两层时抛出
     */
    Map<String, List<String>> definitions() {
        if (layers.size() < 2) {
            throw new IllegalArgumentException("spec 必须声明至少 2 层");
        }
        // 规则保存声明快照，后续继续构建 LayerSpec 不改变已创建规则。
        return Collections.unmodifiableMap(new LinkedHashMap<>(layers));
    }

    /**
     * 检查新层名是否非空且尚未声明。
     *
     * @param name 新层名
     * @throws IllegalArgumentException 层名为空或重复时抛出
     */
    private void requireName(String name) {
        if (name == null || name.isBlank() || layers.containsKey(name)) {
            throw new IllegalArgumentException("name 必须非空且层名不能重复");
        }
    }
}
