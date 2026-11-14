import { defineStore } from "pinia";
let timer: ReturnType<typeof setTimeout>;
export const useUiStore = defineStore("ui", {
  state: () => ({ pending: 0, toast: "", error: "", loginVisible: false }),
  actions: {
    message(text: string, error = false) {
      this.toast = text;
      this.error = error ? text : "";
      clearTimeout(timer);
      timer = setTimeout(() => {
        this.toast = "";
        this.error = "";
      }, 4500);
    },
    openLogin() {
      this.loginVisible = true;
    },
    closeLogin() {
      this.loginVisible = false;
    },
  },
});
