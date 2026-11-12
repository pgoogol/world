# Etap 4: Przepisywanie reguł

Status: plan wstępny. Doprecyzujemy go po odbiorze etapu 3, bo zależy od tego, jak wyjdzie uczenie z opóźnioną nagrodą i jakie zdarzenia trafią do kroniki.

## Cel

Osada sama poprawia swoje zasady i zostawia tylko te, które działają. Reguły są danymi, a nie kodem: osada proponuje zmianę, świat sprawdza ją na kopii i przyjmuje albo odrzuca.

## Założenia z wcześniejszych etapów

- Świat umie zapisać i wczytać swój stan (etap 3). Sędzia korzysta z tego, żeby puścić dwa warianty z tego samego punktu.
- Kronika w bazie opisuje historię osady (etap 3). Z niej powstaje tekst dla modelu językowego.
- Osada ma jedną sieć (etap 3). Na czas próby sieć jest zamrożona, żeby różnicę robiła reguła, a nie dalsza nauka.

## Jak to działa

- **Reguła** to rekord JSON: warunek i skutek z zamkniętej listy. Przykłady: „głodny sąsiad dostaje porcję od postaci z zapasem”, „30% plonów trafia do magazynu”, „zimą pierwszeństwo w schronieniu mają najmłodsi”.
- **Zamknięta lista** jest jedyną bramą. Skutek może przesuwać przedmioty między postaciami i magazynem, ustalać kolejność i limity. Nie może zmieniać fizyki świata, nagród ani punktacji osady, bo takich skutków na liście nie ma.
- **Punktacja osady** żyje w Javie i jest stała: liczba żyjących, przeżyte zimy, zapas na koniec roku.
- **Propozycje** pochodzą z dwóch źródeł. Najpierw losowe mutacje: zmiana progu, dodanie albo usunięcie reguły. Potem model językowy z LM Studio, który czyta kronikę ostatniego roku i uzasadnia zmianę. Model proponuje raz na porę roku.
- **Sędzia** kopiuje bieżący stan, puszcza starą i nową wersję reguł przez kilka pór roku na kilku ziarnach i zostawia lepszą. Każda decyzja z uzasadnieniem i wynikiem trafia do historii wersji.

## Kroki

- [ ] **4.1 Specyfikacja i język reguł.** Schemat JSON, lista warunków i skutków, walidacja, wersjonowanie w bazie.

  *Gotowe, gdy:* poprawna reguła przechodzi walidację, a reguła spoza listy jest odrzucana z nazwą błędu.

- [ ] **4.2 Wykonywanie reguł w świecie.** Warstwa instytucji osady nad zasadami świata.

  *Gotowe, gdy:* każdy warunek i skutek ma test, a osada bez reguł zachowuje się identycznie jak w etapie 3.

- [ ] **4.3 Sędzia.** Kopia stanu, dwa warianty reguł, zamrożona sieć, kilka ziaren, stała punktacja. Kontrakt z mózgami dostaje identyfikator przebiegu, żeby jeden serwer obsłużył kilka kopii świata.

  *Gotowe, gdy:* sędzia dwa razy z rzędu daje ten sam werdykt dla tej samej pary reguł, a regułę wyraźnie szkodliwą odrzuca.

- [ ] **4.4 Losowe mutacje.** Generator propozycji i pętla: propozycja, próba, decyzja, zapis.

  *Gotowe, gdy:* po kilkunastu rundach osada ma zestaw reguł z wyższą punktacją niż startowy.

- [ ] **4.5 Kronika jako tekst.** Streszczenie ostatniego roku z bazy: liczby, najważniejsze zdarzenia, obowiązujące reguły i ich wyniki.

  *Gotowe, gdy:* streszczenie mieści się w limicie modelu i zawiera wszystko, czego potrzeba do propozycji.

- [ ] **4.6 Model językowy.** Klient serwera LM Studio zgodnego z API OpenAI, odpowiedź wymuszona schematem JSON, limit czasu, propozycja raz na porę roku, zastępstwo losową mutacją przy błędzie.

  *Gotowe, gdy:* model proponuje poprawne reguły z uzasadnieniem, a symulacja nie staje, gdy model nie odpowiada.

- [ ] **4.7 Próba oszustwa.** Testy z regułami, które próbują zmienić punktację, nagrody albo fizykę, także ukryte w poprawnie wyglądającym JSON-ie i w tekście uzasadnienia.

  *Gotowe, gdy:* każda taka próba kończy się odrzuceniem przed uruchomieniem.

- [ ] **4.8 Widok.** Obowiązujące reguły, historia wersji, ostatnia propozycja z uzasadnieniem i werdyktem.

- [ ] **4.9 Odbiór etapu.**

## Kryteria ukończenia etapu

- Osada na poprawionych regułach ma wyższą punktację niż na startowych, na ziarnach, których sędzia nie widział.
- Próba zmiany punktacji kończy się odrzuceniem.
- Propozycje modelu językowego wygrywają z losowymi mutacjami albo wiemy z pomiaru, że nie wygrywają.
- Etapy 1–3 dają wyniki identyczne ze swoimi odbiorami.

## Ryzyka

- **Sędzia jest drogi.** Każda propozycja to kilka przebiegów po kilka pór roku. Przy tempie z etapu 3 jedna decyzja może trwać minuty. Liczba ziaren i długość próby to parametry do strojenia.
- **Szum.** Różnica między regułami bywa mniejsza niż rozrzut między ziarnami. Sędzia przyjmuje zmianę dopiero przy wyraźnej przewadze.
- **Lokalny model odpowiada wolno i bywa niepoprawny.** Stąd schemat JSON, limit czasu i zastępstwo mutacją.
- **Reguły mogą nie mieć na co wpływać.** Jeśli osada z etapu 3 jest zbyt prosta, zamknięta lista skutków okaże się za wąska. Wtedy poszerzamy listę, nie bramę.

## Do decyzji

- Gdzie wykonują się reguły? Domyślnie w świecie (Java), jako warstwa instytucji. Mózgi widzą tylko ich skutki.
- Czy sieć uczy się w czasie próby? Domyślnie nie.
- Jaki model w LM Studio? Domyślnie najmniejszy, który trzyma schemat JSON. Wybór po próbie w kroku 4.6.
- Czy reguły dotyczą też mózgów, na przykład podziału pracy? Domyślnie nie w tym etapie.
