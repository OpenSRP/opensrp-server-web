package org.opensrp.web.rest;

import static org.opensrp.common.AllConstants.OpenSRPEvent.Form.SERVER_VERSION;
import static org.opensrp.web.Constants.DEFAULT_GET_ALL_IDS_LIMIT;
import static org.opensrp.web.Constants.DEFAULT_LIMIT;
import static org.opensrp.web.Constants.LIMIT;
import static org.opensrp.web.Constants.RETURN_COUNT;
import static org.opensrp.web.Constants.TOTAL_RECORDS;
import static org.opensrp.web.rest.RestUtils.getStringFilter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.opensrp.common.AllConstants.BaseEntity;
import org.opensrp.web.utils.Utils;
import org.smartregister.domain.Task;
import org.opensrp.domain.TaskUpdate;
import org.opensrp.service.TaskService;
import org.smartregister.utils.TaskDateTimeTypeConverter;
import org.opensrp.web.bean.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

@Controller
@RequestMapping(value = "/rest/task")
public class TaskResource {
	
	private static Logger logger = LoggerFactory.getLogger(TaskResource.class.toString());
	
	public static Gson gson = new GsonBuilder().registerTypeAdapter(DateTime.class, new TaskDateTimeTypeConverter())
	        .create();
	
	public static final String PLAN = "plan";
	
	public static final String GROUP = "group";

	public static final String OWNER= "owner";

	private TaskService taskService;
	
	@Autowired
	public void setTaskService(TaskService taskService) {
		this.taskService = taskService;
	}
	
	@RequestMapping(value = "/{identifier}", method = RequestMethod.GET, produces = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<String> getByUniqueId(@PathVariable("identifier") String identifier) {
		return new ResponseEntity<>(gson.toJson(taskService.getTask(identifier)), RestUtils.getJSONUTF8Headers(),
				HttpStatus.OK);
	}
	
	@RequestMapping(value = "/sync", method = RequestMethod.POST, consumes = {
	        MediaType.APPLICATION_JSON_VALUE }, produces = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<String> getTasksByTaskAndGroup(@RequestBody TaskSyncRequestWrapper taskSyncRequestWrapper) {
		String plan = StringUtils.join(taskSyncRequestWrapper.getPlan(), ",");
		String group = StringUtils.join(taskSyncRequestWrapper.getGroup(), ",");
		String owner = taskSyncRequestWrapper.getOwner();
		long serverVersion = taskSyncRequestWrapper.getServerVersion();
		boolean returnCount = taskSyncRequestWrapper.isReturnCount();
		
		long currentServerVersion = 0;
		try {
			currentServerVersion = serverVersion;
		}
		catch (NumberFormatException e) {
			logger.error("server version not a number");
		}
		return getTaskSyncResponse(plan, group, owner, currentServerVersion, returnCount);
	}
	
	// here for backward compatibility
	@RequestMapping(value = "/sync", method = RequestMethod.GET, produces = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<String> getTasksByTaskAndGroupTwo(HttpServletRequest request) {
		String plan = getStringFilter(PLAN, request);
		String group = getStringFilter(GROUP, request);
		String serverVersion = getStringFilter(BaseEntity.SERVER_VERSIOIN, request);
		String owner = getStringFilter(OWNER, request);
		boolean returnCount = Boolean.getBoolean(getStringFilter(RETURN_COUNT, request));

		long currentServerVersion = 0;
		try {
			currentServerVersion = Long.parseLong(serverVersion);
		}
		catch (NumberFormatException e) {
			logger.error("server version not a number");
		}
		return getTaskSyncResponse(plan, group, owner, currentServerVersion, returnCount);
	}

	private ResponseEntity<String> getTaskSyncResponse(String plan, String group, String owner, long currentServerVersion, boolean returnCount) {
		if (StringUtils.isBlank(plan)) {
			logger.error("Plan Identifier is missing");
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (!StringUtils.isBlank(group)) {
			String tasks = gson.toJson(taskService.getTasksByTaskAndGroup(plan, group, currentServerVersion));
			HttpHeaders headers = RestUtils.getJSONUTF8Headers();
			if (returnCount){
				Long taskCount = taskService.countTasksByPlanAndGroup(plan, group, currentServerVersion);
				headers.add(TOTAL_RECORDS, String.valueOf(taskCount));
			}

			return new ResponseEntity<>(tasks, headers, HttpStatus.OK);
		} else if (!StringUtils.isBlank(owner)) {
			String tasks = gson.toJson(taskService.getTasksByPlanAndOwner(plan, owner, currentServerVersion));
			HttpHeaders headers = RestUtils.getJSONUTF8Headers();
			if (returnCount){
				Long taskCount = taskService.countTasksByPlanAndOwner(plan, owner, currentServerVersion);
				headers.add(TOTAL_RECORDS, String.valueOf(taskCount));
			}

			return new ResponseEntity<>(tasks, headers, HttpStatus.OK);
		} else {
			logger.error("Either owner or group identifier field is missing");
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}
	
	@RequestMapping(method = RequestMethod.POST, consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE })
	public ResponseEntity<HttpStatus> create(@RequestBody String entity) {
		try {
			Task task = gson.fromJson(entity, Task.class);
			taskService.addTask(task);
			return new ResponseEntity<>(HttpStatus.CREATED);
		}
		catch (JsonSyntaxException e) {
			logger.error("The request doesnt contain a valid task representation" + entity);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

	}
	
	@RequestMapping(method = RequestMethod.PUT, consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE })
	public ResponseEntity<HttpStatus> update(@RequestBody String entity) {
		try {
			Task task = gson.fromJson(entity, Task.class);
			taskService.updateTask(task);
			return new ResponseEntity<>(HttpStatus.CREATED);
		}
		catch (JsonSyntaxException e) {
			logger.error("The request doesnt contain a valid task representation" + entity);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

	}
	
	@RequestMapping(value = "/add", method = RequestMethod.POST, consumes = { MediaType.APPLICATION_JSON_VALUE,
	        MediaType.TEXT_PLAIN_VALUE })
	public ResponseEntity<String> batchSave(@RequestBody String entity) {
		try {
			Type listType = new TypeToken<List<Task>>() {}.getType();
			List<Task> tasks = gson.fromJson(entity, listType);
			Set<String> tasksWithErrors = taskService.saveTasks(tasks);
			if (tasksWithErrors.isEmpty())
				return new ResponseEntity<>("All Tasks  processed", HttpStatus.CREATED);
			else
				return new ResponseEntity<>("Tasks with identifiers not processed: " + String.join(",", tasksWithErrors),
				        HttpStatus.CREATED);
		}
		catch (JsonSyntaxException e) {
			logger.error("The request doesnt contain a valid task representation" + entity);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

	}
	
	@RequestMapping(value = "/update_status", method = RequestMethod.POST, consumes = { MediaType.APPLICATION_JSON_VALUE,
	        MediaType.TEXT_PLAIN_VALUE })
	public ResponseEntity<String> updateStatus(@RequestBody String entity) {
		try {
			Type listType = new TypeToken<List<TaskUpdate>>() {}.getType();
			List<TaskUpdate> taskUpdates = gson.fromJson(entity, listType);
			List<String> updateTasks = taskService.updateTaskStatus(taskUpdates);
			if (updateTasks.size() > 0) {
				JSONObject json = new JSONObject();
				json.put("task_ids", updateTasks);
				return new ResponseEntity<>(json.toString(), HttpStatus.CREATED);
			} else {
				return new ResponseEntity<>("Tasks not Updated: ", HttpStatus.CREATED);
			}
		}
		catch (JsonSyntaxException e) {
			logger.error("The request doesnt contain a valid task update representation" + entity);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

	}
	
	/**
	 * This methods provides an API endpoint that searches for all task Ids
	 * ordered by server version ascending
	 *
	 * @param serverVersion serverVersion using to filter by
	 * @return A list of task Ids
	 */
	@RequestMapping(value = "/findIds", method = RequestMethod.GET, produces = {
			MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<Identifier> findIds(
			@RequestParam(value = SERVER_VERSION)  long serverVersion,
			@RequestParam(value = "fromDate", required = false) String fromDate,
			@RequestParam(value = "toDate", required = false) String toDate) {

		Pair<List<String>, Long> taskIdsPair = taskService.findAllTaskIds(serverVersion, DEFAULT_GET_ALL_IDS_LIMIT,
				Utils.getDateTimeFromString(fromDate), Utils.getDateTimeFromString(toDate));
		Identifier identifiers = new Identifier();
		identifiers.setIdentifiers(taskIdsPair.getLeft());
		identifiers.setLastServerVersion(taskIdsPair.getRight());
		return new ResponseEntity<>(identifiers, HttpStatus.OK);
	}
	
	/**
	 * Fetch tasks ordered by serverVersion ascending
	 *
	 * @param serverVersion serverVersion using to filter by
	 * @param limit upper limit on number of tasks to fetch
	 * @return A list of tasks
	 */
	@RequestMapping(value = "/getAll", method = RequestMethod.GET, produces = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<String> getAll(@RequestParam(value = SERVER_VERSION) long serverVersion,
	        @RequestParam(value = LIMIT, required = false) Integer limit) {

		Integer pageLimit = limit == null ? DEFAULT_LIMIT : limit;
		return new ResponseEntity<>(gson.toJson(taskService.getAllTasks(serverVersion, pageLimit)),
				RestUtils.getJSONUTF8Headers(), HttpStatus.OK);
		
	}
	
	static class TaskSyncRequestWrapper {
		
		@JsonProperty
		private List<String> plan = new ArrayList<>();
		
		@JsonProperty
		private List<String> group = new ArrayList<>();
		
		@JsonProperty
		private long serverVersion;

		@JsonProperty
		private String owner;
		
		@JsonProperty(RETURN_COUNT)
		private boolean returnCount;

		public List<String> getPlan() {
			return plan;
		}
		
		public List<String> getGroup() {
			return group;
		}
		
		public long getServerVersion() {
			return serverVersion;
		}

		public String getOwner() {
			return owner;
		}

		
		public boolean isReturnCount() {
			return returnCount;
		}
		
	}
	
}
