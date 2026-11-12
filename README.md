# world

Symulacja świata NPC. Grupa postaci startuje bez żadnej wiedzy i uczy się metodą prób i błędów. Z czasem postacie dzielą się wiedzą, budują osady i poprawiają własne zasady, a osady handlują i rywalizują ze sobą.

| Część | Technologie |
|---|---|
| Świat i widok | Java 25, JavaFX 25, Maven |
| Mózgi postaci | Python 3.14, uv |
| Połączenie | gRPC, Protocol Buffers |

**Status:** etapy 1 (mała osada) i 2 (wspólna wiedza) ukończone. Wytrenowane postacie żyją 6,5 razy dłużej niż losowe, a osada przekazuje wiedzę kolejnym pokoleniom. Następny jest etap 3 (tworzenie świata).

## Dokumenty

- [ROADMAP.md](ROADMAP.md): etapy i kryteria ukończenia
- [docs/stage-1.md](docs/stage-1.md), [docs/stage-2.md](docs/stage-2.md): specyfikacje etapów, kroki i wyniki
- [docs/stage-3.md](docs/stage-3.md): plan etapu 3; [docs/stage-4.md](docs/stage-4.md), [docs/stage-5.md](docs/stage-5.md), [docs/stage-6.md](docs/stage-6.md): plany wstępne kolejnych etapów
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): technologie, kontrakt gRPC, decyzje
- [CLAUDE.md](CLAUDE.md): instrukcje dla Claude Code
