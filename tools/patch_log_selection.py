#!/usr/bin/env python3
"""Keep the account selected for log viewing stable across periodic refreshes.

`avt.DebugToDeath` is shipped as a prebuilt, obfuscated JavaFX class. This
patch records a local log-view index when an account row is clicked, then uses
that index for both the displayed log and its per-account fallback data. The
normal application refresh continues for statuses and account data.

Usage:
    python3 tools/patch_log_selection.py                 # patch app/tool.jar
    python3 tools/patch_log_selection.py --check         # verify only
    python3 tools/patch_log_selection.py path/to/tool.jar
"""
from __future__ import annotations

import argparse
import os
import struct
import tempfile
import zipfile
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

from patch_stop_flag import (
    ClassFormatError,
    ConstantPool,
    CpEntry,
    Instruction,
    Reader,
    _map_pc,
    _rewrite_line_numbers,
    _rewrite_local_variables,
    _rewrite_stack_map,
    decode_instructions,
)

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JAR = ROOT / "app" / "tool.jar"
CONTROLLER = "avt/DebugToDeath.class"

# At these instruction offsets, the top of the stack is the clicked row index
# (or, for buildTable, it has just been saved to local 3).
ROW_INSERTIONS = {
    ("lambda$buildTable$15", "(Ljavafx/scene/control/TableRow;Ljavafx/scene/input/MouseEvent;)V"): 18,
    ("lambda$accountRowNode$4", "(Lavt/n;Ljavafx/scene/input/MouseEvent;)V"): 5,
}

# m() is the periodic renderer; J() chooses the account row used as log fallback.
RENDER_METHOD = ("m", "([Ljava/lang/Object;)V")
ACCOUNT_METHOD = ("J", "([Ljava/lang/Object;)Lorg/json/simple/S;")
RENDER_INSERTION_PC = 812
ACCOUNT_INSERTION_PC = 42


def _member_ref(pool: ConstantPool, tag: int, owner: str, name: str, descriptor: str) -> int:
    class_index = pool.add_class(owner)
    name_and_type_index = pool.add_name_and_type(name, descriptor)
    value = (class_index, name_and_type_index)
    for index, entry in enumerate(pool.entries):
        if entry is not None and entry.tag == tag and entry.value == value:
            return index
    raw = bytes((tag,)) + struct.pack(">HH", *value)
    return pool._append(CpEntry(tag, raw, value))


def _refs(pool: ConstantPool) -> Dict[str, int]:
    return {
        "map_field": _member_ref(pool, 9, "avt/DebugToDeath", "M", "Ljava/util/Map;"),
        "map_put": _member_ref(
            pool, 11, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        ),
        "map_get_or_default": _member_ref(
            pool, 11, "java/util/Map", "getOrDefault",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        ),
        "integer_class": pool.add_class("java/lang/Integer"),
        "integer_value_of": pool.add_methodref(
            "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"
        ),
        "integer_int_value": pool.add_methodref("java/lang/Integer", "intValue", "()I"),
    }


def _invokeinterface(index: int, argument_slots: int) -> bytes:
    return b"\xb9" + struct.pack(">HBB", index, argument_slots, 0)


def _pin_row_code(refs: Dict[str, int], local_index: Optional[int]) -> bytes:
    """Record -1 -> clicked row index in the existing per-row log cache map."""
    code = bytearray()
    if local_index is None:
        # Stack is [this, index]. Save the index temporarily in local 3, leaving
        # `this` on the stack for the original handler body.
        code.extend(b"\x3e")  # istore_3
        code.extend(b"\x59")  # dup
    else:
        code.extend(b"\x2a")  # aload_0
        code.extend(b"\xb4" + struct.pack(">H", refs["map_field"]))
        code.extend(b"\x02")  # iconst_m1
        code.extend(b"\xb8" + struct.pack(">H", refs["integer_value_of"]))
        code.extend(bytes((0x1A + local_index,)))  # iload_n (used with local 3 here)
        code.extend(b"\xb8" + struct.pack(">H", refs["integer_value_of"]))
        code.extend(_invokeinterface(refs["map_put"], 3))
        code.extend(b"\x57")  # pop Map.put's previous value
        return bytes(code)

    # The account-card handler still has `this` on the stack after istore_3.
    code.extend(b"\xb4" + struct.pack(">H", refs["map_field"]))
    code.extend(b"\x02")  # iconst_m1
    code.extend(b"\xb8" + struct.pack(">H", refs["integer_value_of"]))
    code.extend(b"\x1d")  # iload_3
    code.extend(b"\xb8" + struct.pack(">H", refs["integer_value_of"]))
    code.extend(_invokeinterface(refs["map_put"], 3))
    code.extend(b"\x57")  # pop Map.put's previous value
    code.extend(b"\x1d")  # reload the index expected by the original handler
    return bytes(code)


def _selected_index_code(refs: Dict[str, int], local_index: int) -> bytes:
    """Replace an active index with the pinned log index, if one was recorded."""
    code = bytearray()
    code.extend(b"\x2a")  # aload_0
    code.extend(b"\xb4" + struct.pack(">H", refs["map_field"]))
    code.extend(b"\x02")  # iconst_m1
    code.extend(b"\xb8" + struct.pack(">H", refs["integer_value_of"]))

    if local_index <= 3:
        code.append(0x1A + local_index)  # iload_0..iload_3
    else:
        code.extend(b"\xc4\x15" + struct.pack(">H", local_index))  # wide iload
    code.extend(b"\xb8" + struct.pack(">H", refs["integer_value_of"]))
    code.extend(_invokeinterface(refs["map_get_or_default"], 3))
    code.extend(b"\xc0" + struct.pack(">H", refs["integer_class"]))  # checkcast Integer
    code.extend(b"\xb6" + struct.pack(">H", refs["integer_int_value"]))

    if local_index <= 3:
        code.append(0x3B + local_index)  # istore_0..istore_3
    else:
        code.extend(b"\xc4\x36" + struct.pack(">H", local_index))  # wide istore
    return bytes(code)


def _relocate_code(code: bytes, insertions: Dict[int, bytes]) -> Tuple[bytes, Dict[int, int]]:
    instructions = decode_instructions(code)
    boundaries = {ins.pc for ins in instructions} | {len(code)}
    if not set(insertions).issubset(boundaries - {len(code)}):
        raise ClassFormatError("log-selection insertion is not at an instruction boundary")

    positions: Dict[int, int] = {}
    new_pc = 0
    for instruction in instructions:
        new_pc += len(insertions.get(instruction.pc, b""))
        positions[instruction.pc] = new_pc
        if instruction.opcode in (0xAA, 0xAB):
            padding = (4 - ((new_pc + 1) % 4)) % 4
            if instruction.opcode == 0xAA:
                _, _, pairs = instruction.switch_data  # type: ignore[misc]
                new_size = 1 + padding + 12 + 4 * len(pairs)
            else:
                _, _, pairs = instruction.switch_data  # type: ignore[misc]
                new_size = 1 + padding + 8 + 8 * len(pairs)
        else:
            new_size = len(instruction.raw)
        new_pc += new_size
    new_pc += len(insertions.get(len(code), b""))
    positions[len(code)] = new_pc

    output = bytearray()
    for instruction in instructions:
        output.extend(insertions.get(instruction.pc, b""))
        current_pc = positions[instruction.pc]
        if instruction.opcode in (0xAA, 0xAB):
            default_target, marker, pairs = instruction.switch_data  # type: ignore[misc]
            padding = (4 - ((current_pc + 1) % 4)) % 4
            output.append(instruction.opcode)
            output.extend(b"\x00" * padding)
            output.extend(struct.pack(">i", _map_pc(default_target, positions) - current_pc))
            if instruction.opcode == 0xAA:
                low = marker
                high = low + len(pairs) - 1
                output.extend(struct.pack(">ii", low, high))
                for _, target in pairs:
                    output.extend(struct.pack(">i", _map_pc(target, positions) - current_pc))
            else:
                output.extend(struct.pack(">i", len(pairs)))
                for key, target in pairs:
                    output.extend(struct.pack(">ii", key, _map_pc(target, positions) - current_pc))
            continue

        raw = bytearray(instruction.raw)
        if instruction.branch_targets:
            target = instruction.branch_targets[0]
            delta = _map_pc(target, positions) - current_pc
            if instruction.opcode in (0xC8, 0xC9):
                raw[1:5] = struct.pack(">i", delta)
            else:
                if not -0x8000 <= delta <= 0x7FFF:
                    raise ClassFormatError("relocated short branch is out of range")
                raw[1:3] = struct.pack(">h", delta)
        output.extend(raw)

    output.extend(insertions.get(len(code), b""))
    if len(output) != new_pc:
        raise ClassFormatError("log-selection bytecode relocation length mismatch")
    return bytes(output), positions


def _rewrite_code_attribute(info: bytes, pool: ConstantPool,
                            insertions: Dict[int, bytes]) -> bytes:
    reader = Reader(info)
    max_stack, max_locals = reader.u2(), reader.u2()
    old_code = reader.take(reader.u4())
    new_code, positions = _relocate_code(old_code, insertions)
    max_stack = max(max_stack, 3)

    exception_count = reader.u2()
    exceptions = []
    for _ in range(exception_count):
        start_pc, end_pc, handler_pc, catch_type = reader.u2(), reader.u2(), reader.u2(), reader.u2()
        exceptions.append((start_pc, end_pc, handler_pc, catch_type))

    nested_count = reader.u2()
    nested_attributes: List[Tuple[int, bytes]] = []
    for _ in range(nested_count):
        name_index, length = reader.u2(), reader.u4()
        attribute_info = reader.take(length)
        name = pool.utf8(name_index)
        if name == "StackMapTable":
            attribute_info = _rewrite_stack_map(attribute_info, positions)
        elif name == "LineNumberTable":
            attribute_info = _rewrite_line_numbers(attribute_info, positions)
        elif name in ("LocalVariableTable", "LocalVariableTypeTable"):
            attribute_info = _rewrite_local_variables(attribute_info, positions)
        else:
            raise ClassFormatError(
                f"cannot safely relocate code attribute {name!r} in a patched controller method"
            )
        nested_attributes.append((name_index, attribute_info))

    if reader.pos != len(info):
        raise ClassFormatError("trailing bytes in controller Code attribute")

    output = bytearray(struct.pack(">HHI", max_stack, max_locals, len(new_code)))
    output.extend(new_code)
    output.extend(struct.pack(">H", exception_count))
    for start_pc, end_pc, handler_pc, catch_type in exceptions:
        output.extend(struct.pack(">HHHH", _map_pc(start_pc, positions),
                                  _map_pc(end_pc, positions),
                                  _map_pc(handler_pc, positions), catch_type))
    output.extend(struct.pack(">H", len(nested_attributes)))
    for name_index, attribute_info in nested_attributes:
        output.extend(struct.pack(">HI", name_index, len(attribute_info)))
        output.extend(attribute_info)
    return bytes(output)


def patch_class(data: bytes) -> Tuple[bytes, int]:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ClassFormatError(f"{CONTROLLER} is not a class file")

    reader = Reader(data)
    reader.take(8)
    pool = ConstantPool.parse(data, reader)
    refs = _refs(pool)
    output_body = bytearray(reader.take(6))  # access, this_class, super_class
    interfaces_count = reader.u2()
    output_body.extend(struct.pack(">H", interfaces_count))
    output_body.extend(reader.take(2 * interfaces_count))

    fields_count = reader.u2()
    output_body.extend(struct.pack(">H", fields_count))
    for _ in range(fields_count):
        start = reader.pos
        reader.take(6)
        for _ in range(reader.u2()):
            reader.take(2)
            reader.take(reader.u4())
        output_body.extend(data[start:reader.pos])

    methods_count = reader.u2()
    output_body.extend(struct.pack(">H", methods_count))
    seen = set()
    patched = 0
    for _ in range(methods_count):
        access = reader.u2()
        name_index, descriptor_index = reader.u2(), reader.u2()
        method_key = (pool.utf8(name_index), pool.utf8(descriptor_index))
        attr_count = reader.u2()
        output_body.extend(struct.pack(">HHHH", access, name_index, descriptor_index, attr_count))
        insertions: Dict[int, bytes] = {}
        if method_key in ROW_INSERTIONS:
            # The table handler already put the clicked index in local 3. The
            # account-card handler has it on the operand stack at PC 5.
            insertions = {
                ROW_INSERTIONS[method_key]: _pin_row_code(
                    refs, 3 if method_key[0] == "lambda$buildTable$15" else None
                )
            }
        elif method_key == RENDER_METHOD:
            insertions = {RENDER_INSERTION_PC: _selected_index_code(refs, 8)}
        elif method_key == ACCOUNT_METHOD:
            insertions = {ACCOUNT_INSERTION_PC: _selected_index_code(refs, 2)}

        for _ in range(attr_count):
            attr_name_index, length = reader.u2(), reader.u4()
            info = reader.take(length)
            attr_name = pool.utf8(attr_name_index)
            if attr_name == "Code" and insertions:
                code_reader = Reader(info)
                code_reader.take(4)
                old_code = code_reader.take(code_reader.u4())
                has_patch = False
                for instruction in decode_instructions(old_code):
                    if instruction.opcode != 0xB9:
                        continue
                    cp_index = int.from_bytes(instruction.raw[1:3], "big")
                    entry = pool.entries[cp_index]
                    if entry is None or entry.tag != 11:
                        continue
                    class_index, nt_index = entry.value
                    class_entry, nt_entry = pool.entries[class_index], pool.entries[nt_index]
                    if class_entry is None or nt_entry is None:
                        continue
                    owner = pool.utf8(class_entry.value)
                    name = pool.utf8(nt_entry.value[0])
                    descriptor = pool.utf8(nt_entry.value[1])
                    if owner == "java/util/Map" and name in ("put", "getOrDefault"):
                        if method_key in ROW_INSERTIONS and name == "put":
                            has_patch = True
                        elif method_key in (RENDER_METHOD, ACCOUNT_METHOD) and name == "getOrDefault":
                            has_patch = True
                if has_patch:
                    info = info
                else:
                    info = _rewrite_code_attribute(info, pool, insertions)
                    patched += 1
                    seen.add(method_key)
            output_body.extend(struct.pack(">HI", attr_name_index, len(info)))
            output_body.extend(info)

    if methods_count <= 0:
        raise ClassFormatError("controller class has no methods")
    if reader.pos != len(data):
        # Class-level attributes follow. Copy them unchanged.
        output_body.extend(data[reader.pos:])
        reader.pos = len(data)

    required = set(ROW_INSERTIONS) | {RENDER_METHOD, ACCOUNT_METHOD}
    # On an idempotent check, all target methods are expected to already have
    # their marker call; on a fresh patch they are all rewritten above.
    for key in required:
        if key not in [(name, desc) for name, desc in _method_keys(data)]:
            raise ClassFormatError(f"{CONTROLLER}: missing expected method {key}")

    result = data[:8] + pool.to_bytes() + bytes(output_body)
    return result, patched


def _method_keys(data: bytes) -> List[Tuple[str, str]]:
    reader = Reader(data)
    reader.take(8)
    pool = ConstantPool.parse(data, reader)
    reader.take(6)
    reader.take(reader.u2() * 2)
    for _ in range(reader.u2()):
        reader.take(6)
        for _ in range(reader.u2()):
            reader.take(2)
            reader.take(reader.u4())
    keys = []
    for _ in range(reader.u2()):
        reader.take(2)
        keys.append((pool.utf8(reader.u2()), pool.utf8(reader.u2())))
        for _ in range(reader.u2()):
            reader.take(2)
            reader.take(reader.u4())
    return keys


def patch_jar(source: Path, destination: Optional[Path] = None,
              check_only: bool = False) -> int:
    destination = destination or source
    with zipfile.ZipFile(source, "r") as archive:
        entries = archive.infolist()
        contents = {entry.filename: archive.read(entry.filename) for entry in entries}
        archive_comment = archive.comment

    if CONTROLLER not in contents:
        raise ClassFormatError(f"{source} does not contain {CONTROLLER}")
    patched_class, count = patch_class(contents[CONTROLLER])
    contents[CONTROLLER] = patched_class
    if check_only:
        if count:
            raise ClassFormatError(f"{source} still needs {count} log-selection patch(es)")
        return 0
    if count == 0 and source.resolve() == destination.resolve():
        return 0

    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix=f".{destination.name}.", suffix=".tmp",
                                     dir=destination.parent, delete=False) as temporary:
        temporary_path = Path(temporary.name)
    try:
        with zipfile.ZipFile(temporary_path, "w") as output_archive:
            output_archive.comment = archive_comment
            for entry in entries:
                output_archive.writestr(entry, contents[entry.filename],
                                        compress_type=entry.compress_type)
        os.replace(temporary_path, destination)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()
    return count


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", nargs="?", type=Path, default=DEFAULT_JAR)
    parser.add_argument("--check", action="store_true",
                        help="verify the log-selection patch without changing the JAR")
    parser.add_argument("--output", type=Path,
                        help="write to a separate JAR instead of patching in place")
    args = parser.parse_args(argv)
    try:
        count = patch_jar(args.jar, args.output, check_only=args.check)
    except (ClassFormatError, OSError, zipfile.BadZipFile) as error:
        parser.error(str(error))
    if args.check:
        print(f"OK: {args.jar} keeps the clicked account's log selected")
    elif count:
        print(f"Patched {count} account-log selection method(s) in {args.output or args.jar}")
    else:
        print(f"No changes needed: {args.output or args.jar}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
