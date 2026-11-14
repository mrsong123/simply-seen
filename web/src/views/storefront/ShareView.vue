<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, computed, onMounted } from "vue";
import { memberApi } from "@/api/member";
import { useAuthStore } from "@/store/auth";
import { useConfigStore } from "@/store/config";
import { useUiStore } from "@/store/ui";
import { useAction } from "@/composables/useAction";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
const auth = useAuthStore(),
  config = useConfigStore(),
  ui = useUiStore();
const profile = computed(() => auth.memberProfile || auth.member?.profile);
const team = ref<any>({ level1: [], level2: [], commissions: [] }),
  accepted = ref(false);
const { busy, run } = useAction();
const pendingCommission = computed(() =>
  team.value.commissions
    .filter((x: any) => x.state === "PENDING")
    .reduce((sum: number, x: any) => sum + x.amount, 0),
);
async function load() {
  await auth.fetchMemberProfile();
  team.value = await memberApi.getTeam();
}
onMounted(() => load().catch(() => {}));
async function promote() {
  await run(async () => {
    await memberApi.acceptPromotion(accepted.value);
    await load();
    ui.message("推广规则已确认");
  });
}
async function copyInvite() {
  try {
    await navigator.clipboard.writeText(
      (config.config.shareBase || location.origin) +
        "/?invite=" +
        profile.value.invite_code,
    );
    ui.message("邀请链接已复制");
  } catch {
    ui.message("邀请码：" + profile.value.invite_code);
  }
}
</script>
<template>
  <AccountHeader title="好物，值得分享。" /><template v-if="profile"
    ><section class="section narrow">
      <div class="share-card">
        <div class="eyebrow">GOOD THINGS ARE BETTER SHARED</div>
        <h2>分享喜欢，收获心意。</h2>
        <p>
          推荐真实好物，在有效成交后获得佣金。<br />不收取入门费用，不因邀请注册获得现金奖励。
        </p>
        <template v-if="profile.promoter"
          ><div class="invite-code">{{ profile.invite_code }}</div>
          <button class="button dark" @click="copyInvite">
            <Icon name="Copy" :size="16" /> 复制邀请链接
          </button></template
        ><template v-else
          ><label class="checkbox-label"
            ><input type="checkbox" v-model="accepted" /><span
              >我已阅读<RouterLink to="/legal/distribution"
                >《推广服务规则》</RouterLink
              ></span
            ></label
          ><button
            class="button dark"
            :disabled="!accepted || busy"
            @click="promote"
          >
            成为好物分享者
          </button></template
        >
      </div>
      <div class="asset-grid">
        <div class="asset-card">
          <span>直接邀请</span><strong>{{ team.level1.length }}</strong
          ><small>位朋友</small>
        </div>
        <div class="asset-card">
          <span>二级归因</span><strong>{{ team.level2.length }}</strong
          ><small>位朋友</small>
        </div>
        <div class="asset-card">
          <span>待结算佣金</span><strong>¥{{ money(pendingCommission) }}</strong
          ><small>以有效成交及售后结果为准</small>
        </div>
      </div>
      <div class="panel">
        <h3>成交佣金明细</h3>
        <div v-for="t in team.commissions" :key="t.id" class="history-row">
          <div>
            <strong
              >{{ t.level_no === 1 ? "一级" : "二级" }}成交佣金
              <span class="badge">{{ label(t.state) }}</span></strong
            >
            <p>{{ t.order_id }} · {{ date(t.created_at) }}</p>
          </div>
          <span>¥{{ money(t.amount) }}</span>
        </div>
        <p v-if="!team.commissions.length" class="muted">尚无有效成交佣金。</p>
      </div>
      <div class="panel">
        <h3>我的直接邀请</h3>
        <div v-for="m in team.level1" :key="m.id" class="history-row">
          <span>{{ m.name }}</span
          ><small>{{ date(m.created_at) }}</small>
        </div>
        <p v-if="!team.level1.length" class="muted">
          用你的专属邀请链接，分享给真正喜欢好物的朋友。
        </p>
      </div>
    </section></template
  >
</template>
