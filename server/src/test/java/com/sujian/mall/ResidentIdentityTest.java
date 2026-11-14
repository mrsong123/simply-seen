package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.common.Problem;
import com.sujian.mall.common.ResidentIdentity;
import org.junit.jupiter.api.Test;

class ResidentIdentityTest {
  static String number(String base) {
    int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    int sum = 0;
    for (int index = 0; index < 17; index++) sum += (base.charAt(index) - '0') * weights[index];
    return base + "10X98765432".charAt(sum % 11);
  }

  @Test
  void checksDateProvinceSequenceAndChecksumBeforeCallingAnExternalService() {
    String valid = number("11010519900101001");
    assertEquals(valid, ResidentIdentity.number(valid.toLowerCase()));
    for (String invalid : new String[] {
        valid.substring(0, 17) + (valid.charAt(17) == '0' ? '1' : '0'),
        number("11010519900230001"), number("99010519900101001"),
        number("11010520900101001"), number("11010519900101000"), "123"})
      assertThrows(Problem.class, () -> ResidentIdentity.number(invalid));
  }

  @Test
  void keepsOnlyMaskedValuesInDisplayHelpersAndValidatesName() {
    String valid = number("11010519900101001");
    assertEquals("测**", ResidentIdentity.maskName("测试人"));
    assertEquals(18, ResidentIdentity.maskNumber(valid).length());
    assertFalse(ResidentIdentity.maskNumber(valid).contains(valid.substring(6, 14)));
    assertEquals("测试人", ResidentIdentity.name(" 测试人 "));
    assertThrows(Problem.class, () -> ResidentIdentity.name("<script>"));
    assertThrows(Problem.class, () -> ResidentIdentity.name("1234"));
  }
}
