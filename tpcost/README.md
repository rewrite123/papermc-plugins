# tpcost

**Teleport to your friends, but pay for it with XP levels.** Requests must be
accepted, prices grow with distance, and admins can tune every number live.

Built for **Paper 26.3 / Java 25**. No other plugins are needed.

![Features at a glance](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/tpcost/docs/images/features.png)

> **In game:** type `/help tpcost` for a full guide with every command. Admins
> also see the admin settings. It ends with clickable links to this page and to the
> [issue tracker](https://github.com/rewrite123/papermc-plugins/issues).

## Contents

- [Quick start](#quick-start)
- [How much does it cost?](#how-much-does-it-cost)
- [Commands](#commands)
- [Permissions](#permissions)
- [Privacy: closing requests and hiding prices](#privacy-closing-requests-and-hiding-prices)
- [Admin settings](#admin-settings)
- [Install](#install)
- [Links and support](#links-and-support)

## Quick start

![How a paid teleport works](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/tpcost/docs/images/request-flow.png)

1. **Ask:** `/tpa Alex` sends Alex a request. It expires after **60 seconds**.
2. **Approve:** Alex clicks **[Accept]** in chat or types `/tpaccept`.
   **[Deny]** or `/tpdeny` refuses it.
3. **Pay on arrival:** the cost is recalculated from where you both stand *right
   now*. You are teleported and only then lose the levels. If the teleport
   fails or is cancelled, nothing is charged.

![What players see in chat](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/tpcost/docs/images/chat-request.png)

The **[Accept]** and **[Deny]** buttons behave exactly like typing the command.
Clicking an old message after the request has expired or been cancelled does nothing.

## How much does it cost?

![Default teleport cost by distance](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/tpcost/docs/images/cost-by-distance.png)

By default, every **1,000 blocks costs 3 levels**, rounded up. The price never
drops below **3** and the distance part stops growing at **25**. Teleporting
between different worlds adds **+3** on top of the cap.

| Distance (blocks) | Same world | Different worlds |
| ---: | :---: | :---: |
| 0 – 1,000 | 3 | 6 |
| 2,000 | 6 | 9 |
| 3,000 | 9 | 12 |
| 3,330 | 10 | 13 |
| 5,000 | 15 | 18 |
| 8,000 | 24 | 27 |
| farther than 8,000 | **25** (cap) | **28** |

```text
base cost  = min(maximum-cost, max(minimum-cost, ceil(distance × levels-per-1000-blocks ÷ 1000)))
total cost = base cost + (different worlds ? cross-world-surcharge : 0)
```

- Distance is the straight-line **3D** distance between both players' X/Y/Z.
- Across worlds, raw coordinates are compared as if both players were in the
  same world, **without Nether scaling**, and then the surcharge is added.
- Only the requesting player pays. Payment is whole **levels**, so partial
  progress toward your next level is kept.
- Creative and spectator players pay too; there is no free operator bypass.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/tpa <player>` | everyone | Ask to teleport to a player. A new request replaces your previous one. |
| `/tpaccept [player]` | everyone | Accept a request; you can also click **[Accept]**. |
| `/tpdeny [player]` | everyone | Deny a request; you can also click **[Deny]**. |
| `/tpcancel` | everyone | Cancel your own outgoing request. |
| `/tpclose` | everyone | Refuse all incoming requests and cancel the pending ones. |
| `/tpopen` | everyone | Accept incoming requests again. |
| `/tpcost <player>` | everyone | Price check, **only if an admin enables it**. |
| `/tpcostadmin [setting] [value]` | admins | View or change prices and price lookup. |
| `/help tpcost` | everyone | In-game guide with links back here. |

- If several requests are waiting for you, name the player in `/tpaccept` or `/tpdeny`.
- Player names are case-insensitive and tab-complete.
- If another plugin also uses these command names, use the namespaced form, for example `/tpcost:tpa Alex`.

**Requests are cancelled when:**

| Situation | Charged? |
| --- | :---: |
| 60 seconds pass, the target denies, or you `/tpcancel` | no |
| Either player disconnects or the target uses `/tpclose` | no |
| Either player is dead or riding a vehicle at acceptance | no |
| You no longer have enough levels at acceptance | no |
| Another plugin cancels the teleport (`PlayerTeleportEvent`, `PLUGIN` cause) | no |
| Teleport succeeds | **yes** |

## Permissions

| Permission | Default | Grants |
| --- | --- | --- |
| `tpcost.use` | everyone | All player commands above |
| `tpcost.admin` | operators | `/tpcostadmin` and the admin section of `/help tpcost` |

## Privacy: closing requests and hiding prices

**Do not disturb.** `/tpclose` refuses every incoming request. Pending requests
are cancelled and their senders are told. You can still send your own requests.
`/tpopen` turns requests back on. The choice is saved by player UUID in
`plugins/tpcost/request-preferences.yml` and survives disconnects and restarts.

**Price lookup is off by default**, including for operators. An exact price
reveals roughly how far away someone is, so repeated quotes could be used to
triangulate their position. While lookup is off:

- `/tpcost <player>` returns no price and does not suggest player names.
- `/tpa` does not show the price before acceptance, even in "not enough XP"
  messages. Affordability is checked when the target accepts.
- Teleports still cost the configured levels, and the price is shown on success.

This blocks quotes nobody approved. It does not hide distance from someone whose
request you accept. An admin can turn lookups back on with
`/tpcostadmin cost-command-enabled true`.

## Admin settings

Operators, the console, and anyone with `tpcost.admin` can change prices **without
a restart**. Changes are saved to `plugins/tpcost/config.yml` and apply right away
to quotes, new requests, and pending requests when they are accepted.

| Setting | Default | Meaning |
| --- | :---: | --- |
| `minimum-cost` | `3` | Lowest price for any same-world teleport |
| `levels-per-1000-blocks` | `3` | Price added per 1,000 blocks, rounded up |
| `maximum-cost` | `25` | Cap on the distance price (before the world surcharge) |
| `cross-world-surcharge` | `3` | Added when players are in different worlds |
| `cost-command-enabled` | `false` | Allow `/tpcost` quotes and show prices in `/tpa` |

```text
/tpcostadmin                              show every setting
/tpcostadmin maximum-cost                 show one setting
/tpcostadmin maximum-cost 30              change and save it
/tpcostadmin cost-command-enabled true    allow price lookups
```

**Rules:**

- Prices are whole numbers from 0 to 2,147,483,647.
- `minimum-cost` cannot be higher than `maximum-cost`. Raise the maximum first,
  or lower the minimum first.
- `maximum-cost + cross-world-surcharge` must also fit within 2,147,483,647.
- Setting the minimum, rate and surcharge all to `0` makes teleports free.
- If saving fails, the command reports an error and nothing changes.
- After editing `config.yml` by hand, restart the server. Invalid values stop
  the plugin from starting rather than silently changing prices.

<details>
<summary>Default <code>config.yml</code></summary>

```yaml
minimum-cost: 3
levels-per-1000-blocks: 3
maximum-cost: 25
cross-world-surcharge: 3
cost-command-enabled: false
```

</details>

## Install

1. Download the latest jar from [Hangar](https://hangar.papermc.io/rewrite123/tpcost).
2. Put it in your server's `plugins/` folder and restart. The server must run Java 25.
3. Optional: adjust prices with `/tpcostadmin`.

<details>
<summary>Build from source and publish</summary>

```sh
./gradlew build          # jar in build/libs/, runs the test suite
```

Gradle runs on Java 17+ and downloads a Java 25 toolchain if needed. Tests mock
the Paper API; they do not replace a multiplayer smoke test on your server.

Publishing uses [HangarMC's Gradle plugin](https://github.com/HangarMC/hangar-publish-plugin).
Set a unique `version` in `gradle.properties` and supply a Hangar API key, with
**create_version** and **edit_page** permissions, as `HANGAR_API_TOKEN`. Never commit it.

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar \
  -PhangarChangelog="What changed"
```

This README is synced as the Hangar description. Images use absolute
`raw.githubusercontent.com` URLs so they render on both GitHub and Hangar. Regenerate
them with `python3 tools/docs/tpcost.py` from the repository root. A manual
`Publish tpcost to Hangar` GitHub Actions workflow is also available; it needs
the `HANGAR_API_TOKEN` repository secret.

</details>

## Links and support

- **Source and full guide:** [github.com/rewrite123/papermc-plugins/tree/main/tpcost](https://github.com/rewrite123/papermc-plugins/tree/main/tpcost)
- **Download:** [hangar.papermc.io/rewrite123/tpcost](https://hangar.papermc.io/rewrite123/tpcost)
- **Report a bug or request a feature:** [GitHub issues](https://github.com/rewrite123/papermc-plugins/issues)
