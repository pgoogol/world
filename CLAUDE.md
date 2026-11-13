# CLAUDE.md

Symulacja świata NPC: postacie startują bez żadnej wiedzy, uczą się metodą prób i błędów i z czasem tworzą własny świat. Projekt rośnie etapami.

## Dokumenty

Przeczytaj je przed pierwszą zmianą w sesji:

- `ROADMAP.md`: wszystkie etapy, status i kryteria ukończenia.
- `docs/stage-1.md`, `docs/stage-2.md`: specyfikacje ukończonych etapów, parametry, kroki i wyniki odbioru.
- `docs/stage-3.md`: plan bieżącego etapu z krokami. `docs/stage-4.md`, `docs/stage-5.md`, `docs/stage-6.md`: plany wstępne kolejnych etapów, do doprecyzowania po odbiorze poprzedniego.
- `docs/ARCHITECTURE.md`: technologie, kontrakt gRPC i decyzje z uzasadnieniem.

## Jak pracujemy

- Bieżący krok to pierwszy nieodhaczony krok w specyfikacji bieżącego etapu (etapy 1 i 2 są ukończone, bieżący to etap 3). Rób jeden krok naraz i kończ go dopiero, gdy spełnia warunek „Gotowe, gdy”.
- Nie dokładaj niczego z późniejszych etapów (PyTorch, baza danych, Spring Boot, model językowy, Unity), dopóki `ROADMAP.md` ich nie odblokuje.
- Gdy specyfikacja czegoś nie rozstrzyga, zajrzyj do sekcji „Do decyzji” w specyfikacji etapu. Przyjmij wartość domyślną i opisz wybór w podsumowaniu kroku.
- Po każdym kroku uruchom testy obu części i odhacz krok. Nową decyzję albo zmianę kontraktu dopisz do `docs/ARCHITECTURE.md`.

## Zasady architektury

1. Świat (Java) jest sędzią: trzyma zasady, czas i zasoby oraz liczy nagrody. Mózgi (Python) dostają tylko to, co postać widzi, i odsyłają ruchy.
2. `world-core` nie zależy od JavaFX ani gRPC i działa bez okna. Start bez okna mieszka w `world-app`, okno w `world-view`.
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

Start serwera mózgów, w katalogu `brain/` (`--kind random` daje mózg losowy; Ctrl+C zatrzymuje):

```
uv run python -m brain.server
```

Świat bez okna przeciwko działającemu serwerowi, w katalogu głównym repo, po `.\mvnw.cmd verify`. `java` w PATH może wskazywać starsze JDK, dlatego przez `JAVA_HOME`:

```
& "$env:JAVA_HOME\bin\java" -jar world\world-app\target\world-app.jar --ticks 1000
```

Tryb treningu, w katalogu `brain/`. Skrypt sam startuje serwer mózgów, puszcza świat bez okna i zapisuje tablice. Statystyki lądują w `out/<wariant>.csv`, tablice w `brain/data/`. Każdy przebieg zaczyna od pustych tablic, chyba że dostanie `--resume`:

```
uv run python scripts/train.py --variant self
uv run python scripts/train.py --variant fixed
uv run python scripts/train.py --variant random
```

Pomiar wytrenowanych tablic bez uczenia (`out/<wariant>-eval.csv`) i porównanie wariantów:

```
uv run python scripts/train.py --variant self --evaluate --ticks 50000
uv run python scripts/summarize.py
```

Etap 2 (osada z pokoleniami), w katalogu `brain/`. `--sharing` wybiera mechanizmy wymiany wiedzy (`all`, `none`, `inherit`, `observe`, `map`), `--name` nazywa pliki wyjściowe i katalog tablic, `--episodes` zapisuje dziennik epizodów:

```
uv run python scripts/train.py --variant random --config ../config/stage-2.yaml --name s2-random --ticks 100000
uv run python scripts/train.py --variant self --config ../config/stage-2.yaml --name s2-all --sharing all --episodes
uv run python scripts/train.py --variant self --config ../config/stage-2.yaml --name s2-none --sharing none --episodes
uv run python scripts/generations.py --baseline s2-random --runs s2-all s2-none
```

Etap 3, osada ze scenariusza, w katalogu `brain/`. `--settlement` zapisuje `out/<nazwa>-settlement.csv`, a `winters.py` liczy z niego przeżycie zim:

```
uv run python scripts/train.py --variant scripted --config ../config/stage-3.yaml --name s3-scripted --settlement
uv run python scripts/train.py --variant scripted-nobuild --config ../config/stage-3.yaml --name s3-scripted-nobuild --settlement
uv run python scripts/winters.py --runs s3-scripted s3-scripted-nobuild
```

Tryb pokazu etapu 2: serwer w katalogu `brain/`, okno w katalogu głównym repo. W `config/stage-2.yaml` nikt się nie starzeje przez pierwsze 150 000 tur, więc narodziny zobaczysz dopiero po rozruchu albo z `--tps 400`:

```
uv run python -m brain.server --config ../config/stage-2.yaml --mode show --data-dir data/s2-all
```

```
& "$env:JAVA_HOME\bin\java" -jar world\world-view\target\world-view.jar --config config\stage-2.yaml
```

Tryb pokazu etapu 1: najpierw serwer z wytrenowanymi tablicami w katalogu `brain/`, potem okno w katalogu głównym repo. `--tps 50` przyspiesza pokaz, `--snapshot out\show.png` zapisuje zrzut okna:

```
uv run python -m brain.server --mode show
```

```
& "$env:JAVA_HOME\bin\java" -jar world\world-view\target\world-view.jar
```
