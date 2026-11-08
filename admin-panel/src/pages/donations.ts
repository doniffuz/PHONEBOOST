import { api, errorMessage } from "../api.js";
import { h, mount } from "../dom.js";
import { fmtAmount, fmtDate } from "../format.js";
import { badge, busy, card, confirmDialog, loading, notice, stat, table } from "../ui.js";

interface Row {
  id: number; reference: string; amount: number; currency: string; provider: string; status: string;
  donor_name: string | null; anonymous: boolean; consent: boolean; hidden: boolean; created_at: string; updated_at: string;
}
interface Resp { days: number; successCount: number; successTotal: number; pendingCount: number; currency: string; items: Row[] }
const GATEWAYS = ["click", "payme", "uzum", "tbc"];
const PROVIDER_LABEL: Record<string, string> = { manual_card: "Manual card", qr: "QR", click: "Click", payme: "Payme", uzum: "Uzum", tbc: "TBC" };

export async function renderDonations(root: HTMLElement): Promise<void> {
  let status = "";
  const msg = h("div", {});
  const body = h("div", {});

  const act = async (id: number, patch: Record<string, unknown>): Promise<void> => {
    try { await api("/admin/donations", { method: "POST", body: { id, ...patch } }); mount(msg, notice("ok", "Saved.")); await load(); }
    catch (e) { mount(msg, notice("error", errorMessage(e))); }
  };

  const load = async (): Promise<void> => {
    mount(body, loading());
    try {
      const r = await api<Resp>(`/admin/donations?days=90${status ? `&status=${status}` : ""}`);
      mount(body,
        h("div", { class: "grid stats" },
          stat("Successful (90 days)", r.successCount, fmtAmount(r.successTotal, r.currency), "good"),
          stat("Pending review", r.pendingCount, "Needs a manual check", r.pendingCount ? "warn" : "")),
        card(null, table(["Reference", "Amount", "Provider", "Status", "Donor", "Public name", "Created", "Actions"], r.items.map((d) => {
          const pending = d.status === "pending";
          const gateway = GATEWAYS.includes(d.provider);
          const btns = [
            pending && h("button", { class: "btn good sm", disabled: gateway, title: gateway ? "Provider not configured — cannot be marked successful" : "Only after you verified the money arrived", onclick: (e: Event) => {
              if (!confirmDialog(`Mark ${fmtAmount(d.amount, d.currency)} (${d.reference}) as SUCCESS? Do this only after you confirmed the payment in your bank/merchant account.`)) return;
              busy(e.currentTarget as HTMLButtonElement, () => act(d.id, { status: "success" }));
            } }, "Success"),
            pending && h("button", { class: "btn danger sm", onclick: (e: Event) => busy(e.currentTarget as HTMLButtonElement, () => act(d.id, { status: "failed" })) }, "Failed"),
            pending && h("button", { class: "btn ghost sm", onclick: (e: Event) => busy(e.currentTarget as HTMLButtonElement, () => act(d.id, { status: "cancelled" })) }, "Cancel"),
            d.status === "success" && h("button", { class: "btn ghost sm", onclick: (e: Event) => busy(e.currentTarget as HTMLButtonElement, () => act(d.id, { hidden: !d.hidden })) }, d.hidden ? "Show in Top 10" : "Hide from Top 10"),
          ];
          return [
            h("code", {}, d.reference), fmtAmount(d.amount, d.currency),
            h("span", {}, PROVIDER_LABEL[d.provider] ?? d.provider, gateway ? " ⚠" : ""),
            badge(d.status),
            d.donor_name ? d.donor_name : "—",
            d.anonymous ? "Anonymous" : d.consent ? "Allowed" : "No consent (shown as Anonymous)",
            fmtDate(d.created_at), h("div", { class: "row wrap" }, btns)];
        }), "No donations match.")));
    } catch (e) { mount(body, notice("error", errorMessage(e))); }
  };

  const sel = h("select", { class: "select", onchange: () => { status = sel.value; void load(); } },
    [["", "All statuses"], ["pending", "Pending"], ["success", "Success"], ["failed", "Failed"], ["cancelled", "Cancelled"]].map(([v, l]) => h("option", { value: v }, l)));
  mount(root, h("div", { class: "page-head" }, h("h1", {}, "Donations"), sel),
    notice("info", "Nothing is ever marked successful automatically. Manual card and QR payments stay Pending until you verify them and click Success. Click/Payme/Uzum/TBC have no merchant integration yet, so they cannot be marked Success."),
    msg, body);
  await load();
}
