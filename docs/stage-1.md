# Etap 1: Mała osada

## Cel

Pięć postaci uczy się przetrwać na planszy, zaczynając od losowych ruchów. Postęp widać w oknie i na wykresie średniej długości życia.

## Świat (`world-core`)

- Plansza 20×20 otoczona ścianą. Typy pól: puste, jedzenie, woda, lawa, ściana.
- Jedzenie znika po zjedzeniu i odrasta po kilkudziesięciu turach. Woda się nie wyczerpuje.
- Każda postać ma głód, pragnienie i energię w skali 0–100. Głód i pragnienie rosną co turę.
- Energia spada za każdą potrzebę na poziomie progu lub wyżej oraz za każdą turę na lawie.
- Akcje: stój, góra, dół, lewo, prawo, jedz/pij. Jedzenie i picie działa na polu, na którym postać stoi. Ruch w ścianę zostawia postać w miejscu.
- Przy energii 0 postać umiera. Odradza się na losowym polu bez lawy i ściany, z pełną energią oraz zerowym głodem i pragnieniem. Mózg postaci, czyli jej pamięć, zostaje ten sam.
- Limit życia: po 2000 turach epizod kończy się bez kary. Dla uczenia to ucięcie epizodu, nie śmierć.
- Postacie mogą stać na jednym polu i nie blokują się nawzajem.
- Nagrodę liczy świat: mały plus za każdą przeżytą turę, plus za jedzenie lub picie w potrzebie, duży minus za śmierć.
- Losowanie planszy i odrodzeń korzysta z ziarna z konfiguracji.

## Mózgi (`brain`)

- Każda postać ma własną tablicę Q w słowniku: klucz to zakodowana obserwacja, wartość to oceny sześciu akcji. Tablica rośnie tylko o sytuacje, które postać spotkała.
- Obserwację koduje mózg: typy 9 pól wokół postaci (środek to pole pod nią) oraz głód i pragnienie w trzech poziomach (niski, średni, wysoki). Świat wysyła surowe wartości.
- Wybór akcji: epsilon-greedy. Epsilon zaczyna od 1.0, czyli od samych losowych ruchów, i z czasem spada.
- Aktualizacja: Q-learning. Po śmierci mózg liczy cel bez wartości następnego stanu, po ucięciu epizodu z nią.
- Mózg zapisuje tablice na dysk co kilka tysięcy tur i wczytuje je przy starcie.
- Mózg losowy z tym samym interfejsem służy do porównań.

## Parametry startowe

Wartości do strojenia, trzymane w konfiguracji.

| Parametr | Wartość |
|---|---|
| Plansza | 20×20, ściana dookoła |
| Postacie | 5 |
| Udział pól jedzenia / wody / lawy | 8% / 5% / 5% |
| Odrastanie jedzenia | 50 tur |
| Głód i pragnienie | +1 na turę, maksimum 100 |
| Próg potrzeby | od 80 energia spada o 1 na turę za każdą potrzebę |
| Lawa | −25 energii za turę |
| Jedzenie / picie | −40 głodu / −40 pragnienia |
| Limit życia | 2000 tur |
| Nagrody | +0.1 za turę, +1 za jedzenie lub picie przy potrzebie od 50, −10 za śmierć |
| Q-learning | alpha 0.1, gamma 0.95 |
| Epsilon | od 1.0 do 0.05, liniowo przez 200 000 tur |
| Zapis tablic Q | co 5000 tur |
| Seed | 42 |

## Kroki

- [ ] **1.1 Szkielet repo.** Maven w `world/` z modułami `world-core`, `world-brain`, `world-view`. Projekt Pythona w `brain/` z uv. Plik `proto/brain.proto` według szkicu z [ARCHITECTURE.md](ARCHITECTURE.md) i generowanie kodu w obu językach. Plik `.gitignore`.

  *Gotowe, gdy:* build Javy i `pytest` przechodzą na pustych testach, a sekcja „Komendy” w `CLAUDE.md` ma działające polecenia.

- [ ] **1.2 Połączenie.** Serwer mózgów odpowiada losowymi ruchami. Pętla świata bez okna woła `Step` co turę dla 5 postaci na pustej planszy. Testy Javy używają podstawionego mózgu (gRPC in-process).

  *Gotowe, gdy:* świat z prawdziwym serwerem w Pythonie przechodzi 1000 tur bez błędu.

- [ ] **1.3 Zasady świata.** Pola, odrastanie jedzenia, potrzeby, energia, lawa, śmierć, odrodzenie, limit życia, nagroda, ziarno losowości.

  *Gotowe, gdy:* każda zasada ma test jednostkowy, a dwa przebiegi z tym samym seedem dają ten sam stan po 1000 turach.

- [ ] **1.4 Mózgi.** Q-learning dla każdej postaci, kodowanie obserwacji, zanik epsilon, zapis i odczyt tablic, mózg losowy.

  *Gotowe, gdy:* testy w `pytest` sprawdzają aktualizację Q (także po śmierci i po ucięciu epizodu), kodowanie obserwacji oraz zapis i odczyt tablic.

- [ ] **1.5 Tryb treningu.** Pełna prędkość bez okna, statystyki do CSV (średnia długość życia w oknach po 1000 tur), przebieg porównawczy z mózgiem losowym.

  *Gotowe, gdy:* CSV pokazuje wzrost długości życia względem mózgu losowego.

- [ ] **1.6 Tryb pokazu.** Okno JavaFX: plansza na `Canvas`, ruch przez `AnimationTimer` (około 10 tur na sekundę), obok `LineChart` ze średnią długością życia. Mózgi wczytują wytrenowane tablice i działają z minimalnym epsilonem.

  *Gotowe, gdy:* w oknie widać, że postacie omijają lawę i chodzą do jedzenia i wody.

- [ ] **1.7 Odbiór etapu.** Sprawdź kryteria ukończenia i wpisz wyniki (średnia długość życia: mózg losowy kontra wytrenowany) do `ROADMAP.md`.

## Kryteria ukończenia etapu

- Wytrenowane postacie żyją średnio co najmniej 5 razy dłużej niż mózg losowy na tym samym seedzie.
- Dwa przebiegi treningu z tym samym seedem dają identyczny plik CSV.
- W trybie pokazu postacie omijają lawę i chodzą do jedzenia i wody.

## Do decyzji

Każde pytanie ma odpowiedź domyślną, więc praca nie musi czekać.

- Plansza z seeda czy z pliku mapy? Domyślnie z seeda, z warunkiem, że każde pole jedzenia i wody jest osiągalne.
- Format zapisu tablic Q? Domyślnie pickle w katalogu danych mózgu.
- Kod Pythona z proto: commitować czy generować skryptem? Domyślnie generować skryptem i nie commitować.
- Format konfiguracji? Domyślnie `config/stage-1.yaml`, czytany przez świat i mózgi.
