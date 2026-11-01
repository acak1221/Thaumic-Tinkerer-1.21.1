# Multiplayer verification of the add-on

Everything else that checks the add-on runs in one JVM: the GameTests on a dedicated GameTest server with mock players (no network),
the client smoke with an integrated server (packets in memory, one payload registry for both sides). What only a real network shows was
not verified: that a physical client receives the server's config and uses it, that the spawn protection of a real dedicated server holds
for the add-on's tools (vanilla answers `isUnderSpawnProtection` with false on any other server), that the add-on's packets and its
block-entity updates cross a real connection, and that a second player sees what the first one changed. The **multiplayer check** does
exactly that: real dedicated servers and five real clients, each in its own JVM, talking over the loopback socket, on **both loaders**.

It is a development tool, like the client smoke (`CLIENT_VERIFICATION.md`), and never part of a release jar (see "Wiring").

## Running it

```bash
# Gradle is started as ./gradlew in the project root; GRADLE_CMD replaces it, as for tools/client-smoke.sh
tools/multiplayer-smoke.sh                 # Fabric (the default): about 3 minutes after the first compile
tools/multiplayer-smoke.sh --neoforge      # the same on NeoForge
tools/multiplayer-smoke.sh -l /path/gradle.log -t 1200 -p 25591   # Gradle log, client watchdog (s), server port
GRADLE_CMD='<wrapper> {LOG}' tools/multiplayer-smoke.sh            # a wrapper that serialises Gradle and writes the log itself
```

| | Fabric (`--fabric`, default) | NeoForge (`--neoforge`) |
|---|---|---|
| Gradle task | `runTinkererMultiplayer` (`fabric.gradle`) | `:neoforge:runMultiplayer` (`neoforge/build.gradle`) |
| results | `build/tinkerer-multiplayer/` | `neoforge/build/tinkerer-multiplayer/` |
| run directories (git-ignored, rewritten every run) | `run-tinkerer-mp-{server,lead,peer,mismatch,mismatch-kami,kami-on}/` | `neoforge/run-mp-{server,lead,peer,mismatch,mismatch-kami,kami-on}/` |
| default port | 25591 | 25592 |

The script runs the task inside `xvfb-run` with `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe LP_NUM_THREADS=2` (two clients render at
the same time on four CPUs). There is no GPU in the machine this was written on; the pictures are what Mesa's software renderer drew.

**Exit code.** 0 only when every JVM's harness completed and no check of any report is `FAIL`, `HARNESS_ERROR` or `NOT_RUN` (the Gradle task
reads the seven reports, writes `summary.txt` and fails otherwise). This is stricter than the client smoke, whose exit code says only that the
harness completed: every scenario here is expected to pass.

## How it runs

**One Gradle task, seven JVMs.** A server and two clients must run at the same time, and Gradle runs the tasks of one project one after
the other (the machine also serialises whole Gradle invocations with a lock). So the task (`gradle/multiplayer.gradle`, shared by both
loaders) starts the JVMs itself: each is described by an ordinary Loom run (`tinkererMpServer`, `tinkererMpLead`, ... on Fabric; `mpServer`,
`mpLead`, ... on NeoForge), whose task is configured but never executed; the task builds the same command line Loom's `AbstractRunTask` and
Gradle's `JavaExec` would (the JVM arguments with Loom's dev-launch properties and the `@` arg file of the class path, the main class, the
program arguments, the run directory, the run's environment variables), writes it to `<name>-command.txt` and starts it with a
`ProcessBuilder`. The task depends on everything the runs depend on (classes, Loom's launch configuration, the assets).

1. It refuses to start when the port is taken, then rewrites the run directories: the server's `eula.txt`, `server.properties`
   (`online-mode=false`, `spawn-protection=16`, flat world, peaceful, `max-tick-time=-1`, bound to 127.0.0.1), `ops.json`,
   `config/thaumictinkerer.json` (`modFlight.enabled=false`, `focusOfDislocationBlacklist=["minecraft:gold_block"]`), a fresh world;
   each client's `options.txt` and its own `config/thaumictinkerer.json` (flight on, empty blacklist; the mismatch clients see below).
2. **Operators.** Vanilla's `DedicatedServer.isUnderSpawnProtection` returns false while the operator list is **empty**, so a server with
   nobody opped has no spawn protection at all. `ops.json` therefore names `TTServerOp`, who never joins; none of the players is an operator.
3. The server starts; its test mod writes `server-ready` when the world is built. Then the second player `TTBravo` and, five seconds
   later, the lead `TTAlpha` start and join with `--quickPlayMultiplayer 127.0.0.1:<port>`. When both have ended, `TTMismatch` and then
   `TTMismatchKami` join, one after the other. Then the task types `stop` on the server's console and waits for it.
4. **The second server.** The same run directory again, a new world, and the server's config with `kami.forceenabled=false`; the run
   `server` with one more JVM argument, `-Dthaumictinkerer.mpServer.variant=kami-off` (its files are `server-kami-off-*`). One client,
   `TTKamiOn`, whose own file has Kami on, joins it; then the server is stopped as the first one.
5. Every JVM has a watchdog: the clients `-t` seconds (default 1200; the mismatch clients 600), the server an hour more, each writes a
   `TIMED_OUT` report and halts its JVM. The task itself waits for each process with a time limit and destroys what is still running
   (`destroy`, then `destroyForcibly`); a JVM that is killed leaves `ABORTED` in its report (shutdown hook).
6. Heap: 2 GB for the server and for each client (two clients at a time), next to the 3 GB Gradle daemon, on a machine of 15 GB.

**How the clients act.** The clients are the client smoke harness (`common/src/clientSmoke`) in its multiplayer mode
(`-Dthaumictinkerer.clientSmoke.mode=multiplayer-<role>`). They act as a player does, so every packet the add-on depends on crosses the
real connection: a cast or a block click is a press of the use or the attack key (`KeyMapping.click`, which the game's own
`handleKeybinds` turns into `startUseItem` / `startAttack` on what is in the cross-hair), sneaking holds the sneak key down, items go
into menus by container clicks (`MultiPlayerGameMode.handleInventoryMouseClick`), and screen buttons get a mouse click through the
screen (`Screen.mouseClicked` at the button). Before a click the client checks that its **own** cross-hair shows the block the server
named; nothing is aimed by the server.

**How they talk to the server's harness.** Only for coordination: a chat command `/ttmp <action> <name> <id> [arg]` (permission 0,
registered by the server test mod only), answered by a system message `[ttmp] <id> <STATUS> key=value ... | text`. `stage` puts the player
at a scenario (a teleport with the rotation that looks at its block) and remembers the state before; `check` reports what the server finds
afterwards and records it in the server's report; `ready` and `saw` are the second player's. The client then records one line with both
sides: the server's verdict and what its own world and inventory show, which must agree with the server's numbers.

## What it checks

The world: the flat preset (bedrock at y −64, grass at −61), world spawn (0, −60, 0), spawn protection 16 (a non-operator may not build
where `max(|x|, |z|) <= 16`). The lead is in survival and holds a silverwood staff with thaumium caps and the Focus of Dislocation (full of
every primal), the Placement Mirror (stone, 5 x 5), the Awakened Ichorium Pickaxe, a diamond sword, an iron pickaxe, 64 stone and a second
staff with the Focus of Uprising.

| | Scenario | Who | What must happen |
|---|---|---|---|
| a | `config_sync` | lead, peer | `PacketConfigSync` arrives; `ConfigHandler.flightEnabled(client level)` is false although the client's own file says true (`flightEnabled(null)` true); `dislocationBlacklist(client level)` contains `minecraft:gold_block`, the client's own list is empty; the client stays connected (equal registration options) |
| a | `flight` | lead | the Focus of Uprising cast into the open sky: the client neither pays vis nor lifts its own player (fastest motion < 0.3 block/tick; a cast gives 0.667), the server pays nothing |
| a | `dislocation_blacklisted` | lead | the Focus of Dislocation on a gold block **outside** the spawn radius: the client does not even predict the payment (it knows the server's blacklist), the server takes nothing |
| a | `disconnect` | lead, peer | after leaving, `ConfigHandler.fromServer()` is null and the side-aware accessors answer with the client's own file again |
| a | payload census | all | the server receives every client-to-server payload of the add-on (7, checked on the lead and the peer with `NetworkManager.canServerReceive`), every client receives every server-to-client payload (4, checked on the server with `canPlayerReceive` for each client that joins): each side is a separate JVM with its own payload registry. The three Kami payloads (`soul_hearts`, `armor_status`, `shadow_beam`) exist only with `kami.forceenabled` on, so on the second server only `config_sync` is expected (NeoForge counts a channel only when both sides have it; Fabric only asks the client) |
| b | `mismatch` | `TTMismatch` | its own file has `imbuedFire.enabled=false` (the server true): it joins, leaves by itself, and its disconnect screen shows the translated `thaumictinkerer.config.mismatch` naming `general/imbuedFire.enabled (server: true, yours: false)` (picture `mismatch-disconnected.png`); the server sees it leave within 120 s |
| b | `mismatch-kami` | `TTMismatchKami` | its own file has `kami.forceenabled=false` (the server true): without Kami it registers none of the Kami items and blocks, so the loader's registry sync, which runs in the configuration phase before the add-on's payload can arrive, must refuse it; it must not get into the game; the message is recorded as it is (picture `mismatch-kami-disconnected.png`) |
| b | `kami-on` | `TTKamiOn` on the second server | its own file has `kami.forceenabled=true`, the server false: the client has more registry entries than the server; it must get into the game, then leave by itself with the add-on's message naming `kami/kami.forceenabled (server: false, yours: true)` (picture `kami-on-disconnected.png`); the second server sees it leave within 120 s |
| c | `spawn_protection` | server | `isUnderSpawnProtection` is true for the lead at (6, −59, 6) and x = 16, false at (30, −59, 6) and x = 17 |
| c | `dislocation_inside` / `_outside` | lead | inside: the stone stays, no vis is paid on the server, and the client's copy of the wand shows the server's vis (the client cannot know the spawn protection: `ClientLevel.mayInteract` is always true, so it goes through the cast; the harness also records every vis its wand showed in the 40 ticks after the click, to see a predicted payment); outside: the stone is taken, Perditio, Ordo and Terra are paid, the focus carries the stone on both sides |
| c | `dislocation_place_inside` / `_outside` | lead | the stone the focus took is put down against the stone inside the radius: refused, the focus keeps it; then on the cobblestone outside, where it came from: placed, the focus is empty; the client shows the same block and the same focus |
| c | `mirror_inside` / `_boundary` / `_outside` | lead | a click on a wall inside: nothing placed, no stone used; a click on a wall at x = 17, whose 5 x 5 square reaches x = 15..19: the 15 blocks outside the radius are placed, the 10 inside are not, exactly 15 stone used; outside: all 25. The client sees the same blocks and the same stack |
| c | `pickaxe_inside` / `_outside` | lead | a sneaking left click on bedrock at y −64 in a pit: inside, the bedrock stays; outside, it becomes the Bedrock portal; no damage to the pickaxe; the server saw the player sneaking |
| d | `enchanter` | lead | right click on the Osmotic Enchanter opens its menu from the server; the diamond sword goes into the tool slot by two container clicks; a click on the first offered enchantment's button sends `PacketEnchanterAddEnchant`; the client's block entity shows the choice (the server's update) and the server's Enchanter holds the sword and the enchantment (picture `lead-enchanter.png`) |
| e | `tablet` / `tablet_sync` | lead, peer | the second player stands at the Animation Tablet, tells the server its own client shows it empty in left-click mode; the lead opens the tablet's menu, puts the iron pickaxe in by container clicks and clicks the right-click button (`PacketTabletButton`); the second player's **client-side block entity** must then show the pickaxe and right-click mode, and the server compares that with its own state (pictures `lead-tablet.png`, `peer-tablet.png`) |
| | `every_scenario_has_a_server_result` | server | at the end, every scenario above has a result in the server's report |

## Output

| File | What it is |
|---|---|
| `summary.txt` | every check of the seven reports, one line each, and the problems that failed the task |
| `server-report.json`, `server-kami-off-report.json` | the two servers: `harness` (status, loader), `summary`, `facts` (spawn, radius, operators, the server's config), `checks` (scenario, name, result, detail), `events` (joins, leaves, every `/ttmp` command with its answer) |
| `lead/report.json`, `peer/report.json`, `mismatch/report.json`, `mismatch-kami/report.json`, `kami-on/report.json` | the clients: the format of the client smoke's `report.json` (`harness` with `mode`, `facts` with the server address and brand, `checks`, `screenshots`; the lead and the peer also list the system messages they received) |
| `<role>/screenshots/*.png` | the pictures named above, with `INDEX.md` |
| `<name>-console.log`, `<name>-latest.log`, `<name>-command.txt` | the output, the game log and the command line of each JVM (`server`, `server-kami-off` and the five clients); `crash-reports/<name>/` if there are any |
| `gradle.log` | a copy of the Gradle log (by the script) |

## Results

RESULTS

## Wiring

| Tree | Package | What is in it |
|---|---|---|
| `common/src/multiplayerServer/java` | `thaumic.tinkerer.mpserver` | the server test mod: `MultiplayerServer` (armed by `-Dthaumictinkerer.mpServer=true`; Architectury's lifecycle, player and command events; watchdog; `server-ready`), `Scenes` (the world, the items, `stage`, `check`), `TtmpCommand`, `ServerReport` |
| `fabric/src/multiplayerServer` | `thaumic.tinkerer.fabric.mpserver` | `TinkererMultiplayerServer` (the `server` entrypoint), `fabric.mod.json` (mod id `thaumictinkerer_mpserver`, environment `server`) |
| `neoforge/src/multiplayerServer` | `thaumic.tinkerer.neoforge.mpserver` | `TinkererMultiplayerServerNeoForge` (`@Mod(value = "thaumictinkerer_mpserver", dist = Dist.DEDICATED_SERVER)`), `neoforge.mods.toml` |
| `common/src/clientSmoke/java` | `thaumic.tinkerer.clientsmoke` | the client side: `MultiplayerClient` (the roles, the shared scripts: join, config sync, disconnect), `MultiplayerLeadPhase`, `MultiplayerPeerPhase`, `MultiplayerMismatchPhase`, `ServerLink` (`/ttmp`), `Input` (keys, clicks); `ClientSmoke` picks them by `-Dthaumictinkerer.clientSmoke.mode` |
| `gradle/multiplayer.gradle` | (MIT) | `registerTinkererMultiplayer`: the task that prepares, starts, waits for, stops and summarises the seven JVMs |
| `tools/multiplayer-smoke.sh` | (MIT) | the runner under Xvfb |

* Fabric: source set `thaumicTinkererMultiplayerServer` (`fabric.gradle`), runs `tinkererMpServer` (the add-on and the test mod as two classpath groups),
  `tinkererMpLead`, `tinkererMpPeer`, `tinkererMpMismatch`, `tinkererMpMismatchKami`, `tinkererMpKamiOn` (the add-on and the client smoke
  harness), task `runTinkererMultiplayer`. NeoForge: source set `multiplayerServer` (`neoforge/build.gradle`), runs `mpServer` (mods
  `thaumictinkerer` and `thaumictinkerer_mpserver`), `mpLead`, `mpPeer`, `mpMismatch`, `mpMismatchKami`, `mpKamiOn` (mods `thaumictinkerer` and
  `thaumictinkerer_clientsmoke`), task `runMultiplayer`. The second server is the run `tinkererMpServer` / `mpServer` with one more JVM argument.
* `check` compiles the new source set on both loaders; no jar task includes it or the client smoke (checked after the last runs: the jars in
  `build/libs` and `neoforge/build/libs` hold no `mpserver` or `clientsmoke` entry).
* The test mod and the client harness import Minecraft, Architectury, the Thaumcraft API and the add-on only (the loader entries their loader);
  nothing of `thaumcraft.common` or `thaumcraft.client`.

## What it cannot show

* **One machine, loopback.** Latency, packet loss, bandwidth, a slow or distant server and a client that falls behind are not exercised; both ends
  are on 127.0.0.1 and the server is idle apart from the scenarios.
* **Offline mode.** `online-mode=false`: no authentication, no chat signing (`enforce-secure-profile=false`). Player names and UUIDs are the
  offline ones.
* **Scripted players.** The clients press keys and click through the game's own input path, but nobody moves a mouse: the cross-hair is set by the
  server's teleport, screens get synthetic clicks at the centre of a button, and only the few actions above are made. Every other packet and screen of
  the add-on (the Remote Placer's buttons, the Enchanter's start button, the Kami armour toggles, the Celestial Gateway) is only proven to be
  registered on both sides (payload census), not used.
* **Spawn protection only.** The protection that is checked is vanilla's spawn protection of a dedicated server; no protection mod (claims) is
  installed, so the loader-event half of `BlockProtection` is still covered only by the GameTests' test backend. The Remote Placer, the
  Transvector Dislocator and the Animation Tablet's fake player, which ask the same `BlockProtection`, are not used in this check.
* **One flat world, peaceful, noon.** No other dimension (the Bedrock portal is made but not entered), no mobs, no weather.
* **No GPU, no sound.** As for the client smoke: Mesa llvmpipe under Xvfb.
* **Two loaders, not mixed.** A Fabric client on a Fabric server and a NeoForge client on a NeoForge server; a client of one loader cannot join
  the other's server anyway.
