package com.teqzo;

import com.teqzo.domain.DeviceRow;
import com.teqzo.dto.BrandDto;
import com.teqzo.dto.Facets;
import com.teqzo.dto.PageResponse;
import com.teqzo.filter.DeviceFilter;
import com.teqzo.repository.BrandRepository;
import com.teqzo.repository.DeviceRepository;
import com.teqzo.repository.SpecRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared in-memory fakes and sample devices for tests. */
public final class TestData {

	private TestData() {
	}

	public static DeviceRow device(String id, String brand, String name) {
		DeviceRow d = new DeviceRow();
		d.id = id;
		d.brandId = brand.toLowerCase() + "-phones-1.php";
		d.brandName = brand;
		d.name = name;
		d.hasSpecData = true;
		d.os = "Android 12";
		d.statusCode = "AVAILABLE";
		d.announcedYear = 2022;
		return d;
	}

	/** A strong all-rounder with everything listed. */
	public static DeviceRow flagship() {
		DeviceRow d = device("samsung_flag-1.php", "Samsung", "Flagship One");
		d.ramGb = 12.0;
		d.storageGb = 256.0;
		d.batteryMah = 5000.0;
		d.wiredChargingW = 45.0;
		d.wirelessChargingW = 15.0;
		d.wirelessChargingStatus = "YES";
		d.chargingRaw = "45W wired, 15W wireless";
		d.displaySizeInches = 6.8;
		d.displayType = "Dynamic AMOLED 2X";
		d.refreshRateHz = 120.0;
		d.resolutionShortPx = 1440;
		d.resolutionLongPx = 3088;
		d.chipset = "Exynos 2200";
		d.cpuCores = 8;
		d.cpuMaxGhz = 2.8;
		d.mainCameraMp = 108.0;
		d.selfieCameraMp = 40.0;
		d.has5g = true;
		d.nfcRaw = "Yes";
		d.nfcSupported = true;
		d.wifiGeneration = 6;
		d.bluetoothVersion = 5.2;
		d.usbTypeC = true;
		d.ipRating = "IP68";
		d.priceInr = 79999.0;
		return d;
	}

	/** A cheap phone: little listed, no 5G, price in INR. */
	public static DeviceRow budget() {
		DeviceRow d = device("xiaomi_budget-2.php", "Xiaomi", "Budget Plus");
		d.ramGb = 4.0;
		d.storageGb = 64.0;
		d.batteryMah = 6000.0;
		d.wiredChargingW = 18.0;
		d.chargingRaw = "18W wired";
		d.displaySizeInches = 6.5;
		d.displayType = "IPS LCD";
		d.mainCameraMp = 50.0;
		d.has5g = false;
		d.priceInr = 12999.0;
		d.announcedYear = 2021;
		return d;
	}

	public static final class FakeBrandRepository implements BrandRepository {
		@Override
		public List<BrandDto> findAll() {
			return List.of(new BrandDto("samsung-phones-1.php", "Samsung", 2),
					new BrandDto("xiaomi-phones-1.php", "Xiaomi", 2),
					new BrandDto("apple-phones-1.php", "Apple", 1),
					new BrandDto("empty-phones-1.php", "Empty", 0));
		}

		@Override
		public Optional<BrandDto> findById(String id) {
			return findAll().stream().filter(b -> b.id().equals(id)).findFirst();
		}
	}

	/** Applies a small subset of filters in memory; enough to exercise the services. */
	public static class FakeDeviceRepository implements DeviceRepository {
		public final List<DeviceRow> rows = new ArrayList<>();
		public int candidateQueries;
		public RuntimeException failWith;

		public FakeDeviceRepository(DeviceRow... devices) {
			rows.addAll(List.of(devices));
		}

		private boolean matches(DeviceRow d, DeviceFilter f) {
			if (f.ramMinGb != null && (d.ramGb == null || d.ramGb < f.ramMinGb)) return false;
			if (f.batteryMinMah != null && (d.batteryMah == null || d.batteryMah < f.batteryMinMah)) return false;
			if (f.refreshRateMinHz != null && (d.refreshRateHz == null || d.refreshRateHz < f.refreshRateMinHz)) return false;
			if (Boolean.TRUE.equals(f.has5g) && !d.has5g) return false;
			if (Boolean.TRUE.equals(f.wirelessCharging) && !"YES".equals(d.wirelessChargingStatus)) return false;
			if (Boolean.TRUE.equals(f.nfc) && !Boolean.TRUE.equals(d.nfcSupported)) return false;
			if (f.brandIds != null && !f.brandIds.contains(d.brandId)) return false;
			if (f.priceMaxInr != null) {
				if (d.priceInr == null ? !f.includeUnknownPrice : d.priceInr > f.priceMaxInr) return false;
			}
			if (f.q != null && !(d.brandName + d.name).toLowerCase().replace(" ", "").contains(f.q.toLowerCase().replace(" ", ""))) return false;
			return true;
		}

		@Override
		public PageResponse<DeviceRow> search(DeviceFilter f, Double fx) {
			if (failWith != null) throw failWith;
			List<DeviceRow> all = rows.stream().filter(d -> matches(d, f)).toList();
			int from = Math.min(all.size(), f.page * f.size);
			int to = Math.min(all.size(), from + f.size);
			return PageResponse.of(all.subList(from, to), f.page, f.size, all.size());
		}

		@Override
		public List<DeviceRow> findCandidates(DeviceFilter f, Double fx, int limit) {
			candidateQueries++;
			return rows.stream().filter(d -> matches(d, f)).limit(limit).toList();
		}

		@Override
		public Optional<DeviceRow> findById(String id) {
			return rows.stream().filter(d -> d.id.equals(id)).findFirst();
		}

		@Override
		public List<DeviceRow> findByIds(List<String> ids) {
			return rows.stream().filter(d -> ids.contains(d.id)).toList();
		}

		@Override
		public List<SpecRow> findSpecifications(String id) {
			return List.of(new SpecRow("Display", "Type", "AMOLED"), new SpecRow("Tests", "Performance", "\r\nAnTuTu: 100 (v9)"));
		}

		@Override
		public Facets facets() {
			return new Facets(List.of(4.0, 12.0), List.of(64.0, 256.0), List.of(120), List.of("OLED"), List.of("Android"),
					2021, 2022, 6000, 45.0, rows.size(), rows.size(), 2, 2, 0);
		}
	}
}
