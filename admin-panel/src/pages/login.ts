import { ApiError, errorMessage, login, verifyOtp, type Challenge } from "../api.js";
import { h, mount } from "../dom.js";
import { notice } from "../ui.js";

/** 1-qadam: email + parol. 2-qadam: emailga kelgan 6 xonali kod. Sessiya faqat 2-qadamdan keyin beriladi. */
export function renderLogin(root: HTMLElement, onDone: () => void): void {
  const msg = h("div", {});
  const showMsg = (kind: "error" | "ok" | "info", t: string) => mount(msg, notice(kind, t));

  const stepOne = (): void => {
    const email = h("input", { type: "email", id: "email", autocomplete: "username", required: true, placeholder: "admin email", inputmode: "email" });
    const pass = h("input", { type: "password", id: "password", autocomplete: "current-password", required: true, placeholder: "password" });
    const btn = h("button", { type: "submit", class: "btn primary block" }, "Continue");
    const form = h("form", { class: "stack", novalidate: true, onsubmit: (e: Event) => {
      e.preventDefault();
      if (!email.value.trim() || !pass.value) { showMsg("error", "Enter email and password."); return; }
      btn.disabled = true; showMsg("info", "Checking credentials…");
      login(email.value.trim(), pass.value)
        .then((c) => { pass.value = ""; stepTwo(c); })
        .catch((err) => showMsg("error", errorMessage(err)))
        .finally(() => { btn.disabled = false; });
    } },
      h("label", { class: "field", for: "email" }, h("span", { class: "field-label" }, "Email"), email),
      h("label", { class: "field", for: "password" }, h("span", { class: "field-label" }, "Password"), pass),
      btn);
    mount(panel, h("h1", {}, "Sign in"), h("p", { class: "muted" }, "Step 1 of 2 — email and password"), form, msg);
    email.focus();
  };

  const stepTwo = (c: Challenge): void => {
    const code = h("input", { id: "code", class: "otp", inputmode: "numeric", autocomplete: "one-time-code", maxlength: 6, pattern: "[0-9]{6}", placeholder: "000000", required: true });
    const btn = h("button", { type: "submit", class: "btn primary block" }, "Verify and sign in");
    const form = h("form", { class: "stack", novalidate: true, onsubmit: (e: Event) => {
      e.preventDefault();
      if (!/^\d{6}$/.test(code.value)) { showMsg("error", "Enter the 6-digit code."); return; }
      btn.disabled = true;
      verifyOtp(c.challengeId, code.value)
        .then(() => onDone())
        .catch((err) => {
          if (err instanceof ApiError && err.code === "invalid_code") {
            const left = Number(err.extra.attemptsLeft);
            showMsg("error", Number.isFinite(left) ? `Incorrect code. ${left} attempt(s) left.` : errorMessage(err));
            code.value = "";
          } else if (err instanceof ApiError && err.code === "challenge_expired") {
            showMsg("error", errorMessage(err)); stepOne();
          } else showMsg("error", errorMessage(err));
        })
        .finally(() => { btn.disabled = false; });
    } },
      h("label", { class: "field", for: "code" }, h("span", { class: "field-label" }, "6-digit code"), code),
      btn,
      h("button", { type: "button", class: "btn ghost block", onclick: () => stepOne() }, "Back"));
    mount(panel, h("h1", {}, "Check your email"), h("p", { class: "muted" }, `Step 2 of 2 — we sent a one-time code to ${c.sentTo}. It expires in a few minutes and works once.`), form, msg);
    code.focus();
  };

  const panel = h("div", { class: "glass auth-card" });
  mount(root, h("main", { class: "auth" }, h("div", { class: "brand big" }, h("span", { class: "logo" }, "⚡"), h("span", {}, "PHONEBOOST ", h("b", {}, "ADMIN"))), panel));
  stepOne();
}
