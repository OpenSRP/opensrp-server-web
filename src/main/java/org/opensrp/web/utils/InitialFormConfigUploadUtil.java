package org.opensrp.web.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import org.json.JSONObject;
import org.opensrp.service.ClientFormService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/* -------------------------------------------------------------------------------------------------
 DO NOT DEPEND ON THIS FILE FOR RUNTIME TASKS. THIS FILE MAY BE EXCLUDED FROM THE FINAL WAR FILE
------------------------------------------------------------------------------------------------- */

public class InitialFormConfigUploadUtil {

	private ArrayList<String> jsonPathForSubFormReferences = new ArrayList<>();
	private ArrayList<String> jsonPathForRuleReferences = new ArrayList<>();
	private ArrayList<String> jsonPathForPropertyFileReferences = new ArrayList<>();


	public static void main(String[] mainArgs) throws IOException {

		InitialFormConfigUploadUtil initialFormConfigUploadUtil = new InitialFormConfigUploadUtil();

		Properties properties = new Properties();
		InputStream inputStream = initialFormConfigUploadUtil.getClass().getClassLoader().getResourceAsStream("spring/doc_config_script_configs.properties");
		properties.load(inputStream);


		// Path to client project assets folder with json.form
		String assetsFolderFullPath = properties.getProperty("assetsFolderFullPath");
		// Initial form version
		String formVersion = properties.getProperty("formVersion");

		// Base URL for the server to which the forms will be uploaded
		String baseUrl = properties.getProperty("baseUrl");

		// Authentication details for the server
		String username = properties.getProperty("username");
		String password = properties.getProperty("password");

		String appId = properties.getProperty("appId");
		String appVersion = properties.getProperty("appVersion");
		String jsonFormFolderFromAssets = properties.getProperty("jsonFormFolderFromAssets");
		String subFormJsonFolderFromAssets = properties.getProperty("subFormJsonFolderFromAssets");

		initialFormConfigUploadUtil.performUpload(assetsFolderFullPath, formVersion, baseUrl, username, password, appId, appVersion, jsonFormFolderFromAssets, subFormJsonFolderFromAssets);
	}

	public InitialFormConfigUploadUtil() {
		ClientFormValidator clientFormValidator = new ClientFormValidator(new ClientFormService());
		jsonPathForPropertyFileReferences = clientFormValidator.getJsonPathForPropertyFileReferences();
		jsonPathForRuleReferences = clientFormValidator.getJsonPathForRuleReferences();
		jsonPathForSubFormReferences = clientFormValidator.getJsonPathForSubFormReferences();
	}

	private Call generateHttpCall(String clientFormUrl, String basicAuthValue, RequestBody requestBody) {
		Request request = new Request.Builder()
				.header("Authorization", basicAuthValue)
				.url(clientFormUrl)
				.post(requestBody)
				.build();
		OkHttpClient httpClient = new OkHttpClient();
		httpClient.setConnectTimeout(1, TimeUnit.SECONDS);
		httpClient.setReadTimeout(1, TimeUnit.SECONDS);

		return httpClient
				.newCall(request);
	}

	private RequestBody generateRequestBody(String formVersion, String fileName, String fileContentType, File ymlFile,
			String formRelation, String title) throws IOException {
		MultipartBuilder multipartBuilder = new MultipartBuilder()
				.type(MultipartBuilder.FORM)
				.addFormDataPart("form_version", formVersion)
				.addFormDataPart("form_identifier", fileName)
				.addFormDataPart("form_name", title)
				.addFormDataPart("form", fileName, RequestBody
						.create(MediaType.parse(fileContentType), Files.readAllBytes(ymlFile.toPath())));

		//.addPart(RequestBody.create(MediaType.parse("application/x-yaml"), Files.readAllBytes(ymlFile.toPath())));

		if (formRelation != null) {
			multipartBuilder.addFormDataPart("form_relation", formRelation);
		}

		return multipartBuilder.build();
	}


	public void performUpload(String assetsFolderFullPath, String formVersion, String baseUrl, String username, String password
			, String appId, String appVersion, String jsonFolderFolderFromAssets, String jsonSubFormFolderFromAssets) throws IOException {

		String encoding = new String(java.util.Base64.getEncoder().encode((username + ":" + password).getBytes()));
		String clientFormUrl = baseUrl + "/opensrp/rest/clientForm";
		String manifestUrl = baseUrl + "/opensrp/rest/manifest";
		String basicAuthValue = "Basic " + encoding;

		File file = new File(assetsFolderFullPath);
		HashMap<String, String> ruleFileRelations = new HashMap<>();
		HashMap<String, String> propertyFileRelations = new HashMap<>();
		HashMap<String, String> subFormFileRelations = new HashMap<>();
		HashSet<String> jsonFormFiles = new HashSet<>();
		HashMap<String, String> formLabels = new HashMap<>();

		// JSONPath Configuration
		Configuration conf = Configuration.defaultConfiguration()
				.addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);

		if (file.exists() && file.isDirectory()) {
			File[] files = file.listFiles();

			for (File fileD: files) {
				String fileName = fileD.getName();
				if (fileD.isDirectory()) {
					System.out.println(fileD.getName() + " @ " + fileD.getAbsolutePath());
					if (fileName.equals("rule")) {
						File[] ruleFiles = fileD.listFiles();
						if (ruleFiles != null) {
							for (File ruleFile : ruleFiles) {
								String ruleFileName = ruleFile.getName();
								if (ruleFileName.endsWith(".yml")) {
									System.out.println(
											String.format("RULE FILE %s @ %s", ruleFileName, ruleFile.getAbsolutePath()));
									if (!ruleFileRelations.containsKey(ruleFileName)) {
										ruleFileRelations.put(fileName, null);
									}
								}
							}
						}
					} else if (fileName.equals(jsonFolderFolderFromAssets)) {
						File[] formFiles = fileD.listFiles();
						if (formFiles != null) {
							for (File formFile : formFiles) {
								String formFileName = formFile.getName();
								if (formFileName.endsWith(".json")) {
									System.out.println(
											String.format("JSON FORM FILE %s @ %s", formFileName, formFile.getAbsolutePath()));
									jsonFormFiles.add(formFileName);

									String fileStringContent = Files.readString(formFile.toPath());

									String title = (JsonPath.using(conf)).parse(fileStringContent).read("$.step1.title");;

									title = title == null ? "Sample Form" : title;
									formLabels.put(formFileName, title);


									// TODO: FETCH DEPENDENT RULE & FORM FILES
									for (String jsonPath: jsonPathForSubFormReferences) {
										List<String> references = (JsonPath.using(conf)).parse(
												fileStringContent).read(jsonPath);

										if (references != null && references.size() > 0) {
											for (String reference : references) {
												if (reference != null) {
													subFormFileRelations.put(reference + ".json", formFileName);
												}
											}
										}
									}


									for (String jsonPath: jsonPathForRuleReferences) {
										List<String> references = (JsonPath.using(conf)).parse(
												fileStringContent).read(jsonPath);

										if (references != null) {
											for (String reference : references) {
												if (reference != null) {
													ruleFileRelations.put(reference, formFileName);
												}
											}
										}
									}

                                    for (String jsonPath: jsonPathForPropertyFileReferences) {
                                        String reference = (JsonPath.using(conf)).parse(Files.readString(formFile.toPath())).read(jsonPath);

                                        if (reference != null) {
	                                        propertyFileRelations.put(reference + ".properties", formFileName);
                                        }
                                    }


								} else if (formFile.isDirectory() && formFile.getAbsolutePath().contains(jsonSubFormFolderFromAssets)) {
									File[] subFormFiles = formFile.listFiles();
									if (subFormFiles != null) {
										for (File subFormFile : subFormFiles) {
											String subFormFileName = subFormFile.getName();
											if (subFormFileName.endsWith(".json")) {
												System.out.println(
														String.format("JSON SUB-FORM FILE %s @ %s", subFormFileName, subFormFile.getAbsolutePath()));
												if (!subFormFileRelations.containsKey(subFormFileName)) {
													subFormFileRelations.put(subFormFileName, null);
												}

												for (String jsonPath: jsonPathForPropertyFileReferences) {
													String reference = (JsonPath.using(conf)).parse(Files.readString(subFormFile.toPath())).read(jsonPath);

													if (reference != null) {
														propertyFileRelations.put(reference + ".properties", subFormFileName);
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}

			// Get the properties files
			// And get their relations

			File resourcesFolder = new File(assetsFolderFullPath.replace("/assets", "/resources"));
			if (resourcesFolder.exists() && resourcesFolder.isDirectory()) {
				File[] resourcesFolderFiles = resourcesFolder.listFiles();
				for (File propertyFile : resourcesFolderFiles) {
					String propertyFileName = propertyFile.getName();
					if (!propertyFile.isDirectory()) {
						if (propertyFileName.endsWith(".properties") && !propertyFileName.equals("robolectric.properties")) {
							System.out.println(String.format("PROPERTY FILE %s @ %s", propertyFileName, propertyFile.getAbsolutePath()));

							if (!propertyFileRelations.containsKey(propertyFileName)) {
								// If this is a language file the
								String propertyFileRelationsWithoutLocale = propertyFileName.substring(0, propertyFileName.lastIndexOf("_")) + ".properties";
								String relatedFile = propertyFileRelations.get(propertyFileRelationsWithoutLocale);
								propertyFileRelations.put(propertyFileName, relatedFile);
							}
						}
					}
				}
			}


			// TODO: Fetch each properties file & upload
			for (String propertiesFileName: propertyFileRelations.keySet()) {
				File propertiesFile = new File(assetsFolderFullPath.replace("/assets", "/resources") + "/" + propertiesFileName);

				if (propertiesFile.exists()) {
					String parentJsonFormFileName = propertyFileRelations.get(propertiesFileName);
					String title = "Properties File";

					if (parentJsonFormFileName != null) {
						String titleRetrieved = formLabels.get(parentJsonFormFileName);
						title = titleRetrieved == null ? title : titleRetrieved + " Rules File";
					}

					RequestBody requestBody = generateRequestBody(formVersion, propertiesFileName, "application/octet-stream", propertiesFile, parentJsonFormFileName,
							title);
					Call httpCall = generateHttpCall(clientFormUrl, basicAuthValue, requestBody);

					try {
						Response response = httpCall
								.execute();
						if (response.isSuccessful()) {
							// notification about succesful request
							System.out.println("Request was successful for " + propertiesFileName);
						} else {
							// notification about failure request
							System.out.println("Request was not successful for " + propertiesFileName);
						}
					}
					catch (IOException e1) {
						// notification about other problems
						System.out.println("Request exception for posting " + propertiesFileName);
						e1.printStackTrace();
					}
				}
			}

			// TODO: Fetch each YML file & upload
			for (String ymlFileName: ruleFileRelations.keySet()) {
				File ymlFile = new File(assetsFolderFullPath + "/rule/" + ymlFileName);

				if (ymlFile.exists()) {
					String parentJsonFormFileName = ruleFileRelations.get(ymlFileName);
					String title = "Sample YML File";

					if (parentJsonFormFileName != null) {
						String titleRetrieved = formLabels.get(parentJsonFormFileName);
						title = titleRetrieved == null ? title : titleRetrieved + " Rules File";
					}

					RequestBody requestBody = generateRequestBody(formVersion, ymlFileName, "application/x-yaml", ymlFile, parentJsonFormFileName,
							title);
					Call httpCall = generateHttpCall(clientFormUrl, basicAuthValue, requestBody);

					try {
						Response response = httpCall
								.execute();
						if (response.isSuccessful()) {
							// notification about succesful request
							System.out.println("Request was successful for " + ymlFileName);
						} else {
							// notification about failure request
							System.out.println("Request was not successful for " + ymlFileName);
						}
					}
					catch (IOException e1) {
						// notification about other problems
						System.out.println("Request exception for posting " + ymlFileName);
						e1.printStackTrace();
					}
				}
			}

			// TODO: Fetch each JSON sub-form file & upload
			for (String subFormFileName: subFormFileRelations.keySet()) {
				File subFormFile = new File(assetsFolderFullPath + "/" + jsonSubFormFolderFromAssets + "/" + subFormFileName);
				if (subFormFile.exists()) {
					String parentJsonFormFileName = subFormFileRelations.get(subFormFileName);
					String title = "Sample Sub-Form File";

					if (parentJsonFormFileName != null) {
						String titleRetrieved = formLabels.get(parentJsonFormFileName);
						title = titleRetrieved == null ? title : titleRetrieved + " Sub-Form File";
					}

					RequestBody requestBody = generateRequestBody(formVersion, subFormFileName, "application/json", subFormFile, parentJsonFormFileName,
							title);
					Call httpCall = generateHttpCall(clientFormUrl, basicAuthValue, requestBody);

					try {
						Response response = httpCall
								.execute();
						if (response.isSuccessful()) {
							// notification about successful request
							System.out.println("Request was successful for " + subFormFileName);
						} else {
							// notification about failure request
							System.out.println("Request was not successful for " + subFormFileName);
						}
					} catch (IOException e1) {
						// notification about other problems
						System.out.println("Request exception for posting " + subFormFileName);
						e1.printStackTrace();
					}
				}
			}

			// TODO: Check each JSON form file & upload
			Iterator<String> jsonFormFilesIterator = jsonFormFiles.iterator();
			while (jsonFormFilesIterator.hasNext()) {
				String formFileName = jsonFormFilesIterator.next();
				File formFile = new File(assetsFolderFullPath + "/" + jsonFolderFolderFromAssets + "/" + formFileName);

				if (formFile.exists()) {
					String titleRetrieved = formLabels.get(formFileName);
					String title = titleRetrieved == null ? "Sample Form" : titleRetrieved + " Form";

					RequestBody requestBody = generateRequestBody(formVersion, formFileName, "application/json"
							, formFile, null, title);
					Call httpCall = generateHttpCall(clientFormUrl, basicAuthValue, requestBody);

					try {
						Response response = httpCall.execute();
						if (response.isSuccessful()) {
							// notification about successful request
							System.out.println("Request was successful for " + formFileName);
						} else {
							// notification about failure request
							System.out.println("Request was not successful for " + formFileName);
						}
					}
					catch (IOException exception) {
						// notification about other problems
						System.out.println("Request exception for posting " + formFileName);
						exception.printStackTrace();
					}
				}
			}

			// TODO: Upload the final manifest
			JsonObject jsonManifest = new JsonObject();
			jsonManifest.addProperty("identifier", formVersion);
			jsonManifest.addProperty("appId", appId);
			jsonManifest.addProperty("appVersion", appVersion);

			// Generate the forms_version & identifiers
			JsonObject json = new JsonObject();
			json.addProperty("forms_version", formVersion);
			JsonArray identifiersArray = new JsonArray();
			for (String rulesFile: ruleFileRelations.keySet()) {
				identifiersArray.add(rulesFile);
			}

			for (String subFormFile: subFormFileRelations.keySet()) {
				identifiersArray.add(subFormFile);
			}

			jsonFormFilesIterator = jsonFormFiles.iterator();
			while (jsonFormFilesIterator.hasNext()) {
				String jsonFormFile = jsonFormFilesIterator.next();
				identifiersArray.add(jsonFormFile);
			}
			json.add("identifiers", identifiersArray);

			String manifestJsonPropertyString = json.toString();
			jsonManifest.addProperty("json", manifestJsonPropertyString);

			System.out.println("manifest.json = " + manifestJsonPropertyString);

			String manifestString = jsonManifest.toString();
			RequestBody manifestRequestBody = RequestBody.create(MediaType.parse("application/json"), manifestString);


			System.out.println("manifest = " + manifestString);

			Request request = new Request.Builder()
					.header("Authorization", basicAuthValue)
					.url(manifestUrl)
					.post(manifestRequestBody)
					.build();
			OkHttpClient httpClient = new OkHttpClient();
			httpClient.setReadTimeout(5, TimeUnit.SECONDS);


			try {
				Response response = httpClient.newCall(request).execute();
				if (response.isSuccessful()) {
					// notification about successful request
					System.out.println("Request was successful for uploading the manifest");
				} else {
					// notification about failure request
					System.out.println("Request was not successful for uploading the manifest");
				}
			}
			catch (IOException exception) {
				// notification about other problems
				System.out.println("Request exception for uploading the manifest ");
				exception.printStackTrace();
			}
		}

	}



}
