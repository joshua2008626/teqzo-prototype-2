// The "AI Help" brain. It is NOT a language model: it reads a request with fixed rules, then answers from the
// TEQZO data. Pure functions (no DOM) so they can be tested with Node.
import { fullName, priceText, fmtNum, matchesQuery, sortDevices, filterDevices, parseSmartQuery, similarTo, compareTable, verdict } from "./logic.js";

export const SUGGESTIONS = [
  "Best battery phone under 20k",
  "Gaming phone with 8gb ram under 30k",
  "Compare iPhone 16 and Galaxy S24",
  "Phones similar to iPhone 15",
  "Tell me about Pixel 9",
];

export function findDevice(devices, text) {
  const q = String(text || "").trim().replace(/^the\s+/i, "");
  if (!q) return null;
  const hits = devices.filter((d) => matchesQuery(d, q));
  return hits.length ? sortDevices(hits, "relevance", q)[0] : null;
}

export function describe(d) {
  const bits = [];
  const price = priceText(d);
  if (price) bits.push(price);
  if (d.ram != null) bits.push(`${fmtNum(d.ram)} GB RAM`);
  if (d.storage != null) bits.push(`${fmtNum(d.storage)} GB storage`);
  if (d.battery != null) bits.push(`${fmtNum(d.battery)} mAh battery`);
  if (d.display != null) bits.push(`${fmtNum(d.display)} in screen`);
  if (d.mainCam != null) bits.push(`${fmtNum(d.mainCam)} MP main camera`);
  if (d.chipset) bits.push(d.chipset);
  return bits.length ? bits.join(", ") + "." : "The data lists very little about this phone.";
}

const reply = (text, devices = [], extra = {}) => ({ text, devices, ...extra });

/** text -> { text, devices[], action?: {type:"compare"|"browse", ...}, chips? } */
export function answer(input, devices, brands = []) {
  const t = String(input || "").trim();
  if (!t) return reply("Ask me about phones. For example: “best battery phone under 20k”.");
  if (/^(help|hi|hello|hey|what can you do\??|how does this work\??)$/i.test(t)) {
    return reply("I can search phones from a plain-English request, compare two phones, find similar phones and summarise one phone. I answer from the TEQZO data using fixed rules, not a language model, so I only understand the patterns in the suggestions below.");
  }

  const cmp = /^compare\s+(.+?)\s+(?:vs\.?|versus|and|with|to|or)\s+(.+)$/i.exec(t) || /^(.+?)\s+(?:vs\.?|versus)\s+(.+)$/i.exec(t);
  if (cmp) {
    const a = findDevice(devices, cmp[1]);
    const b = findDevice(devices, cmp[2]);
    if (!a || !b) return reply(`I could not find ${!a ? `“${cmp[1]}”` : `“${cmp[2]}”`} in the data. Try the model name as it appears in Phones.`);
    if (a.id === b.id) return reply("Those two names match the same phone. Try two different models.");
    const list = [a, b];
    const v = verdict(list, compareTable(list));
    return reply(v.lines.join(" "), list, { action: { type: "compare", ids: list.map((d) => d.id) } });
  }

  const sim = /(?:similar to|phones like|something like|alternatives? to|instead of)\s+(.+)$/i.exec(t);
  if (sim) {
    const base = findDevice(devices, sim[1]);
    if (!base) return reply(`I could not find “${sim[1]}” in the data.`);
    const out = similarTo(devices, base.id, 5);
    if (!out.length) return reply(`The data lists too little about ${fullName(base)} to find similar phones.`);
    return reply(`Closest to ${fullName(base)} on price, memory, battery, screen, camera and year:`, out.map((x) => x.device));
  }

  const about = /^(?:tell me about|about|what is|what's|specs? of|details of|show me)\s+(.+)$/i.exec(t);
  if (about) {
    const d = findDevice(devices, about[1]);
    if (!d) return reply(`I could not find “${about[1]}” in the data.`);
    return reply(`${fullName(d)}: ${describe(d)}`, [d]);
  }

  const smart = parseSmartQuery(t.replace(/\b(?:best|good|top|phones?|mobiles?|with|a|an|the|for|me|show|find)\b/gi, " "), brands);
  const parsed = smart.structured ? smart : parseSmartQuery(t, brands);
  const f = parsed.filters;
  const hits = sortDevices(filterDevices(devices, f), parsed.sort || "relevance", f.q || "")
    .filter((d) => d.display == null || (d.display >= 4 && d.display <= 8.5));
  if (!hits.length) return reply("No phones match that. Phones that do not list a value never match a filter on it, so try a looser request.");
  const top = hits.slice(0, 5);
  const what = parsed.structured ? `I understood: ${parsed.chips.join(", ")}.` : `Phones matching “${t}”.`;
  return reply(`${what} ${hits.length.toLocaleString("en-US")} match; here are the first ${top.length}.`, top, { chips: parsed.chips, action: { type: "browse", text: t } });
}
