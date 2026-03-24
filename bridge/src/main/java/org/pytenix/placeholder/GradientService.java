package org.pytenix.placeholder;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradientService {

    // Regex für Hex-Colors
    private static final String COLOR_CODE = "(?:§x(?:§[0-9a-fA-F]){6}|[§&]#[0-9a-fA-F]{6})";
    private static final String FORMAT_CODE = "(?:[§&][l-oK-OrR])";
    private static final String TEXT_CHARS = "[^§&]+";

    private static final Pattern GRADIENT_WORD_PATTERN = Pattern.compile("((?:" + COLOR_CODE + "(?:" + FORMAT_CODE + ")*" + TEXT_CHARS + "){2,})");
    private static final Pattern GRADIENT_HEX_PATTERN = Pattern.compile(COLOR_CODE);
    private static final Pattern FORMAT_PATTERN = Pattern.compile("(?i)[§&][l-oK-OrR]");

    public Cache<UUID, Map<String, GradientData>> cachedGradients = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS).build();

    public ExtractionResult stripAndAnalyze(String input) {
        if (input == null || input.isEmpty()) return new ExtractionResult(input, new HashMap<>());

        Map<String, GradientData> foundGradients = new HashMap<>();
        String trimmedInput = input.trim();

        Matcher fullMatcher = GRADIENT_WORD_PATTERN.matcher(trimmedInput);
        if (fullMatcher.matches()) {
            String fullGradientString = fullMatcher.group(1);

            GradientData data = extractColorsAndFormat(fullGradientString);
            foundGradients.put("FULL_LINE", data);

            String cleanText = GRADIENT_HEX_PATTERN.matcher(input).replaceAll("");
            cleanText = FORMAT_PATTERN.matcher(cleanText).replaceAll("");

            return new ExtractionResult(cleanText, foundGradients);
        }

        Matcher m = GRADIENT_WORD_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        int gradientCounter = 0;

        while (m.find()) {
            String fullGradientString = m.group(1);
            GradientData data = extractColorsAndFormat(fullGradientString);

            String cleanWord = GRADIENT_HEX_PATTERN.matcher(fullGradientString).replaceAll("");
            cleanWord = FORMAT_PATTERN.matcher(cleanWord).replaceAll("");

            String tagId = "G" + gradientCounter;
            String replacement = "<" + tagId + ">" + cleanWord + "</" + tagId + ">";

            foundGradients.put(tagId, data);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            gradientCounter++;
        }
        m.appendTail(sb);

        return new ExtractionResult(sb.toString(), foundGradients);
    }

    public String restoreGradients(UUID uuid, String translatedText) {
        Map<String, GradientData> gradients = cachedGradients.getIfPresent(uuid);
        if (gradients == null || gradients.isEmpty()) return translatedText;

        if (gradients.containsKey("FULL_LINE")) {
            return applyGradientToWord(translatedText, gradients.get("FULL_LINE"));
        }

        String result = translatedText;
        for (Map.Entry<String, GradientData> entry : gradients.entrySet()) {
            String tagId = entry.getKey();
            String startTag = "<" + tagId + ">";
            String endTag = "</" + tagId + ">";

            Pattern tagPattern = Pattern.compile(Pattern.quote(startTag) + "(.*?)" + Pattern.quote(endTag), Pattern.DOTALL);
            Matcher m = tagPattern.matcher(result);
            StringBuffer sb = new StringBuffer();

            while (m.find()) {
                String translatedWord = m.group(1);
                String gradientApplied = applyGradientToWord(translatedWord, entry.getValue());
                m.appendReplacement(sb, Matcher.quoteReplacement(gradientApplied));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    private GradientData extractColorsAndFormat(String fullGradientString) {
        Matcher colorMatcher = GRADIENT_HEX_PATTERN.matcher(fullGradientString);
        Color firstColor = null;
        Color lastColor = null;
        while (colorMatcher.find()) {
            if (firstColor == null) firstColor = parseColor(colorMatcher.group());
            lastColor = parseColor(colorMatcher.group());
        }

        boolean isBold = fullGradientString.contains("§l") || fullGradientString.contains("&l");
        boolean isItalic = fullGradientString.contains("§o") || fullGradientString.contains("&o");

        return new GradientData(firstColor, lastColor, isBold, isItalic);
    }

    private String applyGradientToWord(String text, GradientData info) {
        if (text == null || text.isEmpty()) return text;

        if (info.startColor().equals(info.endColor())) {
            StringBuilder sb = new StringBuilder();
            sb.append(toModernHex(info.startColor()));
            if (info.bold()) sb.append("§l");
            if (info.italic()) sb.append("§o");
            sb.append(text);
            return sb.toString();
        }

        int visibleLength = text.length();
        StringBuilder sb = new StringBuilder(text.length() * 14);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float t = (visibleLength > 1) ? (float) i / (visibleLength - 1) : 0;
            Color current = interpolate(info.startColor(), info.endColor(), t);

            sb.append(toModernHex(current));
            if (info.bold()) sb.append("§l");
            if (info.italic()) sb.append("§o");
            sb.append(c);
        }

        return sb.toString();
    }

    private Color interpolate(Color start, Color end, float t) {
        int r = (int) (start.getRed() + t * (end.getRed() - start.getRed()));
        int g = (int) (start.getGreen() + t * (end.getGreen() - start.getGreen()));
        int b = (int) (start.getBlue() + t * (end.getBlue() - start.getBlue()));
        return new Color(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
    }

    private Color parseColor(String hexString) {
        if (hexString.startsWith("§x")) {
            String raw = hexString.replace("§", "").substring(1);
            return new Color(Integer.parseInt(raw, 16));
        }
        return new Color(Integer.parseInt(hexString.substring(2), 16));
    }

    private static String toModernHex(Color c) {
        return String.format("§#%06x", c.getRGB() & 0xFFFFFF);
    }

    public record ExtractionResult(String cleanText, Map<String, GradientData> gradients) {
        public boolean hasGradients() {
            return !gradients.isEmpty();
        }
    }

    public record GradientData(Color startColor, Color endColor, boolean bold, boolean italic) {}
}