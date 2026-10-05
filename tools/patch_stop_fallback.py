#!/usr/bin/env python3
"""Make Stop also find a bot held only by BypassHelper's active-index map.

The obfuscated UI's avt.Q.H Stop handler normally reads avt.Q.o[index]. When
that slot is null, it skips the bot stop path. BypassHelper can also display
lastActiveBot for a one-account install when its indexed cache misses the bot.
This patch mirrors that fallback in the stop helper and inserts the call in the
UI's null-slot branch.

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
SINGLE_ACCOUNT_METHOD = ("getSingleAccountFallbackBot", "(I)Ljava/lang/Object;")
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


def _has_static_call(code: bytes, pool: ConstantPool,
                     target: Tuple[str, str]) -> bool:
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
                and pool.utf8(nt_entry.value[0]) == target[0]
                and pool.utf8(nt_entry.value[1]) == target[1]):
            return True
    return False


def _has_fallback_call(code: bytes, pool: ConstantPool) -> bool:
    return _has_static_call(code, pool, FALLBACK_METHOD)


def _has_single_account_call(code: bytes, pool: ConstantPool) -> bool:
    return _has_static_call(code, pool, SINGLE_ACCOUNT_METHOD)


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


def _full_stack_map(pool: ConstantPool,
                    frames: List[Tuple[int, List[bytes], List[bytes]]]) -> bytes:
    output = bytearray(struct.pack(">H", len(frames)))
    previous_pc = -1
    for pc, locals_items, stack_items in frames:
        delta = pc - previous_pc - 1
        output.append(255)  # full_frame
        output.extend(struct.pack(">H", delta))
        output.extend(struct.pack(">H", len(locals_items)))
        output.extend(b"".join(locals_items))
        output.extend(struct.pack(">H", len(stack_items)))
        output.extend(b"".join(stack_items))
        previous_pc = pc
    return bytes(output)


def _code_attribute(pool: ConstantPool, max_stack: int, max_locals: int,
                    code: bytes, exceptions: List[Tuple[int, int, int, int]],
                    stack_map: bytes) -> bytes:
    code_info = bytearray(struct.pack(">HHI", max_stack, max_locals, len(code)))
    code_info.extend(code)
    code_info.extend(struct.pack(">H", len(exceptions)))
    for exception in exceptions:
        code_info.extend(struct.pack(">HHHH", *exception))
    code_info.extend(struct.pack(">H", 1))
    code_info.extend(struct.pack(">HI", pool.add_utf8("StackMapTable"), len(stack_map)))
    code_info.extend(stack_map)
    return bytes(code_info)


def _method_info(pool: ConstantPool, access: int, key: Tuple[str, str],
                 code_info: bytes) -> bytes:
    method = bytearray(struct.pack(">HHHH", access, pool.add_utf8(key[0]),
                                  pool.add_utf8(key[1]), 1))
    method.extend(struct.pack(">HI", pool.add_utf8("Code"), len(code_info)))
    method.extend(code_info)
    return bytes(method)


def _build_fallback_code(pool: ConstantPool) -> bytes:
    """Build stopBotAtIndex(int), including the one-account last-bot fallback."""
    map_field = _member_ref(
        pool, 9, "avt/BypassHelper", "activeBotsByIndex", "Ljava/util/Map;"
    )
    integer_value_of = _member_ref(
        pool, 10, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"
    )
    map_get = _member_ref(
        pool, 11, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"
    )
    is_stopped = _member_ref(
        pool, 10, "avt/BypassHelper", "isBotStopped", "(Ljava/lang/Object;)Z"
    )
    single_account_bot = _member_ref(
        pool, 10, "avt/BypassHelper", SINGLE_ACCOUNT_METHOD[0], SINGLE_ACCOUNT_METHOD[1]
    )
    cancel_pending = _member_ref(
        pool, 10, "avt/BypassHelper", "cancelPendingCast", "(Ljava/lang/Object;)V"
    )
    game_bot_class = pool.add_class("avt/game/k")
    stop_field = _member_ref(pool, 9, "avt/game/k", "hl", "Z")
    stop_method = _member_ref(
        pool, 10, "avt/game/k", "N", "([Ljava/lang/Object;)V"
    )
    object_class = pool.add_class("java/lang/Object")

    code = bytearray()
    code.extend(b"\xb2" + struct.pack(">H", map_field))
    code.extend(b"\x1a\xb8" + struct.pack(">H", integer_value_of))
    code.extend(b"\xb9" + struct.pack(">HBB", map_get, 2, 0))
    code.append(0x4C)  # astore_1
    code.extend(b"\x2b\xb8" + struct.pack(">H", is_stopped))
    map_is_live_branch = len(code)
    code.extend(b"\x99\x00\x00")  # ifeq selected bot
    code.extend(b"\x1a\xb8" + struct.pack(">H", single_account_bot))
    code.append(0x4C)  # astore_1
    code.extend(b"\x2b\xb8" + struct.pack(">H", is_stopped))
    fallback_is_live_branch = len(code)
    code.extend(b"\x99\x00\x00")  # ifeq selected bot
    code.append(0xB1)  # return when neither candidate is live

    selected_pc = len(code)
    code.extend(b"\x2b\xb8" + struct.pack(">H", cancel_pending))
    code.extend(b"\x2b\xc0" + struct.pack(">H", game_bot_class))
    code.extend(b"\x59\x04\xb5" + struct.pack(">H", stop_field))
    code.extend(b"\x03\xbd" + struct.pack(">H", object_class))
    code.extend(b"\xb6" + struct.pack(">H", stop_method))
    code.append(0xB1)
    for branch_pc in (map_is_live_branch, fallback_is_live_branch):
        code[branch_pc + 1 : branch_pc + 3] = struct.pack(">h", selected_pc - branch_pc)

    object_type = b"\x07" + struct.pack(">H", object_class)
    stack_map = _full_stack_map(pool, [(selected_pc, [b"\x01", object_type], [])])
    return _code_attribute(pool, 3, 2, bytes(code), [], stack_map)


def _build_fallback_method(pool: ConstantPool) -> bytes:
    return _method_info(pool, 0x0009, FALLBACK_METHOD, _build_fallback_code(pool))


def _build_single_account_code(pool: ConstantPool) -> bytes:
    """Return lastActiveBot only for row zero of a one-account UI."""
    init_ref = _member_ref(pool, 10, "avt/BypassHelper", "init", "()V")
    accounts_field = _member_ref(
        pool, 9, "avt/BypassHelper", "fM", "Ljava/lang/reflect/Field;"
    )
    field_get = _member_ref(
        pool, 10, "java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"
    )
    list_class = pool.add_class("java/util/List")
    list_size = _member_ref(pool, 11, "java/util/List", "size", "()I")
    last_bot_field = _member_ref(
        pool, 9, "avt/BypassHelper", "lastActiveBot", "Ljava/lang/Object;"
    )
    throwable_class = pool.add_class("java/lang/Throwable")

    code = bytearray()
    code.append(0x1A)  # iload_0
    nonzero_branch = len(code)
    code.extend(b"\x9a\x00\x00")  # ifne return null
    code.extend(b"\xb8" + struct.pack(">H", init_ref))
    code.extend(b"\xb2" + struct.pack(">H", accounts_field))
    field_missing_branch = len(code)
    code.extend(b"\xc6\x00\x00")  # ifnull return null
    code.extend(b"\xb2" + struct.pack(">H", accounts_field))
    code.extend(b"\x01\xb6" + struct.pack(">H", field_get))
    code.extend(b"\xc0" + struct.pack(">H", list_class))
    code.append(0x4C)  # astore_1
    code.extend(b"\x2b")
    accounts_missing_branch = len(code)
    code.extend(b"\xc6\x00\x00")  # ifnull return null
    code.extend(b"\x2b\xb9" + struct.pack(">HBB", list_size, 1, 0))
    code.append(0x04)  # iconst_1
    wrong_count_branch = len(code)
    code.extend(b"\xa0\x00\x00")  # if_icmpne return null
    code.extend(b"\xb2" + struct.pack(">H", last_bot_field))
    code.append(0xB0)  # areturn

    null_return_pc = len(code)
    code.extend(b"\x01\xb0")  # aconst_null; areturn
    handler_pc = len(code)
    code.extend(b"\x57\x01\xb0")  # pop throwable; return null
    for branch_pc in (nonzero_branch, field_missing_branch,
                      accounts_missing_branch, wrong_count_branch):
        code[branch_pc + 1 : branch_pc + 3] = struct.pack(">h", null_return_pc - branch_pc)

    integer_type = b"\x01"
    throwable_type = b"\x07" + struct.pack(">H", throwable_class)
    stack_map = _full_stack_map(pool, [
        (null_return_pc, [integer_type], []),
        (handler_pc, [integer_type], [throwable_type]),
    ])
    exceptions = [(0, null_return_pc, handler_pc, throwable_class)]
    return _code_attribute(pool, 2, 2, bytes(code), exceptions, stack_map)


def _build_single_account_method(pool: ConstantPool) -> bytes:
    return _method_info(pool, 0x000A, SINGLE_ACCOUNT_METHOD,
                        _build_single_account_code(pool))


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
    seen_single_account_method = False
    fallback_code_found = False
    single_account_code_found = False
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
            if class_name == HELPER_CLASS and method_key == SINGLE_ACCOUNT_METHOD:
                seen_single_account_method = True
            if class_name == HELPER_CLASS and method_key == FALLBACK_METHOD and attribute_name == "Code":
                fallback_code_found = True
                if not _has_single_account_call(_code_from_attribute(attribute_info), pool):
                    attribute_info = _build_fallback_code(pool)
                    patched += 1
            if class_name == HELPER_CLASS and method_key == SINGLE_ACCOUNT_METHOD and attribute_name == "Code":
                single_account_code_found = True
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
    if class_name == HELPER_CLASS:
        if seen_fallback_method and not fallback_code_found:
            raise ClassFormatError(f"{class_name}: {FALLBACK_METHOD} has no Code attribute")
        if seen_single_account_method and not single_account_code_found:
            raise ClassFormatError(f"{class_name}: {SINGLE_ACCOUNT_METHOD} has no Code attribute")
        if not seen_fallback_method:
            method_bytes.append(_build_fallback_method(pool))
            patched += 1
        if not seen_single_account_method:
            method_bytes.append(_build_single_account_method(pool))
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
