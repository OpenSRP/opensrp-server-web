package org.opensrp.web.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.opensrp.common.AllConstants;
import org.opensrp.domain.PlanDefinition;
import org.opensrp.service.PlanService;
import org.opensrp.util.DateTypeConverter;
import org.opensrp.util.TaskDateTimeTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.opensrp.web.rest.RestUtils.getStringFilter;

/**
 * @author Vincent Karuri
 */

@Controller
@RequestMapping(value = "/rest/plans")
public class PlanResource {

	private static Logger logger = LoggerFactory.getLogger(PlanResource.class.toString());

	public static Gson gson = new GsonBuilder().registerTypeAdapter(DateTime.class, new TaskDateTimeTypeConverter())
			.registerTypeAdapter(LocalDate.class, new DateTypeConverter()).create();

	private PlanService planService;

	public static final String OPERATIONAL_AREA_ID = "operational_area_id";

	public static final String IDENTIFIERS = "identifiers";

	public static final String FIELDS = "fields";

	@Autowired
	public void setPlanService(PlanService planService) {
		this.planService = planService;
	}

	@RequestMapping(value = "/{identifier}", method = RequestMethod.GET, produces = {
			MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<String> getPlanByUniqueId(@PathVariable("identifier") String identifier,
			@RequestParam(value = FIELDS, required = false) List<String> fields) {
		if (identifier == null) {
			return new ResponseEntity<>("Plan Id is required", HttpStatus.BAD_REQUEST);
		}

		try {
			return new ResponseEntity<>(gson.toJson(
					planService.getPlansByIdsReturnOptionalFields(Collections.singletonList(identifier), fields)),
					RestUtils.getJSONUTF8Headers(), HttpStatus.OK);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(method = RequestMethod.GET, produces = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<String> getPlans() {
		try {
			return new ResponseEntity<>(gson.toJson(
					planService.getAllPlans()),
					RestUtils.getJSONUTF8Headers(), HttpStatus.OK);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(method = RequestMethod.POST, consumes = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.TEXT_PLAIN_VALUE })
	public ResponseEntity<HttpStatus> create(@RequestBody String entity) {
		try {
			PlanDefinition plan = gson.fromJson(entity, PlanDefinition.class);
			planService.addPlan(plan);
			return new ResponseEntity<>(HttpStatus.CREATED);
		} catch (JsonSyntaxException e) {
			logger.error("The request doesn't contain a valid plan representation" + entity);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(method = RequestMethod.PUT, consumes = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.TEXT_PLAIN_VALUE })
	public ResponseEntity<HttpStatus> update(@RequestBody String entity) {
		try {
			PlanDefinition plan = gson.fromJson(entity, PlanDefinition.class);
			planService.updatePlan(plan);
			return new ResponseEntity<>(HttpStatus.CREATED);
		} catch (JsonSyntaxException e) {
			logger.error("The request doesn't contain a valid plan representation" + entity);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/sync", method = RequestMethod.GET, produces = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<String> syncByServerVersionAndOperationalArea(HttpServletRequest request,
			@RequestParam(value = OPERATIONAL_AREA_ID) List<String> operationalAreaIds) {
		String serverVersion = getStringFilter(AllConstants.BaseEntity.SERVER_VERSIOIN, request);
		long currentServerVersion = 0;
		try {
			currentServerVersion = Long.parseLong(serverVersion);
		} catch (NumberFormatException e) {
			logger.error("server version not a number");
		}
		if (operationalAreaIds.isEmpty()) {
			return new ResponseEntity<>("Juridiction Ids required", HttpStatus.BAD_REQUEST);
		}

		try {
			return new ResponseEntity<>(gson.toJson(
					planService.getPlansByServerVersionAndOperationalArea(currentServerVersion, operationalAreaIds)),
					RestUtils.getJSONUTF8Headers(), HttpStatus.OK);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * This method provides an API endpoint that searches for plans using a list of provided
	 * plan identifiers and returns a subset of fields determined by the list of provided fields
	 * If no plan identifier(s) are provided the method returns all available plans
	 * If no fields are provided the method returns all the available fields
	 * @param identifiers list of plan identifiers
	 * @param fields list of fields to return
	 * @return plan definitions whose identifiers match the provided params
	 */
	@RequestMapping(value = "/findByIdsWithOptionalFields", method = RequestMethod.GET, produces = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<String> findByIdentifiersReturnOptionalFields(HttpServletRequest request,
			@RequestParam(value = IDENTIFIERS) List<String> identifiers,
			@RequestParam(value = FIELDS, required = false) List<String> fields){
		try {

			if (fields != null && !fields.isEmpty()) {
				for (String fieldName : fields) {
					if (!doesObjectContainField(new PlanDefinition(),fieldName)) {
						return new ResponseEntity<>(fieldName + " field is invalid", HttpStatus.BAD_REQUEST);
					}
				}
			}
			return new ResponseEntity<>(gson.toJson(
					planService.getPlansByIdsReturnOptionalFields(identifiers, fields)),
					RestUtils.getJSONUTF8Headers(), HttpStatus.OK);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public boolean doesObjectContainField(Object object, String fieldName) {
		Class<?> objectClass = object.getClass();
		for (Field field : objectClass.getDeclaredFields()) {
			SerializedName sName = field.getAnnotation(SerializedName.class);
			if (sName != null && sName.value().equals(fieldName))
				return true;
			else if (sName == null && field.getName().equals(fieldName)) {
				return true;
			}
		}
		return false;
	}
}
