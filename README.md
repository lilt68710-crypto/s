# Witherfang (Fabric, Minecraft 26.3)

Setup: needs JDK 25. In this folder run `gradle wrapper --gradle-version 9.6.0`, then `./gradlew runClient`
(or `./gradlew build` -> build/libs/witherfang-1.0.0.jar). Bump versions in gradle.properties to match
https://fabricmc.net/develop/template/ if needed.

Controls: right-click = fire wither skull | sneak + right-click = summon 4 wither skeletons.
Recipe: SKS / KNK / _B_  (S wither skull, K netherite ingot, N nether star, B blaze rod)
Tuning constants are at the top of WitherfangEvents.java.

## Build with GitHub (no local setup)
1. Create a free GitHub repo and upload everything in this folder (including the hidden .github folder).
2. Open the repo's Actions tab, wait for "Build mod" to finish (or click Run workflow).
3. Open the finished run, download the "witherfang-jar" artifact, unzip it, and use witherfang-1.0.0.jar (not the -sources one).
