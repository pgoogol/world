# Architektura

## Obraz całości

Projekt działa jako dwa procesy na jednym komputerze:

- **Świat (Java)** trzyma zasady, czas i zasoby oraz liczy nagrody. Działa bez okna, więc przyspieszysz go do treningu. Okno JavaFX tylko pokazuje stan świata.
- **Mózgi (Python)** trzymają pamięć postaci i uczą się na wynikach ruchów.

Procesy łączy gRPC na `localhost:50051`. Mózgi są serwerem. Świat jest klientem i trzyma zegar: w każdej turze wysyła jedno zapytanie za całą grupę i czeka na ruchy.

```
+-----------------------------+     StepRequest      +-----------------------------+
| Świat (Java)                | -------------------> | Mózgi (Python)              |
|   world-core: zasady, czas  |                      |   serwer gRPC               |
|   world-brain: klient gRPC  | <------------------- |   Q-learning dla postaci    |
|   world-app: start, config  |     StepResponse     |                             |
|   world-view: okno JavaFX   |                      |                             |
+-----------------------------+                      +-----------------------------+
```

`StepRequest` niesie to, co widzi każda postać, jej stan i nagrodę za poprzedni ruch. `StepResponse` niesie ruch każdej postaci.

## Struktura repo

```
.
├── proto/brain.proto      kontrakt świat ↔ mózgi
├── world/                 Java 25, Maven (parent POM, Maven Wrapper)
│   ├── world-core/        plansza, postacie, zasady, tury, nagrody
│   ├── world-brain/       klient gRPC
│   ├── world-app/         start bez okna: konfiguracja, pętla symulacji, statystyki
│   └── world-view/        okno JavaFX
├── brain/                 Python 3.14, uv
│   ├── src/brain/         serwer gRPC i Q-learning
│   ├── scripts/           generowanie kodu z proto
│   └── tests/
├── config/                parametry etapów: seed, plansza, uczenie
└── docs/
```

## Technologie etapu 1

| Obszar | Wybór | Dlaczego |
|---|---|---|
| Świat | JDK 25 (LTS) | rekordy, sealed interfaces i `switch` z dopasowaniem wzorców do opisu akcji i zdarzeń; para z JavaFX 25 |
| Widok | JavaFX 25: `Canvas`, `AnimationTimer`, `LineChart` | okno na pulpicie bez strony WWW |
| Build Javy | Maven, cztery moduły | `world-core` bez zależności od widoku i sieci |
| Konfiguracja | YAML: SnakeYAML w `world-app`, PyYAML w mózgach | jeden plik `config/stage-1.yaml` dla obu procesów |
| Testy Javy | JUnit 5 (5.14), AssertJ | test dla każdej zasady świata |
| Mózgi | Python 3.14 | biblioteki mają gotowe paczki dla tej wersji |
| Środowisko Pythona | uv, `pyproject.toml` | jedno narzędzie zamiast pip, venv i pyenv |
| Uczenie | Q-learning w słowniku, czysty Python | działa na CPU, bez karty graficznej |
| Testy Pythona | pytest | |
| Komunikacja | gRPC i Protocol Buffers: grpc-java, `grpcio`, `grpcio-tools` | klasy dla Javy, Pythona i później C# z jednego pliku |

### Tryby uruchomienia

- **Trening:** świat bez okna, pełna prędkość, statystyki do CSV.
- **Pokaz:** okno JavaFX, około 10 tur na sekundę.

Oba tryby korzystają z tego samego `world-core`.

## Kontrakt gRPC

Kontrakt żyje tylko w [proto/brain.proto](../proto/brain.proto). Krok 1.1 ustalił jego pierwszą wersję. Nowe pola dostają nowe numery, a numerów usuniętych pól nie używamy ponownie.

- `Brain.Step(StepRequest) → StepResponse`: jedno wywołanie na turę za całą grupę.
- `StepRequest`: numer tury i lista `NpcView`.
- `NpcView`: `npc_id`, bieżąca `Observation`, nagroda za poprzedni ruch, flagi `died` i `truncated` oraz `final_observation`.
- `Observation`: 9 pól wokół postaci wierszami (środek to pole pod nią), głód, pragnienie i energia w skali 0–100. Świat wysyła surowe wartości, koduje je mózg.
- `StepResponse`: lista `NpcAction` (`npc_id`, `Action`).

Dwie reguły, o których łatwo zapomnieć:

- Po śmierci albo ucięciu epizodu `observation` pokazuje już postać po resecie. Przy `truncated` świat dosyła w `final_observation` stan sprzed resetu, bo z niego mózg liczy cel Q. Po śmierci tego pola nie ma: cel Q nie zawiera wtedy wartości następnego stanu.
- Zero w enumach `Action` i `Cell` znaczy „nie ustawiono”. Świat traktuje `ACTION_UNSPECIFIED` jako błąd mózgu, a mózg tak samo `CELL_UNSPECIFIED`.

### Generowanie kodu

| Język | Jak | Gdzie ląduje |
|---|---|---|
| Java | `protobuf-maven-plugin` w `world-brain`, przy każdym buildzie | `world/world-brain/target/generated-sources/`, pakiet `com.pgoogol.npcworld.brain.proto` |
| Python | `brain/scripts/gen_proto.py`, uruchamiany ręcznie po zmianie proto | `brain/src/brain/proto/`, import `from brain.proto import brain_pb2` |

Wygenerowanego kodu nie commitujemy w żadnym języku.

## Decyzje

1. **Świat w Javie, mózgi w Pythonie.** Świat działa bez okna i przyspiesza do treningu, a biblioteki uczenia maszynowego są najlepsze w Pythonie. Ten sam podział stosuje Unity ML-Agents: gra w C#, trening w Pythonie.
2. **gRPC zamiast socketu z JSON-em.** Jeden plik proto generuje klasy dla Javy, Pythona i później C#, więc kontrakt nie rozjedzie się między językami.
3. **Świat jest sędzią.** Zasady, fizyka i nagrody żyją w Javie. Mózgi ich nie widzą, więc w etapie 4 nie podkręcą sobie wyników.
4. **JDK 25 i JavaFX 25.** JavaFX 25 powstał pod JDK 25. JavaFX 27 też ruszy na JDK 25, ale jego twórcy tej pary nie testują.
5. **Python 3.14, nie 3.15.** Wersja 3.15 wyszła 9 października 2026 i część bibliotek może jeszcze nie mieć dla niej paczek.
6. **Maven, nie Gradle.** Gradle nic tu nie dodaje.
7. **Osobne repo.** Projekt nie korzysta ze Spring Boota ani frontendu WWW, za to dochodzi Python i aplikacja na pulpit.
8. **Bez silnika gry na start.** Unreal odpada, bo wymaga mocnej karty graficznej, a projekt ma działać na CPU. Wariant z Unity i ML-Agents zamiast Javy też odpadł, bo świat zamknięty w silniku trudniej przyspieszyć do treningu. Unity 6 wejdzie w etapie 5 jako widok 3D, klient tego samego świata.
9. **Uczenie od zera plus model językowy.** Ciało postaci uczy się od zera. Od etapu 4 plany, reguły i rozmowy przejmuje model językowy, który zna ludzką mowę, a o świecie wie tylko to, co postać przeżyła. Uczenie od zera wystarcza na przetrwanie, współpracę i prosty handel, ale sojusze i prawa wymagają języka. W Project Sid (Altera) postacie w Minecrafcie głosowały nad podatkami i szerzyły religię, a każdą napędzał model językowy.
10. **Model językowy lokalnie i rzadko.** LM Studio udostępnia lokalny serwer zgodny z API OpenAI. Na CPU model odpowiada wolno, więc decyduje raz na dzień albo sezon symulacji. Częstsze decyzje wymagałyby API.
11. **Reguły jako dane, nie przepisywany kod.** Osada zmienia reguły zapisane w JSON-ie. Świat testuje zmianę na kopii stanu i zostawia ją tylko przy lepszym wyniku. Program przepisujący własny kod łatwo psuje sam siebie albo uczy się oszukiwać metrykę.
12. **Widok tylko czyta stan świata.** Dzięki temu JavaFX wymienisz na Unity bez przepisywania symulacji.
13. **Etap 1 bez Spring Boota, bazy, Dockera i Kafki.** Etap 1 nie ma serwera WWW ani danych do trwałego przechowywania. Spring Boot, PostgreSQL i Flyway wchodzą w etapie 3.
14. **Samostrojenie żyje w mózgu.** Postać liczy epsilon i alpha z własnych liczników odwiedzin, które mózg trzyma obok tablicy Q. Kontrakt się przez to nie zmienia, a świat nie wie, jak mózg się uczy.
15. **Maven Wrapper zamiast globalnego Mavena.** `world/mvnw` pobiera przypiętą wersję Mavena, więc build wymaga tylko JDK 25 w `JAVA_HOME`.
16. **Wtyczka `io.github.ascopes:protobuf-maven-plugin`.** Sama pobiera `protoc` i wtyczkę gRPC z Maven Central, więc nie instalujemy ich na komputerze. Starszej `org.xolstice` nikt już nie rozwija.
17. **Protobuf w Javie przypięty do wersji `protoc`.** grpc-java ciągnie domyślnie protobuf-java 3.x, a kod generujemy przez `protoc` 4.x. `protobuf-bom` w parent POM wyrównuje obie wersje.
18. **Kod Pythona z proto jako podpakiet `brain.proto`.** Skrypt mapuje `proto/` na wirtualną ścieżkę `brain/proto`, dzięki czemu wygenerowane moduły importują się nawzajem przez pakiet, bez poprawiania importów po generowaniu.
19. **Obserwacja końcowa i zero „nie ustawiono” w kontrakcie.** Bez `final_observation` mózg nie policzy celu Q po ucięciu epizodu. Zero zarezerwowane w enumach odróżnia zgubiony ruch od świadomego „stój”.
20. **Czwarty moduł `world-app`.** Start bez okna potrzebuje świata, klienta gRPC, czytania YAML-a i statystyk, ale nie JavaFX. `world-core` dostaje gotowy `WorldConfig` i nie zna formatu pliku. `world-view` zależy od `world-app`.
21. **Świat rozmawia z mózgami przez port `Brain` w `world-core`.** `GrpcBrain` w `world-brain` jest jedną z implementacji, testy świata podstawiają własną. Odpowiedź bez ruchu dla postaci, z ruchem zdublowanym albo z `ACTION_UNSPECIFIED` kończy turę wyjątkiem.
22. **Uruchamialne jary zamiast `javafx-maven-plugin`.** `maven-jar-plugin` wpisuje klasę startową i classpath do manifestu, a `maven-dependency-plugin` kopiuje zależności do `target/lib`. Program startuje przez `java -jar` bez instalowania modułów w lokalnym repozytorium Mavena.
23. **Tura 0 oznacza nowy przebieg.** Serwer mózgów żyje dłużej niż świat. Gdy dostaje `tick = 0`, mózgi porzucają niedokończone przejście z poprzedniego przebiegu, a wiedzę zostawiają.
24. **Każda postać losuje z własnego strumienia.** Ziarno mózgu postaci to `seed * 1 000 003 + npc_id`, więc jej ruchy nie zależą od kolejności ani liczby postaci w zapytaniu. Serwer obsługuje zapytania jednym wątkiem.
25. **Plansza 20×20 liczy się razem ze ścianą.** W środku zostaje 18×18 pól, a udziały jedzenia, wody i lawy liczymy od tych 324 pól.
26. **Kolejność w turze.** Każda faza obejmuje wszystkie postacie, zanim ruszy następna: (1) ruch albo jedzenie i picie, (2) wzrost głodu i pragnienia, (3) utrata energii za potrzeby od progu i za lawę, (4) zegar idzie o jeden i odrasta jedzenie, (5) rozliczenie: śmierć przy zerowej energii albo ucięcie epizodu na limicie życia, potem nagroda. Gdy dwie postacie jedzą to samo pole w jednej turze, dostaje je ta o niższym numerze.
27. **Śmierć zastępuje nagrodę za turę.** W turze śmierci postać dostaje tylko karę, bez plusa za przeżycie i za jedzenie. Ucięcie epizodu zostawia zwykłą nagrodę.
28. **Plansza z seeda, spójna bez lawy.** Generator losuje układ do skutku, aż wszystkie pola poza lawą i ścianą tworzą jedną całość. Odrodzenie losuje pole z tej całości.
29. **Regeneracja energii jako parametr, domyślnie wyłączona.** `energy_regen: 0` zachowuje zasadę ze specyfikacji: energia tylko spada. Wartość dodatnia przywraca energię w turach bez strat.
30. **Świat zgłasza koniec życia zdarzeniem.** `WorldListener` dostaje `LifeEnded` z długością życia i przyczyną. Statystyki i widok słuchają, a nie zaglądają do stanu postaci.
31. **Tablica Q bez NumPy.** Klucz to 11 bajtów (9 pól, poziom głodu, poziom pragnienia), wartość to lista sześciu ocen. Przy sześciu liczbach zwykła lista jest szybsza od tablicy NumPy, więc NumPy wejdzie dopiero z kodem, który go potrzebuje.
32. **Poziomy potrzeb z tych samych progów co zasady świata.** Granice 50 i 80 w `brain.need_levels` pokrywają się z progiem nagrody za jedzenie i progiem utraty energii, więc postać rozróżnia dokładnie te stany, które świat traktuje inaczej. Energia nie wchodzi do klucza.
33. **Remisy w ocenach rozstrzyga losowanie z ziarna postaci.** Świeża tablica ma same zera i bez losowania postać zawsze wybierałaby „stój”.
34. **Tablice w jednym pliku pickle na wariant uczenia.** `brain/data/qtables-<wariant>.pkl`, zapis przez plik tymczasowy i podmianę. Serwer wczytuje plik przy starcie, chyba że dostanie `--fresh`. Tryb `--mode show` niczego nie zapisuje i nie uczy.
35. **Liczniki samostrojenia.** Epsilon bierze liczbę wcześniejszych odwiedzin sytuacji, więc pierwsza wizyta daje 1. Alpha bierze liczbę prób pary sytuacja–akcja razem z bieżącą, więc pierwsza aktualizacja przyjmuje cały cel. Liczniki zapisują się razem z tablicą Q. W trybie pokazu mózg ich nie rusza.
36. **Statystyki życia w oknach po 1000 tur.** Życie liczy się w oknie, w którym się skończyło, śmiercią albo limitem. CSV ma kolumny `tick,lives_ended,mean_life_length`, kropkę dziesiętną i puste pole średniej w oknie bez zakończonych żyć.
37. **Trening jednym skryptem.** `brain/scripts/train.py` startuje serwer mózgów w swoim procesie, uruchamia świat jako podproces i po udanym przebiegu zapisuje tablice. Przebieg zaczyna od pustych tablic, więc ten sam seed daje ten sam CSV. `--evaluate` mierzy zapisane tablice bez uczenia i bez zapisu.
38. **Miara „wytrenowanych postaci”.** `scripts/summarize.py` bierze średnią długość żyć zakończonych w ostatnich 50 oknach treningu i dzieli ją przez średnią mózgu losowego z całego przebiegu. Turę przekroczenia progu wyznacza średnia krocząca z 20 okien, bo jedno okno mieści tylko kilka żyć.
39. **Symulacja w trybie pokazu na własnym wątku.** `SimulationRunner` w `world-app` odlicza tury w stałym tempie i po każdej publikuje niezmienną migawkę `WorldSnapshot`. `AnimationTimer` w oknie rysuje ostatnią migawkę i nie steruje zegarem. Gdy mózg zawiedzie, pętla staje, a okno pokazuje przyczynę.
40. **Okno jako zwykły jar.** Klasa startowa `ShowMain` nie dziedziczy po `Application`, więc JavaFX działa z classpath i okno rusza przez `java -jar`. Opcja `--snapshot` zapisuje zrzut okna do PNG własnym, krótkim koderem, bez zależności `javafx-swing`.

## Co dochodzi później

| Etap | Technologia | Po co |
|---|---|---|
| 2–3 | PyTorch | sieć zamiast tablic Q, w etapie 3 PPO dla opóźnionych nagród |
| 3 | Spring Boot, PostgreSQL, Flyway | historia świata, zapis i wczytywanie stanu |
| 4 | LM Studio, serwer zgodny z API OpenAI | propozycje zmian reguł z uzasadnieniem |
| 5 | Unity 6 | widok 3D wielu osad |
| 6 | GPU w chmurze, w razie potrzeby | trening self-play, gdy CPU nie nadąży |

## Źródła

- [JavaFX 25 Release Notes (Oracle)](https://docs.oracle.com/en/java/java-components/javafx/25/release-notes/javafx-release-notes.pdf)
- [OpenJFX: minimalne JDK dla JavaFX 27](https://mail.openjdk.org/pipermail/openjfx-dev/2026-January/059108.html)
- [Python 3.15.0](https://python.org/downloads/release/python-3150/)
- [Unity ML-Agents](https://github.com/Unity-Technologies/ml-agents)
- [Project Sid: Many-agent simulations toward AI civilization](https://arxiv.org/abs/2411.00114)
