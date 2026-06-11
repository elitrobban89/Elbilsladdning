package util;

import java.util.*;

/**
 * Minimal recursive JSON parser – avoids externa bibliotek.
 * Returnerar: Map<String,Object> för objekt, List<Object> för arrayer,
 * String, Double, Long, Boolean eller null.
 */
public class SimpleJson {

    private final String src;
    private int pos;

    private SimpleJson(String src) {
        this.src = src;
        this.pos = 0;
    }

    public static Object parse(String json) {
        return new SimpleJson(json.strip()).parseValue();
    }

    private Object parseValue() {
        skipWs();
        if (pos >= src.length()) return null;
        return switch (src.charAt(pos)) {
            case '{'            -> parseObject();
            case '['            -> parseArray();
            case '"'            -> parseString();
            case 't'            -> { pos += 4; yield Boolean.TRUE; }
            case 'f'            -> { pos += 5; yield Boolean.FALSE; }
            case 'n'            -> { pos += 4; yield null; }
            default             -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWs();
        if (peek() == '}') { pos++; return map; }
        while (pos < src.length()) {
            skipWs();
            String key = parseString();
            skipWs();
            pos++; // ':'
            map.put(key, parseValue());
            skipWs();
            char sep = src.charAt(pos++);
            if (sep == '}') break;
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipWs();
        if (peek() == ']') { pos++; return list; }
        while (pos < src.length()) {
            list.add(parseValue());
            skipWs();
            char sep = src.charAt(pos++);
            if (sep == ']') break;
        }
        return list;
    }

    private String parseString() {
        pos++; // opening "
        var sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') break;
            if (c == '\\' && pos < src.length()) {
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"', '\\', '/' -> sb.append(esc);
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'u'  -> {
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default   -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Number parseNumber() {
        int start = pos;
        while (pos < src.length() && "-+0123456789.eE".indexOf(src.charAt(pos)) >= 0) pos++;
        String s = src.substring(start, pos);
        if (s.contains(".") || s.contains("e") || s.contains("E"))
            return Double.parseDouble(s);
        return Long.parseLong(s);
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }
}
