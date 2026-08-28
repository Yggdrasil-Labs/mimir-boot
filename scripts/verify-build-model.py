#!/usr/bin/env python3
"""验证 Reactor 的默认与 Maven Central effective POM 签名开关。"""

from __future__ import annotations

import subprocess
import sys
import tempfile
import xml.etree.ElementTree as element_tree
import os
from pathlib import Path

NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
ROOT = Path(__file__).resolve().parents[1]
MVNW = ROOT / "mvnw"
GPG_GROUP = "org.apache.maven.plugins"
GPG_ARTIFACT = "maven-gpg-plugin"


def text(element: element_tree.Element | None) -> str | None:
    return element.text.strip() if element is not None and element.text else None


def java_17_required() -> None:
    result = subprocess.run(["java", "-version"], text=True, capture_output=True, check=False)
    output = result.stderr + result.stdout
    if result.returncode != 0 or 'version "17' not in output:
        raise RuntimeError(f"需要 Java 17，实际输出：{output.strip()}")


def recursive_reactor_poms(pom: Path) -> list[Path]:
    model = element_tree.parse(pom).getroot()
    result = [pom]
    for module in model.findall("m:modules/m:module", NAMESPACE):
        module_pom = pom.parent / (module.text or "") / "pom.xml"
        if not module_pom.is_file():
            raise RuntimeError(f"Reactor module 不存在：{module_pom}")
        result.extend(recursive_reactor_poms(module_pom))
    return result


def effective_pom(
    pom: Path, profile: str | None, output: Path, cache: Path | None
) -> element_tree.Element:
    command = [str(MVNW), "-B", "-N", "-f", str(pom)]
    if cache is not None:
        command.append(f"-Dmaven.repo.local={cache}")
    if profile:
        command.append(f"-P{profile}")
    command.extend(["help:effective-pom", f"-Doutput={output}"])
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
    if result.returncode != 0 or not output.is_file():
        raise RuntimeError(
            f"无法生成 {pom.relative_to(ROOT)} 的 {profile or 'default'} effective POM：\n"
            f"{result.stdout}\n{result.stderr}"
        )
    return element_tree.parse(output).getroot()


def bootstrap_reactor_metadata(cache: Path | None) -> None:
    bootstrap_poms = (
        ROOT / "pom.xml",
        ROOT / "mimir-boot-bom" / "pom.xml",
        ROOT / "mimir-boot-parent" / "pom.xml",
    )
    for pom in bootstrap_poms:
        command = [
            str(MVNW),
            "-B",
            "-N",
            "-f",
            str(pom),
            "install",
            "-Dgpg.skip=true",
        ]
        if cache is not None:
            command.append(f"-Dmaven.repo.local={cache}")
        result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
        if result.returncode != 0:
            raise RuntimeError(
                f"无法向隔离缓存安装 {pom.relative_to(ROOT)} 元数据：\n{result.stdout}\n{result.stderr}"
            )


def require_gpg_configuration(model: element_tree.Element, expected_skip: str, label: str) -> None:
    properties = model.findall("m:properties/m:gpg.skip", NAMESPACE)
    values = [text(item) for item in properties]
    if len(properties) != 1 or values != [expected_skip]:
        raise RuntimeError(f"{label}: gpg.skip 必须唯一且为 {expected_skip}，实际为 {values}")

    plugins = []
    for plugin in model.findall(".//m:build/m:plugins/m:plugin", NAMESPACE):
        group_id = text(plugin.find("m:groupId", NAMESPACE)) or GPG_GROUP
        artifact_id = text(plugin.find("m:artifactId", NAMESPACE))
        if group_id == GPG_GROUP and artifact_id == GPG_ARTIFACT:
            plugins.append(plugin)
    if not plugins:
        raise RuntimeError(f"{label}: effective POM 缺少 {GPG_ARTIFACT}")

    for plugin in plugins:
        top_level_skip = text(plugin.find("m:configuration/m:skip", NAMESPACE))
        if top_level_skip != expected_skip:
            raise RuntimeError(f"{label}: GPG plugin 顶层 skip={top_level_skip!r}，期望 {expected_skip}")
        executions = plugin.findall("m:executions/m:execution", NAMESPACE)
        if not executions:
            raise RuntimeError(f"{label}: GPG plugin 缺少 execution")
        for execution in executions:
            execution_skip = text(execution.find("m:configuration/m:skip", NAMESPACE))
            if execution_skip != expected_skip:
                execution_id = text(execution.find("m:id", NAMESPACE)) or "<unnamed>"
                raise RuntimeError(
                    f"{label}: GPG execution {execution_id} skip={execution_skip!r}，期望 {expected_skip}"
                )


def main() -> int:
    java_17_required()
    poms = recursive_reactor_poms(ROOT / "pom.xml")
    if len(set(poms)) != len(poms):
        raise RuntimeError("Reactor POM 枚举出现重复")
    with tempfile.TemporaryDirectory(prefix="mimir-effective-poms-") as temporary_directory:
        temporary_root = Path(temporary_directory)
        cache_setting = os.environ.get("MIMIR_BUILD_MODEL_M2")
        cache = Path(cache_setting) if cache_setting else None
        if cache is not None:
            cache.mkdir(parents=True, exist_ok=True)
        bootstrap_reactor_metadata(cache)
        for index, pom in enumerate(poms):
            for profile, expected in ((None, "true"), ("maven-central", "false")):
                model = effective_pom(
                    pom,
                    profile,
                    temporary_root / f"{index}-{profile or 'default'}.xml",
                    cache,
                )
                require_gpg_configuration(model, expected, f"{pom.relative_to(ROOT)} [{profile or 'default'}]")
    print(f"已验证 {len(poms)} 个 Reactor POM 的 default/maven-central GPG 模型")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.SubprocessError, element_tree.ParseError) as error:
        print(f"构建模型验证失败：{error}", file=sys.stderr)
        raise SystemExit(1)
