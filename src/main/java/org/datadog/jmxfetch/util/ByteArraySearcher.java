package org.datadog.jmxfetch.util;

/**
 * Employs the literal version of the Bitap/shift-and algorithm
 * to match short search terms in worst-case linear time.
 *
 * <p>The masks are bit-sliced to reduce spatial requirement from ~2KB
 * per matcher to ~256 bytes.
 */
public final class ByteArraySearcher {

    private final long[] high;
    private final long[] low;
    private final long termination;

    /**
     * Constructs a simple literal matcher from the input term.
     * @param term the input term, must be shorter than 64 bytes.
     */
    public ByteArraySearcher(byte[] term) {
        if (term.length > 64) {
            throw new IllegalArgumentException("term must be shorter than 64 characters");
        }
        this.high = new long[16];
        this.low = new long[16];
        int mask = 1;
        for (byte b : term) {
            low[b & 0xF] |= mask;
            high[b >>> 4] |= mask;
            mask <<= 1;
        }
        this.termination = 1 << (term.length - 1);
    }


    /**
     * Returns true if the array contains a literal match of this searcher's term.
     * @param array the input array
     * @return whether the array matches or not
     */
    public boolean matches(byte[] array) {
        long state = 0;
        for (byte symbol : array) {
            long highMask = high[symbol >>> 4];
            long lowMask = low[symbol & 0xF];
            state = ((state << 1) | 1) & highMask & lowMask;
            if ((state & termination) == termination) {
                return true;
            }
        }
        return false;
    }
}
