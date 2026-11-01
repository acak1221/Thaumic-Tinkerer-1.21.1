package thaumic.tinkerer.clientsmoke;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;

/**
 * The second player of the multiplayer check ({@code TTBravo}): it gets the config sync too, then stands next to an Animation
 * Tablet, tells the server that its own client has the tablet (empty, left-click mode, as a new tablet is), and waits until its
 * client's block entity shows what the lead changed through the menu (an iron pickaxe in it, right-click mode). That only happens
 * when the server's block-entity update reaches a client that did not do the change. Then it is the stranger at the lead's claim
 * of Open Parties and Claims (it is in no party with the lead): it uses the add-on's tools and devices on blocks inside the claim,
 * across its edge and outside it (docs/MULTIPLAYER_VERIFICATION.md, section h). Test-only code, new in the 1.21.1 port (no 1.7.10
 * counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), not distributed.
 */
final class MultiplayerPeerPhase extends MultiplayerActor {
    private static final String PHASE = "peer";

    MultiplayerPeerPhase(ServerLink link) {
        super(link, PHASE);
    }

    @Override
    protected List<Script> scripts(SmokeRun run) {
        List<Script> scripts = new ArrayList<>();
        scripts.add(MultiplayerClient.join(PHASE));
        scripts.add(MultiplayerClient.configSync(PHASE));
        scripts.add(tablet(run));
        // h. The claim: the first stage waits until the lead has claimed.
        scripts.add(act("b_dislocation_out", "focus_dislocation", Act.RIGHT_CLICK, 40));
        scripts.add(act("b_dislocation_in", "focus_dislocation", Act.RIGHT_CLICK, 40));
        scripts.add(act("b_dislocation_place_out", "focus_dislocation", Act.RIGHT_CLICK, 40));
        scripts.add(act("b_mirror_in", "placement_mirror", Act.RIGHT_CLICK, 40));
        scripts.add(act("b_mirror_edge", "placement_mirror", Act.RIGHT_CLICK, 40, run, "peer-claim-mirror-edge",
                "The second player's Placement Mirror on a wall just outside the lead's claim (the claim begins two columns to the right):"
                        + " the three columns of stone outside it are placed, the two inside are not"));
        scripts.add(act("b_mirror_out", "placement_mirror", Act.RIGHT_CLICK, 40));
        scripts.add(act("b_pickaxe_in", "ichor_pick_gem", Act.LEFT_CLICK, 40));
        scripts.add(act("b_pickaxe_out", "ichor_pick_gem", Act.LEFT_CLICK, 40));
        scripts.add(act("b_axe_in", "ichor_axe_gem", Act.HOLD_ATTACK, 20));
        scripts.add(act("b_axe_edge", "ichor_axe_gem", Act.HOLD_ATTACK, 20, run, "peer-claim-tree-edge",
                "The second player after felling a tree in tree mode whose top reaches into the lead's claim: the clicked log outside is gone,"
                        + " the trunk above it and the branch inside the claim still stand"));
        scripts.add(act("b_axe_out", "ichor_axe_gem", Act.HOLD_ATTACK, 20));
        scripts.add(press("b_placer_into_claim", "remote_placer", 40));
        scripts.add(press("b_placer_out", "remote_placer", 30));
        scripts.add(press("b_dislocator_into_claim", "dislocator", 40));
        scripts.add(press("b_dislocator_out", "dislocator", 30));
        scripts.add(MultiplayerClient.disconnect(PHASE, link));
        return scripts;
    }

    @Nullable
    private static TileAnimationTablet tablet(Minecraft client, BlockPos pos) {
        return client.level != null && client.level.getBlockEntity(pos) instanceof TileAnimationTablet tablet ? tablet : null;
    }

    private static String shows(@Nullable TileAnimationTablet tablet) {
        return tablet == null ? "no tablet" : "item=" + MultiplayerClient.itemId(tablet.getItem(0)) + " leftClick=" + tablet.leftClick;
    }

    private Script tablet(SmokeRun run) {
        ServerLink.Reply[] stage = { null };
        ServerLink.Reply[] ready = { null };
        ServerLink.Reply[] saw = { null };
        BlockPos[] pos = { null };
        String[] initially = { "" };
        long[] waited = { 0 };
        return new Script("tablet_sync")
                .run("connected", r -> MultiplayerClient.requireConnected(r, PHASE, "tablet_sync"))
                .then("stage", link.ask("stage", "peer", "", stage), 30)
                .then("arrive", r -> MultiplayerClient.at(r.client(), stage[0]), 30)
                .run("the tablet", r -> pos[0] = MultiplayerClient.pos(stage[0].get("target")))
                .until("the tablet in this client's world", () -> tablet(Minecraft.getInstance(), pos[0]) != null, 60)
                .run("what it shows before", r -> {
                    TileAnimationTablet tablet = tablet(r.client(), pos[0]);
                    initially[0] = shows(tablet);
                    r.report().info(PHASE, "tablet_before_the_change", "this client's tablet at " + pos[0].toShortString() + ": " + initially[0]);
                    // a new tablet: empty, left-click mode
                    if (!tablet.getItem(0).isEmpty() || !tablet.leftClick) {
                        r.report().fail(PHASE, "tablet_sync", MultiplayerClient.GROUP, "animation_tablet", "the tablet is not empty before the lead changed it: " + initially[0]);
                        throw new Script.Abort();
                    }
                })
                .then("tell the server", link.ask("ready", "peer", "", ready), 30)
                .run("start the clock", r -> waited[0] = System.nanoTime())
                .then("wait for the lead's change", r -> {
                    TileAnimationTablet tablet = tablet(r.client(), pos[0]);
                    return tablet != null && tablet.getItem(0).is(Items.IRON_PICKAXE) && !tablet.leftClick;
                }, 900)
                .run("seen", r -> waited[0] = (System.nanoTime() - waited[0]) / 1_000_000L)
                .ticks(20)
                .frames(6)
                .then("picture", run.shots().shot("peer-tablet", MultiplayerClient.GROUP,
                        "The second player's view of the Animation Tablet after the lead put an iron pickaxe into it through the menu: the tablet's renderer draws the item this client got from the server"))
                .then("say what this client shows", link.ask("saw", "tablet", () -> shows(tablet(run.client(), pos[0])), saw), 30)
                .run("record", r -> {
                    String now = shows(tablet(r.client(), pos[0]));
                    MultiplayerClient.record(r, PHASE, "tablet_sync", "animation_tablet", saw[0].passed(), "this client's tablet: " + initially[0]
                            + " -> " + now + " (seen " + waited[0] + " ms after it told the server it was ready); server: " + saw[0].status()
                            + " " + saw[0].text() + " " + saw[0].values());
                });
    }
}
