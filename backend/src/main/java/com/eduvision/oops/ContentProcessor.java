package com.eduvision.oops;

/**
 * Interface every content processor implements (interface + polymorphic dispatch).
 *
 * <p>The registry stores processors behind this interface and calls {@link #kind()} /
 * {@link #describe(int, int)} without knowing the concrete class.
 */
public interface ContentProcessor {

    /** Stable identifier used to look the processor up (e.g. {@code "quiz"}). */
    String kind();

    /** Human-readable description of what this processor does for a piece of content. */
    String describe(int parts, int questions);
}
