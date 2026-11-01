package thaumic.tinkerer.mpserver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.ItemApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.wands.WandCap;
import thaumcraft.api.wands.WandHelper;
import thaumcraft.api.wands.WandRod;
import thaumic.tinkerer.common.block.BlockAnimationTablet;
import thaumic.tinkerer.common.block.camo.PulseDeviceBlock;
import thaumic.tinkerer.common.block.tile.TileEnchanter;
import thaumic.tinkerer.common.block.tile.TileRPlacer;
import thaumic.tinkerer.common.block.tile.kami.TileWarpGate;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorDislocator;
import thaumic.tinkerer.common.core.handler.kami.KamiArmorHandler;
import thaumic.tinkerer.common.core.handler.kami.SoulHeartHandler;
import thaumic.tinkerer.common.item.kami.ItemSkyPearl;
import thaumic.tinkerer.common.item.kami.KamiComponents;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.registry.TTRegistry;
import thaumic.tinkerer.mpserver.Scenes.Outcome;
import thaumic.tinkerer.mpserver.Scenes.Stage;

/**
 * The scenes of the second part of the multiplayer check (docs/MULTIPLAYER_VERIFICATION.md, sections f and g): the Remote
 * Placer, the Transvector Dislocator and the Animation Tablet (its fake player) inside and outside the dedicated server's spawn
 * protection, and the packets of the screens and keys that the first part did not send: the Remote Placer's distance buttons, the
 * Enchanter's start button, the Kami armour toggle, the Celestial Gateway's lock button and its teleport, and the server-to-client
 * Soul Hearts and Shadowbeam. Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see
 * NOTICE.md), never part of a release jar.
 *
 * <p>Every device is triggered by the lead itself: a right click on a stone button (the game's own input) next to a stone that
 * powers the device. The buttons stand outside the radius (x = 18 for the devices at x = 16, which are inside), because the
 * dedicated server refuses a non-operator's click on any block inside it ({@code ServerGamePacketListenerImpl.handleUseItemOn}
 * asks {@code mayInteract}), so the devices inside are loaded by the harness, not through their screens. What a device then does
 * is done by its own fake player ({@code [Minecraft]} for the Placer and the Dislocator, {@code [ThaumcraftTablet]} for the
 * tablet), which is no operator: inside the radius the add-on's {@code BlockProtection} must refuse it.</p>
 */
final class DeviceScenes {
    private static final int G = Scenes.G;
    /** The hotbar slot the harness puts a scenario's own item into (the lead's slot 7 is free). */
    static final int SLOT_EXTRA = 7;

    // ---- f. the devices under spawn protection --------------------------------------------------------------------
    // A row per device: the device at x (16: inside the radius; 40: outside), facing west, a stone at x + 1 that the button at x + 2
    // (on the stone's east face) powers, the lead at x + 5.5 looking at the button.
    static final int X_IN = Scenes.RADIUS;
    static final int X_OUT = 40;
    static final int Z_PLACER = 12;
    static final int Z_DISLOCATOR = 14;
    static final int Z_TABLET = 16;
    static final BlockPos PLACER_IN = new BlockPos(X_IN, G, Z_PLACER);
    static final BlockPos PLACER_OUT = new BlockPos(X_OUT, G, Z_PLACER);
    /** The distance the lead chooses on the outside placer's screen (the third radio button). */
    static final int PLACER_BLOCKS = 3;
    static final BlockPos DISLOCATOR_IN = new BlockPos(X_IN, G, Z_DISLOCATOR);
    static final BlockPos DISLOCATOR_OUT = new BlockPos(X_OUT, G, Z_DISLOCATOR);
    /** The ends the Dislocators are bound to: outside the radius in both rows (the swap needs both ends). */
    static final BlockPos END_IN = new BlockPos(X_IN + 3, G, 9);
    static final BlockPos END_OUT = new BlockPos(X_OUT + 3, G, 9);
    static final BlockPos TABLET_IN = new BlockPos(X_IN, G, Z_TABLET);
    static final BlockPos TABLET_OUT = new BlockPos(X_OUT, G, Z_TABLET);
    static final int PLANKS = 4;

    // ---- g. the packets ---------------------------------------------------------------------------------------------
    static final Vec3 ARMOR_SPOT = new Vec3(50.5, G, 30.5);
    static final Vec3 COW_AT = new Vec3(50.5, G, 40.5);
    static final Vec3 BEAM_SPOT = new Vec3(60.5, G, 30.5);
    static final BlockPos GATE_A = new BlockPos(60, G, 50);
    static final BlockPos GATE_B = new BlockPos(60, G, 60);

    static final List<String> NAMES = List.of("placer_menu", "placer_outside", "placer_inside", "dislocator_inside", "dislocator_outside",
            "device_tablet_inside", "device_tablet_outside", "enchanter_start", "armor_off", "armor_on", "soul_hearts", "shadow_beam",
            "gate_teleport", "gate_lock", "gate_locked");

    private DeviceScenes() {}

    /** The button's centre: a stone button on the east face of its stone, two pixels thick. */
    static Vec3 buttonFace(BlockPos button) {
        return new Vec3(button.getX() + 2 / 16.0, button.getY() + 0.5, button.getZ() + 0.5);
    }

    static BlockPos buttonOf(BlockPos device) {
        return device.east(2);
    }

    private static void pressStage(Map<String, Stage> stages, String name, BlockPos device) {
        BlockPos button = buttonOf(device);
        stages.put(name, new Stage(name, new Vec3(device.getX() + 5.5, G, device.getZ() + 0.5), buttonFace(button), button, Direction.EAST,
                Scenes.SLOT_EMPTY, false));
    }

    static void define(Map<String, Stage> stages) {
        // The outside placer's screen: a sneaking wand click opens it (BlockRPlacer.onWandRightClick); the stage gives the planks the
        // lead puts in by a container click. The lead looks at the top of the placer over its stone and button.
        stages.put("placer_menu", new Stage("placer_menu", new Vec3(PLACER_OUT.getX() + 3.5, G, PLACER_OUT.getZ() + 0.5),
                Scenes.faceCenter(PLACER_OUT, Direction.UP), PLACER_OUT, Direction.UP, Scenes.SLOT_FLIGHT, true,
                player -> setHotbar(player, SLOT_EXTRA, new ItemStack(Items.OAK_PLANKS, PLANKS)), null, false));
        pressStage(stages, "placer_outside", PLACER_OUT);
        pressStage(stages, "placer_inside", PLACER_IN);
        pressStage(stages, "dislocator_inside", DISLOCATOR_IN);
        pressStage(stages, "dislocator_outside", DISLOCATOR_OUT);
        pressStage(stages, "device_tablet_inside", TABLET_IN);
        pressStage(stages, "device_tablet_outside", TABLET_OUT);
        // The Enchanter's start button: the same place and screen as "enchanter" (the lead is staged again where it stands, with the
        // screen open, so that the check has the wand's vis to compare with).
        stages.put("enchanter_start", renamed(stages.get("enchanter"), "enchanter_start"));
        // The Kami armour: the stage puts the Gem Boots on (haste while the armour is on); the lead switches it with its key.
        stages.put("armor_off", new Stage("armor_off", ARMOR_SPOT, ARMOR_SPOT.add(0, 1.62, -5), null, null, Scenes.SLOT_EMPTY, false,
                player -> {
                    if (!player.getItemBySlot(EquipmentSlot.FEET).is(TTRegistry.item(LibItemNames.ICHOR_BOOTS_GEM))) {
                        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(TTRegistry.item(LibItemNames.ICHOR_BOOTS_GEM)));
                    }
                }, null, false));
        stages.put("armor_on", new Stage("armor_on", ARMOR_SPOT, ARMOR_SPOT.add(0, 1.62, -5), null, null, Scenes.SLOT_EMPTY, false));
        // Soul Hearts: the Awakened Ichorium Sword in Soul mode, a left click on a cow that does not move.
        stages.put("soul_hearts", new Stage("soul_hearts", new Vec3(COW_AT.x, G, COW_AT.z - 2.5), COW_AT.add(0, 0.7, 0), null, null,
                SLOT_EXTRA, false, player -> {
                    ItemStack sword = new ItemStack(TTRegistry.item(LibItemNames.ICHOR_SWORD_GEM));
                    sword.set(KamiComponents.TOOL_MODE.get(), 2);
                    setHotbar(player, SLOT_EXTRA, sword);
                }, "slot", true));
        // Shadowbeam: a staff with the focus, the use key held while the lead looks up into the sky.
        stages.put("shadow_beam", new Stage("shadow_beam", BEAM_SPOT, BEAM_SPOT.add(0, 31.62, -20), null, null, SLOT_EXTRA, false,
                player -> setHotbar(player, SLOT_EXTRA, Scenes.wand(LibItemNames.FOCUS_SHADOWBEAM)), "slot", false));
        // The Celestial Gateway: on gate A, sneaking (the destinations screen), a shift click on gate B's pearl; the lock button of gate B.
        Vec3 onA = new Vec3(GATE_A.getX() + 0.5, G + 1, GATE_A.getZ() + 0.5);
        stages.put("gate_teleport", new Stage("gate_teleport", onA, onA.add(0, 1.0, 3), null, null, Scenes.SLOT_EMPTY, true));
        stages.put("gate_lock", new Stage("gate_lock", new Vec3(GATE_B.getX() + 2.5, G, GATE_B.getZ() + 0.5),
                Scenes.faceCenter(GATE_B, Direction.EAST), GATE_B, Direction.EAST, Scenes.SLOT_EMPTY, false));
        stages.put("gate_locked", new Stage("gate_locked", onA, onA.add(0, 1.0, 3), null, null, Scenes.SLOT_EMPTY, true));
    }

    private static Stage renamed(Stage stage, String name) {
        return new Stage(name, stage.feet(), stage.aim(), stage.target(), stage.face(), stage.slot(), stage.crouch(), stage.prepare(),
                stage.holding(), stage.entity());
    }

    static void setHotbar(ServerPlayer player, int slot, ItemStack stack) {
        player.getInventory().setItem(slot, stack);
        player.inventoryMenu.broadcastChanges();
    }

    // ---- the world --------------------------------------------------------------------------------------------------

    static BlockState button() {
        return Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.EAST);
    }

    /** A device at {@code at} facing {@code facing}, with its stone and its button to the east. */
    static void deviceRow(ServerLevel level, BlockPos at, BlockState device) {
        Scenes.put(level, at, device);
        Scenes.put(level, at.east(), Blocks.STONE.defaultBlockState());
        Scenes.put(level, buttonOf(at), button());
    }

    static BlockState placer(Direction facing) {
        return TTRegistry.block(LibBlockNames.REMOTE_PLACER).defaultBlockState().setValue(PulseDeviceBlock.FACING, facing);
    }

    static BlockState dislocator(Direction facing) {
        return TTRegistry.block(LibBlockNames.DISLOCATOR).defaultBlockState().setValue(PulseDeviceBlock.FACING, facing);
    }

    /** A Remote Placer loaded with planks at the given distance. */
    static void loadPlacer(ServerLevel level, BlockPos at, int blocks) {
        if (!(level.getBlockEntity(at) instanceof TileRPlacer placer)) throw new IllegalStateException("no Remote Placer at " + at);
        placer.setItem(0, new ItemStack(Items.OAK_PLANKS, PLANKS));
        placer.setBlocks(blocks);
    }

    static void bind(ServerLevel level, BlockPos dislocator, BlockPos end) {
        if (!(level.getBlockEntity(dislocator) instanceof TileTransvectorDislocator device)) {
            throw new IllegalStateException("no Transvector Dislocator at " + dislocator);
        }
        device.setTarget(end);
    }

    static void build(ServerLevel level, ServerReport report) {
        // f. The devices. The inside placer is loaded by the harness (its screen cannot be opened inside the radius); the outside one
        // is empty until the lead fills it through its screen.
        deviceRow(level, PLACER_IN, placer(Direction.WEST));
        loadPlacer(level, PLACER_IN, 1);
        deviceRow(level, PLACER_OUT, placer(Direction.WEST));
        for (BlockPos device : List.of(DISLOCATOR_IN, DISLOCATOR_OUT)) {
            deviceRow(level, device, dislocator(Direction.WEST));
            Scenes.put(level, device.west(), Blocks.LAPIS_BLOCK.defaultBlockState());
        }
        Scenes.put(level, END_IN, Blocks.EMERALD_BLOCK.defaultBlockState());
        Scenes.put(level, END_OUT, Blocks.EMERALD_BLOCK.defaultBlockState());
        bind(level, DISLOCATOR_IN, END_IN);
        bind(level, DISLOCATOR_OUT, END_OUT);
        for (BlockPos tablet : List.of(TABLET_IN, TABLET_OUT)) {
            deviceRow(level, tablet, TTRegistry.block(LibBlockNames.ANIMATION_TABLET).defaultBlockState()
                    .setValue(BlockAnimationTablet.FACING, Direction.WEST));
            Scenes.put(level, tablet.west(), Blocks.STONE.defaultBlockState());
            if (!(level.getBlockEntity(tablet) instanceof TileAnimationTablet tile)) throw new IllegalStateException("no tablet at " + tablet);
            // Redstone mode: the tablet swings only while the button powers it; left click: it breaks the stone in front.
            tile.setItem(0, new ItemStack(Items.IRON_PICKAXE));
            tile.setRedstone(true);
            tile.setLeftClick(true);
        }

        // g. The Enchanter's start needs six pillars (two Obsidian Totems and a Nitor each) and a wand with vis in its wand slot.
        BlockState totem = BlocksTC.state("blockCosmeticSolid", 0);
        BlockState nitor = BlocksTC.state("blockAiry", 1);
        if (totem == null || nitor == null) throw new IllegalStateException("no Obsidian Totem or Nitor block: " + totem + ", " + nitor);
        BlockPos enchanter = Scenes.ENCHANTER;
        for (int[] offset : new int[][] { { -3, -3 }, { 3, -3 }, { -3, 0 }, { 3, 0 }, { -3, 3 }, { 3, 3 } }) {
            BlockPos column = enchanter.offset(offset[0], 0, offset[1]);
            Scenes.put(level, column, totem);
            Scenes.put(level, column.above(), totem);
            Scenes.put(level, column.above(2), nitor);
        }
        if (level.getBlockEntity(enchanter) instanceof TileEnchanter table) {
            ItemStack wand = ItemApi.getItem("itemWandCasting", 0);
            WandHelper.setRod(wand, WandRod.byTag("silverwood"));
            WandHelper.setCap(wand, WandCap.byTag("thaumium"));
            for (Aspect primal : Aspect.getPrimalAspects()) WandHelper.addRealVis(wand, primal, WandHelper.getMaxVis(wand), true);
            table.setItem(TileEnchanter.SLOT_WAND, wand);
        }

        // The cow of the Soul Hearts: it does not move and survives every hit.
        Cow cow = EntityType.COW.create(level);
        if (cow == null) throw new IllegalStateException("no cow");
        cow.setNoAi(true);
        cow.setSilent(true);
        cow.setPersistenceRequired();
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        cow.setHealth(1000F);
        cow.moveTo(COW_AT.x, COW_AT.y, COW_AT.z, 180F, 0F);
        level.addFreshEntity(cow);

        // Two Celestial Gateways; gate A holds a Sky Pearl attuned to gate B.
        Scenes.put(level, GATE_A, TTRegistry.block(LibBlockNames.WARP_GATE).defaultBlockState());
        Scenes.put(level, GATE_B, TTRegistry.block(LibBlockNames.WARP_GATE).defaultBlockState());
        if (!(level.getBlockEntity(GATE_A) instanceof TileWarpGate gate)) throw new IllegalStateException("no Celestial Gateway at " + GATE_A);
        ItemStack pearl = new ItemStack(TTRegistry.item(LibItemNames.SKY_PEARL));
        ItemSkyPearl.setValues(pearl, Level.OVERWORLD, GATE_B);
        gate.setItem(0, pearl);
        report.fact("devices", "placers " + PLACER_IN.toShortString() + " / " + PLACER_OUT.toShortString() + ", dislocators "
                + DISLOCATOR_IN.toShortString() + " / " + DISLOCATOR_OUT.toShortString() + ", tablets " + TABLET_IN.toShortString() + " / "
                + TABLET_OUT.toShortString() + ", gates " + GATE_A.toShortString() + " -> " + GATE_B.toShortString());
    }

    // ---- /ttmp stage: what the checks compare with ------------------------------------------------------------------

    static void baseline(ServerPlayer player, String name, Map<String, Object> before) {
        switch (name) {
            case "soul_hearts" -> before.put("hp", SoulHeartHandler.getHP(player));
            case "armor_off", "armor_on" -> before.put("armor", KamiArmorHandler.getArmorStatus(player));
            case "enchanter_start" -> {
                if (player.serverLevel().getBlockEntity(Scenes.ENCHANTER) instanceof TileEnchanter table) {
                    before.put("table_vis", Scenes.vis(table.getItem(TileEnchanter.SLOT_WAND)));
                }
            }
            default -> {}
        }
    }

    // ---- /ttmp check ------------------------------------------------------------------------------------------------

    private static boolean late(Map<String, Object> before, int seconds) {
        Object at = before.get("staged_at");
        return !(at instanceof Long started) || System.nanoTime() - started > seconds * 1_000_000_000L;
    }

    private static String slot(@Nullable TileRPlacer placer) {
        if (placer == null) return "none";
        ItemStack stack = placer.getItem(0);
        return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
    }

    @Nullable
    static Outcome check(ServerPlayer player, String name, @Nullable String arg, Map<String, Object> before) {
        ServerLevel level = player.serverLevel();
        Map<String, Object> values = new LinkedHashMap<>();
        switch (name) {
            case "placer_menu" -> {
                TileRPlacer placer = level.getBlockEntity(PLACER_OUT) instanceof TileRPlacer p ? p : null;
                int blocks = placer == null ? -1 : placer.blocks;
                values.put("blocks", blocks);
                values.put("slot", slot(placer));
                int left = Scenes.count(player, Items.OAK_PLANKS);
                boolean ok = placer != null && blocks == PLACER_BLOCKS && slot(placer).equals("minecraft:oak_planksx" + PLANKS) && left == 0;
                return Outcome.of(ok, "the outside Remote Placer's distance is " + blocks + " (the lead clicked the radio button for "
                        + arg + ": PacketPlacerButton), its slot holds " + slot(placer) + " (put in by container clicks), planks left on the lead "
                        + left, values);
            }
            case "placer_outside", "placer_inside" -> {
                boolean inside = name.endsWith("inside");
                BlockPos device = inside ? PLACER_IN : PLACER_OUT;
                TileRPlacer placer = level.getBlockEntity(device) instanceof TileRPlacer p ? p : null;
                BlockPos target = device.west(inside ? 1 : PLACER_BLOCKS);
                BlockState state = level.getBlockState(target);
                if (!inside && !state.is(Blocks.OAK_PLANKS) && !late(before, 10)) return Outcome.waiting("the placer has not placed yet");
                values.put("blocks", Scenes.blocks(level, List.of(target)));
                values.put("device", Scenes.pos(device));
                values.put("slot", slot(placer));
                String expectedSlot = "minecraft:oak_planksx" + (inside ? PLANKS : PLANKS - 1);
                boolean ok = (inside ? state.isAir() : state.is(Blocks.OAK_PLANKS)) && slot(placer).equals(expectedSlot);
                return Outcome.of(ok, "the button pulsed the Remote Placer at " + Scenes.pos(device) + (inside ? " inside" : " outside")
                        + " the spawn radius; its fake player [Minecraft] " + (state.is(Blocks.OAK_PLANKS) ? "placed" : "did not place")
                        + " planks at " + Scenes.pos(target) + " (" + Scenes.id(state) + ", expected " + (inside ? "minecraft:air" : "minecraft:oak_planks")
                        + "); the slot holds " + slot(placer) + " (expected " + expectedSlot + ")", values);
            }
            case "dislocator_inside", "dislocator_outside" -> {
                boolean inside = name.endsWith("inside");
                BlockPos front = (inside ? DISLOCATOR_IN : DISLOCATOR_OUT).west();
                BlockPos end = inside ? END_IN : END_OUT;
                BlockState atFront = level.getBlockState(front);
                BlockState atEnd = level.getBlockState(end);
                if (!inside && !atFront.is(Blocks.EMERALD_BLOCK) && !late(before, 10)) return Outcome.waiting("the Dislocator has not swapped yet");
                values.put("blocks", Scenes.blocks(level, List.of(front, end)));
                boolean ok = inside ? atFront.is(Blocks.LAPIS_BLOCK) && atEnd.is(Blocks.EMERALD_BLOCK)
                        : atFront.is(Blocks.EMERALD_BLOCK) && atEnd.is(Blocks.LAPIS_BLOCK);
                return Outcome.of(ok, "the button pulsed the Transvector Dislocator " + (inside ? "inside" : "outside") + " the spawn radius (front "
                        + Scenes.pos(front) + ", bound end " + Scenes.pos(end) + " outside): front " + Scenes.id(atFront) + ", end " + Scenes.id(atEnd)
                        + " (expected " + (inside ? "lapis and emerald, unchanged" : "swapped: emerald and lapis") + ")", values);
            }
            case "device_tablet_inside", "device_tablet_outside" -> {
                boolean inside = name.endsWith("inside");
                BlockPos device = inside ? TABLET_IN : TABLET_OUT;
                BlockPos front = device.west();
                BlockState state = level.getBlockState(front);
                TileAnimationTablet tablet = level.getBlockEntity(device) instanceof TileAnimationTablet t ? t : null;
                if (!inside && !state.isAir() && !late(before, 15)) return Outcome.waiting("the tablet has not broken the stone yet");
                int damage = tablet == null ? -1 : tablet.getItem(0).getDamageValue();
                values.put("blocks", Scenes.blocks(level, List.of(front)));
                values.put("device", Scenes.pos(device));
                values.put("tool_damage", damage);
                boolean holds = tablet != null && tablet.getItem(0).is(Items.IRON_PICKAXE);
                boolean ok = holds && (inside ? state.is(Blocks.STONE) && damage == 0 : state.isAir() && damage > 0);
                return Outcome.of(ok, "the button powered the Animation Tablet (redstone mode, left click, an iron pickaxe) " + (inside ? "inside"
                        : "outside") + " the spawn radius; its fake player [ThaumcraftTablet] " + (state.isAir() ? "broke" : "did not break")
                        + " the stone at " + Scenes.pos(front) + " (" + Scenes.id(state) + "); pickaxe damage " + damage, values);
            }
            case "enchanter_start" -> {
                if (!(level.getBlockEntity(Scenes.ENCHANTER) instanceof TileEnchanter table)) return Outcome.of(false, "no Enchanter", values);
                ItemStack tool = table.getItem(TileEnchanter.SLOT_TOOL);
                int sharpness = enchantmentLevel(level, tool, Enchantments.SHARPNESS);
                if (table.working && !late(before, 60)) return Outcome.waiting("the Enchanter is still working");
                String wandVis = Scenes.vis(table.getItem(TileEnchanter.SLOT_WAND));
                AspectList was = Scenes.parse(String.valueOf(before.get("table_vis")));
                AspectList is = WandHelper.getAllVis(table.getItem(TileEnchanter.SLOT_WAND));
                boolean paid = is.getAmount(Aspect.ORDER) < was.getAmount(Aspect.ORDER);
                values.put("tool", BuiltInRegistries.ITEM.getKey(tool.getItem()));
                values.put("sharpness", sharpness);
                values.put("working", table.working);
                boolean ok = tool.is(Items.DIAMOND_SWORD) && sharpness == 1 && !table.working && table.enchantments.isEmpty() && paid;
                return Outcome.of(ok, "after the start button (PacketEnchanterStartWorking) the Enchanter " + (table.working ? "is still working"
                        : "finished") + ": the tool " + BuiltInRegistries.ITEM.getKey(tool.getItem()) + " has Sharpness " + sharpness
                        + ", the wand in the Enchanter paid ordo " + (paid ? "" : "NOT ") + "(" + before.get("table_vis") + " -> " + wandVis + ")", values);
            }
            case "armor_off", "armor_on" -> {
                boolean on = name.endsWith("on");
                boolean status = KamiArmorHandler.getArmorStatus(player);
                MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);
                boolean hasted = haste != null && haste.getAmplifier() >= 1;
                if ((status != on || hasted != on) && !late(before, 5)) return Outcome.waiting("the armour has not switched yet");
                values.put("status", status);
                values.put("haste", hasted);
                boolean boots = player.getItemBySlot(EquipmentSlot.FEET).is(TTRegistry.item(LibItemNames.ICHOR_BOOTS_GEM));
                boolean ok = boots && status == on && hasted == on;
                return Outcome.of(ok, "the lead pressed its \"Toggle Awakened Armor\" key (PacketToggleArmor): the server's switch for the lead is "
                        + (status ? "on" : "off") + " (was " + before.get("armor") + "), the Gem Boots' Haste II is " + (hasted ? "there" : "gone")
                        + " (expected " + (on ? "on, haste" : "off, no haste") + "); boots worn " + boots, values);
            }
            case "soul_hearts" -> {
                int hp = SoulHeartHandler.getHP(player);
                int was = (Integer) before.get("hp");
                if (hp == was && !late(before, 5)) return Outcome.waiting("no Soul Heart yet");
                values.put("hp", hp);
                return Outcome.of(hp == was + 1, "a left click with the Awakened Ichorium Sword in Soul mode on the cow: the lead's Soul Hearts "
                        + was + " -> " + hp + " (PacketSoulHearts sent to its client)", values);
            }
            case "shadow_beam" -> {
                ItemStack wand = player.getInventory().getItem(SLOT_EXTRA);
                String now = Scenes.vis(wand);
                AspectList was = Scenes.parse(String.valueOf(before.get("vis")));
                AspectList is = WandHelper.getAllVis(wand);
                boolean paid = is.getAmount(Aspect.ORDER) < was.getAmount(Aspect.ORDER) && is.getAmount(Aspect.ENTROPY) < was.getAmount(Aspect.ENTROPY)
                        && is.getAmount(Aspect.AIR) < was.getAmount(Aspect.AIR);
                values.put("vis", now);
                return Outcome.of(paid, "the use key held with the Shadowbeam staff: the server fired the beam (PacketShadowBeam to the players within"
                        + " 64 blocks) and took ordo, perditio and aer every tick: vis " + before.get("vis") + " -> " + now, values);
            }
            case "gate_teleport", "gate_locked" -> {
                boolean expectMoved = name.equals("gate_teleport");
                Vec3 at = player.position();
                boolean onB = horizontal(at, GATE_B) < 1.0 && at.y >= G + 0.9;
                boolean onA = horizontal(at, GATE_A) < 1.0 && at.y >= G + 0.9;
                if (expectMoved && !onB && !late(before, 5)) return Outcome.waiting("the lead has not arrived at gate B yet");
                boolean locked = level.getBlockEntity(GATE_B) instanceof TileWarpGate gate && gate.locked;
                values.put("x", String.format(java.util.Locale.ROOT, "%.2f", at.x));
                values.put("y", String.format(java.util.Locale.ROOT, "%.2f", at.y));
                values.put("z", String.format(java.util.Locale.ROOT, "%.2f", at.z));
                boolean ok = expectMoved ? onB && !locked : onA && locked;
                return Outcome.of(ok, "a shift click on gate B in gate A's destinations screen (PacketWarpGateTeleport) with gate B "
                        + (locked ? "locked" : "open") + ": the lead is at " + Scenes.pos(BlockPos.containing(at)) + " ("
                        + (onB ? "on gate B" : onA ? "still on gate A" : "elsewhere") + "; expected " + (expectMoved ? "on gate B" : "still on gate A") + ")", values);
            }
            case "gate_lock" -> {
                boolean locked = level.getBlockEntity(GATE_B) instanceof TileWarpGate gate && gate.locked;
                if (!locked && !late(before, 5)) return Outcome.waiting("gate B is not locked yet");
                values.put("locked", locked);
                return Outcome.of(locked, "the lock button of gate B's screen (PacketWarpGateButton): gate B is " + (locked ? "locked" : "open")
                        + " on the server", values);
            }
            default -> {
                return null;
            }
        }
    }

    static double horizontal(Vec3 at, BlockPos gate) {
        double dx = at.x - (gate.getX() + 0.5);
        double dz = at.z - (gate.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    static int enchantmentLevel(ServerLevel level, ItemStack stack, ResourceKey<Enchantment> key) {
        return level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolder(key)
                .map(holder -> stack.getEnchantments().getLevel(holder)).orElse(0);
    }
}
