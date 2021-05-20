package org.opensrp.web.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensrp.domain.postgres.ClientForm;
import org.opensrp.service.ClientFormService;
import org.opensrp.web.Constants;
import org.opensrp.web.bean.JsonWidgetValidatorDefinition;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.composer.ComposerException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ClientFormValidator {

	private static final Logger logger = LogManager.getLogger(ClientFormValidator.class.toString());

	private ArrayList<String> jsonPathForSubFormReferences = new ArrayList<>();

	private ArrayList<String> jsonPathForRuleReferences = new ArrayList<>();

	private ArrayList<String> jsonPathForPropertyFileReferences = new ArrayList<>();

	private ClientFormService clientFormService;

	private static final String PROPERTIES_FILE_NAME = "properties_file_name";

    private ClientFormValidator() {
        initialiseSubFormJsonPathReferences();
        initialiseRuleJsonPathReferences();
        initialisePropertiesFileJsonPathReferences();
    }

    public ClientFormValidator(@NonNull ClientFormService clientFormService) {
        this.clientFormService = clientFormService;

		initialiseSubFormJsonPathReferences();
		initialiseRuleJsonPathReferences();
		initialisePropertiesFileJsonPathReferences();
	}

	private void initialiseSubFormJsonPathReferences() {
		jsonPathForSubFormReferences.add("$.*.fields[*][?(@.type == 'native_radio')].options[*].content_form");
		jsonPathForSubFormReferences.add("$.*.fields[*][?(@.type == 'expansion_panel')].content_form");
	}

	private void initialiseRuleJsonPathReferences() {
		jsonPathForRuleReferences.add("$.*.fields[*].calculation.rules-engine.ex-rules.rules-file");
		jsonPathForRuleReferences.add("$.*.fields[*].relevance.rules-engine.ex-rules.rules-file");
		jsonPathForRuleReferences.add("$.*.fields[*].constraints.rules-engine.ex-rules.rules-file");
	}

	private void initialisePropertiesFileJsonPathReferences() {
		jsonPathForPropertyFileReferences.add("$.properties_file_name");
	}

	@NonNull
	public HashSet<String> checkForMissingFormReferences(@NonNull String jsonForm) {
		HashSet<String> subFormReferences = new HashSet<>();
		HashSet<String> missingSubFormReferences = new HashSet<>();

		for (String jsonPath : jsonPathForSubFormReferences) {
			List<String> references = JsonPath.read(jsonForm, jsonPath);
			subFormReferences.addAll(references);
		}

		// Check if the references exist in the DB
		for (String subFormReference : subFormReferences) {
			// If the form does not exist, Add a .json extension & check again
			if (!clientFormService.isClientFormExists(subFormReference) && !clientFormService
					.isClientFormExists(subFormReference + ".json")) {
				missingSubFormReferences.add(subFormReference);
			}
		}

		return missingSubFormReferences;
	}

	@NonNull
	public HashSet<String> checkForMissingRuleReferences(@NonNull String jsonForm) {
		HashSet<String> ruleFileReferences = new HashSet<>();
		HashSet<String> missingRuleFileReferences = new HashSet<>();

		for (String jsonPath : jsonPathForRuleReferences) {
			List<String> references = JsonPath.read(jsonForm, jsonPath);
			ruleFileReferences.addAll(references);
		}

		// Check if the references exist in the DB
		for (String ruleFileReference : ruleFileReferences) {
			if (!clientFormService.isClientFormExists(ruleFileReference)) {
				missingRuleFileReferences.add(ruleFileReference);
			}
		}

		return missingRuleFileReferences;
	}

	@NonNull
	public HashSet<String> checkForMissingPropertyFileReferences(@NonNull String jsonForm) {
		Configuration conf = Configuration.defaultConfiguration()
				.addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);

		HashSet<String> propertyFileReferences = new HashSet<>();
		HashSet<String> missingPropertyFileReferences = new HashSet<>();

		for (String jsonPath : jsonPathForPropertyFileReferences) {
			String reference = (JsonPath.using(conf)).parse(jsonForm).read(jsonPath);

			if (reference != null) {
				propertyFileReferences.add(reference);
			}
		}

		// Check if the references exist in the DB
		for (String propertyFileReference : propertyFileReferences) {
			if (!clientFormService.isClientFormExists(propertyFileReference) && !clientFormService
					.isClientFormExists(propertyFileReference + ".properties")) {
				missingPropertyFileReferences.add(propertyFileReference);
			}
		}

		return missingPropertyFileReferences;
	}

	public HashSet<String> checkForMissingYamlPropertyFileReferences(@NonNull String fileContent) {
		HashSet<String> propertyFileReferences = new HashSet<>();
		HashSet<String> missingPropertyFileReferences = new HashSet<>();
		try {
			Map<Object, Object> document = new Yaml().load(fileContent);
			if (document.containsKey(PROPERTIES_FILE_NAME)) {
				propertyFileReferences.add((String) document.get(PROPERTIES_FILE_NAME));
			}
			// Check if the references exist in the DB
			for (String propertyFileReference : propertyFileReferences) {
				if (!clientFormService.isClientFormExists(propertyFileReference) && !clientFormService
						.isClientFormExists(propertyFileReference + ".properties")) {
					missingPropertyFileReferences.add(propertyFileReference);
				}
			}
		}
		catch (ComposerException exception) {
			logger.error("Validator parsing a YAML file that doesn't conform in structure", exception);
			return missingPropertyFileReferences;
		}
		return missingPropertyFileReferences;
	}

	@NonNull
	public HashSet<String> performWidgetValidation(@NonNull ObjectMapper objectMapper, @NonNull String formIdentifier,
			@NonNull String clientFormContent) {
		ClientForm formValidator = clientFormService.getMostRecentFormValidator(formIdentifier);
		HashSet<String> fieldsMap = new HashSet<>();
		try {
			if (formValidator != null && formValidator.getJson() != null && !((String) formValidator.getJson()).isEmpty()) {
				logger.info((String) formValidator.getJson());
				objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT);
				objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
				objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);

				String formValidatorString = (String) formValidator.getJson();
				formValidatorString = formValidatorString.substring(1, formValidatorString.length() - 1);
				formValidatorString = StringEscapeUtils.unescapeJava(formValidatorString);

				logger.info(formValidatorString);
				JsonWidgetValidatorDefinition jsonWidgetValidatorDefinition =
						objectMapper.readValue(formValidatorString, JsonWidgetValidatorDefinition.class);
				JsonWidgetValidatorDefinition.WidgetCannotRemove widgetCannotRemove = jsonWidgetValidatorDefinition
						.getCannotRemove();

				if (widgetCannotRemove != null && widgetCannotRemove.getFields() != null
						&& widgetCannotRemove.getFields().size() > 0) {
					JsonNode jsonNode = objectMapper.readTree(clientFormContent);
					fieldsMap.addAll(widgetCannotRemove.getFields());

					Iterator<Map.Entry<String, JsonNode>> iterator = jsonNode.fields();
					while (iterator.hasNext()) {
						Map.Entry<String, JsonNode> jsonFormField = iterator.next();
						if (jsonFormField.getKey().startsWith(Constants.JsonForm.Key.STEP)) {
							JsonNode step = jsonFormField.getValue();
							if (step.has(Constants.JsonForm.Key.FIELDS)) {
								JsonNode fields = step.get(Constants.JsonForm.Key.FIELDS);
								if (fields instanceof ArrayNode) {
									ArrayNode fieldsArray = (ArrayNode) fields;
									for (int i = 0; i < fieldsArray.size(); i++) {
										JsonNode jsonNodeField = fieldsArray.get(i);
										String key = jsonNodeField.get(Constants.JsonForm.Key.KEY).asText();

										if (fieldsMap.remove(key) && fieldsMap.size() == 0) {
											return fieldsMap;
										}
									}
								}
							}
						}
					}
				}
			}
		}
		catch (IOException exception) {
			logger.info("", exception);
		}
		return fieldsMap;
	}

	public ArrayList<String> getJsonPathForSubFormReferences() {
		return jsonPathForSubFormReferences;
	}

	public void setJsonPathForSubFormReferences(ArrayList<String> jsonPathForSubFormReferences) {
		this.jsonPathForSubFormReferences = jsonPathForSubFormReferences;
	}

	public ArrayList<String> getJsonPathForRuleReferences() {
		return jsonPathForRuleReferences;
	}

	public void setJsonPathForRuleReferences(ArrayList<String> jsonPathForRuleReferences) {
		this.jsonPathForRuleReferences = jsonPathForRuleReferences;
	}

	public ArrayList<String> getJsonPathForPropertyFileReferences() {
		return jsonPathForPropertyFileReferences;
	}

	public void setJsonPathForPropertyFileReferences(ArrayList<String> jsonPathForPropertyFileReferences) {
		this.jsonPathForPropertyFileReferences = jsonPathForPropertyFileReferences;
	}
}
