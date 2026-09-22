package com.oble.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {
    @Test fun gujaratiScript() =
        assertEquals(SpokenLanguage.GUJARATI, LanguageDetector.detect("આ વિચાર સારો છે"))

    @Test fun gujaratiEnglishMix() =
        assertEquals(SpokenLanguage.GUJARATI_ENGLISH, LanguageDetector.detect("આ feature restaurant owner માટે useful થઈ શકે."))

    @Test fun hindiScript() =
        assertEquals(SpokenLanguage.HINDI, LanguageDetector.detect("यह आइडिया बहुत अच्छा है"))

    @Test fun hinglishRoman() =
        assertEquals(SpokenLanguage.HINGLISH, LanguageDetector.detect("yeh feature customer ke liye bahut useful hai"))

    @Test fun romanGujarati() =
        assertEquals(
            SpokenLanguage.GUJARATI_ENGLISH,
            LanguageDetector.detect("Restaurant ma customer biji vaar ave tyare ena mobile number thi repeat customer automatically identify thai jay to saru")
        )

    @Test fun english() =
        assertEquals(SpokenLanguage.ENGLISH, LanguageDetector.detect("We should sell this as a monthly subscription"))

    @Test fun blank() = assertEquals(SpokenLanguage.UNKNOWN, LanguageDetector.detect("   "))
}
