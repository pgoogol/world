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
- Samostrojenie: postać sama dobiera tempo własnego uczenia z liczników odwiedzin, które prowadzi obok tablicy Q. Epsilon liczy osobno dla każdej sytuacji: w nowej eksperymentuje, w dobrze znanej korzysta z wiedzy. Alpha maleje z liczbą prób danej pary sytuacja–akcja, więc pierwsze doświadczenia zmieniają ocenę mocno, a późniejsze ją wygładzają. Nikt nie ustawia postaci harmonogramu z zewnątrz.
- Stały harmonogram epsilon i stała alpha zostają jako wariant kontrolny. Wariant wybierasz w konfiguracji.
- Aktualizacja: Q-learning. Po śmierci mózg liczy cel bez wartości następnego stanu, po ucięciu epizodu z nią.
- Mózg zapisuje tablice na dysk co kilka tysięcy tur i wczytuje je przy starcie.
- Mózg losowy z tym samym interfejsem służy do porównań.

## Parametry startowe

Wartości do strojenia, trzymane w konfiguracji.

| Parametr | Wartość |
|---|---|
| Plansza | 20×20 razem ze ścianą dookoła, w środku 18×18 |
| Postacie | 5 |
| Udział pól jedzenia / wody / lawy | 8% / 5% / 5% |
| Odrastanie jedzenia | 50 tur |
| Głód i pragnienie | +1 na turę, maksimum 100 |
| Próg potrzeby | od 80 energia spada o 1 na turę za każdą potrzebę |
| Regeneracja energii | 0 (wyłączona); wartość dodatnia przywraca energię w turach bez strat |
| Lawa | −25 energii za turę |
| Jedzenie / picie | −40 głodu / −40 pragnienia |
| Limit życia | 2000 tur |
| Nagrody | +0.1 za turę, +1 za jedzenie lub picie przy potrzebie od 50, −10 za śmierć |
| Wariant uczenia | samostrojenie (domyślny) albo stały harmonogram (kontrolny) |
| Q-learning | gamma 0.95 |
| Samostrojenie: epsilon sytuacji | max(0.05, 1/√(1 + liczba odwiedzin sytuacji)) |
| Samostrojenie: alpha pary sytuacja–akcja | max(0.1, 1/liczba prób pary) |
| Stały harmonogram: alpha | 0.1 |
| Stały harmonogram: epsilon | od 1.0 do 0.05, liniowo przez 200 000 tur |
| Zapis tablic Q | co 5000 tur |
| Długość treningu | 700 000 tur (przy 300 000 samostrojenie dochodziło tylko do 2,9× mózgu losowego) |
| Seed | 42 |

## Kroki

- [x] **1.1 Szkielet repo.** Maven w `world/` z modułami `world-core`, `world-brain`, `world-view` (krok 1.2 dołożył `world-app`). Projekt Pythona w `brain/` z uv. Plik `proto/brain.proto` według szkicu z [ARCHITECTURE.md](ARCHITECTURE.md) i generowanie kodu w obu językach. Plik `.gitignore`.

  *Gotowe, gdy:* build Javy i `pytest` przechodzą na pustych testach, a sekcja „Komendy” w `CLAUDE.md` ma działające polecenia.

- [x] **1.2 Połączenie.** Serwer mózgów odpowiada losowymi ruchami. Pętla świata bez okna woła `Step` co turę dla 5 postaci na pustej planszy. Testy Javy używają podstawionego mózgu (gRPC in-process).

  *Gotowe, gdy:* świat z prawdziwym serwerem w Pythonie przechodzi 1000 tur bez błędu.

- [x] **1.3 Zasady świata.** Pola, odrastanie jedzenia, potrzeby, energia, lawa, śmierć, odrodzenie, limit życia, nagroda, ziarno losowości.

  *Gotowe, gdy:* każda zasada ma test jednostkowy, a dwa przebiegi z tym samym seedem dają ten sam stan po 1000 turach.

- [x] **1.4 Mózgi.** Q-learning dla każdej postaci, kodowanie obserwacji, zanik epsilon, zapis i odczyt tablic, mózg losowy.

  *Gotowe, gdy:* testy w `pytest` sprawdzają aktualizację Q (także po śmierci i po ucięciu epizodu), kodowanie obserwacji oraz zapis i odczyt tablic.

- [x] **1.5 Samostrojenie.** Liczniki odwiedzin sytuacji i par sytuacja–akcja, epsilon i alpha liczone z liczników, zapis i odczyt liczników razem z tablicami Q, wybór wariantu w konfiguracji.

  *Gotowe, gdy:* testy w `pytest` sprawdzają, że epsilon i alpha zależą od liczników i nie spadają poniżej dolnych granic, a przełącznik w konfiguracji wybiera samostrojenie albo stały harmonogram.

- [x] **1.6 Tryb treningu.** Pełna prędkość bez okna, statystyki do CSV (średnia długość życia w oknach po 1000 tur), przebiegi porównawcze z mózgiem losowym i ze stałym harmonogramem.

  *Gotowe, gdy:* CSV pokazuje wzrost długości życia względem mózgu losowego.

- [x] **1.7 Tryb pokazu.** Okno JavaFX: plansza na `Canvas`, ruch przez `AnimationTimer` (około 10 tur na sekundę), obok `LineChart` ze średnią długością życia. Mózgi wczytują wytrenowane tablice i działają z minimalnym epsilonem.

  *Gotowe, gdy:* w oknie widać, że postacie omijają lawę i chodzą do jedzenia i wody.

- [x] **1.8 Odbiór etapu.** Sprawdź kryteria ukończenia i wpisz wyniki (średnia długość życia: mózg losowy, stały harmonogram i samostrojenie) do `ROADMAP.md`.

## Kryteria ukończenia etapu

- Wytrenowane postacie żyją średnio co najmniej 5 razy dłużej niż mózg losowy na tym samym seedzie.
- Postacie z samostrojeniem dochodzą do tego progu w nie większej liczbie tur niż wariant ze stałym harmonogramem na tym samym seedzie.
- Dwa przebiegi treningu z tym samym seedem dają identyczny plik CSV.
- W trybie pokazu postacie omijają lawę i chodzą do jedzenia i wody.

## Wyniki odbioru

Seed 42, trening 700 000 tur, pomiar wytrenowanych tablic bez uczenia przez 50 000 tur.

| Wariant | Średnia długość życia pod koniec treningu | W pomiarze bez uczenia | Względem losowego |
|---|---|---|---|
| Mózg losowy | 87,5 tury | – | 1× |
| Stały harmonogram | 235 tur | 239 tur | 2,7× |
| Samostrojenie | 571 tur | 567 tur | 6,5× |

- **5 razy dłużej niż losowe:** spełnione dla samostrojenia (6,5×). Stały harmonogram, czyli wariant kontrolny, zatrzymuje się na 2,7×.
- **Samostrojenie nie wolniejsze od stałego harmonogramu:** spełnione. Samostrojenie przekracza próg w turze 519 000, stały harmonogram nie dochodzi do niego wcale.
- **Identyczny CSV z tego samego seeda:** spełnione. Dwa przebiegi samostrojenia dały pliki równe co do bajta, przy 300 000 i przy 700 000 tur.
- **Postacie omijają lawę i chodzą do jedzenia i wody:** wytrenowane stoją na lawie przez 0,05% tur (losowe 2,71%), a 41% ich prób jedzenia i picia trafia w zasób (losowe 10%). Zrzut okna: [img/stage-1-show.png](img/stage-1-show.png).

Co sprawdziliśmy po drodze:

- Przy 300 000 tur samostrojenie dawało 2,9×, a stały harmonogram 2,1×. Brakowało czasu nauki: krzywa samostrojenia rosła do końca przebiegu i rośnie dalej po 700 000 tur.
- Regeneracja energii (`energy_regen: 1`) nie pomogła: 273 tury wobec 280 bez niej. Postacie nie giną od starych ran, tylko z pragnienia i głodu, gdy nie znajdą zasobu.
- Pole widzenia 3×3 wystarcza na próg, ale postać nie widzi zasobu z daleka. Marsz przez pół planszy prosto do wody wymaga szerszego pola widzenia albo pamięci miejsc, czyli materiału na etap 2.

## Do decyzji

Każde pytanie ma odpowiedź domyślną, więc praca nie musi czekać.

- Plansza z seeda czy z pliku mapy? Domyślnie z seeda, z warunkiem, że każde pole jedzenia i wody jest osiągalne.
- Format zapisu tablic Q? Domyślnie pickle w katalogu danych mózgu.
- Kod Pythona z proto: commitować czy generować skryptem? Domyślnie generować skryptem i nie commitować.
- Format konfiguracji? Domyślnie `config/stage-1.yaml`, czytany przez świat i mózgi.
- Gdzie mieszka start bez okna? Przyjęte w kroku 1.2: osobny moduł `world-app`.
