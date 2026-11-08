import { api, errorMessage } from "../api.js";
import { h, mount } from "../dom.js";
import { fmtDate } from "../format.js";
import { card, loading, notice, table } from "../ui.js";

interface Row { install_id: string; app_version: string | null; android_sdk: number | null; created_at: string }

export async function renderUsers(root: HTMLElement): Promise<void> {
  mount(root, h("div", { class: "page-head" }, h("h1", {}, "Users")), loading());
  try {
    const r = await api<{ days: number; items: Row[] }>("/admin/users?days=30");
    mount(root, h("div", { class: "page-head" }, h("h1", {}, "Users")),
      notice("info", "PHONEBOOST has no user accounts. This list shows anonymous app installs only (partial install ID, app version, Android SDK). No names, emails or phone numbers are collected."),
      card(`New installs · last ${r.days} days`, table(["Install", "App version", "Android SDK", "First seen"],
        r.items.map((u) => [u.install_id, u.app_version ?? "—", u.android_sdk ?? "—", fmtDate(u.created_at)]))));
  } catch (e) { mount(root, h("h1", {}, "Users"), notice("error", errorMessage(e))); }
}
