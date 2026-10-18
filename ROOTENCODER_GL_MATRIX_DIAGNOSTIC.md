# Diagnostic temporaire RootEncoder — matrice GL caméra

Cette branche instrumente RootEncoder 2.6.1 pour observer la matrice `SurfaceTexture` consommée par le rendu GL. La modification est temporaire et ne change pas le protocole RTSP.

## Mécanisme de vendoring

Les sources de RootEncoder 2.6.1 sont **suivies par Git** dans `third_party/RootEncoder-2.6.1/`. Le projet utilise ces sources locales par défaut ; Gradle ne télécharge ni ne modifie de sources pendant la configuration. Seul `encoder/.../CameraRender.java` reçoit l'instrumentation `GL_FRAME`, ajoutée immédiatement après `surfaceTexture.getTransformMatrix(STMatrix)`.

Le répertoire conservé contient les modules Gradle nécessaires à RootEncoder, leur code source, les ressources et la licence amont. Les pages de documentation générées, l'application de démonstration et les fichiers de CI du dépôt amont ne sont pas nécessaires pour compiler cette application et ont été retirés de la copie vendored afin de limiter le bruit dans la PR.

La licence amont est conservée dans `third_party/RootEncoder-2.6.1/LICENSE.txt`. Les sources viennent du tag officiel `2.6.1` ; les versions de plugins du catalogue local ont été ajustées pour correspondre à celles du projet Android.

## Vérification

1. Synchroniser Gradle et compiler l'application.
2. Lancer l'application sur l'appareil et ouvrir Logcat.
3. Filtrer avec `CameraPreviewDiag`.
4. Vérifier les lignes `GL_FRAME`, qui contiennent le thread, l'horodatage, la taille et les 16 valeurs de la matrice.
5. La compilation CI ne remplace pas la vérification sur appareil : confirmer que les lignes apparaissent bien au démarrage de la caméra.

## Désactivation / retour à la dépendance publiée

Par défaut, `rootEncoderGlMatrixDiagnostic` est activé. Pour utiliser la dépendance Maven publiée à la place des sources locales, lancer les tâches Gradle avec :

```bash
./gradlew -ProotEncoderGlMatrixDiagnostic=false testDebugUnitTest assembleDebug
```

Cette propriété empêche l'inclusion des modules RootEncoder locaux et la substitution de dépendance, puis `app` utilise `com.github.pedroSG94.RootEncoder:library:2.6.1`. Une connexion réseau et la résolution normale des dépendances Maven/JitPack sont alors nécessaires. Pour supprimer entièrement le diagnostic de cette branche, revenir à la dépendance publiée et retirer les modifications de `CameraRender.java`.

La matrice observée est la transformation de `SurfaceTexture` utilisée par `CameraRender`. Elle ne prouve pas à elle seule l'orientation finale affichée ou encodée, car d'autres rotations et inversions sont appliquées ensuite dans le pipeline.
