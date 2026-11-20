package com.sujian.mall.config.env;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads configuration data only; shell substitution and variable interpolation are never evaluated. */
public final class DotenvParser {
  private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private DotenvParser() {}

  public static Map<String, Object> parse(String text) {
    if (text.startsWith("\uFEFF")) text = text.substring(1);
    if (text.indexOf('\0') >= 0) throw invalid(1);
    String[] lines = text.split("\\r?\\n", -1);
    var result = new LinkedHashMap<String, Object>();
    for (int index = 0; index < lines.length; index++) {
      int line = index + 1;
      String entry = lines[index].strip();
      if (entry.isEmpty() || entry.startsWith("#")) continue;
      if (entry.startsWith("export ") || entry.startsWith("export\t")) entry = entry.substring(7).stripLeading();
      int equals = entry.indexOf('=');
      if (equals < 1) throw invalid(line);
      String name = entry.substring(0, equals).strip();
      if (!NAME.matcher(name).matches()) throw invalid(line);
      String value = entry.substring(equals + 1).stripLeading();
      if (value.startsWith("\"") || value.startsWith("'")) {
        char quote = value.charAt(0);
        var raw = new StringBuilder(value.substring(1));
        int end = closing(raw, quote);
        while (end < 0 && ++index < lines.length) {
          raw.append('\n').append(lines[index]);
          end = closing(raw, quote);
        }
        if (end < 0) throw invalid(line);
        String trailing = raw.substring(end + 1).strip();
        if (!trailing.isEmpty() && !trailing.startsWith("#")) throw invalid(line);
        String contents = raw.substring(0, end);
        result.put(name, quote == '\'' ? contents : decode(contents));
      } else {
        char inner = 0;
        boolean escaped = false;
        for (int at = 0; at < value.length(); at++) {
          char current = value.charAt(at);
          if (inner == 0 && (current == '\'' || current == '"')) inner = current;
          else if (inner != 0 && current == inner && !escaped) inner = 0;
          else if (inner == 0 && current == '#' && (at == 0 || Character.isWhitespace(value.charAt(at - 1)))) {
            value = value.substring(0, at);
            break;
          }
          escaped = inner == '"' && current == '\\' && !escaped;
        }
        result.put(name, value.stripTrailing());
      }
    }
    return result;
  }

  private static int closing(CharSequence value, char quote) {
    boolean escaped = false;
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (current == quote && !escaped) return i;
      if (quote == '"' && current == '\\' && !escaped) escaped = true;
      else escaped = false;
    }
    return -1;
  }

  private static String decode(String value) {
    var decoded = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (current == '\\' && i + 1 < value.length()) {
        char next = value.charAt(++i);
        switch (next) {
          case 'n' -> decoded.append('\n');
          case 'r' -> decoded.append('\r');
          case 't' -> decoded.append('\t');
          case '"', '\\' -> decoded.append(next);
          default -> decoded.append('\\').append(next);
        }
      } else decoded.append(current);
    }
    return decoded.toString();
  }

  private static IllegalStateException invalid(int line) {
    return new IllegalStateException("配置文件第" + line + "行格式错误（配置内容不会输出）");
  }
}
