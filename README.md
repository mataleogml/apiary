# Apiary

A Bukkit/Paper plugin for **Minecraft 1.21.x** that adds tiered, level-up-able beehives with named bees, configurable harvest yields, and craftable level tokens. Designed as a drop-in replacement for the abandoned BeehivesPro 1.1.2 plugin — existing in-world beehives and stored items keep working after the swap.

[![Build](https://img.shields.io/github/actions/workflow/status/auroric/apiary/build.yml?branch=main)](../../actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/auroric/apiary)](../../releases)

---

## Contents

- [Features](#features)
- [Installation](#installation)
- [Migrating from BeehivesPro](#migrating-from-beehivespro)
- [How it works](#how-it-works)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration reference](#configuration-reference)
- [Building from source](#building-from-source)
- [Roadmap](#roadmap)
- [Acknowledgements](#acknowledgements)

---

## Features

- **Tiered beehives, max level configurable (default 10).** Each level multiplies what the hive yields per harvest.
- **Three harvest paths** — shears yield honeycombs, glass bottles yield honey bottles, a configurable tool (default golden hoe) yields honey blocks (unlocked at a configurable minimum level).
- **Level-up tokens.** Right-click an Apiary beehive (block or item) with a token to raise its level. Token item type, display name, lore, and crafting recipe are all configurable; both shaped and shapeless recipes are supported.
- **Named bees.** Bees spawned with a hive carry preset names; players can rename them with name tags if `enable-bee-renaming` is on, and renames are formatted with the hive's name behind them.
- **Presets.** Pre-build beehive configurations (name, level, bee names) in `config.yml` and hand them out with `/apiary give <player> <preset> <amount>`.
- **Vanilla upgrade.** Optional: right-click a vanilla beehive with a level token to convert it into a configured preset.
- **Drop-in compat for BeehivesPro.** Wire-compatible with the original plugin's data format; legacy command aliases and `beehivespro.*` permission nodes are honoured by default.
- **Auto-migration** of `plugins/BeehivesPro/config.yml` → `plugins/Apiary/config.yml` on first run.

## Installation

Requires **Paper 1.21+** (or a Paper fork like Purpur/Leaf) and **Java 21**.

1. Download `Apiary-<version>.jar` from the [releases](../../releases) page.
2. Drop it into your server's `plugins/` folder.
3. Start the server — a default `plugins/Apiary/config.yml` is generated on first run.
4. Edit the config, then run `/apiary reload` (or restart) to apply.

## Migrating from BeehivesPro

Apiary is wire-compatible with BeehivesPro 1.1.2 data:

- **Items** already in player inventories, chests, or shulker boxes keep working — they're identified by the same `CustomModelData` value the original plugin used.
- **Placed beehives** in worlds keep working — Apiary uses the same invisible marker ArmorStand mechanism for storing the hive's name and level.
- **Permission grants** for `beehivespro.*` continue to work, controlled by the `legacy-compatibility.permissions` flag in config (default on).
- **Command aliases** `/bp`, `/bhp`, `/beehivespro` continue to work, controlled by `legacy-compatibility.command-aliases` (default on).

### Recommended migration steps

1. Stop the server.
2. Remove `plugins/BeehivesPro-*.jar` (leave the `plugins/BeehivesPro/` folder where it is for now — Apiary will read its config).
3. Drop in `Apiary-<version>.jar`.
4. Start the server. On first enable, Apiary detects `plugins/BeehivesPro/config.yml` and copies it into `plugins/Apiary/config.yml` automatically, logging the import.
5. Test in-game: existing beehives should respond to right-click and shears/bottles; players' inventory items should behave as before.
6. Once you've verified the swap, delete the old `plugins/BeehivesPro/` folder.
7. Optional cleanup: in `plugins/Apiary/config.yml`, set both flags under `legacy-compatibility:` to `false` once you've renamed any LuckPerms grants from `beehivespro.*` to `apiary.*` and trained players on the new `/apiary` command.

### Manual migration

If auto-migration didn't apply (config files in non-standard locations, prior partial migration, etc.):

```
/apiary migrate          # Imports if no Apiary config exists yet
/apiary migrate --force  # Overwrites the current Apiary config (a timestamped
                         # backup of the displaced file is kept in plugins/Apiary/)
```

### Security note on the original BeehivesPro jar

Some copies of the BeehivesPro jar circulating online are several MB in size and contain classes under unrelated namespaces — those are tampered builds and have been associated with supply-chain compromises. The legitimate release is about 30 KB with 7 class files. If you're cleaning up a server that may have run a tampered jar, quarantine the old plugin folder structure but understand that the **config file itself is safe to import** — it's plain YAML with no executable content.

## How it works

A beehive's identity has two parts:

1. **A unique `CustomModelData` value** on the item stack — this is what distinguishes an Apiary beehive item from a vanilla one. Items that have this value plus a display name in the format `<name> - Lv.<n>` are recognised as Apiary beehives in inventories.
2. **An invisible marker ArmorStand** floating at `(+0.5, +1.0, +0.5)` offset from a placed beehive block. Its `customName` holds the encoded `<name> - Lv.<n>` for the placed block; the block itself is a vanilla beehive.

When a player right-clicks a beehive, Apiary looks for a marker at that offset. If found, the click is intercepted: the player gets the level-up flow, the honey harvest flow, or a status message depending on what they're holding and the hive's current honey level.

When a player breaks an Apiary beehive, the block drop is suppressed and an item stack is given to the player encoding the hive's current name and level — so it can be re-placed elsewhere without losing progression.

The level-token item is a normal item (default: a honeycomb) with a hidden enchantment glow, configurable name/lore, and an optional crafting recipe. Tokens are consumed one at a time on each level-up.

## Commands

| Command | Description |
|---|---|
| `/apiary help` | Show the help menu |
| `/apiary reload` | Reload `config.yml` and re-register the level-token recipe |
| `/apiary give <player> <preset> <amount>` | Give a player a preset beehive from the config |
| `/apiary give <player> <name> <level> <bee1,bee2,bee3> <amount>` | Give a player a freshly-built beehive |
| `/apiary addtokens <player> <amount>` | Give a player level-up tokens |
| `/apiary migrate [--force]` | Import config from a BeehivesPro install |

**Native aliases**: `/bee`, `/bh`.
**Legacy aliases** (when enabled): `/beehivespro`, `/bp`, `/bhp`.

## Permissions

| Node | Default | Purpose |
|---|---|---|
| `apiary.admin` | op | Full access — grants every other node |
| `apiary.default` | all | View `/apiary help` |
| `apiary.reload` | op | Run `/apiary reload` |
| `apiary.give` | op | Run `/apiary give` |
| `apiary.addtokens` | op | Run `/apiary addtokens` |
| `apiary.migrate` | op | Run `/apiary migrate` |
| `apiary.harvest-honey-blocks` | all | Harvest honey blocks from leveled hives |

When `legacy-compatibility.permissions` is `true` (default), the corresponding `beehivespro.*` nodes are also honoured. Either prefix grants the same access.

## Configuration reference

The file is `plugins/Apiary/config.yml`. Reload with `/apiary reload`.

### Legacy compatibility

```yaml
legacy-compatibility:
  command-aliases: true   # Accept /bp, /bhp, /beehivespro
  permissions: true       # Accept beehivespro.* permission nodes
```

Set both to `false` for new installs that have no BeehivesPro history.

### Beehive limits and level tokens

```yaml
beehive-max-level: 10
beehive-level-token-item: HONEYCOMB
beehive-level-token-name: "&6[&eApiary Level Token&6]"
beehive-level-token-lore:
  - ""
  - "&7Use this on a &6Beehive &7to level it up!"

enable-beehive-level-tokens-crafting: true
beehive-level-token-crafting-recipe:
  shapeless-recipe: false   # true = order in crafting grid doesn't matter
  recipe:
    1: HONEY_BLOCK
    2: HONEY_BLOCK
    3: HONEY_BLOCK
    4: HONEY_BLOCK
    5: DIAMOND_BLOCK
    6: HONEY_BLOCK
    7: HONEY_BLOCK
    8: HONEY_BLOCK
    9: HONEY_BLOCK
```

For shapeless recipes, use a `<material>: <count>` map under `recipe:` instead of slot positions.

### Harvest yields

```yaml
# Shears → honeycomb (always available)
beehive-shear-message: "&7You harvested &e%AMOUNT% &6Honeycombs"

# Glass bottle → honey bottles (always available)
beehive-bottle-message: "&7You harvested &e%AMOUNT% &6Honey Bottles"

# Configurable tool → honey blocks (unlocked at minimum level)
harvest-honey-blocks: true
harvest-honey-blocks-minimum-level: 4
harvest-honey-blocks-start-amount: 1   # Yield at minimum level; +1 per level above
harvest-honey-blocks-tool: GOLDEN_HOE
harvest-honey-block-message: "&7You harvested &e%AMOUNT% &6Honey Blocks"
```

### Bee names

```yaml
enable-bee-renaming: true
bee-name-format: "%BEE_NAME% &f~ %BEEHIVE_NAME%"   # Set "" to hide bee names
bee-renamed-hive: " &f~ %BEEHIVE_NAME%"            # Appended when a player renames
beehive-item-bees-lore: "&6* &e&nBees&r &6*"        # Lore header above bee names on the item
```

### Vanilla beehive upgrade

```yaml
enable-vanilla-beehive-upgrade: true
vanilla-beehive-upgrade-preset: "default"
vanilla-beehive-spawns-bees-when-upgraded: true
```

If enabled, right-clicking a vanilla beehive with a level token converts it into an instance of the named preset.

### Animations and per-level-up commands

```yaml
beehive-harvest-animation: true     # Falling-honey particles scaled by level
beehive-level-up-animation: true    # Totem particles scaled by level

beehive-level-up-commands:           # Run as console; %PLAYER% is substituted
  - ""
  - ""
```

> **Security note.** Each entry in `beehive-level-up-commands` is executed *as console* on every level-up. Don't put dangerous commands here (`op`, `lp user … parent set …`, etc.) — anyone who can level up a beehive triggers them. Empty strings are ignored.

### Presets

```yaml
beehive-presets:
  default:
    beehive-name: "&6Beehive"
    level: 1
    bees: ["&eBee", "&eBee", "&eBee"]
  super:
    beehive-name: "&4&m--&r &6SUPER HIVE &4&m--&r"
    level: 10
    bees: ["&eSUPER BEE", "&eSUPER BEE", "&eSUPER QUEEN"]
```

Each preset must have at most 3 bees. The preset name is what's used in `/apiary give <player> <preset> <amount>`.

## Building from source

Requires **JDK 21+** and Maven.

```sh
mvn package
```

Output: `target/Apiary-<version>.jar`.

CI builds run on every push to `main` and every pull request (see [.github/workflows/build.yml](.github/workflows/build.yml)). Tagged commits (`v*`) trigger a release build and attach the jar to a GitHub Release.

To cut a release:

```sh
mvn versions:set -DnewVersion=1.0.1
git commit -am "Bump version to 1.0.1"
git tag v1.0.1
git push --tags
```

Dependabot watches Maven dependencies and GitHub Actions versions weekly — see [.github/dependabot.yml](.github/dependabot.yml).

## Roadmap

**v1 (current)** — drop-in compatible with BeehivesPro 1.1.2 data and permissions. Stable on Paper 1.21.x.

**v2 (planned)** — visible differentiation from the original implementation:

- Replace ArmorStand markers with `PersistentDataContainer` keys on the beehive block, with an in-world migration that converts existing markers
- Replace string-based `customName` holograms with `DisplayEntity` (1.19.4+)
- Migrate chat output from legacy `ChatColor` to Adventure Components
- Tab completion for `/apiary` subcommands and preset names
- For new installs, flip both `legacy-compatibility.*` flags to default `false`; `true` remains the explicit upgrade path

## Acknowledgements

The mechanic design — tiered beehives, level tokens, named bees, configurable harvest — was first implemented in the BeehivesPro plugin by OliPulse (2020), now abandoned and incompatible with modern Paper versions. Apiary is an independent rebuild for Paper 1.21+; no code from the original is reused. If you're the original author and would like attribution adjusted, please open an issue.
