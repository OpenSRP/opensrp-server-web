package org.opensrp.web.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONObject;
import org.opensrp.domain.Report;
import org.opensrp.service.PhysicalLocationService;
import org.opensrp.service.ReportService;
import org.smartregister.domain.PhysicalLocation;
import org.smartregister.utils.DateTimeTypeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Controller
@RequestMapping(value = "/rest/report/")
public class ReportResource {

	private static Logger logger = LogManager.getLogger(ReportResource.class.toString());
	
	private ReportService reportService;

	private PhysicalLocationService locationService;

	private Map<String, Map<String, Object>> specificIndicatorsReport;

	private Map<String, Map<String, Object>> aggregatedIndicatorsReport;

	private Map<String, Map<String, Object>> stockIndicatorsReport;

	private static final String PROVINCE = "province";

	private static final String DISTRICT = "district";

	private static final String FACILITY = "facility";

	private static final String MONTH = "month";

	private static final String YEAR = "year";

	private static final String DATE_FROM = "dateFrom";

	private static final String DATE_TO = "dateTo";

	private static final String FACILITIES_COUNT = "facilitiesCount";

	@Autowired
	ServletContext context;

	@Value("#{opensrp['multimedia.directory.name']}")
	private String multiMediaDir;

	Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
			.registerTypeAdapter(DateTime.class, new DateTimeTypeConverter()).create();

	private List<Report> districtReports = null;

	@Autowired
	public ReportResource(ReportService reportService) {
		this.reportService = reportService;
	}

	@Autowired
	public void setLocationService(PhysicalLocationService locationService) {
		this.locationService = locationService;
	}

	/**
	 * adding dhis2 reports to opensrp
	 *
	 * @param data model payload
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping(headers = { "Accept=application/json" }, method = POST, value = "/add")
	public ResponseEntity<HttpStatus> save(@RequestBody String data) {
		try {
			JSONObject syncData = new JSONObject(data);
			if (!syncData.has("reports")) {
				return new ResponseEntity<>(BAD_REQUEST);
			}
			ArrayList<Report> reports = (ArrayList<Report>) gson.fromJson(syncData.getString("reports"),
					new TypeToken<ArrayList<Report>>() {

					}.getType());
			for (Report report : reports) {
				try {
					reportService.addorUpdateReport(report);
				}
				catch (Exception e) {
					logger.error("Report" + report.getId() + " failed to sync", e);
				}
			}
		}
		catch (Exception e) {
			logger.error(format("Sync data processing failed with exception {0}.- ", e));
			return new ResponseEntity<>(INTERNAL_SERVER_ERROR);
		}
		return new ResponseEntity<>(CREATED);
	}

	/**
	 * @param districtId District ID
	 * @param period     Period to generate report for. Takes the format YYYY-MM
	 * @param response
	 * @throws IOException
	 */
	@GetMapping(value = "/generate/{districtId:.+}/{period:.+}")
	public ResponseEntity<HttpStatus> generateReport(@PathVariable("districtId") String districtId,
			@PathVariable("period") String period,
			HttpServletResponse response) {

		if (StringUtils.isBlank(districtId)) {
			logger.error("Missing district argument");
			throw new IllegalArgumentException("Missing district ID");
		}

		PhysicalLocation district = locationService.getLocation(districtId, false, false);
		if (district == null) {
			logger.error("District not found: " + districtId);
			throw new IllegalArgumentException("District not found: " + districtId);
		}

		if (StringUtils.isBlank(period)) {
			logger.error("Missing report period");
			throw new IllegalArgumentException("Missing report period");
		}

		Pattern pattern = Pattern.compile("^(\\d{4}(-\\d{1,2}))$");
		if (!pattern.matcher(period).matches()) {
			logger.error("Invalid report period");
			throw new IllegalArgumentException("Invalid report period");
		}

		String[] parts = period.split("-");
		String year = parts[0];
		String month = parts[1];

		String templateFilename = "report-template.xlsx";
		String formattedDistrictName = district.getProperties().getName().toLowerCase().replace(" ", "_");
		String downloadFilename = String.format("%s-%s-%s-report.xlsx", formattedDistrictName, year, month);

		multiMediaDir = context.getRealPath("/WEB-INF/downloads/");
		Path path = Paths.get(multiMediaDir, templateFilename);

		// read template
		Workbook workbook = this.readXLSXTemplate(path.toFile());

		PhysicalLocation province = locationService.getLocation(district.getProperties().getParentId(), false, false);
		List<PhysicalLocation> facilities = getChildLocationsByLevel(district, "Facility");

		// prepare report data
		Map<String, Object> data = new HashMap<>();
		data.put(PROVINCE, province.getProperties().getName());
		data.put(DISTRICT, district.getProperties().getName());
		data.put(MONTH, month);
		data.put(YEAR, year);
		data.put(DATE_FROM, String.format("%s/%s/%s", "01", month, year));
		int lastDayOfMonth = Month.of(Integer.parseInt(month)).length(Year.isLeap(Long.parseLong(year)));
		data.put(DATE_TO, String.format("%s/%s/%s", lastDayOfMonth, month, year));
		data.put(FACILITIES_COUNT, facilities.size());

		districtReports = new ArrayList<>();
		addFacilitySheets(workbook, facilities, data);
		populateDistrictSheet(workbook, data);

		// remove the template district sheet
		workbook.removeSheetAt(1);

		Path downloadPath = Paths.get(multiMediaDir, downloadFilename);
		FileOutputStream fileOut = null;
		try {
			fileOut = new FileOutputStream(downloadPath.toFile());
			workbook.write(fileOut);
			fileOut.close();
			workbook.close();

			return new ResponseEntity<>(CREATED);
		}
		catch (IOException e) {
			logger.error("Failed to write workbook to file: " + e.getMessage(), e);
			return new ResponseEntity<>(INTERNAL_SERVER_ERROR);
		}
	}

	private List<PhysicalLocation> getChildLocationsByLevel(PhysicalLocation parent, String childLocationLevel) {
		List<PhysicalLocation> children = locationService.findLocationByIdWithChildren(false, parent.getId(), 1000);

		return children.stream().filter(
				c -> c.getLocationTags()
						.stream()
						.anyMatch(t -> t.getName()
								.equalsIgnoreCase(childLocationLevel)
						)
		).collect(Collectors.toList());
	}

	/**
	 * @param districtId District ID
	 * @param period     Period to generate report for. Takes the format YYYY-MM
	 * @param response
	 * @throws IOException
	 */
	@GetMapping(value = "/download/{districtId:.+}/{period:.+}")
	public void downloadFile(@PathVariable("districtId") String districtId, @PathVariable("period") String period,
			HttpServletResponse response) {

		if (StringUtils.isBlank(districtId)) {
			logger.error("Missing district argument");
			throw new IllegalArgumentException("Missing district");
		}

		PhysicalLocation district = locationService.getLocation(districtId, false, false);
		if (district == null) {
			logger.error("District not found: " + districtId);
			throw new IllegalArgumentException("District not found" + districtId);
		}

		if (StringUtils.isBlank(period)) {
			logger.error("Missing report period");
			throw new IllegalArgumentException("Missing report period");
		}

		Pattern pattern = Pattern.compile("^(\\d{4}(-\\d{1,2}))$");
		if (!pattern.matcher(period).matches()) {
			logger.error("Invalid report period");
			throw new IllegalArgumentException("Invalid report period");
		}

		String[] parts = period.split("-");
		String year = parts[0];
		String month = parts[1];

		String formattedDistrictName = district.getProperties().getName().toLowerCase().replace(" ", "_");
		String downloadFilename = String.format("%s-%s-%s-report.xlsx", formattedDistrictName, year, month);

		multiMediaDir = context.getRealPath("/WEB-INF/downloads/");
		Path downloadPath = Paths.get(multiMediaDir, downloadFilename);

		if (Files.exists(downloadPath)) {
			response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			response.addHeader("Content-Disposition", "attachment; filename=" + downloadFilename);

			try {
				Files.copy(downloadPath, response.getOutputStream());
				response.getOutputStream().flush();
			}
			catch (IOException | EncryptedDocumentException e) {
				logger.error("Failed to download file: " + e.getMessage(), e);
				throw new IllegalArgumentException("Report could not be downloaded");
			}
		} else {
			logger.error("Report not found");
			throw new IllegalArgumentException("Report not found");
		}
	}

	/**
	 * Read excel template file from file system
	 *
	 * @param file Location of the Excel file
	 * @return Excel Workbook
	 */
	private Workbook readXLSXTemplate(File file) {
		try {
			FileInputStream inputStream = new FileInputStream(file);
			XSSFWorkbook workbook = new XSSFWorkbook(inputStream);

			return workbook;
		}
		catch (FileNotFoundException e) {
			logger.error("XLSX template file not found", e);
		}
		catch (IOException e) {
			logger.error("Could not read XLSX template file", e);
		}

		return null;
	}

	/**
	 * Load data onto the main district sheet
	 *
	 * @param workbook Workbook being acted on
	 * @param data     Map of data to be populated onto worksheet
	 */
	private void populateDistrictSheet(Workbook workbook, Map<String, Object> data) {
		Sheet sheet = workbook.getSheet("district-sample");
		workbook.setSheetName(0, "Moughataa-" + data.get(DISTRICT).toString());

		// report headers data
		Row row = sheet.getRow(4);
		Cell cell = row.getCell(1);
		cell.setCellValue(
				String.format(cell.getStringCellValue(), data.get(PROVINCE).toString(), data.get(DISTRICT).toString()));
		cell = row.getCell(15);
		cell.setCellValue(String.format(cell.getStringCellValue(), data.get(MONTH).toString()));

		row = sheet.getRow(5);
		cell = row.getCell(1);
		cell.setCellValue(String.format(cell.getStringCellValue(), data.get(FACILITIES_COUNT).toString()));
		cell = row.getCell(7);
		cell.setCellValue(String.format(cell.getStringCellValue(), districtReports.size()));
		cell = row.getCell(15);
		cell.setCellValue(String.format(cell.getStringCellValue(), data.get(YEAR).toString()));

		aggregatedIndicatorsReport = reportService.populateAggregatedIndicatorsSection(districtReports);
		specificIndicatorsReport = reportService.populateSpecificIndicatorsSection(districtReports);
		stockIndicatorsReport = reportService.populateStockIndicatorsSection(districtReports);

		// load stock data
		if (stockIndicatorsReport.size() > 0) {
			int col = 2;
			for (Map.Entry<String, Map<String, Object>> entry : stockIndicatorsReport.entrySet()) {
				row = sheet.getRow(36);
				row.getCell(col).setCellValue(entry.getKey());

				for (Map.Entry<String, Object> value : entry.getValue().entrySet()) {
					row = sheet.getRow(37);
					row.getCell(col).setCellValue(value.getValue().toString());

					row = sheet.getRow(38);
					row.getCell(col).setCellValue(value.getValue().toString());

					row = sheet.getRow(39);
					row.getCell(col).setCellValue(value.getValue().toString());

					row = sheet.getRow(40);
					row.getCell(col).setCellValue(value.getValue().toString());

					row = sheet.getRow(41);
					row.getCell(col).setCellValue(value.getValue().toString());
				}
				col++;
				if (col == 6 || col == 8) {
					col++;
				}
			}
		}

		// load specific data onto sheet
		int startRow = 11;
		Row sampleRow = sheet.getRow(startRow);

		if (specificIndicatorsReport.size() > 0) {
			for (Map.Entry<String, Map<String, Object>> entry : specificIndicatorsReport.entrySet()) {
				// shift rows down
				sheet.shiftRows(startRow, sheet.getLastRowNum() + 1, 1);

				// create new row
				Row newRow = sheet.createRow(startRow);

				copyRow(sheet, sampleRow, newRow);

				// update newly created row
				row = sheet.getRow(startRow);
				row.getCell(1).setCellValue(entry.getKey());
				row.getCell(4).setCellValue(entry.getValue().get("fixed_m").toString());
				row.getCell(5).setCellValue(entry.getValue().get("fixed_f").toString());
				row.getCell(6).setCellValue(entry.getValue().get("mobile_m").toString());
				row.getCell(7).setCellValue(entry.getValue().get("mobile_f").toString());
				row.getCell(8).setCellValue(entry.getValue().get("grand_total_m").toString());
				row.getCell(10).setCellValue(entry.getValue().get("grand_total_f").toString());
				row.getCell(11).setCellValue(entry.getValue().get("grand_total").toString());
				row.getCell(13).setCellValue(entry.getValue().get("out_of_area_m").toString());
				row.getCell(14).setCellValue(entry.getValue().get("out_of_area_f").toString());
				row.getCell(15).setCellValue(entry.getValue().get("out_of_area_total").toString());
				row.getCell(16).setCellValue(entry.getValue().get("out_of_tranche_m").toString());
				row.getCell(17).setCellValue(entry.getValue().get("out_of_tranche_f").toString());
				row.getCell(18).setCellValue(entry.getValue().get("out_of_tranche_total").toString());

				startRow++;
			}
		}

		// load aggregated data onto sheet
		startRow += 7;
		sampleRow = sheet.getRow(startRow);

		if (aggregatedIndicatorsReport.size() > 0) {
			for (Map.Entry<String, Map<String, Object>> entry : aggregatedIndicatorsReport.entrySet()) {
				// shift rows down
				sheet.shiftRows(startRow, sheet.getLastRowNum() + 1, 1);

				// create new row
				Row newRow = sheet.createRow(startRow);

				copyRow(sheet, sampleRow, newRow);

				// update newly created row
				row = sheet.getRow(startRow);
				row.getCell(1).setCellValue(entry.getKey());
				row.getCell(2).setCellValue(entry.getValue().get("stock_start_of_month").toString());
				row.getCell(3).setCellValue(entry.getValue().get("stock_received_during_month").toString());
				row.getCell(4).setCellValue(entry.getValue().get("stock_end_of_month").toString());
				row.getCell(5).setCellValue(entry.getValue().get("qty_used").toString());
				row.getCell(6).setCellValue(entry.getValue().get("qty_administered").toString());
				row.getCell(7).setCellValue(entry.getValue().get("qty_lost").toString());
				row.getCell(8).setCellValue(entry.getValue().get("loss_expired").toString());
				row.getCell(9).setCellValue(entry.getValue().get("loss_freezing").toString());
				row.getCell(10).setCellValue(entry.getValue().get("loss_heat").toString());
				row.getCell(11).setCellValue(entry.getValue().get("loss_other").toString());
				row.getCell(12).setCellValue(entry.getValue().get("loss_of_use").toString());
				row.getCell(14).setCellValue(entry.getValue().get("days_rupture").toString());
				row.getCell(15).setCellValue(entry.getValue().get("main_causes").toString());

				startRow++;
			}
		}
	}

	/**
	 * Create sheets for each district based on the the district template sheet
	 *
	 * @param workbook   Workbook being worked on
	 * @param facilities List of facilities
	 * @param data       Data to be populated in report
	 */
	private void addFacilitySheets(Workbook workbook, List<PhysicalLocation> facilities, Map<String, Object> data) {
		int sheetNumber = 1;

		for (PhysicalLocation facility : facilities) {
			Sheet newSheet = workbook.cloneSheet(1);
			workbook.setSheetName(++sheetNumber, facility.getProperties().getName());

			data.put(FACILITY, facility.getProperties().getName());

			populateFacilitySheet(newSheet, facility, data);
		}
	}

	/**
	 * Populate each facility's sheet with the report data
	 *
	 * @param sheet    Workbook being worked on
	 * @param facility PhysicalLocation Facility to report
	 * @param data     Data to be populated in report
	 */
	private void populateFacilitySheet(Sheet sheet, PhysicalLocation facility, Map<String, Object> data) {
		DateTime dateFrom = DateTimeFormat.forPattern("YYYY-MM-dd").parseDateTime(data.get(DATE_FROM).toString());
		DateTime dateTo = DateTimeFormat.forPattern("YYYY-MM-dd").parseDateTime(data.get(DATE_TO).toString());

		Row row = sheet.getRow(6);
		Cell cell = row.getCell(1);
		cell.setCellValue(String.format(cell.getStringCellValue(), data.get(PROVINCE), data.get(DISTRICT)));
		cell = row.getCell(9);
		cell.setCellValue(String.format(cell.getStringCellValue(), data.get(FACILITY)));

		row = sheet.getRow(7);
		cell = row.getCell(3);
		cell.setCellValue(String.format(cell.getStringCellValue(), data.get(MONTH)));
		cell = row.getCell(9);
		cell.setCellValue(String.format(cell.getStringCellValue(), data.get(YEAR)));

		// fetch reports for the facility for the specified period
		List<Report> reports = reportService.findReports(
				"",
				dateFrom,
				dateTo,
				"",
				"",
				facility.getId(),
				null,
				null
		);

		if (reports != null) {
			districtReports.addAll(reports);
		}

		aggregatedIndicatorsReport = reportService.populateAggregatedIndicatorsSection(reports);
		specificIndicatorsReport = reportService.populateSpecificIndicatorsSection(reports);

		// load specific data onto sheet
		int startRow = 13;
		Row sampleRow = sheet.getRow(startRow);

		if (specificIndicatorsReport.size() > 0) {
			for (Map.Entry<String, Map<String, Object>> entry : specificIndicatorsReport.entrySet()) {
				// shift rows down
				sheet.shiftRows(startRow, sheet.getLastRowNum() + 1, 1);

				// create new row
				Row newRow = sheet.createRow(startRow);

				copyRow(sheet, sampleRow, newRow);

				// newRow = sheet.getRow(startRow);
				newRow.getCell(1).setCellValue(entry.getKey());
				newRow.getCell(4).setCellValue(entry.getValue().get("fixed_m").toString());
				newRow.getCell(5).setCellValue(entry.getValue().get("fixed_f").toString());
				newRow.getCell(6).setCellValue(entry.getValue().get("mobile_m").toString());
				newRow.getCell(7).setCellValue(entry.getValue().get("mobile_f").toString());
				newRow.getCell(8).setCellValue(entry.getValue().get("grand_total_m").toString());
				newRow.getCell(10).setCellValue(entry.getValue().get("grand_total_f").toString());
				newRow.getCell(11).setCellValue(entry.getValue().get("grand_total").toString());
				newRow.getCell(13).setCellValue(entry.getValue().get("out_of_area_m").toString());
				newRow.getCell(14).setCellValue(entry.getValue().get("out_of_area_f").toString());
				newRow.getCell(15).setCellValue(entry.getValue().get("out_of_area_total").toString());
				newRow.getCell(16).setCellValue(entry.getValue().get("out_of_tranche_m").toString());
				newRow.getCell(17).setCellValue(entry.getValue().get("out_of_tranche_f").toString());
				newRow.getCell(18).setCellValue(entry.getValue().get("out_of_tranche_total").toString());

				startRow++;
			}
		}

		// load aggregated data onto sheet
		startRow += 7;
		sampleRow = sheet.getRow(startRow);

		if (aggregatedIndicatorsReport.size() > 0) {
			for (Map.Entry<String, Map<String, Object>> entry : aggregatedIndicatorsReport.entrySet()) {
				// shift rows down
				sheet.shiftRows(startRow, sheet.getLastRowNum() + 1, 1);

				// create new row
				Row newRow = sheet.createRow(startRow);

				copyRow(sheet, sampleRow, newRow);

				// update newly created row
				newRow = sheet.getRow(startRow);
				newRow.getCell(1).setCellValue(entry.getKey());
				newRow.getCell(2).setCellValue(entry.getValue().get("stock_start_of_month").toString());
				newRow.getCell(3).setCellValue(entry.getValue().get("stock_received_during_month").toString());
				newRow.getCell(4).setCellValue(entry.getValue().get("stock_end_of_month").toString());
				newRow.getCell(5).setCellValue(entry.getValue().get("qty_used").toString());
				newRow.getCell(6).setCellValue(entry.getValue().get("qty_administered").toString());
				newRow.getCell(7).setCellValue(entry.getValue().get("qty_lost").toString());
				newRow.getCell(8).setCellValue(entry.getValue().get("loss_expired").toString());
				newRow.getCell(9).setCellValue(entry.getValue().get("loss_freezing").toString());
				newRow.getCell(10).setCellValue(entry.getValue().get("loss_heat").toString());
				newRow.getCell(11).setCellValue(entry.getValue().get("loss_other").toString());
				newRow.getCell(12).setCellValue(entry.getValue().get("loss_of_use").toString());
				newRow.getCell(14).setCellValue(entry.getValue().get("days_rupture").toString());
				newRow.getCell(15).setCellValue(entry.getValue().get("main_causes").toString());

				startRow++;
			}
		}
	}

	/**
	 * Copy row format from source row to new row
	 *
	 * @param sheet     Sheet in workbook being worked on
	 * @param sourceRow Formatted template row
	 * @param newRow    New row to be added to the sheet
	 */
	private static void copyRow(Sheet sheet, Row sourceRow, Row newRow) {
		for (int i = 0; i < sourceRow.getLastCellNum(); i++) {
			// copy old cell to new cell
			Cell oldCell = sourceRow.getCell(i);
			Cell newCell = newRow.createCell(i);

			// if source cell is null, skip
			if (oldCell == null) {
				newCell = null;
				continue;
			}

			// copy style from old cell to new cell
			CellStyle cellStyle = sheet.getWorkbook().createCellStyle();
			cellStyle.cloneStyleFrom(oldCell.getCellStyle());

			newCell.setCellStyle(cellStyle);

			// copy cell comment if present
			if (oldCell.getCellComment() != null) {
				newCell.setCellComment(oldCell.getCellComment());
			}

			// copy cell hyperlink
			if (oldCell.getHyperlink() != null) {
				newCell.setHyperlink(oldCell.getHyperlink());
			}

			// set cell data type
			newCell.setCellType(oldCell.getCellType());

			switch (oldCell.getCellType()) {
				case Cell.CELL_TYPE_BLANK:
					newCell.setCellValue(oldCell.getStringCellValue());
					break;
				case Cell.CELL_TYPE_BOOLEAN:
					newCell.setCellValue(oldCell.getBooleanCellValue());
					break;
				case Cell.CELL_TYPE_ERROR:
					newCell.setCellErrorValue(oldCell.getErrorCellValue());
					break;
				case Cell.CELL_TYPE_FORMULA:
					newCell.setCellFormula(oldCell.getCellFormula());
					break;
				case Cell.CELL_TYPE_NUMERIC:
					newCell.setCellValue(oldCell.getNumericCellValue());
					break;
				case Cell.CELL_TYPE_STRING:
				default:
					newCell.setCellValue(oldCell.getRichStringCellValue());
					break;
			}
		}

		// for any merged regions in the source row, copy to new row
		for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
			CellRangeAddress cellRangeAddress = sheet.getMergedRegion(i);
			if (cellRangeAddress.getFirstRow() == sourceRow.getRowNum()) {
				CellRangeAddress newCellRangeAddress = new CellRangeAddress(newRow.getRowNum(),
						(newRow.getRowNum() + (cellRangeAddress.getLastRow() - cellRangeAddress.getFirstRow())),
						cellRangeAddress.getFirstColumn(),
						cellRangeAddress.getLastColumn());
				sheet.addMergedRegion(newCellRangeAddress);
			}
		}
	}

	public void generateMonthlyDistrictReports() {
		LocalDate currentDate = LocalDate.now();
		LocalDate startDate = currentDate.minusMonths(1).withDayOfMonth(1);
		LocalDate endDate = currentDate.minusMonths(1).with(lastDayOfMonth());

		logger.info("Generating district reports: " + startDate.toString() + " to " + endDate.toString());

		List<PhysicalLocation> districts = locationService.findLocationsByTagName(false, "District");
		for (PhysicalLocation district : districts) {
			logger.info(district.getId() + " - " + district.getProperties().getName() + " district report");

			generateDistrictReport(district, startDate, endDate);
		}
	}

	private void generateDistrictReport(PhysicalLocation district, LocalDate startDate, LocalDate endDate) {
		String templateFilename = "report-template.xlsx";
		multiMediaDir = context.getRealPath("/WEB-INF/downloads/");
		Path path = Paths.get(multiMediaDir, templateFilename);

		// read template
		Workbook workbook = this.readXLSXTemplate(path.toFile());

		PhysicalLocation province = locationService.getLocation(district.getProperties().getParentId(), false, false);
		List<PhysicalLocation> facilities = getChildLocationsByLevel(district, "Facility");

		int month = startDate.getMonth().getValue();
		int year = startDate.getYear();

		// prepare report data
		Map<String, Object> data = new HashMap<>();
		data.put(PROVINCE, province.getProperties().getName());
		data.put(DISTRICT, district.getProperties().getName());
		data.put(MONTH, month);
		data.put(YEAR, year);
		data.put(DATE_FROM, startDate);
		data.put(DATE_TO, endDate);
		data.put(FACILITIES_COUNT, facilities.size());

		String formattedDistrictName = district.getProperties().getName().toLowerCase().replace(" ", "_");
		String downloadFilename = String.format("%s-%s-%s-report.xlsx", formattedDistrictName, year, month);

		districtReports = new ArrayList<>();
		addFacilitySheets(workbook, facilities, data);
		populateDistrictSheet(workbook, data);

		// remove the template district sheet
		workbook.removeSheetAt(1);

		try {
			Path downloadPath = Paths.get(multiMediaDir, downloadFilename);
			FileOutputStream fileOut = new FileOutputStream(downloadPath.toFile());
			workbook.write(fileOut);
			fileOut.close();
			workbook.close();
		}
		catch (IOException e) {
			logger.error("Failed to write workbook to file", e);
		}
	}
}
