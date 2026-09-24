# FileDao

## Ruolo e metodi

DAO Room per `files`. `insert` aggiunge una riga e ne restituisce l'id; `delete` la rimuove; `getById` recupera i metadati. `search(tipo, query)` cerca un nome parziale nel tipo richiesto; `getAllByTipo(tipo)` restituisce la categoria. Entrambe ordinano dal più recente.

## Collegamenti

[FilesDatabase](FilesDatabase.md) lo espone; [FileRepository](../../../manager/repository/FileRepository.md) lo usa come indice del file cifrato.
