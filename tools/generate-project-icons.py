#!/usr/bin/env python3
"""Render original Hangar project icons with ImageMagick (no external textures)."""

from pathlib import Path
import argparse
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
INK = "#0b1020"
WHITE = "#eaf6ff"


class Canvas:
    def __init__(self, accent, title):
        self.draw = []
        self.title = title
        self.accent = accent
        self.rect(0, 0, 127, 127, INK)
        self.rect(3, 3, 124, 124, "#26344c")
        self.rect(5, 5, 122, 122, "#142035")
        for x in range(8, 120, 8):
            for y in range(8, 104, 8):
                color = "#19283f" if (x + y) % 16 else "#17243a"
                self.rect(x, y, x + 6, y + 6, color)
        self.rect(5, 5, 122, 6, accent)
        self.rect(5, 104, 122, 122, "#0e1729")
        self.rect(14, 103, 113, 103, accent)
        for x, y in [(16, 22), (108, 32), (19, 83), (106, 89)]:
            self.spark(x, y, accent)

    def shape(self, color, primitive):
        self.draw.extend(["-fill", color, "-draw", primitive])

    def rect(self, x1, y1, x2, y2, color):
        self.shape(color, f"rectangle {x1},{y1} {x2},{y2}")

    def poly(self, points, color):
        self.shape(color, "polygon " + " ".join(f"{x},{y}" for x, y in points))

    def spark(self, x, y, color):
        self.rect(x - 2, y, x + 2, y, color)
        self.rect(x, y - 2, x, y + 2, color)

    def orb(self, x, y):
        self.rect(x - 3, y - 4, x + 2, y + 3, "#3f932e")
        self.rect(x - 4, y - 2, x + 3, y + 1, "#7dde45")
        self.rect(x - 2, y - 3, x, y, "#dcff83")

    def render(self, slug):
        target = ROOT / slug / "assets" / "icon.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            [
                "convert", "-size", "128x128", "xc:none", "+antialias",
                "-stroke", "none", *self.draw,
                "-font", "DejaVu-Sans-Mono-Bold", "-pointsize", "8",
                "-fill", WHITE, "-gravity", "south", "-annotate", "+0+11",
                self.title, "-filter", "point", "-resize", "512x512",
                "-strip", f"PNG24:{target}",
            ],
            check=True,
        )
        print(target.relative_to(ROOT))


def tpcost():
    c = Canvas("#b789ff", "TP COST")
    c.poly([(23, 88), (67, 78), (106, 89), (63, 100)], "#0b1326")
    c.rect(33, 17, 91, 90, INK)
    c.rect(36, 20, 88, 87, "#34284b")
    for y in range(23, 86, 11):
        c.rect(37, y, 44, y + 7, "#605076")
        c.rect(81, y, 87, y + 7, "#443456")
    c.rect(45, 23, 79, 81, "#703cb0")
    c.rect(48, 26, 76, 78, "#a25bdb")
    c.rect(53, 30, 72, 75, "#c384f0")
    c.rect(57, 35, 68, 69, "#e1b5ff")
    c.rect(48, 49, 52, 70, "#8248c3")
    c.rect(72, 32, 76, 48, "#dc9efa")
    c.rect(42, 82, 82, 86, "#716083")
    # The arrow emerges from the portal, with XP orbs marking the price.
    c.poly([(56, 51), (86, 51), (86, 43), (102, 59),
            (86, 75), (86, 67), (56, 67)], INK)
    c.poly([(59, 54), (89, 54), (89, 50), (98, 59),
            (89, 68), (89, 64), (59, 64)], "#e0ffa3")
    c.orb(28, 62)
    c.orb(38, 95)
    c.orb(86, 95)
    return c


def inventorysorter():
    c = Canvas("#ffc46b", "INVENTORY SORTER")
    c.poly([(21, 85), (76, 77), (109, 90), (58, 99)], "#0b1326")
    c.poly([(24, 49), (77, 37), (104, 49), (51, 64)], INK)
    c.poly([(24, 49), (51, 63), (51, 93), (24, 78)], "#754222")
    c.poly([(51, 63), (104, 49), (104, 80), (51, 93)], "#a3602b")
    c.poly([(28, 49), (77, 41), (98, 50), (51, 60)], "#e5a74b")
    c.poly([(28, 56), (47, 66), (47, 84), (28, 75)], "#a46730")
    c.poly([(55, 67), (100, 55), (100, 76), (55, 88)], "#c18439")
    c.poly([(60, 68), (65, 67), (65, 82), (60, 83)], "#9d642e")
    c.poly([(89, 60), (93, 59), (93, 76), (89, 77)], "#9d642e")
    c.poly([(24, 56), (51, 70), (104, 56), (104, 61),
            (51, 75), (24, 61)], INK)
    c.rect(71, 64, 80, 76, "#412c24")
    c.rect(73, 65, 78, 73, "#fff0a7")
    c.rect(75, 68, 76, 71, "#c79345")
    for x, height, color in [(31, 8, "#7ede91"), (50, 14, "#73cfee"),
                              (69, 20, "#c194fb")]:
        c.rect(x - 1, 32 - height, x + 12, 34, INK)
        c.rect(x, 32 - height, x + 10, 32, color)
        c.rect(x + 2, 33 - height, x + 4, 30, WHITE)
    c.poly([(88, 19), (92, 19), (92, 28), (97, 28),
            (90, 35), (83, 28), (88, 28)], "#e0ffa3")
    return c


def enchantcap():
    c = Canvas("#8ce9e2", "ENCHANT CAP")
    c.poly([(21, 91), (72, 81), (107, 92), (55, 101)], "#0b1326")
    c.poly([(27, 79), (73, 69), (98, 81), (51, 93)], INK)
    c.poly([(30, 78), (73, 72), (93, 81), (51, 90)], "#829caf")
    c.poly([(30, 79), (51, 89), (51, 94), (30, 85)], "#4c5e78")
    c.poly([(51, 89), (93, 80), (93, 86), (51, 95)], "#617b96")
    c.poly([(48, 54), (73, 49), (73, 76), (54, 82), (45, 77)], "#50677e")
    c.poly([(62, 53), (73, 50), (73, 76), (62, 79)], "#7692a4")
    c.poly([(22, 40), (77, 29), (106, 43), (78, 59),
            (41, 66), (24, 54)], INK)
    c.poly([(26, 40), (77, 33), (100, 43), (76, 53), (41, 60)], "#bed8df")
    c.poly([(26, 42), (41, 53), (76, 47), (76, 58),
            (41, 64), (26, 53)], "#758fa4")
    c.poly([(76, 47), (100, 42), (97, 49), (76, 58)], "#526c87")
    c.rect(40, 39, 73, 41, "#e4fcff")
    c.spark(30, 28, "#bc8dff")
    c.spark(99, 64, "#bc8dff")
    c.rect(52, 10, 78, 28, INK)
    c.rect(54, 11, 76, 26, "#327b62")
    c.draw.extend(["-font", "DejaVu-Sans-Mono-Bold", "-pointsize", "12",
                   "-fill", "#dcff9b", "-draw", "text 58,23 '39'"])
    return c


def sleepy():
    c = Canvas("#8caeff", "SLEEPY")
    c.rect(26, 17, 43, 34, "#fbe6aa")
    c.rect(22, 21, 47, 30, "#fbe6aa")
    c.rect(35, 15, 48, 25, "#19283f")
    c.spark(64, 21, "#e2ecff")
    c.spark(98, 16, "#e2ecff")
    c.poly([(17, 85), (70, 75), (111, 89), (58, 101)], "#0b1326")
    c.poly([(24, 59), (71, 45), (104, 63), (57, 81)], INK)
    c.poly([(25, 65), (57, 81), (57, 91), (25, 76)], "#855332")
    c.poly([(57, 81), (104, 64), (104, 77), (57, 95)], "#5e392b")
    c.rect(26, 75, 31, 88, "#b37f4b")
    c.rect(95, 77, 100, 89, "#875a37")
    c.poly([(28, 59), (71, 48), (100, 63), (57, 77)], "#527be2")
    c.poly([(28, 60), (57, 74), (57, 81), (28, 67)], "#324d9c")
    c.poly([(57, 74), (100, 60), (100, 67), (57, 82)], "#3f5cba")
    c.poly([(29, 59), (44, 55), (72, 69), (57, 75)], "#eef6ff")
    c.poly([(31, 59), (43, 57), (65, 68), (57, 71)], "#c8def5")
    for x, y, size in [(64, 39, 5), (78, 29, 7), (94, 22, 9)]:
        c.rect(x, y, x + size, y + 1, "#c4d5ff")
        c.poly([(x + size - 1, y + 1), (x + size + 1, y + 1),
                (x + 2, y + size), (x, y + size)], "#c4d5ff")
        c.rect(x, y + size, x + size, y + size + 1, "#c4d5ff")
    return c


def craftablebottleofenchanting():
    c = Canvas("#b3ed6e", "BOTTLE XP")
    c.poly([(30, 90), (64, 82), (99, 91), (65, 100)], "#0b1326")
    c.poly([(48, 22), (78, 22), (78, 32), (73, 32), (73, 43),
            (91, 59), (91, 85), (82, 94), (45, 94),
            (36, 85), (36, 59), (53, 43), (53, 32), (48, 32)], INK)
    c.poly([(55, 30), (71, 30), (71, 46), (87, 61), (87, 83),
            (79, 90), (48, 90), (40, 82), (40, 61), (57, 46)], "#6e9bb8")
    c.poly([(59, 32), (67, 32), (67, 48), (82, 63),
            (82, 81), (77, 86), (49, 86), (45, 81), (45, 63), (61, 48)],
           "#b5e7dd")
    c.poly([(45, 64), (82, 64), (82, 81), (77, 86),
            (49, 86), (45, 81)], "#68b83a")
    c.rect(46, 66, 80, 73, "#9cea4c")
    c.rect(52, 77, 74, 84, "#86d741")
    c.rect(48, 54, 51, 60, WHITE)
    c.rect(44, 62, 47, 76, WHITE)
    c.rect(50, 23, 76, 29, "#b38652")
    c.rect(53, 23, 73, 25, "#ecc78c")
    c.orb(27, 47)
    c.orb(91, 25)
    c.orb(101, 69)
    c.spark(65, 59, "#f0ffaf")
    return c


def mobbanner():
    c = Canvas("#ec969c", "MOB BANNER")
    c.poly([(35, 93), (69, 85), (94, 93), (60, 101)], "#0b1326")
    c.rect(60, 17, 66, 92, INK)
    c.rect(61, 18, 64, 91, "#c79b5c")
    c.rect(62, 19, 63, 89, "#f2ca83")
    c.rect(34, 20, 94, 25, INK)
    c.rect(36, 21, 92, 23, "#e6c18a")
    c.poly([(39, 25), (89, 25), (89, 78), (77, 86),
            (64, 80), (51, 86), (39, 78)], INK)
    c.poly([(42, 25), (86, 25), (86, 76), (76, 82),
            (64, 76), (52, 82), (42, 76)], "#377b59")
    c.poly([(45, 27), (82, 27), (82, 73), (76, 77),
            (64, 72), (52, 77), (45, 74)], "#68ba6b")
    c.rect(48, 29, 51, 71, "#8ddd83")
    c.rect(79, 29, 82, 72, "#4d9d5f")
    # An original blocky monster mask, not a copied game texture.
    c.rect(51, 38, 60, 48, "#102b31")
    c.rect(68, 38, 77, 48, "#102b31")
    c.rect(60, 48, 68, 54, "#102b31")
    c.rect(55, 54, 73, 63, "#102b31")
    c.rect(54, 61, 59, 67, "#102b31")
    c.rect(69, 61, 74, 67, "#102b31")
    c.rect(53, 39, 56, 41, "#c0f8a0")
    c.rect(70, 39, 73, 41, "#c0f8a0")
    for x, y in [(32, 43), (94, 60), (64, 13)]:
        c.spark(x, y, "#daa4ff")
    return c


def soulgem():
    c = Canvas("#9cebd5", "SOUL GEM")
    c.poly([(22, 90), (67, 80), (107, 91), (62, 101)], "#0b1326")
    c.poly([(25, 75), (39, 52), (36, 29), (53, 15), (68, 32),
            (65, 57), (52, 83), (36, 91)], INK)
    c.poly([(29, 74), (43, 53), (40, 30), (53, 20), (63, 33),
            (61, 56), (49, 79), (36, 86)], "#7548ab")
    c.poly([(40, 31), (53, 20), (52, 47), (36, 79), (29, 74),
            (43, 53)], "#d1a1fa")
    c.poly([(53, 20), (63, 33), (61, 56), (49, 79), (52, 47)], "#aa72e5")
    c.poly([(40, 31), (46, 28), (46, 47), (43, 53)], "#f0d7ff")
    c.poly([(56, 76), (66, 50), (77, 29), (90, 20), (100, 37),
            (94, 68), (78, 91), (66, 94)], INK)
    c.poly([(60, 76), (70, 51), (80, 32), (89, 25), (96, 38),
            (90, 67), (76, 86), (67, 90)], "#246777")
    c.poly([(60, 76), (70, 51), (80, 32), (89, 25), (83, 53),
            (68, 86)], "#76dece")
    c.poly([(89, 25), (96, 38), (90, 67), (76, 86), (83, 53)], "#36999c")
    c.poly([(80, 32), (85, 31), (78, 51), (73, 55)], "#c5fff0")
    c.poly([(70, 62), (79, 56), (84, 64), (78, 75), (69, 78),
            (65, 71)], "#ceffab")
    c.rect(72, 64, 78, 70, "#f3ffd6")
    c.orb(23, 48)
    c.orb(98, 83)
    c.spark(62, 17, "#e7cbff")
    c.spark(103, 52, "#a5ffea")
    return c


def minecarttravel():
    c = Canvas("#ffca70", "MINECART TRAVEL")
    c.poly([(15, 92), (85, 76), (114, 88), (43, 102)], "#0b1326")
    for x, y in [(28, 87), (45, 83), (62, 79), (79, 75), (96, 71)]:
        c.poly([(x - 4, y), (x + 13, y + 7), (x + 13, y + 11),
                (x - 4, y + 4)], "#89552f")
        c.poly([(x - 3, y), (x + 13, y + 7), (x + 16, y + 5),
                (x, y - 2)], "#c38d51")
    c.poly([(22, 86), (100, 68), (104, 70), (26, 90)], "#d6e5ec")
    c.poly([(35, 94), (113, 76), (116, 79), (38, 98)], "#91abbc")
    c.poly([(86, 27), (103, 23), (115, 29), (115, 51),
            (98, 56), (86, 49)], INK)
    c.poly([(89, 29), (103, 26), (111, 30), (98, 34)], "#d4e2e5")
    c.poly([(89, 30), (98, 35), (98, 51), (89, 47)], "#6a8197")
    c.poly([(99, 35), (112, 31), (112, 49), (99, 53)], "#91a9b8")
    c.rect(102, 38, 108, 45, "#4f667e")
    c.rect(104, 39, 106, 43, "#b3f9ef")
    c.rect(35, 77, 43, 86, INK)
    c.rect(38, 79, 41, 83, "#69849b")
    c.rect(73, 69, 81, 78, INK)
    c.rect(76, 71, 79, 75, "#69849b")
    c.poly([(26, 46), (72, 35), (96, 47), (89, 72),
            (49, 83), (31, 70)], INK)
    c.poly([(30, 47), (71, 39), (91, 48), (50, 60)], "#dcebf1")
    c.poly([(36, 47), (71, 42), (83, 48), (50, 56)], "#45576e")
    c.poly([(41, 48), (70, 44), (77, 48), (50, 53)], "#22344a")
    c.poly([(31, 50), (49, 62), (49, 78), (34, 68)], "#7993a8")
    c.poly([(51, 62), (90, 51), (85, 69), (51, 79)], "#aec7d5")
    c.poly([(55, 64), (84, 56), (82, 60), (55, 68)], "#e9f7fa")
    c.poly([(54, 74), (84, 66), (83, 69), (54, 77)], "#6b899e")
    for x, y, length in [(14, 42, 13), (9, 53, 16), (13, 64, 13)]:
        c.rect(x, y, x + length, y + 2, "#ffcb72")
    c.poly([(58, 20), (69, 20), (64, 28), (72, 28),
            (57, 43), (61, 31), (54, 31)], "#e4ffa4")
    c.spark(80, 21, "#fff1b9")
    return c


def reduceendermanworlddmg():
    c = Canvas("#c38bff", "ENDERMAN RETURN")
    c.poly([(22, 91), (68, 81), (108, 91), (64, 101)], "#0b1326")
    c.rect(38, 18, 88, 55, INK)
    c.rect(41, 21, 85, 52, "#242331")
    c.rect(45, 24, 81, 33, "#2e2b3d")
    c.rect(43, 37, 59, 42, "#be77ed")
    c.rect(68, 37, 83, 42, "#be77ed")
    c.rect(49, 37, 54, 42, "#f5c8ff")
    c.rect(72, 37, 77, 42, "#f5c8ff")
    c.rect(51, 54, 75, 78, "#1c1c28")
    c.rect(29, 55, 37, 82, "#242331")
    c.rect(88, 55, 96, 82, "#242331")
    c.poly([(43, 67), (67, 60), (86, 70), (62, 78)], "#9be270")
    c.poly([(43, 67), (62, 78), (62, 95), (43, 84)], "#875534")
    c.poly([(62, 78), (86, 70), (86, 86), (62, 95)], "#af7341")
    c.poly([(43, 67), (62, 78), (62, 83), (43, 73)], "#4b9d4c")
    c.poly([(62, 78), (86, 70), (86, 75), (62, 83)], "#68b858")
    c.rect(49, 78, 53, 82, "#b88656")
    c.rect(72, 84, 77, 87, "#d19659")
    c.poly([(104, 56), (111, 56), (111, 89), (96, 89),
            (96, 95), (86, 85), (96, 75), (96, 81), (104, 81)], "#e0ffa3")
    c.spark(22, 48, "#c38bff")
    return c


def flipdisc():
    c = Canvas("#64e2d2", "FLIP DISC")
    for x, y, label in [(44, 48, "#69d8bd"), (83, 74, "#c18aff")]:
        c.poly([(x - 24, y - 12), (x - 12, y - 24), (x + 12, y - 24),
                (x + 24, y - 12), (x + 24, y + 12), (x + 12, y + 24),
                (x - 12, y + 24), (x - 24, y + 12)], INK)
        c.poly([(x - 21, y - 10), (x - 10, y - 21), (x + 10, y - 21),
                (x + 21, y - 10), (x + 21, y + 10), (x + 10, y + 21),
                (x - 10, y + 21), (x - 21, y + 10)], "#30394d")
        c.rect(x - 17, y - 7, x - 15, y + 7, "#55627b")
        c.rect(x - 9, y - 17, x + 7, y - 15, "#55627b")
        c.rect(x + 15, y - 5, x + 17, y + 8, "#192335")
        c.rect(x - 8, y - 8, x + 8, y + 8, label)
        c.rect(x - 4, y - 4, x + 4, y + 4, "#eaf6ff")
        c.rect(x - 1, y - 1, x + 1, y + 1, INK)
    c.poly([(69, 17), (97, 17), (97, 24), (103, 24), (92, 35),
            (81, 24), (88, 24), (88, 23), (69, 23)], "#e0ffa3")
    c.poly([(58, 98), (29, 98), (29, 91), (23, 91), (34, 80),
            (45, 91), (38, 91), (38, 92), (58, 92)], "#e0ffa3")
    return c


def main():
    factories = (
        tpcost, inventorysorter, enchantcap, sleepy,
        craftablebottleofenchanting, mobbanner, soulgem, minecarttravel,
        reduceendermanworlddmg, flipdisc,
    )
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", choices=[factory.__name__ for factory in factories])
    args = parser.parse_args()
    if shutil.which("convert") is None:
        raise SystemExit("ImageMagick's convert command is required.")
    for factory in factories:
        if args.project is None or factory.__name__ == args.project:
            factory().render(factory.__name__)


if __name__ == "__main__":
    main()
