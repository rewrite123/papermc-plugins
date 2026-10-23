# votersrights

Player-approved server actions for **Paper 26.3 / Java 25**. No runtime dependencies.

## Voting

Use `/vote <action>` to propose an action, `/vote yes` or `/vote no` to cast a
ballot, and `/vote` to see the current vote or enabled actions. Chat includes
clickable voting buttons.

- One server-wide vote at a time, lasting **60 seconds**.
- At least **3 eligible players** must be online to start a vote.
- The proposer automatically votes yes. Each eligible player gets one ballot;
  ballots cannot be changed.
- The electorate is the eligible online players when the vote starts. The
  default threshold is **at least 50%, rounded up**: 2 yes out of 3 or 4,
  3 out of 5. Abstentions are not yes votes.
- A vote passes immediately when it reaches the threshold. Otherwise it fails
  at expiry. Joining does not add someone to an existing electorate; leaving
  does not shrink it or erase a ballot. Reconnecting players in that electorate
  can vote if they have not already done so.
- Blacklisted players cannot vote. When whitelist mode is enabled, players must
  also be whitelisted. Blacklist takes precedence. The `votersrights.vote`
  permission (default: everyone) is required even for listed players.
- No additional proposal can start while a passed restart/timeout is in its
  warning period. Active votes are cancelled on plugin shutdown or admin changes;
  already passed delayed actions remain saved.

## Actions

Action names are case-insensitive, and tab completion lists enabled actions.

| Command | Result |
| --- | --- |
| `/vote restart` | Restart after a **2-minute warning**; default cooldown **20 minutes** after execution |
| `/vote lowerViewDistance` | Lower each world's view distance by 2 chunks |
| `/vote increaseViewDistance` | Increase each world's view distance by 2 chunks |
| `/vote lowerSimulationDistance` | Lower each world's simulation distance by 2 chunks |
| `/vote increaseSimulationDistance` | Increase each world's simulation distance by 2 chunks |
| `/vote timeout <player>` | Give an online player a **30-second warning**, then disconnect them for **3 minutes** |
| `/vote difficulty <difficulty>` | Set difficulty in all worlds; defaults allow easy, normal, hard |
| `/vote setTimeDay` | Set all worlds to time 1000 |
| `/vote setTimeNight` | Set all worlds to time 13000 |

Distance results are clamped to admin limits: view **6-18**, simulation **4-12**
by default. If a world's existing distance is outside those limits, the next
distance vote brings it within the limits, even if that means moving farther
than 2 chunks or correcting in the other direction. Votes are rejected if no
world would change. Distance votes affect current world settings, not
`server.properties` or Paper configuration files; restarting can restore the
server's configured distances. Per-player/client distance limits can still
reduce what an individual sees.

Timeouts block reconnection during their duration, without using or overwriting
the server's ban list. Protection starts when the timeout **ends**, not when the
player eventually reconnects: default **30 minutes** before they can be timed
out again. Logging out during the warning does not escape it. A target must
still be online when the vote passes. No automatic admin immunity is applied.

Restart warnings, cooldown timestamps, timeouts, and protection are saved
atomically in `plugins/votersrights/state.yml`. They use wall-clock time and
survive server restarts. A warning still pending on startup resumes from its
original deadline; an overdue restart executes on the next scheduler tick.
The restart cooldown is saved before dispatching the native console `restart`
command. **Configure and test your Paper restart script/hosting setup first**:
this plugin cannot make a host restart if that setup does not support it.
An unhandled restart command is reported explicitly; its cooldown remains in place.
The cooldown can be configured to zero.

## Admin controls

Operators, console, and players with `votersrights.admin` can manage settings.
Changes save and apply immediately. Invalid changes leave active settings
unchanged; load/save failures are logged and reported. Any in-progress vote is
cancelled when settings are successfully changed or reloaded. Existing timeout
durations/protection are not retroactively changed; a queued restart uses the
cooldown setting in effect when it executes.

```text
/votersrights show
/votersrights set pass-percentage 50
/votersrights set minimum-online-players 3
/votersrights set restart-cooldown-minutes 20
/votersrights set timeout-duration-minutes 3
/votersrights set timeout-protection-minutes 30
/votersrights set minimum-view-distance 6
/votersrights set maximum-view-distance 18
/votersrights set minimum-simulation-distance 4
/votersrights set maximum-simulation-distance 12
/votersrights allow restart false
/votersrights allow timeout true
/votersrights difficulties easy normal hard
/votersrights set whitelist-enabled true
/votersrights whitelist add PlayerName
/votersrights whitelist remove PlayerName
/votersrights blacklist add PlayerName
/votersrights blacklist remove PlayerName
/votersrights whitelist
/votersrights blacklist
/votersrights reload
```

Player list commands accept online/cached names or UUIDs and store UUIDs, so name
changes do not reset voting rights. No remote username lookup is performed.
`difficulties` replaces the allowed list; peaceful can be explicitly allowed.
`allow` works with every built-in or custom action.

Percentage is a whole number **1-100**, minimum players **1 or greater**,
timeout duration **1-525600 minutes**, cooldown/protection **0-525600 minutes**.
Distance limits must be **2-32**, with minimum no greater than maximum.
Raise the maximum before raising the minimum above it; lower the minimum first
when reducing the maximum below it.

The generated `plugins/votersrights/config.yml` has every setting. After manual
edits, run `/votersrights reload`. Invalid configuration prevents startup; a
failed reload preserves the previous active settings.

### Custom vote actions

Admins can register fixed console commands that players may vote to execute:

```text
/votersrights action add announcement say The players have voted!
/vote announcement
/votersrights allow announcement false
/votersrights action remove announcement
```

Repeating `action add` for the same name appends another command, executed in
order. You can also edit `custom-actions` in the configuration:

```yaml
custom-actions:
  announcement:
    enabled: true
    commands:
      - "say The players have voted!"
```

Names use lowercase letters/digits/underscore/hyphen, start with a letter, and
are at most 32 characters. Built-in action names and yes/no are reserved.
Commands have no leading slash, newlines, or `{placeholders}`. Players cannot
supply arguments or substitute command text. **Only add trusted commands**:
these execute with console privileges, and can deliberately bypass built-in
action safeguards (for example, a custom stop/restart command). If a command is
unhandled, execution stops with an explicit failure; earlier commands are not
rolled back. A command handler that returns success is responsible for its own
actual outcome.

## Build, install, and publish

```sh
./gradlew build
```

Copy `build/libs/votersrights-0.1.0.jar` into the Paper server's `plugins/`
directory and restart. Java 25 is required by the server; Gradle provisions the
build toolchain automatically. Unit/MockBukkit tests run with the build; they do
not replace a real multiplayer smoke test or a host restart test.

Publishing uses the official
[Hangar Gradle plugin](https://github.com/HangarMC/hangar-publish-plugin).
Supply an API key with `create_version` and `edit_page` permissions in
`HANGAR_API_TOKEN`, then:

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar \
  -PhangarChangelog="Initial release: configurable player votes."
```

Use a unique version in `gradle.properties` for every release. The manual GitHub
publish workflow uses the `HANGAR_API_TOKEN` repository secret. The Hangar main
page is synchronized from this README. The original project icon is
`assets/icon.png`, generated by the repository's shared icon tool.
