#!/usr/bin/env python3
"""Verify every mixin reference of a KoHs Inventory Tweaks source tree.

Minecraft 26.x ships deobfuscated, so a mixin target name in the source is the
exact name the client looks up at runtime. That makes it possible to prove,
before launching the game, that no @Mixin target class, injected method,
@Accessor field, @Invoker method or INVOKE injection point is missing - the
failure mode that produced the 26.2 startup crash fixed in 1.0.5.

Usage:
    python tools/verify-mixin-targets.py [--source-root .] [--minecraft-jar <path>]

The Minecraft jar defaults to the Loom cache entry for the `minecraft_version`
declared in the tree's gradle.properties.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

MIXIN_ANNOTATION = re.compile(r"@Mixin\s*\((?P<body>.*?)\)\s*(?:public|abstract|final|@|class|interface)", re.S)
CLASS_LITERAL = re.compile(r"([A-Za-z_][\w.]*)\.class")
TARGETS_LITERAL = re.compile(r"targets\s*=\s*\"([^\"]+)\"")
METHOD_REFERENCE = re.compile(r"method\s*=\s*(\{[^}]*\}|\"[^\"]*\")", re.S)
INJECTOR = re.compile(r"@(Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|WrapOperation|WrapWithCondition)\s*\(")
INVOKE_ANNOTATION = re.compile(r'target\s*=\s*"L([^;]+);([^("]+)(\([^"]*)"')
STRING_LITERAL = re.compile(r"\"([^\"]*)\"")
ACCESSOR = re.compile(r"@Accessor\s*\(\s*\"([^\"]+)\"\s*\)")
INVOKER = re.compile(r"@Invoker\s*\(\s*\"([^\"]+)\"\s*\)")
INVOKE_TARGET = re.compile(r"target\s*=\s*\"L([^;]+);([^(\"]+)\(")
IMPORT = re.compile(r"^import\s+(?:static\s+)?([\w.]+);", re.M)


class JarIndex:
    """Method and field names of every class inside the given Minecraft jars."""

    def __init__(self, jars: list[Path]) -> None:
        self.jars = jars
        self.classpath = os.pathsep.join(str(jar) for jar in jars)
        self.classes: set[str] = set()
        for jar in jars:
            with zipfile.ZipFile(jar) as archive:
                self.classes |= {
                    name[:-6].replace("/", ".")
                    for name in archive.namelist()
                    if name.endswith(".class")
                }
        self._members: dict[str, set[str]] = {}
        self._bodies: dict[str, dict[str, str]] = {}

    def has_class(self, binary_name: str) -> bool:
        return binary_name in self.classes

    def members(self, binary_name: str) -> set[str]:
        cached = self._members.get(binary_name)
        if cached is not None:
            return cached
        names: set[str] = set()
        for owner in self._hierarchy(binary_name):
            names |= self._declared(owner)
        self._members[binary_name] = names
        return names

    def _hierarchy(self, binary_name: str) -> list[str]:
        chain = [binary_name]
        seen = {binary_name}
        index = 0
        while index < len(chain):
            for parent in self._supertypes(chain[index]):
                if parent in self.classes and parent not in seen:
                    seen.add(parent)
                    chain.append(parent)
            index += 1
        return chain

    def method_bodies(self, binary_name: str, method_name: str) -> str:
        """Disassembled bytecode of every method of that name, concatenated."""
        cached = self._bodies.get(binary_name)
        if cached is None:
            cached = self._disassemble(binary_name)
            self._bodies[binary_name] = cached
        return cached.get(method_name, "")

    def _disassemble(self, binary_name: str) -> dict[str, str]:
        result = subprocess.run(
            ["javap", "-c", "-p", "-cp", self.classpath, binary_name],
            capture_output=True,
            text=True,
        )
        bodies: dict[str, list[str]] = {}
        current = None
        declaration = ""
        for line in result.stdout.splitlines():
            if line.startswith("  ") and not line.startswith("    "):
                declaration = line.strip()
            elif declaration and not line.startswith("    Code:"):
                declaration += line.strip()

            if declaration and declaration.endswith(";"):
                if "(" in declaration:
                    signature = declaration.split("(", 1)[0]
                    current = signature.split()[-1].rsplit(".", 1)[-1]
                    bodies.setdefault(current, [])
                else:
                    current = None
                declaration = ""
            elif current is not None and not declaration:
                bodies[current].append(line)
        return {name: chr(10).join(lines) for name, lines in bodies.items()}

    def _javap(self, binary_name: str) -> str:
        result = subprocess.run(
            ["javap", "-p", "-cp", self.classpath, binary_name],
            capture_output=True,
            text=True,
        )
        return result.stdout if result.returncode == 0 else ""

    def _declared(self, binary_name: str) -> set[str]:
        names: set[str] = set()
        declaration = ""
        for raw_line in self._javap(binary_name).splitlines():
            stripped = raw_line.strip()
            if not declaration:
                if not raw_line.startswith("  ") or raw_line.startswith("    "):
                    continue
                declaration = stripped
            else:
                declaration += stripped
            if not declaration.endswith(";"):
                continue
            line = declaration[:-1]
            declaration = ""
            if "(" in line:
                signature = line.split("(", 1)[0]
                member = signature.split()[-1]
                names.add(member.rsplit(".", 1)[-1])
            else:
                parts = line.split()
                if parts:
                    names.add(parts[-1])
        return names

    @staticmethod
    def _without_type_arguments(header: str) -> str:
        """Drops every <...> group, nesting included.

        A generic parameter list carries its own bounds, so `class Foo<R extends
        Runnable> extends Bar<R>` contains the word `extends` twice. Splitting the
        raw header on the first one reads `Runnable` as the superclass and loses
        the real chain, which made inherited members look absent.
        """
        result = []
        depth = 0
        for character in header:
            if character == "<":
                depth += 1
            elif character == ">":
                depth = max(0, depth - 1)
            elif depth == 0:
                result.append(character)
        return "".join(result)

    def _supertypes(self, binary_name: str) -> list[str]:
        header = ""
        for line in self._javap(binary_name).splitlines():
            if "class " in line or "interface " in line:
                header = self._without_type_arguments(line)
                break
        parents: list[str] = []
        # Matched instead of split on the keyword: `extends A implements B` would
        # otherwise yield the single token `A implements B`, and the chain stopped
        # at the first class with both clauses.
        for keyword in ("extends", "implements"):
            match = re.search(keyword + r"\s+(?P<names>[\w.$]+(?:\s*,\s*[\w.$]+)*)", header)
            if match:
                parents += [name.strip() for name in match.group("names").split(",")]
        return parents


def resolve_imports(source: str) -> dict[str, str]:
    resolved = {}
    for fqn in IMPORT.findall(source):
        resolved[fqn.rsplit(".", 1)[-1]] = fqn
    return resolved


def mixin_targets(source: str) -> list[str]:
    match = MIXIN_ANNOTATION.search(source)
    if not match:
        return []
    body = match.group("body")
    imports = resolve_imports(source)
    targets = [imports.get(name, name) for name in CLASS_LITERAL.findall(body)]
    targets += TARGETS_LITERAL.findall(body)
    return targets


def injector_blocks(source: str) -> list[tuple[list[str], list[tuple[str, str, str]]]]:
    """Pairs each injector's target methods with its INVOKE injection points."""
    blocks: list[tuple[list[str], list[tuple[str, str, str]]]] = []
    for match in INJECTOR.finditer(source):
        depth = 0
        end = match.end() - 1
        for index in range(match.end() - 1, len(source)):
            character = source[index]
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
                if depth == 0:
                    end = index
                    break
        body = source[match.end():end]
        methods = []
        for raw in METHOD_REFERENCE.findall(body):
            methods += [literal.split("(", 1)[0] for literal in STRING_LITERAL.findall(raw)]
        invocations = [
            (owner.replace("/", "."), member, arguments)
            for owner, member, arguments in INVOKE_ANNOTATION.findall(body)
        ]
        if methods and invocations:
            blocks.append((methods, invocations))
    return blocks


def referenced_methods(source: str) -> list[str]:
    names: list[str] = []
    for raw in METHOD_REFERENCE.findall(source):
        for literal in STRING_LITERAL.findall(raw):
            names.append(literal.split("(", 1)[0])
    names += ACCESSOR.findall(source)
    names += INVOKER.findall(source)
    return names


def verify(source_root: Path, jars: list[Path]) -> int:
    index = JarIndex(jars)
    mixin_dir = source_root / "src/client/java/dev/zymekoh/kohsinventorytweaks/mixin"
    if not mixin_dir.is_dir():
        # An overlay tree carries only metadata and compiles another tree's client
        # sources, so the mixins to check are the ones its build script points at.
        build_script = source_root / "build.gradle"
        upstream = None
        if build_script.is_file():
            match = re.search(r"java\.setSrcDirs\(\['([^']+)'\]\)", build_script.read_text(encoding="utf-8"))
            if match:
                upstream = (source_root / match.group(1)).resolve()
        if upstream is None or not upstream.is_dir():
            print(f"no mixin package under {mixin_dir}", file=sys.stderr)
            return 2
        print(f"sources: {upstream} (overlay)")
        mixin_dir = upstream / "dev/zymekoh/kohsinventorytweaks/mixin"

    failures: list[str] = []
    checked_methods = 0
    checked_invocations = 0
    for path in sorted(mixin_dir.glob("*.java")):
        source = path.read_text(encoding="utf-8")
        if "@Pseudo" in source:
            print(f"  skip  {path.name} (@Pseudo, foreign target)")
            continue

        targets = mixin_targets(source)
        if not targets:
            failures.append(f"{path.name}: no @Mixin target found")
            continue

        missing_target = [name for name in targets if not index.has_class(name)]
        if missing_target:
            failures.append(f"{path.name}: missing target class {missing_target}")
            continue

        available: set[str] = set()
        for target in targets:
            available |= index.members(target)

        for method in referenced_methods(source):
            checked_methods += 1
            if method not in available:
                failures.append(f"{path.name}: {targets} has no member '{method}'")

        for methods, invocations in injector_blocks(source):
            for owner_name, member, arguments in invocations:
                checked_invocations += 1
                if not index.has_class(owner_name):
                    failures.append(f"{path.name}: INVOKE owner {owner_name} is missing")
                    continue
                if member not in index.members(owner_name):
                    failures.append(f"{path.name}: {owner_name} has no method '{member}'")
                    continue
                # The call has to exist inside the very method being injected into,
                # otherwise Mixin refuses to apply the injector at load time.
                qualified = f"{owner_name.rsplit('.', 1)[-1]}.{member}:{arguments}"
                plain = f"Method {member}:{arguments}"
                found = False
                for target in targets:
                    for host in methods:
                        body = index.method_bodies(target, host)
                        if qualified in body or plain in body:
                            found = True
                            break
                    if found:
                        break
                if not found:
                    failures.append(
                        f"{path.name}: {methods} never calls {owner_name}.{member}"
                    )

        print(f"  ok    {path.name} -> {', '.join(t.rsplit('.', 1)[-1] for t in targets)}")

    print()
    print(f"checked {checked_methods} mixin member references and {checked_invocations} INVOKE points")
    if failures:
        print()
        for failure in failures:
            print(f"FAIL  {failure}")
        return 1
    print("every mixin reference resolves against", ", ".join(jar.name for jar in jars))
    return 0


def has_named_classes(jar: Path) -> bool:
    with zipfile.ZipFile(jar) as archive:
        return "net/minecraft/client/Minecraft.class" in archive.namelist()


def default_jars(source_root: Path, override: str | None = None) -> list[Path]:
    """Locates the named Minecraft jars Loom compiled this tree against.

    Minecraft 26.x ships deobfuscated, so its plain client jar already carries
    the runtime names. Earlier versions are obfuscated, and Loom keeps the
    named jars mixins are written against under its minecraftMaven cache.
    """
    properties = (source_root / "gradle.properties").read_text(encoding="utf-8")
    # Single-version trees declare minecraft_version; the multi-version trees
    # declare the range's default as target_minecraft_version instead.
    declared = re.search(r"^(?:minecraft|target_minecraft)_version\s*=\s*(\S+)", properties, re.M)
    if override is None and declared is None:
        return []
    # A range tree declares one default target but has to apply on every version
    # of its range, so the caller can ask for any of them.
    version = override or declared.group(1)
    loom = Path(os.path.expanduser("~")) / ".gradle/caches/fabric-loom"

    client = loom / version / "minecraft-client.jar"
    if client.is_file() and has_named_classes(client):
        return [client]

    maven = loom / "minecraftMaven/net/minecraft"
    # The directory is `<version>-<mapping>`, and a plain prefix glob for 1.21.1
    # also matches 1.21.10 and 1.21.11, which would verify a tree against the
    # wrong Minecraft entirely.
    builds = [
        build for build in (maven / "minecraft-clientonly").glob(f"{version}-*")
        if build.name.split("-", 1)[0] == version
    ]
    for build in sorted(builds, reverse=True):
        for jar in sorted(build.glob("*.jar")):
            if "sources" in jar.name or not has_named_classes(jar):
                continue
            # The client-only jar carries the GUI classes; its sibling common jar,
            # built from the same mapping set, carries ItemStack and friends.
            common = maven / "minecraft-common" / build.name / jar.name.replace(
                "minecraft-clientonly", "minecraft-common", 1
            )
            return [jar, common] if common.is_file() else [jar]
    return []


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", default=".", type=Path)
    parser.add_argument("--minecraft-jar", type=Path)
    parser.add_argument(
        "--minecraft-version",
        help="verify against this Minecraft version instead of the tree's default",
    )
    arguments = parser.parse_args()

    source_root = arguments.source_root.resolve()
    jars = (
        [arguments.minecraft_jar]
        if arguments.minecraft_jar
        else default_jars(source_root, arguments.minecraft_version)
    )
    jars = [jar for jar in jars if jar.is_file()]
    if not jars:
        print("No named Minecraft jar found in the Loom cache.", file=sys.stderr)
        print("Run a Gradle build for this tree first.", file=sys.stderr)
        return 2

    print(f"source : {source_root}")
    for jar in jars:
        print(f"client : {jar}")
    print()
    return verify(source_root, jars)


if __name__ == "__main__":
    raise SystemExit(main())
