import os
import runpy
import sqlite3
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
DB_DIR = os.path.join(HERE, "..")
sys.path.insert(0, DB_DIR)
sys.path.insert(0, HERE)
import import_flipkart as imp  # noqa: E402
import mysql_shim  # noqa: E402

SAMPLE = os.path.join(HERE, "fixtures", "flipkart_sample.csv")


def make_gsmarena_db():
    """A tiny stand-in for the original three tables."""
    c = sqlite3.connect(":memory:")
    c.executescript("""
        CREATE TABLE brand (id TEXT PRIMARY KEY, name TEXT, url TEXT, num_devices INTEGER);
        CREATE TABLE device (id TEXT PRIMARY KEY, brand_id TEXT, name TEXT, url TEXT, thumbnail TEXT, summary TEXT);
        CREATE TABLE device_specification (device_id TEXT, spec_id INTEGER PRIMARY KEY, spec_category TEXT,
                                           specification TEXT, spec_value TEXT);
        INSERT INTO brand VALUES ('samsung-phones-9.php','Samsung','u',2), ('apple-phones-48.php','Apple','u',1),
                                 ('xiaomi-phones-80.php','Xiaomi','u',1), ('motorola-phones-4.php','Motorola','u',0);
        INSERT INTO device VALUES ('samsung_galaxy_a17-1','samsung-phones-9.php','Galaxy A17','u','t','s'),
                                  ('samsung_galaxy_s22-2','samsung-phones-9.php','Galaxy S22 5G','u','t','s'),
                                  ('apple_iphone_14-3','apple-phones-48.php','iPhone 14','u','t','s'),
                                  ('xiaomi_redmi_12-4','xiaomi-phones-80.php','Redmi 12','u','t','s');
        INSERT INTO device_specification VALUES
            ('apple_iphone_14-3',1,'Launch','Announced','2022, September 07'),
            ('apple_iphone_14-3',2,'Launch','Status','Available. Released 2022, September 16'),
            ('apple_iphone_14-3',3,'Memory','Internal','128GB 6GB RAM, 256GB 6GB RAM'),
            ('apple_iphone_14-3',4,'Misc','Price','$ 799.00 / € 949.00'),
            ('apple_iphone_14-3',5,'Platform','OS','iOS 16'),
            ('apple_iphone_14-3',6,'Network','5G bands','1, 2, 3'),
            ('samsung_galaxy_a17-1',7,'Launch','Announced','2022');
    """)
    return c


class CleaningTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.devices, cls.report = imp.clean(imp.read_rows(SAMPLE))
        cls.by_base = {d["base"]: d for d in cls.devices}

    def test_duplicates_and_non_phones_are_removed(self):
        self.assertEqual(self.report["csv rows"], 31)
        self.assertEqual(self.report["after removing exact duplicate rows"], 30)
        names = set(self.by_base)
        self.assertFalse(any("Kechaoda" in n or "Nokia 105" in n for n in names))
        self.assertFalse(any(n.startswith("Weird Phone") for n in names), "price 0 must be dropped")
        self.assertFalse(any(n.startswith("Tab Pro") for n in names), "11 inch tablet must be dropped")

    def test_variants_merge_into_one_device_with_lowest_price(self):
        s26 = self.by_base["Samsung Galaxy S26 Ultra 5G"]
        self.assertEqual(sorted(s26["variants"]), [(12.0, 256.0), (12.0, 512.0)])
        self.assertEqual(s26["variants"][(12.0, 256.0)], 119999.0)  # lowest of 119999 / 124999
        self.assertEqual(s26["price_min"], 119999.0)
        self.assertEqual(s26["colours"], ["Cobalt Violet", "Black"])
        self.assertTrue(s26["five_g"])

    def test_zero_values_become_missing_not_zero(self):
        z = self.by_base["IQOO Z10 5G"]
        self.assertIsNone(z["battery_mah"])
        self.assertIsNone(z["display_inch"])
        self.assertIsNone(z["processor"])
        self.assertEqual(z["rear"], ())
        self.assertIsNone(z["front_raw"])
        self.assertIsNone(z["rating"])
        self.assertEqual(list(z["variants"]), [(None, 128.0)])  # RAM 0 is not stated

    def test_camera_and_processor_cleanup(self):
        self.assertEqual(self.by_base["Infinix Note 50s 5G+"]["rear"], ("64 MP",))  # 0MP lens dropped
        self.assertEqual(self.by_base["vivo V60e"]["front_raw"], "50 MP; f/2.2; FOV 77°; 3P lens")
        self.assertEqual(self.by_base["Apple iPhone 17 Pro"]["processor"], "A19 Pro")

    def test_marketing_text_and_spelling_variants_merge_into_one_phone(self):
        names = set(self.by_base)
        self.assertIn("OPPO K13x 5G", names)
        self.assertEqual(sorted(self.by_base["OPPO K13x 5G"]["variants"].items()), [((6.0, 128.0), 13999.0)])
        self.assertIn("POCO C61", names)
        self.assertEqual(len(self.by_base["POCO C61"]["variants"]), 2)  # "- Locked with Airtel Prepaid" merged
        self.assertIn("Infinix GT 30 5G+", names)  # colour + storage glued to the title removed
        self.assertEqual(len([n for n in names if "M36" in n]), 1)  # "Samsung M36" == "Samsung Galaxy M36"
        self.assertTrue(any("Note 14 Pro 5G" in n for n in names))
        self.assertEqual(len([n for n in names if "Note" in n and "14 Pro" in n]), 1)
        self.assertFalse(any("Charger" in n or "Locked" in n or "mAh" in n for n in names))

    def test_plus_models_are_not_merged_with_the_base_model(self):
        self.assertIn("Samsung Galaxy S26+ 5G", self.by_base)
        self.assertIn("Samsung Galaxy S26 5G", self.by_base)

    def test_rating_is_weighted_by_rating_count(self):
        redmi = self.by_base["REDMI 13 5G"]
        self.assertEqual(redmi["rating"], 4.3)
        self.assertEqual(redmi["rating_count"], 1200)


class NamingTests(unittest.TestCase):
    def test_brand_resolution(self):
        existing = {"samsung": ("samsung-phones-9.php", "Samsung"), "xiaomi": ("x", "Xiaomi"),
                    "motorola": ("m", "Motorola")}
        self.assertEqual(imp.resolve_brand("MOTOROLA", existing), ("m", "Motorola", False))
        self.assertEqual(imp.resolve_brand("REDMI", existing), ("x", "Xiaomi", True))
        self.assertEqual(imp.resolve_brand("IQOO", existing), (None, "iQOO", False))

    def test_device_name_drops_brand_prefix_except_for_aliases(self):
        self.assertEqual(imp.device_name("Samsung Galaxy S26 5G", "Samsung", "Samsung", False), "Galaxy S26 5G")
        self.assertEqual(imp.device_name("MOTOROLA g67 power 5G", "MOTOROLA", "Motorola", False), "g67 power 5G")
        self.assertEqual(imp.device_name("REDMI 13 5G", "REDMI", "Xiaomi", True), "REDMI 13 5G")

    def test_os_is_inferred_only_for_known_brands(self):
        self.assertEqual(imp.infer_os("Apple"), "iOS")
        self.assertEqual(imp.infer_os("Samsung"), "Android")
        self.assertIsNone(imp.infer_os("Jmax"))
        self.assertIsNone(imp.infer_os("Nokia"))


class EndToEndTests(unittest.TestCase):
    """Import into SQLite, then run the project's own two build scripts over the result."""

    @classmethod
    def setUpClass(cls):
        cls.db = make_gsmarena_db()
        devices, _ = imp.clean(imp.read_rows(SAMPLE))
        cls.result = imp.write_all(cls.db, "?", devices)
        # extras builder (its own SQLite mode)
        extras = runpy.run_path(os.path.join(DB_DIR, "build_device_extras.py"), run_name="not_main")
        extras["run"](cls.db, "?", extras["DDL_SQLITE"])
        # core builder: the real MySQL script, through the shim
        mysql_shim.install(cls.db)
        os.environ.update(DB_HOST="x", DB_PORT="1", DB_USERNAME="x", DB_PASSWORD="x")
        runpy.run_path(os.path.join(DB_DIR, "pipeline", "build_device_core_v4.py"), run_name="not_main")

    def one(self, sql, *params):
        return self.db.execute(sql, params).fetchone()

    def test_existing_models_are_skipped_and_originals_untouched(self):
        self.assertIn("Samsung Galaxy A17 5G", self.result["skipped"])
        self.assertEqual(self.one("SELECT COUNT(*) FROM device WHERE id='samsung_galaxy_a17-1'")[0], 1)
        self.assertEqual(self.one("SELECT COUNT(*) FROM device_specification WHERE device_id LIKE 'samsung_%'")[0], 1)
        self.assertEqual(self.one("SELECT name FROM brand WHERE id='samsung-phones-9.php'")[0], "Samsung")

    def test_brands(self):
        names = {r[0] for r in self.db.execute("SELECT name FROM brand WHERE id LIKE 'flipkart-brand-%'")}
        self.assertEqual(names, {"Google", "Infinix", "vivo", "iQOO", "OPPO", "Nothing"})
        brand = self.one("SELECT brand_id FROM device WHERE id LIKE 'flipkart-%redmi%'")[0]
        self.assertEqual(brand, "xiaomi-phones-80.php")

    def test_core_row_values(self):
        row = self.db.execute("SELECT * FROM device_core_v4 WHERE id = 'flipkart-samsung-galaxy-s26-ultra-5g'")
        cols = [d[0] for d in row.description]
        r = dict(zip(cols, row.fetchone()))
        self.assertEqual((r["ram_gb"], r["storage_gb"]), (12.0, 512.0))  # best configuration, as for GSMArena
        self.assertEqual(r["battery_mah"], 5000.0)
        self.assertEqual(r["display_size_inches"], 6.9)
        self.assertEqual(r["main_camera_mp"], 200.0)
        self.assertEqual(r["selfie_camera_mp"], 12.0)
        self.assertEqual(r["chipset"], "Snapdragon 8 Elite Gen 5")
        self.assertEqual(r["os"].split(" (")[0], "Android")
        self.assertEqual(r["has_5g"], 1)
        for missing in ("wired_charging_w", "wireless_charging_w", "weight_g", "refresh_rate_hz", "nfc", "ip_rating"):
            self.assertIsNone(r[missing], missing)

    def test_extras_row_values(self):
        cols = ["price_inr", "price_eur", "price_usd", "announced_year", "listed_year", "data_source", "status_code",
                "has_spec_data"]
        r = dict(zip(cols, self.one(f"SELECT {', '.join(cols)} FROM device_extras_v1 "
                                    "WHERE device_id = 'flipkart-samsung-galaxy-s26-ultra-5g'")))
        self.assertEqual(r, {"price_inr": 119999.0, "price_eur": None, "price_usd": None, "announced_year": None,
                             "listed_year": 2026, "data_source": "FLIPKART_2026", "status_code": "AVAILABLE",
                             "has_spec_data": 1})
        old = self.one("SELECT data_source, listed_year, announced_year FROM device_extras_v1 "
                       "WHERE device_id = 'apple_iphone_14-3'")
        self.assertEqual(old, (None, None, 2022))

    def test_phones_without_5g_in_the_name_have_no_5g_flag_and_are_unknown(self):
        self.assertEqual(self.one("SELECT has_5g FROM device_core_v4 WHERE id = 'flipkart-apple-iphone-17-pro'")[0], 0)
        self.assertEqual(self.one("SELECT os FROM device_core_v4 WHERE id = 'flipkart-apple-iphone-17-pro'")[0].split(" (")[0], "iOS")

    def test_storage_is_kept_when_ram_is_not_listed(self):
        r = self.one("SELECT ram_gb, storage_gb FROM device_core_v4 WHERE id = 'flipkart-apple-iphone-17'")
        self.assertEqual(r, (None, 256.0))

    def test_plus_model_has_its_own_id(self):
        ids = {r[0] for r in self.db.execute("SELECT id FROM device WHERE id LIKE 'flipkart-samsung-galaxy-s26%'")}
        self.assertEqual(ids, {"flipkart-samsung-galaxy-s26-5g", "flipkart-samsung-galaxy-s26-plus-5g",
                               "flipkart-samsung-galaxy-s26-ultra-5g"})

    def test_missing_values_stay_null(self):
        r = self.one("SELECT ram_gb, storage_gb, battery_mah, display_size_inches, chipset, main_camera_mp "
                     "FROM device_core_v4 WHERE id = 'flipkart-iqoo-z10-5g'")
        self.assertEqual(r[0], None)  # RAM 0 in the CSV is not a stated value
        self.assertEqual((r[2], r[3], r[4], r[5]), (None, None, None, None))

    def test_detail_page_specs_are_present(self):
        rows = self.db.execute("SELECT spec_category, specification, spec_value FROM device_specification "
                               "WHERE device_id = 'flipkart-xiaomi-redmi-13-5g'").fetchall()
        misc = {s: v for c, s, v in rows if c == "Misc"}
        self.assertEqual(misc["Price"], "₹ 12,499")
        self.assertIn("6 GB RAM / 128 GB: ₹ 12,499", misc["Price by variant"])
        self.assertIn("Flipkart rating", misc)

    def test_importing_twice_gives_the_same_result(self):
        before = self.one("SELECT COUNT(*) FROM device_specification")[0]
        devices, _ = imp.clean(imp.read_rows(SAMPLE))
        imp.write_all(self.db, "?", devices)
        self.assertEqual(self.one("SELECT COUNT(*) FROM device_specification")[0], before)
        self.assertEqual(self.one("SELECT COUNT(*) FROM device WHERE id LIKE 'flipkart-%'")[0], len(self.result["devices"]))
        self.assertEqual(len({r[0] for r in self.db.execute("SELECT spec_id FROM device_specification")}),
                         self.one("SELECT COUNT(*) FROM device_specification")[0])


if __name__ == "__main__":
    unittest.main()
