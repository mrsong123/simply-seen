package com.sujian.mall;

import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements ApplicationRunner {
  final Db d;
  final Settings c;
  final Auth auth;

  public Bootstrap(Db d, Settings c, Auth a) {
    this.d = d;
    this.c = c;
    auth = a;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    if (!c.dev()) {
      Problem.require(
          c.required("DATABASE_URL").startsWith("jdbc:mysql:"),
          "PRODUCTION_DATABASE",
          "生产环境必须使用MySQL数据库");
      for (String env :
          List.of(
              "WX_APP_ID",
              "WX_APP_SECRET",
              "WX_MCH_ID",
              "WX_MERCHANT_SERIAL",
              "WX_PRIVATE_KEY_PATH",
              "WX_PLATFORM_SERIAL",
              "WX_PLATFORM_PUBLIC_KEY_PATH",
              "WX_API_V3_KEY",
              "SUPPORT_CONTACT",
              "PRIVACY_CONTACT")) c.required(env);
      for (String env :
          List.of(
              "WX_LOGIN_URL",
              "WX_PAY_BASE_URL",
              "WX_PAY_NOTIFY_URL",
              "WX_REFUND_NOTIFY_URL",
              "PUBLIC_STORE_URL"))
        Problem.require(
            c.required(env).startsWith("https://"), "INSECURE_ENDPOINT", env + " 必须使用HTTPS");
      Problem.require(
          c.required("WX_API_V3_KEY").length() == 32, "KEY_CONFIGURATION", "API v3密钥必须为32字节");
      Problem.require(
          java.nio.file.Files.isReadable(java.nio.file.Path.of(c.required("WX_PRIVATE_KEY_PATH")))
              && java.nio.file.Files.isReadable(
                  java.nio.file.Path.of(c.required("WX_PLATFORM_PUBLIC_KEY_PATH"))),
          "KEY_CONFIGURATION",
          "商户私钥或平台公钥文件不可读");
      if (c.enabled("TRANSFER_ENABLED"))
        for (String env :
            List.of(
                "WX_TRANSFER_BASE_URL",
                "WX_TRANSFER_NOTIFY_URL",
                "WX_TRANSFER_SCENE_ID",
                "WX_TRANSFER_PERCEPTION",
                "WX_TRANSFER_REPORT_INFOS")) c.required(env);
    }
    if (d.count("SELECT COUNT(*) FROM admin_account") == 0) {
      String pass = c.required("ADMIN_BOOTSTRAP_PASSWORD");
      Problem.require(pass.length() >= 12, "WEAK_PASSWORD", "管理员初始密码至少12位");
      d.exec(
          "INSERT INTO admin_account VALUES(?,?,?,?,TRUE)",
          d.id(),
          c.get("ADMIN_BOOTSTRAP_USER", "admin"),
          auth.encodePassword(pass),
          "ADMIN");
    }
    if (d.count("SELECT COUNT(*) FROM rule_version") == 0) {
      var rule = new LinkedHashMap<String, Object>();
      rule.put("level1Bps", 500);
      rule.put("level2Bps", 200);
      rule.put("marginSharePercent", 30);
      rule.put("settlementDays", 7);
      rule.put("pointsPer100Yuan", 10);
      rule.put("freightFen", 600);
      rule.put("freeShippingFen", 19900);
      rule.put("monthlyBudgetFen", 100000);
      rule.put("monthlyCapFen", 50000);
      rule.put("monthlyPoolBps", 1000);
      rule.put("commissionBudgetFen", 1000000);
      rule.put("distributionEnabled", c.dev() || c.enabled("DISTRIBUTION_ENABLED"));
      d.exec(
          "INSERT INTO rule_version VALUES(?,'ACTIVE',?,0,0,?)", d.id(), d.encode(rule), d.now());
    }
    if (c.dev() && d.count("SELECT COUNT(*) FROM product") == 0) {
      String[] cats = {"全部好物", "居家日常", "器物茶事", "轻盈出行", "香氛护理"};
      for (int i = 1; i < cats.length; i++)
        d.exec("INSERT INTO category VALUES(?,?,?)", i, cats[i], i);
      String[][] products = {
        {"1", "云朵随行杯", "把一杯温热，带进日常", "cup", "#e8e5df", "12900", "4200", "雾白 / 350ml"},
        {"2", "山岚手冲壶", "慢一点，让风味自然发生", "kettle", "#e0e5df", "28900", "11200", "石墨 / 600ml"},
        {"1", "原野羊毛毯", "给每一个松弛的夜晚", "blanket", "#e8ded0", "39900", "16000", "燕麦 / 130×170cm"},
        {"4", "留白香氛", "木质与柑橘，自然相遇", "scent", "#e7e4db", "19800", "6500", "雪松 / 100ml"},
        {"3", "漫游帆布袋", "轻装出发，装下好心情", "bag", "#dedfd7", "15900", "5200", "苔绿 / 大号"},
        {"2", "日光陶瓷碗", "一餐一器，认真生活", "bowl", "#ece4da", "8900", "2800", "砂白 / 6寸"}
      };
      for (int i = 0; i < products.length; i++) {
        var p = products[i];
        long id = i + 1;
        d.exec(
            "INSERT INTO product VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            id,
            Integer.parseInt(p[0]),
            p[1],
            p[2],
            "以简洁的线条与细腻的质感，回应生活的真实需要。精选材质，注重耐用与手感。\n规格："
                + p[7]
                + "\n养护：温和清洁，置于阴凉通风处。\n售后：支持依法适用的七日无理由退货；质量问题请在订单页申请售后。",
            "/art/" + p[3] + ".svg",
            p[4],
            "ON_SALE",
            i < 3,
            20,
            7,
            d.now());
        d.exec(
            "INSERT INTO sku VALUES(?,?,?,?,?,?,?,0,0)",
            id * 10,
            id,
            p[7],
            "SJ-" + id + "-A",
            Long.parseLong(p[5]),
            Long.parseLong(p[6]),
            100);
        d.exec(
            "INSERT INTO sku VALUES(?,?,?,?,?,?,?,0,0)",
            id * 10 + 1,
            id,
            "限定礼盒装",
            "SJ-" + id + "-B",
            Long.parseLong(p[5]) + 3000,
            Long.parseLong(p[6]) + 1000,
            50);
      }
    }
  }
}
