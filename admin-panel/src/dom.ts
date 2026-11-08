// Xavfsiz DOM yordamchisi: matn HECH QACHON HTML sifatida talqin qilinmaydi (innerHTML ishlatilmaydi).
export type Child = Node | string | number | null | undefined | false | Child[];
type Props = Record<string, unknown>;

export function h<K extends keyof HTMLElementTagNameMap>(tag: K, props: Props = {}, ...children: Child[]): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (v === undefined || v === null || v === false) continue;
    if (k === "class") el.className = String(v);
    else if (k.startsWith("on") && typeof v === "function") el.addEventListener(k.slice(2).toLowerCase(), v as EventListener);
    else if (k === "value" || k === "checked" || k === "disabled" || k === "selected") (el as unknown as Record<string, unknown>)[k] = v;
    else if (k === "style" && typeof v === "object") Object.assign(el.style, v as Record<string, string>); // CSSOM: CSP style-src 'self' bilan mos
    else if (k === "dataset" && typeof v === "object") Object.assign(el.dataset, v as Record<string, string>);
    else el.setAttribute(k, v === true ? "" : String(v));
  }
  append(el, children);
  return el;
}

export function append(parent: Node, children: Child[]): void {
  for (const c of children) {
    if (c === null || c === undefined || c === false) continue;
    if (Array.isArray(c)) append(parent, c);
    else if (c instanceof Node) parent.appendChild(c);
    else parent.appendChild(document.createTextNode(String(c)));
  }
}

export function clear(el: Element): void {
  while (el.firstChild) el.removeChild(el.firstChild);
}

export function mount(el: Element, ...children: Child[]): void {
  clear(el);
  append(el, children);
}
