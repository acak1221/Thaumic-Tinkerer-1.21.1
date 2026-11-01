/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the shape of a computer peripheral of the add-on, without
 * any type of a computer mod. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import org.jetbrains.annotations.Nullable;

/**
 * One peripheral as 1.7.10 offered it to ComputerCraft ({@code IPeripheral}): a type name, a fixed list of method names and a
 * {@code callMethod} that takes the index of a method and its arguments and answers an array of return values.
 *
 * <p>The values are the plain Java values ComputerCraft converts to and from Lua: a Lua number is a {@link Number}, a string a
 * {@link String}, a boolean a {@link Boolean}, a table a {@link java.util.Map}, anything else {@code null}; a returned
 * {@link java.util.Map} or {@link java.util.Collection} becomes a table. Nothing here names a computer mod, so the tables can be
 * tested without one; {@code compat/.../computer/cc/TablePeripheral} hands them to CC: Tweaked.</p>
 *
 * <p>{@link #call} runs on the server thread (the adapter queues it there) and may change the world.</p>
 */
public interface PeripheralTable {
    /** The peripheral type a script sees ({@code peripheral.getType}), the 1.7.10 {@code getType()}. */
    String type();

    /** The method names in the order of the 1.7.10 {@code getMethodNames()}; {@link #call} takes an index into it. */
    String[] methodNames();

    /**
     * The 1.7.10 {@code callMethod}: the return values of method number {@code method}, or null (no value).
     *
     * @throws PeripheralError the Lua error the call raises (a 1.7.10 {@code LuaException})
     */
    @Nullable
    Object[] call(int method, PeripheralArgs arguments);

    /** What the peripheral reads (a block entity): two peripherals of the same type and target are the same peripheral. */
    Object target();
}
