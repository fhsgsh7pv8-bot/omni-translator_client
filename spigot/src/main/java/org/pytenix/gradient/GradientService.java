package org.pytenix.gradient;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradientService {


    //NOTE: KI GENERATED/IMPROVED!

    private static final Pattern GRADIENT_HEX_PATTERN = Pattern.compile(
            "(?:§x(?:§[0-9a-fA-F]){6})|" +
                    "(?:[§&]#[0-9a-fA-F]{6})"
    );


    public ExtractionResult stripAndAnalyze(String input) {
        if (input == null) return new ExtractionResult(input, GradientInfo.NONE);

        Matcher matcher = GRADIENT_HEX_PATTERN.matcher(input);
        Color firstColor = null;
        Color lastColor = null;
        boolean foundAny = false;

        while (matcher.find()) {
            Color foundColor = parseColor(matcher.group());
            if (firstColor == null) firstColor = foundColor;
            lastColor = foundColor;
            foundAny = true;
        }

        boolean isBold = input.contains("§l") || input.contains("&l");
        boolean isItalic = input.contains("§o") || input.contains("&o");

        String cleanText = input;
        cleanText = GRADIENT_HEX_PATTERN.matcher(cleanText).replaceAll("").trim();

        if (!foundAny) {
            return new ExtractionResult(cleanText, GradientInfo.NONE);
        }

        return new ExtractionResult(cleanText, new GradientInfo(firstColor, lastColor, isBold, isItalic));
    }


    public String applyGradient(String text, GradientInfo info) {
        if (text == null || text.isEmpty()) return text;
        if (!info.isGradient()) return text;

        if (info.startColor().equals(info.endColor())) {
            StringBuilder sb = new StringBuilder();
            sb.append(toModernHex(info.startColor()));
            if (info.bold()) sb.append("§l");
            if (info.italic()) sb.append("§o");
            sb.append(text);
            return sb.toString();
        }

        int visibleLength = getVisibleLength(text);

        StringBuilder sb = new StringBuilder(text.length() * 14);
        int currentVisibleIndex = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < text.length()) {
                sb.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }

            float t = (visibleLength > 1) ? (float) currentVisibleIndex / (visibleLength - 1) : 0;
            Color current = interpolate(info.startColor(), info.endColor(), t);

            sb.append(toModernHex(current));
            if (info.bold()) sb.append("§l");
            if (info.italic()) sb.append("§o");
            sb.append(c);

            currentVisibleIndex++;
        }

        return sb.toString();
    }


    private int getVisibleLength(String text) {
        int len = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                i++;
            } else {
                len++;
            }
        }
        return len;
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

    public record ExtractionResult(String cleanText, GradientInfo info) {}

    public record GradientInfo(Color startColor, Color endColor, boolean bold, boolean italic) {
        public static final GradientInfo NONE = new GradientInfo(null, null, false, false);

        public boolean isGradient() {
            return startColor != null && endColor != null;
        }
    }
}