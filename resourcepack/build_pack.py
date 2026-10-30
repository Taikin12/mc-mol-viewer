from __future__ import annotations

import hashlib
import json
import struct
import tempfile
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LEGACY_NAMESPACE = "mcmolview"
OUTPUT = ROOT.parent / "build" / "mcmolview-resourcepack.zip"
RESOLUTION = 9
PACK_FORMAT = 97

FACES = {
    "up": (0, 1, 0),
    "down": (0, -1, 0),
    "north": (0, 0, -1),
    "south": (0, 0, 1),
    "east": (1, 0, 0),
    "west": (-1, 0, 0),
}
UVS = {
    "up": [0, 0, 4, 4],
    "down": [8, 0, 12, 4],
    "north": [4, 0, 8, 4],
    "south": [4, 0, 8, 4],
    "east": [4, 0, 8, 4],
    "west": [4, 0, 8, 4],
}


def png_chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def build_texture() -> bytes:
    width = height = 16
    rows = []
    for y in range(height):
        row = bytearray()
        for x in range(width):
            if x < 4 and y < 4:
                color = (255, 255, 255)
            elif 4 <= x < 8 and y < 4:
                color = (212, 212, 212)
            elif 8 <= x < 12 and y < 4:
                color = (144, 144, 144)
            else:
                color = (0, 0, 0)
            row.extend(color)
        rows.append(b"\x00" + bytes(row))
    raw = b"".join(rows)
    return (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(raw, 9))
        + png_chunk(b"IEND", b"")
    )


def build_model() -> dict:
    step = 16.0 / RESOLUTION
    radius = 8.0

    def offset(index: int) -> float:
        return (index + 0.5) * step - 8.0

    inside = set()
    for i in range(RESOLUTION):
        for j in range(RESOLUTION):
            for k in range(RESOLUTION):
                x, y, z = offset(i), offset(j), offset(k)
                if x * x + y * y + z * z <= radius * radius:
                    inside.add((i, j, k))

    elements = []
    for i, j, k in sorted(inside):
        faces = {}
        for face, (dx, dy, dz) in FACES.items():
            if (i + dx, j + dy, k + dz) in inside:
                continue
            faces[face] = {
                "uv": UVS[face],
                "texture": "#all",
                "tintindex": 0,
            }
        if not faces:
            continue
        elements.append(
            {
                "from": [round(i * step, 4), round(j * step, 4), round(k * step, 4)],
                "to": [round((i + 1) * step, 4), round((j + 1) * step, 4), round((k + 1) * step, 4)],
                "faces": faces,
            }
        )

    return {
        "textures": {
            "particle": "mcmolview:item/sphere",
            "all": "mcmolview:item/sphere",
        },
        "elements": elements,
    }


def build_stick_model() -> dict:
    faces = {
        face: {"uv": [0, 0, 16, 16], "texture": "#all", "tintindex": 0}
        for face in FACES
    }
    return {
        "textures": {
            "particle": "mcmolview:item/stick",
            "all": "mcmolview:item/stick",
        },
        "elements": [
            {
                "from": [0, 0, 0],
                "to": [16, 16, 16],
                "faces": faces,
            }
        ],
    }


def build_stick_texture() -> bytes:
    width = height = 16
    rows = []
    for _ in range(height):
        row = bytearray()
        for _ in range(width):
            row.extend((255, 255, 255))
        rows.append(b"\x00" + bytes(row))
    raw = b"".join(rows)
    return (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(raw, 9))
        + png_chunk(b"IEND", b"")
    )


def build_wand_texture(handle: tuple[int, int, int], gem: tuple[int, int, int],
                       accent: tuple[int, int, int], kind: str) -> bytes:
    size = 16
    pixels = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]

    def put(x: int, y: int, color: tuple[int, int, int]) -> None:
        if 0 <= x < size and 0 <= y < size:
            pixels[y][x] = (color[0], color[1], color[2], 255)

    dark = tuple(max(0, channel - 60) for channel in handle)
    for i in range(8):
        put(3 + i, 14 - i, handle)
        put(4 + i, 14 - i, handle)
        put(4 + i, 15 - i, dark)
    put(3, 14, dark)
    put(3, 15, dark)

    center_x, center_y = 10, 5
    if kind == "menu":
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                put(center_x + dx, center_y + dy, gem)
        put(center_x - 1, center_y - 1, accent)
        put(center_x, center_y - 1, accent)
    elif kind == "view":
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                if abs(dx) + abs(dy) <= 2 and not (dx == 0 and dy == 0):
                    put(center_x + dx, center_y + dy, gem)
        put(center_x, center_y, accent)
    elif kind == "frame":
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                if abs(dx) == 2 or abs(dy) == 2:
                    put(center_x + dx, center_y + dy, gem)
        put(center_x, center_y - 1, accent)
        put(center_x, center_y + 1, accent)
    else:
        for dy in range(-2, 3):
            width = 2 - abs(dy)
            for dx in range(-width, width + 1):
                put(center_x + dx, center_y + dy, gem)
        put(center_x, center_y - 1, accent)
        put(center_x - 1, center_y, accent)

    rows = []
    for row in pixels:
        data = bytearray()
        for pixel in row:
            data.extend(pixel)
        rows.append(b"\x00" + bytes(data))
    raw = b"".join(rows)
    return (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(raw, 9))
        + png_chunk(b"IEND", b"")
    )


def build_simple_item_definition(namespace: str, model: str) -> dict:
    return {
        "model": {
            "type": "minecraft:model",
            "model": f"{namespace}:item/{model}",
        }
    }


def build_generated_model(namespace: str, texture: str) -> dict:
    return {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{namespace}:item/{texture}"},
    }


def build_item_definition(model: str) -> dict:
    return {
        "model": {
            "type": "minecraft:model",
            "model": f"mcmolview:item/{model}",
            "tints": [
                {
                    "type": "minecraft:dye",
                    "default": 16777215,
                }
            ],
        }
    }


def main() -> None:
    sphere_model = build_model()
    stick_model = build_stick_model()
    wand_textures = {
        "wand_menu": build_wand_texture((196, 150, 60), (255, 210, 80), (255, 255, 210), "menu"),
        "wand_view": build_wand_texture((150, 170, 190), (255, 220, 80), (255, 255, 255), "view"),
        "wand_atom": build_wand_texture((70, 150, 170), (80, 235, 255), (220, 255, 255), "atom"),
        "wand_frame": build_wand_texture((140, 110, 190), (200, 140, 255), (245, 220, 255), "frame"),
    }
    texture_source = ROOT / "textures"
    texture_source.mkdir(parents=True, exist_ok=True)
    for name, png in list(wand_textures.items()):
        custom = texture_source / f"{name}.png"
        if custom.is_file():
            wand_textures[name] = custom.read_bytes()
        else:
            custom.write_bytes(png)
    pack_meta = {
        "pack": {
            "pack_format": PACK_FORMAT,
            "description": "McMolView 3D models",
        }
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        files = {
            "pack.mcmeta": json.dumps(pack_meta, indent=2) + "\n",
            "assets/mcmolview/items/sphere.json": json.dumps(build_item_definition("sphere"), indent=2) + "\n",
            "assets/mcmolview/items/stick.json": json.dumps(build_item_definition("stick"), indent=2) + "\n",
            "assets/mcmolview/models/item/sphere.json": json.dumps(sphere_model, indent=2) + "\n",
            "assets/mcmolview/models/item/stick.json": json.dumps(stick_model, indent=2) + "\n",
            **{
                f"assets/{namespace}/items/{name}.json": json.dumps(
                    build_simple_item_definition(namespace, name), indent=2) + "\n"
                for namespace in ("miviewer", LEGACY_NAMESPACE)
                for name in wand_textures
            },
            **{
                f"assets/{namespace}/models/item/{name}.json": json.dumps(
                    build_generated_model(namespace, name), indent=2) + "\n"
                for namespace in ("miviewer", LEGACY_NAMESPACE)
                for name in wand_textures
            },
        }
        for relative, content in files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        texture_dir = root / "assets/mcmolview/textures/item"
        texture_dir.mkdir(parents=True, exist_ok=True)
        (texture_dir / "sphere.png").write_bytes(build_texture())
        (texture_dir / "stick.png").write_bytes(build_stick_texture())
        for namespace in ("miviewer", LEGACY_NAMESPACE):
            wand_texture_dir = root / f"assets/{namespace}/textures/item"
            wand_texture_dir.mkdir(parents=True, exist_ok=True)
            for name, png in wand_textures.items():
                (wand_texture_dir / f"{name}.png").write_bytes(png)

        with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(root.rglob("*")):
                if path.is_file():
                    entry = zipfile.ZipInfo(
                        path.relative_to(root).as_posix(),
                        date_time=(1980, 1, 1, 0, 0, 0),
                    )
                    entry.compress_type = zipfile.ZIP_DEFLATED
                    entry.external_attr = 0o644 << 16
                    archive.writestr(entry, path.read_bytes())

    digest = hashlib.sha1(OUTPUT.read_bytes()).hexdigest()
    print(f"wrote {OUTPUT}")
    print(f"sphere elements: {len(sphere_model['elements'])}")
    print(f"sha1: {digest}")


if __name__ == "__main__":
    main()
