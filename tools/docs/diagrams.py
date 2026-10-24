#!/usr/bin/env python3
"""Shared PIL helpers that draw original README diagrams (no game textures).

Each plugin has a script in tools/docs/<plugin>.py that imports this module and
writes PNGs to <plugin>/docs/images/. README files reference them through
absolute raw.githubusercontent.com URLs (see image_url) so the same Markdown
renders on GitHub and on Hangar.
"""

from __future__ import annotations

import hashlib
import math
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RAW_BASE = "https://raw.githubusercontent.com/rewrite123/papermc-plugins/main"

BG = (20, 32, 53)
PANEL = (30, 45, 72)
PANEL_EDGE = (52, 70, 104)
SLOT = (15, 23, 39)
SLOT_EDGE = (70, 88, 122)
TEXT = (234, 246, 255)
MUTED = (160, 178, 205)
FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")

# Approximate item colors; anything missing gets a stable hashed color.
ITEM_COLORS = {
    "amethyst shard": (154, 92, 198), "amethyst": (154, 92, 198), "barrel": (139, 98, 57),
    "blaze powder": (255, 170, 30), "bone meal": (230, 230, 220), "book": (150, 90, 50),
    "bowl": (140, 100, 60), "bucket": (170, 175, 180), "candle": (230, 210, 160),
    "chest": (160, 110, 50), "coal": (40, 40, 40), "charcoal": (60, 50, 40),
    "copper ingot": (200, 110, 70), "diamond": (90, 230, 220), "dirt": (134, 96, 67),
    "echo shard": (20, 80, 100), "emerald": (40, 200, 90), "ender pearl": (30, 120, 110),
    "eye of ender": (80, 160, 80), "experience bottle": (140, 230, 90), "glass bottle": (190, 220, 240),
    "glass pane": (190, 220, 240), "glass": (190, 220, 240), "glowstone dust": (240, 200, 90),
    "gold ingot": (250, 210, 60), "gold nugget": (250, 210, 60), "gunpowder": (110, 110, 110),
    "honeycomb": (240, 170, 40), "iron ingot": (210, 210, 210), "iron nugget": (200, 200, 200),
    "lapis lazuli": (40, 70, 190), "lantern": (240, 180, 80), "leather": (150, 80, 40),
    "music disc": (60, 60, 70), "nether wart": (150, 30, 40), "obsidian": (40, 25, 60),
    "paper": (240, 240, 230), "phantom membrane": (190, 190, 160), "redstone": (200, 30, 30),
    "rotten flesh": (150, 80, 50), "snow": (245, 250, 255), "soul lantern": (90, 200, 220),
    "soul sand": (90, 70, 50), "soul candle": (90, 200, 230), "spider eye": (180, 40, 60),
    "stick": (140, 100, 60), "stone": (125, 125, 125), "string": (230, 230, 230),
    "sugar": (250, 250, 250), "water bottle": (60, 110, 230), "wax": (245, 240, 225),
    "white dye": (245, 245, 245), "wool": (235, 235, 235), "vault": (60, 70, 80),
    "minecart": (150, 150, 160), "rail": (170, 140, 100), "lodestone": (130, 130, 140),
    "banner": (240, 240, 240), "brewing stand": (180, 160, 70), "milk": (250, 250, 250),
    "potion": (180, 60, 160), "cauldron": (70, 70, 75), "fire": (250, 120, 30), "campfire": (250, 120, 30),
}


def font(size: int, bold: bool = False, mono: bool = False) -> ImageFont.FreeTypeFont:
    name = "DejaVuSansMono" if mono else "DejaVuSans"
    if bold:
        name += "-Bold"
    return ImageFont.truetype(str(FONT_DIR / f"{name}.ttf"), size)


def color_for(item: str) -> tuple[int, int, int]:
    key = item.lower().strip()
    for prefix in ("custom ", "any "):
        key = key.removeprefix(prefix)
    if key in ITEM_COLORS:
        return ITEM_COLORS[key]
    for known in sorted(ITEM_COLORS, key=len, reverse=True):
        if known in key:
            return ITEM_COLORS[known]
    digest = hashlib.sha256(key.encode()).digest()
    return (70 + digest[0] % 150, 70 + digest[1] % 150, 70 + digest[2] % 150)


def hex_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def readable_on(rgb: tuple[int, int, int]) -> tuple[int, int, int]:
    luminance = 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]
    return (15, 20, 30) if luminance > 150 else TEXT


def image_url(plugin: str, name: str) -> str:
    return f"{RAW_BASE}/{plugin}/docs/images/{name}"


class Figure:
    """A themed canvas with a title bar; save() writes <plugin>/docs/images/<name>.

    Coordinates passed to the drawing helpers are relative to the area below the title.
    """

    def __init__(self, width: int, height: int, title: str | None, accent: str = "#5ad1c8",
                 subtitle: str | None = None):
        self.accent = hex_rgb(accent)
        header = (64 if subtitle else 50) if title else 0
        self.image = Image.new("RGB", (width, height + header), BG)
        self.draw = ImageDraw.Draw(self.image)
        self.draw.rectangle([0, 0, width, 4], fill=self.accent)
        if title:
            self.draw.text((24, 16), title, font=font(22, bold=True), fill=TEXT)
            if subtitle:
                self.draw.text((24, 44), subtitle, font=font(14), fill=MUTED)
            self.draw.line([24, header - 4, width - 24, header - 4], fill=PANEL_EDGE, width=1)
        self.top = header
        self.width = width

    def text(self, xy, value, size=16, bold=False, fill=TEXT, anchor="la", mono=False):
        self.draw.text((xy[0], xy[1] + self.top), value, font=font(size, bold, mono), fill=fill, anchor=anchor)

    def wrapped(self, xy, value, width_chars, size=15, fill=MUTED, spacing=4, bold=False):
        y = xy[1]
        for paragraph in value.split("\n"):
            for line in textwrap.wrap(paragraph, width_chars) or [""]:
                self.text((xy[0], y), line, size=size, fill=fill, bold=bold)
                y += size + spacing
        return y

    def box(self, x1, y1, x2, y2, fill=PANEL, outline=PANEL_EDGE, radius=10, width=2):
        self.draw.rounded_rectangle([x1, y1 + self.top, x2, y2 + self.top], radius=radius, fill=fill,
                                    outline=outline, width=width)

    def arrow(self, x1, y1, x2, y2, fill=None, width=4):
        fill = fill or self.accent
        y1 += self.top
        y2 += self.top
        self.draw.line([x1, y1, x2, y2], fill=fill, width=width)
        angle = math.atan2(y2 - y1, x2 - x1)
        size = 12
        self.draw.polygon([(x2, y2),
                           (x2 - size * math.cos(angle - 0.5), y2 - size * math.sin(angle - 0.5)),
                           (x2 - size * math.cos(angle + 0.5), y2 - size * math.sin(angle + 0.5))], fill=fill)

    def item(self, x, y, size, label: str | None, count: int | None = None, highlight=False):
        """Draws an inventory slot; a labelled colored tile stands in for the item."""
        self.box(x, y, x + size, y + size, fill=SLOT, outline=self.accent if highlight else SLOT_EDGE,
                 radius=6, width=3 if highlight else 2)
        if not label:
            return
        rgb = color_for(label)
        pad = max(6, size // 10)
        self.box(x + pad, y + pad, x + size - pad, y + size - pad, fill=rgb,
                 outline=tuple(max(0, c - 50) for c in rgb), radius=6, width=2)
        line_size = 12 if size < 80 else 13
        words = textwrap.wrap(label, max(6, int((size - 2 * pad) / (line_size * 0.62))))[:3]
        start = y + size / 2 - (len(words) * (line_size + 3)) / 2
        for index, word in enumerate(words):
            self.text((x + size / 2, start + index * (line_size + 3)), word, size=line_size, bold=True,
                      fill=readable_on(rgb), anchor="ma")
        if count and count > 1:
            self.box(x + size - 34, y + size - 30, x + size - 4, y + size - 4, fill=BG, outline=self.accent,
                     radius=6, width=2)
            self.text((x + size - 19, y + size - 17), str(count), size=16, bold=True, fill=TEXT, anchor="mm")

    def save(self, plugin: str, name: str) -> Path:
        target = ROOT / plugin / "docs" / "images" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        self.image.save(target, optimize=True)
        return target


def recipe(plugin: str, name: str, title: str, grid, result: str, count: int = 1, note: str | None = None,
           accent: str = "#5ad1c8", shapeless: bool = False) -> Path:
    """grid is up to three rows of up to three item names (None for an empty slot)."""
    slot, gap = 84, 6
    grid_size = 3 * slot + 2 * gap
    width = 24 + grid_size + 140 + slot + 20 + 24
    note_lines = len(textwrap.wrap(note, 80)) if note else 0
    height = grid_size + 48 + (note_lines * 18 + 12 if note else 0)
    figure = Figure(width, height, title, accent, "Shapeless: any arrangement works" if shapeless else None)
    x0, y0 = 24, 24
    for row in range(3):
        for column in range(3):
            label = grid[row][column] if row < len(grid) and column < len(grid[row]) else None
            figure.item(x0 + column * (slot + gap), y0 + row * (slot + gap), slot, label)
    grid_right = x0 + grid_size
    middle = y0 + grid_size / 2
    figure.arrow(grid_right + 24, middle, grid_right + 116, middle)
    figure.item(grid_right + 136, middle - (slot + 20) / 2, slot + 20, result, count, highlight=True)
    if note:
        figure.wrapped((24, y0 + grid_size + 16), note, 80, size=14)
    return figure.save(plugin, name)


def flow(plugin: str, name: str, title: str, steps, accent: str = "#5ad1c8", columns: int | None = None,
         card_width: int = 230, card_height: int = 150) -> Path:
    """steps: list of (heading, body). Cards go left to right and wrap into rows."""
    columns = columns or min(len(steps), 4)
    rows = (len(steps) + columns - 1) // columns
    gap = 54
    width = 48 + columns * card_width + (columns - 1) * gap
    height = 48 + rows * card_height + (rows - 1) * 40
    figure = Figure(width, height, title, accent)
    for index, (heading, body) in enumerate(steps):
        row, column = divmod(index, columns)
        x = 24 + column * (card_width + gap)
        y = 24 + row * (card_height + 40)
        figure.box(x, y, x + card_width, y + card_height)
        figure.draw.ellipse([x + 12, y + 12 + figure.top, x + 40, y + 40 + figure.top], fill=figure.accent)
        figure.text((x + 26, y + 26), str(index + 1), size=15, bold=True, fill=BG, anchor="mm")
        figure.text((x + 50, y + 17), heading, size=16, bold=True)
        figure.wrapped((x + 14, y + 52), body, max(18, int((card_width - 28) / 7.6)), size=13)
        if index + 1 < len(steps):
            if column + 1 < columns:
                figure.arrow(x + card_width + 8, y + card_height / 2, x + card_width + gap - 8, y + card_height / 2)
            else:
                figure.arrow(x + card_width / 2, y + card_height + 6, x + card_width / 2, y + card_height + 34, width=3)
    return figure.save(plugin, name)


def cards(plugin: str, name: str, title: str, items, accent: str = "#5ad1c8", columns: int = 3,
          card_width: int = 280, card_height: int = 140) -> Path:
    """Feature overview: list of (heading, body) or (heading, body, item label)."""
    rows = (len(items) + columns - 1) // columns
    gap = 18
    width = 48 + columns * card_width + (columns - 1) * gap
    height = 48 + rows * card_height + (rows - 1) * gap
    figure = Figure(width, height, title, accent)
    for index, entry in enumerate(items):
        heading, body = entry[0], entry[1]
        label = entry[2] if len(entry) > 2 else None
        row, column = divmod(index, columns)
        x = 24 + column * (card_width + gap)
        y = 24 + row * (card_height + gap)
        figure.box(x, y, x + card_width, y + card_height)
        figure.draw.rectangle([x + 2, y + 12 + figure.top, x + 6, y + card_height - 12 + figure.top],
                              fill=figure.accent)
        text_x = x + 18
        if label:
            figure.item(x + 14, y + 14, 60, label)
            text_x = x + 86
        figure.text((text_x, y + 16), heading, size=16, bold=True)
        figure.wrapped((text_x, y + 42), body, max(14, int((card_width - (text_x - x) - 12) / 7.6)), size=13)
    return figure.save(plugin, name)


def nice_step(raw: float) -> float:
    """Rounds a raw axis step up to 1, 2 or 5 times a power of ten."""
    magnitude = 10 ** math.floor(math.log10(raw))
    return next(m * magnitude for m in (1, 2, 5, 10) if m * magnitude >= raw - 1e-9)


def chart(plugin: str, name: str, title: str, series, x_label: str, y_label: str, accent: str = "#5ad1c8",
          width: int = 860, height: int = 380, step: bool = False, markers=(), y_max=None, x_max=None) -> Path:
    """series: list of (label, [(x, y), ...], hex color or None).

    markers: [(x, y, text)] or [(x, y, text, dx, dy)] where dx/dy offset the label in pixels.
    """
    figure = Figure(width, height, title, accent)
    left, right, top, bottom = 78, width - 28, 24, height - 58
    xs = [x for _, points, _ in series for x, _ in points]
    ys = [y for _, points, _ in series for _, y in points]
    x_max = x_max or max(xs)
    x_step = nice_step(x_max / 6)
    x_ticks = math.ceil(x_max / x_step - 1e-9)
    x_max = x_ticks * x_step
    y_max = y_max or max(ys) * 1.1
    y_step = nice_step(y_max / 5)
    y_ticks = math.ceil(y_max / y_step - 1e-9)
    y_max = y_ticks * y_step

    def sx(x):
        return left + (right - left) * x / x_max

    def sy(y):
        return bottom - (bottom - top) * y / y_max

    for i in range(y_ticks + 1):
        value = y_step * i
        y = sy(value)
        figure.draw.line([left, y + figure.top, right, y + figure.top], fill=PANEL, width=1)
        label = f"{value:g}" if abs(value - round(value)) < 1e-9 else f"{value:.1f}"
        figure.text((left - 10, y - 8), label, size=13, fill=MUTED, anchor="ra")
    for i in range(x_ticks + 1):
        value = x_step * i
        figure.text((sx(value), bottom + 10), f"{value:,g}", size=13, fill=MUTED, anchor="ma")
    figure.draw.line([left, sy(0) + figure.top, right, sy(0) + figure.top], fill=PANEL_EDGE, width=2)
    figure.draw.line([left, top + figure.top, left, bottom + figure.top], fill=PANEL_EDGE, width=2)
    figure.text(((left + right) / 2, bottom + 32), x_label, size=14, bold=True, fill=MUTED, anchor="ma")
    figure.text((14, top - 18), y_label, size=14, bold=True, fill=MUTED)
    for index, (label, points, color) in enumerate(series):
        rgb = hex_rgb(color) if color else figure.accent
        coords = []
        for i, (x, y) in enumerate(points):
            if step and i:
                coords.append((sx(x), sy(points[i - 1][1]) + figure.top))
            coords.append((sx(x), sy(y) + figure.top))
        figure.draw.line(coords, fill=rgb, width=4)
        if len(series) > 1:
            lx, ly = left + 16, top + 6 + index * 22
            figure.draw.rectangle([lx, ly + figure.top + 4, lx + 18, ly + figure.top + 14], fill=rgb)
            figure.text((lx + 26, ly), label, size=14)
    for marker in markers:
        x, y, text = marker[:3]
        dx, dy = marker[3:5] if len(marker) >= 5 else (12, 14)
        px, py = sx(x), sy(y)
        figure.draw.ellipse([px - 6, py - 6 + figure.top, px + 6, py + 6 + figure.top], fill=TEXT)
        label_font = font(13, bold=True)
        length = figure.draw.textlength(text, font=label_font)
        lx, ly = px + dx, py + dy
        figure.box(lx - 6, ly - 4, lx + length + 6, ly + 19, fill=BG, outline=PANEL_EDGE, radius=4, width=1)
        figure.text((lx, ly), text, size=13, bold=True)
    return figure.save(plugin, name)


def chat(plugin: str, name: str, title: str, lines, accent: str = "#5ad1c8", width: int = 860) -> Path:
    """Mock chat window. lines: list of lists of (text, hex color[, underline])."""
    line_height = 26
    height = 48 + line_height * len(lines) + 16
    figure = Figure(width, height, title, accent)
    figure.box(20, 18, width - 20, height - 18, fill=(10, 14, 22), outline=PANEL_EDGE, radius=6)
    mono = font(16, mono=True)
    for row, parts in enumerate(lines):
        x = 36
        y = 32 + row * line_height + figure.top
        for part in parts:
            text, color = part[0], part[1]
            figure.draw.text((x, y), text, font=mono, fill=hex_rgb(color))
            length = figure.draw.textlength(text, font=mono)
            if len(part) > 2 and part[2]:
                figure.draw.line([x, y + 20, x + length, y + 20], fill=hex_rgb(color), width=1)
            x += length
    return figure.save(plugin, name)


def grid_map(plugin: str, name: str, title: str, cells, legend, accent: str = "#5ad1c8", cell: int = 22,
             caption: str | None = None) -> Path:
    """Top-down/side tile map. cells: list of strings; legend: {char: (hex color, label)}."""
    rows, columns = len(cells), max(len(row) for row in cells)
    legend_rows = (len(legend) + 2) // 3
    caption_lines = len(textwrap.wrap(caption, 95)) if caption else 0
    width = max(48 + columns * cell, 660)
    height = 24 + rows * cell + 20 + legend_rows * 30 + caption_lines * 18 + 20
    figure = Figure(width, height, title, accent)
    x0 = (width - columns * cell) // 2
    for r, row in enumerate(cells):
        for c, char in enumerate(row):
            color = hex_rgb(legend[char][0]) if char in legend else SLOT
            figure.draw.rectangle([x0 + c * cell, 24 + r * cell + figure.top, x0 + (c + 1) * cell - 2,
                                   24 + (r + 1) * cell - 2 + figure.top], fill=color)
    y = 24 + rows * cell + 20
    for index, (char, (color, label)) in enumerate(legend.items()):
        column, row = index % 3, index // 3
        x = 24 + column * ((width - 48) // 3)
        figure.draw.rectangle([x, y + row * 30 + figure.top, x + 18, y + row * 30 + 18 + figure.top],
                              fill=hex_rgb(color))
        figure.text((x + 28, y + row * 30), label, size=14)
    if caption:
        figure.wrapped((24, y + legend_rows * 30 + 4), caption, 95, size=13)
    return figure.save(plugin, name)
