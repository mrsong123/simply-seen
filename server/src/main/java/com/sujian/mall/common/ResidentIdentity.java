package com.sujian.mall.common;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Set;

/** Mainland resident-ID syntax validation, not a substitute for the official name/ID check. */
public final class ResidentIdentity {
  private ResidentIdentity() {}

  private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
  private static final String CHECKSUM = "10X98765432";
  private static final Set<String> PROVINCES = Set.of(
      "11", "12", "13", "14", "15", "21", "22", "23", "31", "32", "33", "34", "35",
      "36", "37", "41", "42", "43", "44", "45", "46", "50", "51", "52", "53", "54",
      "61", "62", "63", "64", "65", "71", "81", "82", "91");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd")
      .withResolverStyle(ResolverStyle.STRICT);

  public static String name(String value) {
    String normalized = value == null ? "" : value.strip();
    if (!normalized.matches("[\\p{L}·• ]{2,40}") || !normalized.matches(".*\\p{L}.*"))
      throw new Problem(400, "INVALID_IDENTITY_NAME", "请填写与身份证一致的姓名");
    return normalized;
  }

  public static String number(String value) {
    String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    boolean valid = normalized.matches("[0-9]{17}[0-9X]");
    if (valid) {
      valid = PROVINCES.contains(normalized.substring(0, 2))
          && !normalized.substring(14, 17).equals("000");
      try {
        LocalDate birth = LocalDate.parse(normalized.substring(6, 14), DATE);
        valid &= birth.getYear() >= 1900
            && !birth.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")));
      } catch (DateTimeParseException failure) {
        valid = false;
      }
      int sum = 0;
      for (int index = 0; index < 17; index++) sum += (normalized.charAt(index) - '0') * WEIGHTS[index];
      valid &= normalized.charAt(17) == CHECKSUM.charAt(sum % 11);
    }
    if (!valid) throw new Problem(400, "INVALID_IDENTITY_NUMBER", "身份证号码格式或校验位不正确");
    return normalized;
  }

  public static String maskName(String name) {
    return name.substring(0, 1) + "*".repeat(Math.min(3, name.length() - 1));
  }

  public static String maskNumber(String number) {
    return number.substring(0, 3) + "************" + number.substring(15);
  }
}
