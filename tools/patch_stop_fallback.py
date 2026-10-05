#!/usr/bin/env python3
"""Make Stop also find a bot held only by BypassHelper's active-index map.

The obfuscated UI's avt.Q.H Stop handler normally reads avt.Q.o[index]. When
that slot is null, the handler currently skips the bot stop path even though
BypassHelper may still track the live instance. This patch adds a helper method
that stops that cached bot and inserts a call in the null-slot branch.

The corresponding Java source change is in scratch/BypassHelper.java.

Usage:
    python3 tools/patch_stop_fallback.py                 # patch app/tool.jar
    python3 tools/patch_stop_fallback.py --check         # verify only
    python3 tools/patch_stop_fallback.py path/to/tool.jar
"""
from __future__ import annotations

import argparse
import os
import struct
import tempfile
import zipfile
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

from patch_log_selection import _rewrite_code_attribute
from patch_stop_flag import (
    ClassFormatError,
    ConstantPool,
    CpEntry,
    Reader,
    decode_instructions,
)

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JAR = ROOT / "app" / "tool.jar"
HELPER_CLASS = "avt/BypassHelper.class"
STOP_CLASS = "avt/Q.class"
STOP_METHOD = ("H", "([Ljava/lang/Object;)V")
FALLBACK_METHOD = ("stopBotAtIndex", "(I)V")
FALLBACK_INSERTION_PC = 44


def _member_ref(pool: ConstantPool, tag: int, owner: str,
                name: str, descriptor: str) -> int:
    class_index = pool.add_class(owner)
    name_and_type_index = pool.add_name_and_type(name, descriptor)
    value = (class_index, name_and_type_index)
    for index, entry in enumerate(pool.entries):
        if entry is not None and entry.tag == tag and entry.value == value:
            return index
    raw = bytes((tag,)) + struct.pack(">HH", *value)
    return pool._append(CpEntry(tag, raw, value))


def _has_fallback_call(code: bytes, pool: ConstantPool) -> bool:
    for instruction in decode_instructions(code):
        if instruction.opcode != 0xB8:  # invokestatic
            continue
        cp_index = int.from_bytes(instruction.raw[1:3], "big")
        entry = pool.entries[cp_index]
        if entry is None or entry.tag != 10:
            continue
        class_index, name_and_type_index = entry.value
        class_entry = pool.entries[class_index]
        nt_entry = pool.entries[name_and_type_index]
        if class_entry is None or class_entry.tag != 7 or nt_entry is None or nt_entry.tag != 12:
            continue
        if (pool.utf8(class_entry.value) == "avt/BypassHelper"
                and pool.utf8(nt_entry.value[0]) == FALLBACK_METHOD[0]
                and pool.utf8(nt_entry.value[1]) == FALLBACK_METHOD[1]):
            return True
    return False


def _code_from_attribute(info: bytes) -> bytes:
    reader = Reader(info)
    reader.take(4)  # max_stack, max_locals
    return reader.take(reader.u4())


def _validate_null_slot_insertion_point(code: bytes) -> None:
    instructions = decode_instructions(code)
    by_pc = {instruction.pc: instruction for instruction in instructions}
    fallthrough = by_pc.get(FALLBACK_INSERTION_PC)
    if fallthrough is None or fallthrough.opcode != 0x1B:  # iload_1
        raise ClassFormatError(
            f"{STOP_CLASS}.{STOP_METHOD[0]} no longer matches the expected null-slot bytecode"
        )
    has_null_slot_branch = any(
        instruction.opcode == 0xC7
        and instruction.pc + len(instruction.raw) == FALLBACK_INSERTION_PC
        and instruction.branch_targets
        and instruction.branch_targets[0] > FALLBACK_INSERTION_PC
        for instruction in instructions
    )
    if not has_null_slot_branch:
        raise ClassFormatError(
            f"{STOP_CLASS}.{STOP_METHOD[0]} has no ifnonnull branch around the fallback insertion"
        )


def _build_fallback_method(pool: ConstantPool) -> bytes:
    """Build public static stopBotAtIndex(int), with verifier frames included."""
    map_field = _member_ref(
        pool, 9, "avt/BypassHelper", "activeBotsByIndex", "Ljava/util/Map;"
    )
    integer_value_of = _member_ref(
        pool, 10, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"
    )
    map_get = _member_ref(
        pool, 11, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"
    )
    cancel_pending = _member_ref(
        pool, 10, "avt/BypassHelper", "cancelPendingCast", "(Ljava/lang/Object;)V"
    )
    is_stopped = _member_ref(
        pool, 10, "avt/BypassHelper", "isBotStopped", "(Ljava/lang/Object;)Z"
    )
    game_bot_class = pool.add_class("avt/game/k")
    stop_field = _member_ref(pool, 9, "avt/game/k", "hl", "Z")
    stop_method = _member_ref(
        pool, 10, "avt/game/k", "N", "([Ljava/lang/Object;)V"
    )
    object_class = pool.add_class("java/lang/Object")
    code_name = pool.add_utf8("Code")
    stack_map_name = pool.add_utf8("StackMapTable")
    method_name = pool.add_utf8(FALLBACK_METHOD[0])
    method_descriptor = pool.add_utf8(FALLBACK_METHOD[1])

    code = bytearray()
    code.extend(b"\xb2" + struct.pack(">H", map_field))       # getstatic activeBotsByIndex
    code.extend(b"\x1a")                                     # iload_0 (account index)
    code.extend(b"\xb8" + struct.pack(">H", integer_value_of))
    code.extend(b"\xb9" + struct.pack(">HBB", map_get, 2, 0)) # Map.get(Integer)
    code.extend(b"\x4c")                                     # astore_1 (bot)
    code.extend(b"\x2b")                                     # aload_1
    ifnull_pc = len(code)
    code.append(0xC6)                                           # ifnull return
    ifnull_delta_at = len(code)
    code.extend(b"\x00\x00")
    code.extend(b"\x2b\xb8" + struct.pack(">H", cancel_pending))
    code.extend(b"\x2b\xb8" + struct.pack(">H", is_stopped))
    ifne_pc = len(code)
    code.append(0x9A)                                           # ifne return
    ifne_delta_at = len(code)
    code.extend(b"\x00\x00")
    code.extend(b"\x2b\xc0" + struct.pack(">H", game_bot_class)) # checkcast game.k
    code.extend(b"\x59\x04\xb5" + struct.pack(">H", stop_field)) # dup; iconst_1; putfield hl
    code.extend(b"\x03\xbd" + struct.pack(">H", object_class)) # iconst_0; anewarray Object
    code.extend(b"\xb6" + struct.pack(">H", stop_method))       # invokevirtual N(Object[])
    return_pc = len(code)
    code.append(0xB1)                                              # return
    code[ifnull_delta_at : ifnull_delta_at + 2] = struct.pack(">h", return_pc - ifnull_pc)
    code[ifne_delta_at : ifne_delta_at + 2] = struct.pack(">h", return_pc - ifne_pc)

    # Both conditional branches land at the final return. At that point locals
    # are: int index, Object bot; the operand stack is empty.
    stack_map = bytearray(struct.pack(">H", 1))
    stack_map.append(255)                                          # full_frame
    stack_map.extend(struct.pack(">H", return_pc))               # offset_delta (first frame)
    stack_map.extend(struct.pack(">H", 2))                       # locals count
    stack_map.extend(b"\x01")                                    # Integer local 0
    stack_map.extend(b"\x07" + struct.pack(">H", object_class))  # Object local 1
    stack_map.extend(struct.pack(">H", 0))                       # empty stack

    code_info = bytearray(struct.pack(">HHI", 3, 2, len(code)))
    code_info.extend(code)
    code_info.extend(struct.pack(">H", 0))                       # exception table
    code_info.extend(struct.pack(">H", 1))                       # Code attributes
    code_info.extend(struct.pack(">HI", stack_map_name, len(stack_map)))
    code_info.extend(stack_map)

    method = bytearray(struct.pack(">HHHH", 0x0009, method_name, method_descriptor, 1))
    method.extend(struct.pack(">HI", code_name, len(code_info)))
    method.extend(code_info)
    return bytes(method)


def patch_class(data: bytes, class_name: str) -> Tuple[bytes, int]:
    """Patch either the UI Stop handler or the helper class; safe to re-run."""
    if class_name not in (HELPER_CLASS, STOP_CLASS):
        return data, 0
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ClassFormatError(f"{class_name} is not a class file")

    reader = Reader(data)
    reader.take(8)
    pool = ConstantPool.parse(data, reader)
    body_start = reader.pos
    body_reader = Reader(data[body_start:])

    # Preserve class header, interfaces, and fields byte-for-byte.
    prefix_start = body_reader.pos
    body_reader.take(6)
    body_reader.take(body_reader.u2() * 2)
    for _ in range(body_reader.u2()):
        body_reader.take(6)
        for _ in range(body_reader.u2()):
            body_reader.take(2)
            body_reader.take(body_reader.u4())
    fields_end = body_reader.pos
    output_body = bytearray(body_reader.data[prefix_start:fields_end])

    method_count = body_reader.u2()
    method_bytes: List[bytes] = []
    seen_fallback_method = False
    patched = 0
    stop_method_found = False
    fallback_ref = None
    if class_name == STOP_CLASS:
        fallback_ref = _member_ref(
            pool, 10, "avt/BypassHelper", FALLBACK_METHOD[0], FALLBACK_METHOD[1]
        )

    for _ in range(method_count):
        access = body_reader.u2()
        name_index, descriptor_index = body_reader.u2(), body_reader.u2()
        method_key = (pool.utf8(name_index), pool.utf8(descriptor_index))
        attribute_count = body_reader.u2()
        attributes: List[Tuple[int, bytes]] = []
        for _ in range(attribute_count):
            attribute_name_index = body_reader.u2()
            attribute_info = body_reader.take(body_reader.u4())
            attribute_name = pool.utf8(attribute_name_index)
            if class_name == HELPER_CLASS and method_key == FALLBACK_METHOD:
                seen_fallback_method = True
            if class_name == STOP_CLASS and method_key == STOP_METHOD:
                stop_method_found = True
                if attribute_name == "Code":
                    code = _code_from_attribute(attribute_info)
                    if not _has_fallback_call(code, pool):
                        _validate_null_slot_insertion_point(code)
                        call = b"\x1b\xb8" + struct.pack(">H", fallback_ref)
                        attribute_info = _rewrite_code_attribute(
                            attribute_info, pool, {FALLBACK_INSERTION_PC: call}
                        )
                        patched += 1
            attributes.append((attribute_name_index, attribute_info))

        method = bytearray(struct.pack(">HHHH", access, name_index,
                                       descriptor_index, len(attributes)))
        for attribute_name_index, attribute_info in attributes:
            method.extend(struct.pack(">HI", attribute_name_index, len(attribute_info)))
            method.extend(attribute_info)
        method_bytes.append(bytes(method))

    if class_name == STOP_CLASS and not stop_method_found:
        raise ClassFormatError(f"{class_name} is missing {STOP_METHOD}")
    if class_name == HELPER_CLASS and not seen_fallback_method:
        method_bytes.append(_build_fallback_method(pool))
        patched += 1

    if body_reader.pos != len(body_reader.data):
        # Class-level attributes follow the methods; preserve them unchanged.
        class_attributes = body_reader.take(len(body_reader.data) - body_reader.pos)
    else:
        class_attributes = b""

    output_body.extend(struct.pack(">H", len(method_bytes)))
    output_body.extend(b"".join(method_bytes))
    output_body.extend(class_attributes)
    result = data[:8] + pool.to_bytes() + bytes(output_body)
    return result, patched


def patch_jar(source: Path, destination: Optional[Path] = None,
              check_only: bool = False) -> int:
    destination = destination or source
    with zipfile.ZipFile(source, "r") as archive:
        entries = archive.infolist()
        contents = {entry.filename: archive.read(entry.filename) for entry in entries}
        archive_comment = archive.comment

    for required in (HELPER_CLASS, STOP_CLASS):
        if required not in contents:
            raise ClassFormatError(f"{source} does not contain {required}")

    total_patches = 0
    # Add the callee first, then patch the UI call site.
    for class_name in (HELPER_CLASS, STOP_CLASS):
        patched_class, count = patch_class(contents[class_name], class_name)
        contents[class_name] = patched_class
        total_patches += count

    if check_only:
        if total_patches:
            raise ClassFormatError(f"{source} still needs {total_patches} stop-fallback patch(es)")
        return 0
    if total_patches == 0 and source.resolve() == destination.resolve():
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
    return total_patches


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", nargs="?", type=Path, default=DEFAULT_JAR)
    parser.add_argument("--check", action="store_true",
                        help="verify the cached-bot fallback stop call without changing the JAR")
    parser.add_argument("--output", type=Path,
                        help="write to a separate JAR instead of patching in place")
    args = parser.parse_args(argv)
    try:
        count = patch_jar(args.jar, args.output, check_only=args.check)
    except (ClassFormatError, OSError, zipfile.BadZipFile) as error:
        parser.error(str(error))
    if args.check:
        print(f"OK: {args.jar} stops helper-tracked bots when the primary slot is empty")
    elif count:
        print(f"Patched {count} stop-fallback target(s) in {args.output or args.jar}")
    else:
        print(f"No changes needed: {args.output or args.jar}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
