package org.opensrp.web.listener;

import org.opensrp.service.rapidpro.RapidProService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

@Profile("rapidpro")
@Service
@EnableScheduling
public class RapidProListener {

	private RapidProService rapidProService;

	@Autowired
	public void setRapidProService(RapidProService rapidProService) {
		this.rapidProService = rapidProService;
	}

	public void requestRapidProContacts() {
		rapidProService.queryContacts(() -> {
			//To be implemented
		});
	}
}
