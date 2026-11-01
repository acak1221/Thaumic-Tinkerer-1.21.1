# Multiplayer verification of the add-on

Everything else that checks the add-on runs in one JVM: the GameTests on a dedicated GameTest server with mock players (no network),
the client smoke with an integrated server (packets in memory, one payload registry for both sides). What only a real network shows was
not verified: that a physical client receives the server's config (since 2026-11-01 in the configuration phase of the connection) and uses it, that the spawn protection of a real dedicated server holds
for the add-on's tools (vanilla answers `isUnderSpawnProtection` with false on any other server), that the add-on's packets and its
block-entity updates cross a real connection, and that a second player sees what the first one changed. The **multiplayer check** does
exactly that: real dedicated servers and five real clients, each in its own JVM, talking over the loopback socket, on **both loaders**.
Its second part (2026-11-01, sections f to h below) adds the devices whose fake player asks the protection (the Remote Placer, the
Transvector Dislocator, the Animation Tablet) under spawn protection, the packets of the screens and keys that the first part only
registered (the Remote Placer's buttons, the Enchanter's start, the Kami armour key, the Celestial Gateway's button and teleport, the
server's Soul Hearts and Shadowbeam on the client), and a real claims mod, **Open Parties and Claims**, on the server.

It is a development tool, like the client smoke (`CLIENT_VERIFICATION.md`), and never part of a release jar (see "Wiring").

## Running it

```bash
# Gradle is started as ./gradlew in the project root; GRADLE_CMD replaces it, as for tools/client-smoke.sh
tools/multiplayer-smoke.sh                 # Fabric (the default): about 6 minutes after the first compile
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
   The server's class path also holds **Open Parties and Claims** (OPAC) 0.33.0 for Minecraft 1.21.1 and, on Fabric, the Forge Config API
   Port 21.1.6 it requires (`gradle.properties`: `opac_fabric_version`, `opac_neoforge_version`, `forge_config_api_port_version`;
   Modrinth's Maven), with OPAC's default configuration; the clients do not have it (OPAC needs no client: its network channel is
   optional). Neither is a dependency of the add-on or of any other run (see "Wiring").
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
screen (`Screen.mouseClicked` at the button). Before a click the client checks that its **own** cross-hair shows the block (or, for the
Soul Hearts, the entity) the server named; nothing is aimed by the server. A key held over several ticks (sneak, use for the Shadowbeam,
attack for an axe that breaks a log) is held down with `KeyMapping.setDown` every tick. The add-on's own key mapping ("Toggle Awakened
Armor", unbound by default as in 1.7.10) is bound to U the way the controls screen does it (`setKey`, `KeyMapping.resetMapping`) and
pressed with `KeyMapping.click`. One thing a key mapping cannot do: the Celestial Gateway's destinations screen teleports only on a
click with `Screen.hasShiftDown()`, which asks GLFW for the physical key. For that one click the lead sends its own window a real
X11 `KeyPress` / `KeyRelease` of the left shift key (`XSendEvent` through LWJGL, the key code from `glfwGetKeyScancode`; `Input.realShift`):
the X server delivers it, GLFW reads it like a key of a keyboard, and the second client on the same display gets nothing.

**How they talk to the server's harness.** Only for coordination: a chat command `/ttmp <action> <name> <id> [arg]` (permission 0,
registered by the server test mod only), answered by a system message `[ttmp] <id> <STATUS> key=value ... | text`. `stage` puts the player
at a scenario (a teleport with the rotation that looks at its block) and remembers the state before; for some scenarios it first gives
the player the scenario's item in a hotbar slot or puts the Gem Boots on (setup, not what is tested), and the client waits until its
own inventory shows it. `check` reports what the server finds afterwards and records it in the server's report; while an effect that
takes time (a tablet breaking a stone, the Enchanter at work, a teleport) is not there yet, it answers `WAIT` for a few seconds and the
client asks again. `ready` and `saw` are the second player's; the lead's last `stage` (`wait_peer`) waits until the second player, who
works at the lead's claim, has left. The client then records one line with both
sides: the server's verdict and what its own world and inventory show, which must agree with the server's numbers.

## What it checks

The world: the flat preset (bedrock at y −64, grass at −61), world spawn (0, −60, 0), spawn protection 16 (a non-operator may not build
where `max(|x|, |z|) <= 16`). The lead is in survival and holds a silverwood staff with thaumium caps and the Focus of Dislocation (full of
every primal), the Placement Mirror (stone, 5 x 5), the Awakened Ichorium Pickaxe, a diamond sword, an iron pickaxe, 64 stone and a second
staff with the Focus of Uprising; a scene that needs another item (planks, the Awakened Ichorium Sword or Axe, the Shadowbeam staff, the
Gem Boots, 64 more stone) gets it at its stage. The second player holds the same Dislocation staff, mirror and pickaxe, the Awakened
Ichorium Axe in tree mode and 64 stone. Order: the lead does `flight`, then `tablet` (the second player watches), then `a_claim`, then the
rest; the second player works at the claim (`b_*`, its first stage waits for the claim) while the lead goes on, and the lead's last stage
waits until the second player has left.

| | Scenario | Who | What must happen |
|---|---|---|---|
| a | `config_sync` | lead, peer | `PacketConfigSync` arrived in the configuration phase, before the client had a world (the add-on's configuration task, since 2026-11-01; before, the same payload came on join); `ConfigHandler.flightEnabled(client level)` is false although the client's own file says true (`flightEnabled(null)` true); `dislocationBlacklist(client level)` contains `minecraft:gold_block`, the client's own list is empty; the client stays connected (equal registration options). On the server, `real_connection_and_config_sync` of each join: the client answered the handshake (`PacketConfigSyncReply`, recorded through `ConfigSync.ANSWERED`) with registration options equal to the server's before it joined |
| a | `flight` | lead | the Focus of Uprising cast into the open sky: the client neither pays vis nor lifts its own player (fastest motion < 0.3 block/tick; a cast gives 0.667), the server pays nothing |
| a | `dislocation_blacklisted` | lead | the Focus of Dislocation on a gold block **outside** the spawn radius: the client does not even predict the payment (it knows the server's blacklist), the server takes nothing |
| a | `disconnect` | lead, peer | after leaving, `ConfigHandler.fromServer()` is null and the side-aware accessors answer with the client's own file again |
| a | payload census | all | the server receives every client-to-server payload of the add-on (7, checked on the lead and the peer with `NetworkManager.canServerReceive`), every client receives every server-to-client payload of the play phase (3, checked on the server with `canPlayerReceive` for each client that joins): each side is a separate JVM with its own payload registry. The three Kami payloads (`soul_hearts`, `armor_status`, `shadow_beam`) exist only with `kami.forceenabled` on, so on the second server none is expected (NeoForge counts a channel only when both sides have it; Fabric only asks the client). The config payload and its answer are configuration payloads since 2026-11-01; that a client had them is the handshake's own check (scenario `config_sync`) |
| b | `mismatch` | `TTMismatch` | its own file has `imbuedFire.enabled=false` (the server true): in the configuration phase it gets the server's config and answers with its own options; the server must refuse it before it gets into the game, and its disconnect screen shows the translated `thaumictinkerer.config.mismatch` naming `general/imbuedFire.enabled (server: true, yours: false)` (picture `mismatch-disconnected.png`). The server's report has its answer (`join_mismatch`) and the refusal (`mismatch`: the answer named exactly that option, the client's value false, and the player never joined) |
| b | `mismatch-kami` | `TTMismatchKami` | its own file has `kami.forceenabled=false` (the server true): without Kami it registers none of the Kami items and blocks, so the loader's registry sync, which runs in the configuration phase before the add-on's task (on both loaders), must refuse it; it must not get into the game; the message is recorded as it is (picture `mismatch-kami-disconnected.png`) |
| b | `kami-on` | `TTKamiOn` on the second server | its own file has `kami.forceenabled=true`, the server false: the client has more registry entries than the server, which the loader's registry sync lets through; the add-on's task must then refuse it before it gets into the game with the add-on's message naming `kami/kami.forceenabled (server: false, yours: true)` (picture `kami-on-disconnected.png`); the second server's report has its answer (`join_kami_on`) and the refusal (`kami_on`) |
| c | `spawn_protection` | server | `isUnderSpawnProtection` is true for the lead at (6, −59, 6) and x = 16, false at (30, −59, 6) and x = 17 |
| c | `dislocation_inside` / `_outside` | lead | inside: the stone stays, no vis is paid on the server, and the client's copy of the wand shows the server's vis (the client cannot know the spawn protection: `ClientLevel.mayInteract` is always true, so it goes through the cast; the harness also records every vis its wand showed in the 40 ticks after the click, to see a predicted payment); outside: the stone is taken, Perditio, Ordo and Terra are paid, the focus carries the stone on both sides |
| c | `dislocation_place_inside` / `_outside` | lead | the stone the focus took is put down against the stone inside the radius: refused, the focus keeps it; then on the cobblestone outside, where it came from: placed, the focus is empty; the client shows the same block and the same focus |
| c | `mirror_inside` / `_boundary` / `_outside` | lead | a click on a wall inside: nothing placed, no stone used; a click on a wall at x = 17, whose 5 x 5 square reaches x = 15..19: the 15 blocks outside the radius are placed, the 10 inside are not, exactly 15 stone used; outside: all 25. The client sees the same blocks and the same stack |
| c | `pickaxe_inside` / `_outside` | lead | a sneaking left click on bedrock at y −64 in a pit: inside, the bedrock stays; outside, it becomes the Bedrock portal; no damage to the pickaxe; the server saw the player sneaking |
| d | `enchanter` | lead | right click on the Osmotic Enchanter opens its menu from the server; the diamond sword goes into the tool slot by two container clicks; a click on the first offered enchantment's button sends `PacketEnchanterAddEnchant`; the client's block entity shows the choice (the server's update) and the server's Enchanter holds the sword and the enchantment (picture `lead-enchanter.png`) |
| e | `tablet` / `tablet_sync` | lead, peer | the second player stands at the Animation Tablet, tells the server its own client shows it empty in left-click mode; the lead opens the tablet's menu, puts the iron pickaxe in by container clicks and clicks the right-click button (`PacketTabletButton`); the second player's **client-side block entity** must then show the pickaxe and right-click mode, and the server compares that with its own state (pictures `lead-tablet.png`, `peer-tablet.png`) |
| f | `placer_menu` | lead | the outside Remote Placer (x = 40, facing west): a sneaking wand click on it opens its screen from the server (`BlockRPlacer.onWandRightClick`); four planks go into its slot by two container clicks; a click on the third distance button sends `PacketPlacerButton`; the server's placer has distance 3 and the planks, the lead none; the client's block entity shows the same after the screen closed (picture `lead-placer.png`) |
| f | `placer_outside` / `placer_inside` | lead | a right click on a stone button outside the radius (x = 42 / 18) pulses the placer at x = 40 / 16 through the stone it sits on: outside, its fake player `[Minecraft]` puts planks three blocks west (as 1.7.10 does) and the slot keeps 3; inside (loaded by the harness with 4 planks at distance 1: a non-operator cannot open its screen inside the radius), nothing is placed at x = 15 and the slot keeps 4. The client shows the same block and slot |
| f | `dislocator_inside` / `_outside` | lead | the same with a Transvector Dislocator facing west, lapis in front of it and bound to an emerald block outside the radius: inside (front x = 15), nothing moves; outside, the two blocks swap (as in 1.7.10). The client shows the same blocks |
| f | `device_tablet_inside` / `_outside` | lead | an Animation Tablet in redstone and left-click mode holding an iron pickaxe, in front of a stone: the button powers it; inside, its fake player `[ThaumcraftTablet]` breaks nothing and the pickaxe stays new; outside, the stone is broken and the pickaxe worn by 1 (as in 1.7.10). The client shows the same block and tool (pictures `lead-devices-inside.png`, `lead-devices-outside.png`) |
| g | `enchanter_start` | lead | after `enchanter`, on the same screen: the harness built six pillars (two Obsidian Totems and a Nitor each) and put a wand full of vis into the wand slot; a click on the start button sends `PacketEnchanterStartWorking`; the client's block entity shows the work running and then the sword with Sharpness I; the server's sword has Sharpness I, the Enchanter is idle again and its wand paid ordo (picture `lead-enchanter-done.png`) |
| g | `armor_off` / `armor_on` | lead | the stage puts the Gem Boots on (Haste II while the awakened armour is on); the add-on's "Toggle Awakened Armor" key (unbound by default, as in 1.7.10; bound to U as in the controls screen) is pressed: `PacketToggleArmor`; the server's switch and the boots' Haste follow it, and so do the client's own copy of the switch and the effect the server sent it |
| g | `soul_hearts` | lead | a left click with the Awakened Ichorium Sword in Soul mode on an iron golem without AI (an animal would be discarded at once: `spawn-animals=false`): the server gives half a Soul Heart and sends `PacketSoulHearts`; the client's `SoulHeartClientHandler` holds the same number and its HUD draws it (picture `lead-soul-hearts.png`) |
| g | `shadow_beam` | lead | the use key held for 30 ticks with a staff carrying the Shadowbeam focus, looking into the sky: the server takes ordo, perditio and aer every tick and sends the beam's path (`PacketShadowBeam`) to the players within 64 blocks; the client draws its dark dust along it (counted in its own particle engine) and its wand shows the server's vis (picture `lead-shadow-beam.png`) |
| g | `gate_teleport` / `gate_lock` / `gate_locked` | lead | two Celestial Gateways, A holding a Sky Pearl attuned to B ten blocks south. Sneaking on A opens the destinations screen (the gate's own client tick); a real shift key (see "How the clients act") and a click on B's pearl send `PacketWarpGateTeleport`: the lead stands on B on both sides. At B, its screen's lock button sends `PacketWarpGateButton`: B is locked on both sides. The same shift click from A again: refused, the lead stays on A and gets the gate's message "... does not accept incoming teleports" (pictures `lead-warp-destinations.png`, `lead-warp-gate.png`) |
| h | `a_claim` | lead | Open Parties and Claims on the server: the lead types OPAC's own command, `/oclaims claim 96 -32 111 -17` and `/oclaims claim 112 -32 127 -17` (one chunk each; see "What it found"); OPAC owns the chunks (6, -2) and (7, -2), x 96..127, z -32..-17, for the lead (read through OPAC's API by reflection in the harness) |
| h | `a_dislocation_own`, `a_mirror_own`, `a_pickaxe_own`, `a_axe_own` | lead | in its own claim the owner's tools work: the Focus of Dislocation takes a stone and pays, the Placement Mirror places all 25, the Awakened Ichorium Pickaxe makes the Bedrock portal, the Awakened Ichorium Axe in tree mode fells a tree of four logs |
| h | `a_placer_own`, `a_dislocator_own` | lead | the owner presses the button of a Remote Placer and of a Transvector Dislocator standing in its own claim: **nothing happens** (the devices act as their fake player `[Minecraft]`, a stranger to OPAC; see "What it found") |
| h | `b_*_in` | peer | the second player, in no party with the lead, from the wilderness on blocks in the claim: the Focus of Dislocation, carrying the stone it took outside (`b_dislocation_out` comes first), puts nothing down in the claim and keeps its load; the Placement Mirror places nothing; the pickaxe leaves the bedrock (its pit is in the claim); the axe cannot break the log |
| h | `b_mirror_edge`, `b_axe_edge`, `b_placer_into_claim`, `b_dislocator_into_claim` | peer | over the claim's edge, where only the add-on's question can stop it: the mirror on a wall outside whose square reaches two columns in places the 15 positions outside and none of the 10 inside; tree mode on a log outside fells it but no log of the branch inside; a placer outside aiming two blocks into the claim places nothing; a Dislocator outside whose front (air) is in the claim and whose end (emerald) is outside moves nothing into it (pictures `peer-claim-mirror-edge.png`, `peer-claim-tree-edge.png`) |
| h | `b_*_out` | peer | the same tools and devices outside the claim work: a stone taken and paid for, and after the refusal in the claim put back on its cobblestone (`b_dislocation_place_out`; two chunks away from the claim: OPAC also refuses a stranger's item use in the chunks next to a claim), 25 stone placed, the portal made, a tree felled, planks placed, a swap. The client shows the same blocks, vis and stacks in every scene |
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

The second part (sections f to h) is reported in its own subsection below; this first one is the first part's (sections a to e), as it stood
before the second part was added.

The final runs (2026-11-01), each started as `GRADLE_CMD='<wrapper> {LOG}' tools/multiplayer-smoke.sh [--neoforge]` (the wrapper serialises
Gradle on the machine and is not part of the repository). Both ended `BUILD SUCCESSFUL`, exit 0, and `summary.txt` "no problems":

| Report | Fabric | NeoForge |
|---|---|---|
| `server-report.json` | COMPLETED, 25 PASS | COMPLETED, 25 PASS |
| `server-kami-off-report.json` | COMPLETED, 6 PASS | COMPLETED, 6 PASS |
| `lead/report.json` | COMPLETED, 20 PASS | COMPLETED, 20 PASS |
| `peer/report.json` | COMPLETED, 8 PASS, 1 INFO | COMPLETED, 8 PASS, 1 INFO |
| `mismatch` / `mismatch-kami` / `kami-on` | COMPLETED, 3 / 2 / 3 PASS | COMPLETED, 3 / 2 / 3 PASS |
| all seven reports | **67 PASS, 0 FAIL, 0 HARNESS_ERROR, 0 NOT_RUN** | **67 PASS, 0 FAIL, 0 HARNESS_ERROR, 0 NOT_RUN** |
| the Gradle task | 5 min 31 s (a new Gradle daemon) | 3 min 46 s |
| pictures | 6, none blank | 6, none blank |

What the lines say, the same on both loaders unless noted:

* **a. Config sync.** Both clients got `PacketConfigSync`; with their own file at flight on and an empty blacklist, `flightEnabled(client level)` was
  false and `dislocationBlacklist(client level)` was `[minecraft:gold_block]`, and both were back to the file's values after leaving
  (`fromServer()=null`). The Focus of Uprising cast into the sky moved the lead at most 0.078 block/tick (standing still; a cast gives 0.667) and
  paid nothing on either side; the Focus of Dislocation on the server-blacklisted gold block took nothing and paid nothing, and the client's wand
  showed 25000 of every primal the whole time. Payload census: 7 of 7 client-to-server types on both clients, 4 of 4 server-to-client types for
  every client that joined the first server (1 of 1, `config_sync`, on the second).
* **b. Mismatch.** `TTMismatch` (imbuedFire off) got into the game and left 1.2 s (Fabric) and 0.8 s (NeoForge) later; its screen "Connection Lost" showed
  `thaumictinkerer.config.mismatch`, rendered "This server's Thaumic Tinkerer config differs from yours ...: general/imbuedFire.enabled
  (server: true, yours: false). Set them in config/thaumictinkerer.json as the server has them, restart the game and join again."
  (`mismatch-disconnected.png`). `TTKamiOn` on the second server: the same message with `kami/kami.forceenabled (server: false, yours: true)`.
  `TTMismatchKami` (Kami off, the server on) never got into the game: the loader's registry sync refused it first, Fabric with "Received 50 registry
  entries that are unknown to this client ... thaumictinkerer", NeoForge with "The server send registries with unknown keys: ..." and the 50 keys
  (`neoforge.network.registries.sync.server-with-unknown-keys`).
* **c. Spawn protection.** `isUnderSpawnProtection` for the lead: true at (6, -59, 6) and x = 16, false at (30, -59, 6) and x = 17. Inside: the
  Dislocation took nothing (vis 25000 everywhere on both sides) and could not put its load down (6, -59, 7 stayed air, the focus kept the stone);
  the mirror clicked on a wall inside placed nothing; the sneaking pickaxe click left the bedrock as it was (the server saw the player sneaking).
  Outside: the stone was taken (Perditio and Ordo 25000 -> 24550, Terra -> 24910, the same on the client) and put back on its cobblestone; the mirror
  placed all 25; the bedrock became `thaumictinkerer:bedrock_portal`. On the edge: 15 stone outside the radius, 0 of the 10 inside, 15 taken from the
  stack (64 -> 49). Pickaxe damage 0 throughout; every client view agreed with the server's.
* **d. Menu round trip.** The Enchanter's screen opened from the server's menu packet, the sword went in by two container clicks, the first
  offered button was `minecraft:sharpness`; the server's Enchanter held the diamond sword with `[minecraft:sharpness]` at level 1 and the client's
  block entity showed it before the screen closed (`lead-enchanter.png`).
* **e. Second player.** The tablet changed in the second player's own client from `item=minecraft:air leftClick=true` to
  `item=minecraft:iron_pickaxe leftClick=false` 51.0 s (Fabric) and 57.3 s (NeoForge) after it said it was ready (the lead's thirteen
  scenarios came first), and the server's state was the same (`peer-tablet.png`: the second player sees the lead and the pickaxe on the tablet).

### What the first part found

* **Defects of the add-on's code: none.** Every scenario passed on both loaders once the harness itself worked. In particular the Focus of
  Dislocation, the Placement Mirror and the Awakened Ichorium Pickaxe respect a real dedicated server's spawn protection, including a mirror square
  that crosses the edge, and the client's copy of the wand, the blocks and the stacks always agreed with the server.
* **A wrong statement in the documents, corrected.** `DEVIATIONS.md` section 1 said that a client whose `kami.forceenabled` differs leaves with the
  add-on's message. That holds only when the client has Kami and the server does not; a client **without** Kami on a server with it lacks 50 registry
  entries and is refused by the loader's registry sync, in the configuration phase, before the add-on's payload can arrive, with the loader's message.
  The client is refused either way; only the message differs. The section now says so.
* **For the owner of the Kami group (not changed here; the Kami data belongs to that group):** the second server, with `kami.forceenabled=false`,
  logs two data-load errors at start on both loaders: "Parsing error loading recipe thaumictinkerer:ichor_nuggets" (documented in
  `deviations/KamiGear.md` section 11 as the one expected error) and "Couldn't parse element ... thaumictinkerer:blocks/warp_gate - Unknown registry key
  ... thaumictinkerer:warp_gate" (the Celestial Gateway's loot table; not documented). Both are skipped entries; nothing else of the run depends on them.
  **Fixed afterwards (2026-11-01, on `main`):** both files now carry a load condition for each loader on their item (`fabric:load_conditions` with
  `fabric:registry_contains`, `neoforge:conditions` with `neoforge:item_exists`; `deviations/KamiGear.md` section 11). In the next run of both loaders the
  Kami-off server's log (`server-kami-off-latest.log`) has neither error; with Kami on the recipe and the gate's drop are still checked by the GameTests.
* **Problems of the harness, found by the first runs and fixed:** a new Animation Tablet is in left-click mode, so the scenario switches it to right
  click; the mismatch client may leave before a client tick sees its level, so its join is taken from `CLIENT_PLAYER_JOIN`; Architectury 13.0.11
  posts no `ClientSystemMessageEvent` on NeoForge, so the `/ttmp` answers are heard through `ClientChatEvent` too (`NEOFORGE.md`); NeoForge's
  `canPlayerReceive` needs a channel on both sides, so the census expects the Kami payloads only on a server with Kami. The first Fabric run also needed
  a lead that waited forever for a peer that had already given up (killed by hand); the stage now answers `WAIT` and the peer's precondition is right.
* **Seen on the way:** a dedicated server needs an operator in `ops.json` for spawn protection to exist at all (vanilla returns false while the list is
  empty); the run lists one who never joins. Every server also logs vanilla's "No key layers in MapLike[{}]" for the empty `generator-settings` of
  the flat world (the default layers are used).

**The rest after the change** (the same tree, 2026-11-01): `check runTinkererGameTest :neoforge:runGameTest assemble :neoforge:assemble` ended
`BUILD SUCCESSFUL`: JUnit 423 tests, 0 failures; Fabric "All 354 required tests passed"; NeoForge "All 362 required tests passed". The
singleplayer client smoke, whose `SmokeRun` and `ClientSmoke` gained the mode switch: Fabric 727 PASS, NeoForge 730 PASS, 0 FAIL, 1 NOT_RUN, 80
pictures each (the one PASS fewer than the last recorded runs is a fire block's `up=true` state, `CLIENT_VERIFICATION.md`). The jars in
`build/libs` and `neoforge/build/libs` hold no `mpserver`, `clientsmoke`, `multiplayer` or GameTest entry.

Earlier runs while the harness was written (Fabric 1 to 3, NeoForge 1) failed or were stopped for the harness problems above only; Fabric run 3, the
first complete one, had 63 PASS and 0 FAIL (before the two Dislocation placement scenarios existed).

### Second part: devices, packets and Open Parties and Claims (2026-11-01)

The final runs (2026-11-01, the code of commit `fd041b5`), each started as
`GRADLE_CMD='<wrapper> {LOG}' tools/multiplayer-smoke.sh [--neoforge]` with its own port (`-p 25631` / `-p 25632`): both ended `BUILD SUCCESSFUL`,
exit 0, and `summary.txt` "no problems":

| Report | Fabric | NeoForge |
|---|---|---|
| `server-report.json` | COMPLETED, 62 PASS | COMPLETED, 62 PASS |
| `server-kami-off-report.json` | COMPLETED, 6 PASS | COMPLETED, 6 PASS |
| `lead/report.json` | COMPLETED, 42 PASS | COMPLETED, 42 PASS |
| `peer/report.json` | COMPLETED, 23 PASS, 1 INFO | COMPLETED, 23 PASS, 1 INFO |
| `mismatch` / `mismatch-kami` / `kami-on` | COMPLETED, 3 / 2 / 3 PASS | COMPLETED, 3 / 2 / 3 PASS |
| all seven reports | **141 PASS, 0 FAIL, 0 HARNESS_ERROR, 0 NOT_RUN** | **141 PASS, 0 FAIL, 0 HARNESS_ERROR, 0 NOT_RUN** |
| the Gradle task | 5 min 49 s | 5 min 56 s |
| pictures | 16, none blank | 16, none blank |

What the lines say, the same on both loaders unless noted (numbers from the Fabric run):

* **f. Devices under spawn protection.** Inside the radius none of the three changed anything: the placer's planks stayed in its slot (4) and x = 15
  stayed air, the Dislocator's lapis and emerald stayed where they were, the tablet's stone stayed and its pickaxe kept damage 0. Outside, each did
  what 1.7.10 does: planks three blocks west of the placer (the distance the lead set on its screen, `PacketPlacerButton`; slot 4 -> 3), lapis and
  emerald swapped, the stone broken by the tablet's fake player and the pickaxe worn to damage 1. The lead's client showed the same blocks, slots
  and tool every time (`lead-devices-inside.png`, `lead-devices-outside.png`: the three rows seen from the east, inside unchanged, outside with
  the planks, the emerald in front of the Dislocator and the tablet's stone gone).
* **g. The packets.** `PacketEnchanterStartWorking`: the client's Enchanter showed the work running, then the sword with Sharpness I; the server's
  wand in the Enchanter paid ordo 10000 -> 8920. `PacketToggleArmor`: the switch went off and on again on both sides, and Haste II of the Gem Boots
  with it. `PacketSoulHearts`: 0 -> 1 on the server and on the client (`lead-soul-hearts.png`: the iron golem in the cross-hair, the half Soul Heart
  drawn above the right end of the health bar). `PacketShadowBeam`: the server took aer 25000 -> 24626, ordo and perditio 25000 -> 24354 in 30
  ticks; the client counted up to 4612 dust particles of the beam's colour (NeoForge 4498; 0 before) and its wand showed the server's vis (`lead-shadow-beam.png`: a
  dark cloud along the beam up into the sky). `PacketWarpGateTeleport`: the lead went from gate A (60, -59, 50) to gate B (60, -59, 60) on both
  sides, with GLFW reporting the real shift key down (`lead-warp-destinations.png`: the map centred on gate A, the pearl of gate B below it).
  `PacketWarpGateButton`: gate B locked on both sides (`lead-warp-gate.png`); the next shift click from A was refused, the lead stayed and got "The
  Celestial Gateway in the destination does not accept incoming teleports."
* **h. Open Parties and Claims.** The lead's two commands claimed (6, -2) and (7, -2) ("Successfully claimed the chunk at ..."). In its own claim
  the lead's Focus of Dislocation took a stone, its mirror placed 25 of 25, its pickaxe made the portal, tree mode felled 4 of 4 logs (attack key
  held 6 ticks); its placer and Dislocator did nothing (the documented limitation below). The second player: on blocks in the claim OPAC refused
  the clicks themselves ("(Main Hand) You are not allowed to interact with this block (minecraft:stone)!", also for the polished andesite, the
  bedrock and the log), the loaded focus kept its stone and the mirror placed nothing; over the edge the mirror placed 15 of 15 outside and 0 of 10
  inside (on Fabric OPAC answers each of the ten place questions, now asked through `PlayerBlockBreakEvents.BEFORE`, with its chat message for the
  air there; on NeoForge its place handler sends none), tree mode broke the
  clicked log outside and left the branch in the claim (2 of 2 logs; one OPAC message for the question about it), the placer placed nothing at
  (110, -60, -17) and the Dislocator moved nothing into the claim; outside the claim everything worked (a stone taken and, after the refusal, put
  back; 25 placed; the portal; 3 of 3 logs felled; planks placed; the swap). Every client view agreed with the server's
  (`peer-claim-mirror-edge.png`: the placed stone in front of the second player, the two columns in the claim shown only as the mirror's
  translucent ghost blocks; `peer-claim-tree-edge.png`: the dropped log at the foot of the trunk, the trunk and the branch still standing).

#### What the second part found

* **A defect of the add-on's code, fixed: on Fabric a place question did not reach the claims mod.** Fabric API has no place event, and Open
  Parties and Claims 0.33.0 on Fabric listens to `AttackBlockCallback`, `PlayerBlockBreakEvents.BEFORE`, `UseBlockCallback`, `UseItemCallback`,
  `UseEntityCallback` and `AttackEntityCallback` (`xaero.pac.common.event.CommonEventsFabric.registerFabricAPIEvents`) and to no place event
  (`CommonEvents.onEntityPlaceBlock`, posted only from NeoForge's `BlockEvent.EntityPlaceEvent`, says "only supported by Forge atm"). The Fabric
  backend asked placements through Architectury's `BlockEvent.PLACE`, which OPAC does not hear. The first run in which the claim scenes ran, before the fix, showed it on Fabric:
  the second player's Placement Mirror on a wall outside the claim placed **10 of the 10** positions inside it (`b_mirror_edge`), and a
  Transvector Dislocator outside moved its emerald block into the claim (`b_dislocator_into_claim`). The Remote Placer aiming into the claim placed
  nothing even then, because its fake player's real use passes `UseBlockCallback`, which OPAC refuses. NeoForge was not affected (OPAC hears its
  `EntityPlaceEvent`, which the NeoForge backend posts). Fix (`FabricBlockProtection.mayPlace`, loader-general, no OPAC code): the place question
  also asks Fabric API's own `PlayerBlockBreakEvents.BEFORE` about what is at the position (what the placement replaces), so a mod that does not
  let the player change that position refuses the placement there too (`HOOKS.md` section 16). Test: the Fabric-native GameTest
  `blockProtectionAsksTheLoadersOwnBreakAndPlaceEvents` now also checks that a `BEFORE` listener refusing a free cell makes `mayPlace` false there,
  asked once while probing about the air there, nothing placed (Fabric hook GameTests: "All 19 required tests passed"). After the fix both scenes
  are refused on Fabric as on NeoForge.
* **A limitation, for the owner to decide: the devices are strangers to a claims mod in their owner's claim too.** The Remote Placer and the
  Transvector Dislocator act as their fake player `[Minecraft]` (the shared machine player of 1.7.10's `FakePlayerFactory.getMinecraft`), the
  Animation Tablet as `[ThaumcraftTablet]`. OPAC compares the acting player with the claim's owner and party (`ChunkProtection.hasChunkAccess`:
  `accessorId.equals(claimConfig.getPlayerId())`, then party and allies); a fake player passes only when its name or UUID is in OPAC's server option
  `staticFakePlayers` (default `[IntegratedTunnels]` and Ars Nouveau's UUID), and then only where the claim matches the fake player's own position
  (`isAllowedStaticFakePlayerAction`). So the lead's own placer and Dislocator in its own claim are refused (`a_placer_own`, `a_dislocator_own`
  record this as the expected result), as are the second player's aiming into it. The add-on cannot fix this with a loader-general change without
  changing whom the devices act as (their owner, which 1.7.10 did not do) - a decision for the owner, not taken here. A server can list `[Minecraft]`
  in `staticFakePlayers`; not tried, and the Remote Placer's fake player stands in its target block (up to four blocks from the placer), which OPAC
  would take for the device's origin.
* **What OPAC does, besides (all OPAC's own behaviour, documented so that nobody mistakes it for the add-on's):** it refuses the click itself
  inside a claim (`UseBlockCallback` / `PlayerInteractEvent.RightClickBlock`, `AttackBlockCallback` / `LeftClickBlock`), before any code of the
  add-on runs, so the `b_*_in` scenes are refused by OPAC directly and the edge scenes are the ones that only the add-on's question can stop. It
  refuses a stranger's item use (a right click into the air: how the wand casts a focus) in every chunk **next to** a claim as well
  (`claims.protection.neighborChunksItemUse`, on by default; `ChunkProtection.onItemRightClick` looks at the 3 x 3 chunks around the player), which
  is why `b_dislocation_out` stands two chunks away (seen in `b_dislocation_in`, from chunk 5: "(Main Hand) You are not allowed to use this item this close to somebody else's claim (thaumcraft:wand)! Try empty hand?"). It answers a refused question with the same chat message as a refused click ("(Main Hand) You
  are not allowed to interact with this block (minecraft:oak_log)!" for the branch log of `b_axe_edge`), so a player sees one line per refused log
  of a tree-mode felling. Its claim command takes an area of 2 x 1 chunks for a single chunk: `ClaimsClaimCommands` treats an area as "more than 1
  chunk" only when its middle differs from its first corner, and the middle of chunks 6 and 7 is 6, so `/oclaims claim 96 -32 127 -17` claimed
  (6, -2) alone; the lead claims one chunk per command.
* **Problems of the harness, found by these runs and fixed:** Forge Config API Port carries night-config inside its jar, which Fabric Loader does
  not unpack from a development class path (OPAC did not start: `NoClassDefFoundError: com/electronwill/nightconfig/core/ConfigFormat`); the cow of
  the Soul Hearts was discarded by vanilla on its first tick because `spawn-animals=false` (`ServerLevel.shouldDiscardEntity`), now an iron golem;
  a client window that does not hold the mouse (two clients, one X display; in an early Fabric run the second player's) does not mine with
  the attack key held, because `Minecraft.continueAttack` runs only with `mouseHandler.isMouseGrabbed()` (the harness makes the same `continueDestroyBlock` call; single clicks never needed the
  grab); the server's harness set the second player's selected hotbar slot at join without telling its client, so the client's own press of
  slot 0 changed nothing and sent nothing and the server took every cast of its Focus of Dislocation for a click with an empty hand (found with a
  record of the right clicks that reach the server; the harness now sends `ClientboundSetCarriedItemPacket`); the refusal message was compared with
  a translation made before the client had loaded its languages; the Kami-off server must not build the Kami scenes.


### Third part: the config handshake in the configuration phase (2026-11-01, branch `modernize-c`)

The config sync moved from the play phase (sent on `PLAYER_JOIN`, the client left by itself) into a configuration task of the connection
(`ConfigSync`; `DEVIATIONS.md` section 1): the server sends `PacketConfigSync` before the client has a world, the client answers with its
registration options (`PacketConfigSyncReply`), the server refuses a mismatch with the add-on's message. The scenarios were adapted to the phase,
nothing else: `config_sync` on the clients is unchanged (the copy must be there once the client is in the world); on the server
`real_connection_and_config_sync` asks whether the joining client answered the handshake with equal options (the server's harness listens to
`ConfigSync.ANSWERED`) instead of whether it registered a play channel; the census of server-to-client play payloads has 3 types (the config payload
is no play payload any more; on the second server none); `mismatch` and `kami-on` must be refused **before** the game (`refused_before_the_game`
on the client instead of `joined_before_it_left`; on the server `answered_the_config_handshake` and `refused_in_the_configuration_phase` instead of
the two join checks and the leave check of each, so the server reports have one PASS fewer each). `mismatch-kami` is unchanged.

The runs (2026-11-01, `GRADLE_CMD='<wrapper> {LOG}' tools/multiplayer-smoke.sh [--neoforge] -p 25593 / -p 25594`): both ended
`BUILD SUCCESSFUL`, exit 0, and `summary.txt` "no problems":

| Report | Fabric | NeoForge |
|---|---|---|
| `server-report.json` | COMPLETED, 61 PASS | COMPLETED, 61 PASS |
| `server-kami-off-report.json` | COMPLETED, 5 PASS | COMPLETED, 5 PASS |
| `lead/report.json` | COMPLETED, 42 PASS | COMPLETED, 42 PASS |
| `peer/report.json` | COMPLETED, 23 PASS, 1 INFO | COMPLETED, 23 PASS, 1 INFO |
| `mismatch` / `mismatch-kami` / `kami-on` | COMPLETED, 3 / 2 / 3 PASS | COMPLETED, 3 / 2 / 3 PASS |
| all seven reports | **139 PASS, 0 FAIL, 0 HARNESS_ERROR, 0 NOT_RUN** | **139 PASS, 0 FAIL, 0 HARNESS_ERROR, 0 NOT_RUN** |
| the Gradle task | 5 min 38 s | 5 min 40 s |
| pictures | 16 | 16 |

What the lines say, the same on both loaders:

* **a.** The lead and the second player answered the handshake with `{general/imbuedFire.enabled=true, general/shareTome.survival.enabled=true,
  kami/kami.forceenabled=true}`, equal to the server's, before they joined; in the world their copy of the server's config was there
  (`config_sync_arrived`, flight off and the gold block blacklisted although their files say otherwise) and was cleared when they left. Every other
  scenario of the first and second part passed as before (the add-on's play payloads are untouched).
* **b.** `TTMismatch` answered with `imbuedFire.enabled=false` and was refused in the configuration phase: it never entered the game
  (`CLIENT_PLAYER_JOIN` was not posted), its screen "Connection Lost" showed `thaumictinkerer.config.mismatch`, rendered "This server's Thaumic
  Tinkerer config differs from yours ...: general/imbuedFire.enabled (server: true, yours: false). ...", the same text as before, now built by the
  server and translated by the client. `TTKamiOn` on the second server: the loader's registry sync let it through (it has more entries than the
  server), the add-on's task refused it with `kami/kami.forceenabled (server: false, yours: true)`. `TTMismatchKami` was refused by the loader's
  registry sync before the add-on's task, as before (Fabric "Received 50 registry entries that are unknown to this client ...", NeoForge
  `neoforge.network.registries.sync.server-with-unknown-keys`); it never reached the add-on's task (no answer in the server's report).
* **Not run:** a client without the add-on (it gets no task: both glues add it only when the client has the channel, read in the code and covered by
  `ConfigHandlerTest.bothLoadersInstallTheHandshake` only as wiring), a client of an earlier version of the add-on, and a client that never answers.

## Wiring

| Tree | Package | What is in it |
|---|---|---|
| `common/src/multiplayerServer/java` | `thaumic.tinkerer.mpserver` | the server test mod: `MultiplayerServer` (armed by `-Dthaumictinkerer.mpServer=true`; Architectury's lifecycle, player and command events; watchdog; `server-ready`), `Scenes` (the world, the items, `stage`, `check`), `DeviceScenes` (sections f and g), `ClaimScenes` (section h; reads OPAC's claim owners by reflection, OPAC is not on its compile class path), `TtmpCommand`, `ServerReport` |
| `fabric/src/multiplayerServer` | `thaumic.tinkerer.fabric.mpserver` | `TinkererMultiplayerServer` (the `server` entrypoint), `fabric.mod.json` (mod id `thaumictinkerer_mpserver`, environment `server`) |
| `neoforge/src/multiplayerServer` | `thaumic.tinkerer.neoforge.mpserver` | `TinkererMultiplayerServerNeoForge` (`@Mod(value = "thaumictinkerer_mpserver", dist = Dist.DEDICATED_SERVER)`), `neoforge.mods.toml` |
| `common/src/clientSmoke/java` | `thaumic.tinkerer.clientsmoke` | the client side: `MultiplayerClient` (the roles, the shared scripts: join, config sync, disconnect), `MultiplayerActor` (what the two acting clients share: staging, held keys, the comparison of the server's numbers with the client's world), `MultiplayerLeadPhase`, `MultiplayerPeerPhase`, `MultiplayerMismatchPhase`, `ServerLink` (`/ttmp`), `Input` (keys, key mappings, clicks, the X11 shift key), `BeamParticles` (the Shadowbeam's dust in the particle engine); `ClientSmoke` picks them by `-Dthaumictinkerer.clientSmoke.mode` |
| `gradle/multiplayer.gradle` | (MIT) | `registerTinkererMultiplayer`: the task that prepares, starts, waits for, stops and summarises the seven JVMs |
| `tools/multiplayer-smoke.sh` | (MIT) | the runner under Xvfb |

* Fabric: source set `thaumicTinkererMultiplayerServer` (`fabric.gradle`), runs `tinkererMpServer` (the add-on and the test mod as two classpath groups),
  `tinkererMpLead`, `tinkererMpPeer`, `tinkererMpMismatch`, `tinkererMpMismatchKami`, `tinkererMpKamiOn` (the add-on and the client smoke
  harness), task `runTinkererMultiplayer`. NeoForge: source set `multiplayerServer` (`neoforge/build.gradle`), runs `mpServer` (mods
  `thaumictinkerer` and `thaumictinkerer_mpserver`), `mpLead`, `mpPeer`, `mpMismatch`, `mpMismatchKami`, `mpKamiOn` (mods `thaumictinkerer` and
  `thaumictinkerer_clientsmoke`), task `runMultiplayer`. The second server is the run `tinkererMpServer` / `mpServer` with one more JVM argument.
* Open Parties and Claims: Loom's remapped configuration of the server's source set only (`loom.createRemapConfigurations`;
  `modThaumicTinkererMultiplayerServerRuntimeOnly` in `fabric.gradle`, `modMultiplayerServerRuntimeOnly` in `neoforge/build.gradle`), with the
  versions in `gradle.properties` (`opac_fabric_version=fabric-1.21.1-0.33.0`, `opac_neoforge_version=neoforge-1.21.1-0.33.0`, both published
  2026-10-17; on Fabric also `forge_config_api_port_version=v21.1.6-1.21.1-Fabric`, published 2025-11-12, and night-config 3.8.0, the version
  Forge Config API Port carries inside its jar, which Fabric Loader does not unpack from a development class path). No client run, no other run
  and no jar has it; the add-on names no OPAC class.
* `check` compiles the new source set on both loaders; no jar task includes it or the client smoke (checked after the last runs: the jars in
  `build/libs` and `neoforge/build/libs` hold no `mpserver`, `clientsmoke`, `xaero`, `openpartiesandclaims` or `forgeconfigapiport` entry).
* The test mod and the client harness import Minecraft, Architectury, the Thaumcraft API and the add-on only (the loader entries their loader);
  nothing of `thaumcraft.common` or `thaumcraft.client`.

## What it cannot show

* **One machine, loopback.** Latency, packet loss, bandwidth, a slow or distant server and a client that falls behind are not exercised; both ends
  are on 127.0.0.1 and the server is idle apart from the scenarios.
* **Offline mode.** `online-mode=false`: no authentication, no chat signing (`enforce-secure-profile=false`). Player names and UUIDs are the
  offline ones.
* **Scripted players.** The clients press keys and click through the game's own input path, but nobody moves a mouse: the cross-hair is set by the
  server's teleport, screens get synthetic clicks at the centre of a button (the destinations screen at the computed place of the pearl), and only
  the actions above are made. Two places leave the game's input path a little: a key held over several ticks by a client whose window does not hold
  the mouse (the attack key: the harness calls `continueDestroyBlock` as the game's own tick would; which of the two windows on the shared display has
  it varies: in the final Fabric run the second client's did not, in the NeoForge one it did), and the one real shift key, an X event the harness
  sends to its own window. Every packet of the add-on is now sent once in its main use; the Kami armour's login status
  (`PacketArmorStatus`) is only received at login with its default value (on), not after a change.
* **One claims mod, one configuration.** Open Parties and Claims 0.33.0 with its default configuration, on the server only (no client has it, so
  no client-side prediction of OPAC is seen); no party, no ally, no OPAC permission system, no `staticFakePlayers`. Other claims mods (FTB Chunks,
  Flan, GOML, ...) listen to other events; what the add-on asks is in `HOOKS.md` section 16, which mod hears what is theirs.
* **What the client shows, not how it feels.** The client's copy of blocks, stacks, vis and block entities is compared with the server's after each
  action. A cast that the server refuses can still make the client swing and play the wand's sound (the client cannot know the spawn protection);
  nothing looks at that. Thaumcraft's `WandHelper.consumeAllVis` never changes a stack on a client, so no predicted vis payment can show either
  (the lead records every vis value its wand showed in the 40 ticks after a Dislocation cast: always the server's).
* **One run per loader for the numbers below.** Timing (how long the second player waited, how fast the clients started) differs between runs.
* **One flat world, peaceful, noon.** No other dimension (the Bedrock portal is made but not entered), no mobs but one iron golem without AI, no
  weather.
* **No GPU, no sound.** As for the client smoke: Mesa llvmpipe under Xvfb.
* **Two loaders, not mixed.** A Fabric client on a Fabric server and a NeoForge client on a NeoForge server; a client of one loader cannot join
  the other's server anyway.
