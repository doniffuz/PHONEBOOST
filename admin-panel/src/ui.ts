import { h, type Child } from "./dom.js";
import { statusLabel, statusTone } from "./format.js";

export const badge = (status: string, label?: string): HTMLElement => h("span", { class: `badge ${statusTone(status)}` }, label ?? statusLabel(status));

export function card(title: string | null, ...children: Child[]): HTMLElement {
  return h("section", { class: "card" }, title ? h("h2", {}, title) : null, ...children);
}

export function stat(label: string, value: string | number, hint?: string, tone = ""): HTMLElement {
  return h("div", { class: `stat ${tone}` }, h("div", { class: "stat-label" }, label), h("div", { class: "stat-value" }, String(value)), hint ? h("div", { class: "stat-hint" }, hint) : null);
}

export function notice(kind: "error" | "ok" | "info", text: string): HTMLElement {
  return h("div", { class: `notice ${kind}`, role: kind === "error" ? "alert" : "status" }, text);
}

export function field(label: string, input: HTMLElement, hint?: string): HTMLElement {
  return h("label", { class: "field" }, h("span", { class: "field-label" }, label), input, hint ? h("span", { class: "field-hint" }, hint) : null);
}

export function toggle(label: string, checked: boolean, onChange?: (v: boolean) => void): { el: HTMLElement; input: HTMLInputElement } {
  const input = h("input", { type: "checkbox", checked, onchange: () => onChange?.(input.checked) });
  const el = h("label", { class: "switch" }, input, h("span", { class: "slider" }), h("span", { class: "switch-label" }, label));
  return { el, input };
}

/** Jadval: telefonda qatorlar kartaga aylanadi (data-label). */
export function table(headers: string[], rows: Child[][], empty = "No data yet."): HTMLElement {
  if (rows.length === 0) return h("p", { class: "empty" }, empty);
  return h("div", { class: "table-wrap" }, h("table", {},
    h("thead", {}, h("tr", {}, headers.map((x) => h("th", {}, x)))),
    h("tbody", {}, rows.map((r) => h("tr", {}, r.map((c, i) => h("td", { "data-label": headers[i] ?? "" }, c)))))));
}

export function confirmDialog(message: string): boolean {
  return window.confirm(message);
}

export function busy(btn: HTMLButtonElement, fn: () => Promise<void>): void {
  btn.disabled = true;
  fn().finally(() => { btn.disabled = false; });
}

export const loading = (): HTMLElement => h("div", { class: "loading", "aria-busy": "true" }, h("span", { class: "spinner" }), "Loading…");
