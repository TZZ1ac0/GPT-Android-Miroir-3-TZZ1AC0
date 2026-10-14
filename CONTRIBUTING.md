# Contribuer

## Flux de travail

1. Créer une branche dédiée : `feat/...`, `fix/...`, `test/...` ou `docs/...`.
2. Réaliser une modification ciblée et ajouter/adapter les tests.
3. Utiliser des commits [Conventional Commits](https://www.conventionalcommits.org/).
4. Ouvrir une pull request vers `main` avec le but, les changements et les vérifications.
5. Attendre la réussite de la CI, puis demander la revue fonctionnelle. La fusion est réservée au mainteneur.
6. Après fusion, le workflow de publication crée un tag et une release si la version de `gradle.properties` n'a pas encore été publiée.

## Vérifications locales

Avec JDK 17 et Gradle 8.9 :

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

## Versionnement

Suivre SemVer : correctif pour les corrections compatibles, mineure pour les nouvelles fonctionnalités compatibles, majeure pour les changements incompatibles. Mettre à jour `versionName` et le changelog dans la même pull request.
