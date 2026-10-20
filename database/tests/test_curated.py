import csv
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import curated  # noqa: E402
import export_site_data as ex  # noqa: E402

HEAD = ["brand", "name", "year", "ram_gb", "storage_gb", "battery_mah", "wired_w", "display_in", "refresh_hz", "os", "chipset",
        "main_mp", "selfie_mp", "weight_g", "has_5g", "nfc", "wireless", "price_inr", "price_usd", "source_url"]


def write_csv(rows):
    f = tempfile.NamedTemporaryFile("w", suffix=".csv", delete=False, newline="", encoding="utf-8")
    w = csv.writer(f)
    w.writerow(HEAD)
    w.writerows(rows)
    f.close()
    return f.name


def row(**kw):
    d = dict.fromkeys(HEAD, "")
    d.update(kw)
    return [d[h] for h in HEAD]


def blank(fields, **kw):
    r = [None] * len(fields)
    for k, v in kw.items():
        r[fields.index(k)] = v
    return r


class CuratedTest(unittest.TestCase):
    def test_fills_only_empty_cells(self):
        path = write_csv([row(brand="Apple", name="iPhone 16", year="2024", ram_gb="8", battery_mah="3561", source_url="https://x.test/a")])
        recs, dropped = curated.load(path)
        os.unlink(path)
        rows = [blank(ex.FIELDS, id="f-1", b="Apple", n="iPhone 16", ram=6, s=1, cu=0)]
        new, info = curated.apply(rows, ex.FIELDS, recs)
        r = dict(zip(ex.FIELDS, rows[0]))
        self.assertEqual(r["ram"], 6)            # existing value is never overwritten
        self.assertEqual(r["bat"], 3561)         # empty cell filled
        self.assertEqual(r["y"], 2024)
        self.assertEqual(r["cu"], 1)
        self.assertEqual(new, [])
        self.assertNotIn("ram", info["f-1"]["filled"])

    def test_adds_missing_phone_and_ignores_5g_in_match(self):
        path = write_csv([row(brand="Samsung", name="Galaxy S25", ram_gb="12", source_url="https://x.test/b"),
                          row(brand="Poco", name="F7", ram_gb="12", source_url="https://x.test/c")])
        recs, _ = curated.load(path)
        os.unlink(path)
        rows = [blank(ex.FIELDS, id="g-1", b="Samsung", n="Galaxy S25 5G", s=0, cu=0)]
        new, info = curated.apply(rows, ex.FIELDS, recs)
        self.assertEqual(dict(zip(ex.FIELDS, rows[0]))["ram"], 12)   # matched the "5G" listing
        self.assertEqual(len(new), 1)
        self.assertEqual(new[0][ex.FIELDS.index("s")], curated.CURATED_SOURCE)
        self.assertTrue(info[new[0][0]]["added"])

    def test_out_of_range_values_are_dropped(self):
        path = write_csv([row(brand="X", name="Y", battery_mah="90", ram_gb="64", weight_g="170", source_url="https://x.test/d")])
        recs, dropped = curated.load(path)
        os.unlink(path)
        self.assertEqual(recs[0]["values"], {"w": 170})
        self.assertEqual({d[2] for d in dropped}, {"battery_mah", "ram_gb"})

    def test_rows_without_a_source_are_skipped(self):
        path = write_csv([row(brand="X", name="Y", ram_gb="8")])
        recs, _ = curated.load(path)
        os.unlink(path)
        self.assertEqual(recs, [])

    def test_shipped_csv_is_valid(self):
        recs, dropped = curated.load()
        self.assertGreater(len(recs), 50)
        self.assertEqual(dropped, [])


if __name__ == "__main__":
    unittest.main()
