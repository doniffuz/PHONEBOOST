import { api, errorMessage } from "../api.js";
import { h, mount } from "../dom.js";
import { fmtAmount, fmtDate } from "../format.js";
import { badge, card, loading, notice, table } from "../ui.js";

interface Row { rank: number; displayName: string | null; anonymous: boolean; amount: number; currency: string; date: string; consent: boolean }

export async function renderDonors(root: HTMLElement): Promise<void> {
  mount(root, h("div", { class: "page-head" }, h("h1", {}, "Top 10 Donors")), loading());
  try {
    const r = await api<{ items: Row[] }>("/admin/top-donors");
    mount(root, h("div", { class: "page-head" }, h("h1", {}, "Top 10 Donors")),
      notice("info", "Computed from donations with status Success only. A real name is shown publicly only when the donor chose to be named AND gave consent; otherwise the entry appears as Anonymous. Use “Hide from Top 10” on the Donations page to remove an entry."),
      card(null, table(["Rank", "Public name", "Visibility", "Total", "Last donation", "Consent"], r.items.map((d) => [
        `#${d.rank}`, d.displayName ?? "Anonymous", badge(d.anonymous ? "disabled" : "success", d.anonymous ? "Anonymous" : "Named"),
        fmtAmount(d.amount, d.currency), fmtDate(d.date), d.consent ? "Yes" : "No"]), "No successful donations yet.")));
  } catch (e) { mount(root, h("h1", {}, "Top 10 Donors"), notice("error", errorMessage(e))); }
}
