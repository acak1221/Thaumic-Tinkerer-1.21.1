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
package thaumic.tinkerer.common.core.handler.kami;

import com.mojang.serialization.Codec;
import dev.architectury.event.events.common.PlayerEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.hooks.PlayerData;
import thaumic.tinkerer.common.hooks.TinkererEvents;
import thaumic.tinkerer.common.item.kami.armor.IchorAbsorption;
import thaumic.tinkerer.common.item.kami.armor.ItemGemBoots;
import thaumic.tinkerer.common.item.kami.armor.ItemGemChest;
import thaumic.tinkerer.common.item.kami.armor.ItemGemHelm;
import thaumic.tinkerer.common.item.kami.armor.ItemGemLegs;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * The master switch of the awakened armour (1.7.10: the {@code GemArmor} flag in the player's entity data, copied to the
 * client by {@code PlayerTracker} and set from the client by the toggle key) and the per-tick dispatch of the awakened
 * pieces.
 *
 * <p>The switch is the player data attachment {@link #ARMOR_ON} ({@link PlayerData}): saved with the player, kept through death
 * (as the earlier port's world file kept it; in 1.7.10 the client's copy survived a death, the dedicated server's did not), and
 * synced to the player's own client, which needs it every tick for the effects that run there (the boots' speed). The toggle
 * key sets the local player's copy at once and tells the server ({@code PacketToggleArmor}), whose change comes back as the
 * sync. Worlds of the earlier port kept the players that switched it off in the world's {@code thaumictinkerer_kami_armor.dat}
 * ({@link StatusData}); a player's entry there is moved into the attachment the first time the player joins ({@link #migrate}),
 * and the file is not written any more.</p>
 */
public final class KamiArmorHandler {
    /** On (the default: no attachment) or off: saved, copied on death, synced to the owner. */
    public static final PlayerData.Key<Boolean> ARMOR_ON = PlayerData.Key.<Boolean>builder(LibMisc.id("kami_armor_on"), () -> true)
            .persistent(Codec.BOOL).copyOnDeath().syncedToOwner(ByteBufCodecs.BOOL).build();

    /** The name of the old save file in the overworld's data folder, and of its migration ({@link PlayerData#migrateOnce}). */
    public static final String LEGACY_DATA_NAME = LibMisc.MOD_ID + "_kami_armor";

    private KamiArmorHandler() {}

    /**
     * The player data of the awakened armour, the switch and the Robes' flight grant (from the registrar, whatever
     * {@code kami.forceenabled} says: an unregistered attachment is dropped when the player loads, PlayerData's class comment).
     */
    public static void registerPlayerData() {
        PlayerData.register(ARMOR_ON, ItemGemChest.FLIGHT_GRANTED);
    }

    /** Whether the awakened armour of this player is on: the server's switch, or on a client its own player's synced copy. */
    public static boolean getArmorStatus(Player player) {
        return PlayerData.get(player, ARMOR_ON);
    }

    /**
     * Sets the switch of a player: on the server the switch itself (sent to its client), on a client the local player's copy until
     * the server's sync arrives (the key's prediction).
     */
    public static void setArmorStatus(Player player, boolean status) {
        if (status == getArmorStatus(player)) return;
        if (status) PlayerData.remove(player, ARMOR_ON);
        else PlayerData.set(player, ARMOR_ON, false);
    }

    /** True when the player wears at least one awakened piece. */
    public static boolean wearsAwakenedArmor(Player player) {
        return isAwakened(player.getItemBySlot(EquipmentSlot.HEAD)) || isAwakened(player.getItemBySlot(EquipmentSlot.CHEST))
                || isAwakened(player.getItemBySlot(EquipmentSlot.LEGS)) || isAwakened(player.getItemBySlot(EquipmentSlot.FEET));
    }

    private static boolean isAwakened(ItemStack stack) {
        return stack.getItem() instanceof thaumic.tinkerer.common.item.kami.armor.ItemIchorclothArmorAdv;
    }

    /**
     * Registers the events of the armour: the tick of the players, the migration of the old save file on join, and the loader
     * events that stand in for 1.7.10's Forge events ({@code docs/HOOKS.md}): the Gem Boots' jump and fall
     * ({@code LivingJumpEvent}, {@code LivingFallEvent}) and the Ichorcloth absorption ({@code ISpecialArmor}).
     */
    public static void register() {
        dev.architectury.event.events.common.TickEvent.PLAYER_POST.register(KamiArmorHandler::onPlayerTick);
        PlayerEvent.PLAYER_JOIN.register(player -> PlayerData.migrateOnce(player, LEGACY_DATA_NAME,
                joined -> migrate(joined, legacyData(joined.server))));
        // The listeners of one event run in the order of registration. As in 1.7.10 (the boots in preInit, Ascent Boost in init) the
        // boots' jump comes first: the Enchantments group registers Ascent Boost's in its ready phase (ModEnchantments.initJump).
        TinkererEvents.LIVING_JUMP.register(ItemGemBoots::onLivingJump);
        TinkererEvents.LIVING_FALL.register(ItemGemBoots::onLivingFall);
        TinkererEvents.LIVING_ARMOR_ABSORB.register(IchorAbsorption::onLivingArmorAbsorb);
    }

    /** What 1.7.10 ran from {@code LivingUpdateEvent} in each awakened item, in the order helmet, chestplate, leggings, boots. */
    public static void onPlayerTick(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof ItemGemHelm) ItemGemHelm.tickPlayer(player, head);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof ItemGemChest) ItemGemChest.tickPlayer(player, chest);
        ItemGemChest.updatePlayerFlyStatus(player);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (legs.getItem() instanceof ItemGemLegs) ItemGemLegs.tickPlayer(player, legs);
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
        ItemGemBoots.tick(player, feet);
    }

    // ---- the save file of the earlier port (read once per player, never written) -------------------------------------------

    /** How the old save file is read (no data fixer: it never had one). */
    public static final SavedData.Factory<StatusData> LEGACY_FACTORY = new SavedData.Factory<>(StatusData::new, StatusData::load, null);

    /** The old save file of the overworld, or null when the world has none (it is read from disk once and kept by the data storage). */
    @Nullable
    public static StatusData legacyData(MinecraftServer server) {
        return server.overworld().getDataStorage().get(LEGACY_FACTORY, LEGACY_DATA_NAME);
    }

    /**
     * A player the old save file lists as switched off is switched off in the attachment. The entry is left in the file, which is
     * never written again; {@link PlayerData#migrateOnce} makes sure it is read once per player.
     */
    public static void migrate(ServerPlayer player, @Nullable StatusData legacy) {
        if (legacy != null && legacy.disabled(player.getUUID())) setArmorStatus(player, false);
    }

    /**
     * The players that switched their awakened armour off, as the earlier port saved them with the world
     * ({@code data/thaumictinkerer_kami_armor.dat} of the overworld: {@code {disabled: [<UUID>, ...]}}). Read only: nothing sets it
     * dirty, so the game never writes it again.
     */
    public static final class StatusData extends SavedData {
        private final Set<UUID> disabled = new HashSet<>();

        public static StatusData load(CompoundTag tag, HolderLookup.Provider registries) {
            StatusData data = new StatusData();
            for (Tag entry : tag.getList("disabled", Tag.TAG_INT_ARRAY)) data.disabled.add(NbtUtils.loadUUID(entry));
            return data;
        }

        /** The format the file had; called only if the data were dirty, which they never are. */
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (UUID id : disabled) list.add(NbtUtils.createUUID(id));
            tag.put("disabled", list);
            return tag;
        }

        public boolean disabled(UUID player) {
            return disabled.contains(player);
        }
    }
}
