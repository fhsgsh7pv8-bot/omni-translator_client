package org.pytenix.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor @NoArgsConstructor
public class TranslationResponse {

    private UUID id;

    private String translatedText;

    private String targetLang; // z.B. "EN", "DE", "ES"

}
