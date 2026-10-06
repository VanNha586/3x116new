#!/usr/bin/env python3
"""Patch the bundled JAR to use the bot's actual stop flag.

The executable JAR contains obfuscated, prebuilt classes. The source fix lives
in `scratch/BypassHelper.java`; this standard-library class-file patch keeps
`app/tool.jar` in sync and adjusts bytecode metadata without changing unrelated
class behavior.

Usage:
    python3 tools/patch_stop_flag.py                 # patch app/tool.jar
    python3 tools/patch_stop_flag.py --check         # verify only
    python3 tools/patch_stop_flag.py path/to/tool.jar
"""
from __future__ import annotations

import argparse
import os
import struct
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JAR = ROOT / "app" / "tool.jar"
STOP_FIELD = "hl"

TARGET_METHODS = {
    "avt/BypassHelper.class": {
        ("handleDisconnected", "(Ljava/lang/Object;)V"),
        ("getStateJson", "([Ljava/lang/Object;)Ljava/lang/String;"),
    },
    # NOTE: anonymous class numbering shifted by 1 after the shutdown-hook lambda
    # was added in BypassHelper.java (static initializer). The watchdog Runnable
    # is now $5 instead of $4.
    "avt/BypassHelper$5.class": {
        ("run", "()V"),
    },
}

# These entry points can be reached by work already queued when Stop is pressed.
# Their first instructions must reject stopped bots before doing more work/logging.
GUARDED_METHODS = {
    "avt/BypassHelper.class": {
        ("onBotLog", "(Ljava/lang/Object;[Ljava/lang/Object;)V"),
        ("doQuangCau", "(Ljava/lang/Object;)V"),
        ("doSr", "(Ljava/lang/Object;)V"),
        ("sendQuangCauNow", "(Ljava/lang/Object;)V"),
    },
}
STOP_HELPER = ("isBotStopped", "(Ljava/lang/Object;)Z")
GUARD_PREFIX_LENGTH = 8


class ClassFormatError(ValueError):
    """Raised when an unexpected/unsupported class-file structure is found."""


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def take(self, length: int) -> bytes:
        if length < 0 or self.pos + length > len(self.data):
            raise ClassFormatError("truncated class-file structure")
        result = self.data[self.pos : self.pos + length]
        self.pos += length
        return result

    def u1(self) -> int:
        return self.take(1)[0]

    def u2(self) -> int:
        return struct.unpack(">H", self.take(2))[0]

    def u4(self) -> int:
        return struct.unpack(">I", self.take(4))[0]


@dataclass
class CpEntry:
    tag: int
    raw: bytes
    value: object = None


class ConstantPool:
    def __init__(self, entries: List[Optional[CpEntry]], count: int):
        self.entries = entries
        self.count = count

    @classmethod
    def parse(cls, data: bytes, reader: Reader) -> "ConstantPool":
        count = reader.u2()
        entries: List[Optional[CpEntry]] = [None] * count
        index = 1
        while index < count:
            start = reader.pos
            tag = reader.u1()
            if tag == 1:  # CONSTANT_Utf8
                length = reader.u2()
                raw_value = reader.take(length)
                try:
                    value = raw_value.decode("utf-8")
                except UnicodeDecodeError:
                    # Class names, method names, and the string we patch are ASCII.
                    # Keep malformed/surrogate Modified UTF-8 entries opaque.
                    value = raw_value.decode("utf-8", errors="replace")
                entry = CpEntry(tag, data[start : reader.pos], value)
            elif tag in (3, 4):
                payload = reader.take(4)
                entry = CpEntry(tag, data[start : reader.pos], payload)
            elif tag in (5, 6):
                payload = reader.take(8)
                entry = CpEntry(tag, data[start : reader.pos], payload)
                entries[index] = entry
                index += 1
                if index < count:
                    entries[index] = None  # reserved slot after long/double
                    index += 1
                continue
            elif tag in (7, 8, 16, 19, 20):
                value = reader.u2()
                entry = CpEntry(tag, data[start : reader.pos], value)
            elif tag in (9, 10, 11, 12, 17, 18):
                value = (reader.u2(), reader.u2())
                entry = CpEntry(tag, data[start : reader.pos], value)
            elif tag == 15:
                value = (reader.u1(), reader.u2())
                entry = CpEntry(tag, data[start : reader.pos], value)
            else:
                raise ClassFormatError(f"unsupported constant-pool tag {tag} at #{index}")
            entries[index] = entry
            index += 1
        return cls(entries, count)

    def utf8(self, index: int) -> str:
        if not 0 < index < self.count:
            raise ClassFormatError(f"invalid CONSTANT_Utf8 index {index}")
        entry = self.entries[index]
        if entry is None or entry.tag != 1:
            raise ClassFormatError(f"constant-pool entry #{index} is not Utf8")
        return str(entry.value)

    def string(self, index: int) -> Optional[str]:
        if not 0 < index < self.count:
            return None
        entry = self.entries[index]
        if entry is None or entry.tag != 8:
            return None
        return self.utf8(int(entry.value))

    def _append(self, entry: CpEntry, slots: int = 1) -> int:
        index = self.count
        if index + slots > 0xFFFF:
            raise ClassFormatError("constant pool is full")
        self.entries.append(entry)
        if slots == 2:
            self.entries.append(None)
        self.count += slots
        return index

    def add_utf8(self, value: str) -> int:
        for index, entry in enumerate(self.entries):
            if entry is not None and entry.tag == 1 and entry.value == value:
                return index
        encoded = value.encode("utf-8")
        if len(encoded) > 0xFFFF:
            raise ClassFormatError("UTF-8 constant is too long")
        raw = b"\x01" + struct.pack(">H", len(encoded)) + encoded
        return self._append(CpEntry(1, raw, value))

    def add_class(self, name: str) -> int:
        name_index = self.add_utf8(name)
        for index, entry in enumerate(self.entries):
            if entry is not None and entry.tag == 7 and entry.value == name_index:
                return index
        return self._append(CpEntry(7, b"\x07" + struct.pack(">H", name_index), name_index))

    def add_name_and_type(self, name: str, descriptor: str) -> int:
        name_index = self.add_utf8(name)
        descriptor_index = self.add_utf8(descriptor)
        value = (name_index, descriptor_index)
        for index, entry in enumerate(self.entries):
            if entry is not None and entry.tag == 12 and entry.value == value:
                return index
        return self._append(CpEntry(12, b"\x0c" + struct.pack(">HH", *value), value))

    def find_methodref(self, owner: str, name: str, descriptor: str) -> Optional[int]:
        for index, entry in enumerate(self.entries):
            if entry is None or entry.tag not in (10, 11):
                continue
            class_index, name_and_type_index = entry.value
            class_entry = self.entries[class_index]
            nt_entry = self.entries[name_and_type_index]
            if class_entry is None or class_entry.tag != 7 or nt_entry is None or nt_entry.tag != 12:
                continue
            if (self.utf8(class_entry.value) == owner
                    and self.utf8(nt_entry.value[0]) == name
                    and self.utf8(nt_entry.value[1]) == descriptor):
                return index
        return None

    def add_methodref(self, owner: str, name: str, descriptor: str) -> int:
        existing = self.find_methodref(owner, name, descriptor)
        if existing is not None:
            return existing
        class_index = self.add_class(owner)
        name_and_type_index = self.add_name_and_type(name, descriptor)
        value = (class_index, name_and_type_index)
        return self._append(CpEntry(10, b"\x0a" + struct.pack(">HH", *value), value))

    def add_string(self, value: str) -> int:
        for index, entry in enumerate(self.entries):
            if entry is not None and entry.tag == 8 and self.string(index) == value:
                return index

        utf8_index = self.add_utf8(value)
        raw = b"\x08" + struct.pack(">H", utf8_index)
        return self._append(CpEntry(8, raw, utf8_index))

    def to_bytes(self) -> bytes:
        return struct.pack(">H", self.count) + b"".join(
            entry.raw for entry in self.entries[1:] if entry is not None
        )


@dataclass
class Instruction:
    pc: int
    opcode: int
    raw: bytes
    branch_targets: Tuple[int, ...] = ()
    switch_data: Optional[Tuple[int, int, Tuple[Tuple[int, int], ...]]] = None
    # switch_data is (default_target, low/high marker, (key, target) pairs).


_FIXED_LENGTHS: Dict[int, int] = {
    0x10: 2, 0x11: 3,
    0x12: 2, 0x13: 3, 0x14: 3,
    **{opcode: 2 for opcode in range(0x15, 0x1A)},
    **{opcode: 2 for opcode in range(0x36, 0x3B)},
    0x84: 3,
    0xA9: 2,
    **{opcode: 3 for opcode in range(0x99, 0xA9)},
    **{opcode: 3 for opcode in range(0xB2, 0xB9)},
    0xB9: 5, 0xBA: 5, 0xBB: 3, 0xBC: 2, 0xBD: 3,
    0xC0: 3, 0xC1: 3, 0xC5: 4, 0xC6: 3, 0xC7: 3,
    0xC8: 5, 0xC9: 5,
}


def _signed(data: bytes) -> int:
    return int.from_bytes(data, "big", signed=True)


def decode_instructions(code: bytes) -> List[Instruction]:
    instructions: List[Instruction] = []
    pc = 0
    while pc < len(code):
        opcode = code[pc]
        if opcode == 0xAA:  # tableswitch
            padding = (4 - ((pc + 1) % 4)) % 4
            base = pc + 1 + padding
            if base + 12 > len(code):
                raise ClassFormatError("truncated tableswitch")
            default_delta = _signed(code[base : base + 4])
            low = _signed(code[base + 4 : base + 8])
            high = _signed(code[base + 8 : base + 12])
            if high < low:
                raise ClassFormatError("invalid tableswitch range")
            count = high - low + 1
            end = base + 12 + count * 4
            if end > len(code):
                raise ClassFormatError("truncated tableswitch targets")
            targets = tuple(
                pc + _signed(code[base + 12 + 4 * i : base + 16 + 4 * i])
                for i in range(count)
            )
            pairs = tuple((low + i, target) for i, target in enumerate(targets))
            instructions.append(
                Instruction(pc, opcode, code[pc:end], (pc + default_delta,) + targets,
                            (pc + default_delta, low, pairs))
            )
            pc = end
            continue
        if opcode == 0xAB:  # lookupswitch
            padding = (4 - ((pc + 1) % 4)) % 4
            base = pc + 1 + padding
            if base + 8 > len(code):
                raise ClassFormatError("truncated lookupswitch")
            default_delta = _signed(code[base : base + 4])
            count = _signed(code[base + 4 : base + 8])
            if count < 0:
                raise ClassFormatError("invalid lookupswitch pair count")
            end = base + 8 + count * 8
            if end > len(code):
                raise ClassFormatError("truncated lookupswitch pairs")
            pairs = tuple(
                (
                    _signed(code[base + 8 + 8 * i : base + 12 + 8 * i]),
                    pc + _signed(code[base + 12 + 8 * i : base + 16 + 8 * i]),
                )
                for i in range(count)
            )
            targets = tuple(target for _, target in pairs)
            instructions.append(
                Instruction(pc, opcode, code[pc:end], (pc + default_delta,) + targets,
                            (pc + default_delta, 0, pairs))
            )
            pc = end
            continue
        if opcode == 0xC4:  # wide
            if pc + 1 >= len(code):
                raise ClassFormatError("truncated wide instruction")
            length = 6 if code[pc + 1] == 0x84 else 4
        else:
            length = _FIXED_LENGTHS.get(opcode, 1)
        if pc + length > len(code):
            raise ClassFormatError(f"truncated opcode 0x{opcode:02x} at {pc}")
        raw = code[pc : pc + length]
        targets: Tuple[int, ...] = ()
        if 0x99 <= opcode <= 0xA8 or opcode in (0xC6, 0xC7):
            targets = (pc + _signed(raw[1:3]),)
        elif opcode in (0xC8, 0xC9):
            targets = (pc + _signed(raw[1:5]),)
        instructions.append(Instruction(pc, opcode, raw, targets))
        pc += length
    if pc != len(code):
        raise ClassFormatError("instruction decoder did not end at code_length")
    return instructions


def _map_pc(pc: int, positions: Dict[int, int]) -> int:
    try:
        return positions[pc]
    except KeyError as exc:
        raise ClassFormatError(f"bytecode offset {pc} is not an instruction boundary") from exc


def rewrite_code(code: bytes, pool: ConstantPool, string_index: int) -> Tuple[bytes, Dict[int, int], int]:
    instructions = decode_instructions(code)
    replacements: Dict[int, int] = {}
    for instruction in instructions:
        if instruction.opcode not in (0x12, 0x13):
            continue
        index = instruction.raw[1] if instruction.opcode == 0x12 else int.from_bytes(instruction.raw[1:3], "big")
        if pool.string(index) == "w":
            replacements[instruction.pc] = string_index

    if not replacements:
        return code, {instruction.pc: instruction.pc for instruction in instructions} | {len(code): len(code)}, 0

    # First calculate the relocated program-counter of every instruction.  Switch
    # padding depends on its new alignment, so it is included in this pass.
    positions: Dict[int, int] = {}
    new_pc = 0
    for instruction in instructions:
        positions[instruction.pc] = new_pc
        if instruction.pc in replacements:
            new_size = 3 if replacements[instruction.pc] > 0xFF else 2
        elif instruction.opcode in (0xAA, 0xAB):
            padding = (4 - ((new_pc + 1) % 4)) % 4
            if instruction.opcode == 0xAA:
                _, low, pairs = instruction.switch_data  # type: ignore[misc]
                new_size = 1 + padding + 12 + 4 * len(pairs)
            else:
                _, _, pairs = instruction.switch_data  # type: ignore[misc]
                new_size = 1 + padding + 8 + 8 * len(pairs)
        else:
            new_size = len(instruction.raw)
        new_pc += new_size
    positions[len(code)] = new_pc

    output = bytearray()
    for instruction in instructions:
        old_pc = instruction.pc
        current_pc = positions[old_pc]
        if old_pc in replacements:
            index = replacements[old_pc]
            if index <= 0xFF:
                output.extend((0x12, index))
            else:
                output.append(0x13)
                output.extend(struct.pack(">H", index))
            continue

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
            target = _map_pc(instruction.branch_targets[0], positions)
            delta = target - current_pc
            if instruction.opcode in (0xC8, 0xC9):
                raw[1:5] = struct.pack(">i", delta)
            else:
                if not -32768 <= delta <= 32767:
                    raise ClassFormatError("relocated short branch is out of range")
                raw[1:3] = struct.pack(">h", delta)
        output.extend(raw)

    if len(output) != new_pc:
        raise ClassFormatError("relocated bytecode length mismatch")
    return bytes(output), positions, len(replacements)


def _read_verification_type(reader: Reader) -> Tuple[int, Optional[int]]:
    tag = reader.u1()
    if tag in range(0, 7):
        return tag, None
    if tag in (7, 8):
        return tag, reader.u2()
    raise ClassFormatError(f"invalid verification_type_info tag {tag}")


def _write_verification_type(value: Tuple[int, Optional[int]], positions: Dict[int, int]) -> bytes:
    tag, payload = value
    result = bytearray((tag,))
    if tag == 7:
        assert payload is not None
        result.extend(struct.pack(">H", payload))
    elif tag == 8:
        assert payload is not None
        result.extend(struct.pack(">H", _map_pc(payload, positions)))
    return bytes(result)


def _rewrite_stack_map(info: bytes, positions: Dict[int, int],
                       extra_same_frames: Sequence[int] = ()) -> bytes:
    reader = Reader(info)
    number_of_entries = reader.u2()
    extra_frames = sorted(set(extra_same_frames))
    output = bytearray(struct.pack(">H", number_of_entries + len(extra_frames)))
    old_previous = -1
    new_previous = -1

    for absolute_pc in extra_frames:
        new_delta = absolute_pc - new_previous - 1
        if not 0 <= new_delta <= 0xFFFF:
            raise ClassFormatError("injected stack-map offset is out of range")
        if new_delta <= 63:
            output.append(new_delta)
        else:
            output.append(251)
            output.extend(struct.pack(">H", new_delta))
        new_previous = absolute_pc

    for _ in range(number_of_entries):
        frame_type = reader.u1()
        kind: str
        chop_or_append = 0
        locals_items: List[Tuple[int, Optional[int]]] = []
        stack_items: List[Tuple[int, Optional[int]]] = []

        if frame_type <= 63:
            kind = "same"
            old_delta = frame_type
        elif frame_type <= 127:
            kind = "same1"
            old_delta = frame_type - 64
            stack_items.append(_read_verification_type(reader))
        elif frame_type == 247:
            kind = "same1"
            old_delta = reader.u2()
            stack_items.append(_read_verification_type(reader))
        elif 248 <= frame_type <= 250:
            kind = "chop"
            chop_or_append = frame_type
            old_delta = reader.u2()
        elif frame_type == 251:
            kind = "same"
            old_delta = reader.u2()
        elif 252 <= frame_type <= 254:
            kind = "append"
            chop_or_append = frame_type
            old_delta = reader.u2()
            locals_items = [_read_verification_type(reader) for _ in range(frame_type - 251)]
        elif frame_type == 255:
            kind = "full"
            old_delta = reader.u2()
            locals_count = reader.u2()
            locals_items = [_read_verification_type(reader) for _ in range(locals_count)]
            stack_count = reader.u2()
            stack_items = [_read_verification_type(reader) for _ in range(stack_count)]
        else:
            raise ClassFormatError(f"reserved stack-map frame type {frame_type}")

        old_absolute = old_previous + old_delta + 1
        new_absolute = _map_pc(old_absolute, positions)
        new_delta = new_absolute - new_previous - 1
        if not 0 <= new_delta <= 0xFFFF:
            raise ClassFormatError("relocated stack-map offset is out of range")

        if kind == "same":
            if new_delta <= 63:
                output.append(new_delta)
            else:
                output.append(251)
                output.extend(struct.pack(">H", new_delta))
        elif kind == "same1":
            if new_delta <= 63:
                output.append(64 + new_delta)
            else:
                output.append(247)
                output.extend(struct.pack(">H", new_delta))
            output.extend(_write_verification_type(stack_items[0], positions))
        elif kind == "chop":
            output.append(chop_or_append)
            output.extend(struct.pack(">H", new_delta))
        elif kind == "append":
            output.append(chop_or_append)
            output.extend(struct.pack(">H", new_delta))
            for value in locals_items:
                output.extend(_write_verification_type(value, positions))
        elif kind == "full":
            output.append(255)
            output.extend(struct.pack(">H", new_delta))
            output.extend(struct.pack(">H", len(locals_items)))
            for value in locals_items:
                output.extend(_write_verification_type(value, positions))
            output.extend(struct.pack(">H", len(stack_items)))
            for value in stack_items:
                output.extend(_write_verification_type(value, positions))

        old_previous = old_absolute
        new_previous = new_absolute

    if reader.pos != len(info):
        raise ClassFormatError("trailing bytes in StackMapTable")
    return bytes(output)


def _rewrite_line_numbers(info: bytes, positions: Dict[int, int]) -> bytes:
    reader = Reader(info)
    count = reader.u2()
    output = bytearray(struct.pack(">H", count))
    for _ in range(count):
        start_pc, line = reader.u2(), reader.u2()
        output.extend(struct.pack(">HH", _map_pc(start_pc, positions), line))
    if reader.pos != len(info):
        raise ClassFormatError("trailing bytes in LineNumberTable")
    return bytes(output)


def _rewrite_local_variables(info: bytes, positions: Dict[int, int]) -> bytes:
    reader = Reader(info)
    count = reader.u2()
    output = bytearray(struct.pack(">H", count))
    for _ in range(count):
        start_pc, length = reader.u2(), reader.u2()
        name_index, descriptor_index, local_index = reader.u2(), reader.u2(), reader.u2()
        new_start = _map_pc(start_pc, positions)
        new_end = _map_pc(start_pc + length, positions)
        output.extend(struct.pack(">HHHHH", new_start, new_end - new_start,
                                  name_index, descriptor_index, local_index))
    if reader.pos != len(info):
        raise ClassFormatError("trailing bytes in local-variable table")
    return bytes(output)


def _guard_prefix(methodref_index: int) -> bytes:
    # if (isBotStopped(arg0)) return; -- target body starts at bytecode offset 8.
    return b"\x2a\xb8" + struct.pack(">H", methodref_index) + b"\x99\x00\x04\xb1"


def _rewrite_code_attribute(info: bytes, pool: ConstantPool, new_string_index: int,
                             guard_methodref: Optional[int] = None,
                             stack_map_name_index: Optional[int] = None) -> Tuple[bytes, int]:
    reader = Reader(info)
    max_stack, max_locals = reader.u2(), reader.u2()
    old_code_length = reader.u4()
    old_code = reader.take(old_code_length)
    new_code, positions, patched_count = rewrite_code(old_code, pool, new_string_index)

    guard_prefix = _guard_prefix(guard_methodref) if guard_methodref is not None else b""
    if guard_prefix:
        positions = {old_pc: new_pc + len(guard_prefix) for old_pc, new_pc in positions.items()}
        new_code = guard_prefix + new_code
        max_stack = max(max_stack, 1)

    relocate = bool(patched_count or guard_prefix)
    exception_count = reader.u2()
    exceptions = []
    for _ in range(exception_count):
        start_pc, end_pc, handler_pc, catch_type = reader.u2(), reader.u2(), reader.u2(), reader.u2()
        exceptions.append((start_pc, end_pc, handler_pc, catch_type))

    nested_count = reader.u2()
    nested_attributes = []
    saw_stack_map = False
    for _ in range(nested_count):
        name_index, length = reader.u2(), reader.u4()
        attribute_info = reader.take(length)
        name = pool.utf8(name_index)
        if relocate:
            if name == "StackMapTable":
                saw_stack_map = True
                extra = (len(guard_prefix),) if guard_prefix else ()
                attribute_info = _rewrite_stack_map(attribute_info, positions, extra)
            elif name == "LineNumberTable":
                attribute_info = _rewrite_line_numbers(attribute_info, positions)
            elif name in ("LocalVariableTable", "LocalVariableTypeTable"):
                attribute_info = _rewrite_local_variables(attribute_info, positions)
            else:
                raise ClassFormatError(
                    f"cannot safely relocate code attribute {name!r} in a patched method"
                )
        nested_attributes.append((name_index, attribute_info))

    if reader.pos != len(info):
        raise ClassFormatError("trailing bytes in Code attribute")

    if guard_prefix and not saw_stack_map:
        if stack_map_name_index is None:
            raise ClassFormatError("missing StackMapTable constant for stop guard")
        frame = bytes((len(guard_prefix),))
        nested_attributes.append((stack_map_name_index, struct.pack(">H", 1) + frame))

    output = bytearray(struct.pack(">HHI", max_stack, max_locals, len(new_code)))
    output.extend(new_code)
    output.extend(struct.pack(">H", exception_count))
    for start_pc, end_pc, handler_pc, catch_type in exceptions:
        if relocate:
            start_pc = _map_pc(start_pc, positions)
            end_pc = _map_pc(end_pc, positions)
            handler_pc = _map_pc(handler_pc, positions)
        output.extend(struct.pack(">HHHH", start_pc, end_pc, handler_pc, catch_type))
    output.extend(struct.pack(">H", len(nested_attributes)))
    for name_index, attribute_info in nested_attributes:
        output.extend(struct.pack(">HI", name_index, len(attribute_info)))
        output.extend(attribute_info)
    return bytes(output), patched_count + int(bool(guard_prefix))


def _attributes(reader: Reader, pool: ConstantPool, patch_code: bool = False,
                new_string_index: Optional[int] = None,
                guard_methodref: Optional[int] = None,
                stack_map_name_index: Optional[int] = None) -> Tuple[bytes, int]:
    count = reader.u2()
    result = bytearray(struct.pack(">H", count))
    total_patches = 0
    for _ in range(count):
        name_index, length = reader.u2(), reader.u4()
        info = reader.take(length)
        if pool.utf8(name_index) == "Code" and (patch_code or guard_methodref is not None):
            if new_string_index is None:
                new_string_index = pool.add_string(STOP_FIELD)
            info, patched = _rewrite_code_attribute(
                info, pool, new_string_index, guard_methodref, stack_map_name_index
            )
            total_patches += patched
        result.extend(struct.pack(">HI", name_index, len(info)))
        result.extend(info)
    return bytes(result), total_patches


def _code_from_attribute(info: bytes) -> bytes:
    reader = Reader(info)
    reader.take(4)  # max_stack, max_locals
    return reader.take(reader.u4())


def _has_stop_guard(code: bytes, pool: ConstantPool, methodref_index: Optional[int]) -> bool:
    if methodref_index is None:
        return False
    return code.startswith(_guard_prefix(methodref_index))


def _build_stop_helper_method(pool: ConstantPool) -> bytes:
    stop_string = pool.add_string(STOP_FIELD)
    object_get_class = pool.add_methodref(
        "java/lang/Object", "getClass", "()Ljava/lang/Class;"
    )
    class_get_field = pool.add_methodref(
        "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;"
    )
    field_set_accessible = pool.add_methodref(
        "java/lang/reflect/Field", "setAccessible", "(Z)V"
    )
    field_get_boolean = pool.add_methodref(
        "java/lang/reflect/Field", "getBoolean", "(Ljava/lang/Object;)Z"
    )
    throwable_class = pool.add_class("java/lang/Throwable")
    code_name = pool.add_utf8("Code")
    stack_map_name = pool.add_utf8("StackMapTable")
    method_name = pool.add_utf8(STOP_HELPER[0])
    method_descriptor = pool.add_utf8(STOP_HELPER[1])

    code = bytearray()
    code.extend(b"\x2a\xb6" + struct.pack(">H", object_get_class))
    code.extend(b"\x13" + struct.pack(">H", stop_string))
    code.extend(b"\xb6" + struct.pack(">H", class_get_field))
    code.extend(b"\x59\x04\xb6" + struct.pack(">H", field_set_accessible))
    code.extend(b"\x2a\xb6" + struct.pack(">H", field_get_boolean))
    code.append(0xAC)  # ireturn
    handler_pc = len(code)
    code.extend(b"\x57\x04\xac")  # pop throwable; return true (fail closed)

    stack_map = struct.pack(">H", 1) + bytes((64 + handler_pc, 7)) + struct.pack(">H", throwable_class)
    code_info = bytearray(struct.pack(">HHI", 3, 1, len(code)))
    code_info.extend(code)
    code_info.extend(struct.pack(">H", 1))
    code_info.extend(struct.pack(">HHHH", 0, handler_pc, handler_pc, throwable_class))
    code_info.extend(struct.pack(">H", 1))
    code_info.extend(struct.pack(">HI", stack_map_name, len(stack_map)))
    code_info.extend(stack_map)

    method = bytearray(struct.pack(">HHHH", 0x000A, method_name, method_descriptor, 1))
    method.extend(struct.pack(">HI", code_name, len(code_info)))
    method.extend(code_info)
    return bytes(method)


def patch_class(data: bytes, class_name: str) -> Tuple[bytes, int]:
    if class_name not in TARGET_METHODS and class_name not in GUARDED_METHODS:
        return data, 0
    if data[:4] != b"\xCA\xFE\xBA\xBE":
        raise ClassFormatError(f"{class_name} is not a class file")

    reader = Reader(data)
    reader.take(8)  # magic and class-file version
    pool = ConstantPool.parse(data, reader)
    body_start = reader.pos
    methods_to_patch = TARGET_METHODS.get(class_name, set())
    methods_to_guard = GUARDED_METHODS.get(class_name, set())

    # Inspect the current binary first so applying this patch is idempotent.
    scan = Reader(data[body_start:])
    scan.take(6)  # access_flags, this_class, super_class
    scan.take(scan.u2() * 2)  # interfaces
    for _ in range(scan.u2()):  # fields
        scan.take(6)
        for _ in range(scan.u2()):
            scan.take(2)
            scan.take(scan.u4())

    found_old_references = 0
    found_new_references = 0
    seen_methods = set()
    guarded_methods = set()
    stop_helper_code = None
    guard_ref = pool.find_methodref("avt/BypassHelper", STOP_HELPER[0], STOP_HELPER[1])
    methods_count = scan.u2()
    for _ in range(methods_count):
        scan.u2()  # access_flags
        name_index, descriptor_index = scan.u2(), scan.u2()
        method_key = (pool.utf8(name_index), pool.utf8(descriptor_index))
        seen_methods.add(method_key)
        for _ in range(scan.u2()):
            attr_name_index, length = scan.u2(), scan.u4()
            info = scan.take(length)
            if pool.utf8(attr_name_index) != "Code":
                continue
            code = _code_from_attribute(info)
            if method_key in methods_to_patch:
                for instruction in decode_instructions(code):
                    if instruction.opcode in (0x12, 0x13):
                        cp_index = (instruction.raw[1] if instruction.opcode == 0x12
                                    else int.from_bytes(instruction.raw[1:3], "big"))
                        value = pool.string(cp_index)
                        found_old_references += value == "w"
                        found_new_references += value == STOP_FIELD
            if method_key in methods_to_guard and _has_stop_guard(code, pool, guard_ref):
                guarded_methods.add(method_key)
            if method_key == STOP_HELPER:
                stop_helper_code = code

    if found_old_references + found_new_references != len(methods_to_patch):
        raise ClassFormatError(
            f"{class_name}: expected {len(methods_to_patch)} stop-field references, "
            f"found {found_old_references} stale and {found_new_references} corrected"
        )

    missing_guards = methods_to_guard - guarded_methods
    add_stop_helper = class_name == "avt/BypassHelper.class" and STOP_HELPER not in seen_methods
    if STOP_HELPER in seen_methods and guard_ref is None:
        raise ClassFormatError(f"{class_name}: stop helper exists but its call reference is missing")
    if STOP_HELPER in seen_methods and not stop_helper_code:
        raise ClassFormatError(f"{class_name}: stop helper has no Code attribute")

    if not found_old_references and not missing_guards and not add_stop_helper:
        return data, 0

    replacement_index = pool.add_string(STOP_FIELD)
    stack_map_name_index = pool.add_utf8("StackMapTable")
    if methods_to_guard:
        guard_ref = pool.add_methodref("avt/BypassHelper", STOP_HELPER[0], STOP_HELPER[1])
    helper_method = _build_stop_helper_method(pool) if add_stop_helper else b""

    # Reparse the class body to write patched Code attributes and (once) the
    # fail-closed reflection helper used by guards on pending callbacks.
    body_reader = Reader(data[body_start:])
    body_prefix_start = body_reader.pos
    body_reader.take(6)
    body_reader.take(body_reader.u2() * 2)
    for _ in range(body_reader.u2()):
        body_reader.take(6)
        for _ in range(body_reader.u2()):
            body_reader.take(2)
            body_reader.take(body_reader.u4())
    fields_end = body_reader.pos
    output_body = bytearray(body_reader.data[body_prefix_start:fields_end])
    method_count = body_reader.u2()
    output_body.extend(struct.pack(">H", method_count + int(bool(helper_method))))
    total_edits = 0

    for _ in range(method_count):
        access_flags, name_index, descriptor_index = body_reader.u2(), body_reader.u2(), body_reader.u2()
        method_key = (pool.utf8(name_index), pool.utf8(descriptor_index))
        is_stop_target = method_key in methods_to_patch and found_old_references > 0
        guard_methodref = guard_ref if method_key in missing_guards else None
        output_body.extend(struct.pack(">HHH", access_flags, name_index, descriptor_index))
        attrs, edits = _attributes(
            body_reader,
            pool,
            is_stop_target,
            replacement_index,
            guard_methodref,
            stack_map_name_index,
        )
        total_edits += edits
        output_body.extend(attrs)

    if helper_method:
        output_body.extend(helper_method)
        total_edits += 1

    # Class attributes are not Code attributes and remain byte-for-byte identical.
    output_body.extend(body_reader.take(len(body_reader.data) - body_reader.pos))
    expected_edits = found_old_references + len(missing_guards) + int(bool(helper_method))
    if total_edits != expected_edits:
        raise ClassFormatError(
            f"{class_name}: expected {expected_edits} class edits but applied {total_edits}"
        )

    output = data[:8] + pool.to_bytes() + bytes(output_body)
    return output, total_edits

def patch_jar(source: Path, destination: Optional[Path] = None, check_only: bool = False) -> int:
    destination = destination or source
    with zipfile.ZipFile(source, "r") as archive:
        entries = archive.infolist()
        contents = {entry.filename: archive.read(entry.filename) for entry in entries}
        archive_comment = archive.comment

    total_patches = 0
    for class_name in TARGET_METHODS:
        if class_name not in contents:
            raise ClassFormatError(f"{source} does not contain {class_name}")
        patched, count = patch_class(contents[class_name], class_name)
        contents[class_name] = patched
        total_patches += count

    if check_only:
        if total_patches:
            raise ClassFormatError(f"{source} still needs {total_patches} stop-path patch(es)")
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
    parser.add_argument("jar", nargs="?", type=Path, default=DEFAULT_JAR,
                        help=f"JAR to patch (default: {DEFAULT_JAR.relative_to(ROOT)})")
    parser.add_argument("--check", action="store_true",
                        help="verify stop-flag references and queued-work guards without changing the JAR")
    parser.add_argument("--output", type=Path,
                        help="write to a separate JAR instead of patching in place")
    args = parser.parse_args(argv)
    try:
        count = patch_jar(args.jar, args.output, check_only=args.check)
    except (ClassFormatError, OSError, zipfile.BadZipFile) as error:
        parser.error(str(error))
    if args.check:
        print(f"OK: {args.jar} has the `hl` stop flag and queued-work guards")
    elif count:
        target = args.output or args.jar
        print(f"Applied {count} stop-path update(s) in {target}")
    else:
        print(f"No changes needed: {args.output or args.jar}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
