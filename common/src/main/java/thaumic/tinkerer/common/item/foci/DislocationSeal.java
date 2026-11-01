/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the server's seal on the loads of the Focus of
 * Dislocation. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.common.item.foci;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The seal that tells a load the focus took itself from a load written by hand.
 *
 * <p>Why: a creative player can give his stacks any component, so a focus can be made to carry a spawner of withers or a
 * sign with commands. Vanilla puts the data of such block entities ({@code BlockEntity.onlyOpCanSetNbt}: the spawners, the
 * command block, signs, lecterns) from an item only for a player who may use the game master blocks
 * ({@code BlockItem.updateCustomBlockEntityTag}); the focus follows that rule, but a load the focus really took from the world
 * is no forgery, and moving a spawner, a sign or a lectern with its book is what the focus is for (1.7.10 asked 100 Perditio
 * for a spawner because it moved its mob). So the server seals what it takes: an HMAC-SHA256 of the carried state and block
 * entity data under a key of 32 random bytes that the world keeps ({@code data/thaumictinkerer_dislocation_seal.dat} of the
 * overworld) and that never leaves the server. A load whose seal matches is put back with its data for every player.</p>
 *
 * <p>The input is the state as {@link NbtUtils#writeBlockState} and the block entity as its text form, both with the keys of
 * every compound sorted ({@code StringTagVisitor}), so a load that went through saving and the network gives the same text.</p>
 */
public final class DislocationSeal extends SavedData {
    static final String NAME = "thaumictinkerer_dislocation_seal";
    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_BYTES = 32;

    private final byte[] key;

    private DislocationSeal(byte[] key) {
        this.key = key;
    }

    private static DislocationSeal create() {
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        DislocationSeal seal = new DislocationSeal(key);
        seal.setDirty();
        return seal;
    }

    private static DislocationSeal load(CompoundTag tag, HolderLookup.Provider registries) {
        byte[] key = tag.getByteArray("key");
        return key.length == KEY_BYTES ? new DislocationSeal(key) : create();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putByteArray("key", key);
        return tag;
    }

    private static DislocationSeal of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DislocationSeal::create, DislocationSeal::load, null), NAME);
    }

    /** The seal of this server on a load of this state and block entity data. */
    public static String seal(MinecraftServer server, BlockState state, CompoundTag tile) {
        return HexFormat.of().formatHex(mac(of(server).key, state, tile));
    }

    /** Whether the load carries the seal of this server on exactly its state and block entity data. */
    public static boolean isGenuine(MinecraftServer server, FociComponents.DislocationData data) {
        if (data.seal().isEmpty()) return false;
        byte[] given;
        try {
            given = HexFormat.of().parseHex(data.seal());
        } catch (IllegalArgumentException notHex) {
            return false;
        }
        return MessageDigest.isEqual(given, mac(of(server).key, data.state(), data.tile()));
    }

    private static byte[] mac(byte[] key, BlockState state, CompoundTag tile) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            mac.update(NbtUtils.writeBlockState(state).toString().getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(tile.toString().getBytes(StandardCharsets.UTF_8));
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is a standard algorithm every Java runtime has.
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
