package se.elitrobban.elbilsladdning.config;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Besökarens IP för rate limiting, och jämförelse av admin-nyckeln.
 *
 * <p>Läser {@code CF-Connecting-IP}, inte {@code X-Forwarded-For}. Render ligger bakom
 * Cloudflare, och Cloudflare skriver alltid över {@code CF-Connecting-IP} med den IP som
 * faktiskt anslöt. {@code X-Forwarded-For} släpps däremot igenom som klienten skickade den
 * (Render lägger bara till sin egen post på slutet), så den första posten var fritt vald av
 * anroparen, och en ny påhittad IP per anrop kringgick rate limiten helt.
 *
 * <p>Saknas headern (lokal körning, tester) används anslutningens egen adress.
 *
 * @author Robert Andersson Kopler
 */
public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        return request.getRemoteAddr();
    }

    /** Admin-nyckeln jämförs i konstant tid: {@code String.equals} avbryter vid första olika
     *  tecken, så svarstiden avslöjade hur långt in en gissning stämde. */
    public static boolean isAdminUnauthorized(String adminKey, String key) {
        if (key == null || adminKey == null || adminKey.isBlank()) return true;
        return !MessageDigest.isEqual(adminKey.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8));
    }
}
