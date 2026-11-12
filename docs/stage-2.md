# Etap 2: Wspólna wiedza

## Cel

Wiedza przeżywa pojedyncze postacie. Postacie starzeją się i umierają, nowe rodzą się z pustą pamięcią, a osada przekazuje im to, czego się nauczyła. Na końcu porównujemy osadę z wymianą wiedzy z osadą kontrolną bez niej.

Etap 1 zostaje uruchamialny na `config/stage-1.yaml` i daje te same wyniki co przed etapem 2.

## Świat (`world-core`)

Zasady etapu 1 obowiązują dalej. Dochodzą:

- **Dwa rodzaje końca życia.** Upadek (energia 0) działa jak w etapie 1: postać wraca na losowe bezpieczne pole z tą samą pamięcią. Śmierć ze starości zabiera postać na stałe.
- **Starzenie.** Każda postać ma długość życia losowaną z seeda wokół wartości z konfiguracji. Wiek rośnie co turę i nie zeruje się po upadku.
- **Narodziny.** Na miejsce zmarłej ze starości postaci rodzi się nowa: z nowym numerem, pustą pamięcią i pokoleniem o jeden wyższym. Pojawia się na bezpiecznym polu obok losowej żyjącej postaci.
- **Założyciele.** Pierwsze pokolenie startuje z wiekiem rozłożonym równo od zera do długości życia, więc postacie umierają po kolei, a nie naraz. Przez pierwsze tury (rozruch) nikt się nie starzeje, żeby założyciele zdążyli się czegoś nauczyć.
- **Sąsiedzi.** Świat podaje każdej postaci numery postaci stojących w jej polu 3×3. Wiedza przechodzi tylko między sąsiadami.
- **Pozycja.** Postać zna swoje współrzędne na planszy.
- Limit życia z etapu 1 (2000 tur bez upadku) zostaje jako ucięcie epizodu.

## Mózgi (`brain`)

Każdy mechanizm ma przełącznik w konfiguracji. Osada kontrolna ma wyłączone wszystkie trzy.

- **Dziedziczenie.** Noworodek startuje od średniej tablic Q sąsiadów obecnych przy narodzinach, ważonej ich licznikami prób. Liczniki dostaje pomnożone przez współczynnik zaufania, więc wiedzę z drugiej ręki sprawdza sam.
- **Podglądanie.** Postać uczy się też z ruchów sąsiada: jego przejście (sytuacja, ruch, nagroda, następna sytuacja) aktualizuje jej tablicę ze słabszą wagą.
- **Mapa odkryć.** Każda postać zapamiętuje, gdzie widziała wodę i jedzenie. Sąsiedzi wymieniają mapy, a noworodek dostaje mapę przy narodzinach. Mapa daje postaci siódmy ruch: „idź do znanego miejsca”, czyli krok w stronę najbliższego miejsca z zasobem, którego postać potrzebuje bardziej, z omijaniem widocznej lawy i ścian. Na miejscu ten ruch oznacza jedzenie albo picie. Stan Q dostaje jedną informację: czy postać zna takie miejsce. Kiedy używać nowego ruchu, postać uczy się sama.
- Mózg postaci zmarłej ze starości znika razem z nią.

## Kontrakt

Same nowe pola, stare numery bez zmian:

- `Observation.x`, `Observation.y`: pozycja postaci.
- `NpcView.neighbour_ids`: numery sąsiadów.
- `StepRequest.retired_npc_ids`: postacie zmarłe ze starości od poprzedniego zapytania.

## Parametry startowe

Wartości do strojenia w `config/stage-2.yaml`. Parametry etapu 1, których tabela nie wymienia, zostają bez zmian.

| Parametr | Wartość |
|---|---|
| Postacie | 12 |
| Plansza | 20×20 razem ze ścianą |
| Długość życia | 60 000 tur ± 20% |
| Rozruch bez starzenia | 150 000 tur |
| Dziedziczenie: zaufanie do liczników | 0,25 |
| Podglądanie: waga | 0,5 |
| Mapa: ponowna próba pustego miejsca z jedzeniem | po 50 turach |
| Długość przebiegu | 400 000 tur (rozruch i 4 długości życia) |
| Seed | 42 |

## Kroki

- [x] **2.1 Specyfikacja i kontrakt.** Ten dokument, `config/stage-2.yaml`, nowe pola w `proto/brain.proto`, konfiguracja z wartościami domyślnymi dla plików etapu 1.

  *Gotowe, gdy:* build i testy przechodzą, a etap 1 na swoim pliku konfiguracji daje CSV identyczny z wynikiem sprzed etapu 2.

- [x] **2.2 Starzenie i narodziny.** Wiek, śmierć ze starości, noworodek z nowym numerem obok żyjącej postaci, pokolenia, rozruch, sąsiedzi i pozycja w widoku, zdarzenia narodzin i śmierci, dziennik epizodów per postać.

  *Gotowe, gdy:* każda zasada ma test, a osada przechodzi 3 pokolenia powtarzalnie na tym samym seedzie.

- [x] **2.3 Dziedziczenie.** Cykl życia mózgu (narodziny, śmierć ze starości), średnia ważona licznikami, współczynnik zaufania.

  *Gotowe, gdy:* testy sprawdzają średnią, wagi, pomniejszanie liczników i to, że noworodek dziedziczy tylko po sąsiadach.

- [x] **2.4 Podglądanie.** Nauka z przejść sąsiadów ze słabszą wagą.

  *Gotowe, gdy:* testy pokazują zmianę tablicy obserwatora, a wyłączony przełącznik jej nie rusza.

- [x] **2.5 Mapa odkryć.** Osobista mapa, wymiana przy kontakcie, ruch „idź do znanego miejsca”, mapa w zapisie na dysk.

  *Gotowe, gdy:* testy pokrywają budowę mapy, scalanie, wybór celu i krok w jego stronę.

- [ ] **2.6 Pomiar.** Przebieg pokoleń dla osady z wymianą wiedzy, osady kontrolnej i każdego mechanizmu osobno. Skrypt liczy poziom pokoleń i wiek, w którym noworodki dochodzą do poziomu dorosłych.

  *Gotowe, gdy:* przebiegi dają powtarzalny wynik, a skrypt wypisuje tabelę wariantów.

- [ ] **2.7 Sieć zamiast tablic (warunkowo).** Mała sieć w PyTorchu zastępuje tablice tylko wtedy, gdy tablica jednej postaci przekroczy 200 000 sytuacji albo postacie z mapą uczą się wolniej niż bez niej.

  *Gotowe, gdy:* pomiar z kroku 2.6 rozstrzyga, czy warunek zaszedł, a decyzja jest zapisana w `ARCHITECTURE.md`.

- [ ] **2.8 Widok.** Wiek i pokolenie postaci w oknie, zaznaczony kontakt sąsiadów.

  *Gotowe, gdy:* w oknie widać, kto jest noworodkiem, a kto starszym, i kiedy postacie stoją obok siebie.

- [ ] **2.9 Odbiór etapu.** Sprawdź kryteria i wpisz wyniki do `ROADMAP.md`.

## Kryteria ukończenia etapu

- **Poziom dorosłych** to 5× średnia długość życia mózgu losowego w tym samym świecie (średnia liczba tur między upadkami).
- Noworodki w osadzie z wymianą wiedzy dochodzą do poziomu dorosłych co najmniej 2 razy szybciej niż noworodki w osadzie kontrolnej. Noworodek, który nie dochodzi do poziomu przed śmiercią ze starości, liczy się jako wolniejszy od każdego, który doszedł.
- Osada z wymianą wiedzy trzyma poziom dorosłych przez 3 pokolenia po założycielach, a osada kontrolna go nie osiąga.
- Dwa przebiegi z tym samym seedem dają identyczne pliki statystyk.
- Etap 1 na `config/stage-1.yaml` daje wyniki identyczne z odbiorem etapu 1.

## Do decyzji

Każde pytanie ma odpowiedź domyślną, więc praca nie musi czekać.

- Czy każda śmierć ma kasować pamięć? Domyślnie nie: upadek zostawia pamięć, kasuje ją tylko starość. Wariant „każda śmierć jest ostateczna” to osobny eksperyment po etapie.
- Gdzie rodzi się noworodek? Domyślnie na losowym bezpiecznym polu w polu 3×3 losowej żyjącej postaci.
- Po kim dziedziczy noworodek? Domyślnie po wszystkich sąsiadach z pierwszej tury życia.
- Czy podglądanie zwiększa liczniki prób? Domyślnie tak: cudze doświadczenie to też dowód.
- Jak długo żyją postacie? Wartości z tabeli dobrane tak, żeby przebieg mieścił 3 pokolenia w kilkunastu minutach. Stroimy je w kroku 2.6 po pomiarze tempa nauki.
