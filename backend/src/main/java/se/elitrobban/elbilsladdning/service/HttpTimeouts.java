package se.elitrobban.elbilsladdning.service;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient med tidsgränser.
 *
 * {@code RestClient.create()} har VARKEN anslutnings- eller lästidsgräns: svarar en källa
 * aldrig väntar tråden i all evighet. Uppmätt 2026-08-30 mot /api/stations: ett anrop stod
 * öppet i över nio minuter utan att komma tillbaka, medan ett annat med samma parametrar
 * svarade på en sekund. En hängning är värre än ett fel — ett fel går att visa för
 * användaren, en hängning ser ut som att appen slutat fungera.
 *
 * Talen är satta efter vad en användare orkar vänta på ett sökresultat, inte efter vad
 * källorna klarar i bästa fall: anslutningen ska ta bråkdelar av en sekund, och en källa
 * som behöver mer än åtta sekunder på sig hinner ändå inte med i svaret.
 */
public final class HttpTimeouts {

    private HttpTimeouts() {}

    public static RestClient restClient(Duration connect, Duration read) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return RestClient.builder().requestFactory(factory).build();
    }

    /** Standardgränserna för de källor som ligger i stationssökningens väg. */
    public static RestClient restClient() {
        return restClient(Duration.ofSeconds(4), Duration.ofSeconds(8));
    }
}
