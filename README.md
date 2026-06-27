DroneWeather 🚁🌤️
[Version Française ci-dessous]
DroneWeather is a comprehensive Android application designed for drone pilots. It provides critical real-time and forecast data to ensure safe flight conditions. Optimized for both standard mobile devices and dedicated controllers like the DJI RC2.
Key Features
•
Advanced Weather Dashboard: Instant overview of flight safety with a color-coded status (Safe, Warning, Danger).
•
Multi-Altitude Wind Profiles: Wind speed and gusts predicted from ground level up to 1500m.
•
7-Day Kp Index Forecast: Combines high-resolution 3-day forecasts with long-term 27-day outlooks from NOAA.
•
Satellite Prediction Engine: Powered by Orekit to predict available and locked satellites (GPS, GLONASS, Galileo, BeiDou) based on your exact location.
•
Interactive Map: Direct integration with Geoportail (France) for drone flight restrictions.
•
Solar Wind Monitoring: Real-time data on Solar Wind Speed, Density, and Bz component.
•
Smart Tools:
◦
Wind Compass: Visualize wind direction relative to your phone's orientation.
◦
Pilot Checklist: Customizable pre-flight safety checklist.
◦
Forecast Table: Detailed hourly breakdown for the next 7 days.
•
Multilingual: Native support for French, English, and Polish.
Technical Stack
•
UI: Jetpack Compose (Material 3).
•
Navigation: State-driven single activity architecture.
•
Location: Google Play Services Location & Geocoding.
•
Satellite Math: Orekit (Space dynamics library).
•
Database: Room for caching weather and satellite data.
•
Background Tasks: WorkManager for periodic TLE and forecast updates.
•
Networking: Retrofit 2 with Gson.
•
APIs: Open-Meteo, NOAA (SWPC), GFZ Potsdam.
DroneWeather 🚁🌤️ [Version Française]
DroneWeather est une application Android complète conçue pour les pilotes de drones. Elle fournit des données critiques en temps réel et des prévisions pour garantir des conditions de vol optimales. L'interface est optimisée pour les smartphones standard ainsi que pour les radiocommandes dédiées comme la DJI RC2.
Caractéristiques Principales
•
Tableau de Bord Météo Avancé : Aperçu instantané de la sécurité du vol avec un code couleur intuitif (Prêt, Attention, Danger).
•
Profils de Vent Multi-Altitudes : Vitesse du vent et rafales prévues du sol jusqu'à 1500m.
•
Prévisions de l'Indice Kp sur 7 Jours : Système hybride combinant les prévisions haute résolution de la NOAA à 3 jours avec les perspectives à 27 jours.
•
Moteur de Prédiction Satellite : Utilise la bibliothèque scientifique Orekit pour prédire les satellites visibles et verrouillés (GPS, GLONASS, Galileo, BeiDou) selon votre position.
•
Carte Interactive : Intégration directe de Geoportail (France) pour visualiser les zones de restrictions de vol.
•
Surveillance du Vent Solaire : Données en temps réel sur la vitesse du vent solaire, sa densité et la composante Bz.
•
Outils Intelligents :
◦
Boussole de Vent : Visualisez la direction du vent par rapport à l'orientation réelle de votre appareil.
◦
Check-list Pilote : Liste de vérification de sécurité avant vol entièrement personnalisable.
◦
Tableau de Prévisions : Détails horaires complets (Temp, Vent, Kp, Pluie) pour les 7 prochains jours.
•
Multilingue : Support natif du Français, de l'Anglais et du Polonais.
Pile Technique
•
Interface : Jetpack Compose (Material 3).
•
Navigation : Architecture à activité unique pilotée par l'état (State-driven).
•
Localisation : Google Play Services Location & Geocoding.
•
Calculs Satellites : Orekit (Bibliothèque de dynamique spatiale).
•
Base de données : Room pour la mise en cache et le fonctionnement hors-ligne.
•
Tâches de fond : WorkManager pour la mise à jour automatique des données TLE et des prévisions.
•
Communication : Retrofit 2 avec conversion Gson.
•
Sources de données : Open-Meteo, NOAA (SWPC), GFZ Potsdam.
License / Licence
Copyright (C) 2026 Mick Licensed under the GNU GPL v3.
