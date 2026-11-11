# CLAUDE.md

Symulacja świata NPC: postacie startują bez żadnej wiedzy, uczą się metodą prób i błędów i z czasem tworzą własny świat. Projekt rośnie etapami.

## Dokumenty

Przeczytaj je przed pierwszą zmianą w sesji:

- `ROADMAP.md`: wszystkie etapy, status i kryteria ukończenia.
- `docs/stage-1.md`: specyfikacja bieżącego etapu, parametry i lista kroków.
- `docs/ARCHITECTURE.md`: technologie, kontrakt gRPC i decyzje z uzasadnieniem.

## Jak pracujemy

- Bieżący krok to pierwszy nieodhaczony krok w `docs/stage-1.md`. Rób jeden krok naraz i kończ go dopiero, gdy spełnia warunek „Gotowe, gdy”.
- Nie dokładaj niczego z późniejszych etapów (PyTorch, baza danych, Spring Boot, model językowy, Unity), dopóki `ROADMAP.md` ich nie odblokuje.
- Gdy specyfikacja czegoś nie rozstrzyga, zajrzyj do sekcji „Do decyzji” w specyfikacji etapu. Przyjmij wartość domyślną i opisz wybór w podsumowaniu kroku.
- Po każdym kroku uruchom testy obu części i odhacz krok. Nową decyzję albo zmianę kontraktu dopisz do `docs/ARCHITECTURE.md`.

## Zasady architektury

1. Świat (Java) jest sędzią: trzyma zasady, czas i zasoby oraz liczy nagrody. Mózgi (Python) dostają tylko to, co postać widzi, i odsyłają ruchy.
2. `world-core` nie zależy od JavaFX ani gRPC i działa bez okna.
3. Zegar należy do świata: jedna tura to jedno wywołanie `Brain.Step` za całą grupę.
4. Kontrakt między procesami żyje tylko w `proto/brain.proto`. Kod z proto generuje build albo skrypt; nie edytuj go ręcznie.
5. Każde losowanie w Javie i Pythonie bierze ziarno z konfiguracji. Ten sam seed daje ten sam przebieg co do tury.
6. Parametry świata i uczenia trzymaj w konfiguracji, nie w kodzie.
7. Widok tylko czyta stan świata. Nie zmienia go i nie liczy niczego, co wpływa na symulację.

## Konwencje

- Java 25, Maven z modułami, pakiety `com.pgoogol.npcworld.*`. Globalne reguły z `~/.claude/` obowiązują dalej; ten plik dodaje reguły projektu.
- Python 3.14, zależności przez uv w `brain/pyproject.toml`.
- Testy: JUnit 5 i AssertJ w Javie, pytest w Pythonie. Każda zasada świata ma własny test.
- Kod, identyfikatory i komentarze w kodzie po angielsku, dokumentacja po polsku.

## Komendy

Sprawdzone w kroku 1.1 w PowerShellu. Java wymaga JDK 25 w `JAVA_HOME`; Mavena i Pythona 3.14 pobierają wrapper i uv.

Build i testy Javy (generuje też klasy z proto), w katalogu `world/`:

```
.\mvnw.cmd verify
```

Testy jednego modułu, np. `world-core`, razem z modułami, od których zależy:

```
.\mvnw.cmd -pl world-core -am verify
```

Python, w katalogu `brain/`. Kod z proto generuj po każdej zmianie `proto/brain.proto` i po świeżym klonie:

```
uv sync
uv run python scripts/gen_proto.py
uv run pytest
```

Start serwera mózgów, tryb treningu i tryb pokazu dopisują kroki, które je tworzą (1.2, 1.6, 1.7).
