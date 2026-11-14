# Etap 3: Tworzenie świata

Status: w toku. Kroki są do wykonania po kolei, parametry stroimy w trakcie.

## Cel

Postacie zmieniają świat, żeby przetrwać trudniejsze warunki. Dochodzą pory roku: zimą nic nie rośnie, więc osada, która nie sadzi, nie gromadzi zapasów i nie buduje, wymiera. Na końcu porównujemy osadę, która może budować, z tą samą osadą bez tej możliwości.

Etapy 1 i 2 zostają uruchamialne na swoich plikach konfiguracji i dają te same wyniki co w swoich odbiorach.

## Co wynika z etapów 1 i 2

Plan opiera się na czterech rzeczach, które zmierzyliśmy:

- **Tablica nie uogólnia.** Kierunek do wody w stanie Q nie zadziałał w żadnej z trzech wersji. Zadziałał dopiero gotowy ruch „idź do znanego miejsca”. Dlatego etap 3 od początku daje postaciom umiejętności złożone, a uczenie wybiera, której użyć.
- **Nauka jest wolna.** Jedna postać potrzebowała ponad 500 000 tur, żeby dojść do poziomu dorosłych. Opóźniona nagroda (sadzisz wiosną, jesz jesienią) jest dużo trudniejsza, więc najpierw sprawdzamy scenariuszem, że świat da się przeżyć, a dopiero potem uczymy.
- **Wiedzę niosą dziedziczenie i mapa.** Podglądanie sąsiadów nie dało mierzalnego efektu. W etapie 3 wiedzę osady trzyma jedna sieć, a mapa zostaje pamięcią postaci.
- **Tempo to około 800–2000 tur na sekundę przy 5–12 postaciach.** Przy 50 postaciach i sieci neuronowej spadnie kilka razy. Pomiar tempa jest osobnym krokiem przed uczeniem.

## Świat (`world-core`)

Zasady etapów 1 i 2 obowiązują dalej. Każda nowa zasada ma przełącznik albo parametr w konfiguracji, a jego wartość domyślna odtwarza zachowanie etapów 1 i 2.

- **Plansza 64×64.** Typy pól z etapu 1 plus drzewo, pniak, pole zasiane, pole dojrzałe, plac budowy schronienia, plac budowy magazynu, schronienie i magazyn. Po każdym polu poza ścianą i lawą da się chodzić.
- **Pory roku.** Rok ma cztery pory o równej długości: wiosnę, lato, jesień i zimę. Odrastanie jedzenia, drzew i pól liczy punkty wzrostu: wiosną i latem pełne, jesienią połowa, zimą zero. Pierwszego dnia zimy dzikie jedzenie i niezebrane dojrzałe pola marnieją, a dzikie jedzenie odrasta dopiero wiosną. Zimą postać poza schronieniem traci energię z zimna.
- **Energia wraca.** W turze bez żadnej straty postać odzyskuje trochę energii. Bez tego każda postać prędzej czy później by umarła, bo śmierć jest teraz ostateczna.
- **Przedmioty.** Postać nosi najwyżej 4 przedmioty: porcje jedzenia i drewno. Zbiera je ruchem „zbierz” z dzikiego jedzenia, z dojrzałego pola i z drzewa. Ścięte drzewo zostawia pniak, który z czasem odrasta. Niesione jedzenie zjada osobnym ruchem. Ruch „jedz” działa też wprost na dojrzałym polu i na magazynie (jedna porcja), więc postać z rękami pełnymi drewna nie głoduje obok jedzenia.
- **Sadzenie.** Postać sadzi niesioną porcję jedzenia na pustym polu. Pole dojrzewa po określonej liczbie punktów wzrostu i daje kilka porcji, które zbiera się po jednej. Sadzisz jedną porcję, zbierasz cztery. Specyfikacja wstępna mówiła o nasionach; porcja jedzenia w roli nasiona daje ten sam wybór (zjeść teraz albo zasiać) bez dodatkowego przedmiotu.
- **Budowanie.** Postać zakłada plac budowy na pustym polu, a potem przynosi na niego drewno i pracuje. Każde drewno i każda tura pracy to jeden ruch „buduj”, więc kilka postaci może budować razem. Schronienie chroni przed zimnem do 4 postaci, które na nim stoją. Magazyn przyjmuje niesione jedzenie i wydaje je po jednej porcji. Jedzenie w magazynie się nie psuje.
- **Śmierć i narodziny.** Upadek z głodu, pragnienia, zimna albo lawy oznacza śmierć na stałe, tak jak starość. Noworodek rodzi się przy magazynie, gdy w magazynach leży nadwyżka jedzenia ponad zapas na każdego mieszkańca, w schronieniach są wolne miejsca, a od poprzednich narodzin minął ustalony czas. Narodziny kosztują porcje z magazynu. Osada może więc rosnąć, kurczyć się i wymrzeć.
- **Założyciele** startują blisko siebie, w promieniu kilku pól od losowego miejsca na planszy.
- **Nagroda.** Liczy ją świat, jak dotąd. Dochodzi premia dla każdej postaci, która przeżyła zimę. Nagrody za samo sadzenie, magazynowanie i budowanie nie ma: ich wartość postać ma odkryć przez opóźniony skutek. Specyfikacja wstępna przewidywała plus za odłożenie jedzenia do magazynu; usunąłem go, bo kłócił się z kryterium „osada magazynuje, choć świat tego nie nagradza”.
- **Limit życia z etapu 1** (ucięcie epizodu po 2000 turach bez upadku) jest wyłączony, bo przenosiłby postać w losowe miejsce.

## Mózgi (`brain`)

- **Sieć zamiast tablic.** Jedna mała sieć na osadę w PyTorchu, trenowana PPO. Wszystkie postacie osady korzystają z tej samej sieci i każda dokłada do niej swoje doświadczenia. To odpowiednik dziedziczenia z etapu 2: noworodek od razu ma wiedzę osady.
- **Obserwacja dla sieci.** Wycinek planszy wokół postaci (większy niż 3×3), potrzeby, ekwipunek, pora roku i tura w roku, wiek oraz cechy z mapy odkryć: odległość i kierunek do najbliższego znanego miejsca każdego typu.
- **Umiejętności złożone.** Sieć wybiera umiejętność, a mózg zamienia ją na pojedyncze ruchy kontraktu, tak jak „idź do znanego miejsca” w etapie 2. Startowy zestaw: idź do wody, idź do jedzenia, idź po drewno, zanieś do magazynu, weź z magazynu, zasadź tutaj, buduj tutaj, schroń się, odkrywaj.
- **Mapa odkryć** zostaje pamięcią pojedynczej postaci i wymienia się przy kontakcie, jak w etapie 2. Obejmuje nowe typy miejsc.
- **Tablice Q** zostają w kodzie dla etapów 1 i 2.

## Historia i zapis (`world-history`)

Nowy moduł ze Spring Bootem, PostgreSQL i Flywayem. `world-core` dalej nie wie o bazie.

- **Kronika.** Zdarzenia świata (narodziny, śmierci, budowy, zbiory, zmiany pór roku) trafiają do bazy przez słuchacza zdarzeń. Zapis nie spowalnia tury: idzie partiami w tle.
- **Zapis i wczytanie stanu.** Cały stan świata zapisujesz i wczytujesz jak zapis gry. Wczytany świat daje dalej ten sam przebieg co oryginał.
- **Baza lokalnie.** PostgreSQL uruchamiany przez Docker Compose, testy na Testcontainers.

## Kontrakt

Same nowe pola i wartości, stare numery bez zmian:

- `Cell`: drzewo, pniak, pole zasiane, pole dojrzałe, plac budowy schronienia, plac budowy magazynu, schronienie, magazyn.
- `Action`: zbierz, zjedz niesione, zasiej, buduj schronienie, buduj magazyn, odłóż do magazynu, weź z magazynu.
- `Observation`: bok wycinka planszy (3 w etapach 1 i 2, 7 w etapie 3), niesione jedzenie i drewno, pora roku, liczba tur do jej końca oraz to, co leży na polu pod postacią (porcje na dojrzałym polu, jedzenie w magazynie, drewno na placu budowy).
- `StepRequest`: identyfikator osady (jedna osada w etapie 3, wiele od etapu 5).

## Parametry startowe

Wartości do strojenia w `config/stage-3.yaml`. Krok 3.6 ustala je tak, żeby świat dało się przeżyć z budowaniem i nie dało bez niego.

| Parametr | Wartość |
|---|---|
| Plansza | 64×64 razem ze ścianą |
| Postacie na starcie / limit | 20 / 50 |
| Założyciele | w promieniu 4 pól od losowego miejsca |
| Udział pól jedzenia / wody / lawy / drzew | 6% / 2% / 3% / 5% |
| Odrastanie dzikiego jedzenia / drzew | 60 / 300 tur pełnego wzrostu |
| Długość pory roku | 500 tur (rok 2000) |
| Wzrost wiosną / latem / jesienią / zimą | 100% / 100% / 50% / 0% |
| Zimno zimą | −1 energii na turę poza schronieniem |
| Odzyskiwanie energii | +1 na turę bez strat |
| Ekwipunek | 4 przedmioty |
| Dojrzewanie pola | 300 tur pełnego wzrostu, plon 4 porcje |
| Schronienie | 6 drewna, 30 tur pracy, 4 miejsca |
| Magazyn | 4 drewna, 20 tur pracy, 100 porcji |
| Narodziny | 10 porcji z magazynu, zapas 5 porcji na mieszkańca, co najmniej 200 tur odstępu |
| Wycinek planszy w obserwacji | 7×7 |
| Długość życia | 3 lata (6000 tur) ± 20% |
| Premia za przeżytą zimę | +5 |
| Seed | 42 |

Pora roku jest krótsza niż w specyfikacji wstępnej (500 zamiast 1000 tur), bo zimowy zapas rośnie z długością zimy: przy 1000 turach osada 20 postaci potrzebowałaby kilkuset porcji w magazynach.

## Kroki

- [x] **3.1 Specyfikacja i kontrakt.** Doprecyzowanie tego dokumentu, `config/stage-3.yaml`, nowe pola i wartości w `proto/brain.proto`, wartości domyślne dla plików etapów 1 i 2.

  *Gotowe, gdy:* build i testy przechodzą, a etapy 1 i 2 dają pliki identyczne ze swoimi odbiorami.

- [x] **3.2 Pory roku i zimno.** Zegar roku, odrastanie zależne od pory, utrata energii z zimna.

  *Gotowe, gdy:* każda zasada ma test, a rok przebiega powtarzalnie na tym samym seedzie.

- [x] **3.3 Przedmioty i drzewa.** Ekwipunek, zbieranie drewna, noszenie jedzenia, odkładanie i podnoszenie.

  *Gotowe, gdy:* każda zasada ma test, w tym limit ekwipunku i to, że przedmioty nie znikają ani się nie mnożą.

- [x] **3.4 Sadzenie i pola.** Nasiona, zasiew, dojrzewanie, zbiór.

  *Gotowe, gdy:* testy pokrywają cały cykl pola, także zasiew tuż przed zimą.

- [x] **3.5 Budowanie i magazyn.** Schronienie, magazyn, praca rozłożona na tury i na kilka postaci.

  *Gotowe, gdy:* testy sprawdzają koszt, czas budowy, pojemność i ochronę przed zimnem.

- [x] **3.6 Śmierć, narodziny i osada ze scenariusza.** Ostateczna śmierć, narodziny z nadwyżki, mózg ze scenariuszem napisanym ręcznie (zbieraj, sadź, buduj, magazynuj) i ten sam scenariusz bez budowania.

  *Gotowe, gdy:* osada ze scenariuszem przeżywa 5 zim, a wersja bez budowania wymiera przed końcem drugiej zimy. Ten krok stroi parametry świata.

- [x] **3.7 Skala i tempo.** Plansza 64×64, do 50 postaci, pomiar tur na sekundę ze scenariuszem i z pustą siecią, profilowanie transportu i świata.

  *Gotowe, gdy:* znamy tempo i wiemy, ile trwa rok symulacji. Jeśli rok trwa dłużej niż 20 sekund, krok kończy się planem przyspieszenia (większe paczki, kilka światów naraz), zanim ruszy uczenie.

- [ ] **3.8 Kronika i zapis stanu.** Moduł `world-history`, schemat Flyway, zapis zdarzeń w tle, zapis i wczytanie stanu świata.

  *Gotowe, gdy:* wczytany zapis daje dalej ten sam przebieg co oryginał, a kronikę da się odpytać o historię jednej postaci i jednego roku.

- [ ] **3.9 Sieć na świecie etapu 2.** PyTorch, PPO, jedna sieć na osadę, uczenie na świecie etapu 2 bez nowych zasad.

  *Gotowe, gdy:* osada z siecią dochodzi do poziomu dorosłych z etapu 2 (434 tury między upadkami). Ten krok sprawdza sieć na zadaniu, które już umiemy zmierzyć.

- [ ] **3.10 Umiejętności złożone.** Zestaw umiejętności, ich zamiana na ruchy, wybór umiejętności przez sieć, mapa z nowymi typami miejsc.

  *Gotowe, gdy:* każda umiejętność ma test, a postać ze scenariuszem zbudowanym z samych umiejętności przeżywa zimę.

- [ ] **3.11 Uczenie z opóźnioną nagrodą.** PPO na pełnym świecie etapu 3, najpierw na krótszych porach roku, potem na docelowych.

  *Gotowe, gdy:* krzywa uczenia pokazuje, że osada zaczyna sadzić i magazynować bez nagrody za te czynności.

- [ ] **3.12 Widok.** Pory roku, budowle, ekwipunek, liczba mieszkańców, zapas w magazynie, wczytanie zapisu.

  *Gotowe, gdy:* w oknie widać cykl roku: zbiory jesienią, schronienia zimą, zasiewy wiosną.

- [ ] **3.13 Odbiór etapu.** Sprawdź kryteria i wpisz wyniki do `ROADMAP.md`.

## Wyniki kroków

### 3.6 Osada ze scenariusza

Seed 42, plansza 64×64, 20 założycieli, pięć lat (10 000 tur). Scenariusz napisany ręcznie łączy to, co widzą wszystkie postacie, w jedną pamięć osady: wybiera dom przy wodzie, planuje magazyny pod zimowy zapas i schronienia pod liczbę mieszkańców, a role dzieli na budowniczych (co trzecia postać, gdy jest plac budowy) i zbieraczy. Zimą wszyscy stoją w schronieniach i jedzą z magazynów.

| Zima | Postacie przed zimą | Po zimie | Jedzenie w magazynach | Schronienia | Magazyny |
|---|---|---|---|---|---|
| 1 | 21 | 19 | 354 | 8 | 4 |
| 2 | 23 | 23 | 317 | 9 | 4 |
| 3 | 23 | 24 | 325 | 9 | 4 |
| 4 | 25 | 25 | 295 | 9 | 5 |
| 5 | 21 | 20 | 358 | 9 | 5 |

- **Z budowaniem osada przeżywa pięć zim:** po każdej zimie żyje ponad połowa, a po pięciu latach 20 postaci, tyle samo co na starcie. Przez pięć lat z innych przyczyn niż starość zginęły 4 postacie.
- **Bez budowania osada wymiera w 1600. turze,** 100 tur po początku pierwszej zimy: zimno zabiera energię szybciej, niż wraca, a jedzenia nie ma gdzie przechować.
- **Zapas jest niewielki.** 20 postaci po pięciu latach to dokładnie próg kryterium. Wynik pochodzi z jednego seeda.

Strojenie świata w tym kroku:

- **Dzikiego jedzenia było za mało.** Przy 3% pól i odrastaniu po 100 turach osada znała zwykle 2–13 miejsc z jedzeniem i postacie umierały z głodu już wiosną. Teraz 6% pól i 60 tur.
- **Odstęp między narodzinami spadł z 250 do 200 tur,** żeby narodziny nadążały za śmiercią ze starości (założyciele mają wiek rozłożony na całą długość życia, więc w pierwszych latach umiera ich około sześciu rocznie).
- **„Jedz” na polu i magazynie.** Budowniczy z czterema drewnami nie mógł wziąć porcji z magazynu i umierał z głodu obok jedzenia.

Lekcje dla uczenia w kroku 3.11, wyciągnięte z poprawek scenariusza:

- Zimą marnieje jedzenie, którego nikt nie widzi. Pamięć, która tego nie uwzględnia, prowadzi wiosną do pustych krzaków.
- Głodna postać musi iść do jedzenia pewnego (widzianego albo w magazynie). Krzaki „na próbę” zabijały postacie przy pełnych magazynach.
- Drewna nie da się odłożyć. Kto je niesie bez placu budowy, ma ręce zajęte na zawsze.

### 3.7 Skala i tempo

Plansza 64×64, 4000 tur na pomiar, jeden wątek po stronie mózgów. Losowe ruchy i pusta sieć zabijają osadę w kilkaset tur, więc te wiersze liczą świat ze stałą populacją (bez ostatecznej śmierci, starości i narodzin). Osada ze scenariusza działa na prawdziwych zasadach.

| Postacie | Mózg | Tur/s | Rok (s) | ms/turę | W tym mózg (ms) |
|---|---|---|---|---|---|
| 20 | sam świat, ruchy w procesie Javy | 22 013 | 0,1 | 0,05 | – |
| 20 | losowy przez gRPC | 799 | 2,5 | 1,25 | 0,07 |
| 20 | scenariusz | 102 | 19,6 | 9,80 | 8,26 |
| 20 | pusta sieć (MLP, PyTorch) | 499 | 4,0 | 2,00 | 0,75 |
| 50 | sam świat, ruchy w procesie Javy | 15 195 | 0,1 | 0,07 | – |
| 50 | losowy przez gRPC | 739 | 2,7 | 1,35 | 0,12 |
| 50 | scenariusz | 82 | 24,4 | 12,20 | 10,52 |
| 50 | pusta sieć (MLP, PyTorch) | 367 | 5,4 | 2,72 | 1,25 |

- **Świat nie jest wąskim gardłem.** Bez gRPC liczy 15–22 tysiące tur na sekundę.
- **Większość czasu tury zjada transport.** Wywołanie gRPC z kodowaniem wiadomości kosztuje około 1,2 ms na turę, kilkanaście razy więcej niż sam świat.
- **Rok z siecią trwa 4–5,4 s,** poniżej progu 20 s. Plan przyspieszenia przed uczeniem nie jest potrzebny.
- **Scenariusz jest wolny (20–24 s na rok)** przez przeszukiwanie mapy w Pythonie co turę. Służy tylko jako dowód, więc go nie przyspieszam.

Gdyby uczenie w kroku 3.11 potrzebowało więcej lat symulacji, pierwszym krokiem jest kilka światów w jednym zapytaniu `Step`: koszt transportu rozłoży się na kilka osad, a świat ma zapas kilkunastokrotny.

## Kryteria ukończenia etapu

- Wytrenowana osada przeżywa 5 kolejnych zim: po każdej zimie żyje co najmniej połowa stanu sprzed zimy, a po pięciu latach osada nie jest mniejsza niż na starcie.
- Ta sama osada uczona bez możliwości sadzenia, magazynowania i budowania wymiera.
- Osada sadzi i magazynuje, choć świat nie nagradza samych tych czynności.
- Dwa przebiegi z tym samym seedem na tym samym komputerze dają identyczne pliki statystyk.
- Wczytany zapis stanu daje dalej ten sam przebieg co oryginał.
- Etapy 1 i 2 dają wyniki identyczne ze swoimi odbiorami.

## Ryzyka

- **Opóźniona nagroda może się nie wyuczyć.** To najtrudniejsza rzecz w całym projekcie do tej pory. Zabezpieczenia: osada ze scenariusza jako dowód, że się da (3.6), sieć sprawdzona najpierw na znanym zadaniu (3.9), umiejętności złożone skracające łańcuch decyzji (3.10), krótsze pory roku na początek (3.11). Jeśli mimo to się nie uda, pokażę krzywe i zaproponuję zmianę, zamiast dokładać nagrody za sadzenie po cichu.
- **Tempo.** 50 postaci z siecią na CPU może dać kilkadziesiąt tur na sekundę. Krok 3.7 mierzy to przed uczeniem.
- **Powtarzalność z PyTorchem.** Wymaga ustawienia ziaren, jednego wątku i trybu deterministycznego. Obiecujemy identyczny przebieg na tym samym komputerze, nie między komputerami.
- **Python 3.14 i PyTorch.** Krok 3.9 zaczyna od sprawdzenia, czy jest paczka dla 3.14. Jeśli nie, mózgi przechodzą na wersję Pythona, którą PyTorch obsługuje.
- **Rozrost kontraktu.** Etap 3 dokłada najwięcej pól od początku projektu. Każde pole dostaje test w obu językach.

## Do decyzji

Każde pytanie ma odpowiedź domyślną, więc praca nie musi czekać.

- Czy śmierć ma być ostateczna? Domyślnie tak. Bez tego „osada wymiera” nic nie znaczy. Etapy 1 i 2 zachowują swoje zasady.
- Jedna sieć na osadę czy osobna na postać? Domyślnie jedna na osadę. Pięćdziesiąt osobnych sieci nie zmieści się w czasie na CPU, a etap 5 i tak zakłada sieć na osadę.
- Skąd biorą się noworodki? Domyślnie z nadwyżki jedzenia w magazynie i wolnych miejsc w schronieniach.
- Jak długa jest pora roku? Domyślnie 1000 tur. Krótsza ułatwia uczenie, dłuższa daje ciekawszy pokaz.
- Docker dla bazy? Domyślnie tak, Docker Compose z samym PostgreSQL. Świat i mózgi dalej startują bez Dockera.
- Czy kronika jest wymagana do treningu? Domyślnie nie: trening działa bez bazy, kronikę włącza przełącznik.
