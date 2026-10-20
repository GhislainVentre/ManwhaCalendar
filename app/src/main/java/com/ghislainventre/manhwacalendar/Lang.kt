package com.ghislainventre.manhwacalendar

import java.util.Locale

/** L'appli parle français sur un téléphone réglé en français, anglais partout ailleurs. */
val isFrenchUi: Boolean get() = Locale.getDefault().language == "fr"

/** Langue des dates affichées, assortie aux textes. */
val uiLocale: Locale get() = if (isFrenchUi) Locale.FRENCH else Locale.ENGLISH

/** Texte affiché dans la langue du téléphone. */
fun tr(fr: String, en: String): String = if (isFrenchUi) fr else en
