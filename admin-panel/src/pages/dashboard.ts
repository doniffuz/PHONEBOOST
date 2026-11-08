import { api, errorMessage } from "../api.js";
import { h, mount } from "../dom.js";
import { fmtAmount } from "../format.js";
import { card, loading, notice, stat } from "../ui.js";

interface Totals { count: number; total: number }
interface Stats {
  days: number; users: number; devices: number; optimizations: number; gameBoosts: number; diagnostics: number;
  donations: number; errors: number; appVersion: string | null; maintenance: boolean;
  donationTotals: { currency: string; today: Totals; week: Totals; month: Totals };
}
interface Series { days: number; count: number; avgScore: number | null; series: Array<{ date: string; count: number }> }

export async function renderDashboard(root: HTMLElement): Promise<void> {
  let days = 7;
  const body = h("div", {});
  const load = async (): Promise<void> => {
    mount(body, loading());
    try {
      const [s, opt] = await Promise.all([api<Stats>(`/admin/stats?days=${days}`), api<Series>(`/admin/optimization?days=${days}`)]);
      const t = s.donationTotals;
      const max = Math.max(1, ...opt.series.map((p) => p.count));
      mount(body,
        h("div", { class: "grid stats" },
          stat("Active installs", s.users, `last ${s.days} days (anonymous)`),
          stat("New installs", s.devices, `last ${s.days} days`),
          stat("Optimizations", s.optimizations),
          stat("Game boosts", s.gameBoosts),
          stat("Diagnostics", s.diagnostics),
          stat("Successful donations", s.donations, `last ${s.days} days`, "good"),
          stat("Errors reported", s.errors, "", s.errors > 0 ? "warn" : ""),
          stat("Latest app version", s.appVersion ?? "—", s.maintenance ? "Maintenance mode ON" : "Maintenance off", s.maintenance ? "warn" : "")),
        card("Confirmed donations (success only)",
          h("div", { class: "grid stats" },
            stat("Today", fmtAmount(t.today.total, t.currency), `${t.today.count} donation(s)`, "good"),
            stat("Last 7 days", fmtAmount(t.week.total, t.currency), `${t.week.count} donation(s)`, "good"),
            stat("Last 30 days", fmtAmount(t.month.total, t.currency), `${t.month.count} donation(s)`, "good"))),
        card(`Optimizations per day${opt.avgScore !== null ? ` · average score ${opt.avgScore}` : ""}`,
          opt.series.length === 0 ? h("p", { class: "empty" }, "No optimization data yet.") :
            h("div", { class: "bars", role: "img", "aria-label": "Optimizations per day" }, opt.series.map((p) =>
              h("div", { class: "bar-col", title: `${p.date}: ${p.count}` },
                h("div", { class: "bar", style: { height: `${Math.max(4, Math.round((p.count / max) * 100))}%` } }),
                h("div", { class: "bar-label" }, p.date.slice(5)))))));
    } catch (e) { mount(body, notice("error", errorMessage(e))); }
  };
  const sel = h("select", { class: "select", onchange: () => { days = Number(sel.value); void load(); } },
    [7, 30, 90].map((d) => h("option", { value: String(d) }, `Last ${d} days`)));
  mount(root, h("div", { class: "page-head" }, h("h1", {}, "Dashboard"), sel), body);
  await load();
}
