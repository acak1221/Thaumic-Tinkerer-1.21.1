package thaumic.tinkerer.clientsmoke;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.block.tile.TileRPlacer;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;
import thaumic.tinkerer.common.item.foci.FociComponents.DislocationData;
import thaumic.tinkerer.common.item.foci.ItemFocusDislocation;

/**
 * What the two acting clients of the multiplayer check share (the lead {@link MultiplayerLeadPhase} and the second player
 * {@link MultiplayerPeerPhase}): the start of a scenario ({@code /ttmp stage}, wait until standing there, take the slot, wait until
 * the slot shows the item the server put there, sneak if asked, wait until the client's own cross-hair shows the block or entity the
 * server named), the keys held down over several ticks (sneak, use, attack), and the comparison of the server's numbers with what
 * this client's own world shows. Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on
 * (see NOTICE.md), not distributed.
 */
abstract class MultiplayerActor extends ScriptedPhase {
    protected final ServerLink link;
    private final String phase;
    /** Held down every tick while true (a screen that opens releases every key; the next tick presses them again). */
    protected boolean sneaking;
    protected boolean using;
    protected boolean attacking;

    MultiplayerActor(ServerLink link, String phase) {
        this.link = link;
        this.phase = phase;
    }

    @Override
    public String name() {
        return phase;
    }

    @Override
    public boolean tick(SmokeRun run) throws Exception {
        Minecraft client = run.client();
        if (client != null) {
            if (sneaking) Input.sneak(client, true);
            if (using) client.options.keyUse.setDown(true);
            if (attacking) client.options.keyAttack.setDown(true);
        }
        return super.tick(run);
    }

    /** Lets go of every held key. */
    protected void release(Minecraft client) {
        if (sneaking) Input.sneak(client, false);
        if (using) client.options.keyUse.setDown(false);
        if (attacking) client.options.keyAttack.setDown(false);
        sneaking = false;
        using = false;
        attacking = false;
    }

    /** What this client shows before and after a scenario. */
    record View(String vis, int stone, int damage, String state) {}

    static final int SLOT_PICK = 2;

    static View view(Minecraft client, ServerLink.Reply stage) {
        int slot = Integer.parseInt(stage.get("slot"));
        var inventory = client.player.getInventory();
        String state = stage.get("target").isEmpty() ? "" : MultiplayerClient.blockId(client, MultiplayerClient.pos(stage.get("target")));
        return new View(MultiplayerClient.vis(inventory.getItem(slot)), stone(client), inventory.getItem(SLOT_PICK).getDamageValue(), state);
    }

    static int stone(Minecraft client) {
        var inventory = client.player.getInventory();
        int stone = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(Items.STONE)) stone += inventory.getItem(i).getCount();
        return stone;
    }

    /**
     * The common start of a scenario: the server stages it, the client waits until it stands there, takes the slot, waits until the
     * slot shows what the server put into it, sneaks if the scenario wants it, and waits until its own cross-hair shows the block
     * (or the entity) the server named (the rotation came with the server's teleport; the client's ray is its own).
     */
    protected Script staged(String name, ServerLink.Reply[] stage, View[] before, int[] mark) {
        int[] aimTicks = { 0 };
        return new Script(name)
                .run("connected", run -> MultiplayerClient.requireConnected(run, phase, name))
                .run("mark the messages", run -> mark[0] = link.mark())
                .then("stage", link.ask("stage", name, "", stage), 600)
                .then("arrive", run -> MultiplayerClient.at(run.client(), stage[0]), 30)
                .run("take the slot", run -> Input.select(run.client(), Integer.parseInt(stage[0].get("slot"))))
                .ticks(10)
                .until("the slot shows the item the server put there", () -> {
                    Minecraft client = Minecraft.getInstance();
                    String holding = stage[0].get("holding");
                    if (holding.isEmpty()) return true;
                    ItemStack held = client.player.getInventory().getItem(Integer.parseInt(stage[0].get("slot")));
                    return MultiplayerClient.itemId(held).equals(holding) && client.player.getInventory().selected == Integer.parseInt(stage[0].get("slot"));
                }, 20)
                .run("sneak", run -> sneaking = Boolean.parseBoolean(stage[0].get("crouch")))
                .ticks(10)
                .then("aim", run -> {
                    Minecraft client = run.client();
                    if (Boolean.parseBoolean(stage[0].get("entity"))) {
                        if (client.hitResult instanceof EntityHitResult) return true;
                    } else {
                        if (stage[0].get("target").isEmpty()) return true;
                        BlockPos target = MultiplayerClient.pos(stage[0].get("target"));
                        Direction face = stage[0].get("face").isEmpty() ? null : Direction.byName(stage[0].get("face"));
                        if (Input.aimsAt(client, target, face)) return true;
                    }
                    if (++aimTicks[0] > 100) {
                        throw new IllegalStateException("the cross-hair does not show " + (Boolean.parseBoolean(stage[0].get("entity")) ? "an entity"
                                : stage[0].get("target") + " face " + stage[0].get("face")) + " but " + Input.describeAim(client) + " (player at "
                                + client.player.position() + ", yaw " + client.player.getYRot() + ", pitch " + client.player.getXRot() + ")");
                    }
                    return false;
                }, 30)
                .run("before", run -> before[0] = view(run.client(), stage[0]));
    }

    protected Script.Step check(String name, Supplier<String> arg, ServerLink.Reply[] check) {
        return link.ask("check", name, arg, check);
    }

    // ---- the comparison of the server's numbers with this client's world ----------------------------------------------

    /**
     * Compares what the server reported with this client's own copy, for each value the reply has: {@code blocks}
     * ({@code x,y,z:id;...}: the block ids in this client's world), {@code vis} (the wand in the stage's slot), {@code stone} (the
     * stone in the inventory), {@code carried} (what the Focus of Dislocation in the stage's slot carries), {@code slot} with
     * {@code device} (a Remote Placer's slot), {@code tool_damage} with {@code device} (an Animation Tablet's tool). Returns the
     * differences; empty when the client agrees.
     */
    static List<String> differences(Minecraft client, ServerLink.Reply stage, ServerLink.Reply check) {
        List<String> differ = new ArrayList<>();
        String blocks = check.get("blocks");
        if (!blocks.isEmpty()) {
            for (String entry : blocks.split(";")) {
                int colon = entry.indexOf(':');
                BlockPos pos = MultiplayerClient.pos(entry.substring(0, colon));
                String server = entry.substring(colon + 1);
                String mine = MultiplayerClient.blockId(client, pos);
                if (!mine.equals(server)) differ.add(pos.toShortString() + " server " + server + ", client " + mine);
            }
        }
        int slot = Integer.parseInt(stage.get("slot"));
        ItemStack held = client.player.getInventory().getItem(slot);
        if (!check.get("vis").isEmpty() && !MultiplayerClient.vis(held).equals(check.get("vis"))) {
            differ.add("vis server " + check.get("vis") + ", client " + MultiplayerClient.vis(held));
        }
        if (!check.get("stone").isEmpty() && !String.valueOf(stone(client)).equals(check.get("stone"))) {
            differ.add("stone server " + check.get("stone") + ", client " + stone(client));
        }
        if (!check.get("carried").isEmpty()) {
            DislocationData carried = ItemFocusDislocation.getData(held);
            String mine = carried == null ? "nothing" : BuiltInRegistries.BLOCK.getKey(carried.state().getBlock()).toString();
            if (!mine.equals(check.get("carried"))) differ.add("focus carries server " + check.get("carried") + ", client " + mine);
        }
        if (!check.get("device").isEmpty()) {
            BlockPos device = MultiplayerClient.pos(check.get("device"));
            if (!check.get("slot").isEmpty()) {
                String mine = client.level.getBlockEntity(device) instanceof TileRPlacer placer ? stack(placer.getItem(0)) : "none";
                if (!mine.equals(check.get("slot"))) differ.add("placer slot server " + check.get("slot") + ", client " + mine);
            }
            if (!check.get("tool_damage").isEmpty()) {
                String mine = client.level.getBlockEntity(device) instanceof TileAnimationTablet tablet ? String.valueOf(tablet.getItem(0).getDamageValue()) : "none";
                if (!mine.equals(check.get("tool_damage"))) differ.add("tablet tool damage server " + check.get("tool_damage") + ", client " + mine);
            }
        }
        return differ;
    }

    static String stack(ItemStack stack) {
        return stack.isEmpty() ? "empty" : MultiplayerClient.itemId(stack) + "x" + stack.getCount();
    }

    /** One line with both sides: the server's verdict and whether this client agrees. */
    protected void recordBoth(SmokeRun run, String name, String target, ServerLink.Reply stage, ServerLink.Reply check, int mark,
            boolean extraOk, String extra) {
        List<String> differ = differences(run.client(), stage, check);
        List<String> messages = link.since(mark);
        boolean ok = check.passed() && differ.isEmpty() && extraOk;
        MultiplayerClient.record(run, phase, name, target, ok, "server " + check.status() + ": " + check.text() + "; client: "
                + (differ.isEmpty() ? "its world, inventory and devices agree with the server's numbers" : "differs: " + differ)
                + (extra.isEmpty() ? "" : "; " + extra) + (messages.isEmpty() ? "" : "; messages from the server meanwhile: " + messages));
    }

    // ---- the generic scenarios ------------------------------------------------------------------------------------------

    /** How a scenario acts once the client aims at its target. */
    enum Act { RIGHT_CLICK, LEFT_CLICK, HOLD_ATTACK }

    /**
     * A scenario that acts once ({@code act}), waits {@code ticks} and asks the server's check; then compares the server's numbers
     * with this client's world ({@link #differences}). {@link Act#HOLD_ATTACK} holds the attack key until the block in the
     * cross-hair is gone in this client's world, or 80 ticks.
     */
    protected Script act(String name, String target, Act act, int ticks) {
        return act(name, target, act, ticks, null, "", "");
    }

    /** {@link #act(String, String, Act, int)} with a picture after the check ({@code run} is null for none). */
    protected Script act(String name, String target, Act act, int ticks, @Nullable SmokeRun run, String picture, String shows) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] check = { null };
        View[] before = { null };
        int[] mark = { 0 };
        int[] held = { 0 };
        Script script = staged(name, stage, before, mark);
        switch (act) {
            case RIGHT_CLICK -> script.run("right click", r -> Input.rightClick(r.client()));
            case LEFT_CLICK -> script.run("left click", r -> Input.leftClick(r.client()));
            case HOLD_ATTACK -> script.run("press the attack key", r -> {
                        Input.leftClick(r.client());
                        attacking = true;
                    })
                    .then("hold it until the block is gone", r -> {
                        Minecraft client = r.client();
                        BlockPos pos = MultiplayerClient.pos(stage[0].get("target"));
                        boolean gone = !MultiplayerClient.blockId(client, pos).equals(before[0].state());
                        if (!gone) holdAttack(client, pos, Direction.byName(stage[0].get("face")));
                        return gone || ++held[0] >= 80;
                    }, 30)
                    .run("let go", r -> {
                        attacking = false;
                        r.client().options.keyAttack.setDown(false);
                    });
        }
        script.ticks(ticks)
                .then("check", check(name, () -> "", check), 30)
                .run("stand up", r -> release(r.client()))
                .ticks(10);
        if (run != null) script.frames(6).then("picture", run.shots().shot(picture, MultiplayerClient.GROUP, shows));
        return script.run("record", r -> recordBoth(r, name, target, stage[0], check[0], mark[0], true,
                act == Act.HOLD_ATTACK ? "held the attack key " + held[0] + " ticks" : ""));
    }

    /**
     * One tick of the attack key held on a block. The game's own tick does this ({@code Minecraft.continueAttack}: the mining of the
     * block in the cross-hair goes on, the arm swings) only while its window holds the mouse ({@code keyAttack.isDown() &&
     * mouseHandler.isMouseGrabbed()}); two clients share one X display here and only the focused window holds it, so for a window
     * without it the harness makes the same two calls. A single click ({@code startAttack}) needs no grab.
     */
    static void holdAttack(Minecraft client, BlockPos pos, Direction face) {
        if (client.mouseHandler.isMouseGrabbed() || client.player.isUsingItem() || !(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                || !hit.getBlockPos().equals(pos)) {
            return;
        }
        if (client.gameMode.continueDestroyBlock(pos, face)) client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** The click on a stone button that pulses a device (Remote Placer, Transvector Dislocator, Animation Tablet). */
    protected Script press(String name, String target, int ticks) {
        return act(name, target, Act.RIGHT_CLICK, ticks);
    }

    protected Script press(String name, String target, int ticks, SmokeRun run, String picture, String shows) {
        return act(name, target, Act.RIGHT_CLICK, ticks, run, picture, shows);
    }

    @Nullable
    static Entity aimedEntity(Minecraft client) {
        return client.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
    }
}
