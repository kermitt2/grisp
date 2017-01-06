package com.scienceminer.grisp.nerd.data;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.*;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Test;

public class ProcessTranslationTest {

	@Before
	public void setUp() {
		// 
	}

	@Test
	public void testConvertSqlEntryToCsvr() {
		List<String> inputs = Arrays.asList("(142236,'fr','salut les nuls')", "(746,'ab','Азербаиџьан')");
		List<String> outputs = Arrays.asList("142236|fr|salut les nuls", null);
		for (String input : inputs) {
			String output = ProcessTranslation.convertSqlEntry(input);
			System.out.println(input + " -> " + output);
		}
	}
	

}
