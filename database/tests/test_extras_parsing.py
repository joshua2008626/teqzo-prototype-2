import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from extras_parsing import (compact_search_text, parse_bluetooth_version, parse_charging, parse_cpu,
                            parse_nfc, parse_price, parse_resolution, parse_status_code,
                            parse_usb_type_c, parse_wifi_generation, parse_year)


class ChargingTests(unittest.TestCase):
    def test_wireless_watts_before_keyword(self):
        r = parse_charging("18W wireless")
        self.assertEqual((r["wireless_w"], r["wireless_status"], r["wired_w"]), (18.0, "YES", None))

    def test_wired_and_magnetic_wireless(self):
        r = parse_charging("66W wired; 15W magnetic wireless (gravity version)")
        self.assertEqual((r["wired_w"], r["wireless_w"]), (66.0, 15.0))

    def test_wireless_without_watts_is_not_zero(self):
        r = parse_charging("Wireless (Qi)")
        self.assertIsNone(r["wireless_w"])
        self.assertEqual(r["wireless_status"], "YES")

    def test_conditional_wireless(self):
        self.assertEqual(parse_charging("Wireless (Qi) (optional)")["wireless_status"], "CONDITIONAL")
        self.assertEqual(parse_charging("Wireless (Qi) (market dependent)")["wireless_status"], "CONDITIONAL")

    def test_reverse_is_not_wireless(self):
        r = parse_charging("18W wired, 5W reverse wired")
        self.assertEqual((r["wired_w"], r["reverse_w"], r["wireless_status"]), (18.0, 5.0, None))
        self.assertEqual(r["reverse_status"], "YES")

    def test_unknown(self):
        self.assertIsNone(parse_charging(None)["wired_w"])
        self.assertIsNone(parse_charging("Fast charging")["wired_w"])


class PriceTests(unittest.TestCase):
    def test_multi_currency(self):
        r = parse_price("€\u20091,099.00 / $\u2009899.99 / £\u2009949.00 / ₹\u200989,900")
        self.assertEqual(r, {"inr": 89900.0, "eur": 1099.0, "usd": 899.99})

    def test_about_form(self):
        self.assertEqual(parse_price("About 330 EUR")["eur"], 330.0)
        self.assertEqual(parse_price("About 5000 INR")["inr"], 5000.0)

    def test_unrelated_currency_ignored(self):
        self.assertEqual(parse_price("Rp\u20091,658,000"), {"inr": None, "eur": None, "usd": None})
        self.assertIsNone(parse_price("C$\u20091,049.99")["usd"])

    def test_empty(self):
        self.assertEqual(parse_price(None), {"inr": None, "eur": None, "usd": None})


class MiscTests(unittest.TestCase):
    def test_search_text(self):
        self.assertEqual(compact_search_text("Apple", "iPhone 15 Pro"), "appleiphone15pro")

    def test_years_and_status(self):
        self.assertEqual(parse_year("2016, August. Released 2016, October"), 2016)
        self.assertIsNone(parse_year("Not officially announced yet"))
        self.assertEqual(parse_year("2018, March"), 2018)
        self.assertEqual(parse_status_code("Available. Released 2019, September"), "AVAILABLE")
        self.assertEqual(parse_status_code("Cancelled"), "CANCELLED")

    def test_cpu(self):
        self.assertEqual(parse_cpu("Octa-core (2x2.2 GHz 360 Gold & 6x1.7 GHz Kryo 360 Silver)"), (8, 2.2))
        self.assertEqual(parse_cpu("Quad-core 1.2 GHz Cortex-A7"), (4, 1.2))
        self.assertEqual(parse_cpu("Hexa-core (2x2.93 GHz + 4xX.X GHz)"), (6, 2.93))
        self.assertEqual(parse_cpu(None), (None, None))

    def test_resolution(self):
        self.assertEqual(parse_resolution("1080 x 2400 pixels, 20:9 ratio (~409 ppi density)"), (1080, 2400))
        self.assertEqual(parse_resolution("Yes"), (None, None))

    def test_nfc(self):
        self.assertEqual((parse_nfc("Yes"), parse_nfc("No"), parse_nfc("Yes (optional)"), parse_nfc(None)),
                         (1, 0, None, None))

    def test_wifi_bt_usb(self):
        self.assertEqual(parse_wifi_generation("Wi-Fi 802.11 a/b/g/n/ac/6, dual-band"), 6)
        self.assertEqual(parse_wifi_generation("Wi-Fi 802.11 a/b/g/n/ac, dual-band"), 5)
        self.assertEqual(parse_wifi_generation("Wi-Fi 802.11 b/g/n"), 4)
        self.assertIsNone(parse_wifi_generation("No"))
        self.assertEqual(parse_bluetooth_version("5.0, A2DP, LE"), 5.0)
        self.assertIsNone(parse_bluetooth_version("Yes"))
        self.assertEqual(parse_usb_type_c("USB Type-C 2.0, USB On-The-Go"), 1)
        self.assertEqual(parse_usb_type_c("microUSB 2.0"), 0)


if __name__ == "__main__":
    unittest.main()
