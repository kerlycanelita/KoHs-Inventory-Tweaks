#!/usr/bin/env python3
"""Check the five maintained release JARs without launching Minecraft.

Run after Gradle build and verify-mixin-targets.py for each target. This checks
packaging and source-port invariants, not gameplay or rendering correctness.
"""
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path

TARGETS = ("1.21.11", "26.1", "26.1.1", "26.1.2", "26.2")
PACKAGE = "src/client/java/dev/zymekoh/kohsinventorytweaks"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def source(tree, path):
    return (tree / PACKAGE / path).read_text(encoding="utf-8")


def verify(repo, release):
    records = []
    for mc in TARGETS:
        tree = repo if mc == "26.1.2" else repo / "versions" / f"kohs-inventory-tweaks-{mc}"
        filename = f"kohs-inventory-tweaks-{release}+mc{mc}.jar"
        jar = tree / "build/libs" / filename
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            require(archive.testzip() is None, f"{mc}: corrupt ZIP entry")
            metadata = json.loads(archive.read("fabric.mod.json"))
            require(metadata["id"] == "kohs_inventory_tweaks", f"{mc}: wrong mod")
            require(metadata["version"] == f"{release}+mc{mc}", f"{mc}: wrong version")
            require(metadata["environment"] == "client", f"{mc}: wrong environment")
            require(metadata["license"] == "MIT", f"{mc}: wrong license")
            require(metadata["depends"]["minecraft"] == mc, f"{mc}: wrong Minecraft dependency")
            require(metadata["icon"] in names, f"{mc}: missing icon")
            require(archive.read(metadata["icon"]).startswith(b"\x89PNG\r\n\x1a\n"), f"{mc}: invalid PNG")
            require(any("LICENSE" in n and b"MIT License" in archive.read(n)
                        for n in names if not n.endswith("/")), f"{mc}: MIT text not packaged")
            require(not any("kohsinventorydebug" in n or "kohs_inventory_debug" in n for n in names),
                    f"{mc}: diagnostic companion leaked into release")
            for config in metadata["mixins"]:
                config_name = config if isinstance(config, str) else config["config"]
                mixins = json.loads(archive.read(config_name))
                for name in mixins.get("client", []) + mixins.get("mixins", []):
                    class_name = (mixins["package"] + "." + name).replace(".", "/") + ".class"
                    require(class_name in names, f"{mc}: mixin class {name} missing")
            classes = [n for n in names if n.startswith("dev/zymekoh/") and n.endswith(".class")]
            expected_major = 65 if mc == "1.21.11" else 69
            require(all(struct.unpack(">H", archive.read(n)[6:8])[0] == expected_major for n in classes),
                    f"{mc}: wrong Java target")
            lang = "assets/kohs_inventory_tweaks/lang/"
            spanish = json.loads(archive.read(lang + "es_es.json"))
            for locale in ("es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve"):
                require(json.loads(archive.read(lang + locale + ".json")) == spanish,
                        f"{mc}: divergent Spanish locale {locale}")

        scaler = source(tree, "inventory/InventoryGuiScaler.java")
        require("player.getRecipeBook().isOpen" in scaler, f"{mc}: recipe-book state port absent")
        require("hasActiveSurfaceScope" in scaler, f"{mc}: scale ownership absent")
        off_branch = scaler.split("public static double configuredContainerScale(", 1)[1].split(
            "return toSurfaceScale", 1)[0]
        require("!config.inventoryGuiScalerEnabled" in off_branch and "return 1.0;" in off_branch,
                f"{mc}: OFF does not return an identity transform")
        ui = source(tree, "screen/InventoryTweaksScreen.java")
        require("persistCustomizationSlider" in ui, f"{mc}: slider release persistence absent")
        require(ui.count("this.drawFastOpenStatus(graphics);") == 1, f"{mc}: duplicate/missing status")
        config = source(tree, "config/InventoryTweaksConfig.java")
        require("DEFAULT_INVENTORY_GUI_SCALE = 2.00;" in config
                and "inventoryGuiScale = DEFAULT_INVENTORY_GUI_SCALE;" in config
                and "affectAllContainers = true" in config,
                f"{mc}: new-install defaults changed")
        data = jar.read_bytes()
        records.append({"minecraft": mc, "filename": filename, "size": len(data),
                        "sha256": hashlib.sha256(data).hexdigest(),
                        "sha512": hashlib.sha512(data).hexdigest(),
                        "path": jar.relative_to(repo).as_posix()})
    return records


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", required=True)
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1], args.release), indent=2))
