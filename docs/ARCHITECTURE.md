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
|   world-view: okno JavaFX   |     StepResponse     |                             |
+-----------------------------+                      +-----------------------------+
```

`StepRequest` niesie to, co widzi każda postać, jej stan i nagrodę za poprzedni ruch. `StepResponse` niesie ruch każdej postaci.

## Struktura repo

```
.
├── proto/brain.proto      kontrakt świat ↔ mózgi
├── world/                 Java 25, Maven (parent POM)
│   ├── world-core/        plansza, postacie, zasady, tury, nagrody
│   ├── world-brain/       klient gRPC
│   └── world-view/        okno JavaFX
├── brain/                 Python 3.14, uv
│   └── src/brain/         serwer gRPC i Q-learning
├── config/                parametry etapów: seed, plansza, uczenie
└── docs/
```

## Technologie etapu 1

| Obszar | Wybór | Dlaczego |
|---|---|---|
| Świat | JDK 25 (LTS) | rekordy, sealed interfaces i `switch` z dopasowaniem wzorców do opisu akcji i zdarzeń; para z JavaFX 25 |
| Widok | JavaFX 25: `Canvas`, `AnimationTimer`, `LineChart` | okno na pulpicie bez strony WWW, uruchamiane przez `javafx-maven-plugin` |
| Build Javy | Maven, trzy moduły | `world-core` bez zależności od widoku i sieci |
| Testy Javy | JUnit 5, AssertJ | test dla każdej zasady świata |
| Mózgi | Python 3.14 | biblioteki mają gotowe paczki dla tej wersji |
| Środowisko Pythona | uv, `pyproject.toml` | jedno narzędzie zamiast pip, venv i pyenv |
| Uczenie | Q-learning w słowniku, NumPy | działa na CPU, bez karty graficznej |
| Testy Pythona | pytest | |
| Komunikacja | gRPC i Protocol Buffers: grpc-java, `grpcio`, `grpcio-tools` | klasy dla Javy, Pythona i później C# z jednego pliku |

### Tryby uruchomienia

- **Trening:** świat bez okna, pełna prędkość, statystyki do CSV.
- **Pokaz:** okno JavaFX, około 10 tur na sekundę.

Oba tryby korzystają z tego samego `world-core`.

## Kontrakt gRPC (szkic)

Ostateczną wersję ustala krok 1.1. Nowe pola dostają nowe numery, a numerów usuniętych pól nie używamy ponownie.

```proto
syntax = "proto3";

package npcworld.brain.v1;

option java_package = "com.pgoogol.npcworld.brain.proto";
option java_multiple_files = true;

service Brain {
  // One call per tick, for the whole group of NPCs.
  rpc Step(StepRequest) returns (StepResponse);
}

message StepRequest {
  int64 tick = 1;
  repeated NpcView npcs = 2;
}

message NpcView {
  int32 npc_id = 1;
  repeated Cell surroundings = 2;  // 3x3 around the NPC, row by row; the centre is the NPC's own cell
  int32 hunger = 3;                // 0-100
  int32 thirst = 4;                // 0-100
  int32 energy = 5;                // 0-100
  double reward = 6;               // reward for the previous action, computed by the world
  bool died = 7;                   // the previous action ended in death; the view already shows the respawned NPC
  bool truncated = 8;              // life limit reached: episode ends without a penalty
}

message StepResponse {
  repeated NpcAction actions = 1;
}

message NpcAction {
  int32 npc_id = 1;
  Action action = 2;
}

enum Action {
  ACTION_STAY = 0;
  ACTION_UP = 1;
  ACTION_DOWN = 2;
  ACTION_LEFT = 3;
  ACTION_RIGHT = 4;
  ACTION_CONSUME = 5;  // eat or drink on the current cell
}

enum Cell {
  CELL_EMPTY = 0;
  CELL_FOOD = 1;
  CELL_WATER = 2;
  CELL_LAVA = 3;
  CELL_WALL = 4;
}
```

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
