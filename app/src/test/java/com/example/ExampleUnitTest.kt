package com.example

import com.example.nlp.ArabicNlpEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun rootConsonantGuard_preventsFalsePositiveOnNahlaVsRihla() {
        // نَحْلَة (Arı) vs رحلة (Yolculuk) — tek kök harf değişimi doğru sayılmamalı (< 0.65)
        val cmp = ArabicNlpEngine.compareArabic("نَحْلَةٌ", "رحلة")
        assertTrue("Root consonant guard should penalize ن -> ر below 0.65", cmp.score < 0.65)

        val tips = ArabicNlpEngine.analyzePhonemeAndMakhrajErrors("نَحْلَةٌ", "رحلة")
        assertTrue("Should produce Nûn-Râ makhraj tip", tips.isNotEmpty())
        assertTrue(tips.first().title.contains("ن ➔ ر"))
    }

    @Test
    fun collapseConsecutiveRepeats_deduplicatesEchoedWords() {
        // Öğrencinin "نحلة نحلة" demesi tekil "نحلة" ile tam eşleşmeli
        val cmp = ArabicNlpEngine.compareArabic("نَحْلَةٌ", "نحلة نحلة")
        assertTrue("Echoed repeat should match with high score", cmp.score >= 0.95)
    }

    @Test
    fun waznAndMedDistinction_separatesKitabVsKatabaAndQalaVsQul() {
        // 4 harfli isim (كِتَاب) -> 3 harfli fiil (كتب): Sarf Kalıbı / Vezin Farklılığı
        val tipsWazn = ArabicNlpEngine.analyzePhonemeAndMakhrajErrors("كِتَابٌ", "كتب")
        assertTrue(tipsWazn.any { it.title.contains("Sarf Kalıbı / Vezin Farklılığı") })

        // 3 harfli ecvef fiil (قَالَ) -> (قل): Med (Uzatma) Eksikliği
        val tipsMed = ArabicNlpEngine.analyzePhonemeAndMakhrajErrors("قَالَ", "قل")
        assertTrue(tipsMed.any { it.title.contains("Med (Uzatma) Eksikliği") })
    }

    @Test
    fun harakaGuide_countsVowelsCorrectly() {
        val guide = ArabicNlpEngine.buildHarakaGuide("مُعَلِّمٌ")
        assertTrue(guide.contains("Damme"))
        assertTrue(guide.contains("Fetha"))
        assertTrue(guide.contains("Şedde"))
    }

    @Test
    fun cefrEstimator_classifiesSimpleAndComplexItems() {
        assertEquals("A1", ArabicNlpEngine.estimateWordCefr("كِتَابٌ", "📘"))
        assertEquals("A1", ArabicNlpEngine.estimateSentenceCefr("السَّلَامُ عَلَيْكُمْ"))
        assertFalse(ArabicNlpEngine.estimateSentenceCefr("بِالرَّغْمِ مِنْ صُعُوبَةِ الِامْتِحَانِ حَضَرَ الْبَاحِثُونَ مُؤْتَمَراً عِلْمِيّاً حَوْلَ تَعْلِيمِ اللُّغَاتِ") == "A1")
    }
}
