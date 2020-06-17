package org.opensrp.web.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensrp.TestFileContent;
import org.opensrp.domain.postgres.ClientForm;
import org.opensrp.service.ClientFormService;

import java.text.DateFormat;
import java.util.HashSet;

public class ClientFormValidatorTest extends TestCase {

    @Mock
    private ClientFormService clientFormService;

    private ClientFormValidator clientFormValidator;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        clientFormValidator = new ClientFormValidator(clientFormService);
    }

    public void testCheckForMissingFormReferencesShouldReturnEmptyHashSetWhenGivenSubForm() {
        HashSet<String> missingReferences = clientFormValidator.checkForMissingFormReferences(TestFileContent.HIV_TESTS_SUB_FORM);
        assertEquals(0, missingReferences.size());
    }

    public void testCheckForMissingFormReferencesShouldReturnEmptyHashSetWhenGivenFormWithoutSubFormReferences() {
        HashSet<String> missingReferences = clientFormValidator.checkForMissingFormReferences(TestFileContent.ANC_QUICK_CHECK_JSON_FORM_FILE);
        assertEquals(0, missingReferences.size());
    }

    public void testCheckForMissingFormReferencesShouldReturnNonEmptyHashSetWhenGivenFormWithMissingSubFormReferences() {
        Mockito.doReturn(true).when(clientFormService).isClientFormExists("abdominal_exam_sub_form");

        HashSet<String> missingReferences = clientFormValidator.checkForMissingFormReferences(TestFileContent.PHYSICAL_EXAM_FORM_FILE);
        Mockito.verify(clientFormService, Mockito.times(13)).isClientFormExists(Mockito.anyString());
        assertEquals(6, missingReferences.size());
    }

    public void testCheckForMissingFormReferencesShouldReturnEmptyHashSetWhenGivenFormWithAvailableSubFormReferences() {
        Mockito.doReturn(true).when(clientFormService).isClientFormExists(Mockito.anyString());

        HashSet<String> missingReferences = clientFormValidator.checkForMissingFormReferences(TestFileContent.PHYSICAL_EXAM_FORM_FILE);
        Mockito.verify(clientFormService, Mockito.times(7)).isClientFormExists(Mockito.anyString());
        assertEquals(0, missingReferences.size());
    }

    public void testCheckForMissingRuleReferencesShouldReturnEmptyHashSetWhenGivenFormWithoutRuleFileReferences() {
        HashSet<String> missingReferences = clientFormValidator.checkForMissingRuleReferences(TestFileContent.JSON_FORM_FILE);
        Mockito.verifyNoInteractions(clientFormService);
        assertEquals(0, missingReferences.size());
    }

    public void testCheckForMissingRuleReferencesShouldReturnNonEmptyHashSetWhenGivenFormWithMissingRuleFileReferences() {
        Mockito.doReturn(true).when(clientFormService).isClientFormExists("physical-exam-relevance-rules.yml");

        HashSet<String> missingReferences = clientFormValidator.checkForMissingRuleReferences(TestFileContent.PHYSICAL_EXAM_FORM_FILE);
        Mockito.verify(clientFormService, Mockito.times(2)).isClientFormExists(Mockito.anyString());
        assertEquals(1, missingReferences.size());
    }

    public void testCheckForMissingRuleReferencesShouldReturnEmptyHashSetWhenGivenFormWithAvailableRuleFiles() {
        Mockito.doReturn(true).when(clientFormService).isClientFormExists("physical-exam-relevance-rules.yml");
        Mockito.doReturn(true).when(clientFormService).isClientFormExists("physical-exam-calculations-rules.yml");

        HashSet<String> missingReferences = clientFormValidator.checkForMissingRuleReferences(TestFileContent.PHYSICAL_EXAM_FORM_FILE);
        Mockito.verify(clientFormService, Mockito.times(2)).isClientFormExists(Mockito.anyString());
        assertEquals(0, missingReferences.size());
    }

    public void testCheckForMissingPropertyFileReferencesShouldReturnEmptyHashSetWhenGivenFormWithAvailablePropertyFiles() {
        Mockito.doReturn(true).when(clientFormService).isClientFormExists("anc_register.properties");

        HashSet<String> missingReferences = clientFormValidator.checkForMissingPropertyFileReferences(TestFileContent.ANC_REGISTER_JSON_FORM_FILE);
        Mockito.verify(clientFormService, Mockito.times(2)).isClientFormExists(Mockito.anyString());
        assertEquals(0, missingReferences.size());
    }

    public void testCheckForMissingPropertyFileReferencesShouldReturnNonEmptyHashSetWhenGivenFormWithMissingPropertyFiles() {
        HashSet<String> missingReferences = clientFormValidator.checkForMissingPropertyFileReferences(TestFileContent.ANC_REGISTER_JSON_FORM_FILE);
        Mockito.verify(clientFormService, Mockito.times(2)).isClientFormExists(Mockito.anyString());
        assertEquals(1, missingReferences.size());
        assertEquals("anc_register", missingReferences.toArray()[0]);
    }


    public void testPerformWidgetValidation() throws JsonProcessingException {
        String formIdentifier = "anc_registration.json";

        ClientForm formValidator = new ClientForm();
        formValidator.setJson("{\"cannot_remove\":{\"title\":\"Fields you cannot remove\",\"fields\":[\"reaction_vaccine_duration\",\"reaction_vaccine_dosage\", \"aefi_form\"]}}");

        Mockito.doReturn(formValidator)
                .when(clientFormService).getMostRecentFormValidator(formIdentifier);


        ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setDateFormat(DateFormat.getDateTimeInstance());

        HashSet<String> missingWidgets = clientFormValidator.performWidgetValidation(mapper, formIdentifier, TestFileContent.JSON_FORM_FILE);
        assertEquals(2, missingWidgets.size());
    }
}
