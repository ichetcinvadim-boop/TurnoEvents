package pro.turnoworld.events;

import java.util.List;
import java.util.Locale;

public record Question(String text, List<String> answers, long points) {
    public boolean matches(String input) {
        String value = normalize(input);
        return answers.stream().map(Question::normalize).anyMatch(value::equals);
    }
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replace('ё', 'е').replaceAll("\\s+", " ");
    }
}
