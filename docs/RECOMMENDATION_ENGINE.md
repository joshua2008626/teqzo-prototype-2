# Recommendation engine and AI safety

```
User text
  → requirement parser (LLM or built-in rules)          produces structured requirements only
  → merge with earlier requirements (conversation)      scalars replace, lists add, "forget X" clears
  → validation in Java                                ranges, enums, brand names checked against the data
  → database filtering (hard requirements)              SQL; unknown values never qualify
  → in-memory re-check of every hard requirement        second, independent check; failures are dropped
  → deterministic scoring and ranking                   fixed formula, below
  → reply text                                          template, or LLM wording checked by a grounding guard
```

The language model never receives device data to choose from and never decides eligibility or rank.

## Hard requirements vs. preferences

* **Hard** (must match, else the device is not returned): RAM, storage, battery, wired charging, wireless/reverse
  charging, 5G, NFC, IP rating, refresh rate, display size/type, main camera MP, weight, OS, brand, budget, release year.
  A device whose value is not listed does **not** match.
* **Soft** (only change the order): `priorities` / `use_case` and `ignored`.
* Implicit: cancelled devices and entries without specifications or OS are excluded; phone-sized display unless a size
  is given or a tablet is requested.

## When nothing matches

Requirements are dropped one at a time, least essential first, until at least one device qualifies:
budget-with-unknown-price → NFC → water resistance → wireless charging → reverse charging → display type → refresh rate
→ camera MP → storage → wired charging → 5G → release year → weight → display size → RAM → battery.
Brand, excluded brands, OS and the budget amount are never dropped. Every dropped requirement is listed in
`relaxed` and stated in the reply ("No device matches everything you asked for. Dropped the minimum storage ...");
each returned device's `checks` show which requirements it does not meet.

## Score (0–100)

`score = 100 × Σ weight(d) × subscore(d)` over eight dimensions. A sub-score is 0–1 from the **listed** values;
a dimension with no listed value scores nothing and is shown as "not listed".

| Dimension | Sub-score (cap = value earning full marks) |
|-----------|---------------------------------------------|
| Performance | RAM ÷ 12 GB (40%), CPU clock ÷ 3.2 GHz (40%), core count ÷ 8 (20%). *A proxy, not a benchmark.* |
| Memory & storage | RAM ÷ 12 GB (50%), storage ÷ 512 GB (50%) |
| Battery | (mAh − 2000) ÷ (6000 − 2000) |
| Charging | wired ÷ 100 W (60%), wireless ÷ 30 W (25%; 0.4 credit if supported but wattage not listed), reverse charging (15%) |
| Camera | main MP ÷ 108 (70%), selfie MP ÷ 32 (30%). *Megapixels only.* |
| Display | size (4→7 in) 20%, refresh (60→144 Hz) 30%, OLED 1.0 / LCD-IPS 0.5 / other 0.3 25%, resolution ÷ 1440×3200 25% |
| Connectivity | 5G 35%, NFC 20%, Wi-Fi generation 20%, Bluetooth version 15%, USB-C 10% |
| Recency | linear over the 8 years up to the newest year in the dataset |

Within a dimension, sub-parts that are not listed are left out and the rest renormalised.

Weights (percent) per use case, order Performance / Memory / Battery / Charging / Camera / Display / Connectivity / Recency:

| Use case | Weights |
|----------|---------|
| balanced (default) | 20 / 10 / 15 / 10 / 15 / 15 / 10 / 5 |
| gaming | 33 / 12 / 10 / 5 / 2 / 25 / 3 / 10 |
| camera | 10 / 5 / 10 / 3 / 47 / 10 / 5 / 10 |
| battery | 5 / 3 / 47 / 25 / 3 / 4 / 3 / 10 |
| display | 10 / 3 / 8 / 2 / 7 / 55 / 5 / 10 |
| performance | 45 / 18 / 7 / 3 / 3 / 8 / 6 / 10 |

Several priorities: their profiles are averaged and blended 75/25 with *balanced*. Ignored dimensions get weight 0 and
the rest is renormalised. Ties are broken by newer year, then name. Every response contains the weights and each
device's per-dimension points, the listed values behind them, requirement checks and caveats ("Why this device?").

## Budget handling

Only prices the dataset lists are used. By default that means INR prices (≈360 devices). If `PRICE_EUR_TO_INR` is set,
EUR prices are converted at that fixed rate and always labelled approximate. Devices without a usable price are excluded
by a budget; if that leaves nothing, they are included and flagged "price not listed". The reply says that only devices
with a listed price were checked.

## Preventing hallucination

1. The model's only structured job is extracting requirements; its JSON is validated (unknown keys ignored, ranges
   and enums enforced, brand names checked against the database).
2. Candidates come only from SQL; the list shown to the user is built from database rows.
3. All numbers in "Why this device?" are rendered from database values by deterministic code.
4. An optional model-written summary is accepted only if every number in it also appears in the facts it was given
   (small counts excepted), has no links and is short. Otherwise the template summary is used.
5. Any provider failure falls back to the rule-based parser and template text, with a notice.

Rule-based parser coverage: RAM / storage (`8GB RAM`, `256gb storage`, `1TB`), battery (`5000mAh`), charging (`65W`,
`fast charging` = 30 W), refresh rate (`120Hz`, `high refresh rate` = 90 Hz), camera MP, display size, 5G, NFC,
wireless/reverse charging, water resistance, display type, OS, brands (include / exclude), budgets (`under 30000`,
`30k budget`, `₹25,000`, `1.5 lakh`), "latest", tablets, and use cases (gaming, camera, battery, display, performance),
including negations ("don't care about the camera").
