package org.pytenix.placeholder;

import java.util.regex.Pattern;

import java.util.UUID;

public interface BasePlaceholder {


    public String toPlaceholder(UUID id, String text);
    public String fromPlaceholder(UUID id, String text);


    public Pattern getPattern();
}
