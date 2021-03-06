/**
 * 
 */
package org.opensrp.web.rest;

import java.text.MessageFormat;

import org.keycloak.adapters.KeycloakDeployment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * @author Samuel Githengi created on 04/30/20
 */
@RestController
@RequestMapping(value = "/rest/config")
public class ConfigResource {
	
	@Autowired
	private KeycloakDeployment keycloakDeployment;
	
	@Value("#{opensrp['keycloak.configuration.endpoint']}")
	protected String keycloakConfigurationURL;
	
	@GetMapping(value = "/keycloak", produces = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<String> getKeycloakDetails() {
		String url = MessageFormat.format(keycloakConfigurationURL, keycloakDeployment.getAuthServerBaseUrl(),
		    keycloakDeployment.getRealm());
		return new RestTemplate().getForEntity(url, String.class);
		
	}
	
}
