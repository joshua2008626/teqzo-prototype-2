package com.teqzo.config;

import com.teqzo.ai.AiProvider;
import com.teqzo.ai.AiProviderFactory;
import com.teqzo.ai.AiRecommendationService;
import com.teqzo.ai.AiSettings;
import com.teqzo.ai.DefaultAiRecommendationService;
import com.teqzo.ai.RuleBasedRequirementParser;
import com.teqzo.dto.BrandDto;
import com.teqzo.recommendation.RecommendationService;
import com.teqzo.repository.BrandRepository;
import com.teqzo.repository.Database;
import com.teqzo.repository.DeviceRepository;
import com.teqzo.repository.Jdbc;
import com.teqzo.repository.JdbcBrandRepository;
import com.teqzo.repository.JdbcDeviceRepository;
import com.teqzo.service.BrandService;
import com.teqzo.service.ComparisonService;
import com.teqzo.service.DeviceService;
import com.teqzo.service.FacetsService;
import com.teqzo.service.InsightsService;
import com.teqzo.service.RegionalPriceService;
import com.teqzo.service.ShortlistStore;
import com.teqzo.service.SimilarityService;
import com.teqzo.service.PricePolicy;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Creates the objects of the application and connects them (constructor injection written by hand, no framework):
 * database -> repositories -> services -> assistant.
 */
public final class AppContext {

	private static final int FALLBACK_NEWEST_YEAR = 2022;

	public final AppConfig config;
	public final Database database;
	public final Jdbc jdbc;
	public final DeviceRepository devices;
	public final BrandRepository brandRepository;
	public final PricePolicy pricePolicy;
	public final BrandService brands;
	public final FacetsService facets;
	public final DeviceService deviceService;
	public final ComparisonService comparison;
	public final RegionalPriceService regionalPrices;
	public final SimilarityService similarity;
	public final InsightsService insights;
	public final ShortlistStore shortlist;
	public final RecommendationService recommendations;
	public final AiSettings aiSettings;
	public final AiRecommendationService assistant;

	public AppContext(AppConfig config, Database database) {
		this.config = config;
		this.database = database;
		this.jdbc = new Jdbc(database);
		this.devices = new JdbcDeviceRepository(jdbc);
		this.brandRepository = new JdbcBrandRepository(jdbc);
		this.pricePolicy = new PricePolicy(config.get("PRICE_EUR_TO_INR"));
		this.brands = new BrandService(brandRepository);
		this.facets = new FacetsService(devices);
		this.deviceService = new DeviceService(devices, brandRepository, pricePolicy);
		this.comparison = new ComparisonService(deviceService, pricePolicy);
		this.regionalPrices = new RegionalPriceService(devices);
		this.similarity = new SimilarityService(devices, pricePolicy);
		this.insights = new InsightsService(devices);
		this.shortlist = new ShortlistStore(Path.of(config.get("TEQZO_SHORTLIST", "teqzo-shortlist.txt")));
		this.recommendations = new RecommendationService(devices, brands, facets, pricePolicy);
		this.aiSettings = AiSettings.resolve(config.get("AI_PROVIDER", "openai"), config.get("AI_API_KEY"),
				config.get("AI_MODEL"), config.get("AI_BASE_URL"), config.getInt("AI_TIMEOUT_SECONDS", 30));
		Optional<AiProvider> provider = AiProviderFactory.create(aiSettings);
		RuleBasedRequirementParser rules = new RuleBasedRequirementParser(
				() -> brands.all().stream().map(BrandDto::name).toList(),
				() -> {
					Integer year = facets.get().maxReleaseYear();
					return year == null ? FALLBACK_NEWEST_YEAR : year;
				});
		this.assistant = new DefaultAiRecommendationService(aiSettings, provider, rules, recommendations);
	}
}
