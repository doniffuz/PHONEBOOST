// tsc natijasi (dist/js) + statik fayllar -> dist/. Bundler/framework kerak emas.
import { execFileSync } from "node:child_process";
import { cpSync, copyFileSync, mkdirSync, rmSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dist = path.join(root, "dist");
rmSync(dist, { recursive: true, force: true });
mkdirSync(dist, { recursive: true });

const local = path.join(root, "node_modules/.bin/tsc");
const tsc = existsSync(local) ? local : "tsc";
execFileSync(tsc, ["-p", path.join(root, "tsconfig.json")], { stdio: "inherit", cwd: root });

copyFileSync(path.join(root, "index.html"), path.join(dist, "index.html"));
copyFileSync(path.join(root, "index.html"), path.join(dist, "404.html")); // GitHub Pages SPA fallback
copyFileSync(path.join(root, "src/styles.css"), path.join(dist, "styles.css"));
cpSync(path.join(root, "public"), dist, { recursive: true });
console.log("BUILD OK ->", dist);
