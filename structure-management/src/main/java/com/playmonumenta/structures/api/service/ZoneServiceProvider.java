package com.playmonumenta.structures.api.service;

import com.playmonumenta.structures.utils.Services;

public interface ZoneServiceProvider extends Services.ServiceProvider<ZoneService> {
	@Override
	int priority();

	@Override
	ZoneService createService();
}
