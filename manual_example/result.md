## Overall Takeaways & Recent Focus

1. **Non-blocking Async Architecture:** Projekt intensywnie wykorzystuje asynchroniczne API `java.net.http.HttpClient` (dostępne od Javy 11). Kluczowe jest zrozumienie reaktywnego przetwarzania strumieni (`BodyPublisher`, `BodySubscriber`), aby unikać blokowania wątków I/O.
2. **Feature Development:** Ostatnie zmiany w `ResponseInterceptor.java` i `ProxyListener.java` sugerują rozbudowę możliwości monitorowania i modyfikacji ruchu (observability).
3. **JDK Compatibility Improvements:** Klasa `HttpClientUtils` pokazuje walkę z różnicami w implementacji `HttpClient` w różnych wersjach JDK, szczególnie w kontekście nagłówków zabronionych (restricted headers).
4. **Technology Stack:**
   - **Java 11+** (wymagana przez `java.net.http`).
   - **Mu Server** (jako serwer aplikacyjny).
   - **Maven** (budowanie).
   - **JUnit 4 & Hamcrest** (testy).

## Potential Complexity/Areas to Note

- **Asynchroniczne przetwarzanie Body:** W `ReverseProxy.java` znajduje się złożona logika kopiowania i przesyłania fragmentów (`ByteBuffer`) ciała żądania i odpowiedź. Należy zwrócić uwagę na synchronizację i obsługę błędów w callbackach `asyncHandle` oraz subskrybentach Flow.
- **Obsługa nagłówków Hop-by-Hop:** Projekt musi ręcznie zarządzać nagłówkami, które nie powinny być przekazywane dalej (np. `Connection`, `Transfer-Encoding`). Lista `REPRESSED` w `ReverseProxy.java` definiuje te reguły.
- **Error Handling & Timeouts:** Mapowanie wyjątków z `HttpClient` na odpowiednie kody HTTP (np. 504 Gateway Timeout) oraz obsługa anulowania `CompletableFuture` w przypadku rozłączenia klienta.

## Questions for the Team

1. Jakie są główne przypadki użycia (use-cases) dla `ResponseInterceptor`? Czy używamy tego do modyfikacji statusów błędów z backendu?
2. Czy planujemy migrację testów z JUnit 4 na JUnit 5, biorąc pod uwagę, że projekt wymaga Javy 11+?
3. Jak wygląda strategia testowania wydajnościowego proxy przy dużym obciążeniu (backpressure w `Flow.Subscriber`)?
4. Dlaczego w `HttpClientUtils` musimy ręcznie ustawiać propertisy systemowe dla JDK (`jdk.httpclient.allowRestrictedHeaders`) i czy to nie powoduje konfliktów w środowiskach współdzielonych?
5. Jak obsługiwane są Websockety? Czy `murp` wspiera ich proxy'owanie (w kodzie widać odniesienia do HTTP/2 i streamingu, ale warto dopytać o WS)?

## Next Steps

1. **Zbuduj projekt:** Uruchom `mvn clean install` aby upewnić się, że środowisko jest poprawne i wszystkie testy przechodzą.
2. **Przeanalizuj `ManualTest.java`:** To świetny punkt startowy, który uruchamia lokalny serwer docelowy i serwer proxy. Pozwoli Ci to zobaczyć działanie biblioteki "na żywo" i poeksperymentować z `UriMapper`.
3. **Zgłęb `ReverseProxy.java`:** Skup się na metodzie `handle`. Prześledź drogę żądania od momentu wejścia do `MuServer`, przez zbudowanie `HttpRequest` do targetu, aż po asynchroniczne pompowanie odpowiedzi z powrotem do klienta.
4. **Sprawdź obsługę nagłówków:** Zobacz w `HttpClientUtils`, jak radzimy sobie z nagłówkiem `Host`, co jest częstym źródłem problemów w reverse proxy.

## Development Environment Setup

1. **Prerequisites:**
   - JDK 11 lub nowsze (projekt używa `java.net.http`).
   - Maven 3.x.
2. **Dependency Installation:** `mvn install` (standardowy cykl Mavena).
3. **Building the Project:** `mvn clean install` (zbuduje słoik i uruchomi testy).
4. **Running the Application:** Możesz uruchomić przykłady bezpośrednio z IDE lub z linii komend, np. kompilując i uruchamiając klasę `src/test/java/Example.java` lub `src/test/java/ManualTest.java`.
5. **Running Tests:** `mvn test` lub poprzez IDE (IntelliJ/Eclipse).
6. **Common Issues:** Uważaj na wersję JDK. Mimo że Travis ma konfigurację dla JDK 8, `pom.xml` i kod źródłowy (moduły Java 9+, `var`, `HttpClient`) wyraźnie wskazują na wymóg Javy 11+.

## Helpful Resources

- **Documentation:** [https://muserver.io/murp](https://muserver.io/murp) (oficjalna dokumentacja).
- **Issue Tracker:** [https://github.com/3redronin/murp/issues](https://github.com/3redronin/murp/issues).
- **Source Code:** [https://github.com/3redronin/murp](https://github.com/3redronin/murp).
- **Mu Server Docs:** [https://muserver.io](https://muserver.io) (zrozumienie serwera, na którym to działa, jest kluczowe).