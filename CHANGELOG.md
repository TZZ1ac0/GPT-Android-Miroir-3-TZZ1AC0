# Changelog

Les changements notables du projet sont documentés ici. Le format suit les principes de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le versionnement [SemVer](https://semver.org/lang/fr/).

## [Non publié]

### À venir

## [0.4.3] - 2026-10-17

### Corrigé
- Affiche l'aperçu de la caméra avant dès son activation en utilisant un `TextureView` compatible avec le rendu Compose, au lieu de l'ancien aperçu basé sur `OpenGlView`.
- Incrémente le `versionCode` Android afin que cette version puisse être distinguée des versions précédentes lors de l'installation et des mises à jour.

## [0.4.2] - 2026-10-16

### Modifié
- Partage la même source CameraX entre l'aperçu affiché sur le téléphone et le flux RTSP, afin de conserver une exposition cohérente.
- Prépare l'encodeur audio silencieux requis par RootEncoder, tout en exposant un flux RTSP vidéo uniquement.

## [0.4.1] - 2026-10-15

### Corrigé
- Utilise l'orientation calculée par Android pour l'aperçu et l'encodage RTSP afin d'éviter une image tournée de 90°.

## [0.4.0] - 2026-10-15

### Ajouté
- Serveur RTSP activable depuis le menu pour diffuser la caméra avant avec encodage matériel H.264.
- Sous-menu RTSP avec activation/désactivation selon l'état du serveur et dialogue de configuration du port (8554 par défaut).
- Affichage de l'adresse du flux et de la commande client VLC dans la configuration RTSP.

## [0.3.0] - 2026-10-14

### Ajouté
- Aperçu de la caméra avant via CameraX, avec exposition automatique et affichage intégral du champ de vision.

### Corrigé
- Corrige les dépendances Gradle et la déclaration de permission caméra.
- Utilise une nouvelle version de publication afin d'éviter le conflit avec le tag v0.2.0 déjà publié.

## [0.2.0] - 2026-10-14

### Ajouté
- Barre de titre permanente « Miroir Android. ».
- Menu à trois points verticaux avec l'action « Quitter ».
- Publication de l'APK installable dans les artefacts de la GitHub Release.

## [0.1.0] - 2026-10-14

### Ajouté
- Fondation Android Kotlin / Jetpack Compose.
- Écran « Hello World! » et tests unitaires.
