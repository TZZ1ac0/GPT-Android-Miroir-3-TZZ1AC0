# Diagnostic temporaire RootEncoder — matrice GL caméra

Cette branche active une instrumentation de diagnostic de RootEncoder 2.6.1 sans modifier la dépendance déclarée dans `app/build.gradle.kts`.

## Fonctionnement

Lors de la configuration Gradle, `settings.gradle.kts` télécharge les sources officielles du tag RootEncoder `2.6.1` dans `.gradle/rootencoder-gl-diagnostic/`, puis ajoute une instrumentation à `encoder/.../CameraRender.java`. Gradle inclut ensuite le build local et substitue `com.github.pedroSG94.RootEncoder:library` par le module local `:library`.

La ligne `GL_FRAME` est journalisée dans `CameraRender.draw()`, immédiatement après `surfaceTexture.getTransformMatrix(STMatrix)`. Dans ce chemin de RootEncoder, `updateTexImage()` a déjà été appelé pour la frame courante. Les 120 premières frames comprennent le thread, l'horodatage, la taille et les 16 valeurs de la matrice fournie au shader.

## Vérification

1. Ouvrir cette branche dans Android Studio et synchroniser Gradle. Une connexion réseau est nécessaire au premier téléchargement des sources RootEncoder.
2. Lancer l'application et ouvrir Logcat.
3. Filtrer avec `CameraPreviewDiag`.
4. Vérifier la présence de lignes `GL_FRAME` avec `timestampNs`, `size` et `surfaceTextureMatrix`.

L'instrumentation n'est pas considérée comme validée tant que la compilation et l'apparition de ces lignes n'ont pas été confirmées sur l'appareil.

## Désactivation / retour arrière

Pour revenir à la dépendance publiée sans modifier les fichiers, exécuter Gradle avec `-ProotEncoderGlDiagnostic=false`. Pour désactiver par défaut dans cette branche, retirer `rootEncoderGlDiagnostic=true` de `gradle.properties`. La source téléchargée reste dans le cache Gradle local et n'est pas suivie par Git.

La matrice observée est la transformation de `SurfaceTexture` utilisée par `CameraRender`. Elle ne prouve pas à elle seule l'orientation finale affichée ou encodée, car d'autres rotations et inversions sont appliquées ensuite dans le pipeline.
