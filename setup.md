# Setup Guide - PlayerDataSync Reloaded

Dieses Dokument beschreibt die Einrichtung des Build-Prozesses, insbesondere für **Jenkins**.

## Voraussetzungen
- **Java JDK 25**: Das Projekt ist auf Java 25 ausgelegt (siehe `build.gradle.kts`).
- **Git**: Zum Klonen des Quellcodes.

---

## Jenkins Einrichtung (Freestyle-Projekt)

Um PlayerDataSync Reloaded automatisch mit Jenkins zu bauen, folge diesen Schritten:

### 1. Quellcode-Verwaltung
- Wähle **Git** aus.
- **Repository-URL**: `https://github.com/DerGamer009/PlayerDataSyncReloaded.git`
- **Branch-Verweise**: `*/master`

### 2. Build-Umgebung
- Stelle sicher, dass Jenkins Zugriff auf ein **JDK 25** hat.
- Du kannst dies unter *Jenkins verwalten > Tools* einrichten und im Job unter "Build-Umgebung" auswählen.

### 3. Build-Schritte
Füge den Build-Schritt **Gradle ausführen** hinzu (wie in deinem Screenshot zu sehen):

- **Invoke Gradle**: Auswählen.
- **Gradle-Version**: Wähle `(Default)` oder eine spezifische Installation.
- **Gradle-Wrapper verwenden**: **Aktivieren** (Empfohlen). Da das Repository die `gradlew` Dateien enthält, ist dies der stabilste Weg.
- **Tasks**: Gib hier die folgenden Befehle ein:
  ```bash
  clean :plugin:shadowJar
  ```
  > [!NOTE]
  > Der Task `:plugin:shadowJar` erstellt die fertige "Fat-JAR", die alle notwendigen Abhängigkeiten für den Minecraft-Server enthält.

**Beispiel Konfiguration:**
| Feld | Wert |
| :--- | :--- |
| **Gradle ausführen** | Hinzugefügt |
| **Gradle-Version** | (Default) |
| **Gradle-Wrapper verwenden** | [x] (Selektiert) |
| **Tasks** | `clean :plugin:shadowJar` |

### 4. Post-Build-Aktionen
Um die fertige `.jar` Datei nach dem Build zu speichern:
- Füge die Aktion **Artefakte archivieren** hinzu.
- **Zu archivierende Dateien**: `plugin/build/libs/PlayerDataSyncReloaded-*.jar`

---

## Manueller Build (Lokal)
Falls du das Projekt lokal ohne Jenkins bauen möchtest:

1. Repository klonen:
   ```bash
   git clone https://github.com/DerGamer009/PlayerDataSyncReloaded.git
   ```
2. Build ausführen:
   ```bash
   ./gradlew clean :plugin:shadowJar
   ```
3. Die Datei findest du unter: `plugin/build/libs/`
