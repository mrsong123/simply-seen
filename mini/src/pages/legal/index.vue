<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import { useAppStore } from "../../store/app";
import { useTask } from "../../utils/useTask";
const app = useAppStore(),
  type = ref("terms");
const { run } = useTask();
const title = computed(
  () =>
    ({
      privacy: "隐私与信息保护",
      distribution: "推广服务规则",
      support: "联系素见",
      terms: "交易与售后规则",
    })[type.value] || "交易与售后规则",
);
onLoad((options) => {
  type.value = options?.type || "terms";
  void run(() => app.load());
});
function copySupport() {
  uni.setClipboardData({ data: app.config?.support || "" });
}
</script>
<template>
  <PageShell
    ><PageHeading :title="title" /><view class="box"
      ><template v-if="type === 'privacy'"
        ><text class="description"
          >我们仅处理服务必需的信息：微信身份标识用于登录，收货人、手机与地址用于订单履约，交易和账本用于售后及法定义务。收货信息仅在履约需要时提供给物流方，支付通过微信商户服务处理。推广者不能查看被邀请会员的地址、手机及微信标识。您可以在地址页修改或删除地址。信息查询、复制、撤回授权及注销请联系
          {{
            app.config?.privacy
          }}。未结交易与依法需留存的财务记录按法律义务处理。</text
        ></template
      ><template v-else-if="type === 'distribution'"
        ><text class="description"
          >DIST-1.0：首次注册固定邀请归属，最多两级，不收取入门费，不强制消费或囤货，无拉人头奖励，自邀及虚假成交不计佣。一级基准{{
            (app.rules?.level1Bps || 0) / 100
          }}%，二级{{
            (app.rules?.level2Bps || 0) / 100
          }}%，实际受商品佣金比例、现金实收、可分配毛利和预算约束。运费与积分抵扣不计佣。确认收货后{{
            app.rules?.settlementDays
          }}天且无进行中的售后，才可结算；退款依原订单比例冲回，已出款不足冲回则生成追偿，新佣金优先抵偿。提现须双人复核并以渠道成功为准。不承诺固定收益，不得宣传消费解锁或保本回报。</text
        ></template
      ><template v-else-if="type === 'support'"
        ><text class="description"
          >{{
            app.config?.support
          }}。订单售后请通过订单详情提交申请。个人信息查询、复制及注销请联系
          {{ app.config?.privacy }}。</text
        ><button class="secondary" @tap="copySupport">
          复制联系方式
        </button></template
      ><template v-else
        ><text class="description"
          >价格、运费、积分限额由服务端报价，下单时再次校验。未付订单超时后经查单及关单确认，再释放库存和积分。支付以渠道结果为准。每满100元合格商品现金消费赠{{
            app.rules?.pointsPer100Yuan
          }}积分，按单取整，运费及积分抵扣部分不再计赠。积分不可提现或转赠，按商品限额每点抵1元。订单支持按商品数量申请部分及全额退款，审核后原路退款，最终以渠道成功为准。依法适用七日无理由退货及商品质量售后权利。任何争议请联系
          {{ app.config?.support }}。</text
        ></template
      ></view
    ></PageShell
  >
</template>
