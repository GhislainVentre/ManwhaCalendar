# Manhwa Calendar

Application Android qui suit les dates de sortie de vos manhwa.

## Fonctionnalités

- **Recherche** de séries via l'API publique [MangaDex](https://api.mangadex.org/docs/) (filtre « manhwa coréen » activé par défaut).
- **Mes séries** : liste des séries suivies avec le dernier chapitre traduit et sa date.
- **Calendrier** : chapitres sortis cette semaine, puis prochaines sorties estimées jour par jour.
- **Notifications** : une vérification en arrière-plan toutes les 6 heures prévient quand un nouveau chapitre sort.
- Langue des chapitres au choix : français, anglais, ou les deux.

## Comment la date de sortie est estimée

MangaDex ne publie pas de date de sortie future. L'application regarde les dates des derniers chapitres
traduits, calcule le rythme habituel (médiane des derniers intervalles, en regroupant les mises en ligne
groupées) et en déduit la prochaine date probable. Si la série a plus de deux sorties de retard, elle est
indiquée « en pause ou rythme irrégulier ».

Les dates correspondent aux traductions disponibles sur MangaDex, pas forcément à la sortie coréenne
d'origine. Les séries sous licence officielle (Webtoon, Tapas…) peuvent ne pas avoir de chapitres sur MangaDex.

## Installer l'APK

1. Ouvrir l'onglet **Actions** du dépôt, puis la dernière exécution réussie de **Build APK**.
2. Télécharger l'artefact `ManhwaCalendar-apk` (un zip contenant `ManhwaCalendar.apk`).
3. Copier l'APK sur le téléphone et l'ouvrir (autoriser l'installation depuis des sources inconnues).

Android 8.0 ou plus récent est requis.

## Construire en local

```bash
./gradlew assembleRelease   # APK dans app/build/outputs/apk/release/
./gradlew testDebugUnitTest # tests unitaires
```

Nécessite le JDK 17 et le SDK Android (API 35).
