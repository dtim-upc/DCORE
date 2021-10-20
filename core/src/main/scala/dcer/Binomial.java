package dcer;

import java.math.BigInteger;

// Credit to https://rosettacode.org/wiki/Evaluate_binomial_coefficients#Java

public class Binomial {

    // Blazing fast binomial.
    // NB: this method overflows without emitting any exception
    public static long binomialUnsafe(int n, int k) {
        if (k > n - k)
            k = n - k;

        long binom = 1;
        for (int i = 1; i <= k; i++)
            binom = binom * (n + 1 - i) / i;
        return binom;
    }

    // Same as unsafeBinomial but emits an exception on overflows.
    public static long binomialThrow(int n, int k) {
        if (k > n - k)
            k = n - k;

        long binom = 1;
        for (int i = 1; i <= k; i++) {
            try {
                binom = Math.multiplyExact(binom, n + 1 - i) / i;
            } catch (ArithmeticException e) {
                throw e;
            }
        }
        return binom;
    }

    // Safe version of unsafeBinomial but slower.
    public static BigInteger binomialSafe(int n, int k) {
        if (k > n - k)
            k = n - k;

        BigInteger binom = BigInteger.ONE;
        for (int i = 1; i <= k; i++) {
            binom = binom.multiply(BigInteger.valueOf(n + 1 - i));
            binom = binom.divide(BigInteger.valueOf(i));
        }
        return binom;
    }
}
