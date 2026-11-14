<script setup lang="ts"></script>
<template>
  <footer class="store-footer">
    <div class="footer-top">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark">素</span
        ><span>素见好物<small>SIMPLY SEEN</small></span></RouterLink
      >
      <p>让好物回归日常，让生活简单而丰盈。</p>
      <span>LESS, BUT BETTER.</span>
    </div>
    <div class="footer-bottom">
      <small>© {{ new Date().getFullYear() }} 素见好物</small>
      <div>
        <RouterLink to="/legal/privacy">隐私政策</RouterLink
        ><RouterLink to="/legal/terms">交易与售后</RouterLink
        ><RouterLink to="/legal/distribution">推广规则</RouterLink
        ><RouterLink to="/admin">管理后台</RouterLink>
      </div>
    </div>
  </footer>
</template>
