/**
 * 
 */
package org.opensrp.web.config.security;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.OPTIONS;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationProvider;
import org.keycloak.adapters.springsecurity.client.KeycloakClientRequestFactory;
import org.keycloak.adapters.springsecurity.client.KeycloakRestTemplate;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.opensrp.web.config.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * @author Samuel Githengi created on 04/20/20
 */
@KeycloakConfiguration
public class SecurityConfig extends KeycloakWebSecurityConfigurerAdapter {
	
	@Value("#{opensrp['opensrp.cors.allowed.source']}")
	private String opensrpAllowedSources;
	
	@Value("#{opensrp['opensrp.cors.max.age']}")
	private long corsMaxAge;
	
	@Value("${keycloak.configurationFile:WEB-INF/keycloak.json}")
	private Resource keycloakConfigFileResource;
	
	@Autowired
	private KeycloakClientRequestFactory keycloakClientRequestFactory;
	
	private static final String CORS_ALLOWED_HEADERS = "origin,content-type,accept,x-requested-with,Authorization";
	
	/**
	 * Registers the KeycloakAuthenticationProvider with the authentication manager.
	 */
	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
		SimpleAuthorityMapper grantedAuthorityMapper = new SimpleAuthorityMapper();
		grantedAuthorityMapper.setPrefix("ROLE_");
		
		KeycloakAuthenticationProvider keycloakAuthenticationProvider = keycloakAuthenticationProvider();
		keycloakAuthenticationProvider.setGrantedAuthoritiesMapper(grantedAuthorityMapper);
		auth.authenticationProvider(keycloakAuthenticationProvider);
	}
	
	/**
	 * Defines the session authentication strategy.
	 */
	@Bean
	@Override
	protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
	}
	
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		super.configure(http);
		/* @formatter:off */
		http.cors()
		.and()
			.authorizeRequests()
			.mvcMatchers("/index.html").permitAll()
			.mvcMatchers("/").permitAll()
			.mvcMatchers("/logout.do").permitAll()
			.mvcMatchers("/rest/viewconfiguration/**").permitAll()
			.mvcMatchers("/rest/config/keycloak").permitAll()
			.mvcMatchers("/rest/*/getAll").hasRole(Role.ALL_EVENTS)
			.mvcMatchers(OPTIONS,"/**").permitAll()
			.mvcMatchers("/rest/**").hasRole(Role.OPENMRS)
			.anyRequest().authenticated()
		.and()
    		.csrf()
    		.ignoringAntMatchers("/rest/**","/multimedia/**")
    	.and()
    		.logout()
    		.logoutRequestMatcher(new AntPathRequestMatcher("logout.do", "GET"));
		/* @formatter:on */
	}
	
	@Override
	public void configure(WebSecurity web) throws Exception {
		/* @formatter:off */
		web.ignoring().mvcMatchers("/js/**")
			.and().ignoring().mvcMatchers("/css/**")
			.and().ignoring().mvcMatchers("/images/**")
			.and().ignoring().mvcMatchers("/rest/rapidpro/**");
		/* @formatter:on */
	}
	
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.asList(opensrpAllowedSources.split(",")));
		configuration.setAllowedMethods(Arrays.asList(GET.name(), POST.name(), PUT.name(), DELETE.name()));
		configuration.setAllowedHeaders(Arrays.asList(CORS_ALLOWED_HEADERS.split(",")));
		configuration.setMaxAge(corsMaxAge);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
	
	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public KeycloakRestTemplate keycloakRestTemplate() {
		return new KeycloakRestTemplate(keycloakClientRequestFactory);
	}
	
	@Bean
	public KeycloakDeployment keycloakDeployment() throws IOException {
		if (!keycloakConfigFileResource.isReadable()) {
			throw new FileNotFoundException(String.format("Unable to locate Keycloak configuration file: %s",
			    keycloakConfigFileResource.getFilename()));
		}
		
		try(InputStream inputStream=keycloakConfigFileResource.getInputStream()){
			return KeycloakDeploymentBuilder.build(inputStream);
		}
		
	}
	
}
