package org.pytenix.placeholder;

import com.google.gson.Gson;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;

import java.util.*;

public class WordProtector {

    private Trie trie;


    public void build(Set<String> words) {
        if (words == null || words.isEmpty()) {
            this.trie = null;
            return;
        }

        Trie.TrieBuilder builder = Trie.builder()
                .ignoreCase()
                .onlyWholeWords();

        for (String word : words) {
            if (word.length() > 1) {
                builder.addKeyword(word);

            }
        }

        this.trie = builder.build();
    }

    public ProtectionResult protect(String text) {
        if (trie == null || text == null || text.isEmpty()) {
            return new ProtectionResult(text, Collections.emptyMap());
        }
        Collection<Emit> emits = trie.parseText(text);
        if (emits.isEmpty()) {
            return new ProtectionResult(text, Collections.emptyMap());
        }


        Map<String, String> replacements = new HashMap<>();
        StringBuilder sb = new StringBuilder(text);


        List<Emit> sortedEmits = new ArrayList<>(emits);
        sortedEmits.sort(Comparator.comparingInt(Emit::getStart).reversed());

        int counter = 0;
        for (Emit emit : sortedEmits) {

            String placeholder = "{W" + counter++ + "}";
            String originalWord = emit.getKeyword();


            replacements.put(placeholder, originalWord);


            sb.replace(emit.getStart(), emit.getEnd() + 1, placeholder);
        }

        return new ProtectionResult(sb.toString(), replacements);
    }

    public String restore(String text, Map<String, String> replacements) {
        if (replacements == null || replacements.isEmpty()) return text;


        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }

    public record ProtectionResult(String maskedText, Map<String, String> replacements) {}
}