<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import PageLoadState from "../../components/PageLoadState.vue";
import { useAppStore } from "../../store/app";
import { useTask } from "../../utils/useTask";
const app = useAppStore(),
  type = ref("terms");
const { busy, run } = useTask();
const loaded = ref(false), loadError = ref(false);
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
  void reload();
});
async function reload() {
  await run(async () => {
    loadError.value = false;
    try {
      await app.load(true);
      loaded.value = true;
    } catch (error) {
      loadError.value = true;
      throw error;
    }
  });
}
async function copySupport() {
  await run(async () => {
    const data = app.config?.support?.trim();
    if (!data) throw new Error("联系方式暂不可用，请重新读取后再试");
    await new Promise<void>((resolve, reject) =>
      uni.setClipboardData({ data, success: () => resolve(), fail: reject }),
    );
  });
}
</script>
<template>
  <PageShell
    ><PageHeading :title="title" />
    <PageLoadState :loading="busy && !loaded" :failed="loadError" @retry="reload" />
    <view v-if="loaded && !loadError" class="box"
      ><template v-if="type === 'privacy'"
        ><text class="description"
          >我们仅处理服务必需的信息：微信身份标识用于登录和绑定；经您主动请求发送的短信验证码用于手机号登录或绑定，首次验证的新手机号会注册独立会员。手机号与短信发送参数会提供给短信服务商，验证码有有效期、单次使用及频率限制，不用于营销推送。实名认证会在取得您单独同意后，将姓名和大陆居民身份证号码提供给认证服务商核验一致性，用于账户安全与提现准入，不包含人脸或活体识别。服务端加密保存必要信息，页面只展示脱敏结果；姓名和身份证原文不写入小程序本地缓存，提交后或离开页面即清空表单。开发模拟结果不代表真实认证，不可用于生产提现。
          收货人、手机与地址用于订单履约，交易和账本用于售后及法定义务。收货信息仅在履约需要时提供给物流方，支付通过微信商户服务处理。推广者不能查看被邀请会员的地址、手机、证件及微信标识。手机号与微信已有独立会员时，不自动合并资产及邀请关系。您可以在地址页修改或删除地址。信息查询、复制、撤回授权及注销请联系
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
        ><button class="secondary" :disabled="busy" @tap="copySupport">
          复制联系方式
        </button></template
      ><template v-else
        ><text class="description"
          >使用手机号验证码登录须同意服务规则与隐私政策，新手机号首次验证会自动注册。已有微信账户请先微信登录并绑定手机号，以保留原会员积分、资金与邀请归属。短信码不得转交他人。实名认证仅核验姓名和身份证号码一致性，不含人脸、活体或微信收款同名验证；真实提现须满足服务端实名及微信绑定校验。开发测试登录、短信与认证不代表真实身份或真实资金交易。
          价格、运费、积分限额由服务端报价，下单时再次校验。未付订单超时后经查单及关单确认，再释放库存和积分。支付以渠道结果为准。每满100元合格商品现金消费赠{{
            app.rules?.pointsPer100Yuan
          }}积分，按单取整，运费及积分抵扣部分不再计赠。积分不可提现或转赠，按商品限额每点抵1元。订单支持按商品数量申请部分及全额退款，审核后原路退款，最终以渠道成功为准。依法适用七日无理由退货及商品质量售后权利。任何争议请联系
          {{ app.config?.support }}。</text
        ></template
      ></view
    ></PageShell
  >
</template>
