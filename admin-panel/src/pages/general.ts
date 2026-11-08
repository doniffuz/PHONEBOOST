import { api, errorMessage } from "../api.js";
import { h, mount } from "../dom.js";
import { busy, card, field, loading, notice, toggle } from "../ui.js";
import { loadSession } from "../session.js";

interface Settings { maintenance: { enabled: boolean; message: string } }

export async function renderGeneral(root: HTMLElement): Promise<void> {
  mount(root, h("div", { class: "page-head" }, h("h1", {}, "General Settings")), loading());
  const msg = h("div", {});
  try {
    const s = await api<Settings>("/admin/settings");
    const on = toggle("Maintenance mode (shown to app users)", s.maintenance.enabled);
    const text = h("textarea", { rows: 3, maxlength: 300 }); text.value = s.maintenance.message;
    const btn = h("button", { class: "btn primary", type: "submit" }, "Save");
    mount(root, h("div", { class: "page-head" }, h("h1", {}, "General Settings")), msg,
      card("Maintenance", h("form", { class: "stack", onsubmit: (e: Event) => {
        e.preventDefault();
        busy(btn, async () => {
          try { await api("/admin/settings", { method: "POST", body: { maintenance: { enabled: on.input.checked, message: text.value } } }); mount(msg, notice("ok", "Saved.")); }
          catch (err) { mount(msg, notice("error", errorMessage(err))); }
        });
      } }, on.el, field("Message", text), btn)),
      card("Session", h("p", {}, `Signed in as ${loadSession()?.email ?? "—"}`),
        h("p", { class: "muted" }, `API: ${window.PB_ADMIN_CONFIG?.apiBase ?? "not configured"}`),
        h("p", { class: "muted" }, "Sessions end after 8 hours, or after 30 minutes without activity in this tab.")));
  } catch (e) { mount(root, h("h1", {}, "General Settings"), notice("error", errorMessage(e))); }
}
