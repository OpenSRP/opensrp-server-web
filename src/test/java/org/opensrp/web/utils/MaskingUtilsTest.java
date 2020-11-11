package org.opensrp.web.utils;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;

public class MaskingUtilsTest {
	
	@Test
	public void testMaskDateFromStringWithValidDate() {
		
		MaskingUtils util = new MaskingUtils();
		String param = "2019-12-15";
		String result = util.maskDate(param);
		
		Assert.assertEquals("2019-01-01", result);
		
	}
	
	@Test
	public void testMaskDateFromStringWithInvalidDate() {
		
		MaskingUtils util = new MaskingUtils();
		String param = "2011kdjf";
		String result = util.maskDate(param);
		
		Assert.assertEquals("", result);
		
	}
	
	@Test
	public void testMaskDateFromStringWithEmptyParam() {
		
		MaskingUtils util = new MaskingUtils();
		String param = "";
		String result = util.maskDate(param);
		
		Assert.assertEquals("", result);
		
	}
	
	public void testMaskDateFromDateObjectWithValidDate() {
		
		MaskingUtils util = new MaskingUtils();
		
		LocalDate localDate = LocalDate.of(2015, 3, 16);
		
		Date result = util.maskDate(java.sql.Date.valueOf(localDate));
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(result);
		
		Assert.assertEquals(2015, cal.get(Calendar.YEAR));
		Assert.assertEquals(1, cal.get(Calendar.MONTH));
		Assert.assertEquals(1, cal.get(Calendar.DATE));
		
	}
	
	@Test
	public void testMaskDateFromDateObjectWithInvalidDate() {
		
		MaskingUtils util = new MaskingUtils();
		
		Date date = null;
		
		Date result = util.maskDate(date);
		
		Assert.assertNull(result);
		
	}
	
	@Test
	public void testMaskStringForValidValue() {
		
		//Valid
		MaskingUtils util = new MaskingUtils();
		String param = "Jonathan Archer";
		String result = util.maskString(param);
		
		Assert.assertEquals("xxxxxxxxxx", result);
		
	}
	
	@Test
	public void testMaskStringForEmptyParam() {
		
		//Valid
		MaskingUtils util = new MaskingUtils();
		String param = "";
		String result = util.maskString(param);
		
		Assert.assertEquals("xxxxxxxxxx", result);
		
	}
	
	@Test
	public void testMaskStringForNullParam() {
		
		//Valid
		MaskingUtils util = new MaskingUtils();
		String param = null;
		String result = util.maskString(param);
		
		Assert.assertEquals("xxxxxxxxxx", result);
		
	}
	
	public void testMaskDateFromJodaLocalDateObjectWithValidDate() {
		
		MaskingUtils util = new MaskingUtils();
		
		org.joda.time.LocalDate localDate = org.joda.time.LocalDate.parse("1998-10-07");
		
		Date result = util.maskDate(localDate);
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(result);
		
		Assert.assertEquals(1998, cal.get(Calendar.YEAR));
		Assert.assertEquals(1, cal.get(Calendar.MONTH));
		Assert.assertEquals(1, cal.get(Calendar.DATE));
		
	}
	
	public void testMaskDateFromJavaLocalDateObjectWithValidDate() {
		
		MaskingUtils util = new MaskingUtils();
		
		LocalDate localDate = LocalDate.of(2020, 2, 12);
		
		Date result = util.maskDate(localDate);
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(result);
		
		Assert.assertEquals(20, cal.get(Calendar.YEAR));
		Assert.assertEquals(1, cal.get(Calendar.MONTH));
		Assert.assertEquals(1, cal.get(Calendar.DATE));
		
	}
	
	public void testMaskDateFromDateTimeObjectWithValidDate() {
		
		MaskingUtils util = new MaskingUtils();
		
		DateTime dateTime = new DateTime(2010, 4, 2, 0, 0, 0, 0);
		
		DateTime result = util.maskDate(dateTime);
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(result.toDate());
		
		Assert.assertEquals(2010, cal.get(Calendar.YEAR));
		Assert.assertEquals(1, cal.get(Calendar.MONTH));
		Assert.assertEquals(1, cal.get(Calendar.DATE));
		
	}
	
}
