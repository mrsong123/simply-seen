<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, computed, onMounted } from "vue";
import { memberApi } from "@/api/member";
import { useAuthStore } from "@/store/auth";
import { useUiStore } from "@/store/ui";
import { useAction } from "@/composables/useAction";
import { createRequestKey } from "@/utils/idempotency";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
const auth = useAuthStore(),
  ui = useUiStore();
const profile = computed(() => auth.memberProfile || auth.member?.profile);
const withdrawals = ref<any[]>([]),
  withdrawAmount = ref("");
let requestKey = createRequestKey();
const { busy, run } = useAction();
async function load() {
  await auth.fetchMemberProfile();
  withdrawals.value = await memberApi.getWithdrawals();
}
onMounted(() => load().catch(() => {}));
async function withdraw() {
  await run(async () => {
    const amount = Number(withdrawAmount.value);
    if (
      !Number.isFinite(amount) ||
      amount <= 0 ||
      !/^\d+(\.\d{1,2})?$/.test(withdrawAmount.value)
    ) {
      ui.message("请输入最多两位小数的金额", true);
      return;
    }
    await memberApi.createWithdrawal(Math.round(amount * 100), requestKey);
    requestKey = createRequestKey();
    withdrawAmount.value = "";
    await load();
    ui.message("提现申请已提交，等待财务复核");
  });
}
</script>
<template>
  <AccountHeader title="佣金提现" /><template v-if="profile"
    ><section class="section bag-layout">
      <div class="panel">
        <div class="eyebrow">YOUR EARNINGS</div>
        <h2 class="balance">¥{{ money(profile.available) }}</h2>
        <p class="muted">
          可申请提现佣金 · 冻结 ¥{{ money(profile.reserved) }}
        </p>
        <p v-if="profile.debt" class="warning">
          待追偿 ¥{{ money(profile.debt) }}，请先联系客服核对。
        </p>
        <label class="input-label"
          >申请金额（元）<input
            v-model="withdrawAmount"
            inputmode="decimal"
            placeholder="请输入提现金额"
        /></label>
        <p class="caption left">
          提现需经财务审核及另一名管理员执行；税额按审核后的适用税务依据确定，渠道成功前不会标记为到账。
        </p>
        <button
          class="button dark"
          :disabled="busy || !withdrawAmount"
          @click="withdraw"
        >
          提交提现申请 <Icon name="ArrowRight" :size="17" />
        </button>
      </div>
      <div class="panel">
        <h3>提现记录</h3>
        <div v-for="r in withdrawals" :key="r.id" class="withdraw-history">
          <div class="summary-line">
            <strong>¥{{ money(r.gross) }}</strong
            ><span class="badge">{{ label(r.state) }}</span>
          </div>
          <p>税款 ¥{{ money(r.tax) }} · 税后 ¥{{ money(r.net) }}</p>
          <small>{{ date(r.created_at) }}</small>
          <p v-if="r.reason">{{ r.reason }}</p>
          <p v-if="r.state === 'CONFIRMING'" class="warning">
            请在微信小程序内确认收款。
          </p>
        </div>
        <p v-if="!withdrawals.length" class="muted">暂无提现记录。</p>
      </div>
    </section></template
  >
</template>
