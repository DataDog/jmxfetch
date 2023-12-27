package org.datadog.misbehavingjmxserver;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/*
 * Just for fun, most of this was generated
 * Can also just use a random number.
 * Sample output: 
 * eyajutewe-icajudiwiq, ozujirimugo-uxuwivico, ubapabituz-taxesujuji, bowabucoyaqa-royedeyujap, uxayihifaf-rurobawifu, sicaqulorube-jeninojusom
 */
public class RandomIdentifier {
    private static final List<String> VOWELS = List.of("a", "e", "i", "o", "u");
    private static final List<String> CONSONANTS = List.of("b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "q", "r", "s", "t", "v", "w", "x", "y", "z");
    private static final List<String> RARE_SEQUENCES = Arrays.asList("qz", "qp", "qj", "qw", "qx", "qr", "qt", "qy", "qs", "qd", "qf", "qg", "qh", "qk", "ql", "qc", "qv", "qb", "qn", "qm", "wz", "wq", "wk", "wp", "wj", "wy", "wh", "wl");
    private final Random RANDOM;

    public RandomIdentifier(long seed) {
        this.RANDOM = new Random(seed);
    }

    private String generateWord(int minLength, int maxLength) {
        int wordLength = minLength + RANDOM.nextInt(maxLength - minLength + 1);
        StringBuilder wordBuilder = new StringBuilder();

        boolean hasVowel = false;
        for (int j = 0; j < wordLength; j++) {
            String letter;
            if (j % 2 == 0) {
                letter = getRandomLetter(CONSONANTS);
            } else {
                letter = getRandomLetter(VOWELS);
                hasVowel = true;
            }

            if (j > 0 && isRareSequence(wordBuilder.charAt(j - 1), letter)) {
                j--;
                continue;
            }

            wordBuilder.append(letter);
        }

        if (!hasVowel) {
            wordBuilder.setCharAt(RANDOM.nextInt(wordLength), VOWELS.get(RANDOM.nextInt(VOWELS.size())).charAt(0));
        }

        return wordBuilder.toString();
    }

    public String generateIdentifier() {
        int minLength = 9;
        int maxLength = 13;
        return generateWord(minLength, maxLength) + "-" + generateWord(minLength, maxLength);
    }

    private String getRandomLetter(List<String> letterSet) {
        return letterSet.get(RANDOM.nextInt(letterSet.size()));
    }

    private boolean isRareSequence(char prev, String next) {
        return RARE_SEQUENCES.contains(prev + next) || CONSONANTS.contains(prev + "") && CONSONANTS.contains(next);
    }
}

