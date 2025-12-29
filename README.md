# Simulation des capteurs IoT distribués avec Synchronisation Lamport

## Description
Ce projet simule un **système distribué de capteurs IoT** utilisant l’**algorithme de Lamport** pour garantir la synchronisation des événements et l’accès ordonné aux ressources partagées. Plusieurs clients peuvent consulter les données de capteurs en temps réel via un **dashboard JavaFX**, tout en respectant l’ordre logique des événements.

## Objectifs
- Simuler un environnement IoT distribué avec plusieurs capteurs et clients.
- Assurer la synchronisation des événements à l’aide de l’algorithme de Lamport.
- Permettre un accès ordonné à la **section critique** pour l’écriture/lecture des données.
- Visualiser en temps réel les données sur un **dashboard interactif**.
- Gérer la communication via **sockets TCP** et la concurrence avec **threads Java**.

## Architecture
Le système se compose de trois principaux composants :  
1. **Capteurs** : Génèrent des données simulées (température, humidité, pression) et envoient des messages avec un horodatage Lamport au broker.  
2. **Broker (Serveur)** : Reçoit et centralise les messages, applique Lamport pour gérer l’accès à la section critique, et redistribue les données aux clients.  
3. **Clients** : Reçoivent et affichent les données synchronisées en temps réel sur le dashboard JavaFX.

### Flux de données
- Capteurs → Broker → Clients
- Chaque composant fonctionne sur un **thread indépendant**.
- Les clients affichent les données avec les horloges logiques Lamport pour conserver l’ordre causal des événements.

## Fonctionnalités
- Simulation des capteurs IoT et génération de données en temps réel.  
- Synchronisation des événements distribués avec l’algorithme de Lamport.  
- Gestion de l’accès concurrent aux ressources critiques.  
- Dashboard interactif JavaFX pour visualiser les données et l’état des processus.  
- Logs détaillés pour suivre l’ordre des événements et les accès à la section critique.

## Technologies utilisées
- **Java** : Backend et logique de l’algorithme Lamport.  
- **JavaFX** : Interface graphique interactive pour le dashboard.  
- **Sockets TCP** : Communication fiable entre composants.  
- **Threads Java** : Gestion de la concurrence et exécution simultanée des processus.  
- **Gson** : Sérialisation et désérialisation JSON pour l’échange de données.

## Installation et exécution

# Compiler le projet
javac -cp ".;lib/gson-2.10.1.jar" *.java

# Lancer le serveur (broker)
java -cp ".;lib/gson-2.10.1.jar" IoTLamportDashboard

# Lancer les clients pour visualiser les données en temps réel





