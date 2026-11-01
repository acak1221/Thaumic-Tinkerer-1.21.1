package thaumic.tinkerer.clientsmoke;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumic.tinkerer.client.gui.GuiAnimationTablet;
import thaumic.tinkerer.client.gui.GuiEnchanting;
import thaumic.tinkerer.client.gui.button.GuiButtonATRadio;
import thaumic.tinkerer.client.gui.button.GuiButtonEnchantment;
import thaumic.tinkerer.common.block.tile.TileEnchanter;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.item.foci.FociComponents.DislocationData;
import thaumic.tinkerer.common.item.foci.ItemFocusDislocation;

/**
 * The lead player of the multiplayer check ({@code TTAlpha}, not an operator, survival): the config sync, the spawn protection of
 * the dedicated server (the Focus of Dislocation, the Placement Mirror and the Awakened Ichorium Pickaxe inside and outside the
 * radius), a menu round trip with the Osmotic Enchanter, and the change of an Animation Tablet that the second player must see.
 * Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), not
 * distributed.
 *
 * <p>Every scenario: {@code /ttmp stage} (the server puts the player at the scenario and says where he looks), the client checks
 * that its own cross-hair shows the block the server named, presses the key a player would press, waits, asks
 * {@code /ttmp check} (what the server finds), and records one line with both sides: the server's verdict and what this client
 * shows (the block in its own world, the vis of its own copy of the wand, the stack sizes), which must agree with the server.</p>
 */
final class MultiplayerLeadPhase extends ScriptedPhase {
    private static final String PHASE = "lead";
    private static final int SLOT_SWORD = 3;
    private static final int SLOT_TOOL = 4;
    private static final int SLOT_STONE = 5;
    private static final int SLOT_PICK = 2;

    private final ServerLink link;
    /** While true the sneak key is held down every tick (nothing a screen opens resets it then). */
    private boolean sneaking;

    MultiplayerLeadPhase(ServerLink link) {
        this.link = link;
    }

    @Override
    public String name() {
        return PHASE;
    }

    @Override
    public boolean tick(SmokeRun run) throws Exception {
        if (sneaking && run.client() != null) Input.sneak(run.client(), true);
        return super.tick(run);
    }

    @Override
    protected List<Script> scripts(SmokeRun run) {
        List<Script> scripts = new ArrayList<>();
        scripts.add(MultiplayerClient.join(PHASE));
        scripts.add(MultiplayerClient.configSync(PHASE));
        scripts.add(flight());
        for (String name : List.of("dislocation_blacklisted", "dislocation_inside", "dislocation_outside")) scripts.add(dislocation(name));
        for (String name : List.of("dislocation_place_inside", "dislocation_place_outside")) scripts.add(dislocationPlace(name));
        for (String name : List.of("mirror_inside", "mirror_boundary", "mirror_outside")) scripts.add(mirror(name));
        for (String name : List.of("pickaxe_inside", "pickaxe_outside")) scripts.add(pickaxe(name));
        scripts.add(enchanter(run));
        scripts.add(tablet(run));
        scripts.add(MultiplayerClient.disconnect(PHASE, link));
        return scripts;
    }

    /** What this client shows before and after a scenario. */
    private record View(String vis, int stone, int damage, String state) {}

    private static View view(Minecraft client, ServerLink.Reply stage) {
        int slot = Integer.parseInt(stage.get("slot"));
        var inventory = client.player.getInventory();
        int stone = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(Items.STONE)) stone += inventory.getItem(i).getCount();
        String state = stage.get("target").isEmpty() ? "" : MultiplayerClient.blockId(client, MultiplayerClient.pos(stage.get("target")));
        return new View(MultiplayerClient.vis(inventory.getItem(slot)), stone, inventory.getItem(SLOT_PICK).getDamageValue(), state);
    }

    /**
     * The common start of a scenario: the server stages it, the client waits until it stands there, takes the slot, sneaks if
     * the scenario wants it, and waits until its own cross-hair shows the block the server named (the rotation came with the
     * server's teleport; the client's ray is its own).
     */
    private Script staged(String name, ServerLink.Reply[] stage, View[] before) {
        int[] aimTicks = { 0 };
        return new Script(name)
                .run("connected", run -> MultiplayerClient.requireConnected(run, PHASE, name))
                .then("stage", link.ask("stage", name, "", stage), 600)
                .then("arrive", run -> MultiplayerClient.at(run.client(), stage[0]), 30)
                .run("take the slot", run -> Input.select(run.client(), Integer.parseInt(stage[0].get("slot"))))
                .ticks(10)
                .run("sneak", run -> sneaking = Boolean.parseBoolean(stage[0].get("crouch")))
                .ticks(10)
                .then("aim", run -> {
                    Minecraft client = run.client();
                    if (stage[0].get("target").isEmpty()) return true;
                    BlockPos target = MultiplayerClient.pos(stage[0].get("target"));
                    Direction face = stage[0].get("face").isEmpty() ? null : Direction.byName(stage[0].get("face"));
                    if (Input.aimsAt(client, target, face)) return true;
                    if (++aimTicks[0] > 100) {
                        throw new IllegalStateException("the cross-hair does not show " + target.toShortString() + " face " + face + " but "
                                + Input.describeAim(client) + " (player at " + client.player.position() + ", yaw " + client.player.getYRot()
                                + ", pitch " + client.player.getXRot() + ")");
                    }
                    return false;
                }, 30)
                .run("before", run -> before[0] = view(run.client(), stage[0]));
    }

    private Script.Step check(String name, java.util.function.Supplier<String> arg, ServerLink.Reply[] check) {
        return link.ask("check", name, arg, check);
    }

    // ---- a: the Focus of Uprising with flight off on the server ------------------------------------------------------

    private Script flight() {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        double[] fastest = { 0 };
        int[] watched = { 0 };
        return staged("flight", stage, before)
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
        // What the client's own copy of the wand showed while the server answered: a prediction of the payment, put back by the server.
        java.util.LinkedHashSet<String> shown = new java.util.LinkedHashSet<>();
        int[] watched = { 0 };
        return staged(name, stage, before)
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
        return staged(name, stage, before)
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
        return staged(name, stage, before)
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
        return staged(name, stage, before)
                .run("left click while sneaking", run -> Input.leftClick(run.client()))
                .ticks(40)
                .then("check", check(name, () -> "", check), 30)
                .run("stand up", run -> {
                    sneaking = false;
                    Input.sneak(run.client(), false);
                })
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

    // ---- d: a menu round trip with the Osmotic Enchanter ----------------------------------------------------------------

    private Script enchanter(SmokeRun run) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] slots = { -1, -1 };
        GuiButtonEnchantment[] button = { null };
        String[] chosen = { "" };
        return staged("enchanter", stage, before)
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
                .run("close the screen", r -> r.client().player.closeContainer())
                .ticks(10)
                .then("check", check("enchanter", () -> chosen[0], check), 30)
                .run("record", r -> {
                    boolean ok = check[0].passed() && check[0].get("enchants").contains(chosen[0]);
                    MultiplayerClient.record(r, PHASE, "enchanter", "enchanter", ok, "server " + check[0].status() + ": " + check[0].text()
                            + "; client: the screen opened from the server's menu packet, the sword went in by a container click, the button of "
                            + chosen[0] + " sent PacketEnchanterAddEnchant and the client's block entity showed the choice before the screen closed");
                });
    }

    // ---- e: the Animation Tablet, which the second player watches -------------------------------------------------------

    private Script tablet(SmokeRun run) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] slots = { -1, -1 };
        boolean[] clicked = { false };
        return staged("tablet", stage, before)
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
}
