# Datenübertragung & Migration - PlayerDataSync Reloaded

Dieses Dokument erklärt, wie du Daten zwischen verschiedenen Speicher-Backends migrierst oder von der alten PlayerDataSync-Version auf **Reloaded** umsteigst.

---

## 1. Zwischen Backends migrieren
Wenn du z.B. von **MySQL** zu **MongoDB** oder **PostgreSQL** wechseln möchtest, kannst du das integrierte Migrations-System nutzen.

### Vorbereitung
1. Stelle sicher, dass dein aktuelles Storage-System in der `config.yml` unter `storage:` korrekt konfiguriert ist.
2. Trage das **neue Ziel-System** unter `migration:` in der `config.yml` ein.

**Beispiel Konfiguration:**
```yaml
storage:
  type: "mysql"
  host: "localhost"
  # ... (Deine aktuelle Datenbank)

migration:
  type: "mongodb"
  connection_url: "mongodb://localhost:27017"
  database: "minecraft_reloaded"
```

### Befehl ausführen
Starte die Migration mit folgendem Befehl in der Konsole:
```bash
/pds migrate
```
Das Plugin kopiert nun alle vorhandenen Spielerdaten vom Quell-System in das Ziel-System. Der Fortschritt wird in der Konsole angezeigt.

---

## 2. Legacy Migration (Von Alt-Versionen)
Falls du von der ursprünglichen Version von PlayerDataSync kommst, musst du das Legacy-Mapping aktivieren.

1. Konfiguriere dein altes System unter `storage:`.
2. Konfiguriere dein neues Reloaded-System unter `migration:`.
3. Setze `migration.legacy: true` in der `config.yml`.
4. Führe `/pds migrate` aus.

> [!IMPORTANT]
> Die Legacy-Migration konvertiert alte Datenformate automatisch in das neue, komprimierte Reloaded-Format.

---

## 3. Backup System (Export/Import)
Alternativ zur direkten Datenbank-zu-Datenbank Migration kannst du Zip-Backups verwenden.

### Exportieren
Erstellt ein komprimiertes Backup aller Daten im Ordner `/plugins/PlayerDataSyncReloaded/backups/`.
```bash
/pds backup export <name>
```

### Importieren
Lädt die Daten aus einer Backup-Datei in das aktuell aktive Storage-System.
```bash
/pds backup import <name>
```

---

## Sicherheitshinweise
- **Backup erstellen**: Erstelle **IMMER** ein Backup deiner Datenbank, bevor du eine Migration startest.
- **Wartungsmodus**: Es wird empfohlen, während der Migration keine Spieler auf dem Netzwerk zu haben, um Dateninkonsistenzen zu vermeiden.
- **Konfiguration anpassen**: Nach erfolgreicher Migration musst du den `storage:` Block in deiner `config.yml` auf die neuen Zugangsdaten anpassen und den Server neustarten.
