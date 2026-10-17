# How to Become a Contributor

Welcome! Bug fixes, new features, and documentation improvements are all welcome.

- [Before You Start](#before-you-start)
- [Setting Up](#setting-up)
- [Commit Convention](#commit-convention)
- [Project Layout](#project-layout)
- [Resources](#resources)
- [The Extension System](#the-extension-system)
- [Startup Sequence](#startup-sequence)
- [Command Registration](#command-registration)
- [Package Responsibilities](#package-responsibilities)
- [Web Panel](#web-panel)
- [Development Notes](#development-notes)
- [Build and Verify](#build-and-verify)
- [Testing Against a Real Database](#testing-against-a-real-database)

## Before You Start

- **Java 17** — this project requires Java 17 or newer;
- **Maven** — for dependency management and builds;
- **Git**.

If you want to *write an extension* for Lengbanlist rather than change the core, read
[`lengbanlist-api/README.md`](../lengbanlist-api/README.md) instead: the extension contract,
a complete example, and the method reference all live there.

## Setting Up

1. **Clone the repository**:

   ```bash
   git clone https://github.com/Serendisand/Lengbanlist.git
   cd Lengbanlist
   ```

2. **Build once** to make sure your environment is fine:

   ```bash
   mvn clean install
   ```

   The artifact lands in `Lengbanlist - main/target/Lengbanlist-<version>.jar`.

3. Drop the jar into a test server's `plugins/` folder and restart.

## Commit Convention

One line per commit, 50 characters or fewer, in the form `prefix: what changed`:

```
fix: cancel pending tasks when a player quits
refactor: split config loading
```

Pick one of the prefixes below — releases are grouped by them:

| Category | Prefixes |
| --- | --- |
| New feature | `feat` `add` `feature` |
| Bug fix | `fix` `hotfix` `bug` |
| Improvement | `improve` `refactor` `perf` |
| Other | `docs` `style` `chore` `test`, or anything that matches nothing above |

**One commit, one change.** Unrelated edits belong in separate commits, not in the same one.

## Project Layout

A multi-module Maven build, parent POM `Lengbanlist-Parent`:

```text
Lengbanlist/
├── lengbanlist-api/          # Contract module: extension interfaces, immutable data objects, Model
├── Lengbanlist - main/       # The plugin itself (artifactId: Lengbanlist)
└── docs/
```

- `lengbanlist-api` has **zero third-party dependencies** (spigot-api, `provided`, only).
  Extensions depend on it with `provided` scope too, and at runtime they use the copy the core
  loads — if an extension shades `org/leng/api` or `org/leng/object` into its own jar, a second
  copy of the interfaces appears and type checks on objects returned by the core fail with
  `ClassCastException`, which is painful to track down.
- The plugin compiles against the **lower bound** of the supported range (1.17.1), so a single
  jar covers 1.17.1 ~ 26.3+. Don't reach for APIs newer than 1.17.1 unless you add a reflective
  fallback.

Plugin package layout (`Lengbanlist - main/src/main/java/org/leng/`):

```text
├── Lengbanlist.java              # Main class: lifecycle, config, command/listener registration
├── command/                      # Command implementations; CommandRegistry derives the command table
├── extension/                    # Extension registry, unified gating, built-in provider declarations
│   └── lua/                      # Lua subsystem: sandbox, loader, command bridge, event bridge, facade
├── gui/                          # Chest GUI sessions and chat wizards
├── integration/                  # Character models, themes, update checks, IP geo, webhooks, PAPI
├── listener/                     # Player, chat, GUI, and OP-join listeners
├── net/                          # Outbound network entry point (downloads, mirror chain, HTTP)
├── service/                      # Managers: bans, mutes, warnings, reports, appeals, audit, sync…
├── storage/                      # Database connections and dialects, caches, identity, migrations
├── util/                         # Time, Folia-safe scheduler, text, IP matching, bStats
└── web/                          # Web panel HTTP API
```

Data objects (`BanEntry`, `MuteEntry`, `WarnEntry`, `ReportEntry`, `AppealEntry`, …) and the
`Model` interface live in `lengbanlist-api` under `org.leng.object` / `org.leng.models`: they are
part of the contract, shared by the core and by extensions.

## Resources

`Lengbanlist - main/src/main/resources/`:

| File | Contents |
| --- | --- |
| `plugin.yml` | The `/lban` command, permission nodes, version placeholder, Folia support flag. Standalone commands (`/ban`, `/mute`, …) are **not** declared here — `CommandRegistry` registers them at runtime based on feature toggles, so a disabled feature frees its command name for other plugins |
| `config.yml` | Feature toggles, broadcast, update checks, download mirrors |
| `storage.yml` | Database connection, retention policy, `server-name` for multi-server setups |
| `broadcast.yml` | Broadcast texts |
| `chatconfig.yml` | Chat filter and moderation settings |
| `eula.yml` | EULA acceptance, checked before the plugin enables — see [Usage Restrictions](Mustn't_en.md) |
| `models/` | Built-in character models: `_base.yml` (base + custom template), `default.yml`, `english.yml` |
| `web/` | Static pages and assets for the web panel |
| `extensions/` | Bundled official Lua extensions (`tp.lua`, `staffchat.lua` and their config folders), extracted only when the data folder's `extensions/` directory is **entirely absent** |

## The Extension System

Low-frequency features no longer live in the core; they ship as extensions. **Built-in
providers, Java extensions, and Lua scripts all walk the same registration path**
(`ExtensionRegistry`), so the command table, help filtering, tab completion, permissions, and
gating stay consistent automatically (`/lban ext` only manages the Lua ones).

| Form | Entry point | Where the toggle lives |
| --- | --- | --- |
| Built-in provider | `BuiltinExtensions.declarations()` | `features.<key>` in the core `config.yml` |
| Java extension | A standalone Bukkit plugin calling `CoreService.enable()` | The extension's own `plugins/<name>/config.yml` (the base class implements `SelfGatedExtension`) |
| Lua extension | `extensions/<id>.lua` in the data folder | The extension's own `extensions/<id>/config.yml` |

Gating has exactly one meaning: `ExtensionRegistry.isFeatureActive(key)` = **the owning provider
is enabled AND the toggle is on**. Core code must ask it, not `plugin.isFeatureEnabled()` — the
latter only reads the config toggle and would treat an uninstalled extension as active.

Commands are gated automatically by that rule; listeners and scheduled tasks are not — when an
extension is disabled, the Bukkit plugin itself stays enabled and its registered listeners keep
firing. Extensions must therefore check `context.isFeatureActive()` at event and task entry.

Key classes:

- `ExtensionRegistry` — registry, unified gating, per-extension hook storage
- `CoreService` — registered with Bukkit's `ServicesManager`; the only way an external extension
  reaches the core
- `ExtensionContextImpl` — one context per extension (config, log prefix, scheduler, commands,
  hooks, facades)
- `BuiltinExtensions` — built-in providers and the **single command declaration table**
- `extension/lua/` — the Lua subsystem: sandbox (whitelisted libraries, instruction budget and
  wall-clock timeouts), loader, command bridge, event bridge, facade

## Startup Sequence

1. `onLoad()`: initialise `SchedulerUtils`, check the EULA, load default configs.
2. `onEnable()`: create `ExtensionRegistry` and load built-in providers (commands and
   feature-only keys), register `CoreService` with the `ServicesManager`, then let
   `CommandRegistry` derive the command table from the registry. Java extensions enable
   as separate plugins and hand their commands and hooks in through `CoreService.enable()`.
3. `LuaLoader.loadAll()` scans the data folder and loads Lua extensions.
4. `DatabaseManager` opens the database; legacy YAML data is migrated by `StorageMigrationManager`.
5. Managers are created (`BanManager`, `MuteManager`, `WarnManager`, `ReportManager`,
   `IpAssociationManager`, …) along with `WebServer`.
6. Listeners are registered; auto-broadcast, the web panel, history cleanup, expiry reminders,
   update checks, and bStats start according to config.
7. `onDisable()`: unregister extensions and commands, cancel tasks, stop the web server, save
   broadcast config, close the database.

## Command Registration

There is exactly **one source of truth** for the command table:
`BuiltinExtensions.declarations()`. Each declaration states its feature key once, and
`BuiltinProvider` injects it into every command of that feature; both registration and
`/lban help` filtering derive from it, so the two can never drift apart.

Adding a built-in command touches:

1. the nested class in the matching `XxxCommands` file (one class per command);
2. one row in `BuiltinExtensions.declarations()`: feature / name / permission / usage /
   description / executor factory;
3. the `permissions:` section of `plugin.yml`;
4. the model files, if the command should show up in `/lban help` (the help list is curated by hand).

Standalone commands stay out of `plugin.yml`'s `commands:` section — `CommandRegistry`
registers and unregisters them at runtime based on `features.*`, so a disabled feature doesn't
even occupy the command name. `CommandRegistryTest` cross-checks the declaration table against
`plugin.yml` permissions and `config.yml` toggles; get it wrong and the test goes red.

## Package Responsibilities

### command

| File | Commands | What it does |
| --- | --- | --- |
| `CommandRegistry` | — | Derives the command table from the extension registry: registers/unregisters standalone commands per `features.*`, maintains permissions, usage and the `/lban help` feature mapping, wires extension subcommands into `/lban`'s dispatcher |
| `FeatureCommand` | — | Shell for standalone commands: toggle fallback, permission check, tab-completion forwarding |
| `LengbanlistCommand` | `/lban` | Main entry: broadcast toggle, ban list, reload, ban/unban, help, GUI, model switching, audit, web panel, sponsor links |
| `BanCommands` | `/ban` `/ban-ip` `/setban` `/unban` `/kick` | The ban pipeline: duration and reason parsing, player vs. IP, re-scheduling, unbanning, kicking |
| `MuteCommands` | `/mute` `/unmute` `/listmute` `/allowmsg` | Mutes and message control |
| `WarnCommands` | `/warn` `/unwarn` `/warnmsg` | Warnings, including automatic punishment and auto-unban |
| `FreezeCommands` | `/lban freeze` `/lban unfreeze` | Freeze and unfreeze |
| `QueryCommands` | `/check` `/history` `/getip` `/info` | Punishment state and history, IP geolocation, plugin info |
| `ReportCommands` | `/report` `/admin` | Report flow: submit, review, handle, close |
| `StaffCommands` | `/lban vanish` `/lban rollback` | Vanish and time-ranged rollback (`/sc` moved to a Lua extension) |
| `GuiCommands` | `/alts` `/lban open` | Chest GUIs: admin menu, model picker, same-IP alt lookup |
| `ModelsCommand` | `/lban models` `/lban model` | Cloud character models: list, download on demand, switch |
| `LuaExtCommand` | `/lban ext list\|reload\|enable\|disable` | Lua extension management |

Command classes handle permissions, argument parsing, toggle fallback, and user feedback only —
the actual business logic belongs in `service/`.

### listener

- `PlayerJoinListener` — ban, IP-ban, IP-association and VPN checks on join, plus pending-report hints
- `ChatListener` — mutes, chat filtering, moderation, staff approval
- `OpJoinListener` — async update check for OPs on join, plus pending-report hints
- `ModelChoiceListener` — model picker GUI and model switching
- `FreezeListener` — blocks movement, breaking, interaction, dropping, commands and teleports while frozen
- `MuteCommandBlockListener` — command blocking while muted
- `VanishListener` — visibility maintenance for vanished staff
- `GuiCleanupListener` — GUI session cleanup

### service

- `BanManager` / `MuteManager` / `WarnManager` / `ReportManager` — the four punishment managers
- `AppealManager` — ban appeals
- `AuditManager` / `RollbackManager` — audit log (hash chain) and rollback by actor and time range
- `SyncManager` — data sync across servers
- `IpAssociationManager` / `PlayerIpService` — historical IPs, same-IP alts, proxy/VPN detection
- Weight-based immunity and repeat-offender escalation moved to the `punishpolicy` extension
  (implementing `PunishmentDecisionHook` / `DurationPolicyHook`); the core keeps only the
  fallback used when the hook is absent
- `VanishManager` / `FreezeManager` / `BroadcastManager` / `ExpiryReminderTask` /
  `MuteCommandBlockPolicy` / `BanMutationFeedback`

### storage

- `DatabaseManager` — SQLite / MySQL / MariaDB / PostgreSQL connections, schema, reads and writes
- `DatabaseDialect` / `SchemaMigrations` — dialect differences and schema migrations
- `BanCache` / `WarnCache` — punishment caches
- `PlayerIdentityResolver` — name and UUID resolution
- `StorageMigrationManager` — legacy YAML data → database

### integration

- `ModelManager` / `CustomModel` / `ModelCloudManager` — built-in and custom character models, cloud repo
- `ThemeManager` — interface themes
- `AutoUpdateManager` / `GitHubUpdateChecker` — update checks and installation
- `IpGeoLookup` — IP geolocation
- `WebhookNotifier` — webhook delivery
- `PlaceholderAPIHook` — PlaceholderAPI placeholders

### gui / net / util / web

- `gui/` — `GuiSessionManager` (chest menu sessions), `WizardManager` (chat wizards)
- `net/` — `DownloadService`, `MirrorChain`, `HttpTransport`; all outbound downloads go through here
- `util/` — `SchedulerUtils` (Folia-safe scheduling), `TimeUtils`, `Utils`, `IpMatcher`,
  `PlayerProfileHelper`, `ConsoleText`, `ErrorLog`, `Metrics`
- `web/` — `WebServer` and the various `*Controller` classes, see below

## Web Panel

`web/WebServer.java` runs a REST API on the JDK's `HttpServer`, with JWT auth and rate limiting;
the static pages live in `resources/web/`. The feature is gated by both `features.web` and
`web.enabled`. Requests are handled on HTTP threads — **don't** touch main-thread-sensitive
Bukkit state there; hop back with `SchedulerUtils` when you need to.

## Development Notes

- Give every new feature a toggle under `features.*` in `config.yml`, and check it via
  `getExtensionRegistry().isFeatureActive()`, not `isFeatureEnabled()`.
- Keep command classes thin; persistence and core rules belong in `service/`.
- Database changes must work on all four dialects; prefer handling them centrally in `DatabaseManager`.
- Hop back to the main thread with `SchedulerUtils.runTask()` for main-thread-sensitive work;
  run network and database queries asynchronously where possible.
- Keep update URLs, download filenames and version comparison inside `GitHubUpdateChecker`;
  never hard-code GitHub addresses in commands or listeners.
- Auto-update only installs the new jar and asks for a restart — there is no runtime hot reload.
- A new built-in character model needs a `Model` implementation registered with `ModelManager`,
  and must show up in the model picker GUI; player-made models go in the data folder's `models/`.
- After touching punishment logic, walk the main paths: ban, unban, warn, mute, history.

## Build and Verify

```bash
mvn clean install                          # everything: contract module + plugin
mvn -pl "Lengbanlist - main" -am package   # plugin only
mvn test                                   # tests
```

`CommandRegistryTest` checks the command declaration table against `plugin.yml` and
`config.yml`; `ExtensionRegistryTest` and `LuaExtensionLoadTest` cover extension registration
and Lua loading.

## Testing Against a Real Database

By default the whole test suite runs on a temporary SQLite database and needs nothing external:

```bash
mvn test
```

The MariaDB / PostgreSQL branches (dialect SQL, boolean columns, audit-chain row locking) need a
real database. Point the environment variables at a **throwaway test database** and
`DatabaseManagerNetworkTest` picks them up automatically:

```bash
export LENGBANLIST_TEST_MARIADB_URL="jdbc:mariadb://127.0.0.1:3306/lengbanlist_test"
export LENGBANLIST_TEST_MARIADB_USER="root"
export LENGBANLIST_TEST_MARIADB_PASSWORD="your-password"
export LENGBANLIST_TEST_POSTGRESQL_URL="jdbc:postgresql://127.0.0.1:5432/lengbanlist_test"
export LENGBANLIST_TEST_POSTGRESQL_USER="postgres"
export LENGBANLIST_TEST_POSTGRESQL_PASSWORD="your-password"
mvn test -Dtest=DatabaseManagerNetworkTest
```

Dialects without environment variables are skipped rather than failed. The test creates its
tables, writes a handful of rows prefixed with `Lbtest`, covers bans / IP bans / mutes /
warnings / reports / IP history / the audit chain, and cleans up the ban rows it created.
