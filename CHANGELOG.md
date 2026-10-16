# Changelog

Les changements notables du projet sont documentés ici. Le format suit les principes de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le versionnement [SemVer](https://semver.org/lang/fr/).

## [Non publié]

### À venir

## [0.4.12] - 2026-10-16

### Diagnostic
- Journalise les paramètres d'exposition demandés dans la requête Camera2 (mode de contrôle, mode/verrouillage AE, compensation, plage FPS, temps d'exposition et ISO) en parallèle des valeurs réellement rapportées par le capteur.

## [0.4.11] - 2026-10-16

### Corrigé
- Attache le callback de résultats à la requête répétitive existante de RootEncoder, sans créer de requête concurrente ni accéder aux surfaces internes.
- Conserve la requête et ses cibles d'origine, tout en journalisant les résultats réels du capteur.

## [0.4.10] - 2026-10-16

### Corrigé
- Corrige l'accès aux surfaces cibles de la requête Camera2 de diagnostic en les lisant depuis le builder RootEncoder.

## [0.4.9] - 2026-10-16

### Corrigé
- Isole la requête Camera2 de diagnostic de celle de RootEncoder : le test de compensation AE ne modifie plus son builder interne.

### Diagnostic
- Journalise le nombre de paramètres de requête copiés et ceux qui n'ont pas pu l'être.

## [0.4.8] - 2026-10-15

### Diagnostic
- Journalise les valeurs AE réellement rapportées par les résultats Camera2 (mode, verrouillage et compensation), en plus de l'état AE et des paramètres du capteur.
- Augmente la fréquence d'échantillonnage pendant le test de compensation afin d'observer la réponse du capteur avant la restauration.

## [0.4.7] - 2026-10-15

### Diagnostic
- Journalise la plage et le pas de compensation AE pris en charge par Camera2.
- Teste temporairement une compensation d'environ +2 EV pendant le flux RTSP, journalise les paramètres demandés et les résultats du capteur, puis restaure la compensation initiale.

## [0.4.6] - 2026-10-15

### Corrigé
- Corrige la constante Camera2 utilisée pour journaliser la durée maximale d'image annoncée par le capteur.

### Diagnostic
- Conserve les journaux des paramètres d'exposition demandés et des capacités du capteur.

## [0.4.5] - 2026-10-15

### Diagnostic
- Journalise les paramètres Camera2 demandés (modes de contrôle/AE, verrouillage AE, plage FPS et éventuelles valeurs manuelles d'exposition/ISO/durée d'image).
- Journalise les plages de temps d'exposition, de sensibilité et de durée d'image annoncées par le matériel pour comparer les demandes aux résultats du capteur.

## [0.4.4] - 2026-10-15

### Diagnostic
- Journalise les résultats Camera2 réels pendant le flux RTSP : état AE, temps d'exposition du capteur, sensibilité ISO et durée d'image.
- Ajoute ce diagnostic via le callback de capture de la session RootEncoder, sans confondre acceptation de requête et réponse effective du capteur.

## [0.4.3] - 2026-10-15

### Diagnostic
- Journalise le résultat des tentatives d'activation de l'exposition automatique et les modes AE annoncés par Camera2.
- Échantillonne la luminance moyenne des images YUV de la caméra pour vérifier si elle varie avec l'éclairage pendant le flux RTSP.

## [0.4.2] - 2026-10-15

### Corrigé
- Active l'exposition automatique une fois la session de capture Camera2 prête, avec de nouvelles tentatives si la caméra n'est pas encore disponible.

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
- CI GitHub Actions, badges, documentation et automatisation des releases.
