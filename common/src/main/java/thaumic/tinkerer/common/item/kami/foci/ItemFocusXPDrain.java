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
package thaumic.tinkerer.common.item.kami.foci;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchPage;
import thaumcraft.api.wands.FocusUpgradeType;
import thaumcraft.api.wands.WandHelper;
import thaumic.tinkerer.common.item.kami.ItemKamiResource;
import thaumic.tinkerer.common.item.kami.KamiHooks;
import thaumic.tinkerer.common.item.kami.KamiResearch;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.lib.LibResearch;
import thaumic.tinkerer.common.registry.TTRegistry;
import thaumic.tinkerer.common.registry.ThaumicTinkererInfusionRecipe;
import thaumic.tinkerer.common.registry.ThaumicTinkererRecipe;
import thaumic.tinkerer.common.research.ResearchHelper;
import thaumic.tinkerer.common.research.TTResearchItem;

/**
 * Wand Focus: Experience Drain. Held, it turns the caster's experience into vis, every tick into the primal aspect the
 * wand holds least of: 5 vis (+20% per potency) for 15 experience points (-1 per frugal level).
 */
public class ItemFocusXPDrain extends ItemModKamiFocus {

    public ItemFocusXPDrain(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isVisCostPerTick(ItemStack focusstack) {
        return true;
    }

    @Override
    public String getSortingHelper(ItemStack focusstack) {
        return "TTKXP" + super.getSortingHelper(focusstack);
    }

    @Override
    protected boolean isKamiRarity() {
        return true;
    }

    @Override
    public void onUsingFocusTick(ItemStack wandstack, Player player, int count) {
        if (player.level().isClientSide) return;

        AspectList aspects = WandHelper.getAllVis(wandstack);
        Aspect lowestAspect = null;
        int lowestAmount = Integer.MAX_VALUE;
        int maxVis = WandHelper.getMaxVis(wandstack);

        for (Aspect aspect : Aspect.getPrimalAspects()) {
            int amount = aspects.getAmount(aspect);
            if (amount < maxVis && amount < lowestAmount) {
                lowestAmount = amount;
                lowestAspect = aspect;
            }
        }
        if (lowestAspect == null) return;

        int potency = WandHelper.getFocusPotency(wandstack);
        float multiplier = 1.0F + (0.2F * potency);
        int intendedAdd = Math.round(500 * multiplier);
        int actualAdd = Math.min(intendedAdd, maxVis - lowestAmount);

        int xpUse = 15 - WandHelper.getFocusFrugal(wandstack);
        int scaledXp = Math.max(1, Math.round(xpUse * (actualAdd / (float) intendedAdd)));
        if (getPlayerXP(player) >= scaledXp && player instanceof ServerPlayer server) {
            drainPlayerXP(server, scaledXp);
            WandHelper.addRealVis(wandstack, lowestAspect, actualAdd, true);
        }
    }

    /** A colour that runs through the rainbow on the client (1.7.10 {@code Color.HSBtoRGB(ticksExisted * 2 % 360 / 360F, 1, 1)}); white where there is no client player. */
    @Override
    public int getFocusColor(ItemStack focusstack) {
        Player player = KamiHooks.localPlayer();
        return player == null ? 0xFFFFFF : Mth.hsvToRgb(player.tickCount * 2 % 360 / 360F, 1F, 1F);
    }

    int getXpUse(ItemStack focusstack) {
        try {
            return 15 - getUpgradeLevel(focusstack, FocusUpgradeType.frugal);
        } catch (RuntimeException notAFocusStack) {
            // getUpgradeLevel refuses a stack that is not a supported focus (docs/API_GAPS.md 3b); show the base cost.
            return 15;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.Focus.cost2"));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("ttmisc.experience").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" x " + getXpUse(stack)).withStyle(ChatFormatting.WHITE)));
    }

    @Override
    public AspectList getVisCost(ItemStack focusstack) {
        return new AspectList();
    }

    @Override
    public FocusUpgradeType[] getPossibleUpgradesByRank(ItemStack focusstack, int rank) {
        return rank <= 5 ? new FocusUpgradeType[] { FocusUpgradeType.frugal, FocusUpgradeType.potency } : null;
    }

    // ---- experience (1.7.10 ExperienceHelper, with the 1.21.1 experience curve) -------------------------------

    /**
     * The upper bound of the level search: far above level 21,863, the last level whose points (2,147,407,943) fit in an {@code int};
     * level 21,864 needs more.
     */
    private static final int LEVEL_SEARCH_LIMIT = 1 << 16;

    /**
     * The points of a level: the total needed to reach it from level 0 (1.21.1 curve: 2L+7, then 5L-38, then 9L-158 per level); a
     * level whose points do not fit in an {@code int} gives {@link Integer#MAX_VALUE}.
     */
    public static int getExperienceForLevel(int level) {
        return (int) Math.min(Integer.MAX_VALUE, pointsForLevel(level));
    }

    /** The highest level whose points do not exceed the amount (a binary search: it ends for every int, the 1.7.10 loop did not). */
    public static int getLevelForExperience(int experience) {
        int low = 0;
        int high = LEVEL_SEARCH_LIMIT;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (pointsForLevel(middle) <= experience) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    /** The exact points from level 0 to the level, in a {@code long} so that no level overflows. */
    private static long pointsForLevel(long level) {
        if (level <= 0) return 0;
        level = Math.min(level, LEVEL_SEARCH_LIMIT); // far past the int range already; keeps the squares below a long
        if (level <= 16) return level * level + 6 * level;
        // 2.5 L^2 - 40.5 L + 360 and 4.5 L^2 - 162.5 L + 2220, written in whole numbers (the products are even)
        if (level <= 31) return (5 * level * level - 81 * level) / 2 + 360;
        return (9 * level * level - 325 * level) / 2 + 2220;
    }

    /** The points of the player: those of the whole levels plus the filled part of the bar (saturated at the int range). */
    public static int getPlayerXP(Player player) {
        long points = pointsForLevel(player.experienceLevel) + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
        return (int) Math.min(Integer.MAX_VALUE, points);
    }

    /**
     * 1.7.10 {@code ExperienceHelper.drainPlayerXP}: the player is left with its points less {@code amount} (never below 0), as a level and
     * a filled part of the bar, set through the server player's own setters ({@code setExperienceLevels}, {@code setExperiencePoints}:
     * what {@code /xp set} uses; they send the bar to the client). The total, which those leave alone, becomes what is left, as 1.7.10
     * set {@code experienceTotal}. ({@code giveExperiencePoints(-amount)} would also lower the score of the death screen, which the
     * original did not.)
     */
    public static void drainPlayerXP(ServerPlayer player, int amount) {
        int experience = (int) Math.max(0L, (long) getPlayerXP(player) - amount);
        int level = getLevelForExperience(experience);
        player.setExperienceLevels(level);
        player.setExperiencePoints(experience - getExperienceForLevel(level));
        player.totalExperience = experience;
    }

    @Override
    public ThaumicTinkererRecipe getRecipeItem() {
        return new ThaumicTinkererInfusionRecipe(LibResearch.KEY_FOCUS_XP_DRAIN, new ItemStack(this), 12,
                new AspectList().add(Aspect.MIND, 65).add(Aspect.TAINT, 16).add(Aspect.MAGIC, 50).add(Aspect.AURA, 32),
                new ItemStack(Items.ENDER_PEARL), ItemKamiResource.stack(ItemKamiResource.ICHOR),
                new ItemStack(Items.EXPERIENCE_BOTTLE), new ItemStack(Items.DIAMOND),
                TTRegistry.stack(LibItemNames.XP_TALISMAN), new ItemStack(Items.ENCHANTING_TABLE),
                ItemKamiResource.stack(ItemKamiResource.ICHOR));
    }

    public static TTResearchItem research() {
        return KamiResearch.entry(LibResearch.KEY_FOCUS_XP_DRAIN,
                new AspectList().add(Aspect.MIND, 2).add(Aspect.MAGIC, 1).add(Aspect.AURA, 1).add(Aspect.MAN, 1), 12, 3,
                TTRegistry.stack(LibItemNames.FOCUS_XP_DRAIN), 0, KamiResearch.of(LibResearch.KEY_ICHORCLOTH_ROD),
                KamiResearch.NONE, new ResearchPage("0"), ResearchHelper.infusionPage(LibResearch.KEY_FOCUS_XP_DRAIN));
    }
}
