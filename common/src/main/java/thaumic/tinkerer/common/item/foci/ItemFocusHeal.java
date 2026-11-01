/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.item.foci;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.ThaumcraftSoundIds;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchPage;
import thaumcraft.api.wands.FocusUpgradeType;
import thaumcraft.api.wands.WandHelper;
import thaumic.tinkerer.common.hooks.PlayerData;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.common.lib.LibResearch;
import thaumic.tinkerer.common.registry.ThaumicTinkererInfusionRecipe;
import thaumic.tinkerer.common.registry.ThaumicTinkererRecipe;
import thaumic.tinkerer.common.research.IRegisterableResearch;
import thaumic.tinkerer.common.research.ResearchHelper;
import thaumic.tinkerer.common.research.TTResearchItem;

/**
 * Wand Focus: Mending. Held, it heals the player while he is hurt: every 30 ticks (fewer with potency) it takes 0.45 Terra
 * and 0.45 Aqua and heals one health point plus one per level of potency.
 *
 * <p>The progress of each player is kept per side, as the dedicated server and the client of 1.7.10 kept it: the client
 * counts for its own sparkles and the server for the healing. (1.7.10 kept one static map by the name of the player, which
 * the integrated server and client of a single-player game shared.) It is the player data {@link #PROGRESS} ({@code PlayerData}):
 * the client's {@code LocalPlayer} and the server's {@code ServerPlayer} each carry their own, nothing is saved or synced, a
 * respawn after death keeps it (1.7.10's map by name did) and a logout ends it with the entity (1.7.10 removed the name).</p>
 */
public class ItemFocusHeal extends ItemModFocus {
    /** The cost of a cast (or of a tick), in centivis. */
    public static final AspectList VIS_USAGE = new AspectList().add(Aspect.EARTH, 45).add(Aspect.WATER, 45);

    /** The ticks the player has been healing (1.7.10 {@code playerHealData}), on each side's player entity. */
    public static final PlayerData.Key<Integer> PROGRESS = PlayerData.Key.<Integer>builder(LibMisc.id("focus_heal_progress"), () -> 0)
            .copyOnDeath().build();

    /** The sparkles around the healed player are the violet ones of the API ({@code sparkle(x, y, z, 0)}). */
    private static final int SPARKLE_TYPE = FociFx.SPARKLE_VIOLET;

    public ItemFocusHeal(Item.Properties properties) {
        super(properties);
    }

    /** The ticks of healing that make one heal: {@code 30 - potency * 10 / 3} (integer division, as in 1.7.10). */
    public static int requiredProgress(int potency) {
        return 30 - potency * 10 / 3;
    }

    /** The health points one heal gives: {@code 1 + potency}. */
    public static float healAmount(int potency) {
        return 1 + potency;
    }

    /** The ticks of healing of this player entity so far (zero for none): the server's or the client's, by the entity. */
    public static int progressOf(Player player) {
        return PlayerData.get(player, PROGRESS);
    }

    @Override
    public void onUsingFocusTick(ItemStack wandstack, Player player, int count) {
        if (!WandHelper.consumeAllVis(wandstack, player, VIS_USAGE, false, false) || !player.isHurt()) return;

        int potency = WandHelper.getFocusPotency(wandstack);

        Level level = player.level();
        int progress = progressOf(player) + 1;
        PlayerData.set(player, PROGRESS, progress);

        FociFx.sparkleAround(level, player, SPARKLE_TYPE);

        if (progress >= requiredProgress(potency)) {
            PlayerData.set(player, PROGRESS, 0);

            WandHelper.consumeAllVis(wandstack, player, VIS_USAGE, true, false);
            // 1.7.10 healed on both sides; the health of the player is the server's, the client's copy follows it.
            if (!level.isClientSide) player.heal(healAmount(potency));
            FociFx.thaumcraftSound(level, player, ThaumcraftSoundIds.WAND, 0.5F, 1F);
        }
    }

    @Override
    public void onPlayerStoppedUsingFocus(ItemStack wandstack, Level level, Player player, int count) {
        PlayerData.set(player, PROGRESS, 0);
    }

    @Override
    protected boolean hasDepth() {
        return true;
    }

    @Override
    public boolean isVisCostPerTick(ItemStack focusstack) {
        return false;
    }

    @Override
    public boolean isUseItem(ItemStack focusstack) {
        return true;
    }

    @Override
    public String getSortingHelper(ItemStack focusstack) {
        return "TTHE" + super.getSortingHelper(focusstack);
    }

    @Override
    public int getFocusColor(ItemStack focusstack) {
        return 0xFD00D6;
    }

    @Override
    public AspectList getVisCost(ItemStack focusstack) {
        return VIS_USAGE.copy();
    }

    @Override
    public FocusUpgradeType[] getPossibleUpgradesByRank(ItemStack focusstack, int rank) {
        return rank >= 1 && rank <= 5 ? new FocusUpgradeType[] { FocusUpgradeType.frugal, FocusUpgradeType.potency } : null;
    }

    @Override
    public IRegisterableResearch getResearchItem() {
        if (!ThaumcraftApi.isMirrorsAllowed()) {
            return null;
        }
        TTResearchItem research = new TTResearchItem(LibResearch.KEY_FOCUS_HEAL,
                new AspectList().add(Aspect.HEAL, 2).add(Aspect.SOUL, 1).add(Aspect.MAGIC, 1), -6, -4, 2,
                new ItemStack(this));
        research.setParents(LibResearch.KEY_FOCUS_DEFLECT).setConcealed().setPages(new ResearchPage("0"),
                ResearchHelper.infusionPage(LibResearch.KEY_FOCUS_HEAL)).setSecondary();
        return research;
    }

    @Override
    public ThaumicTinkererRecipe getRecipeItem() {
        return new ThaumicTinkererInfusionRecipe(LibResearch.KEY_FOCUS_HEAL, new ItemStack(this), 4,
                new AspectList().add(Aspect.HEAL, 10).add(Aspect.SOUL, 10).add(Aspect.LIFE, 15),
                tc("itemFocusPech", 0), new ItemStack(Items.GOLDEN_CARROT), new ItemStack(Items.GOLD_NUGGET),
                new ItemStack(Items.GOLD_NUGGET), new ItemStack(Items.GOLD_NUGGET));
    }
}
