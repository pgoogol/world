# Etap 5: Relacje między osadami

Status: plan wstępny. Doprecyzujemy go po odbiorze etapu 4.

## Cel

Kilka osad z własną kulturą spotyka się, handluje i buduje zaufanie. Handel i specjalizacja mają powstać same, bo zasoby leżą nierówno, a nie dlatego, że ktoś je zaprogramował.

## Założenia z wcześniejszych etapów

- Osada ma własną sieć (etap 3) i własne reguły (etap 4). W etapie 5 każda osada trenuje i poprawia się osobno.
- Kontrakt z mózgami niesie identyfikator osady (etap 3) i przebiegu (etap 4).
- Kronika w bazie pamięta historię (etap 3). Przywódcy korzystają z niej jako z pamięci spotkań.

## Świat

- **3–5 osad** na większej planszy podzielonej na krainy. Każda postać należy do jednej osady.
- **Nierówne zasoby.** Każda kraina ma nadmiar jednego zasobu i brak innego, na przykład las bez żyznej ziemi albo pola bez drewna. Dochodzi co najmniej jeden nowy zasób, żeby było czym handlować.
- **Umowy.** Osady składają oferty wymiany. Świat trzyma towar obu stron w depozycie i wykonuje wymianę, gdy obie strony dostarczą swoje. Umowę da się zerwać: strona nie dostarcza towaru w terminie.
- **Zaufanie.** Świat zapisuje dotrzymane i zerwane umowy jako graf między osadami. Zaufanie jest liczone ze stałego wzoru w Javie i widoczne dla obu stron.
- **Posłańcy.** Towar przenoszą postacie, więc handel kosztuje czas i ludzi.

## Mózgi

- **Sieć na osadę.** Osady uczą się równolegle, każda na własnych doświadczeniach. Zwyczaje mają prawo się rozejść.
- **Przywódca.** Jedna postać w osadzie negocjuje. Decyzje przywódcy są rzadkie: raz na porę roku albo przy spotkaniu.
- **Negocjacje przez model językowy.** Przywódca dostaje stan osady, graf zaufania i historię spotkań z drugą stroną, a odpowiada ofertą w ustalonym formacie JSON. Świat przyjmuje tylko oferty zgodne ze schematem.
- **Negocjator bez modelu.** Prosty negocjator z regułami napisanymi ręcznie służy jako punkt odniesienia i zastępstwo, gdy model nie odpowiada.

## Widok

- **Strumień stanu świata.** Świat wystawia stan przez gRPC jako osobną usługę tylko do odczytu. To formalizuje zasadę „widok tylko czyta”.
- **Unity 6** jako widok 3D, klient tego samego strumienia w C#. JavaFX zostaje jako widok roboczy.

## Kroki

- [ ] **5.1 Specyfikacja i kontrakt.** Krainy, przynależność do osady, nowy zasób, usługa widoku w proto.
- [ ] **5.2 Wiele osad w jednym świecie.** Krainy, nierówne zasoby, osobne magazyny i reguły każdej osady.

  *Gotowe, gdy:* każda osada osobno przeżywa zimę, ale żadna nie ma wszystkiego, czego potrzebuje do wzrostu.

- [ ] **5.3 Umowy i depozyt.** Oferta, przyjęcie, dostawa, wykonanie, zerwanie.

  *Gotowe, gdy:* każda ścieżka umowy ma test, a towar nie znika ani się nie mnoży.

- [ ] **5.4 Graf zaufania.** Zapis dotrzymanych i zerwanych umów, stały wzór zaufania, historia w bazie.
- [ ] **5.5 Trening wielu osad.** Kilka sieci naraz, pomiar tempa, osada kontrolna odcięta od handlu.

  *Gotowe, gdy:* znamy tempo dla 3 i 5 osad i wiemy, ile trwa przebieg odbioru.

- [ ] **5.6 Negocjator bez modelu.** Oferty z reguł, pełna pętla handlu bez LM Studio.

  *Gotowe, gdy:* osady z prostym negocjatorem wymieniają towary i rosną szybciej niż osada odcięta od handlu.

- [ ] **5.7 Przywódcy z modelem językowym.** Oferty w schemacie JSON, pamięć spotkań z kroniki, limit czasu.
- [ ] **5.8 Strumień stanu świata.** Usługa widoku, JavaFX przeniesiony na strumień.

  *Gotowe, gdy:* okno JavaFX działa jako osobny proces i pokazuje to samo co dziś.

- [ ] **5.9 Widok w Unity 6.** Klient C# strumienia, plansza 3D, osady, posłańcy, graf zaufania.
- [ ] **5.10 Odbiór etapu.**

## Kryteria ukończenia etapu

- Osady handlują bez zaprogramowanego scenariusza: wymiana pojawia się w przebiegu i rośnie.
- Osady się specjalizują: każda produkuje więcej zasobu swojej krainy, niż zużywa, a brakujący sprowadza.
- Zaufanie rośnie po dotrzymanych umowach i spada po zerwanych, a oferty wobec strony o niskim zaufaniu są ostrożniejsze.
- Osady handlujące radzą sobie lepiej niż ta sama osada odcięta od handlu.
- Etapy 1–4 dają wyniki identyczne ze swoimi odbiorami.

## Ryzyka

- **Handel może się nie opłacać.** Jeśli każda osada przeżyje sama, nikt nie wyśle posłańca. Krok 5.2 stroi nierówność zasobów tak, żeby samotna osada stała w miejscu.
- **Wiele sieci na CPU.** Tempo spadnie proporcjonalnie do liczby osad. Możliwe wyjście to mniejsze sieci albo trening osad na zmianę.
- **Model językowy jako przywódca jest wolny.** Dlatego decyduje rzadko, a krok 5.6 daje działający handel bez niego.
- **Unity to osobny projekt.** Nowy język, nowe narzędzia, osobny build. Krok 5.8 oddziela go od symulacji, więc opóźnienie w Unity nie blokuje reszty.

## Do decyzji

- Ile osad na start? Domyślnie 3.
- Czy przywódcę wybiera świat? Domyślnie tak: najstarsza żyjąca postać osady.
- Czy osady widzą cudzy magazyn? Domyślnie nie. Znają tylko oferty i graf zaufania.
- Jeden serwer mózgów czy osobny na osadę? Domyślnie jeden z wieloma sieciami. Osobne procesy, gdy pomiar pokaże zysk.
- Czy JavaFX zostaje po wejściu Unity? Domyślnie tak, jako widok do pracy nad symulacją.
