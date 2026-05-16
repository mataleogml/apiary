# Apiary — Agent Context

## What this is

A Bukkit/Paper plugin for Minecraft 1.21.x that adds tiered beehives with level-up tokens, named bees, and configurable honey / honeycomb / honey-block harvest yields. Designed to be a drop-in replacement for the abandoned BeehivesPro 1.1.2 plugin: existing in-world beehives and stored items keep working after the swap.

## Origin

This is a clean-room rebuild. The behaviour of the original plugin was recovered by decompiling its public release jar with CFR, then **rewritten** in our own style under our own package namespace (`net.auroric.apiary`). The decompiled output is not in the tree; class boundaries, control flow, and identifiers are our own.

If you ever re-decompile to study the original behaviour, only use the official release jar (~30 KB, 7 class files). Malware-injected variants of the BeehivesPro jar exist in the wild — they are several MB and contain classes under namespaces unrelated to the plugin. Verify class count with `unzip -l` before opening any sample.

## Build

```sh
mvn package
```

Output: `target/Apiary-<version>.jar`. Java 21 is required (Paper 1.21+ no longer supports 17). Paper API is resolved from `repo.papermc.io`. No shaded dependencies, no annotation processors — `mvn package` is the entire build.

## Layout

```
src/main/
├── java/net/auroric/apiary/
│   ├── Apiary.java               # main; loads/validates config, registers commands+listener, runs auto-migration
│   ├── ApiaryConfigException.java
│   ├── Migration.java            # legacy BeehivesPro config importer
│   ├── beehive/
│   │   ├── Beehive.java          # instance: name, level, bees; spawn/place/give
│   │   └── BeehiveManager.java   # static state + identity helpers + level token
│   ├── command/ApiaryCommand.java    # /apiary entry point; routes subcommands
│   ├── listener/BeehiveListener.java # event handlers (place/break/interact/inv-click/rename/burn)
│   └── recipe/LevelTokenRecipe.java  # shaped + shapeless level-token crafting recipes
└── resources/{plugin.yml, config.yml}
```

## Wire-compatibility constraints (v1)

These are deliberate. **Don't change them in v1.** A future v2 may break them behind an in-world migration step.

- **CustomModelData**: items use the magic number `555666555` (`BeehiveManager.BEEHIVE_MODEL_DATA`). This is how the original plugin identified its items, so existing inventories and chests still resolve.
- **Marker mechanism**: placed beehives store name + level on an invisible marker ArmorStand at offset `+0.5/+1.0/+0.5` from the block, with `customName` in the form `<name> - Lv.<n>`. Worlds saved by the original plugin rely on this layout.
- **Name/level format**: ` - Lv.N` delimiter. `BeehiveManager.parseLevel` is tolerant of malformed input (returns 0 rather than throwing) because the input is player-craftable.

## Native vs. legacy command + permission surfaces

Native (always on):
- Commands: `/apiary` with aliases `/bee`, `/bh`
- Permissions: `apiary.admin`, `apiary.default`, `apiary.reload`, `apiary.give`, `apiary.addtokens`, `apiary.migrate`, `apiary.harvest-honey-blocks`

Legacy compatibility (toggled by the `legacy-compatibility` section in `config.yml`, both default `true`):
- `command-aliases`: also registers `/beehivespro`, `/bp`, `/bhp`
- `permissions`: also honours `beehivespro.*` nodes

Permission resolution goes through `Apiary#hasPermission(target, suffix)`. Use that helper from command/listener code, never raw `hasPermission()` calls — the legacy fallback stays centralised that way.

## Migration utility

`Migration.importLegacyConfig(plugin, force)`:
- Reads `plugins/BeehivesPro/config.yml` (the standard Bukkit data path)
- Copies it to `plugins/Apiary/config.yml`
- On `force=true`, backs up the existing target as `config.yml.pre-migrate-<timestamp>`
- Runs automatically on first enable when no Apiary config exists
- Exposed manually as `/apiary migrate [--force]`

This does NOT migrate in-world data — wire-compat handles that. Migration is just for config.

## Conventions

- Target Java 21. No back-compat shims.
- Modern Java idioms welcome: `var`, switch expressions, pattern matching for `instanceof`, streams.
- Legacy `ChatColor.translateAlternateColorCodes` is used throughout for config messages. Deprecated but functional on Paper 1.21; an Adventure Components migration is planned for v2.
- No comments narrating what code does. Comments only for non-obvious reasoning (e.g. the "player-craftable, never throw" note in `parseLevel`).
- Keep `plugin.yml` permission nodes in sync with what the code actually checks.

## v2 differentiation plan (not yet implemented)

When the v1 deploy is settled and we're ready to make the implementation visibly its own rather than functionally identical to the decompile:

- Replace ArmorStand markers with PersistentDataContainer keys on the beehive block, with a one-shot in-world migration that converts existing markers
- Replace ArmorStand holograms with `DisplayEntity` (1.19.4+)
- Migrate string-based chat output to Adventure Components
- Add tab completion for `/apiary` subcommands and preset names
- For new installs, flip both `legacy-compatibility.*` flags to default `false`; keep `true` only as an explicit upgrade path
