/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [Dec 11, 2013, 9:41:49 PM (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import thaumcraft.api.machines.IArcaneEar;

/**
 * {@code tt_arcaneear}: Thaumcraft's Arcane Ear, 1.7.10 {@code PeripheralArcaneEar} (and {@code ArcaneEarImplementation} of the
 * OpenComputers driver), through {@code thaumcraft.api.machines} (request A12).
 *
 * <ul>
 *   <li>{@code getNote()}: the note the ear listens for, 0 to 24 in note block clicks.</li>
 *   <li>{@code setNote(number)}: tunes the ear, saved and sent to the clients like a tuning by hand, without playing the note;
 *       answers nothing. 1.7.10 stored any number as a byte ({@code (byte) argument}), a note the ear could never hear; the API
 *       takes 0 to 24 only, so another number is the error {@code bad argument #1 (note 0 to 24 expected, got <n>)}
 *       (docs/deviations/ComputerCraft.md).</li>
 * </ul>
 */
public final class ArcaneEarTable implements PeripheralTable {
    public static final String TYPE = "tt_arcaneear";
    public static final String[] METHOD_NAMES = { "getNote", "setNote" };

    private final IArcaneEar ear;

    public ArcaneEarTable(IArcaneEar ear) {
        this.ear = ear;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String[] methodNames() {
        return METHOD_NAMES.clone();
    }

    @Override
    public Object target() {
        return ear;
    }

    @Override
    public Object[] call(int method, PeripheralArgs arguments) {
        // 1.7.10: "if (method == 0) getNote, else setNote"
        if (method == 0) return new Object[] { ear.getNote() };
        int note = (int) arguments.getDouble(0);
        if (note < IArcaneEar.MIN_NOTE || note > IArcaneEar.MAX_NOTE) {
            throw new PeripheralError("bad argument #1 (note " + IArcaneEar.MIN_NOTE + " to " + IArcaneEar.MAX_NOTE + " expected, got " + note + ")");
        }
        ear.setNote(note);
        return null;
    }
}
