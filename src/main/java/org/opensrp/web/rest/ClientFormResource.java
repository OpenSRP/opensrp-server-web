package org.opensrp.web.rest;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.util.TextUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jeasy.rules.mvel.MVELRuleFactory;
import org.jeasy.rules.support.YamlRuleDefinitionReader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opensrp.domain.IdVersionTuple;
import org.opensrp.domain.Manifest;
import org.opensrp.domain.postgres.ClientForm;
import org.opensrp.domain.postgres.ClientFormMetadata;
import org.opensrp.service.ClientFormService;
import org.opensrp.service.ManifestService;
import org.opensrp.web.Constants;
import org.opensrp.web.utils.ClientFormValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.*;

@Controller
@RequestMapping(value = "/rest/clientForm")
public class ClientFormResource {

    public static final String FORM_IDENTIFIERS = "identifiers";

    private static Logger logger = LoggerFactory.getLogger(EventResource.class.toString());
    protected ObjectMapper objectMapper;
    private ClientFormService clientFormService;
    private ManifestService manifestService;
    private ClientFormValidator clientFormValidator;

    @Autowired
    public void setClientFormService(ClientFormService clientFormService, ManifestService manifestService) {
        this.clientFormService = clientFormService;
        this.manifestService = manifestService;
        this.clientFormValidator = new ClientFormValidator(clientFormService);
    }

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RequestMapping(method = RequestMethod.GET, path = "/metadata")
    private ResponseEntity<String> getClientFormMetadataList(@RequestParam(value = "is_draft", required = false) String isDraftParam) throws JsonProcessingException {
        List<ClientFormMetadata> clientFormMetadataList = new ArrayList<>();
        if (isDraftParam == null) {
            clientFormMetadataList = clientFormService.getAllClientFormMetadata();
        } else {
            boolean isDraft = Boolean.parseBoolean(isDraftParam.toLowerCase());
            clientFormMetadataList = clientFormService.getClientFormMetadata(isDraft);
        }
        return new ResponseEntity<>(objectMapper.writeValueAsString(clientFormMetadataList.toArray(new ClientFormMetadata[0])), HttpStatus.OK);
    }

    @RequestMapping(method = RequestMethod.GET)
    private ResponseEntity<String> searchForFormByFormVersionAndIdentifier(@RequestParam(value = "form_identifier") String formIdentifier
            , @RequestParam(value = "form_version") String formVersion
            , @RequestParam(value = "strict", defaultValue = "false") String strict
            , @RequestParam(value = "current_form_version", required = false) String currentFormVersion
            , @RequestParam(value = "is_json_validator", defaultValue = "false") String isJsonValidatorStringRep) throws JsonProcessingException {
        boolean strictSearch = Boolean.parseBoolean(strict.toLowerCase());
        boolean isJsonValidator = Boolean.parseBoolean(isJsonValidatorStringRep.toLowerCase());
        DefaultArtifactVersion formVersionRequired = new DefaultArtifactVersion(formVersion);
        DefaultArtifactVersion currentFormVersionV = null;
        if (!TextUtils.isEmpty(currentFormVersion)) {
            currentFormVersionV = new DefaultArtifactVersion(currentFormVersion);
        }

        if (currentFormVersionV != null && currentFormVersionV.compareTo(formVersionRequired) > 0) {
            return new ResponseEntity<>((String) null, HttpStatus.BAD_REQUEST);
        }

        if (!clientFormService.isClientFormExists(formIdentifier, isJsonValidator)) {
            return new ResponseEntity<>((String) null, HttpStatus.NOT_FOUND);
        }

        // Check if the form identifier with that version exists
        ClientFormMetadata clientFormMetadata = clientFormService.getClientFormMetadataByIdentifierAndVersion(formIdentifier, formVersion, isJsonValidator);
        ClientFormService.CompleteClientForm completeClientForm = null;

        long formId;

        if (clientFormMetadata == null) {
            // Get an older form version
            clientFormMetadata = getMostRecentVersion(formIdentifier, isJsonValidator);
            if (clientFormMetadata == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            } else {
                formId = clientFormMetadata.getId();

                if (strictSearch) {
                    if (clientFormMetadata.getVersion().equals(currentFormVersion)) {
                        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
                    } else {
                        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                    }
                }
            }
        } else {
            formId = clientFormMetadata.getId();
        }

        ClientForm clientForm = clientFormService.getClientFormById(formId);
        if (clientForm == null) {
            return new ResponseEntity<>((String) null, HttpStatus.NOT_FOUND);
        }

        completeClientForm = new ClientFormService.CompleteClientForm(clientForm, clientFormMetadata);

        return new ResponseEntity<>(objectMapper.writeValueAsString(completeClientForm), HttpStatus.OK);
    }

    private ClientFormMetadata getMostRecentVersion(@NonNull String formIdentifier, boolean isJsonValidator) {
        List<IdVersionTuple> availableFormVersions = clientFormService.getAvailableClientFormMetadataVersionByIdentifier(formIdentifier, isJsonValidator);
        DefaultArtifactVersion highestVersion = null;
        IdVersionTuple highestIdVersionTuple = null;

        for (IdVersionTuple availableFormVersion : availableFormVersions) {
            DefaultArtifactVersion semanticFormVersion = new DefaultArtifactVersion(availableFormVersion.getVersion());

            if (highestVersion == null || semanticFormVersion.compareTo(highestVersion) > 0) {
                highestVersion = semanticFormVersion;
                highestIdVersionTuple = availableFormVersion;
            }
        }

        if (highestVersion == null) {
            return null;
        } else {
            long formId = highestIdVersionTuple.getId();
            return clientFormService.getClientFormMetadataById(formId);
        }
    }

    @RequestMapping(method = RequestMethod.GET, path = "release-related-files")
    private ResponseEntity<String> getAllFilesRelatedToRelease(@RequestParam(value = "identifier") String releaseIdentifier)
            throws JsonProcessingException {
        if (StringUtils.isBlank(releaseIdentifier)) {
            return new ResponseEntity<>("Request parameter cannot be empty", HttpStatus.BAD_REQUEST);
        }

        List<ClientFormMetadata> clientFormMetadataList = new ArrayList<>();

        Manifest manifest = manifestService.getManifest(releaseIdentifier);
        if (manifest != null && StringUtils.isNotBlank(manifest.getJson())) {
            JSONObject json = new JSONObject(manifest.getJson());
            if (json.has(FORM_IDENTIFIERS)) {
                JSONArray fileIdentifiers = json.getJSONArray(FORM_IDENTIFIERS);
                if (fileIdentifiers != null && fileIdentifiers.length() > 0) {
                    for (int i = 0; i < fileIdentifiers.length(); i++) {
                        String fileIdentifier = fileIdentifiers.getString(i);
                        ClientFormMetadata clientFormMetadata = getMostRecentVersion(fileIdentifier, false);
                        if (clientFormMetadata != null) {
                            clientFormMetadataList.add(clientFormMetadata);
                        }
                    }
                } else {
                    return new ResponseEntity<>("This manifest does not have any files related to it",
                                HttpStatus.NO_CONTENT);
                }
            } else {
                return new ResponseEntity<>("This manifest does not have any files related to it",
                       HttpStatus.NO_CONTENT);
            }
        } else {
            return new ResponseEntity<>("This manifest does not have any files related to it",
                    HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(objectMapper.writeValueAsString(clientFormMetadataList.toArray(new ClientFormMetadata[0])), HttpStatus.OK);
    }

    @RequestMapping(headers = {"Accept=multipart/form-data"}, method = RequestMethod.POST)
    private ResponseEntity<String> addClientForm(@RequestParam("form_version") String formVersion,
                                                 @RequestParam(value = "form_identifier", required = false) String formIdentifier,
                                                 @RequestParam(value = "form_relation", required = false) String relation,
                                                 @RequestParam("form_name") String formName,
                                                 @RequestParam("form") MultipartFile jsonFile,
                                                 @RequestParam(required = false) String module,
                                                 @RequestParam(value = "is_json_validator", defaultValue = "false") String isJsonValidatorStringRep)
            throws JsonProcessingException {
        boolean isJsonValidator = Boolean.parseBoolean(isJsonValidatorStringRep.toLowerCase());
        if (TextUtils.isEmpty(formVersion) || TextUtils.isEmpty(formName) || jsonFile.isEmpty()) {
            return new ResponseEntity<>("Required params is empty", HttpStatus.BAD_REQUEST);
        }

        String fileContentType = jsonFile.getContentType();
        if (!(isClientFormContentTypeValid(fileContentType) || isPropertiesFile(fileContentType, jsonFile.getOriginalFilename()))) {
            return new ResponseEntity<>("The form is not a JSON/Properties/Yaml file", HttpStatus.BAD_REQUEST);
        }

        if (jsonFile.isEmpty()) {
            return new ResponseEntity<>("File is empty", HttpStatus.BAD_REQUEST);
        }

        ClientForm clientForm = new ClientForm();

        byte[] bytes;
        try {
            bytes = jsonFile.getBytes();
        } catch (IOException e) {
            logger.error("Error occurred trying to read uploaded json file", e);
            return new ResponseEntity<>("Invalid file", HttpStatus.BAD_REQUEST);
        }

        String fileContentString = new String(bytes);

        String errorMessageForInvalidContent = checkValidJsonYamlPropertiesStructure(fileContentString, fileContentType);
        if (errorMessageForInvalidContent != null) {
            return new ResponseEntity<>("File content error:\n" + errorMessageForInvalidContent, HttpStatus.BAD_REQUEST);
        }

        if (isJsonFile(fileContentType) && !isJsonValidator) {
            HashSet<String> missingSubFormReferences = clientFormValidator.checkForMissingFormReferences(fileContentString);
            HashSet<String> missingRuleFileReferences = clientFormValidator.checkForMissingRuleReferences(fileContentString);
            HashSet<String> missingPropertyFileReferences = clientFormValidator.checkForMissingPropertyFileReferences(fileContentString);

            String errorMessage = null;
            if (!missingRuleFileReferences.isEmpty() || !missingSubFormReferences.isEmpty() || !missingPropertyFileReferences.isEmpty()) {
                errorMessage = "Form upload failed.";

                if (!missingSubFormReferences.isEmpty()) {
                    errorMessage += "Kindly make sure that the following sub-form(s) are uploaded before: " + String.join(", ", missingSubFormReferences);
                }

                if (!missingRuleFileReferences.isEmpty()) {
                    errorMessage += "Kindly make sure that the following rules file(s) are uploaded before: " + String.join(", ", missingRuleFileReferences);
                }

                if (!missingPropertyFileReferences.isEmpty()) {
                    errorMessage += "Kindly make sure that the following property file(s) are uploaded before: " + String.join(", ", missingPropertyFileReferences);
                }

                return new ResponseEntity<>(errorMessage, HttpStatus.BAD_REQUEST);
            }

            // Perform check to make sure that fields are not removed
            HashSet<String> missingFields = clientFormValidator.performWidgetValidation(objectMapper, formIdentifier, fileContentString);
            if (missingFields.size() > 0) {
                return new ResponseEntity<>("Kindly make sure that the following fields are still in the form : " + String.join(", ", missingFields)
                        + ". The fields cannot be removed as per the Administrator policy", HttpStatus.BAD_REQUEST);
            }
        }

        String identifier = formIdentifier;
        if (TextUtils.isBlank(formIdentifier)){
            identifier = Paths.get(jsonFile.getOriginalFilename()).getFileName().toString();
        }

        logger.info(fileContentString);
        clientForm.setJson(fileContentString);
        clientForm.setCreatedAt(new Date());

        ClientFormMetadata clientFormMetadata = new ClientFormMetadata();
        clientFormMetadata.setVersion(formVersion);
        clientFormMetadata.setIdentifier(identifier);
        clientFormMetadata.setLabel(formName);
        clientFormMetadata.setIsJsonValidator(isJsonValidator);
        clientFormMetadata.setCreatedAt(new Date());
        clientFormMetadata.setModule(module);

        if (!TextUtils.isBlank(relation)){
            clientFormMetadata.setRelation(relation);
        }

        ClientFormService.CompleteClientForm completeClientForm = clientFormService.addClientForm(clientForm, clientFormMetadata);

        if (completeClientForm == null) {
            return new ResponseEntity<>("Unknown error. Kindly confirm that the form does not already exist on the server", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @VisibleForTesting
    @Nullable
    protected String checkValidJsonYamlPropertiesStructure(@NonNull String fileContentString, @NonNull String contentType) {
        if (isJsonFile(contentType)) {
            try {
                new JSONObject(fileContentString);
                return null;
            } catch (JSONException ex) {
                logger.error("JSON File upload is invalid JSON", ex);
                return ex.getMessage();
            }
        } else if (isYamlContentType(contentType)) {
            try {
                (new MVELRuleFactory(new YamlRuleDefinitionReader())).createRule(new BufferedReader(new StringReader(fileContentString)));
            } catch (Exception ex) {
                logger.error("Rules file upload is invalid YAML rules file", ex);
                return ex.getMessage();
            }
        } else {
            // This is a properties file
            try {
                Properties properties = new Properties();
                properties.load(new StringReader(fileContentString));
            } catch (Exception e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }

        return null;
    }

    @VisibleForTesting
    protected boolean isClientFormContentTypeValid(@Nullable String fileContentType) {
        return fileContentType != null && (isJsonFile(fileContentType) ||
                isYamlContentType(fileContentType));
    }

    private boolean isJsonFile(@Nullable String fileContentType) {
        return fileContentType.equals(ContentType.APPLICATION_JSON.getMimeType());
    }

    private boolean isYamlContentType(@NonNull String fileContentType) {
        return fileContentType.equals(Constants.ContentType.APPLICATION_YAML) || fileContentType.equals(Constants.ContentType.TEXT_YAML);
    }

    @VisibleForTesting
    protected boolean isPropertiesFile(@NonNull String fileContentType, @NonNull String fileName) {
        return fileContentType.equals(ContentType.APPLICATION_OCTET_STREAM.getMimeType()) && fileName.endsWith(".properties");
    }

}
