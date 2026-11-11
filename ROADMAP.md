# Roadmapa

Każdy etap kończy się czymś, co widać w oknie albo na wykresie. Etap 1 ma pełną specyfikację w [docs/stage-1.md](docs/stage-1.md). Dalsze etapy doprecyzujemy po wynikach poprzednich, dlatego ich opis jest ogólny.

| Etap | Nazwa | Skala | Status |
|---|---|---|---|
| 1 | Mała osada | 5 postaci, plansza 20×20 | Bieżący |
| 2 | Wspólna wiedza | 10–20 postaci, kolejne pokolenia | Planowany |
| 3 | Tworzenie świata | 20–50 postaci, plansza 64×64, pory roku | Planowany |
| 4 | Przepisywanie reguł | jak w etapie 3 | Planowany |
| 5 | Relacje między osadami | 3–5 osad | Planowany |
| 6 | Rywalizacja | wiele osad | Planowany |

## Etap 1: Mała osada

Pięć postaci uczy się przetrwać, zaczynając od losowych ruchów.

- **Świat:** jedzenie (odrasta), woda, lawa i ściany. Głód i pragnienie rosną co turę. Gdy są wysokie albo postać stoi na lawie, spada jej energia. Przy zerowej energii postać umiera i odradza się z tą samą pamięcią. Nagrodę liczy świat.
- **Mózgi:** osobna tablica Q dla każdej postaci. Postać widzi pola 3×3 wokół siebie oraz swój głód i pragnienie.
- **Zobaczysz:** najpierw błądzenie i śmierć na lawie, po treningu marsz prosto do jedzenia i wody oraz rosnący wykres długości życia.
- **Gotowe, gdy:** wytrenowane postacie żyją co najmniej 5 razy dłużej niż losowe, a ten sam seed daje identyczny przebieg.

Kroki i parametry: [docs/stage-1.md](docs/stage-1.md).

## Etap 2: Wspólna wiedza

Wiedza przeżywa pojedyncze postacie.

- **Świat:** postacie starzeją się i umierają, a na ich miejsce rodzą się nowe z pustą pamięcią. Uczyć się można tylko od sąsiada, więc świat podaje, kto stoi obok kogo.
- **Mózgi:** osada prowadzi wspólną mapę odkryć. Noworodek startuje od uśrednionej wiedzy starszych i podgląda ich ruchy. Gdy tablice Q urosną za bardzo, przechodzimy na małą sieć w PyTorchu.
- **Gotowe, gdy:** noworodki z wymianą wiedzy dochodzą do poziomu dorosłych co najmniej 2 razy szybciej niż w osadzie kontrolnej bez niej.

## Etap 3: Tworzenie świata

Postacie zmieniają świat, żeby przetrwać trudniejsze warunki.

- **Świat:** pory roku, a zimą nic nie rośnie. Postacie zbierają drewno, sadzą, budują schronienia i magazyny, noszą przedmioty. Historia świata trafia do PostgreSQL (Spring Boot, Flyway), a stan świata zapiszesz i wczytasz jak zapis gry.
- **Mózgi:** nagroda przychodzi z opóźnieniem (sadzisz wiosną, jesz jesienią), więc wchodzi PyTorch z PPO i akcje złożone typu „idź po drewno”.
- **Gotowe, gdy:** osada przeżywa zimę dzięki polom i zapasom, a jej wersja bez budowania wymiera.

## Etap 4: Przepisywanie reguł

Osada sama poprawia swoje zasady i zostawia tylko te, które działają. Tu wraca pomysł samonadpisującego się algorytmu.

- **Reguły jako dane:** JSON z historią wersji, np. „dziel się jedzeniem z głodnym” albo „30% plonów do magazynu”.
- **Propozycje:** najpierw losowe mutacje, potem model językowy z LM Studio, który czyta kronikę osady i uzasadnia zmianę. Lokalny model odpowiada wolno, więc proponuje raz na sezon.
- **Sędzia w Javie:** świat kopiuje bieżący stan, puszcza starą i nową regułę przez kilka sezonów i zostawia lepszą. Reguły nie mają dostępu do fizyki świata ani punktacji.
- **Gotowe, gdy:** osada na poprawionych regułach radzi sobie lepiej niż na startowych, a próba zmiany punktacji kończy się odrzuceniem.

## Etap 5: Relacje między osadami

Kilka osad z własną kulturą spotyka się, handluje i buduje zaufanie.

- **Świat:** zasoby rozłożone nierówno, więc handel się opłaca. Świat wykonuje umowy i zapisuje zaufanie między osadami jako graf.
- **Mózgi:** każda osada trenuje własną sieć i wykształca własne zwyczaje. Przywódcy negocjują przez model językowy i pamiętają wcześniejsze spotkania.
- **Widok:** Unity 6 jako widok 3D, podpięty do tego samego świata.
- **Gotowe, gdy:** osady same handlują i się specjalizują, a zaufanie rośnie po dotrzymanych umowach i spada po zerwanych.

## Etap 6: Rywalizacja

Osady walczą o kurczące się zasoby i teren, zawierają sojusze i je zrywają.

- **Świat:** wyczerpujące się złoża, susze, granice terytoriów, napady, obrona i migracje. Konflikty rozstrzyga świat według stałych zasad.
- **Mózgi:** osady trenują przeciwko sobie i starszym wersjom rywali (self-play z ligą), żeby nie krążyły w kółko między tymi samymi strategiami. Sojusze i pokój negocjują przywódcy.
- **Obliczenia:** tu trening na CPU zwolni. Wtedy zmniejszamy sieci albo wynajmujemy GPU w chmurze na czas treningu.
- **Gotowe, gdy:** sojusze i konflikty powstają bez zaprogramowanych scenariuszy.

## Dalej

Gospodarkę z walutą, władzę z prawami uchwalanymi głosowaniem, rodziny i wynalazki dokładamy jako osobne moduły świata, każdy z własnymi wiadomościami w kontrakcie. Kolejność ustalimy po etapie 6, patrząc na to, co samo wyrosło w symulacji.
