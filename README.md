# DroneWeather 🚁🌤️

[Version Française ci-dessous]

**DroneWeather** is a high-precision Android application specifically engineered for professional and hobbyist drone pilots. Unlike generic weather apps, it bridges the gap between atmospheric meteorology and space weather to ensure total flight safety.

## Why DroneWeather is Different?

Most drone weather apps provide basic estimates. DroneWeather stands out through scientific rigor:

- **The Orekit Engine 🛰️**: We don't just "estimate" satellite counts. DroneWeather integrates **Orekit**, a professional-grade space dynamics library (used by space agencies like CNES). It performs real-time orbital propagation using the latest TLE data to calculate exactly which satellites (GPS, GLONASS, Galileo, BeiDou) are visible from your position and—more importantly—which ones are likely to achieve a reliable navigation lock based on their elevation and current solar activity.
- **Advanced 7-Day Kp Index ☀️**: Standard solar forecasts often stop at 3 days or provide a single daily value. DroneWeather uses a **proprietary hybrid engine**:
    - **Days 1-3**: High-resolution 3-hourly data from NOAA.
    - **Days 4-7**: Predictive modeling based on the NOAA 27-day outlook, enhanced with a natural cyclic variation algorithm. This gives you a realistic, hour-by-hour estimation of geomagnetic activity for a full week, allowing for better mission planning.
- **UAV-Centric Wind Profiling**: Generic apps give you ground wind. We provide a **Wind Profile** from the ground up to 1500m (5000ft), crucial for understanding wind shear and battery consumption at flight altitude.
- **DJI RC2 Optimized**: Specifically designed and tested to run smoothly on dedicated controllers like the DJI RC2, with an interface that remains readable and responsive in the field.

## Key Features

- **Advanced Dashboard**: Instant safety status (Safe, Warning, Danger) with customizable thresholds.
- **Interactive Restrictions Map**: Direct integration with Geoportail (France) for flight zones.
- **Solar Wind Monitoring**: Real-time Speed, Density, and Bz component tracking.
- **Smart Pilot Tools**:
    - **Wind Compass**: Visualize wind direction relative to your phone's orientation.
    - **Pilot Checklist**: Customizable pre-flight safety steps.
    - **Detailed Forecast Table**: Hourly breakdown of all flight parameters.
- **Multilingual**: Native support for French, English, and Polish.

## Technical Stack

- **UI**: Jetpack Compose (Material 3).
- **Satellite Math**: Orekit (Space dynamics library).
- **Background Tasks**: WorkManager for periodic orbital (TLE) updates.
- **Networking**: Retrofit 2 / Gson.
- **Auto-Updates 🔄**: Integrated GitHub release monitoring for seamless app updates.
- **APIs**: Open-Meteo, NOAA (SWPC), GFZ Potsdam.

## Update System

DroneWeather includes a built-in update mechanism that checks for new releases on GitHub. 

- **How it works**: The app compares its local `versionCode` with the tag associated with the latest release on GitHub. 
- **User Permission**: For security, Android requires the user to grant permission to "Install Unknown Apps" for DroneWeather. If not granted, the app will automatically redirect you to the system settings during the first update attempt.
- **Reliability**: Downloads are performed via `externalCacheDir` to ensure compatibility with the Android Package Installer.

---

# DroneWeather 🚁🌤️ [Version Française]

**DroneWeather** est une application Android de haute précision conçue spécifiquement pour les pilotes de drones. Contrairement aux applications météo classiques, elle fait le pont entre la météorologie atmosphérique et la météo spatiale pour garantir une sécurité de vol totale.

## Pourquoi DroneWeather est différent ?

Là où la plupart des applications fournissent des estimations simplistes, DroneWeather se distingue par sa rigueur scientifique :

- **Le Moteur Orekit 🛰️** : Nous ne nous contentons pas d'estimer le nombre de satellites. DroneWeather intègre **Orekit**, une bibliothèque professionnelle de dynamique spatiale (utilisée par des agences comme le CNES). Elle effectue une propagation orbitale en temps réel via les dernières données TLE pour calculer exactement quels satellites (GPS, GLONASS, Galileo, BeiDou) sont visibles et, surtout, lesquels sont susceptibles d'être verrouillés pour la navigation selon leur élévation et l'activité solaire.
- **Indice Kp Avancé sur 7 Jours ☀️** : Les prévisions solaires s'arrêtent souvent à 3 jours ou donnent une valeur fixe par jour. DroneWeather utilise un **moteur hybride exclusif** :
    - **Jours 1-3** : Données haute résolution toutes les 3h de la NOAA.
    - **Jours 4-7** : Modélisation prédictive basée sur les perspectives à 27 jours de la NOAA, enrichie par un algorithme de variation cyclique naturelle. Cela vous donne une estimation réaliste, heure par heure, sur une semaine complète pour mieux planifier vos missions.
- **Profil de Vent Spécifique UAV** : Les applications génériques donnent le vent au sol. Nous fournissons un **Profil de Vent** du sol jusqu'à 1500m, indispensable pour anticiper le cisaillement du vent et la consommation batterie en altitude.
- **Optimisé DJI RC2** : Conçu et testé pour fonctionner parfaitement sur les radiocommandes comme la DJI RC2, avec une interface lisible et réactive sur le terrain.

## Caractéristiques Principales

- **Tableau de Bord Avancé** : Statut de sécurité instantané (Prêt, Attention, Danger) avec seuils personnalisables.
- **Carte des Restrictions** : Intégration directe de Geoportail (France) pour les zones de vol.
- **Vent Solaire** : Suivi en temps réel de la vitesse, densité et composante Bz.
- **Outils Pilote** :
    - **Boussole de Vent** : Direction du vent par rapport à l'orientation de l'appareil.
    - **Check-list** : Liste de vérification personnalisable avant le décollage.
    - **Tableau de Prévisions** : Détails horaires de tous les paramètres critiques.

## Pile Technique

- **Interface** : Jetpack Compose (Material 3).
- **Calculs Satellites** : Orekit.
- **Mises à jour Auto 🔄** : Système intégré de détection et d'installation des nouvelles versions via GitHub.
- **Tâches de fond** : WorkManager pour les mises à jour orbitales (TLE).
- **Sources de données** : Open-Meteo, NOAA (SWPC), GFZ Potsdam.

## Système de Mise à Jour

DroneWeather intègre son propre système de mise à jour pour vous garantir de toujours voler avec les derniers algorithmes de sécurité.

- **Fonctionnement** : L'application compare son `versionCode` local avec le dernier tag publié sur GitHub. Si une version supérieure est disponible, un badge apparaît dans l'onglet Aide.
- **Sécurité** : Pour installer une mise à jour, Android vous demandera l'autorisation d'installer des "applications de sources inconnues". DroneWeather vous redirigera automatiquement vers les paramètres système si nécessaire.
- **Signature** : Notez que si vous installez une version via Android Studio (version de debug), elle ne pourra pas être mise à jour par une version téléchargée sur GitHub (version de release) sans une désinstallation préalable, à cause des règles de sécurité de signature d'Android.

## License / Licence

Copyright (C) 2026 Mick - Licensed under the **GNU GPL v3**.
