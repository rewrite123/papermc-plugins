# craftablebottleofenchanting

Craft Bottles o' Enchanting at enchanting tables and recover empty bottles after
throwing them. For **Paper 26.3 / Java 25**, with no runtime dependencies.

## Bottle Experience

Right-click an enchanting table with **glass bottles in your main hand** to open
a custom menu with one action, **Bottle Experience**. Click it to convert one glass
bottle into one Bottle o' Enchanting for **11 raw XP points**, with **no lapis**.
Keep the input bottles in the hotbar slot used to open the menu. The output is
placed in your inventory, merging with compatible stacks when possible.

Normal enchanting is unchanged when you open the table without glass bottles.
This is a custom menu, not an enchantment added to the vanilla enchanting list.
No bookshelves are required. Each click converts one bottle; shift-clicking,
number keys, dragging, and taking the display item are blocked. Nothing is consumed
if XP or output space is insufficient. Armor and offhand items are untouched.
Stay within six blocks of the table; removed tables cannot be used.

All game modes pay the same XP cost. Spectators cannot use the menu.
The `craftablebottleofenchanting.use` permission defaults to true.
Cancelled table-interaction events are respected.

When any thrown Bottle o' Enchanting breaks, **one glass bottle drops at its impact
location**, including dispenser-thrown bottles. Normal XP drops (3-11 XP) remain
unchanged. Charging 11 points prevents gaining XP by repeatedly bottling and
throwing, under vanilla XP-drop rules. Other plugins may change those drops.
Cancelled impacts do not produce a bottle. Drops obey normal item physics and
may be destroyed by hazards.

## Build and install

```sh
./gradlew build
```

Gradle runs on Java 17+ and provisions Java 25 for compilation if needed.
Windows users can use `gradlew.bat`. Copy
`build/libs/craftablebottleofenchanting-0.1.0.jar` into your Paper server's
`plugins/` directory, then restart using Java 25.

Run `./gradlew test` for conversion and event-handling tests. Tests use the
available MockBukkit 26.2 registry harness; production compiles against Paper
26.3. Smoke-test the menu, protected enchanting tables, and bottle impacts on your
Paper 26.3 server before production deployment.

## Publish to Hangar

Uses [HangarMC's official publishing plugin](https://github.com/HangarMC/hangar-publish-plugin).
Create the project and supply `HANGAR_API_TOKEN` with `create_version` permission:

```sh
./gradlew build publishPluginPublicationToHangar \
  -PhangarChangelog="Initial release: bottle experience without lapis and recover glass bottles."
```

Defaults: project `craftablebottleofenchanting`, channel `Release`, Paper `26.3`.
Set a unique version for each release in `gradle.properties`. Never commit API
keys. Normal builds do not upload anything.
