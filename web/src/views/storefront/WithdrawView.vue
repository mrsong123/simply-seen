<script setup lang="ts">
import ResourceState from "@/components/common/ResourceState.vue";
import { useMemberRecords } from "@/composables/useMemberRecords";
import PageNavigation from "@/components/common/PageNavigation.vue";
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, computed, onMounted } from "vue";
import { memberApi } from "@/api/member";
import { useAuthStore } from "@/store/auth";
import { useConfigStore } from "@/store/config";
import { withdrawalRequirements } from "@/utils/account";
import WithdrawalReadiness from "@/components/account/WithdrawalReadiness.vue";
import { useUiStore } from "@/store/ui";
import { useAction } from "@/composables/useAction";
import { createRequestKey } from "@/utils/idempotency";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
const auth = useAuthStore(),
  ui = useUiStore(),
  config = useConfigStore();
const profile = computed(() => auth.memberProfile || auth.member?.profile);
const requirements = computed(() =>
  withdrawalRequirements(profile.value || {}, config.config),
);
const blocked = computed(
  () => requirements.value.needsIdentity || requirements.value.needsWechat,
);
const records = useMemberRecords<any>("withdrawals");
const { items: withdrawals, loading, error } = records;
async function load() { await auth.fetchMemberProfile(); await records.load(); }
onMounted(() => auth.fetchMemberProfile().catch(() => {}));
const withdrawAmount = ref("");
const open = computed(() => config.config.devMode || config.config.transferEnabled);
let requestKey = createRequestKey();
const { busy, run } = useAction();
async function withdraw() {
  if (!open.value) { ui.message("提现服务当前尚未开放", true); return; }
  if (blocked.value) {
    ui.message("请先完成实名认证及微信绑定", true);
    return;
  }
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
        <ResourceState :loading="loading" :error="error" @retry="load" />
        <p v-if="!open" class="notice">提现服务当前尚未开放，历史申请仍可查看。</p>
        <div class="eyebrow">YOUR EARNINGS</div>
        <h2 class="balance">¥{{ money(profile.available) }}</h2>
        <p class="muted">
          可申请提现佣金 · 冻结 ¥{{ money(profile.reserved) }}
        </p>
        <p v-if="profile.debt" class="warning">
          待追偿 ¥{{ money(profile.debt) }}，请先联系客服核对。
        </p>
        <WithdrawalReadiness
          :needs-identity="requirements.needsIdentity"
          :needs-wechat="requirements.needsWechat"
        />
        <label class="input-label"
          >申请金额（元）<input
            v-model="withdrawAmount"
            inputmode="decimal"
            placeholder="请输入提现金额"
            :disabled="!open || blocked || busy"
        /></label>
        <p class="caption left">
          提现需经财务审核及另一名管理员执行；税额按审核后的适用税务依据确定，渠道成功前不会标记为到账。
        </p>
        <button
          class="button dark"
          :disabled="!open || blocked || busy || !withdrawAmount || !!error"
          @click="withdraw"
        >
          提交提现申请 <Icon name="ArrowRight" :size="17" />
        </button>
      </div>
      <div class="panel">
        <h3>提现记录</h3>
        <select v-if="records.states.value.length" :value="records.state.value" aria-label="提现状态" @change="records.setState(($event.target as HTMLSelectElement).value)"><option value="">全部状态</option><option v-for="state in records.states.value" :key="state" :value="state">{{ label(state) }}</option></select>
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
        <PageNavigation :records="records" label="提现" />
        <p v-if="!loading && !error && !withdrawals.length" class="muted">暂无提现记录。</p>
      </div>
    </section></template
  >
</template>
