package se.elitrobban.elbilsladdning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/** @author Robert Andersson Kopler */
@Configuration
public class CorsConfig {

    /**
     * Sidor som får anropa chatten från en besökares webbläsare. Övriga /api/** är öppna för
     * alla sajter (laddstationer och priser är publik data), men chatten kostar Groq-kvot:
     * med {@code *} kunde vem som helst bygga in den på sin egen sida.
     *
     * <p>Appens egen adress måste stå med: Render avslutar TLS före appen, så Tomcat ser
     * {@code http} medan webbläsarens Origin är {@code https}, och Spring behandlar då även
     * egna anrop som cross-origin. caradvice.onrender.com serverar en kopia av ev-app.js.
     *
     * <p>CORS stoppar bara webbläsare. Skript och curl skickar vad de vill, och mot dem är
     * det rate limiten som skyddar.
     */
    static final List<String> CHAT_ORIGINS = List.of(
            "https://elbilsladdning.onrender.com",
            "https://caradvice.onrender.com",
            "https://elitrobban.se",
            "https://www.elitrobban.se",
            "http://localhost:[*]");

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration chat = new CorsConfiguration();
        chat.setAllowedOriginPatterns(CHAT_ORIGINS);
        chat.addAllowedMethod("POST");
        chat.addAllowedMethod("OPTIONS");
        chat.addAllowedHeader("*");

        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");

        // Ordningen avgör: källan returnerar första mönstret som matchar
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/chat/**", chat);
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
