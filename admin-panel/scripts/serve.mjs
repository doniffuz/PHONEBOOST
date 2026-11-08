// Lokal dev server (SPA fallback bilan). `npm run build && npm run dev`
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const dist = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../dist");
const types = { ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml" };
createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", "http://localhost");
  let file = path.join(dist, path.normalize(url.pathname).replace(/^(\.\.[/\\])+/, ""));
  if (!file.startsWith(dist)) { res.writeHead(403).end(); return; }
  let body;
  try { body = await readFile(file); } catch { file = path.join(dist, "index.html"); body = await readFile(file); }
  res.writeHead(200, { "content-type": types[path.extname(file)] ?? "application/octet-stream", "cache-control": "no-store" });
  res.end(body);
}).listen(5173, () => console.log("http://localhost:5173/admin/login"));
