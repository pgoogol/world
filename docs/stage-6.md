# Etap 6: Rywalizacja

Status: plan wstępny. Doprecyzujemy go po odbiorze etapu 5.

## Cel

Osady walczą o kurczące się zasoby i teren, zawierają sojusze i je zrywają. Sojusze i konflikty mają powstać bez zaprogramowanych scenariuszy.

## Założenia z wcześniejszych etapów

- Wiele osad, umowy, graf zaufania i negocjacje przywódców działają (etap 5).
- Reguły osad są danymi i poprawia je sędzia (etap 4).
- Każda osada ma własną sieć (etapy 3 i 5).

## Świat

- **Niedobór.** Złoża się wyczerpują, a co kilka lat przychodzi susza: jedzenie odrasta wolniej na całej planszy albo w jednej krainie.
- **Terytoria.** Osada ma granice, które rosną z jej budowlami. Zasób w cudzych granicach należy do właściciela.
- **Napady i obrona.** Postać może zabrać zasób z cudzego terytorium albo magazynu. Świat rozstrzyga starcie według stałych zasad: liczba napastników, liczba obrońców, umocnienia. Wynik jest powtarzalny dla tego samego seeda.
- **Migracje.** Postać albo grupa może opuścić osadę i dołączyć do innej albo założyć nową.
- **Sojusze i pokój.** Nowe rodzaje umów obok handlowych: pakt o nieagresji, wspólna obrona. Zerwanie obniża zaufanie mocniej niż zerwana wymiana.

## Mózgi

- **Trening przeciwko rywalom.** Osady uczą się przeciwko sobie i przeciwko zapisanym starszym wersjom rywali. Liga zapisanych wersji chroni przed krążeniem w kółko między tymi samymi strategiami.
- **Ocena siły.** Każda zapisana wersja sieci ma ocenę liczoną z wyników starć w lidze.
- **Przywódcy** negocjują sojusze i pokój tym samym kanałem co handel w etapie 5.

## Obliczenia

Trening wielu sieci przeciwko lidze nie zmieści się w rozsądnym czasie na CPU. Krok 6.6 mierzy to i kończy się decyzją: mniejsze sieci albo wynajęty GPU w chmurze na czas treningu.

## Kroki

- [ ] **6.1 Specyfikacja i kontrakt.** Nowe akcje, typy umów i zdarzenia.
- [ ] **6.2 Niedobór.** Wyczerpywanie złóż, susze, pomiar tego, jak osady z etapu 5 znoszą niedobór bez walki.

  *Gotowe, gdy:* każda zasada ma test, a niedobór realnie zmusza osady do wyboru.

- [ ] **6.3 Terytoria.** Granice, własność zasobów, widoczność granic dla postaci.
- [ ] **6.4 Napady i obrona.** Akcje, stałe zasady rozstrzygania, umocnienia jako nowa budowla.

  *Gotowe, gdy:* każda zasada starcia ma test, a wynik zależy tylko od stanu świata i seeda.

- [ ] **6.5 Migracje.** Odejście, dołączenie, założenie osady, przeniesienie wiedzy postaci.
- [ ] **6.6 Budżet obliczeń.** Pomiar treningu jednej rundy ligi, decyzja o wielkości sieci i o GPU.

  *Gotowe, gdy:* wiemy, ile trwa i kosztuje przebieg odbioru w obu wariantach.

- [ ] **6.7 Liga.** Zapis wersji sieci, dobór przeciwników, ocena siły, trening przeciwko lidze.

  *Gotowe, gdy:* nowe wersje wygrywają ze starszymi częściej, niż przegrywają, a ocena siły rośnie.

- [ ] **6.8 Sojusze i pokój.** Nowe umowy w negocjacjach przywódców, skutki zerwania w grafie zaufania.
- [ ] **6.9 Wykrywanie zjawisk.** Miary liczone z kroniki, bez scenariuszy: trwałe pary osad działających razem, okresy napadów, zmiany granic, fale migracji.

  *Gotowe, gdy:* miary odróżniają przebieg z sojuszem od przebiegu bez niego na przykładach przygotowanych ręcznie.

- [ ] **6.10 Widok.** Granice, starcia, sojusze, oś czasu konfliktów.
- [ ] **6.11 Odbiór etapu.**

## Kryteria ukończenia etapu

- Sojusze i konflikty powstają w przebiegach bez zaprogramowanych scenariuszy, co pokazują miary z kroku 6.9.
- Te same osady przy obfitości zasobów walczą rzadziej niż przy niedoborze.
- Liga nie krąży w kółko: najnowsze wersje wygrywają z dużo starszymi.
- Etapy 1–5 dają wyniki identyczne ze swoimi odbiorami.

## Ryzyka

- **Wynik może być jednostajny.** Osady mogą nauczyć się tylko napadać albo tylko unikać się nawzajem. Wtedy stroimy koszty napadu i zysk z sojuszu i opisujemy to wprost.
- **Koszt obliczeń.** To pierwszy etap, w którym CPU może nie wystarczyć. Decyzja o GPU zapada na podstawie pomiaru, a nie z góry.
- **„Powstaje samo” trudno udowodnić.** Dlatego miary z kroku 6.9 definiujemy przed treningiem, a nie po obejrzeniu wyników.
- **Powtarzalność na GPU.** Trening na karcie graficznej może nie dać identycznych przebiegów. Zasadę 5 z `CLAUDE.md` trzeba będzie wtedy zawęzić do świata i do przebiegów na CPU.

## Do decyzji

- Czy postać może zginąć w starciu? Domyślnie tak, z małym prawdopodobieństwem zależnym od przewagi.
- Czy napad na magazyn jest możliwy od początku? Domyślnie tak, a umocnienia go utrudniają.
- Ile wersji trzyma liga? Domyślnie 10 na osadę.
- Gdzie trenować, gdy CPU nie wystarczy? Domyślnie wynajęty GPU na czas treningu, a pokaz i odbiór lokalnie na zapisanych sieciach.
