package org.opensrp.web.rest.rapid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import com.mysql.jdbc.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opensrp.common.AllConstants;
import org.opensrp.domain.Client;
import org.opensrp.domain.Event;
import org.opensrp.search.EventSearchBean;
import org.opensrp.service.ClientService;
import org.opensrp.service.EventService;
import org.opensrp.util.DateTimeTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opensrp.common.AllConstants.BaseEntity.BASE_ENTITY_ID;
import static org.opensrp.common.AllConstants.CLIENTS_FETCH_BATCH_SIZE;
import static org.opensrp.web.rest.RestUtils.getStringFilter;

@Controller
@RequestMapping(value = "/rest/vaccines/")
public class GetAllVaccinesResource {

	public static final String VACCINE_SEARCH_INDENTIFIER = "identifier";

	private static Logger logger = LoggerFactory.getLogger(GetAllVaccinesResource.class.toString());

	private ClientService clientService;

	private EventService eventService;

	private Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").registerTypeAdapter(DateTime.class, new DateTimeTypeConverter()).create();

	@Autowired
	public GetAllVaccinesResource(ClientService clientService, EventService eventService) {
		this.clientService = clientService;
		this.eventService = eventService;
	}

	/**
	 * Validate that the client and event ids reference actual documents
	 *
	 * @param data
	 * @return
	 */

	@RequestMapping(value = "/sync", method = RequestMethod.GET)
	@ResponseBody
	protected ResponseEntity<String> sync(HttpServletRequest request) {
		Map<String, Object> response = new HashMap<String, Object>();
		Map<String, Object> eventsMap = new HashMap<>();
		try {
			String identifier = getStringFilter(VACCINE_SEARCH_INDENTIFIER, request);
			Long lastSyncedServerVersion = 0l;
			String baseEntityId = null;
			if (!StringUtils.isEmptyOrWhitespaceOnly(identifier)) {
				Client c = clientService.find(identifier);
				if (c == null) {
					return new ResponseEntity<>(gson.toJson(response), HttpStatus.OK);
				}
				baseEntityId = c.getBaseEntityId();
			}
			List<Event> events = new ArrayList<>();
			List<String> clientIds = new ArrayList<String>();
			List<Client> clients = new ArrayList<Client>();
			if (baseEntityId != null) {
				EventSearchBean eventSearchBean = new EventSearchBean();
				eventSearchBean.setBaseEntityId(baseEntityId);
				eventSearchBean.setServerVersion(lastSyncedServerVersion);
				events = eventService.findEvents(eventSearchBean, AllConstants.BaseEntity.SERVER_VERSIOIN, "asc", 25);
				if (!events.isEmpty()) {
					for (Event event : events) {
						if (event.getBaseEntityId() != null && !event.getBaseEntityId().isEmpty() && !clientIds.contains(event.getBaseEntityId())) {
							clientIds.add(event.getBaseEntityId());
						}
					}
					for (int i = 0; i < clientIds.size(); i = i + CLIENTS_FETCH_BATCH_SIZE) {
						int end = i + CLIENTS_FETCH_BATCH_SIZE < clientIds.size() ? i + CLIENTS_FETCH_BATCH_SIZE : clientIds.size();
						clients.addAll(clientService.findByFieldValue(BASE_ENTITY_ID, clientIds.subList(i, end)));
					}
				}
			}
			JsonArray eventsArray = (JsonArray) gson.toJsonTree(events, new TypeToken<List<Event>>() {

			}.getType());
			eventsMap.put("events", eventsArray);
			List<Vaccines> vaccinesList = vaccines(eventsMap);
			JsonArray vaccinesArray = (JsonArray) gson.toJsonTree(vaccinesList, new TypeToken<List<Vaccines>>() {

			}.getType());
			if (vaccinesArray.size() > 0)
				response.put("vaccines", vaccinesArray);
			response.put("clientUUID", baseEntityId);
			response.put("ZEIR_ID", identifier);
			return new ResponseEntity<>(new Gson().toJson(response), HttpStatus.OK);
		}
		catch (Exception e) {
			response.put("msg", "Error occurred");
			logger.error("", e);
			return new ResponseEntity<>(gson.toJson(response), HttpStatus.OK);
		}
	}

	public static List<Vaccines> vaccines(Map<String, Object> events) {
		List<Vaccines> vaccines = new ArrayList<>();
		Vaccines vaccineObjects;
		try {
			String clientEvents = events.get("events").toString();
			JSONArray jsonArray = new JSONArray(clientEvents);
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject vaccine = jsonArray.getJSONObject(i);
				if (vaccine.getString("eventType").equals("Vaccination")) {
					vaccineObjects = new Vaccines();
					JSONArray eventObs = vaccine.getJSONArray("obs");
					for (int j = 0; j < eventObs.length(); j++) {
						JSONObject obsObject = eventObs.getJSONObject(j);
						if (obsObject.optString("fieldDataType").equals("date")) {
							String vaccine_name = obsObject.getString("formSubmissionField").toUpperCase();
							if (vaccine_name.contains("_")) {
								vaccineObjects.setVaccine_name(vaccine_name.substring(0, vaccine_name.indexOf("_")));
							} else {
								vaccineObjects.setVaccine_name(vaccine_name);
							}
							vaccineObjects.setDate_administered(obsObject.getJSONArray("values").get(0).toString());
						} else if (obsObject.optString("fieldDataType").equals("calculate")) {
							vaccineObjects.setSequence(obsObject.getJSONArray("values").get(0).toString());
						}
					}
					vaccines.add(vaccineObjects);
				}
			}
		}
		catch (JSONException e) {
			logger.error("", e);
		}
		return vaccines;
	}

	static class Vaccines {

		public String vaccine_name;

		public String date_administered;

		public String sequence;

		public void setVaccine_name(String vaccine_name) {
			this.vaccine_name = vaccine_name;
		}

		public void setDate_administered(String date_administered) {
			this.date_administered = date_administered;
		}

		public void setSequence(String sequence) {
			this.sequence = sequence;
		}

	}

}
