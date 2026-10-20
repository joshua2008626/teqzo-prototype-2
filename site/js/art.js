// Generated phone illustrations. The datasets contain no photos (and GSMArena/Flipkart pictures are copyrighted),
// so every phone gets a drawn illustration: colour from the brand, camera layout from the camera resolution,
// screen proportions from the screen size. It is an illustration, not a photo of the real device.
const NS = "http://www.w3.org/2000/svg";
let uid = 0;

function hash(text) {
  let h = 2166136261;
  for (const ch of String(text)) { h ^= ch.codePointAt(0); h = Math.imul(h, 16777619); }
  return h >>> 0;
}

const BRAND_HUE = { apple: [215, 8, 62], google: [200, 55, 70], nothing: [0, 0, 88], samsung: [232, 40, 34], xiaomi: [24, 85, 55], redmi: [8, 75, 52],
  poco: [48, 90, 52], oneplus: [350, 70, 42], realme: [48, 95, 55], oppo: [150, 55, 42], vivo: [222, 80, 55], motorola: [205, 70, 40],
  honor: [215, 75, 55], huawei: [355, 75, 50], sony: [250, 15, 25], nokia: [195, 60, 38], infinix: [160, 70, 38], tecno: [215, 70, 45],
  iqoo: [20, 90, 52], lava: [10, 70, 48], asus: [0, 0, 18], "ai+": [265, 70, 50] };

function palette(d) {
  const key = (d.brand || "").toLowerCase();
  const [h, s, l] = BRAND_HUE[key] || [hash(key) % 360, 55, 46];
  const shift = (hash(d.name || d.id) % 24) - 12; // models of one brand get slightly different tones
  return { a: `hsl(${(h + shift + 360) % 360} ${s}% ${Math.min(l + 14, 82)}%)`, b: `hsl(${(h + shift + 28 + 360) % 360} ${s}% ${Math.max(l - 10, 14)}%)`, h: (h + shift + 360) % 360 };
}

function el(tag, attrs, parent) {
  const n = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs || {})) n.setAttribute(k, v);
  if (parent) parent.append(n);
  return n;
}

function lensCount(d) {
  if (d.mainCam == null) return 2;
  if (d.mainCam <= 13) return 1;
  if (d.mainCam <= 50) return 2;
  return 3;
}

function back(svg, d, p, x0) {
  const id = `g${++uid}`;
  const defs = el("defs", {}, svg);
  const g = el("linearGradient", { id, x1: "0", y1: "0", x2: "1", y2: "1" }, defs);
  el("stop", { offset: "0", "stop-color": p.a }, g);
  el("stop", { offset: "1", "stop-color": p.b }, g);
  const grp = el("g", { transform: `translate(${x0} 0)` }, svg);
  el("rect", { x: 1, y: 1, width: 98, height: 198, rx: 16, fill: `url(#${id})`, stroke: "rgba(255,255,255,.35)", "stroke-width": 1.2 }, grp);
  el("path", { d: "M1 70 L99 20 L99 60 L1 120 Z", fill: "rgba(255,255,255,.10)" }, grp);
  const style = hash(d.brand + d.name) % 3;
  const n = lensCount(d);
  const lens = (cx, cy, r) => { el("circle", { cx, cy, r: r + 2.4, fill: "rgba(10,12,25,.55)" }, grp); el("circle", { cx, cy, r, fill: "#0b0e1c", stroke: "rgba(255,255,255,.28)", "stroke-width": 1 }, grp); el("circle", { cx: cx - r * 0.3, cy: cy - r * 0.3, r: r * 0.28, fill: "rgba(255,255,255,.45)" }, grp); };
  if (style === 0) { // square island
    el("rect", { x: 10, y: 10, width: 46, height: n === 1 ? 30 : 46, rx: 11, fill: "rgba(255,255,255,.16)", stroke: "rgba(255,255,255,.3)" }, grp);
    lens(24, 24, 7); if (n > 1) lens(24, 44, 7); if (n > 2) lens(43, 34, 7);
  } else if (style === 1) { // vertical pill
    el("rect", { x: 11, y: 11, width: 24, height: 18 + n * 16, rx: 12, fill: "rgba(255,255,255,.16)", stroke: "rgba(255,255,255,.3)" }, grp);
    for (let i = 0; i < n; i++) lens(23, 24 + i * 17, 7);
  } else { // round island
    el("circle", { cx: 50, cy: 34, r: 24, fill: "rgba(255,255,255,.16)", stroke: "rgba(255,255,255,.3)" }, grp);
    if (n === 1) lens(50, 34, 9); else if (n === 2) { lens(41, 34, 7); lens(59, 34, 7); } else { lens(42, 27, 6.5); lens(58, 27, 6.5); lens(50, 42, 6.5); }
  }
  el("circle", { cx: style === 2 ? 82 : 66, cy: 17, r: 3, fill: "rgba(255,255,255,.8)" }, grp);
  const word = el("text", { x: 50, y: 170, "text-anchor": "middle", "font-size": 10, "font-weight": 700, "letter-spacing": 2, fill: "rgba(255,255,255,.55)" }, grp);
  word.textContent = (d.brand || "").slice(0, 12).toUpperCase();
}

function front(svg, d, p, x0) {
  const id = `s${++uid}`;
  const defs = el("defs", {}, svg);
  const g = el("linearGradient", { id, x1: "0", y1: "0", x2: "0.6", y2: "1" }, defs);
  el("stop", { offset: "0", "stop-color": `hsl(${p.h} 80% 62%)` }, g);
  el("stop", { offset: ".55", "stop-color": `hsl(${(p.h + 50) % 360} 75% 45%)` }, g);
  el("stop", { offset: "1", "stop-color": `hsl(${(p.h + 120) % 360} 70% 22%)` }, g);
  const grp = el("g", { transform: `translate(${x0} 0)` }, svg);
  el("rect", { x: 1, y: 1, width: 98, height: 198, rx: 16, fill: "#0a0c18", stroke: "rgba(255,255,255,.3)", "stroke-width": 1.2 }, grp);
  el("rect", { x: 5, y: 5, width: 90, height: 190, rx: 12, fill: `url(#${id})` }, grp);
  el("circle", { cx: 50, cy: 14, r: 3.2, fill: "#0a0c18" }, grp);
  const t = el("text", { x: 50, y: 74, "text-anchor": "middle", "font-size": 22, "font-weight": 300, fill: "rgba(255,255,255,.92)" }, grp);
  t.textContent = "9:41";
  el("rect", { x: 38, y: 188, width: 24, height: 2.4, rx: 1.2, fill: "rgba(255,255,255,.7)" }, grp);
}

/** view: "back" | "front" | "pair". Returns an <svg> that scales with its CSS width. */
export function phoneArt(d, view = "back") {
  const p = palette(d);
  const w = view === "pair" ? 214 : 100;
  const svg = el("svg", { viewBox: `0 0 ${w} 200`, role: "img", "aria-label": `Illustration of ${d.brand} ${d.name} (not a photo)`, class: `art art-${view}` });
  if (view === "pair") { front(svg, d, p, 0); back(svg, d, p, 114); }
  else if (view === "front") front(svg, d, p, 0);
  else back(svg, d, p, 0);
  return svg;
}
