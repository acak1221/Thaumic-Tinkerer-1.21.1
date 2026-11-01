/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.common.core.fakeplayer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A server-side player that is never connected and never added to the level: the stand-in of the Animation Tablet and
 * the Remote Placer (1.7.10: Forge's {@code FakePlayer}), and the player the Transvector Dislocator asks the protection
 * questions with (new in the port).
 *
 * <p>The add-on is loader neutral, so it cannot use the FakePlayer of Fabric API or NeoForge. This is a plain
 * {@link ServerPlayer} with a dummy packet listener (every packet is dropped), no menus, no chat, no damage, no
 * collision box (0 by 0, so its eye is at its position: the owner places the eye), no statistics or scores, no
 * riding and no sleeping. It is not ticked by the level; its owner positions it and drives it ({@link #useItemOnBlock},
 * {@link #useItemInAir}, {@link #attackEntity}, {@link #interactWithEntity}), which run the vanilla paths
 * ({@code ServerPlayerGameMode}, {@code Player.attack}, {@code Player.interactOn}), so enchantments, tool wear, drops,
 * and the loaders' interaction and break events apply as they would to a player. NeoForge sees it as a fake player
 * ({@link #isFakePlayer()}).</p>
 */
public class TinkererFakePlayer extends ServerPlayer {
    /**
     * The profile of Forge's {@code FakePlayerFactory.getMinecraft} player, the one shared machine player of 1.7.10 that the
     * Remote Placer used; the port's devices without a profile of their own (the Remote Placer, the Transvector Dislocator)
     * act under it.
     */
    public static final GameProfile MINECRAFT_PROFILE =
            new GameProfile(UUID.fromString("41C82C87-7AfB-4024-BA57-13D2C99CAE77"), "[Minecraft]");

    /** The attack-strength ticker value that counts as a fully charged swing (1.7.10 had no attack cooldown). */
    private static final int CHARGED = 1000;
    /**
     * The most ticks {@link #advanceCooldowns()} catches up at once (five minutes): longer than any vanilla item cooldown,
     * and a bound on the loop after a device stood still for a long time.
     */
    private static final int MAX_COOLDOWN_CATCH_UP = 6000;

    private final Multimap<Holder<Attribute>, AttributeModifier> appliedModifiers = HashMultimap.create();
    private ItemStack modifiersFor = ItemStack.EMPTY;
    /** The game time up to which the item cooldowns have run (see {@link #advanceCooldowns()}). */
    private long cooldownsAt;

    public TinkererFakePlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile, ClientInformation.createDefault());
        this.connection = new SilentConnection(level, this, profile);
        this.getAbilities().instabuild = false;
        // The Entity constructor takes the size and the eye height of the entity type (a player's 0.6 by 1.8, eye 1.62);
        // getDefaultDimensions below is only asked when the dimensions are refreshed, which a player that is never ticked
        // and never changes its pose does not do by itself. Refreshed here, the box is 0 by 0 and the eye is at the
        // position, so an item that looks along the player's view (a bucket, a thrown pearl) starts where the owner put
        // the player, not 1.62 above it.
        this.refreshDimensions();
        this.cooldownsAt = level.getGameTime();
        // Not on the ground, as the 1.7.10 FakePlayer (never ticked, so never landed): the player breaks blocks at a fifth
        // of the speed (Player.getDestroySpeed, 1.7.10 getBreakSpeed had the same rule), and its attacks are neither
        // sweeping (a charged sword swing on the ground also hurts the mobs next to the target) nor critical.
    }

    /**
     * The fake player earns no advancements: the {@code ServerPlayer} constructor registers the criterion listeners of its
     * profile, so a kill of the Tablet or a block of the Placer could complete an advancement, give its rewards and announce
     * "[ThaumcraftTablet] has made the advancement ..." in the chat. The listeners are taken off whenever the advancements are
     * asked for (every trigger asks), which also covers the re-registration of a data pack reload.
     */
    @Override
    public PlayerAdvancements getAdvancements() {
        PlayerAdvancements advancements = super.getAdvancements();
        if (advancements != null) advancements.stopListening();
        return advancements;
    }

    /** The super constructor looks up the world spawn height through the chunk source: not needed, and it loads chunks. */
    @Override
    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
        return pos;
    }

    /**
     * Runs the item cooldowns (an ender pearl's, a wind charge's, a chorus fruit's) up to the level's game time. A player's
     * cooldowns run in its tick; the fake player is never ticked, so without this a cooldown it started never ended and the
     * item stayed unusable in the device for good. The owner calls it before the fake player acts (the Tablet every tick,
     * the Remote Placer on every pulse); at most {@value #MAX_COOLDOWN_CATCH_UP} ticks are caught up per call.
     */
    public void advanceCooldowns() {
        long now = this.serverLevel().getGameTime();
        long elapsed = Math.min(Math.max(now - cooldownsAt, 0L), MAX_COOLDOWN_CATCH_UP);
        cooldownsAt = now;
        for (long i = 0; i < elapsed; i++) this.getCooldowns().tick();
    }

    /** Puts the fake player at a point, looking along {@code yRot}/{@code xRot} (1.7.10 {@code posX/rotationYaw}). */
    public void placeAt(double x, double y, double z, float yRot, float xRot) {
        this.moveTo(x, y, z, yRot, xRot);
        this.setYHeadRot(yRot);
        this.setYBodyRot(yRot);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0;
    }

    /**
     * Makes the attribute map agree with the stack in the main hand. A level ticks its players, which is where vanilla
     * applies the attribute modifiers of held items (a sword's attack damage); a fake player is never ticked, so the
     * owner calls this before it attacks.
     */
    public void refreshHeldItemModifiers() {
        ItemStack held = this.getMainHandItem();
        if (ItemStack.isSameItemSameComponents(modifiersFor, held)) return;
        this.getAttributes().removeAttributeModifiers(appliedModifiers);
        appliedModifiers.clear();
        if (!held.isEmpty()) {
            held.forEachModifier(EquipmentSlot.MAINHAND, appliedModifiers::put);
            this.getAttributes().addTransientAttributeModifiers(appliedModifiers);
        }
        modifiersFor = held.copy();
    }

    /** A right click on a block, as a player would do it; the vanilla path fires the interaction events. */
    public InteractionResult useItemOnBlock(BlockHitResult hit) {
        ItemStack stack = this.getMainHandItem();
        return this.gameMode.useItemOn(this, this.serverLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }

    /** A right click into the air ({@code sendUseItem} of 1.7.10). The use of an item that needs holding is dropped at once. */
    public InteractionResult useItemInAir() {
        ItemStack stack = this.getMainHandItem();
        InteractionResult result = this.gameMode.useItem(this, this.serverLevel(), stack, InteractionHand.MAIN_HAND);
        if (this.isUsingItem()) this.stopUsingItem();
        return result;
    }

    /** {@code attackTargetEntityWithCurrentItem}: a left click on an entity with a charged swing. */
    public void attackEntity(Entity target) {
        refreshHeldItemModifiers();
        this.attackStrengthTicker = CHARGED;
        this.attack(target);
    }

    /**
     * {@code interactWith}: a right click on an entity; true when something happened. A villager or wandering trader that
     * started trading with the fake player is let go at once: no trading screen opens for it, and the merchant would
     * otherwise stay "busy" with a player that never closes the screen, for every real player.
     */
    public boolean interactWithEntity(Entity target) {
        InteractionResult result = this.interactOn(target, InteractionHand.MAIN_HAND);
        if (this.isUsingItem()) this.stopUsingItem();
        if (target instanceof AbstractVillager merchant && merchant.getTradingPlayer() == this) merchant.setTradingPlayer(null);
        return result.consumesAction();
    }

    /** Throws out everything the fake player carries except the slots in {@code keep}. */
    public void dropAllExcept(int... keep) {
        for (int slot = 0; slot < this.getInventory().getContainerSize(); slot++) {
            boolean kept = false;
            for (int k : keep) kept |= k == slot;
            if (kept) continue;
            ItemStack stack = this.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                this.spawnAtLocation(stack, 0);
                this.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    // ---- everything below keeps the fake player inert -------------------------------------------------------------

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return EntityDimensions.fixed(0.0F, 0.0F);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public boolean setGameMode(GameType gameMode) {
        return false;
    }

    @Override
    public void tick() {}

    @Override
    public void doTick() {}

    @Override
    public void setHealth(float health) {}

    @Override
    public void sendSystemMessage(Component message) {}

    @Override
    public void displayClientMessage(Component message, boolean actionBar) {}

    /** No menu is ever shown to the fake player, so a chest it "opens" does not keep a viewer. */
    @Override
    public OptionalInt openMenu(MenuProvider provider) {
        return OptionalInt.empty();
    }

    /**
     * NeoForge's extended menus ({@code IPlayerExtension.openMenu(MenuProvider, Consumer)}, what Architectury's
     * {@code MenuRegistry.openExtendedMenu} and NeoForge's own {@code openMenu(provider, pos)} call; NeoForge's
     * {@code openMenu(MenuProvider)} goes through it too): refused like the plain one. No {@code @Override}: the method does
     * not exist in vanilla, against which this class is compiled; on NeoForge it overrides {@code ServerPlayer}'s.
     */
    public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
        return OptionalInt.empty();
    }

    /** A horse's or llama's inventory is not opened either (vanilla opens it without {@link #openMenu}). */
    @Override
    public void openHorseInventory(AbstractHorse horse, Container inventory) {}

    /**
     * NeoForge's {@code IPlayerExtension.isFakePlayer()}: true, so NeoForge and the mods that ask treat the devices' player
     * as a machine (as Forge's {@code FakePlayer} of 1.7.10 was). No {@code @Override}, for the reason given above.
     */
    public boolean isFakePlayer() {
        return true;
    }

    /**
     * No statistics and so no scoreboard scores: a scoreboard objective on a statistic (blocks mined, items used, mobs
     * killed) would otherwise list "[ThaumcraftTablet]" or "[Minecraft]" with the devices' work.
     */
    @Override
    public void awardStat(Stat<?> stat, int amount) {}

    @Override
    public void resetStat(Stat<?> stat) {}

    /** No kill score either (the "totalKillCount" and team-kill objectives, and the player's score). */
    @Override
    public void awardKillScore(Entity killed, int score, DamageSource source) {}

    /**
     * The fake player boards nothing: a Tablet in right-click mode in front of a boat or a minecart would sit a player
     * that is never ticked in it, and the vehicle would stand still with it for good.
     */
    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    /**
     * Nor does it sleep: at night a Tablet in right-click mode in front of a bed would lie down in it, and the bed would stay
     * occupied by a player that never wakes. (In the day vanilla also sets the respawn point to the bed; that is refused too.)
     */
    @Override
    public Either<BedSleepingProblem, Unit> startSleepInBed(BlockPos pos) {
        return Either.left(BedSleepingProblem.OTHER_PROBLEM);
    }

    @Override
    public void startSleeping(BlockPos pos) {}

    /** The packet listener of the fake player: it has no channel, so every packet is dropped. */
    private static final class SilentConnection extends ServerGamePacketListenerImpl {
        SilentConnection(ServerLevel level, ServerPlayer player, GameProfile profile) {
            super(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(profile, false));
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {}

        @Override
        public void disconnect(Component reason) {}
    }
}
