# Workflow — Ajout d'une imprimante (admin)

Ce document décrit le fonctionnement **actuel** du flux d'ajout d'une imprimante depuis la page d'administration, du clic sur "Ajouter une imprimante" jusqu'au clic final sur "Ajouter" dans le formulaire, pour les deux types d'imprimante (THERMAL et A4/réseau).

Sources : `printer-list.component.ts`, `printer-form.component.ts`, `PrinterController.java`, `PrinterService.java`, `PrintQueueService.java`, `PrinterBridgeClient.java`.

```mermaid
sequenceDiagram
    actor Admin
    participant List as PrinterListComponent (front)
    participant Form as PrinterFormComponent (dialog, front)
    participant Ctrl as PrinterController (back)
    participant Svc as PrinterService (back)
    participant Queue as PrintQueueService (back)
    participant Bridge as PrinterBridgeClient (back)
    participant PB as PrinterBridge (service externe)
    participant DB as Base de données

    Admin->>List: Clique "Ajouter une imprimante"
    List->>Ctrl: GET /admin/printers/discovered
    Ctrl->>Svc: discover()
    Svc->>Bridge: discover()
    Bridge->>PB: GET /printers
    PB-->>Bridge: imprimantes connues (id, nom, type, status)
    Bridge-->>Svc: liste brute
    Svc->>Svc: exclut les imprimantes déjà enregistrées ou ignorées
    Svc-->>Ctrl: DiscoveredPrinterDto[]
    Ctrl-->>List: 200 OK

    List->>Form: ouvre le dialogue (liste détectée, erreur éventuelle)

    alt Découverte en échec (PrinterBridge inaccessible)
        Form-->>Admin: bandeau d'avertissement, pas de formulaire
    else Liste reçue
        Form-->>Admin: tableau des imprimantes détectées (nom + statut déjà connu)
        Admin->>Form: clique "Enregistrer" sur une ligne
        Form->>Form: selectRow() -> printerBridgeId, selectedPrinter
        Form->>Form: applySelectedPrinter() -> selectedType = THERMAL ou A4

        alt Type THERMAL
            Form-->>Admin: formulaire = nom + largeur papier (57/80mm, requis)
        else Type A4 / réseau
            Form-->>Admin: formulaire = nom uniquement
        end

        Admin->>Form: saisit le nom (+ largeur si THERMAL), clique "Ajouter"
        Form->>Form: onSubmit() — vérifie que le formulaire est valide
        Form->>Ctrl: POST /admin/printers { name, type, printerBridgeId, widthMm? }
        Ctrl->>Svc: create(dto)
        Svc->>Svc: validateConfiguration(dto)

        alt Config invalide (THERMAL sans largeur 57/80)
            Svc-->>Ctrl: 422 InvalidPrinterConfigurationException
            Ctrl-->>Form: erreur
            Form-->>Admin: message d'erreur inline, dialogue reste ouvert
        else Config valide
            Svc->>Svc: mapper.toEntity(dto)
            Svc->>DB: save(printer)

            alt Nom déjà utilisé
                DB-->>Svc: DataIntegrityViolationException
                Svc-->>Ctrl: 422 InvalidPrinterConfigurationException
                Ctrl-->>Form: erreur
                Form-->>Admin: message d'erreur inline
            else Sauvegarde OK
                DB-->>Svc: imprimante persistée (id)
                Svc->>Queue: registerPrinter(printer)
                Queue->>Queue: createHandle() — choix du checker selon le type
                Note over Queue,PB: Appel réseau synchrone et bloquant,<br/>dans la même requête HTTP<br/>(jusqu'à 2s connexion + 10s lecture)
                Queue->>Bridge: checkStatus(printerBridgeId)
                Bridge->>PB: GET /printers/{id}/status
                PB-->>Bridge: status ONLINE / OFFLINE / UNKNOWN
                Bridge-->>Queue: status

                alt Status OFFLINE
                    Queue->>Queue: handle.setLastError(...) + log warning
                else Status ONLINE / UNKNOWN
                    Queue->>Queue: rien (pas d'erreur enregistrée)
                end

                Queue->>Queue: handle.start() (thread consommateur dédié)
                Queue-->>Svc: retour
                Svc-->>Ctrl: 201 Created (PrinterDto)
                Ctrl-->>Form: 201
                Form->>Form: dialogRef.close()
                Form-->>List: ref.closed déclenché
                List->>Ctrl: GET /admin/printers + GET /admin/printers/ignored
                List-->>Admin: liste rafraîchie, nouvelle imprimante visible
            end
        end
    end
```

## Points clés du flux actuel

- **Un seul chemin de code pour THERMAL et A4** : la différence entre les deux types se limite au champ `widthMm` (affiché/requis seulement pour THERMAL) côté formulaire, et au choix du `PrinterConnectivityChecker` (`ThermalPrinterConnectivityChecker` vs `NetworkPrinterConnectivityChecker`) côté backend — les deux délèguent à `PrinterBridgeClient.checkStatus()`.
- **Le statut est récupéré deux fois** : une première fois lors de `discover()` (déjà présent dans `DiscoveredPrinterDto.status`, jamais transmis au `create()`), une seconde fois de façon bloquante lors de `registerPrinter()` au moment de l'enregistrement — c'est ce second appel qui pénalise le temps de réponse du clic final sur "Ajouter" (voir note dans le diagramme).
- **L'échec de la vérification de connectivité n'empêche pas la création** : si `checkStatus()` renvoie `OFFLINE` ou lève une exception applicative, l'imprimante est enregistrée quand même (seul `lastError` est renseigné sur le handle en mémoire) — seule une erreur de validation (422) ou un nom en doublon bloque réellement la création.
