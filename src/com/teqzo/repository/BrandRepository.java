package com.teqzo.repository;

import com.teqzo.dto.BrandDto;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {

	/** All brands with the number of devices actually present in the processed device table. */
	List<BrandDto> findAll();

	Optional<BrandDto> findById(String id);
}
