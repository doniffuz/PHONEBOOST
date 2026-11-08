import { api, errorMessage } from "../api.js";
import { h, mount } from "../dom.js";
import { fmtDate, fromLocalInput, isHttpsUrl, toLocalInput } from "../format.js";
import { badge, busy, card, confirmDialog, field, loading, notice, toggle } from "../ui.js";

interface Row { id: number; title: string; message: string; image_url: string | null; publish_at: string; active: boolean; created_at: string }

export async function renderAnnouncements(root: HTMLElement): Promise<void> {
  const msg = h("div", {});
  const list = h("div", {});
  const formHost = h("div", {});
  let editing: Row | null = null;

  const save = async (b: Record<string, unknown>): Promise<boolean> => {
    try { await api("/admin/announcements", { method: "POST", body: b }); mount(msg, notice("ok", "Saved.")); return true; }
    catch (e) { mount(msg, notice("error", errorMessage(e))); return false; }
  };

  const renderForm = (): void => {
    const e = editing;
    const title = h("input", { type: "text", maxlength: 120, value: e?.title ?? "", placeholder: "Title" });
    const message = h("textarea", { rows: 4, maxlength: 500, placeholder: "Message (required)" });
    message.value = e?.message ?? "";
    const image = h("input", { type: "url", maxlength: 500, value: e?.image_url ?? "", placeholder: "https://… (optional)" });
    const when = h("input", { type: "datetime-local", value: toLocalInput(e?.publish_at ?? null) });
    const active = toggle("Published (visible in the app)", e?.active ?? false);
    const btn = h("button", { class: "btn primary", type: "submit" }, e ? "Save changes" : "Create announcement");
    mount(formHost, card(e ? `Edit #${e.id}` : "New announcement", h("form", { class: "stack", novalidate: true, onsubmit: (ev: Event) => {
      ev.preventDefault();
      if (!message.value.trim()) { mount(msg, notice("error", "Message is required.")); return; }
      if (image.value.trim() && !isHttpsUrl(image.value.trim())) { mount(msg, notice("error", "Image URL must start with https://")); return; }
      const body: Record<string, unknown> = {
        ...(e ? { id: e.id } : {}), title: title.value.trim(), message: message.value.trim(), imageUrl: image.value.trim() || null,
        active: active.input.checked,
      };
      const iso = fromLocalInput(when.value);
      if (iso) body.publishAt = iso;
      busy(btn, async () => { if (await save(body)) { editing = null; renderForm(); await load(); } });
    } },
      field("Title", title), field("Message", message), field("Image URL", image, "Optional. HTTPS only."),
      field("Publish date/time", when, "Leave empty to publish immediately when Published is on."), active.el,
      h("div", { class: "row" }, btn, e ? h("button", { type: "button", class: "btn ghost", onclick: () => { editing = null; renderForm(); } }, "Cancel") : null))));
  };

  const load = async (): Promise<void> => {
    mount(list, loading());
    try {
      const r = await api<{ items: Row[] }>("/admin/announcements");
      mount(list, card("All announcements", r.items.length === 0 ? h("p", { class: "empty" }, "No announcements yet.") :
        h("div", { class: "stack" }, r.items.map((a) => {
          const scheduled = a.active && Date.parse(a.publish_at) > Date.now();
          return h("article", { class: "item" },
            h("div", { class: "row between wrap" },
              h("strong", {}, a.title || "(no title)"),
              badge(scheduled ? "pending" : a.active ? "success" : "disabled", scheduled ? "Scheduled" : a.active ? "Published" : "Unpublished")),
            h("p", {}, a.message),
            a.image_url ? h("img", { class: "thumb", src: a.image_url, alt: "", loading: "lazy", referrerpolicy: "no-referrer" }) : null,
            h("small", { class: "muted" }, `Publish: ${fmtDate(a.publish_at)}`),
            h("div", { class: "row wrap" },
              h("button", { class: "btn ghost sm", onclick: () => { editing = a; renderForm(); window.scrollTo({ top: 0, behavior: "smooth" }); } }, "Edit"),
              a.active
                ? h("button", { class: "btn ghost sm", onclick: (ev: Event) => busy(ev.currentTarget as HTMLButtonElement, async () => { if (await save({ id: a.id, unpublish: true })) await load(); }) }, "Unpublish")
                : h("button", { class: "btn good sm", onclick: (ev: Event) => busy(ev.currentTarget as HTMLButtonElement, async () => { if (await save({ id: a.id, publish: true })) await load(); }) }, "Publish now"),
              h("button", { class: "btn danger sm", onclick: (ev: Event) => {
                if (!confirmDialog("Delete this announcement permanently?")) return;
                busy(ev.currentTarget as HTMLButtonElement, async () => { if (await save({ id: a.id, delete: true })) await load(); });
              } }, "Delete")));
        }))));
    } catch (e) { mount(list, notice("error", errorMessage(e))); }
  };

  mount(root, h("div", { class: "page-head" }, h("h1", {}, "Announcements")), msg, formHost, list);
  renderForm();
  await load();
}
