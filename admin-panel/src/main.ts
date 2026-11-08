import { logout, setLogoutHandler } from "./api.js";
import { h, mount } from "./dom.js";
import { renderAnnouncements } from "./pages/announcements.js";
import { renderAppUpdate } from "./pages/appUpdate.js";
import { renderAudit } from "./pages/audit.js";
import { renderDashboard } from "./pages/dashboard.js";
import { renderDonationSettings } from "./pages/donationSettings.js";
import { renderDonations } from "./pages/donations.js";
import { renderDonors } from "./pages/donors.js";
import { renderGeneral } from "./pages/general.js";
import { renderLogin } from "./pages/login.js";
import { renderUsers } from "./pages/users.js";
import { clearSession, loadSession } from "./session.js";

interface Route { path: string; label: string; icon: string; render: (root: HTMLElement) => Promise<void> }
const ROUTES: Route[] = [
  { path: "/admin", label: "Dashboard", icon: "◈", render: renderDashboard },
  { path: "/admin/users", label: "Users", icon: "◉", render: renderUsers },
  { path: "/admin/donations", label: "Donations", icon: "♥", render: renderDonations },
  { path: "/admin/top-donors", label: "Top 10 Donors", icon: "★", render: renderDonors },
  { path: "/admin/announcements", label: "Announcements", icon: "✦", render: renderAnnouncements },
  { path: "/admin/app-update", label: "App Update", icon: "⇪", render: renderAppUpdate },
  { path: "/admin/donation-settings", label: "Donation Settings", icon: "⚙", render: renderDonationSettings },
  { path: "/admin/settings", label: "General Settings", icon: "☰", render: renderGeneral },
  { path: "/admin/audit-logs", label: "Audit Logs", icon: "⌘", render: renderAudit },
];

const app = document.getElementById("app") as HTMLElement;
const normalize = (p: string): string => p.replace(/\/+$/, "") || "/";

export function navigate(path: string, replace = false): void {
  if (normalize(location.pathname) !== normalize(path)) history[replace ? "replaceState" : "pushState"]({}, "", path);
  void route();
}

setLogoutHandler(() => { clearSession(); navigate("/admin/login", true); });

async function route(): Promise<void> {
  const path = normalize(location.pathname);
  const session = loadSession();
  if (path === "/admin/login") {
    if (session) { navigate("/admin", true); return; }
    renderLogin(app, () => navigate("/admin", true));
    return;
  }
  if (!session) { navigate("/admin/login", true); return; }
  const current = ROUTES.find((r) => r.path === path);
  if (!current) { if (path === "/") navigate("/admin", true); else renderShell(null); return; }
  renderShell(current);
}

function renderShell(current: Route | null): void {
  const content = h("div", { class: "content", id: "main", tabindex: "-1" });
  const nav = h("nav", { class: "nav", "aria-label": "Admin sections" }, ROUTES.map((r) =>
    h("a", { href: r.path, class: `nav-item${current?.path === r.path ? " active" : ""}`, "aria-current": current?.path === r.path ? "page" : null, onclick: (e: Event) => { e.preventDefault(); document.body.classList.remove("drawer-open"); navigate(r.path); } },
      h("span", { class: "nav-icon", "aria-hidden": "true" }, r.icon), r.label)));
  const signOut = h("button", { class: "btn ghost block", onclick: () => { void logout().then(() => navigate("/admin/login", true)); } }, "Sign out");
  const sidebar = h("aside", { class: "sidebar glass" },
    h("div", { class: "brand" }, h("span", { class: "logo" }, "⚡"), h("span", {}, "PHONEBOOST ", h("b", {}, "ADMIN"))),
    nav, h("div", { class: "side-foot" }, h("small", { class: "muted" }, loadSession()?.email ?? ""), signOut));
  const topbar = h("header", { class: "topbar glass" },
    h("button", { class: "btn ghost icon", "aria-label": "Menu", onclick: () => document.body.classList.toggle("drawer-open") }, "☰"),
    h("div", { class: "brand" }, h("span", { class: "logo" }, "⚡"), h("b", {}, current?.label ?? "Admin")));
  mount(app, h("div", { class: "shell" }, topbar, sidebar, h("div", { class: "scrim", onclick: () => document.body.classList.remove("drawer-open") }), h("main", { class: "main" }, content)));
  if (!current) { mount(content, h("h1", {}, "Page not found"), h("p", { class: "muted" }, "Choose a section from the menu.")); return; }
  document.title = `${current.label} · PHONEBOOST Admin`;
  void current.render(content);
}

// Faolsizlik: 30 daqiqadan keyin tab sessiyasi tugaydi.
let idleTimer: number | undefined;
const IDLE_MS = 30 * 60 * 1000;
function resetIdle(): void {
  window.clearTimeout(idleTimer);
  idleTimer = window.setTimeout(() => { if (loadSession()) { void logout().then(() => navigate("/admin/login", true)); } }, IDLE_MS);
}
["click", "keydown", "pointerdown", "visibilitychange"].forEach((ev) => window.addEventListener(ev, resetIdle, { passive: true }));
resetIdle();

window.addEventListener("popstate", () => void route());
void route();
