package it.vantaggi.scoreboardessential.utils

import java.text.Normalizer
import java.util.Locale

private val segni = Regex("\\p{Mn}+")

/** Il testo senza accenti e in minuscolo, per le ricerche: «Nicolò» si trova scrivendo «nicolo». */
fun testoPiano(testo: String): String = segni.replace(Normalizer.normalize(testo, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
