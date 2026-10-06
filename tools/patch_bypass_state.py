#!/usr/bin/env python3
"""Inject the recompiled BypassHelper classes into app/tool.jar.

This patch replaces avt/BypassHelper*.class with freshly compiled versions
that add three improvements:
  1. Gộp luồng log - stoppedBotIndices chặn log từ enqueued work sau khi stop
  2. Dừng thật sự - stopBotAtIndex đánh dấu index ngay lập tức qua stoppedBotIndices
  3. Lưu trữ thông tin - fishCountMap/missionDetailMap persist vào data/bot-state.json

Usage:
    python3 tools/patch_bypass_state.py               # patch app/tool.jar in-place
    python3 tools/patch_bypass_state.py --check       # verify patch is applied
    python3 tools/patch_bypass_state.py path/to.jar   # patch a specific jar
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Dict, List, Optional, Sequence

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JAR = ROOT / "app" / "tool.jar"
SOURCE_FILE = ROOT / "scratch" / "BypassHelper.java"
COMPILE_TMP = ROOT / ".compile_bypass_tmp"

# Classes produced by compiling BypassHelper.java
BYPASS_CLASS_PREFIX = "avt/BypassHelper"


class PatchError(ValueError):
    """Raised when the patch cannot be applied."""


def _find_javac() -> str:
    """Return path to javac, preferring JDK over JRE."""
    # Try system PATH first
    for candidate in ("javac", "javac.exe"):
        try:
            subprocess.run([candidate, "-version"], capture_output=True, check=True)
            return candidate
        except (FileNotFoundError, subprocess.CalledProcessError):
            pass
    raise PatchError(
        "javac not found. Install a JDK and ensure javac is on your PATH."
    )


def _compile_bypass_helper(jar_path: Path) -> Dict[str, bytes]:
    """Compile BypassHelper.java against the given JAR and return class bytes."""
    javac = _find_javac()
    COMPILE_TMP.mkdir(parents=True, exist_ok=True)

    # Copy source to compile dir (javac wants it in the right place)
    src_copy = COMPILE_TMP / "BypassHelper.java"
    src_copy.write_bytes(SOURCE_FILE.read_bytes())

    result = subprocess.run(
        [
            javac,
            "--release", "8",
            "-cp", str(jar_path),
            "-d", str(COMPILE_TMP),
            str(src_copy),
        ],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise PatchError(
            f"javac failed to compile BypassHelper.java:\n"
            f"{result.stderr}\n{result.stdout}"
        )

    # Collect all generated class files
    avt_dir = COMPILE_TMP / "avt"
    if not avt_dir.exists():
        raise PatchError("Compilation produced no output in avt/ directory")

    classes: Dict[str, bytes] = {}
    for class_file in avt_dir.glob("BypassHelper*.class"):
        jar_key = f"avt/{class_file.name}"
        classes[jar_key] = class_file.read_bytes()

    if not classes:
        raise PatchError("Compilation produced no BypassHelper*.class files")

    print(f"  Compiled {len(classes)} class file(s): {sorted(classes)}")
    return classes


def _has_stopped_bot_indices_field(class_bytes: bytes) -> bool:
    """Quick heuristic: check if 'stoppedBotIndices' UTF-8 is in the constant pool."""
    return b"stoppedBotIndices" in class_bytes


def patch_jar(
    source: Path,
    destination: Optional[Path] = None,
    check_only: bool = False,
) -> int:
    """Apply (or verify) the BypassHelper state-persistence patch.

    Returns the number of classes patched (0 if already up-to-date).
    """
    destination = destination or source

    with zipfile.ZipFile(source, "r") as archive:
        entries = archive.infolist()
        contents: Dict[str, bytes] = {
            e.filename: archive.read(e.filename) for e in entries
        }
        archive_comment = archive.comment

    main_class_key = "avt/BypassHelper.class"
    if main_class_key not in contents:
        raise PatchError(f"{source} does not contain {main_class_key}")

    # Check if already patched
    already_patched = _has_stopped_bot_indices_field(contents[main_class_key])

    if check_only:
        if not already_patched:
            raise PatchError(
                f"{source} is missing the stoppedBotIndices state-persistence patch"
            )
        return 0

    if already_patched and source.resolve() == destination.resolve():
        return 0

    # Compile fresh classes
    print(f"Compiling {SOURCE_FILE.name} against {source.name}...")
    new_classes = _compile_bypass_helper(source)

    # Remove old BypassHelper classes and insert new ones
    old_keys = [k for k in contents if k.startswith(BYPASS_CLASS_PREFIX)]
    for k in old_keys:
        del contents[k]
    contents.update(new_classes)

    # Rebuild JAR
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        prefix=f".{destination.name}.",
        suffix=".tmp",
        dir=destination.parent,
        delete=False,
    ) as tmp_f:
        tmp_path = Path(tmp_f.name)

    try:
        # Build a name -> ZipInfo lookup so we preserve metadata for existing entries
        info_by_name = {e.filename: e for e in entries}
        with zipfile.ZipFile(tmp_path, "w") as out:
            out.comment = archive_comment
            for key, data in contents.items():
                if key in info_by_name:
                    entry = info_by_name[key]
                    out.writestr(entry, data, compress_type=entry.compress_type)
                else:
                    # New class file added by compilation
                    out.writestr(key, data, compress_type=zipfile.ZIP_DEFLATED)
        os.replace(tmp_path, destination)
    finally:
        if tmp_path.exists():
            tmp_path.unlink()

    return len(new_classes)


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "jar", nargs="?", type=Path, default=DEFAULT_JAR,
        help="Path to tool.jar (default: app/tool.jar)"
    )
    parser.add_argument(
        "--check", action="store_true",
        help="Verify the patch is applied without modifying the JAR"
    )
    parser.add_argument(
        "--output", type=Path,
        help="Write to a different JAR instead of patching in place"
    )
    args = parser.parse_args(argv)

    try:
        count = patch_jar(args.jar, args.output, check_only=args.check)
    except (PatchError, OSError, zipfile.BadZipFile) as err:
        print(f"error: {err}", file=sys.stderr)
        return 1

    if args.check:
        print(f"OK: {args.jar} has the state-persistence patch (stoppedBotIndices present)")
    elif count:
        print(
            f"Patched {count} BypassHelper class file(s) in {args.output or args.jar}\n"
            f"  + fishCount/mission state persists to data/bot-state.json on exit\n"
            f"  + stoppedBotIndices blocks queued log lines after Stop\n"
            f"  + registerActiveBot clears stop flag when bot restarts"
        )
    else:
        print(f"No changes needed: {args.output or args.jar}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
