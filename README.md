# HopperFilter

Paper-Plugin (1.21.11): Lege fest, welche Items in einen Trichter, eine Kiste, ein Fass usw. hinein duerfen.

## Befehle (Alias `/hf`, Permission `hopperfilter.use`)
Schau dabei auf den Block:

| Befehl | Wirkung |
|---|---|
| `/hf set [item]` | Nur dieses Item erlauben (ohne Angabe: Item in der Hand) |
| `/hf add [item]` | Weiteres Item erlauben |
| `/hf remove [item]` | Item aus dem Filter entfernen |
| `/hf clear` | Filter loeschen |
| `/hf info` | Filter anzeigen |

Der Filter wird direkt im Block gespeichert (PersistentDataContainer) und bleibt nach Neustarts erhalten.
Doppelkisten werden unterstuetzt.

## Bauen
```
mvn package
```
Die Jar liegt dann in `target/`. Alternativ baut GitHub Actions sie bei jedem Push (Reiter *Actions* -> Artifact).
