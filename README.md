# GPT-Android-Miroir-3-TZZ1AC0

[![Android CI](https://github.com/TZZ1ac0/GPT-Android-Miroir-3-TZZ1AC0/actions/workflows/android-ci.yml/badge.svg)](https://github.com/TZZ1ac0/GPT-Android-Miroir-3-TZZ1AC0/actions/workflows/android-ci.yml)
[![Latest release](https://img.shields.io/github/v/release/TZZ1ac0/GPT-Android-Miroir-3-TZZ1AC0)](https://github.com/TZZ1ac0/GPT-Android-Miroir-3-TZZ1AC0/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Une application Android native évolutive, construite avec **Kotlin** et **Jetpack Compose**.

## Fonctionnalités

- Aperçu de la caméra avant avec exposition automatique et champ de vision intégral.
- Serveur RTSP activable depuis le menu pour diffuser la caméra avant sur le réseau local.
- Configuration du port RTSP (8554 par défaut) et affichage de l'URL ainsi que de la commande client VLC.
- Menu à trois points avec accès à la caméra, aux commandes RTSP et à la fermeture.
- Intégration continue GitHub Actions : compilation et tests unitaires.
- Versionnement SemVer, commits conventionnels et journal des modifications.
- Publication d'une GitHub Release et d'un APK installable après fusion sur `main`.

## Utiliser le flux RTSP

1. Connecter le téléphone et le client (par exemple VLC) au même réseau local.
2. Dans le menu, ouvrir **RTSP → Configurer RTSP** pour vérifier le port et l'adresse proposés.
3. Choisir **RTSP → Activer RTSP**. L'aperçu passe à la caméra utilisée par le serveur afin que le flux et l'image affichée proviennent de la même capture.
4. Dans VLC, ouvrir l'URL affichée, au format `rtsp://ADRESSE_IP_DU_TÉLÉPHONE:8554/`.
5. Choisir **RTSP → Désactiver RTSP** pour arrêter le serveur et revenir à l'aperçu CameraX.

Le serveur est désactivé au lancement de l'application. Le flux est destiné au réseau local et reste actif tant que l'écran de l'application conserve le serveur actif. Si l'adresse IP ou le réseau change, vérifier l'URL dans la boîte de configuration.

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

**Pour chaque publication, augmente explicitement `VERSION_NAME` dans la PR** et mets à jour `CHANGELOG.md`. La fusion sur `main` déclenche la compilation, les tests, la création du tag `vX.Y.Z`, la GitHub Release et le téléversement de l'APK installable. Si la version existe déjà pour un autre commit, le workflow échoue au lieu de remplacer silencieusement la version publiée. Une relance est autorisée si le tag existant pointe exactement vers le commit courant.

Consulter [CHANGELOG.md](CHANGELOG.md) et la page [Releases](https://github.com/TZZ1ac0/GPT-Android-Miroir-3-TZZ1AC0/releases).

## Licence

Distribué sous licence MIT. Voir [LICENSE](LICENSE).
