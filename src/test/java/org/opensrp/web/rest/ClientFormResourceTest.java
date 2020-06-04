package org.opensrp.web.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.http.entity.ContentType;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.opensrp.TestFileContent;
import org.opensrp.domain.IdVersionTuple;
import org.opensrp.domain.postgres.ClientForm;
import org.opensrp.domain.postgres.ClientFormMetadata;
import org.opensrp.service.ClientFormService;
import org.opensrp.util.DateTimeDeserializer;
import org.opensrp.util.DateTimeSerializer;
import org.opensrp.web.Constants;
import org.opensrp.web.config.security.filter.CrossSiteScriptingPreventionFilter;
import org.opensrp.web.rest.it.TestWebContextLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.server.MockMvc;
import org.springframework.test.web.server.MvcResult;
import org.springframework.test.web.server.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.server.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.server.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.server.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = TestWebContextLoader.class, locations = {"classpath:test-webmvc-config.xml",})
@ActiveProfiles(profiles = { "jedis", "postgres", "basic_auth" })
public class ClientFormResourceTest {

    @Autowired
    protected WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private ClientFormService clientFormService;

    private String BASE_URL = "/rest/clientForm/";

    protected ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

    @Before
    public void setUp() {
        clientFormService = mock(ClientFormService.class);
        ClientFormResource clientFormResource = webApplicationContext.getBean(ClientFormResource.class);
        clientFormResource.setClientFormService(clientFormService);
        clientFormResource.setObjectMapper(mapper);

        mockMvc = MockMvcBuilders.webApplicationContextSetup(webApplicationContext).
                addFilter(new CrossSiteScriptingPreventionFilter(), "/*").build();

        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setDateFormat(DateFormat.getDateTimeInstance());

        SimpleModule dateTimeModule = new SimpleModule("DateTimeModule");
        dateTimeModule.addDeserializer(DateTime.class, new DateTimeDeserializer());
        dateTimeModule.addSerializer(DateTime.class, new DateTimeSerializer());
        mapper.registerModule(dateTimeModule);
    }

    @Test
    public void testSearchForFormByFormVersionShouldReturnSpecificVersion() throws Exception {
        String formIdentifier = "opd/reg.json";
        String formVersion = "0.0.3";
        String currentFormVersion = "0.0.1";
        List<IdVersionTuple> idVersionTuples = new ArrayList<>();
        idVersionTuples.add(new IdVersionTuple(1, "0.0.1"));
        idVersionTuples.add(new IdVersionTuple(2, "0.0.2"));
        idVersionTuples.add(new IdVersionTuple(3, "0.0.3"));

        ClientForm clientForm = new ClientForm();
        clientForm.setJson("{}");
        clientForm.setId(3L);

        ClientFormMetadata clientFormMetadata = new ClientFormMetadata();
        clientFormMetadata.setId(3L);
        clientFormMetadata.setIdentifier(formIdentifier);
        clientFormMetadata.setVersion("0.0.3");

        when(clientFormService.isClientFormExists(formIdentifier)).thenReturn(true);
        when(clientFormService.getClientFormMetadataByIdentifierAndVersion(formIdentifier, formVersion)).thenReturn(clientFormMetadata);
        when(clientFormService.getClientFormById(3L)).thenReturn(clientForm);
        when(clientFormService.getClientFormMetadataById(3L)).thenReturn(clientFormMetadata);

        MvcResult result = mockMvc.perform(get(BASE_URL)
                .param("form_identifier", formIdentifier)
                .param("form_version", formVersion)
                .param("current_form_version", currentFormVersion)
                .param("strict", "true"))
                .andExpect(status().isOk())
                .andReturn();
        String responseString = result.getResponse().getContentAsString();
        JsonNode jsonNode = mapper.readTree(responseString);
        assertEquals("{}", jsonNode.get("clientForm").get("json").textValue());
        assertEquals("opd/reg.json", jsonNode.get("clientFormMetadata").get("identifier").textValue());
        assertEquals("0.0.3", jsonNode.get("clientFormMetadata").get("version").textValue());
    }

    @Test
    public void testSearchForFormByFormVersionShouldReturnNoContentWhenVersionHasntChangedAndStrictIsTrue() throws Exception {
        String formIdentifier = "opd/reg.json";
        String formVersion = "0.1.1";
        String currentFormVersion = "0.0.3";
        List<IdVersionTuple> idVersionTuples = new ArrayList<>();
        idVersionTuples.add(new IdVersionTuple(1, "0.0.1"));
        idVersionTuples.add(new IdVersionTuple(2, "0.0.2"));
        idVersionTuples.add(new IdVersionTuple(3, "0.0.3"));

        ClientFormMetadata clientFormMetadata = new ClientFormMetadata();
        clientFormMetadata.setId(3L);
        clientFormMetadata.setIdentifier(formIdentifier);
        clientFormMetadata.setVersion("0.0.3");

        when(clientFormService.isClientFormExists(formIdentifier)).thenReturn(true);
        when(clientFormService.getClientFormMetadataByIdentifierAndVersion(formIdentifier, formVersion)).thenReturn(null);
        when(clientFormService.getAvailableClientFormMetadataVersionByIdentifier(formIdentifier)).thenReturn(idVersionTuples);
        when(clientFormService.getClientFormMetadataById(3L)).thenReturn(clientFormMetadata);

        MvcResult result = mockMvc.perform(get(BASE_URL)
                .param("form_identifier", formIdentifier)
                .param("form_version", formVersion)
                .param("strict", "True")
                .param("current_form_version", currentFormVersion))
                .andExpect(status().isNoContent())
                .andReturn();
        String responseString = result.getResponse().getContentAsString();
        assertEquals("", responseString);
    }

    @Test
    public void testSearchForFormByFormVersionShouldReturn404WhenStrictIsTrueAndCurrentFormVersionIsOutOfDate() throws Exception {
        String formIdentifier = "opd/reg.json";
        String formVersion = "0.1.1";
        String currentFormVersion = "0.0.2";
        List<IdVersionTuple> idVersionTuples = new ArrayList<>();
        idVersionTuples.add(new IdVersionTuple(1, "0.0.1"));
        idVersionTuples.add(new IdVersionTuple(2, "0.0.2"));
        idVersionTuples.add(new IdVersionTuple(3, "0.0.3"));

        ClientFormMetadata clientFormMetadata = new ClientFormMetadata();
        clientFormMetadata.setId(3L);
        clientFormMetadata.setIdentifier(formIdentifier);
        clientFormMetadata.setVersion("0.0.3");

        when(clientFormService.isClientFormExists(formIdentifier)).thenReturn(true);
        when(clientFormService.getClientFormMetadataByIdentifierAndVersion(formIdentifier, formVersion)).thenReturn(null);
        when(clientFormService.getAvailableClientFormMetadataVersionByIdentifier(formIdentifier)).thenReturn(idVersionTuples);
        when(clientFormService.getClientFormMetadataById(3L)).thenReturn(clientFormMetadata);

        MvcResult result = mockMvc.perform(get(BASE_URL)
                .param("form_identifier", formIdentifier)
                .param("form_version", formVersion)
                .param("strict", "True")
                .param("current_form_version", currentFormVersion))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals("", result.getResponse().getContentAsString());
    }

    @Test
    public void testSearchForFormByFormVersionShouldReturnNextVersion() throws Exception {
        String formIdentifier = "opd/reg.json";
        String formVersion = "0.1.1";
        String currentFormVersion = "0.0.1";
        List<IdVersionTuple> idVersionTuples = new ArrayList<>();
        idVersionTuples.add(new IdVersionTuple(1, "0.0.1"));
        idVersionTuples.add(new IdVersionTuple(2, "0.0.2"));
        idVersionTuples.add(new IdVersionTuple(3, "0.0.3"));

        ClientForm clientForm = new ClientForm();
        clientForm.setJson("{}");
        clientForm.setId(3L);

        ClientFormMetadata clientFormMetadata = new ClientFormMetadata();
        clientFormMetadata.setId(3L);
        clientFormMetadata.setIdentifier(formIdentifier);
        clientFormMetadata.setVersion("0.0.3");

        when(clientFormService.isClientFormExists(formIdentifier)).thenReturn(true);
        when(clientFormService.getClientFormMetadataByIdentifierAndVersion(formIdentifier, formVersion)).thenReturn(null);
        when(clientFormService.getAvailableClientFormMetadataVersionByIdentifier(formIdentifier)).thenReturn(idVersionTuples);
        when(clientFormService.getClientFormById(3L)).thenReturn(clientForm);
        when(clientFormService.getClientFormMetadataById(3L)).thenReturn(clientFormMetadata);

        MvcResult result = mockMvc.perform(get(BASE_URL)
                .param("form_identifier", formIdentifier)
                .param("form_version", formVersion)
                .param("current_form_version", currentFormVersion))
                .andExpect(status().isOk())
                .andReturn();
        String responseString = result.getResponse().getContentAsString();
        JsonNode jsonNode = mapper.readTree(responseString);
        assertEquals("{}", jsonNode.get("clientForm").get("json").textValue());
        assertEquals("opd/reg.json", jsonNode.get("clientFormMetadata").get("identifier").textValue());
        assertEquals("0.0.3", jsonNode.get("clientFormMetadata").get("version").textValue());
    }

    @Test
    public void testAddClientFormWhenGivenJSON() throws Exception {
        String formIdentifier = "opd/reg.json";
        String formVersion = "0.1.1";
        String formName = "REGISTRATION FORM";

        MockMultipartFile file = new MockMultipartFile("form", "path/to/opd/reg.json",
                "application/json", TestFileContent.JSON_FORM_FILE.getBytes());

        when(clientFormService.addClientForm(any(ClientForm.class), any(ClientFormMetadata.class))).thenReturn(mock(ClientFormService.CompleteClientForm.class));

        mockMvc.perform(
                fileUpload(BASE_URL)
                        .file(file)
                        .param("form_identifier", formIdentifier)
                        .param("form_version", formVersion)
                        .param("form_name", formName))
                .andExpect(status().isCreated())
                .andReturn();

        ArgumentCaptor<ClientForm> clientFormArgumentCaptor = ArgumentCaptor.forClass(ClientForm.class);
        ArgumentCaptor<ClientFormMetadata> clientFormMetadataArgumentCaptor = ArgumentCaptor.forClass(ClientFormMetadata.class);
        verify(clientFormService).addClientForm(clientFormArgumentCaptor.capture(), clientFormMetadataArgumentCaptor.capture());

        assertEquals(TestFileContent.JSON_FORM_FILE, clientFormArgumentCaptor.getValue().getJson().toString());
        ClientFormMetadata clientFormMetadata = clientFormMetadataArgumentCaptor.getValue();
        assertEquals(formIdentifier, clientFormMetadata.getIdentifier());
        assertEquals(formVersion, clientFormMetadata.getVersion());
        assertEquals(formName, clientFormMetadata.getLabel());
        assertNull(clientFormMetadata.getModule());
    }

    @Test
    public void testAddClientFormWhenGivenInvalidJSONShouldReturn400() throws Exception {
        String formIdentifier = "opd/reg.json";
        String formVersion = "0.1.1";
        String formName = "REGISTRATION FORM";

        MockMultipartFile file = new MockMultipartFile("form", "path/to/opd/reg.json",
                "application/json", TestFileContent.JSON_FORM_FILE.substring(0, 20).getBytes());

        when(clientFormService.addClientForm(any(ClientForm.class), any(ClientFormMetadata.class))).thenReturn(mock(ClientFormService.CompleteClientForm.class));

        MvcResult result = mockMvc.perform(
                fileUpload(BASE_URL)
                        .file(file)
                        .param("form_identifier", formIdentifier)
                        .param("form_version", formVersion)
                        .param("form_name", formName))
                .andExpect(status().isBadRequest())
                .andReturn();

        verifyNoInteractions(clientFormService);

        String errorMessage = result.getResponse().getContentAsString();
        assertEquals("File content error:", errorMessage.substring(0, 19));
    }

    @Test
    public void testAddClientFormWhenGivenYaml() throws Exception {
        String formIdentifier = "opd/calculation.yaml";
        String formVersion = "0.1.1";
        String formName = "Calculation file";

        MockMultipartFile file = new MockMultipartFile("form", "path/to/opd/calculation.yaml",
                "application/x-yaml", TestFileContent.CALCULATION_YAML_FILE_CONTENT.getBytes());

        when(clientFormService.addClientForm(any(ClientForm.class), any(ClientFormMetadata.class))).thenReturn(mock(ClientFormService.CompleteClientForm.class));

        mockMvc.perform(
                fileUpload(BASE_URL)
                        .file(file)
                        .param("form_identifier", formIdentifier)
                        .param("form_version", formVersion)
                        .param("form_name", formName))
                .andExpect(status().isCreated())
                .andReturn();

        ArgumentCaptor<ClientForm> clientFormArgumentCaptor = ArgumentCaptor.forClass(ClientForm.class);
        ArgumentCaptor<ClientFormMetadata> clientFormMetadataArgumentCaptor = ArgumentCaptor.forClass(ClientFormMetadata.class);
        verify(clientFormService).addClientForm(clientFormArgumentCaptor.capture(), clientFormMetadataArgumentCaptor.capture());

        assertEquals(TestFileContent.CALCULATION_YAML_FILE_CONTENT, clientFormArgumentCaptor.getValue().getJson().toString());
        ClientFormMetadata clientFormMetadata = clientFormMetadataArgumentCaptor.getValue();
        assertEquals(formIdentifier, clientFormMetadata.getIdentifier());
        assertEquals(formVersion, clientFormMetadata.getVersion());
        assertEquals(formName, clientFormMetadata.getLabel());
        assertNull(clientFormMetadata.getModule());
    }

    @Test
    public void testAddClientFormWhenGivenInvalidYamlShouldReturn400() throws Exception {
        String formIdentifier = "opd/calculation.yaml";
        String formVersion = "0.1.1";
        String formName = "Calculation file";

        MockMultipartFile file = new MockMultipartFile("form", "path/to/opd/calculation.yaml",
                "application/x-yaml", TestFileContent.CALCULATION_YAML_FILE_CONTENT.substring(0, 10).getBytes());

        when(clientFormService.addClientForm(any(ClientForm.class), any(ClientFormMetadata.class))).thenReturn(mock(ClientFormService.CompleteClientForm.class));

        MvcResult result = mockMvc.perform(
                fileUpload(BASE_URL)
                        .file(file)
                        .param("form_identifier", formIdentifier)
                        .param("form_version", formVersion)
                        .param("form_name", formName))
                .andExpect(status().isBadRequest())
                .andReturn();

        ArgumentCaptor<ClientForm> clientFormArgumentCaptor = ArgumentCaptor.forClass(ClientForm.class);
        ArgumentCaptor<ClientFormMetadata> clientFormMetadataArgumentCaptor = ArgumentCaptor.forClass(ClientFormMetadata.class);
        verifyNoInteractions(clientFormService);

        String errorMessage = result.getResponse().getContentAsString();
        assertEquals("File content error:", errorMessage.substring(0, 19));
    }

    @Test
    public void testAddClientFormWhenGivenPropertiesFile() throws Exception {
        String formIdentifier = "opd/opd_register.properties";
        String formVersion = "0.1.1";
        String formName = "Registration properties file";

        MockMultipartFile file = new MockMultipartFile("form", "path/to/opd/opd_register.properties",
                "application/octet-stream", TestFileContent.JMAG_PROPERTIES_FILE_CONTENT.getBytes());

        when(clientFormService.addClientForm(any(ClientForm.class), any(ClientFormMetadata.class))).thenReturn(mock(ClientFormService.CompleteClientForm.class));

        mockMvc.perform(
                fileUpload(BASE_URL)
                        .file(file)
                        .param("form_identifier", formIdentifier)
                        .param("form_version", formVersion)
                        .param("form_name", formName))
                .andExpect(status().isCreated())
                .andReturn();

        ArgumentCaptor<ClientForm> clientFormArgumentCaptor = ArgumentCaptor.forClass(ClientForm.class);
        ArgumentCaptor<ClientFormMetadata> clientFormMetadataArgumentCaptor = ArgumentCaptor.forClass(ClientFormMetadata.class);
        verify(clientFormService).addClientForm(clientFormArgumentCaptor.capture(), clientFormMetadataArgumentCaptor.capture());

        assertEquals(TestFileContent.JMAG_PROPERTIES_FILE_CONTENT, clientFormArgumentCaptor.getValue().getJson().toString());
        ClientFormMetadata clientFormMetadata = clientFormMetadataArgumentCaptor.getValue();
        assertEquals(formIdentifier, clientFormMetadata.getIdentifier());
        assertEquals(formVersion, clientFormMetadata.getVersion());
        assertEquals(formName, clientFormMetadata.getLabel());
        assertNull(clientFormMetadata.getModule());
    }

    @Test
    public void testIsClientFormContentTypeValidShouldReturnTrueWhenGivenJSON() throws Exception {
        ClientFormResource clientFormResource = webApplicationContext.getBean(ClientFormResource.class);
        assertTrue(clientFormResource.isClientFormContentTypeValid(ContentType.APPLICATION_JSON.getMimeType()));
    }

    @Test
    public void testIsClientFormContentTypeValidShouldReturnTrueWhenGivenApplicationYaml() throws Exception {
        ClientFormResource clientFormResource = webApplicationContext.getBean(ClientFormResource.class);
        assertTrue(clientFormResource.isClientFormContentTypeValid(Constants.ContentType.APPLICATION_YAML));
    }

    @Test
    public void testIsClientFormContentTypeValidShouldReturnTrueWhenGivenTextYamlContentTypeForYamlFile() throws Exception {
        ClientFormResource clientFormResource = webApplicationContext.getBean(ClientFormResource.class);
        assertTrue(clientFormResource.isClientFormContentTypeValid(Constants.ContentType.TEXT_YAML));
    }

    @Test
    public void testIsPropertiesFileShouldReturnTrue() {
        ClientFormResource clientFormResource = webApplicationContext.getBean(ClientFormResource.class);
        assertTrue(clientFormResource.isPropertiesFile("application/octet-stream", "anc_register.properties"));
    }

    @Test
    public void testIsPropertiesFileShouldReturnFalse() {
        ClientFormResource clientFormResource = webApplicationContext.getBean(ClientFormResource.class);
        assertFalse(clientFormResource.isPropertiesFile("application/octet-stream", "anc_register"));
    }

    @Test
    public void testCheckValidContentShouldReturnErrorMessageWhenGivenInvalidJSONStructure() {
        ClientFormResource clientFormResource = webApplicationContext.getBean(ClientFormResource.class);
        assertNotNull(clientFormResource.checkValidJsonYamlPropertiesStructure(TestFileContent.JSON_FORM_FILE.substring(0, 10), "application/json"));
    }

    @Test
    public void testCheckValidJsonYamlPropertiesStructureShouldReturnErrorMessageWhenGivenInvalidYamlStructure() {
        ClientFormResource clientFormResource = webApplicationContext.getBean(ClientFormResource.class);
        assertNotNull(clientFormResource.checkValidJsonYamlPropertiesStructure(TestFileContent.CALCULATION_YAML_FILE_CONTENT.substring(0, 10), "application/x-yaml"));
    }

}
