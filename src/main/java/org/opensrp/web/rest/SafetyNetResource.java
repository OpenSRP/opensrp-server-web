package org.opensrp.web.rest;

import org.json.JSONObject;
import org.opensrp.domain.AttestationStatement;
import org.opensrp.service.SafetyNetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.springframework.http.HttpStatus.*;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Controller
@RequestMapping(value = "/rest/safetynet")
public class SafetyNetResource {
	private static Logger logger = LoggerFactory.getLogger(CampaignResource.class.toString());

	private SafetyNetService safetyNetService;

	@Autowired
	public void setSafetyNetService(SafetyNetService safetyNetService) {
		this.safetyNetService = safetyNetService;
	}

	@RequestMapping(headers = { "Accept=application/json" }, method = POST)
	public ResponseEntity<String> verify(@RequestBody String data) {

		JSONObject syncData = new JSONObject(data);
		if (!syncData.has("jws_result")) {
			return new ResponseEntity<>(BAD_REQUEST);
		}

		String jwsResult = syncData.getString("jws_result");
		AttestationStatement stmt = safetyNetService.parseAndVerify(jwsResult);

		JSONObject request = new JSONObject();

		if (stmt == null) {
			logger.error("Failure: Failed to parse and verify the attestation statement.");

			request.put("status", false);
			request.put("msg", "Failed to parse and verify the attestation statement");
		}
		else if(stmt.isCtsProfileMatch() && stmt.hasBasicIntegrity()) {

			request.put("status", true);
			request.put("msg", "Verification Passed");
		} else {

			request.put("status", false);
			request.put("msg", "Verification Failed");
		}

		return new ResponseEntity<>(request.toString(), HttpStatus.OK);
	}
}
