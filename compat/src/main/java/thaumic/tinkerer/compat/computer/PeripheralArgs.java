/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import java.util.Arrays;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * The arguments of one call: the values (converted by the computer mod: {@link Number}, {@link String}, {@link Boolean},
 * {@link Map} or null) and the Lua type name of each ({@code "nil"}, {@code "number"}, {@code "function"} ...), both copied on
 * the computer thread before the call is queued for the server thread.
 *
 * <p>1.7.10 cast the arguments ({@code (Double) arguments[0]}), so a missing or wrong argument failed with a Java exception
 * that ComputerCraft showed as {@code Java Exception Thrown: java.lang.ClassCastException}. The getters here fail with the
 * message CC: Tweaked uses for its own methods, {@code bad argument #1 (number expected, got nil)}.</p>
 */
public record PeripheralArgs(@Nullable Object[] values, String[] types) {
    public PeripheralArgs {
        if (values.length != types.length) throw new IllegalArgumentException("one type per value");
    }

    /** Arguments of plain Java values (the type names derived from the values), as a test or a script passes them. */
    public static PeripheralArgs of(@Nullable Object... values) {
        Object[] copy = values == null ? new Object[0] : values.clone();
        return new PeripheralArgs(copy, Arrays.stream(copy).map(PeripheralArgs::typeOf).toArray(String[]::new));
    }

    /** The Lua type name of a converted value. */
    public static String typeOf(@Nullable Object value) {
        if (value == null) return "nil";
        if (value instanceof String) return "string";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Number) return "number";
        if (value instanceof Map) return "table";
        return "userdata";
    }

    /** How many arguments the script passed (1.7.10 {@code arguments.length}). */
    public int count() {
        return values.length;
    }

    @Nullable
    public Object get(int index) {
        return index < values.length ? values[index] : null;
    }

    private String type(int index) {
        return index < types.length ? types[index] : "nil";
    }

    public double getDouble(int index) {
        if (get(index) instanceof Number number) return number.doubleValue();
        throw bad(index, "number");
    }

    public boolean getBoolean(int index) {
        if (get(index) instanceof Boolean bool) return bool;
        throw bad(index, "boolean");
    }

    public String getString(int index) {
        if (get(index) instanceof String string) return string;
        throw bad(index, "string");
    }

    public Map<?, ?> getTable(int index) {
        if (get(index) instanceof Map<?, ?> table) return table;
        throw bad(index, "table");
    }

    /** CC: Tweaked's {@code LuaValues.badArgument}: {@code bad argument #<n> (<expected> expected, got <actual>)}. */
    public PeripheralError bad(int index, String expected) {
        return new PeripheralError("bad argument #" + (index + 1) + " (" + expected + " expected, got " + type(index) + ")");
    }
}
