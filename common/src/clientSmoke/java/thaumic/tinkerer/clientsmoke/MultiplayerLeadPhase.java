package thaumic.tinkerer.clientsmoke;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import thaumic.tinkerer.client.core.handler.kami.SoulHeartClientHandler;
import thaumic.tinkerer.client.gui.GuiAnimationTablet;
import thaumic.tinkerer.client.gui.GuiEnchanting;
import thaumic.tinkerer.client.gui.GuiRemotePlacer;
import thaumic.tinkerer.client.gui.button.GuiButtonATRadio;
import thaumic.tinkerer.client.gui.button.GuiButtonEnchant;
import thaumic.tinkerer.client.gui.button.GuiButtonEnchantment;
import thaumic.tinkerer.client.gui.button.GuiButtonRPRadio;
import thaumic.tinkerer.client.gui.button.kami.GuiButtonWG;
import thaumic.tinkerer.client.gui.kami.GuiWarpGate;
import thaumic.tinkerer.client.gui.kami.GuiWarpGateDestinations;
import thaumic.tinkerer.common.block.tile.TileEnchanter;
import thaumic.tinkerer.common.block.tile.TileRPlacer;
import thaumic.tinkerer.common.block.tile.kami.TileWarpGate;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.core.handler.kami.KamiArmorHandler;
import thaumic.tinkerer.common.item.foci.FociComponents.DislocationData;
import thaumic.tinkerer.common.item.foci.ItemFocusDislocation;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * The lead player of the multiplayer check ({@code TTAlpha}, not an operator, survival): the config sync, the spawn protection of
 * the dedicated server (the Focus of Dislocation, the Placement Mirror and the Awakened Ichorium Pickaxe; the Remote Placer, the
 * Transvector Dislocator and the Animation Tablet inside and outside the radius), the screens and keys of the add-on (the
 * Enchanter's enchantment and start buttons, the Remote Placer's distance buttons, the Animation Tablet that the second player
 * watches, the Kami armour key, the Celestial Gateway's lock button and teleport), the server's Soul Hearts and Shadowbeam on this
 * client, and the claim of Open Parties and Claims that the second player must respect. Test-only code, new in the 1.21.1 port (no
 * 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), not distributed.
 *
 * <p>Every scenario: {@code /ttmp stage} (the server puts the player at the scenario and says where he looks), the client checks
 * that its own cross-hair shows the block the server named, presses the key a player would press, waits, asks
 * {@code /ttmp check} (what the server finds), and records one line with both sides: the server's verdict and what this client
 * shows (the block in its own world, the vis of its own copy of the wand, the stack sizes), which must agree with the server.</p>
 */
final class MultiplayerLeadPhase extends MultiplayerActor {
    private static final String PHASE = "lead";
    private static final int SLOT_SWORD = 3;
    private static final int SLOT_TOOL = 4;
    private static final int SLOT_EXTRA = 7;
    /** The key the harness binds the add-on's unbound "Toggle Awakened Armor" mapping to, as a player would in the controls screen. */
    private static final String TOGGLE_KEY = "key.keyboard.u";

    MultiplayerLeadPhase(ServerLink link) {
        super(link, PHASE);
    }

    @Override
    protected List<Script> scripts(SmokeRun run) {
        List<Script> scripts = new ArrayList<>();
        scripts.add(MultiplayerClient.join(PHASE));
        scripts.add(MultiplayerClient.configSync(PHASE));
        scripts.add(flight());
        // The second player watches the tablet first, then works at the claim, which the lead makes next.
        scripts.add(tablet(run));
        scripts.add(claim());
        for (String name : List.of("dislocation_blacklisted", "dislocation_inside", "dislocation_outside")) scripts.add(dislocation(name));
        for (String name : List.of("dislocation_place_inside", "dislocation_place_outside")) scripts.add(dislocationPlace(name));
        for (String name : List.of("mirror_inside", "mirror_boundary", "mirror_outside")) scripts.add(mirror(name));
        for (String name : List.of("pickaxe_inside", "pickaxe_outside")) scripts.add(pickaxe(name));
        scripts.add(enchanter(run));
        // f. The devices under spawn protection.
        scripts.add(placerMenu(run));
        scripts.add(press("placer_outside", "remote_placer", 30));
        scripts.add(press("placer_inside", "remote_placer", 40));
        scripts.add(press("dislocator_inside", "dislocator", 40));
        scripts.add(press("dislocator_outside", "dislocator", 30));
        scripts.add(press("device_tablet_inside", "animation_tablet", 100, run, "lead-devices-inside",
                "The three devices inside the spawn radius (x = 16) after their buttons (x = 18, outside) were pressed, seen by the lead from the"
                        + " east: the Remote Placer (front) placed nothing, the Dislocator kept its lapis in front and the emerald end, the tablet's"
                        + " stone (back) is still there"));
        scripts.add(press("device_tablet_outside", "animation_tablet", 60, run, "lead-devices-outside",
                "The same three devices outside the radius (x = 40): planks three blocks west of the placer (front), emerald in front of the"
                        + " Dislocator (swapped with the lapis of its end), the tablet's stone (back) broken"));
        // h. The lead in its own claim.
        scripts.add(act("a_dislocation_own", "focus_dislocation", Act.RIGHT_CLICK, 40));
        scripts.add(act("a_mirror_own", "placement_mirror", Act.RIGHT_CLICK, 40));
        scripts.add(act("a_pickaxe_own", "ichor_pick_gem", Act.LEFT_CLICK, 40));
        scripts.add(act("a_axe_own", "ichor_axe_gem", Act.HOLD_ATTACK, 20));
        scripts.add(press("a_placer_own", "remote_placer", 40));
        scripts.add(press("a_dislocator_own", "dislocator", 40));
        // g. The keys and the server-to-client packets of the Kami gear, and the Celestial Gateway.
        scripts.add(armor("armor_off", false));
        scripts.add(armor("armor_on", true));
        scripts.add(soulHearts(run));
        scripts.add(shadowBeam(run));
        scripts.add(gateTeleport(run, "gate_teleport", true));
        scripts.add(gateLock(run));
        scripts.add(gateTeleport(run, "gate_locked", false));
        scripts.add(waitForPeer());
        scripts.add(MultiplayerClient.disconnect(PHASE, link));
        return scripts;
    }

    // ---- a: the Focus of Uprising with flight off on the server ------------------------------------------------------

    private Script flight() {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        double[] fastest = { 0 };
        int[] watched = { 0 };
        return staged("flight", stage, before, mark)
                .run("right click into the sky", run -> Input.rightClick(run.client()))
                .then("watch the player", run -> {
                    fastest[0] = Math.max(fastest[0], run.client().player.getDeltaMovement().length());
                    return ++watched[0] >= 40;
                }, 30)
                .then("check", check("flight", () -> "", check), 30)
                .run("record", run -> {
                    Minecraft client = run.client();
                    View after = view(client, stage[0]);
                    boolean ok = check[0].passed() && after.vis().equals(before[0].vis()) && after.vis().equals(check[0].get("vis"))
                            && fastest[0] < 0.3;
                    MultiplayerClient.record(run, PHASE, "flight", "focus_flight", ok, "server " + check[0].status() + ": " + check[0].text()
                            + "; client: wand vis " + before[0].vis() + " -> " + after.vis() + " (server " + check[0].get("vis")
                            + "), fastest motion " + String.format("%.3f", fastest[0]) + " blocks/tick (a cast gives 0.667),"
                            + " flightEnabled(client level)=" + ConfigHandler.flightEnabled(client.level));
                });
    }

    // ---- c: the Focus of Dislocation (and a: the synced blacklist) ----------------------------------------------------

    private Script dislocation(String name) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        // What the client's own copy of the wand showed while the server answered: a prediction of the payment, put back by the server.
        java.util.LinkedHashSet<String> shown = new java.util.LinkedHashSet<>();
        int[] watched = { 0 };
        return staged(name, stage, before, mark)
                .run("right click (the wand's use)", run -> Input.rightClick(run.client()))
                .then("watch the client's wand", run -> {
                    shown.add(MultiplayerClient.vis(run.client().player.getInventory().getItem(Integer.parseInt(stage[0].get("slot")))));
                    return ++watched[0] >= 40;
                }, 30)
                .then("check", check(name, () -> "", check), 30)
                .ticks(10)
                .run("record", run -> {
                    Minecraft client = run.client();
                    View after = view(client, stage[0]);
                    ItemStack wand = client.player.getInventory().getItem(Integer.parseInt(stage[0].get("slot")));
                    DislocationData carried = ItemFocusDislocation.getData(wand);
                    String expected = switch (name) {
                        case "dislocation_blacklisted" -> "minecraft:gold_block";
                        case "dislocation_inside" -> "minecraft:stone";
                        default -> "minecraft:air";
                    };
                    boolean visAgrees = after.vis().equals(check[0].get("vis"));
                    boolean extra = switch (name) {
                        // the client knows the server's blacklist, so it does not even predict the payment
                        case "dislocation_blacklisted" -> after.vis().equals(before[0].vis()) && carried == null;
                        case "dislocation_inside" -> carried == null;
                        default -> carried != null && carried.state().getBlock() == net.minecraft.world.level.block.Blocks.STONE;
                    };
                    boolean ok = check[0].passed() && after.state().equals(expected) && visAgrees && extra;
                    MultiplayerClient.record(run, PHASE, name, "focus_dislocation", ok, "server " + check[0].status() + ": "
                            + check[0].text() + "; client: block " + before[0].state() + " -> " + after.state() + " (expected " + expected
                            + "), its wand's vis " + before[0].vis() + " -> " + after.vis() + (visAgrees ? " = the server's" : " != the server's "
                            + check[0].get("vis")) + ", its focus carries " + (carried == null ? "nothing" : carried.state())
                            + "; the wand's vis this client showed in the 40 ticks after the click: " + shown);
                });
    }

    /** The other half of the focus: putting the stone it carries down, against a block inside the radius, then outside it. */
    private Script dislocationPlace(String name) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        return staged(name, stage, before, mark)
                .run("right click (the wand's use)", run -> Input.rightClick(run.client()))
                .ticks(40)
                .then("check", check(name, () -> "", check), 30)
                .ticks(10)
                .run("record", run -> {
                    Minecraft client = run.client();
                    BlockPos at = MultiplayerClient.pos(stage[0].get("target")).relative(Direction.byName(stage[0].get("face")));
                    String placed = MultiplayerClient.blockId(client, at);
                    ItemStack wand = client.player.getInventory().getItem(Integer.parseInt(stage[0].get("slot")));
                    DislocationData carried = ItemFocusDislocation.getData(wand);
                    boolean inside = name.endsWith("inside");
                    boolean clientAgrees = placed.equals(check[0].get("placed")) && (carried == null ? "nothing"
                            : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(carried.state().getBlock()).toString()).equals(check[0].get("carried"))
                            && MultiplayerClient.vis(wand).equals(check[0].get("vis"));
                    boolean ok = check[0].passed() && clientAgrees && placed.equals(inside ? "minecraft:air" : "minecraft:stone");
                    MultiplayerClient.record(run, PHASE, name, "focus_dislocation", ok, "server " + check[0].status() + ": " + check[0].text()
                            + "; client: the block at " + at.toShortString() + " is " + placed + ", its focus carries "
                            + (carried == null ? "nothing" : carried.state()) + (clientAgrees ? " (agrees with the server)" : " (differs from the server)"));
                });
    }

    // ---- c: the Placement Mirror -----------------------------------------------------------------------------------

    private Script mirror(String name) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        return staged(name, stage, before, mark)
                .run("right click (the mirror's use on the wall)", run -> Input.rightClick(run.client()))
                .ticks(40)
                .then("check", check(name, () -> "", check), 30)
                .ticks(10)
                .run("record", run -> {
                    Minecraft client = run.client();
                    View after = view(client, stage[0]);
                    String[] box = stage[0].get("square").split(":");
                    BlockPos from = MultiplayerClient.pos(box[0]);
                    BlockPos to = MultiplayerClient.pos(box[1]);
                    BlockPos spawn = MultiplayerClient.pos(stage[0].get("spawn"));
                    int radius = Integer.parseInt(stage[0].get("radius"));
                    int inside = 0;
                    int outside = 0;
                    for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                        if (!client.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.STONE)) continue;
                        boolean protectedPos = Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ())) <= radius;
                        if (protectedPos) inside++;
                        else outside++;
                    }
                    boolean agrees = String.valueOf(inside).equals(check[0].get("placed_inside"))
                            && String.valueOf(outside).equals(check[0].get("placed_outside")) && String.valueOf(after.stone()).equals(check[0].get("stone"));
                    boolean ok = check[0].passed() && agrees;
                    MultiplayerClient.record(run, PHASE, name, "placement_mirror", ok, "server " + check[0].status() + ": " + check[0].text()
                            + "; client: stone in the square " + inside + " inside the radius and " + outside + " outside, stone in its inventory "
                            + before[0].stone() + " -> " + after.stone() + (agrees ? " (agrees with the server)" : " (differs from the server)"));
                });
    }

    // ---- c: the Awakened Ichorium Pickaxe on bedrock ------------------------------------------------------------------

    private Script pickaxe(String name) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        return staged(name, stage, before, mark)
                .run("left click while sneaking", run -> Input.leftClick(run.client()))
                .ticks(40)
                .then("check", check(name, () -> "", check), 30)
                .run("stand up", run -> release(run.client()))
                .ticks(10)
                .run("record", run -> {
                    Minecraft client = run.client();
                    View after = view(client, stage[0]);
                    boolean agrees = after.state().equals(check[0].get("state")) && String.valueOf(after.damage()).equals(check[0].get("damage"));
                    boolean ok = check[0].passed() && agrees;
                    MultiplayerClient.record(run, PHASE, name, "ichor_pick_gem", ok, "server " + check[0].status() + ": " + check[0].text()
                            + "; client: block " + before[0].state() + " -> " + after.state() + ", pickaxe damage " + after.damage()
                            + (agrees ? " (agrees with the server)" : " (differs from the server)"));
                });
    }

    // ---- d and g: the Osmotic Enchanter, its enchantment button and its start button ----------------------------------

    private Script enchanter(SmokeRun run) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        ServerLink.Reply[] restage = { null };
        ServerLink.Reply[] started = { null };
        View[] before = { null };
        int[] mark = { 0 };
        int[] slots = { -1, -1 };
        GuiButtonEnchantment[] button = { null };
        GuiButtonEnchant[] start = { null };
        String[] chosen = { "" };
        boolean[] sawWorking = { false };
        return staged("enchanter", stage, before, mark)
                .run("right click the Enchanter", r -> Input.rightClick(r.client()))
                .until("the Enchanter's screen (opened by the server)", () -> Minecraft.getInstance().screen instanceof GuiEnchanting, 20)
                .run("find the slots", r -> {
                    Minecraft client = r.client();
                    GuiEnchanting screen = (GuiEnchanting) client.screen;
                    slots[0] = Input.menuSlot(client.player.containerMenu, c -> c == client.player.getInventory(), SLOT_SWORD);
                    slots[1] = Input.menuSlot(client.player.containerMenu, c -> c == screen.enchanter, TileEnchanter.SLOT_TOOL);
                    if (slots[0] < 0 || slots[1] < 0) throw new IllegalStateException("menu slots not found: " + slots[0] + ", " + slots[1]);
                })
                .run("pick up the sword", r -> Input.clickSlot(r.client(), slots[0]))
                .ticks(5)
                .run("put it into the tool slot", r -> Input.clickSlot(r.client(), slots[1]))
                .until("the tool slot shows the sword", () -> {
                    Minecraft client = Minecraft.getInstance();
                    return client.screen instanceof GuiEnchanting screen && screen.enchanter.getItem(TileEnchanter.SLOT_TOOL).is(Items.DIAMOND_SWORD)
                            && client.player.containerMenu.getCarried().isEmpty();
                }, 20)
                .until("the screen offers enchantments", () -> {
                    if (!(Minecraft.getInstance().screen instanceof GuiEnchanting screen)) return false;
                    for (var child : screen.children()) {
                        if (child instanceof GuiButtonEnchantment candidate && candidate.getClass() == GuiButtonEnchantment.class && candidate.active
                                && candidate.visible && candidate.enchant != null) {
                            button[0] = candidate;
                            return true;
                        }
                    }
                    return false;
                }, 20)
                .run("click the first enchantment", r -> {
                    chosen[0] = button[0].enchant.location().toString();
                    Input.clickWidget(r.client().screen, button[0]);
                })
                .until("the client's Enchanter shows the choice (the server's block entity update)", () -> {
                    Minecraft client = Minecraft.getInstance();
                    return client.screen instanceof GuiEnchanting screen
                            && screen.enchanter.enchantments.stream().anyMatch(k -> k.location().toString().equals(chosen[0]));
                }, 20)
                .frames(6)
                .then("picture", run.shots().shot("lead-enchanter", MultiplayerClient.GROUP,
                        "The lead's Osmotic Enchanter screen on the dedicated server, after the diamond sword was put in by a container click and the first offered enchantment chosen (the framed icon on the right is what the server sent back)"))
                .then("check", check("enchanter", () -> chosen[0], check), 30)
                .run("record", r -> {
                    boolean ok = check[0].passed() && check[0].get("enchants").contains(chosen[0]);
                    MultiplayerClient.record(r, PHASE, "enchanter", "enchanter", ok, "server " + check[0].status() + ": " + check[0].text()
                            + "; client: the screen opened from the server's menu packet, the sword went in by a container click, the button of "
                            + chosen[0] + " sent PacketEnchanterAddEnchant and the client's block entity showed the choice");
                })
                // g. The start button: the six pillars and the wand in the wand slot were built by the server's harness.
                .then("stage the start where the lead stands", link.ask("stage", "enchanter_start", "", restage), 30)
                .until("the start button is active", () -> {
                    if (!(Minecraft.getInstance().screen instanceof GuiEnchanting screen)) return false;
                    start[0] = Input.widget(screen, GuiButtonEnchant.class);
                    return start[0] != null && start[0].active && start[0].visible;
                }, 20)
                .run("click the start button", r -> Input.clickWidget(r.client().screen, start[0]))
                .until("the client's Enchanter has finished (the server's updates: working, then the enchanted sword)", () -> {
                    Minecraft client = Minecraft.getInstance();
                    if (!(client.screen instanceof GuiEnchanting screen)) return false;
                    if (screen.enchanter.working) sawWorking[0] = true;
                    return !screen.enchanter.working && sharpness(client, screen.enchanter.getItem(TileEnchanter.SLOT_TOOL)) == 1;
                }, 90)
                .frames(6)
                .then("picture of the finished work", run.shots().shot("lead-enchanter-done", MultiplayerClient.GROUP,
                        "The lead's Osmotic Enchanter screen after its start button: the server took the vis from the wand in the Enchanter and put Sharpness I on the sword (the tool slot shows it enchanted, the framed choice is gone)"))
                .run("close the screen", r -> r.client().player.closeContainer())
                .ticks(10)
                .then("check the start", check("enchanter_start", () -> "", started), 90)
                .run("record the start", r -> {
                    Minecraft client = r.client();
                    BlockPos at = MultiplayerClient.pos(stage[0].get("target"));
                    int mine = client.level.getBlockEntity(at) instanceof TileEnchanter table ? sharpness(client, table.getItem(TileEnchanter.SLOT_TOOL)) : -1;
                    boolean agrees = String.valueOf(mine).equals(started[0].get("sharpness"));
                    MultiplayerClient.record(r, PHASE, "enchanter_start", "enchanter", started[0].passed() && agrees && sawWorking[0],
                            "server " + started[0].status() + ": " + started[0].text() + "; client: the start button sent PacketEnchanterStartWorking, its"
                                    + " block entity showed the work " + (sawWorking[0] ? "running" : "NOT running") + " and then the sword with Sharpness "
                                    + mine + (agrees ? " (agrees with the server)" : " (differs from the server)"));
                });
    }

    private static int sharpness(Minecraft client, ItemStack stack) {
        return client.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.SHARPNESS)
                .map(holder -> stack.getEnchantments().getLevel(holder)).orElse(-1);
    }

    // ---- e: the Animation Tablet, which the second player watches -------------------------------------------------------

    private Script tablet(SmokeRun run) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        int[] slots = { -1, -1 };
        boolean[] clicked = { false };
        return staged("tablet", stage, before, mark)
                .run("right click the tablet", r -> Input.rightClick(r.client()))
                .until("the tablet's screen (opened by the server)", () -> Minecraft.getInstance().screen instanceof GuiAnimationTablet, 20)
                .run("find the slots", r -> {
                    Minecraft client = r.client();
                    slots[0] = Input.menuSlot(client.player.containerMenu, c -> c == client.player.getInventory(), SLOT_TOOL);
                    slots[1] = Input.menuSlot(client.player.containerMenu, c -> c instanceof TileAnimationTablet, 0);
                    if (slots[0] < 0 || slots[1] < 0) throw new IllegalStateException("menu slots not found: " + slots[0] + ", " + slots[1]);
                })
                .run("pick up the iron pickaxe", r -> Input.clickSlot(r.client(), slots[0]))
                .ticks(5)
                .run("put it into the tablet", r -> Input.clickSlot(r.client(), slots[1]))
                .until("the tablet slot shows the pickaxe", () -> {
                    Minecraft client = Minecraft.getInstance();
                    return client.screen instanceof GuiAnimationTablet && client.player.containerMenu.getSlot(slots[1]).getItem().is(Items.IRON_PICKAXE)
                            && client.player.containerMenu.getCarried().isEmpty();
                }, 20)
                .run("click the right-click button", r -> {
                    // A new tablet is in left-click mode; the second radio button (GuiAnimationTablet adds left, then right) switches it.
                    List<GuiButtonATRadio> radios = Input.widgets(r.client().screen, GuiButtonATRadio.class);
                    if (radios.size() != 2) throw new IllegalStateException("the tablet's screen has " + radios.size() + " radio buttons");
                    clicked[0] = Input.clickWidget(r.client().screen, radios.get(1));
                })
                .ticks(10)
                .frames(6)
                .then("picture", run.shots().shot("lead-tablet", MultiplayerClient.GROUP,
                        "The lead's Animation Tablet screen on the dedicated server: the iron pickaxe put in by a container click, the right-click mode chosen (PacketTabletButton)"))
                .run("close the screen", r -> r.client().player.closeContainer())
                .ticks(20)
                .then("check", check("tablet", () -> "", check), 30)
                .run("record", r -> {
                    boolean ok = check[0].passed() && clicked[0];
                    MultiplayerClient.record(r, PHASE, "tablet", "animation_tablet", ok, "server " + check[0].status() + ": " + check[0].text()
                            + "; client: the screen opened from the server's menu packet, the pickaxe went in by a container click, the right-click radio button took the click: "
                            + clicked[0]);
                });
    }

    // ---- f: the outside Remote Placer's screen: the planks by container clicks, the distance by a radio button ----------

    private Script placerMenu(SmokeRun run) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        int[] slots = { -1, -1 };
        BlockPos[] placer = { null };
        int blocks = 3;
        return staged("placer_menu", stage, before, mark)
                .until("the planks the server gave", () -> Minecraft.getInstance().player.getInventory().getItem(SLOT_EXTRA).is(Items.OAK_PLANKS), 20)
                .run("sneaking right click with the wand (the placer's screen)", r -> {
                    placer[0] = MultiplayerClient.pos(stage[0].get("target"));
                    Input.rightClick(r.client());
                })
                .until("the placer's screen (opened by the server)", () -> Minecraft.getInstance().screen instanceof GuiRemotePlacer, 20)
                .run("stand up", r -> release(r.client()))
                .run("find the slots", r -> {
                    Minecraft client = r.client();
                    slots[0] = Input.menuSlot(client.player.containerMenu, c -> c == client.player.getInventory(), SLOT_EXTRA);
                    slots[1] = Input.menuSlot(client.player.containerMenu, c -> c instanceof TileRPlacer, 0);
                    if (slots[0] < 0 || slots[1] < 0) throw new IllegalStateException("menu slots not found: " + slots[0] + ", " + slots[1]);
                })
                .run("pick up the planks", r -> Input.clickSlot(r.client(), slots[0]))
                .ticks(5)
                .run("put them into the placer", r -> Input.clickSlot(r.client(), slots[1]))
                .until("the placer's slot shows the planks", () -> {
                    Minecraft client = Minecraft.getInstance();
                    return client.screen instanceof GuiRemotePlacer && client.player.containerMenu.getSlot(slots[1]).getItem().is(Items.OAK_PLANKS)
                            && client.player.containerMenu.getCarried().isEmpty();
                }, 20)
                .run("click the radio button of " + blocks + " blocks", r -> {
                    List<GuiButtonRPRadio> radios = Input.widgets(r.client().screen, GuiButtonRPRadio.class);
                    if (radios.size() != 4) throw new IllegalStateException("the placer's screen has " + radios.size() + " radio buttons");
                    Input.clickWidget(r.client().screen, radios.get(blocks - 1));
                })
                .ticks(10)
                .frames(6)
                .then("picture", run.shots().shot("lead-placer", MultiplayerClient.GROUP,
                        "The lead's Remote Placer screen on the dedicated server: the planks put in by container clicks, the third distance button chosen (PacketPlacerButton)"))
                .run("close the screen", r -> r.client().player.closeContainer())
                .ticks(20)
                .then("check", check("placer_menu", () -> String.valueOf(blocks), check), 30)
                .run("record", r -> {
                    Minecraft client = r.client();
                    TileRPlacer mine = client.level.getBlockEntity(placer[0]) instanceof TileRPlacer p ? p : null;
                    String shown = mine == null ? "no placer" : "blocks=" + mine.blocks + " slot=" + stack(mine.getItem(0));
                    boolean agrees = mine != null && String.valueOf(mine.blocks).equals(check[0].get("blocks")) && stack(mine.getItem(0)).equals(check[0].get("slot"));
                    MultiplayerClient.record(r, PHASE, "placer_menu", "remote_placer", check[0].passed() && agrees, "server " + check[0].status()
                            + ": " + check[0].text() + "; client: the screen opened from the server's menu packet after a sneaking wand click, its"
                            + " block entity shows " + shown + (agrees ? " (agrees with the server)" : " (differs from the server)"));
                });
    }

    // ---- g: the Kami armour's key ---------------------------------------------------------------------------------------

    private Script armor(String name, boolean on) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        String[] bound = { "" };
        boolean[] statusBefore = { false };
        return staged(name, stage, before, mark)
                .until("the Gem Boots on this client's player", () -> Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.FEET)
                        .is(TTRegistry.item(LibItemNames.ICHOR_BOOTS_GEM)), 20)
                .until("the armour's state before the key (" + (on ? "off" : "on") + ")", () -> hasted(Minecraft.getInstance()) != on
                        && KamiArmorHandler.clientStatus() != on, 20)
                .run("press the Toggle Awakened Armor key", r -> {
                    statusBefore[0] = KamiArmorHandler.clientStatus();
                    bound[0] = Input.pressMapping(r.client(), "ttmisc.toggleArmor", TOGGLE_KEY);
                })
                .ticks(40)
                .then("check", check(name, () -> "", check), 30)
                .run("record", r -> {
                    Minecraft client = r.client();
                    boolean status = KamiArmorHandler.clientStatus();
                    boolean haste = hasted(client);
                    boolean agrees = String.valueOf(status).equals(check[0].get("status")) && String.valueOf(haste).equals(check[0].get("haste"));
                    MultiplayerClient.record(r, PHASE, name, "ichorcloth_boots_gem", check[0].passed() && agrees && status == on, "server "
                            + check[0].status() + ": " + check[0].text() + "; client: the key " + bound[0] + " switched this client's copy "
                            + statusBefore[0] + " -> " + status + " (PacketToggleArmor to the server), Haste II on this client's player: " + haste
                            + (agrees ? " (agrees with the server)" : " (differs from the server)"));
                });
    }

    private static boolean hasted(Minecraft client) {
        MobEffectInstance haste = client.player.getEffect(MobEffects.DIG_SPEED);
        return haste != null && haste.getAmplifier() >= 1;
    }

    // ---- g: Soul Hearts from the server ---------------------------------------------------------------------------------

    private Script soulHearts(SmokeRun run) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        int[] hpBefore = { 0 };
        String[] aimed = { "" };
        return staged("soul_hearts", stage, before, mark)
                .run("left click the cow", r -> {
                    hpBefore[0] = SoulHeartClientHandler.clientPlayerHP;
                    aimed[0] = String.valueOf(aimedEntity(r.client()) == null ? "nothing" : aimedEntity(r.client()).getType().toShortString());
                    Input.leftClick(r.client());
                })
                .until("this client's Soul Hearts change (PacketSoulHearts)", () -> SoulHeartClientHandler.clientPlayerHP != hpBefore[0], 10)
                .ticks(10)
                .frames(6)
                .then("picture", run.shots().shot("lead-soul-hearts", MultiplayerClient.GROUP,
                        "The lead's HUD after one hit with the Awakened Ichorium Sword in Soul mode on a cow: half a Soul Heart above the health bar, drawn from the number the server sent (PacketSoulHearts)"))
                .then("check", check("soul_hearts", () -> "", check), 30)
                .run("record", r -> {
                    int mine = SoulHeartClientHandler.clientPlayerHP;
                    boolean agrees = String.valueOf(mine).equals(check[0].get("hp"));
                    boolean cow = aimed[0].equals("cow") && aimedEntity(r.client()) instanceof Cow;
                    MultiplayerClient.record(r, PHASE, "soul_hearts", "ichor_sword_gem", check[0].passed() && agrees && cow, "server "
                            + check[0].status() + ": " + check[0].text() + "; client: the cross-hair showed " + aimed[0] + ", this client's Soul Hearts "
                            + hpBefore[0] + " -> " + mine + (agrees ? " (agrees with the server)" : " (differs from the server)"));
                });
    }

    // ---- g: the Shadowbeam's path from the server ------------------------------------------------------------------------

    private Script shadowBeam(SmokeRun run) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        int[] held = { 0 };
        int[] most = { 0 };
        int[] baseline = { 0 };
        return staged("shadow_beam", stage, before, mark)
                .run("count before", r -> baseline[0] = BeamParticles.count(r.client()))
                .run("press and hold the use key", r -> {
                    Input.rightClick(r.client());
                    using = true;
                })
                .then("hold it 20 ticks, counting the beam's particles", r -> {
                    most[0] = Math.max(most[0], BeamParticles.count(r.client()));
                    return ++held[0] >= 20;
                }, 30)
                .then("picture", run.shots().shot("lead-shadow-beam", MultiplayerClient.GROUP,
                        "The lead holding the use key with the Shadowbeam staff, looking up into the sky: the dark dust along the beam is what this client drew from the path the server sent (PacketShadowBeam)"))
                .then("hold 10 more ticks", r -> {
                    most[0] = Math.max(most[0], BeamParticles.count(r.client()));
                    return ++held[0] >= 30;
                }, 30)
                .run("let go", r -> release(r.client()))
                .ticks(20)
                .then("check", check("shadow_beam", () -> "", check), 30)
                .run("record", r -> {
                    List<String> differ = differences(r.client(), stage[0], check[0]);
                    boolean drawn = most[0] > baseline[0] && most[0] > 0;
                    MultiplayerClient.record(r, PHASE, "shadow_beam", "focus_shadowbeam", check[0].passed() && differ.isEmpty() && drawn, "server "
                            + check[0].status() + ": " + check[0].text() + "; client: " + (differ.isEmpty() ? "its wand's vis agrees with the server's"
                            : "differs: " + differ) + "; " + BeamParticles.method + ": " + baseline[0] + " before, at most " + most[0]
                            + " while the use key was held");
                });
    }

    // ---- g: the Celestial Gateway: the destinations screen, its shift click, the lock button ------------------------------

    private Script gateTeleport(SmokeRun run, String name, boolean expectMoved) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        Vec3[] start = { null };
        boolean[] shift = { false };
        return staged(name, stage, before, mark)
                .until("the destinations screen (the gate's own client tick, the player sneaking above it)",
                        () -> Minecraft.getInstance().screen instanceof GuiWarpGateDestinations, 20)
                .run("press the real shift key", r -> {
                    start[0] = r.client().player.position();
                    Input.realShift(r.client(), true);
                })
                .until("GLFW reports shift down", () -> Input.glfwShiftDown(Minecraft.getInstance()), 10)
                .frames(4)
                .then("picture of the destinations", name.equals("gate_teleport") ? run.shots().shot("lead-warp-destinations", MultiplayerClient.GROUP,
                        "The lead's destinations screen of Celestial Gateway A, opened by sneaking on it: the gateway in the middle and the pearl of gate B ten blocks south") : r -> true)
                .run("shift click on gate B's pearl", r -> {
                    Minecraft client = r.client();
                    shift[0] = Input.glfwShiftDown(client);
                    // the map is centred on gate A; gate B is ten blocks south of it (one block is one GUI pixel)
                    double x = client.screen.width / 2 + 0.5;
                    double y = client.screen.height / 2 + 10.5;
                    client.screen.mouseClicked(x, y, 0);
                    if (client.screen != null) client.screen.mouseReleased(x, y, 0);
                })
                .run("let go of shift", r -> {
                    Input.realShift(r.client(), false);
                    release(r.client());
                    if (r.client().screen instanceof GuiWarpGateDestinations) r.client().setScreen(null);
                })
                .ticks(5)
                // the gate's client tick may have seen the player still sneaking for one more tick and opened the screen again
                .run("close the destinations screen if it opened again", r -> {
                    if (r.client().screen instanceof GuiWarpGateDestinations) r.client().setScreen(null);
                })
                .then("wait for the teleport (or not)", r -> {
                    double moved = r.client().player.position().distanceTo(start[0]);
                    return expectMoved ? moved > 5 : true;
                }, 10)
                .ticks(expectMoved ? 10 : 40)
                .then("check", check(name, () -> "", check), 30)
                .run("record", r -> {
                    Minecraft client = r.client();
                    Vec3 at = client.player.position();
                    Vec3 server = new Vec3(Double.parseDouble(check[0].get("x")), Double.parseDouble(check[0].get("y")), Double.parseDouble(check[0].get("z")));
                    boolean agrees = at.distanceTo(server) < 0.5;
                    List<String> messages = link.since(mark[0]);
                    // translated now: the scripts are built before the client has loaded its languages
                    String refused = Component.translatable("ttmisc.noTeleport").getString();
                    boolean refusedSeen = messages.contains(refused);
                    boolean ok = check[0].passed() && agrees && shift[0] && (expectMoved || refusedSeen);
                    MultiplayerClient.record(r, PHASE, name, "warp_gate", ok, "server " + check[0].status() + ": " + check[0].text() + "; client: shift held"
                            + " (GLFW) " + shift[0] + ", its player " + String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f) -> (%.2f, %.2f, %.2f)",
                            start[0].x, start[0].y, start[0].z, at.x, at.y, at.z) + (agrees ? " (agrees with the server)" : " (differs from the server)")
                            + (expectMoved ? "" : "; the refusal \"" + refused + "\" " + (refusedSeen ? "arrived" : "did NOT arrive")) + "; messages: " + messages);
                });
    }

    private Script gateLock(SmokeRun run) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        BlockPos[] gate = { null };
        return staged("gate_lock", stage, before, mark)
                .run("right click gate B", r -> {
                    gate[0] = MultiplayerClient.pos(stage[0].get("target"));
                    Input.rightClick(r.client());
                })
                .until("the gateway's screen (opened by the server)", () -> Minecraft.getInstance().screen instanceof GuiWarpGate, 20)
                .run("click the lock button", r -> {
                    GuiButtonWG button = Input.widget(r.client().screen, GuiButtonWG.class);
                    if (button == null) throw new IllegalStateException("no lock button");
                    Input.clickWidget(r.client().screen, button);
                })
                .ticks(10)
                .frames(6)
                .then("picture", run.shots().shot("lead-warp-gate", MultiplayerClient.GROUP,
                        "The lead's screen of Celestial Gateway B after a click on its lock button (PacketWarpGateButton): the button shows the gate locked"))
                .run("close the screen", r -> r.client().player.closeContainer())
                .ticks(20)
                .then("check", check("gate_lock", () -> "", check), 30)
                .run("record", r -> {
                    boolean mine = r.client().level.getBlockEntity(gate[0]) instanceof TileWarpGate tile && tile.locked;
                    boolean agrees = String.valueOf(mine).equals(check[0].get("locked"));
                    MultiplayerClient.record(r, PHASE, "gate_lock", "warp_gate", check[0].passed() && agrees, "server " + check[0].status() + ": "
                            + check[0].text() + "; client: its gate B is " + (mine ? "locked" : "open") + (agrees ? " (agrees with the server)" : " (differs)"));
                });
    }

    // ---- h: the lead's claim -------------------------------------------------------------------------------------------

    private Script claim() {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        // One chunk per command: OPAC 0.33.0 takes an area of 2 x 1 chunks for one chunk (ClaimsClaimCommands: an area is "more than 1
        // chunk" only when its middle differs from its first corner, and the middle of 6..7 is 6) and claims only its middle.
        String first = "oclaims claim 96 -32 111 -17";
        String second = "oclaims claim 112 -32 127 -17";
        return staged("a_claim", stage, before, mark)
                .run("type OPAC's claim command for the first chunk", r -> r.client().getConnection().sendCommand(first))
                .ticks(20)
                .run("and for the second", r -> r.client().getConnection().sendCommand(second))
                .ticks(40)
                .then("check", check("a_claim", () -> "", check), 40)
                .run("record", r -> MultiplayerClient.record(r, PHASE, "a_claim", "openpartiesandclaims", check[0].passed(), "server "
                        + check[0].status() + ": " + check[0].text() + "; client: typed /" + first + " and /" + second + "; OPAC answered "
                        + link.since(mark[0])));
    }

    /** At the end: the lead stays until the second player, who works in its claim, has left. */
    private Script waitForPeer() {
        ServerLink.Reply[] stage = { null };
        return new Script("wait_peer")
                .run("connected", r -> MultiplayerClient.requireConnected(r, PHASE, "wait_peer"))
                .then("wait until the second player has left", link.ask("stage", "wait_peer", "", stage), 900);
    }
}
