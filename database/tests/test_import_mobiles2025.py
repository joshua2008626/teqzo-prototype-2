import os
import runpy
import sqlite3
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
DB_DIR = os.path.join(HERE, "..")
sys.path.insert(0, DB_DIR)
sys.path.insert(0, HERE)
import import_flipkart as fk  # noqa: E402
import import_mobiles2025 as m25  # noqa: E402
import mysql_shim  # noqa: E402
from test_import_flipkart import make_gsmarena_db  # noqa: E402

SAMPLE = os.path.join(HERE, "fixtures", "mobiles2025_sample.csv")
FLIPKART_SAMPLE = os.path.join(HERE, "fixtures", "flipkart_sample.csv")


class ParsingTests(unittest.TestCase):
    def test_money(self):
        self.assertEqual(m25.money("PKR 224,999"), 224999)
        self.assertEqual(m25.money("¥13,999"), 13999)
        self.assertEqual(m25.money("CNY 17,999 "), 17999)
        self.assertIsNone(m25.money("Not available"))
        self.assertIsNone(m25.money(""))

    def test_split_model(self):
        self.assertEqual(m25.split_model("iPhone 16 Plus 128GB"), ("iPhone 16 Plus", 128.0))
        self.assertEqual(m25.split_model("iPad Pro 13-inch 2TB"), ("iPad Pro 13-inch", 2048.0))
        self.assertEqual(m25.split_model("Mate 40E"), ("Mate 40E", None))

    def test_ram_options(self):
        self.assertEqual(m25.ram_options("8GB / 12GB"), [8.0, 12.0])
        self.assertEqual(m25.ram_options("1.5GB"), [1.5])
        self.assertEqual(m25.ram_options("64GB"), [])  # implausible RAM is dropped, not stored

    def test_camera_list(self):
        self.assertEqual(m25.camera_list("50MP (Main) + 12MP (Ultra-wide)"), ["50 MP (Main)", "12 MP (Ultra-wide)"])
        self.assertEqual(m25.camera_list("12MP / 4K"), ["12 MP"])
        self.assertEqual(m25.camera_list("Dual 32MP"), ["32 MP"])
        self.assertEqual(m25.camera_list("0MP"), [])


class CleaningTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.devices, cls.report = m25.clean(m25.read_rows(SAMPLE))
        cls.by_name = {d["base"]: d for d in cls.devices}

    def test_report_counts(self):
        self.assertEqual(self.report["csv rows"], 14)
        self.assertEqual(self.report["after removing exact duplicate rows"], 13)
        self.assertEqual(self.report["dropped: tablets and watches (by name)"], 1)

    def test_storage_variants_merge_into_one_device(self):
        iphone = self.by_name["iPhone 14"]
        self.assertEqual(set(iphone["variants"]), {(6.0, 128.0), (6.0, 256.0)})
        self.assertEqual(m25.lowest(iphone, "India"), ("INR", 69999.0))
        self.assertEqual(m25.lowest(iphone, "USA"), ("USD", 799.0))

    def test_poco_spellings_are_one_device(self):
        poco = [d for d in self.devices if d["base"] == "Poco X6 Pro"]
        self.assertEqual(len(poco), 1)
        self.assertEqual(len(poco[0]["variants"]), 2)

    def test_ram_alternatives_become_variants(self):
        self.assertEqual(set(self.by_name["Reno 12"]["variants"]), {(8.0, 256.0), (12.0, 256.0)})

    def test_missing_price_stays_missing(self):
        self.assertIsNone(m25.lowest(self.by_name["Reno 12"], "Pakistan"))
        self.assertEqual(m25.lowest(self.by_name["P50 Pocket"], "China"), ("CNY", 13999.0))

    def test_four_g_and_five_g_models_stay_separate(self):
        self.assertIn("Hot 50", self.by_name)
        self.assertIn("Hot 50 5G", self.by_name)


class EndToEndTests(unittest.TestCase):
    """GSMArena stand-in -> Flipkart import -> Mobiles 2025 import -> the project's own two build scripts."""

    @classmethod
    def setUpClass(cls):
        cls.db = make_gsmarena_db()
        fk_devices, _ = fk.clean(fk.read_rows(FLIPKART_SAMPLE))
        fk.write_all(cls.db, "?", fk_devices)
        cls.flipkart_count = cls.db.execute("SELECT COUNT(*) FROM device WHERE id LIKE 'flipkart-%'").fetchone()[0]
        cls.devices, _ = m25.clean(m25.read_rows(SAMPLE))
        cls.result = m25.write_all(cls.db, "?", cls.devices)
        extras = runpy.run_path(os.path.join(DB_DIR, "build_device_extras.py"), run_name="not_main")
        extras["run"](cls.db, "?", extras["DDL_SQLITE"])
        mysql_shim.install(cls.db)
        os.environ.update(DB_HOST="x", DB_PORT="1", DB_USERNAME="x", DB_PASSWORD="x")
        runpy.run_path(os.path.join(DB_DIR, "pipeline", "build_device_core_v4.py"), run_name="not_main")

    def one(self, sql, *params):
        return self.db.execute(sql, params).fetchone()

    def ids(self):
        return {r[0] for r in self.db.execute("SELECT id FROM device WHERE id LIKE 'm25-%'")}

    def test_models_already_in_the_data_are_not_added_again(self):
        ids = self.ids()
        self.assertNotIn("m25-apple-iphone-14", ids)          # in the GSMArena data
        self.assertNotIn("m25-samsung-galaxy-s22", ids)       # GSMArena has "Galaxy S22 5G": 5G is ignored
        self.assertNotIn("m25-apple-iphone-17-pro", ids)      # in the Flipkart data
        names = {n for n, _ in self.result["matched"]}
        self.assertEqual(names, {"Apple iPhone 14", "Samsung Galaxy S22", "Apple iPhone 17 Pro"})

    def test_prices_of_matched_models_are_attached_to_the_existing_device(self):
        rows = self.db.execute("SELECT DISTINCT country FROM device_price_v1 WHERE device_id = 'apple_iphone_14-3'").fetchall()
        self.assertEqual({r[0] for r in rows}, {"Pakistan", "India", "China", "USA", "Dubai"})
        self.assertEqual(self.one("SELECT amount FROM device_price_v1 WHERE device_id = 'apple_iphone_14-3' "
                                  "AND country = 'USA' AND variant = '6 GB RAM / 256 GB'")[0], 849)
        self.assertIsNotNone(self.one("SELECT 1 FROM device_price_v1 WHERE device_id = 'flipkart-apple-iphone-17-pro'"))

    def test_originals_and_flipkart_rows_are_untouched(self):
        self.assertEqual(self.one("SELECT COUNT(*) FROM device WHERE id LIKE 'flipkart-%'")[0], self.flipkart_count)
        self.assertEqual(self.one("SELECT COUNT(*) FROM device WHERE id NOT LIKE 'flipkart-%' AND id NOT LIKE 'm25-%'")[0], 4)

    def test_no_phone_exists_twice(self):
        rows = self.db.execute("SELECT b.name, d.name FROM device d LEFT JOIN brand b ON b.id = d.brand_id").fetchall()
        keys = [fk.name_key(b, n) for b, n in rows]
        # the only allowed repeats are the 4G / 5G pairs the dataset itself lists as two phones
        repeated = {k for k in keys if keys.count(k) > 1}
        self.assertEqual({fk.name_key("Infinix", "Hot 50")}, repeated)
        self.assertEqual(self.one("SELECT COUNT(*) FROM device")[0], len(set(r[0] for r in self.db.execute("SELECT id FROM device"))))

    def test_new_devices(self):
        self.assertEqual(self.ids(), {"m25-infinix-hot-50", "m25-infinix-hot-50-5g", "m25-huawei-p50-pocket",
                                      "m25-xiaomi-poco-x6-pro", "m25-oppo-reno-12", "m25-nokia-g42",
                                      "m25-motorola-razr-50-ultra"})

    def test_new_brands_are_created_only_when_missing(self):
        names = {r[0] for r in self.db.execute("SELECT name FROM brand WHERE id LIKE 'm25-brand-%'")}
        self.assertTrue({"Huawei", "Nokia"} <= names)
        self.assertNotIn("Xiaomi", names)  # Poco phones go under the existing Xiaomi brand
        self.assertEqual(self.one("SELECT brand_id FROM device WHERE id = 'm25-xiaomi-poco-x6-pro'")[0], "xiaomi-phones-80.php")

    def test_core_values(self):
        row = self.db.execute("SELECT ram_gb, storage_gb, battery_mah, weight_g, display_size_inches, main_camera_mp, "
                              "selfie_camera_mp, chipset FROM device_core_v4 WHERE id = 'm25-oppo-reno-12'").fetchone()
        self.assertEqual(row, (12, 256, 5000, 181, 6.7, 50, 50, "MediaTek Dimensity 7300"))

    def test_extras_values(self):
        row = self.db.execute("SELECT announced_year, data_source, price_inr, price_usd, has_spec_data FROM device_extras_v1 "
                              "WHERE device_id = 'm25-oppo-reno-12'").fetchone()
        self.assertEqual(row, (2024, "MOBILES_2025", 36999, 499, 1))

    def test_model_without_storage_keeps_ram_and_no_storage(self):
        row = self.db.execute("SELECT ram_gb, storage_gb, display_size_inches FROM device_core_v4 "
                              "WHERE id = 'm25-huawei-p50-pocket'").fetchone()
        self.assertEqual(row, (8, None, 6.9))

    def test_foldable_keeps_both_screens_in_the_text(self):
        value = self.one("SELECT spec_value FROM device_specification WHERE device_id = 'm25-motorola-razr-50-ultra' "
                         "AND specification = 'Size'")[0]
        self.assertIn("external", value)

    def test_os_is_inferred_only_where_safe(self):
        def os_of(device_id):
            r = self.one("SELECT spec_value FROM device_specification WHERE device_id = ? AND specification = 'OS'", device_id)
            return r[0] if r else None
        self.assertEqual(os_of("m25-oppo-reno-12"), "Android (inferred from brand)")
        self.assertIsNone(os_of("m25-huawei-p50-pocket"))

    def test_five_g_only_when_named(self):
        self.assertEqual(self.one("SELECT has_5g FROM device_core_v4 WHERE id = 'm25-infinix-hot-50-5g'")[0], 1)
        self.assertFalse(self.one("SELECT has_5g FROM device_core_v4 WHERE id = 'm25-infinix-hot-50'")[0])

    def test_importing_twice_gives_the_same_result(self):
        before = (self.one("SELECT COUNT(*) FROM device")[0], self.one("SELECT COUNT(*) FROM device_specification")[0],
                  self.one("SELECT COUNT(*) FROM device_price_v1")[0], self.one("SELECT COUNT(*) FROM brand")[0])
        m25.write_all(self.db, "?", self.devices)
        after = (self.one("SELECT COUNT(*) FROM device")[0], self.one("SELECT COUNT(*) FROM device_specification")[0],
                 self.one("SELECT COUNT(*) FROM device_price_v1")[0], self.one("SELECT COUNT(*) FROM brand")[0])
        self.assertEqual(before, after)

    def test_flipkart_reimport_removes_m25_rows_so_they_can_be_matched_again(self):
        db = make_gsmarena_db()
        fk_devices, _ = fk.clean(fk.read_rows(FLIPKART_SAMPLE))
        fk.write_all(db, "?", fk_devices)
        m25.write_all(db, "?", self.devices)
        self.assertGreater(db.execute("SELECT COUNT(*) FROM device WHERE id LIKE 'm25-%'").fetchone()[0], 0)
        fk.write_all(db, "?", fk_devices)
        self.assertEqual(db.execute("SELECT COUNT(*) FROM device WHERE id LIKE 'm25-%'").fetchone()[0], 0)
        self.assertEqual(db.execute("SELECT COUNT(*) FROM brand WHERE id LIKE 'm25-brand-%'").fetchone()[0], 0)


if __name__ == "__main__":
    unittest.main()
