<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
import { useAppStore } from "./store/app";
const app = useAppStore();
onLaunch(() => {
  app.load().catch(() => undefined);
});
</script>
<style>
@import "./styles/base.css";
@import "./styles/catalog.css";
@import "./styles/trade.css";
@import "./styles/member.css";
page {
  background: #f6f7f1;
  color: #344432;
  font-family: -apple-system, "PingFang SC", sans-serif;
  font-size: 28rpx;
}
button {
  font-size: 26rpx;
}
button::after {
  border: 0;
}
view,
text,
button,
input,
textarea {
  box-sizing: border-box;
}
</style>
