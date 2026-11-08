import { api, errorMessage } from "../api.js";
import { h, mount } from "../dom.js";
import { fmtDate, isHttpsUrl } from "../format.js";
import { badge, busy, card, field, loading, notice, table, toggle } from "../ui.js";

interface Row {
  version_code: number; version_name: string; changelog: string; download_url: string; force_update: boolean;
  min_supported_code: number; published: boolean; created_at: string;
}

export async function renderAppUpdate(root: HTMLElement): Promise<void> {
  const msg = h("div", {});
  const formHost = h("div", {});
  const list = h("div", {});
  let items: Row[] = [];

  const renderForm = (p?: Row): void => {
    const latest = items.filter((x) => x.published).sort((a, b) => b.version_code - a.version_code)[0];
    const code = h("input", { type: "number", min: 1, step: 1, value: String(p?.version_code ?? (latest ? latest.version_code + 1 : 1)) });
    const name = h("input", { type: "text", maxlength: 30, value: p?.version_name ?? "" });
    const min = h("input", { type: "number", min: 0, step: 1, value: String(p?.min_supported_code ?? 0) });
    const notes = h("textarea", { rows: 4, maxlength: 2000 }); notes.value = p?.changelog ?? "";
    const url = h("input", { type: "url", maxlength: 500, value: p?.download_url ?? "", placeholder: "https://…" });
    const force = toggle("Force update (users cannot skip it)", p?.force_update ?? false);
    const pub = toggle("Published", p?.published ?? true);
    const btn = h("button", { class: "btn primary", type: "submit" }, "Save version");
    const hint = h("p", { class: "muted" }, "");
    const refresh = (): void => {
      const c = Number(code.value), m = Number(min.value);
      hint.textContent = m > c ? "Minimum supported version cannot be higher than this version."
        : force.input.checked ? "Everyone below this version must update."
        : m > 0 ? `Optional update — but apps older than version code ${m} are forced to update.` : "Optional update for everyone.";
    };
    [code, min].forEach((i) => i.addEventListener("input", refresh)); force.input.addEventListener("change", refresh); refresh();
    mount(formHost, card(p ? `Edit version ${p.version_code}` : "Publish a version", h("form", { class: "stack", novalidate: true, onsubmit: (e: Event) => {
      e.preventDefault();
      const c = Number(code.value), m = Number(min.value);
      if (!Number.isInteger(c) || c < 1 || !name.value.trim()) { mount(msg, notice("error", "Version code and name are required.")); return; }
      if (!Number.isInteger(m) || m < 0 || m > c) { mount(msg, notice("error", "Minimum supported version must be between 0 and this version code.")); return; }
      if (url.value.trim() && !isHttpsUrl(url.value.trim())) { mount(msg, notice("error", "Update URL must start with https://")); return; }
      busy(btn, async () => {
        try {
          await api("/admin/version", { method: "POST", body: { versionCode: c, versionName: name.value.trim(), minSupportedVersionCode: m, releaseNotes: notes.value, downloadUrl: url.value.trim(), forceUpdate: force.input.checked, published: pub.input.checked } });
          mount(msg, notice("ok", "Saved.")); await load();
        } catch (err) { mount(msg, notice("error", errorMessage(err))); }
      });
    } },
      h("div", { class: "grid two" }, field("Version code", code, "Integer from build.gradle"), field("Version name", name, "e.g. 0.5.0")),
      field("Minimum supported version code", min, "Apps below this must update. 0 = no minimum."),
      field("Release notes", notes), field("Update URL", url, "HTTPS download page / APK / store link"),
      force.el, pub.el, hint, btn)));
  };

  const load = async (): Promise<void> => {
    mount(list, loading());
    try {
      items = (await api<{ items: Row[] }>("/admin/version")).items;
      const latestCode = Math.max(0, ...items.filter((x) => x.published).map((x) => x.version_code));
      mount(list, card("Versions", table(["Code", "Name", "Min supported", "Type", "Status", "Notes", "Created", ""],
        items.map((v) => [
          v.version_code, v.version_name, v.min_supported_code || "—",
          badge(v.force_update ? "failed" : "info", v.force_update ? "Force" : "Optional"),
          badge(v.published ? (v.version_code === latestCode ? "success" : "disabled") : "pending", v.published ? (v.version_code === latestCode ? "Latest" : "Published") : "Unpublished"),
          v.changelog ? v.changelog.slice(0, 80) : "—", fmtDate(v.created_at),
          h("button", { class: "btn ghost sm", onclick: () => { renderForm(v); window.scrollTo({ top: 0, behavior: "smooth" }); } }, "Edit")]), "No versions yet.")));
      renderForm();
    } catch (e) { mount(list, notice("error", errorMessage(e))); }
  };

  mount(root, h("div", { class: "page-head" }, h("h1", {}, "App Update")), msg, formHost, list);
  await load();
}
