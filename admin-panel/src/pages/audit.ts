import { api, errorMessage } from "../api.js";
import { h, mount } from "../dom.js";
import { fmtDate } from "../format.js";
import { badge, card, loading, notice, table } from "../ui.js";

interface Row { id: number; admin_email: string; action: string; target: string | null; details: Record<string, unknown>; created_at: string; result: string }

export async function renderAudit(root: HTMLElement): Promise<void> {
  let offset = 0;
  const body = h("div", {});
  const load = async (): Promise<void> => {
    mount(body, loading());
    try {
      const r = await api<{ offset: number; items: Row[] }>(`/admin/audit-logs?offset=${offset}`);
      const prev = h("button", { class: "btn ghost", disabled: offset === 0, onclick: () => { offset = Math.max(0, offset - 100); void load(); } }, "← Newer");
      const next = h("button", { class: "btn ghost", disabled: r.items.length < 100, onclick: () => { offset += 100; void load(); } }, "Older →");
      mount(body, card(null,
        table(["Time", "Admin", "Action", "Target", "Result", "Details"], r.items.map((x) => [
          fmtDate(x.created_at), x.admin_email, x.action, x.target ?? "—", badge(x.result === "success" ? "success" : "failed", x.result),
          h("code", { class: "details" }, Object.keys(x.details ?? {}).length ? JSON.stringify(x.details) : "—")]), "No audit entries."),
        h("div", { class: "row end" }, prev, next)));
    } catch (e) { mount(body, notice("error", errorMessage(e))); }
  };
  mount(root, h("div", { class: "page-head" }, h("h1", {}, "Audit Logs")),
    notice("info", "Append-only. Passwords, one-time codes, card numbers and tokens are never written here."), body);
  await load();
}
