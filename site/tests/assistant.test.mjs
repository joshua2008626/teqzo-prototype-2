import test from "node:test";
import assert from "node:assert/strict";
import { answer, findDevice } from "../js/assistant.js";

const mk = (id, brand, name, o = {}) => ({ id, brand, name, year: 2024, source: 0, priceInr: null, priceUsd: null, priceEur: null, ram: null, storage: null, battery: null, wired: null, wireless: null, display: 6.5, refresh: null, os: null, chipset: null, mainCam: null, selfieCam: null, weight: null, has5g: null, nfc: null, ...o });
const D = [
  mk("a", "Apple", "iPhone 16", { priceInr: 79900, ram: 8, battery: 3561, mainCam: 48 }),
  mk("b", "Samsung", "Galaxy S24", { priceInr: 74999, ram: 8, battery: 4000, mainCam: 50 }),
  mk("c", "Redmi", "Note 14", { priceInr: 17999, ram: 8, battery: 5500, mainCam: 50, has5g: 1 }),
  mk("d", "Redmi", "Note 13", { priceInr: 15999, ram: 6, battery: 5000, mainCam: 108 }),
];
const brands = ["Apple", "Samsung", "Redmi"];

test("findDevice picks the exact name first", () => assert.equal(findDevice(D, "iphone 16").id, "a"));
test("compare two phones", () => {
  const r = answer("Compare iPhone 16 and Galaxy S24", D, brands);
  assert.equal(r.action.type, "compare");
  assert.deepEqual(r.devices.map((d) => d.id), ["a", "b"]);
});
test("vs without the word compare", () => assert.equal(answer("iphone 16 vs galaxy s24", D, brands).action.type, "compare"));
test("unknown phone is reported, not guessed", () => assert.match(answer("compare zzz and iphone 16", D, brands).text, /could not find/));
test("budget plus battery intent", () => {
  const r = answer("best battery phone under 20k", D, brands);
  assert.equal(r.devices[0].id, "c");
  assert.ok(r.devices.every((d) => d.priceInr <= 20000));
});
test("about a phone", () => assert.match(answer("tell me about iphone 16", D, brands).text, /8 GB RAM/));
test("similar to", () => assert.ok(answer("phones similar to note 14", D, brands).devices.length >= 1));
test("empty and help", () => { assert.ok(answer("", D).text); assert.match(answer("help", D).text, /not a language model/); });
test("no match gives a clear message", () => assert.match(answer("samsung under 5k", D, brands).text, /No phones match/));
