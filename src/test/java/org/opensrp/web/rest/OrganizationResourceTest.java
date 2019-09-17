/**
 * 
 */
package org.opensrp.web.rest;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.server.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.server.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.server.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.server.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.opensrp.domain.Organization;
import org.opensrp.service.OrganizationService;
import org.opensrp.web.rest.it.TestWebContextLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.server.MockMvc;
import org.springframework.test.web.server.MvcResult;
import org.springframework.test.web.server.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Samuel Githengi created on 09/17/19
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = TestWebContextLoader.class, locations = { "classpath:test-webmvc-config.xml", })
public class OrganizationResourceTest {

	@Rule
	public MockitoRule rule = MockitoJUnit.rule();

	@Autowired
	protected WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@Mock
	private OrganizationService organizationService;

	@Captor
	private ArgumentCaptor<Organization> organizationArgumentCaptor;

	private String BASE_URL = "/rest/organization/";

	private String organizationJSON = "{\"identifier\":\"801874c0-d963-11e9-8a34-2a2ae2dbcce4\",\"active\":true,\"name\":\"B Team\",\"partOf\":1123,\"type\":{\"coding\":[{\"system\":\"http://terminology.hl7.org/CodeSystem/organization-type\",\"code\":\"team\",\"display\":\"Team\"}]}}";

	private static ObjectMapper objectMapper = new ObjectMapper();

	@Before
	public void setUp() {
		organizationService = Mockito.mock(OrganizationService.class);
		OrganizationResource organizationResource = webApplicationContext.getBean(OrganizationResource.class);
		organizationResource.setOrganizationService(organizationService);
		mockMvc = MockMvcBuilders.webApplicationContextSetup(webApplicationContext).build();
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
	}

	@Test
	public void testGetAllOrganizations() throws Exception {
		List<Organization> expected = Collections.singletonList(getOrganization());
		when(organizationService.getAllOrganizations()).thenReturn(expected);
		MvcResult result = mockMvc.perform(get(BASE_URL)).andExpect(status().isOk()).andReturn();
		verify(organizationService).getAllOrganizations();
		verifyNoMoreInteractions(organizationService);
		assertEquals("[" + organizationJSON + "]", result.getResponse().getContentAsString());

	}

	@Test
	public void testGetOrganizationByIdentifier() throws Exception {
		Organization expected = getOrganization();
		when(organizationService.getOrganization(expected.getIdentifier())).thenReturn(expected);
		MvcResult result = mockMvc.perform(get(BASE_URL + "/{identifier}", expected.getIdentifier()))
				.andExpect(status().isOk()).andReturn();
		verify(organizationService).getOrganization(expected.getIdentifier());
		verifyNoMoreInteractions(organizationService);
		assertEquals(organizationJSON, result.getResponse().getContentAsString());

	}

	@Test
	public void testCreateOrganization() throws Exception {

		mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).body(organizationJSON.getBytes()))
				.andExpect(status().isCreated());
		verify(organizationService).addOrganization(organizationArgumentCaptor.capture());
		assertEquals(organizationJSON, objectMapper.writeValueAsString(organizationArgumentCaptor.getValue()));
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testCreateOrganizationWithoutIdentifier() throws Exception {
		Mockito.doThrow(IllegalArgumentException.class).when(organizationService)
				.addOrganization(any(Organization.class));
		mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).body(organizationJSON.getBytes()))
				.andExpect(status().isBadRequest());
		verify(organizationService).addOrganization(organizationArgumentCaptor.capture());
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testCreateOrganizationWithError() throws Exception {
		Mockito.doThrow(SQLException.class).when(organizationService).addOrganization(any(Organization.class));
		mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).body(organizationJSON.getBytes()))
				.andExpect(status().isInternalServerError());
		verify(organizationService).addOrganization(organizationArgumentCaptor.capture());
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testUpdateOrganization() throws Exception {

		mockMvc.perform(put(BASE_URL + "/{identifier}", getOrganization().getIdentifier())
				.contentType(MediaType.APPLICATION_JSON).body(organizationJSON.getBytes()))
				.andExpect(status().isCreated());
		verify(organizationService).updateOrganization(organizationArgumentCaptor.capture());
		assertEquals(organizationJSON, objectMapper.writeValueAsString(organizationArgumentCaptor.getValue()));
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testUpdateOrganizationWithoutIdentifier() throws Exception {
		Mockito.doThrow(IllegalArgumentException.class).when(organizationService)
				.updateOrganization(any(Organization.class));
		mockMvc.perform(put(BASE_URL + "/{identifier}", getOrganization().getIdentifier())
				.contentType(MediaType.APPLICATION_JSON).body(organizationJSON.getBytes()))
				.andExpect(status().isBadRequest());
		verify(organizationService).updateOrganization(organizationArgumentCaptor.capture());
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testUpdateOrganizationWithError() throws Exception {
		Mockito.doThrow(SQLException.class).when(organizationService).updateOrganization(any(Organization.class));
		mockMvc.perform(put(BASE_URL + "/{identifier}", getOrganization().getIdentifier())
				.contentType(MediaType.APPLICATION_JSON).body(organizationJSON.getBytes()))
				.andExpect(status().isInternalServerError());
		verify(organizationService).updateOrganization(organizationArgumentCaptor.capture());
		verifyNoMoreInteractions(organizationService);

	}

	private Organization getOrganization() {
		try {
			return objectMapper.readValue(organizationJSON, Organization.class);
		} catch (IOException e) {
			return null;
		}
	}

}
