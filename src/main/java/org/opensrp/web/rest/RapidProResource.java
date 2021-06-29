package org.opensrp.web.rest;

import org.opensrp.web.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping(value = "/rest/rapidpro")
public class RapidProResource {

	@Value("#{opensrp['rapidpro.token']}")
	private String rapidProToken;

	@RequestMapping(value = "/callback", method = RequestMethod.POST, consumes = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.TEXT_PLAIN_VALUE }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> callback(HttpServletRequest request, @RequestBody String payload) {

		String apiToken = request.getHeader(Constants.AUTHORIZATION);
		String[] tokenValues = apiToken.split(" ");

		if (tokenValues.length == 2 && rapidProToken.equalsIgnoreCase(tokenValues[1].trim())) {
			ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.ACCEPTED);
			return responseEntity;
		}
		return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
	}
}
