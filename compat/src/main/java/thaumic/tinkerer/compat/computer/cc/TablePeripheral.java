/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the bridge from the add-on's peripheral tables to
 * CC: Tweaked. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer.cc;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.compat.computer.PeripheralArgs;
import thaumic.tinkerer.compat.computer.PeripheralError;
import thaumic.tinkerer.compat.computer.PeripheralTable;

/**
 * A {@link PeripheralTable} as a CC: Tweaked peripheral. {@link IDynamicPeripheral} has the shape of the 1.7.10
 * {@code IPeripheral} (a type, the method names, {@code callMethod} by index), so the type and the names a script sees are
 * the table's.
 *
 * <p>A call copies its arguments on the computer thread ({@link IArguments} may not be read later) and runs the table on the
 * server thread with {@link ILuaContext#executeMainThreadTask}: the script waits for the next server tick. (1.7.10 ComputerCraft
 * ran {@code callMethod} on the computer thread, where the original changed blocks unsafely; CC: Tweaked asks peripherals that
 * touch the world to do this.) A {@link PeripheralError} becomes a Lua error with its message.</p>
 *
 * <p>Two peripherals are equal when they are of the same type and read the same block entity, so a computer keeps its
 * peripheral when the block around it changes but the block entity stays.</p>
 */
public final class TablePeripheral implements IDynamicPeripheral {
    private final PeripheralTable table;

    public TablePeripheral(PeripheralTable table) {
        this.table = table;
    }

    public PeripheralTable table() {
        return table;
    }

    @Override
    public String getType() {
        return table.type();
    }

    @Override
    public String[] getMethodNames() {
        return table.methodNames();
    }

    @Override
    public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments)
            throws LuaException {
        PeripheralArgs copied = copy(arguments);
        return context.executeMainThreadTask(() -> {
            try {
                return table.call(method, copied);
            } catch (PeripheralError error) {
                throw new LuaException(error.getMessage());
            }
        });
    }

    /** The values and the Lua type names of the arguments, read while the call is running on the computer thread. */
    static PeripheralArgs copy(IArguments arguments) throws LuaException {
        int count = arguments.count();
        Object[] values = new Object[count];
        String[] types = new String[count];
        for (int i = 0; i < count; i++) {
            values[i] = arguments.get(i);
            types[i] = arguments.getType(i);
        }
        return new PeripheralArgs(values, types);
    }

    @Override
    public Object getTarget() {
        return table.target();
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof TablePeripheral peripheral && peripheral.table.type().equals(table.type())
                && peripheral.table.target() == table.target();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IPeripheral peripheral && equals(peripheral);
    }

    @Override
    public int hashCode() {
        return 31 * table.type().hashCode() + System.identityHashCode(table.target());
    }

    @Override
    public String toString() {
        return "TablePeripheral[" + table.type() + " of " + table.target() + "]";
    }
}
