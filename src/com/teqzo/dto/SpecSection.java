package com.teqzo.dto;

import java.util.List;

public record SpecSection(String key, String title, List<SpecItem> items) {
}
