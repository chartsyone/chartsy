package one.chartsy;

/**
 * The type of financial instrument.
 *
 * @see AssetType
 */
public interface InstrumentType {

    /**
     * The financial asset type identifier.
     */
    String name();

    /**
     * Yields {@code true} if the referred instrument type is tradable, and yields {@code false} otherwise.
     */
    boolean isTradable();
}
