package com.playmonumenta.structures.api.service;

import com.playmonumenta.structures.utils.Services;

public interface ZoneServiceProvider extends Services.ServiceProvider<ZoneService> {
	@Override
	default int priority() {
		return 0;
	}

	@Override
	ZoneService createService();
}
