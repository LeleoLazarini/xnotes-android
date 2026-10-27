package com.xnotes.platform

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * Text translation on the device with ML Kit: the source language is detected, each language pair's
 * model (about 30 MB per language) downloads once, then it works offline.
 */
object Translate {
    /** The targets offered, as (ML Kit code, label). */
    val targets = listOf(
        TranslateLanguage.ENGLISH to "Inglês",
        TranslateLanguage.PORTUGUESE to "Português",
        TranslateLanguage.SPANISH to "Espanhol",
        TranslateLanguage.FRENCH to "Francês",
        TranslateLanguage.ITALIAN to "Italiano",
        TranslateLanguage.GERMAN to "Alemão",
    )

    /** [text] in [target]; null when the language can't be told or the model can't be fetched. */
    suspend fun run(text: String, target: String): String? = runCatching {
        val detected = LanguageIdentification.getClient().identifyLanguage(text).await()
        val source = TranslateLanguage.fromLanguageTag(detected) ?: TranslateLanguage.PORTUGUESE
        if (source == target) return text
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        try {
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            client.translate(text).await()
        } finally {
            client.close()
        }
    }.getOrNull()
}

/** Arithmetic written by hand ("12 x 3 + 4 =") worked out: + - × ÷ ^ %, brackets, comma or point decimals. */
object HandMath {
    /** The value of [expr], or null when it isn't a sum this can read. */
    fun evaluate(expr: String): Double? {
        val s = expr.lowercase()
            .replace('×', '*').replace('x', '*').replace('÷', '/').replace(':', '/')
            .replace(',', '.').replace('−', '-').replace('–', '-')
            .substringBefore('=')
            .filter { it.isDigit() || it in "+-*/^%().".toSet() }
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        return runCatching { Parser(s).parse() }.getOrNull()?.takeIf { it.isFinite() }
    }

    /** [v] as people write it: no trailing zeros, comma decimals. */
    fun format(v: Double): String {
        val r = Math.round(v * 1e6) / 1e6
        return if (r == Math.floor(r) && kotlin.math.abs(r) < 1e15) r.toLong().toString() else r.toString().replace('.', ',')
    }

    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double { val v = sum(); if (i != s.length) error("left over"); return v }
        fun sum(): Double {
            var v = product()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) { val op = s[i++]; val r = product(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun product(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) { val op = s[i++]; val r = power(); v = if (op == '*') v * r else v / r }
            return v
        }
        fun power(): Double { val b = unary(); return if (i < s.length && s[i] == '^') { i++; Math.pow(b, power()) } else b }
        fun unary(): Double = if (i < s.length && s[i] == '-') { i++; -unary() } else postfix()
        fun postfix(): Double { var v = atom(); while (i < s.length && s[i] == '%') { i++; v /= 100 }; return v }
        fun atom(): Double {
            if (i < s.length && s[i] == '(') { i++; val v = sum(); if (i < s.length && s[i] == ')') i++; return v }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDouble()
        }
    }
}
