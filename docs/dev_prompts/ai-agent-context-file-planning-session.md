Jesteś starszym inżynierem oprogramowania i architektem systemów AI. Twoim zadaniem jest pomoc w zaprojektowaniu logicznego przepływu (flow) oraz promptów do LLM dla User Story **US-019: Generate AI Agent Context File** w ramach projektu "Repo Onboarder".

Twoim celem jest wygenerowanie listy pytań i rekomendacji technicznych, które pozwolą precyzyjnie zdefiniować algorytm tworzenia zoptymalizowanego pliku kontekstowego, uwzględniając ograniczenia modeli językowych (LLM) oraz użyteczność dla asystentów kodowania (np. GitHub Copilot, Cursor).

Prosimy o uważne zapoznanie się z poniższymi informacjami:

<product_requirements>
@.ai/prd.md
</product_requirements>

<tech_stack>
Spring AI
</tech_stack>

Przeanalizuj dostarczone informacje, koncentrując się na logice biznesowej, przetwarzaniu plików i optymalizacji danych. Rozważ następujące kwestie:

1.  **Strategia selekcji treści:** Jak odróżnić pliki kluczowe dla zrozumienia projektu od plików drugoplanowych? Jak wykorzystać analizę "hotspots" (z US-022) do priorytetyzacji.
2.  **Poziom szczegółowości:** Kiedy dołączać pełny kod, a kiedy tylko sygnatury metod/klas (AST parsing vs raw text).
3.  **Optymalizacja tokenów:** Techniki kompresji kontekstu (usuwanie komentarzy, białych znaków, importów) bez utraty znaczenia semantycznego.
4.  **Format wyjściowy:** Struktura pliku (np. XML-like tags, Markdown, JSON), która jest najlepiej interpretowana przez obecne modele AI.
5.  **Bezpieczeństwo:** Wykrywanie i maskowanie potencjalnych sekretów/kluczy API w kodzie źródłowym przed dodaniem do kontekstu.
6.  **Skalowalność procesu:** Obsługa dużych repozytoriów w pamięci (streaming vs buforowanie).

Na podstawie analizy wygeneruj listę 10 pytań i zaleceń w formie łączonej (pytanie + zalecenie techniczne). Powinny one dotyczyć wszelkich niejasności, edge-case'ów lub decyzji architektonicznych niezbędnych do implementacji w Javie (Spring Boot).

Rozważ pytania dotyczące:
1.  Hierarchii ważności plików (np. `pom.xml` > `Utils.java`).
2.  Formatu metadanych dla każdego pliku (ścieżka, język, rozmiar).
3.  Obsługi plików konfiguracyjnych vs kodu źródłowego.
4.  Limitów wielkości generowanego pliku kontekstowego.
5.  Sposobu reprezentacji struktury katalogów (drzewo vs lista płaska).
6.  Strategii "graceful degradation" gdy repozytorium jest zbyt duże.

Dane wyjściowe powinny mieć następującą strukturę:

<pytania>
Wymień tutaj swoje pytania i zalecenia, ponumerowane dla przejrzystości:

Na przykład:
1. W jaki sposób powinniśmy traktować pliki testowe (katalog `src/test`) w kontekście dla AI?

Rekomendacja: Zalecam domyślne wykluczenie ciał metod testowych, pozostawiając jedynie nazwy klas i metod testowych (jako dokumentacja zachowania), chyba że testy stanowią jedyną dokumentację usage examples. Pozwoli to zaoszczędzić tokeny na kod produkcyjny.
</pytania>

Pamiętaj, że Twoim celem jest dostarczenie kompleksowej listy pytań i rekomendacji technicznych, które pomogą w stworzeniu robustnego mechanizmu generowania kontekstu. Skoncentruj się na jasności, trafności i wykonalności w Javie/Spring AI. Nie dołączaj żadnych dodatkowych komentarzy ani wyjaśnień poza określonym formatem wyjściowym.

Kontynuuj ten proces, generując nowe pytania i rekomendacje w oparciu o przekazany kontekst i odpowiedzi użytkownika, dopóki użytkownik wyraźnie nie poprosi o podsumowanie.

