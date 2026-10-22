package eu.kanade.tachiyomi.data.tts

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SupertonicLanguageTest {

    @Test
    fun `scripts map to their language`() {
        supertonicLanguage("The fog rolled over Tingen.") shouldBe "en"
        supertonicLanguage("안녕하세요, 반갑습니다.") shouldBe "ko"
        supertonicLanguage("彼は静かに本を読んでいた。") shouldBe "ja"
        supertonicLanguage("他静静地看着窗外的雨。") shouldBe "zh"
        supertonicLanguage("Он медленно открыл дверь.") shouldBe "ru"
        supertonicLanguage("Він повільно відчинив двері, і їх скрип.") shouldBe "uk"
        supertonicLanguage("Η πόρτα άνοιξε αργά.") shouldBe "el"
        supertonicLanguage("فتح الباب ببطء.") shouldBe "ar"
        supertonicLanguage("उसने धीरे से दरवाज़ा खोला।") shouldBe "hi"
        supertonicLanguage("เขาเปิดประตูช้าๆ") shouldBe "th"
    }

    @Test
    fun `source languages become plain codes`() {
        SupertonicLanguages.fromSource("pt-BR") shouldBe "pt"
        SupertonicLanguages.fromSource("es-419") shouldBe "es"
        SupertonicLanguages.fromSource("all") shouldBe null
        SupertonicLanguages.fromSource("") shouldBe null
        SupertonicLanguages.supports("vi") shouldBe true
        SupertonicLanguages.supports("zh") shouldBe false
    }
}
