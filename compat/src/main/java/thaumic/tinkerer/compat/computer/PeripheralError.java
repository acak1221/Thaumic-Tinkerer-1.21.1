/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

/** The Lua error a peripheral method raises (1.7.10 {@code LuaException}); the adapter turns it into CC: Tweaked's. */
public final class PeripheralError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PeripheralError(String message) {
        super(message, null, false, false);
    }
}
