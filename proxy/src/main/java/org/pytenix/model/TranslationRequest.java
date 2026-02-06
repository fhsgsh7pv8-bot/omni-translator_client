package org.pytenix.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class TranslationRequest {


    private UUID id;

    private String text;

    private String targetLang; // z.B. "EN", "DE", "ES"

    private String licenseKey;
    private String module;

}