# GPT-Android-Miroir-3-TZZ1AC0

[![Android CI](https://github.com/TZZ1ac0/GPT-Android-Miroir-3-TZZ1AC0/actions/workflows/android-ci.yml/badge.svg)](https://github.com/TZZ1ac0/GPT-Android-Miroir-3-TZZ1AC0/actions/workflows/android-ci.yml)
[![Latest release](https://img.shields.io/github/v/release/TZZ1ac0/GPT-Android-Miroir-3-TZZ1AC0)](https://github.com/TZZ1ac0/GPT-Android-Miroir-3-TZZ1AC0/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Une application Android native évolutive, construite avec **Kotlin** et **Jetpack Compose**.

## Fonctionnalités

- Écran d'accueil Compose minimaliste.
- Fonction de salutation isolée et couverte par des tests unitaires.
- Intégration continue GitHub Actions : compilation et tests unitaires.
- Versionnement SemVer, commits conventionnels et journal des modifications.
- Publication d'une GitHub Release et d'un APK installable après fusion sur `main`.

## Prérequis

- Android Studio récent avec Android SDK.
- JDK 17.
- Gradle 8.9 (ou le wrapper Gradle fourni/généré par Android Studio).

## Démarrer

1. Cloner le dépôt et ouvrir le projet dans Android Studio.
2. Laisser Android Studio synchroniser les fichiers Gradle.
3. Lancer la configuration `app` sur un émulateur ou un appareil Android.

En ligne de commande, avec Gradle 8.9 installé :

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

L'APK de débogage est généré dans `app/build/outputs/apk/debug/`.

## Qualité et contribution

Chaque fonctionnalité doit être développée sur une branche dédiée et proposée par pull request. La CI doit réussir avant la revue fonctionnelle et la fusion. Voir [CONTRIBUTING.md](CONTRIBUTING.md).

Les messages de commit suivent [Conventional Commits](https://www.conventionalcommits.org/) : par exemple `feat: add welcome screen`, `fix: correct greeting` ou `test: cover greeting helper`.

## Versions et releases

Le projet suit [Semantic Versioning](https://semver.org/lang/fr/). La version de l'application est déclarée dans `VERSION_NAME` de `gradle.properties`.

**Pour chaque publication, augmente explicitement `VERSION_NAME` dans la PR** (par exemple `0.2.0` vers `0.3.0`) et mets à jour `CHANGELOG.md`. La fusion sur `main` déclenche la compilation, les tests, la création du tag `vX.Y.Z`, la GitHub Release et le téléversement de l'APK installable. Si la version existe déjà pour un autre commit, le workflow échoue au lieu de remplacer silencieusement la version publiée. Une relance est autorisée si le tag existant pointe exactement vers le commit courant.

Consulter [CHANGELOG.md](CHANGELOG.md) et la page [Releases](https://github.com/TZZ1ac0/GPT-Android-Miroir-3-TZZ1AC0/releases).

## Licence

Distribué sous licence MIT. Voir [LICENSE](LICENSE).
