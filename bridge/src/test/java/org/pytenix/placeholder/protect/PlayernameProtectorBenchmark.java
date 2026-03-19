package org.pytenix.placeholder.protect;

import java.util.HashMap;
import java.util.Map;

public class PlayernameProtectorBenchmark {
    public static void main(String[] args) {
        PlayernameProtector protector = new PlayernameProtector(null);
        Map<String, String> replacements = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            replacements.put("{P" + i + "}", "PlayerName_" + i);
        }

        String template = "Hello {P0}, {P1}, and {P2}! Welcome {P3} and {P4} to the server. Check out {P5}, {P6}, {P7}, {P8}, {P9}, {P10}, {P11}, {P12}, {P13}, {P14}, {P15}, {P16}, {P17}, {P18}, {P19}.";

        // Warmup
        for (int i = 0; i < 100000; i++) {
            protector.restoreNames(template, replacements);
        }

        long start = System.nanoTime();
        int iterations = 1000000;
        for (int i = 0; i < iterations; i++) {
            protector.restoreNames(template, replacements);
        }
        long end = System.nanoTime();

        System.out.println("Time taken: " + (end - start) / 1000000.0 + " ms");
    }
}
