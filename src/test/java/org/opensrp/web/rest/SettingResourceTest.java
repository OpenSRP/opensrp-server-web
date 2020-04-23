package org.opensrp.web.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.server.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.server.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.opensrp.common.AllConstants.BaseEntity;
import org.opensrp.common.AllConstants.Event;
import org.opensrp.domain.setting.SettingConfiguration;
import org.opensrp.repository.SettingRepository;
import org.opensrp.search.SettingSearchBean;
import org.opensrp.service.SettingService;
import org.opensrp.util.DateTimeTypeConverter;
import org.opensrp.web.rest.it.TestWebContextLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.server.MockMvc;
import org.springframework.test.web.server.MvcResult;
import org.springframework.test.web.server.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = TestWebContextLoader.class, locations = { "classpath:test-webmvc-config.xml", })
public class SettingResourceTest {
	
	@Autowired
	protected WebApplicationContext webApplicationContext;
	
	private SettingService settingService;
	
	private SettingRepository settingRepository;
	
	private Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
	        .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter()).create();
	
	private String BASE_URL = "/rest/settings/";
	
	private String settingJson = "{\n" + "    \"_id\": \"1\",\n" + "    \"_rev\": \"v1\",\n"
	        + "    \"type\": \"SettingConfiguration\",\n" + "    \"identifier\": \"site_characteristics\",\n"
	        + "    \"documentId\": \"document-id\",\n" + "    \"locationId\": \"\",\n" + "    \"providerId\": \"\",\n"
	        + "    \"teamId\": \"my-team-id\",\n" + "    \"dateCreated\": \"1970-10-04T10:17:09.993+03:00\",\n"
	        + "    \"serverVersion\": 1,\n" + "    \"settings\": [\n" + "        {\n"
	        + "            \"key\": \"site_ipv_assess\",\n"
	        + "            \"label\": \"Minimum requirements for IPV assessment\",\n" + "            \"value\": null,\n"
	        + "            \"description\": \"Are all of the following in place at your facility: \\r\\n\\ta. A protocol or standard operating procedure for Intimate Partner Violence (IPV); \\r\\n\\tb. A health worker trained on how to ask about IPV and how to provide the minimum response or beyond;\\r\\n\\tc. A private setting; \\r\\n\\td. A way to ensure confidentiality; \\r\\n\\te. Time to allow for appropriate disclosure; and\\r\\n\\tf. A system for referral in place. \"\n"
	        + "        },\n" + "        {\n" + "            \"key\": \"site_anc_hiv\",\n"
	        + "            \"label\": \"Generalized HIV epidemic\",\n" + "            \"value\": null,\n"
	        + "            \"description\": \"Is the HIV prevalence consistently > 1% in pregnant women attending antenatal clinics at your facility?\"\n"
	        + "        },\n" + "        {\n" + "            \"key\": \"site_ultrasound\",\n"
	        + "            \"label\": \"Ultrasound available\",\n" + "            \"value\": null,\n"
	        + "            \"description\": \"Is an ultrasound machine available and functional at your facility and a trained health worker available to use it?\"\n"
	        + "        },\n" + "        {\n" + "            \"key\": \"site_bp_tool\",\n"
	        + "            \"label\": \"Automated BP measurement tool\",\n" + "            \"value\": null,\n"
	        + "            \"description\": \"Does your facility use an automated blood pressure (BP) measurement tool?\"\n"
	        + "        }\n" + "    ]\n" + "}";
	
	private String settingJsonUpdate = "{\n" + "    \"_id\": \"settings-document-id-2\",\n" + "    \"_rev\": \"v1\",\n"
	        + "    \"type\": \"SettingConfiguration\",\n" + "    \"identifier\": \"site_characteristics\",\n"
	        + "    \"documentId\": \"settings-document-id-2\",\n" 
	        + "    \"locationId\": \"\",\n" + "    \"providerId\": \"\",\n" + "    \"teamId\": \"my-team-id\",\n"
	        + "    \"dateCreated\": \"1970-10-04T10:17:09.993+03:00\",\n" + "    \"serverVersion\": 1,\n"
	        + "    \"settings\": [\n" + "        {\n" + "            \"key\": \"site_ipv_assess\",\n"
	        + "            \"label\": \"Minimum requirements for IPV assessment\",\n" + "            \"value\": null,\n"
	        + "            \"description\": \"Are all of the following in place at your facility: \\r\\n\\ta. A protocol or standard operating procedure for Intimate Partner Violence (IPV); \\r\\n\\tb. A health worker trained on how to ask about IPV and how to provide the minimum response or beyond;\\r\\n\\tc. A private setting; \\r\\n\\td. A way to ensure confidentiality; \\r\\n\\te. Time to allow for appropriate disclosure; and\\r\\n\\tf. A system for referral in place. \"\n"
	        + "        },\n" + "        {\n" + "            \"key\": \"site_anc_hiv\",\n"
	        + "            \"label\": \"Generalized HIV epidemic\",\n" + "            \"value\": null,\n"
	        + "            \"description\": \"Is the HIV prevalence consistently > 1% in pregnant women attending antenatal clinics at your facility?\"\n"
	        + "        },\n" + "        {\n" + "            \"key\": \"site_ultrasound\",\n"
	        + "            \"label\": \"Ultrasound available\",\n" + "            \"value\": null,\n"
	        + "            \"description\": \"Is an ultrasound machine available and functional at your facility and a trained health worker available to use it?\"\n"
	        + "        },\n" + "        {\n" + "            \"key\": \"site_bp_tool\",\n"
	        + "            \"label\": \"Automated BP measurement tool\",\n" + "            \"value\": null,\n"
	        + "            \"description\": \"Does your facility use an automated blood pressure (BP) measurement tool?\"\n"
	        + "        }\n" + "    ]\n" + "}";
	
	private List<SettingConfiguration> listSettingConfigurations;
	
	private ArgumentCaptor<SettingConfiguration> settingConfigurationArgumentCaptor = ArgumentCaptor
	        .forClass(SettingConfiguration.class);
	
	private MockMvc mockMvc;
	
	@Before
	public void setUp() {
		settingService = Mockito.spy(new SettingService());
		settingRepository = Mockito.mock(SettingRepository.class);
		settingService.setSettingRepository(settingRepository);
		SettingResource settingResource = webApplicationContext.getBean(SettingResource.class);
		settingResource.setSettingService(settingService);
		
		listSettingConfigurations = new ArrayList<>();
		
		SettingConfiguration settingConfiguration = new SettingConfiguration();
		settingConfiguration.setIdentifier("site_characteristics");
		settingConfiguration.setTeamId("my-team-id");
		listSettingConfigurations.add(settingConfiguration);
		
		mockMvc = MockMvcBuilders.webApplicationContextSetup(webApplicationContext).build();
	}
	
	@Test
	public void testGetByUniqueId() throws Exception {
		SettingSearchBean settingQueryBean = new SettingSearchBean();
		settingQueryBean.setTeamId("my-team-id");
		
		List<SettingConfiguration> settingConfig = new ArrayList<>();
		
		SettingConfiguration config = new SettingConfiguration();
		settingConfig.add(config);
		settingQueryBean.setServerVersion(0L);
		Mockito.when(settingService.findSettings(settingQueryBean)).thenReturn(settingConfig);
		
		MvcResult result = mockMvc.perform(get(BASE_URL + "/sync").param(BaseEntity.SERVER_VERSIOIN, "0")
		        .param(Event.TEAM_ID, "my-team-id").param(Event.PROVIDER_ID, "demo")).andExpect(status().isOk()).andReturn();
		
		Mockito.verify(settingService, Mockito.times(1)).findSettings(settingQueryBean);

		assertEquals(new ArrayList<>().toString(), result.getResponse().getContentAsString());
	}

	@Test
	public void findSettingsByVersionShouldReturn500IfServerVersionIsNotSpecified() throws Exception {
		MvcResult result = mockMvc.perform(get(BASE_URL + "/sync")).andExpect(status().isBadRequest()).andReturn();
		assertEquals("{}", result.getResponse().getContentAsString());
	}
	
	@Test
	public void testFindSettingsByVersionAndTeamId() throws Exception {
		SettingSearchBean sQB = new SettingSearchBean();
		sQB.setTeamId("my-team-id");
		sQB.setTeam(null);
		sQB.setLocationId(null);
		sQB.setProviderId(null);
		sQB.setServerVersion(1000L);
		
		settingService.findSettings(sQB);
		Mockito.verify(settingRepository, Mockito.times(1)).findSettings(sQB);
		Mockito.verifyNoMoreInteractions(settingRepository);
		
	}
	
	@Test
	public void testSaveSetting() throws Exception {
		String documentId = "1";
		Mockito.doNothing().when(settingRepository).add(Matchers.any(SettingConfiguration.class));
		settingService.saveSetting(settingJson);
		
		Mockito.verify(settingRepository, Mockito.times(1)).add(settingConfigurationArgumentCaptor.capture());
		Mockito.verify(settingRepository, Mockito.times(1)).get(documentId);
		Mockito.verifyNoMoreInteractions(settingRepository);
	}
	
	@Test
	public void testUpdateSetting() throws Exception {
		String documentId = "settings-document-id-2";
		Mockito.when(settingRepository.get("settings-document-id-2")).thenReturn(new SettingConfiguration());
		Mockito.doNothing().when(settingRepository).update(Matchers.any(SettingConfiguration.class));
		
		settingService.saveSetting(settingJsonUpdate);
		
		Mockito.verify(settingRepository, Mockito.times(1)).get(documentId);
		Mockito.verify(settingRepository, Mockito.times(1)).update(settingConfigurationArgumentCaptor.capture());
		Mockito.verifyNoMoreInteractions(settingRepository);
	}
	
	@Test
	public void testAddServerVersion() throws Exception {
		
		settingService.addServerVersion();
		Mockito.verify(settingRepository, Mockito.times(1)).findByEmptyServerVersion();
		Mockito.verifyNoMoreInteractions(settingRepository);
	}
	
	@Test
	public void testValidValue() throws Exception {
		SettingConfiguration settingConfiguration = getSettingConfigurationObject();
		assertNotNull(settingConfiguration);
		assertEquals("site_characteristics", settingConfiguration.getIdentifier());
		assertEquals("my-team-id", settingConfiguration.getTeamId());
	}
	
	private SettingConfiguration getSettingConfigurationObject() {
		return gson.fromJson(settingJson, new TypeToken<SettingConfiguration>() {}.getType());
	}
}
