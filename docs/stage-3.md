# Etap 3: Tworzenie świata

Status: plan. Kroki są do wykonania po kolei, parametry stroimy w trakcie.

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

Zasady etapów 1 i 2 obowiązują dalej, z jedną zmianą: śmierć jest ostateczna.

- **Plansza 64×64.** Typy pól z etapu 1 plus drzewo, pole uprawne (zasiane, dojrzałe), schronienie i magazyn.
- **Pory roku.** Rok ma cztery pory o równej długości. Wiosną i latem jedzenie odrasta, jesienią wolniej, zimą wcale. Zimą postać poza schronieniem traci energię z zimna.
- **Przedmioty.** Postać nosi ograniczoną liczbę przedmiotów: drewno, jedzenie, nasiona. Zjedzone jedzenie z pola daje nasiona.
- **Sadzenie.** Postać sadzi nasiona na pustym polu. Pole dojrzewa po określonej liczbie tur ciepłych pór i daje kilka porcji jedzenia.
- **Budowanie.** Schronienie i magazyn kosztują drewno i kilka tur pracy. Schronienie chroni przed zimnem postacie, które w nim stoją. Magazyn przechowuje jedzenie, które się nie psuje i nie znika.
- **Śmierć i narodziny.** Upadek z głodu, pragnienia, zimna albo lawy oznacza śmierć na stałe, tak jak starość. Noworodek rodzi się tylko wtedy, gdy osada ma nadwyżkę jedzenia w magazynie i wolne miejsce w schronieniach. Osada może więc rosnąć, kurczyć się i wymrzeć.
- **Nagroda.** Liczy ją świat, jak dotąd. Dochodzi mały plus za jedzenie odłożone do magazynu i za przeżycie zimy. Nagrody za samo sadzenie i budowanie nie ma: ich wartość postać ma odkryć przez opóźniony skutek.

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

- nowe wartości `Cell` i `Action`,
- w `Observation`: ekwipunek, pora roku, tura w roku, większy wycinek planszy (rozmiar w osobnym polu),
- w `StepRequest`: identyfikator osady (jedna osada w etapie 3, wiele od etapu 5).

## Parametry startowe

Wartości do strojenia w `config/stage-3.yaml`. Krok 3.6 ustala je tak, żeby świat dało się przeżyć z budowaniem i nie dało bez niego.

| Parametr | Wartość |
|---|---|
| Plansza | 64×64 razem ze ścianą |
| Postacie na starcie / limit | 20 / 50 |
| Długość pory roku | 1000 tur (rok 4000) |
| Zimno zimą | −1 energii na turę poza schronieniem |
| Ekwipunek | 4 przedmioty |
| Dojrzewanie pola | 800 tur ciepłych pór, plon 4 porcje |
| Schronienie | 6 drewna, 30 tur pracy, 4 miejsca |
| Magazyn | 4 drewna, 20 tur pracy, 40 porcji |
| Wycinek planszy w obserwacji | 7×7 |
| Długość życia | 3 lata (12 000 tur) ± 20% |
| Seed | 42 |

## Kroki

- [ ] **3.1 Specyfikacja i kontrakt.** Doprecyzowanie tego dokumentu, `config/stage-3.yaml`, nowe pola i wartości w `proto/brain.proto`, wartości domyślne dla plików etapów 1 i 2.

  *Gotowe, gdy:* build i testy przechodzą, a etapy 1 i 2 dają pliki identyczne ze swoimi odbiorami.

- [ ] **3.2 Pory roku i zimno.** Zegar roku, odrastanie zależne od pory, utrata energii z zimna.

  *Gotowe, gdy:* każda zasada ma test, a rok przebiega powtarzalnie na tym samym seedzie.

- [ ] **3.3 Przedmioty i drzewa.** Ekwipunek, zbieranie drewna, noszenie jedzenia, odkładanie i podnoszenie.

  *Gotowe, gdy:* każda zasada ma test, w tym limit ekwipunku i to, że przedmioty nie znikają ani się nie mnożą.

- [ ] **3.4 Sadzenie i pola.** Nasiona, zasiew, dojrzewanie, zbiór.

  *Gotowe, gdy:* testy pokrywają cały cykl pola, także zasiew tuż przed zimą.

- [ ] **3.5 Budowanie i magazyn.** Schronienie, magazyn, praca rozłożona na tury i na kilka postaci.

  *Gotowe, gdy:* testy sprawdzają koszt, czas budowy, pojemność i ochronę przed zimnem.

- [ ] **3.6 Śmierć, narodziny i osada ze scenariusza.** Ostateczna śmierć, narodziny z nadwyżki, mózg ze scenariuszem napisanym ręcznie (zbieraj, sadź, buduj, magazynuj) i ten sam scenariusz bez budowania.

  *Gotowe, gdy:* osada ze scenariuszem przeżywa 5 zim, a wersja bez budowania wymiera przed końcem drugiej zimy. Ten krok stroi parametry świata.

- [ ] **3.7 Skala i tempo.** Plansza 64×64, do 50 postaci, pomiar tur na sekundę ze scenariuszem i z pustą siecią, profilowanie transportu i świata.

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
