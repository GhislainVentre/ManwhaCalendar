# Manhwa Calendar

Application Android qui suit les dates de sortie de vos manhwa.

## Fonctionnalités

- **Recherche sur plusieurs sites à la fois** : MangaDex (API publique) et des sites de lecture en anglais
  (ToonGod, ManhwaTop, MangaRead, Manhuaus, ManhwaClan). Les résultats s'affichent site par site dès
  qu'ils arrivent. Le menu ⋮ → *Sites de recherche* permet d'activer, retirer ou ajouter un site
  (tout site WordPress au thème « Madara »). Ces sites sont souvent protégés par Cloudflare :
  l'application les lit dans un navigateur intégré invisible, ce qui prend quelques secondes.
- **Mes séries** : liste des séries suivies avec le dernier chapitre traduit et sa date.
- **Calendrier** : chapitres sortis cette semaine, puis prochaines sorties estimées jour par jour.
- **Notifications** : une vérification en arrière-plan toutes les 6 heures prévient quand un nouveau chapitre sort.
- Langue des chapitres au choix : français, anglais, ou les deux.

## Comment la date de sortie est estimée

Aucune des deux sources ne publie de date de sortie future. L'application regarde les dates des derniers chapitres
mis en ligne, calcule le rythme habituel (médiane des derniers intervalles, en regroupant les mises en ligne
groupées) et en déduit la prochaine date probable. Si la série a plus de deux sorties de retard, elle est
indiquée « en pause ou rythme irrégulier ».

Les dates correspondent aux traductions disponibles sur MangaDex, pas forcément à la sortie coréenne
d'origine. Les séries sous licence officielle (Webtoon, Tapas…) peuvent ne pas avoir de chapitres sur MangaDex.

## Installer l'APK

Depuis le téléphone, télécharger `ManhwaCalendar.apk` dans la release
[latest](../../releases/latest) puis l'ouvrir (autoriser l'installation depuis des sources inconnues).

Chaque exécution de **Build APK** produit aussi l'artefact `ManhwaCalendar-apk` dans l'onglet **Actions**.

Android 8.0 ou plus récent est requis.

## Construire en local

```bash
./gradlew assembleRelease   # APK dans app/build/outputs/apk/release/
./gradlew testDebugUnitTest # tests unitaires
```

Nécessite le JDK 17 et le SDK Android (API 35).
