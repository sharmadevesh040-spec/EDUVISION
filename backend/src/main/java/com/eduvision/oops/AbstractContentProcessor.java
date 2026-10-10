package com.eduvision.oops;

/**
 * Abstract base for all {@link ContentProcessor}s - inheritance + shared formatting logic.
 *
 * <p>Concrete processors only provide {@link #kind()} and {@link #describe(int, int)}; the common
 * string layout is defined here once and reused (template-method style).
 */
public abstract class AbstractContentProcessor implements ContentProcessor {

    /** Shared formatting helper reused by every concrete processor. */
    protected String summary(int parts, int questions) {
        return String.format("%s[kind=%s, parts=%d, questions=%d]",
                getClass().getSimpleName(), kind(), parts, questions);
    }

    /** Appends a processor-specific detail to the shared summary. */
    protected String describeWith(String detail, int parts, int questions) {
        return summary(parts, questions) + " " + detail;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{kind=" + kind() + "}";
    }
}
