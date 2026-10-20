package com.teqzo.filter;

import java.util.List;

/**
 * Every field is optional; a {@code null} field means "do not filter on this".
 * Hard requirements: devices whose value is unknown never match a filter on that value.
 */
public class DeviceFilter {
	public String q;
	public List<String> brandIds;
	public List<String> excludeBrandIds;

	public Double ramMinGb;
	public Double storageMinGb;
	public Double batteryMinMah;
	public Double wiredChargingMinW;
	public Boolean wirelessCharging;
	public Double wirelessChargingMinW;
	public Boolean reverseCharging;
	public Boolean has5g;
	public Boolean nfc;
	public Boolean ipRated;

	public Double displaySizeMinInches;
	public Double displaySizeMaxInches;
	public String displayType;
	public Double refreshRateMinHz;
	public String chipset;
	public String os;

	public Double mainCameraMinMp;
	public Double selfieCameraMinMp;
	public Double weightMaxG;

	/** Budget in INR. Devices without a usable price never match; see {@link DeviceQueryBuilder}. */
	public Double priceMaxInr;
	public Double priceMinInr;
	/** When true, devices with an unknown price are NOT excluded by the budget (recommendation relaxation). */
	public boolean includeUnknownPrice;

	public Integer releasedFromYear;
	/**
	 * When true, {@link #releasedFromYear}/{@link #releasedToYear} also accept devices whose launch date is unknown
	 * but that were listed for sale in a given year (Flipkart import). Used by the recommender; browsing keeps the
	 * strict "announced" meaning.
	 */
	public boolean includeListedYear;
	public Integer releasedToYear;
	public boolean excludeCancelled;
	/** Only devices that have specification data (hides the ~680 name-only entries). */
	public boolean onlyWithSpecifications;
	/** Only devices whose operating system is listed (excludes feature phones and name-only entries). */
	public boolean requireOsListed;

	public SortOption sort;
	public int page;
	public int size = 20;

	public DeviceFilter copy() {
		DeviceFilter c = new DeviceFilter();
		c.q = q;
		c.brandIds = brandIds;
		c.excludeBrandIds = excludeBrandIds;
		c.ramMinGb = ramMinGb;
		c.storageMinGb = storageMinGb;
		c.batteryMinMah = batteryMinMah;
		c.wiredChargingMinW = wiredChargingMinW;
		c.wirelessCharging = wirelessCharging;
		c.wirelessChargingMinW = wirelessChargingMinW;
		c.reverseCharging = reverseCharging;
		c.has5g = has5g;
		c.nfc = nfc;
		c.ipRated = ipRated;
		c.displaySizeMinInches = displaySizeMinInches;
		c.displaySizeMaxInches = displaySizeMaxInches;
		c.displayType = displayType;
		c.refreshRateMinHz = refreshRateMinHz;
		c.chipset = chipset;
		c.os = os;
		c.mainCameraMinMp = mainCameraMinMp;
		c.selfieCameraMinMp = selfieCameraMinMp;
		c.weightMaxG = weightMaxG;
		c.priceMaxInr = priceMaxInr;
		c.priceMinInr = priceMinInr;
		c.includeUnknownPrice = includeUnknownPrice;
		c.releasedFromYear = releasedFromYear;
		c.includeListedYear = includeListedYear;
		c.releasedToYear = releasedToYear;
		c.excludeCancelled = excludeCancelled;
		c.onlyWithSpecifications = onlyWithSpecifications;
		c.requireOsListed = requireOsListed;
		c.sort = sort;
		c.page = page;
		c.size = size;
		return c;
	}
}
