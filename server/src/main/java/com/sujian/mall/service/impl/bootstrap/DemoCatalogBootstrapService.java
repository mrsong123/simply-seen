package com.sujian.mall.service.impl.bootstrap;

import com.sujian.mall.common.*;
import com.sujian.mall.config.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DemoCatalogBootstrapService {
  @org.springframework.beans.factory.annotation.Autowired private CategoryMapper categoryMapper;
  @org.springframework.beans.factory.annotation.Autowired private ProductMapper productMapper;
  @org.springframework.beans.factory.annotation.Autowired private SkuMapper skuMapper;

  private final BusinessSupport d;
  private final Settings c;

  public DemoCatalogBootstrapService(BusinessSupport d, Settings c) {
    this.d = d;
    this.c = c;
  }

  public void initialize() {
    if (c.dev() && productMapper.countForRun() == 0) {
      String[] cats = {"全部好物", "居家日常", "器物茶事", "轻盈出行", "香氛护理"};
      for (int i = 1; i < cats.length; i++) categoryMapper.insertForCategory(i, cats[i], i);
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
        productMapper.insertForSaveProduct(
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
        skuMapper.insertCatalogSku(
            id * 10, id, p[7], "SJ-" + id + "-A", Long.parseLong(p[5]), Long.parseLong(p[6]), 100);
        skuMapper.insertCatalogSku(
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
