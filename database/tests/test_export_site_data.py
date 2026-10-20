import json
import os
import sqlite3
import tempfile
import unittest
import sys
import zlib

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import export_site_data as ex  # noqa: E402


def make_db():
    c = sqlite3.connect(":memory:")
    c.executescript("""
    CREATE TABLE brand(id TEXT, name TEXT);
    CREATE TABLE device_core_v4(id TEXT, brand_id TEXT, name TEXT, ram_gb REAL, storage_gb REAL, battery_mah REAL,
      wired_charging_w REAL, display_size_inches REAL, refresh_rate_hz REAL, os TEXT, chipset TEXT, main_camera_mp REAL,
      selfie_camera_mp REAL, weight_g REAL, has_5g INTEGER, ip_rating TEXT, display_type TEXT);
    CREATE TABLE device_extras_v1(device_id TEXT, brand_name TEXT, announced_year INT, listed_year INT, data_source TEXT,
      price_inr REAL, price_usd REAL, price_eur REAL, wireless_charging_status TEXT, nfc_supported INT,
      charging_raw TEXT, has_spec_data INT);
    CREATE TABLE device_specification(spec_id INTEGER PRIMARY KEY, device_id TEXT, spec_category TEXT, specification TEXT, spec_value TEXT);
    CREATE TABLE device_price_v1(device_id TEXT, country TEXT, currency TEXT, variant TEXT, amount REAL);
    INSERT INTO brand VALUES('b1','Acme');
    INSERT INTO device_core_v4 VALUES('gsm-1','b1','Acme One',8,128,5000,33,6.5,120,'Android 14','X1',50,16,190,0,NULL,'OLED');
    INSERT INTO device_core_v4 VALUES('m25-acme-two','b1','Acme Two',6,128,5000,NULL,6.6,NULL,NULL,NULL,50,8,NULL,0,NULL,NULL);
    INSERT INTO device_core_v4 VALUES('gsm-3','b1','Name only',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL);
    INSERT INTO device_extras_v1 VALUES('gsm-1','Acme',2023,NULL,NULL,29999,NULL,NULL,'YES',1,NULL,1);
    INSERT INTO device_extras_v1 VALUES('m25-acme-two','Acme',NULL,2025,'MOBILES_2025',NULL,299,NULL,NULL,NULL,NULL,1);
    INSERT INTO device_extras_v1 VALUES('gsm-3','Acme',2010,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0);
    INSERT INTO device_specification(device_id,spec_category,specification,spec_value) VALUES('gsm-1','Body','Weight','190 g');
    INSERT INTO device_specification(device_id,spec_category,specification,spec_value) VALUES('gsm-3','Body','Weight','x');
    INSERT INTO device_price_v1 VALUES('m25-acme-two','USA','USD','6/128',299);
    INSERT INTO device_price_v1 VALUES('gsm-3','USA','USD','',1);
    """)
    return c


class ExportTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.meta, self.priced = ex.export(make_db(), self.tmp.name, None)
        with open(os.path.join(self.tmp.name, "devices.json"), encoding="utf-8") as f:
            self.payload = json.load(f)
        self.rows = {r[0]: dict(zip(self.payload["fields"], r)) for r in self.payload["rows"]}

    def test_only_phones_with_specs(self):
        self.assertEqual(set(self.rows), {"gsm-1", "m25-acme-two"})

    def test_five_g_unknown_for_small_datasets(self):
        self.assertEqual(self.rows["gsm-1"]["g5"], 0)
        self.assertIsNone(self.rows["m25-acme-two"]["g5"])

    def test_missing_stays_null_and_source_codes(self):
        two = self.rows["m25-acme-two"]
        self.assertIsNone(two["w"])
        self.assertIsNone(two["pi"])
        self.assertEqual(two["s"], 2)
        self.assertEqual(self.rows["gsm-1"]["wl"], 1)

    def test_prices_only_for_exported_devices(self):
        with open(os.path.join(self.tmp.name, "prices.json"), encoding="utf-8") as f:
            prices = json.load(f)
        self.assertEqual(list(prices), ["m25-acme-two"])
        self.assertEqual(self.priced, 1)

    def test_spec_bucket_matches_crc32(self):
        b = zlib.crc32(b"gsm-1") % 128
        with open(os.path.join(self.tmp.name, "specs", f"{b:02x}.json"), encoding="utf-8") as f:
            self.assertIn("gsm-1", json.load(f))
        self.assertEqual(len(os.listdir(os.path.join(self.tmp.name, "specs"))), 128)

    def test_curated_layer_adds_phones_and_marks_them(self):
        with tempfile.TemporaryDirectory() as out:
            ex.export(make_db(), out)
            with open(os.path.join(out, "devices.json"), encoding="utf-8") as f:
                payload = json.load(f)
            cu = payload["fields"].index("cu")
            self.assertTrue(any(r[cu] == 1 for r in payload["rows"]))
            with open(os.path.join(out, "curated.json"), encoding="utf-8") as f:
                self.assertTrue(json.load(f))


if __name__ == "__main__":
    unittest.main()
