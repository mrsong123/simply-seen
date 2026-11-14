import { installRequestHooks } from "@/bootstrap/http";
import { createApp } from "vue";
import App from "./App.vue";
import { pinia } from "@/store";
import { router } from "@/router";
import "./style.css";
installRequestHooks();
createApp(App).use(pinia).use(router).mount("#app");
