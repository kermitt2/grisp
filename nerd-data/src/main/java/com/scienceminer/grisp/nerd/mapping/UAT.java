package com.scienceminer.grisp.nerd.mapping;

import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;    
import java.math.BigInteger;

import com.scienceminer.nerd.disambiguation.*;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

public class UAT {

	static private String REST_URL = "http://localhost:8090/service/disambiguate";
	static private String QUERY_TEMPLATE = "{ \"termVector\" : [%1%], \"language\" : { \"lang\" : \"en\" }, \"entities\" : [], \"onlyNER\" : false, \"resultLanguages\" : [ \"de\", \"fr\"],\"nbest\" : false, \"sentence\" : false, \"format\" : \"JSON\", \"customisation\" : \"generic\" };";

	private List<String> terms = null;

	public UAT(String path) {
		int nb = readUAT(path);
		System.out.println(nb + " terms in UAT");
	}

	/**
	 * Read the UAT thesaurus in json 
	 */
	public int readUAT(String path) {
		System.out.println("Reading UAT from " + path);
		String json = null;
		try {
			json = FileUtils.readFileToString(new File(path), "UTF-8");
		} catch(IOException e) {
			e.printStackTrace();
		}
		ObjectMapper mapper = new ObjectMapper();
		terms = new ArrayList<String>(); 

		// get all the names and create a term vector
		try {
			JsonNode rootNode = mapper.readTree(json);	
			treewalk(rootNode);
		} catch(IOException e) {
			e.printStackTrace();
		}
		/*for(String term : terms) {
			System.out.println(term);
		}*/

		return terms.size();
	}

	private void treewalk(JsonNode node) {
		JsonNode nameNode = node.findPath("name");
		String name = null;
        if ((nameNode != null) && (!nameNode.isMissingNode())) {
        	name = nameNode.textValue().trim();
        	if (!terms.contains(name))
        		terms.add(name);
        }
		Iterator ite = node.elements();
		if (ite != null) {
			while(ite.hasNext()) {
				treewalk((JsonNode)ite.next());
			}
		}
	}

	public void injectResults(String inputPath, String resultPath) {
		
	}

	public int mapNerd() {
		int nbResult = 0;

		StringBuilder builder =new StringBuilder();
		boolean start = true;
		for(String term : terms) {
			if (start)
				start = false;
			else
				builder.append(", ");
			builder.append("{\"term\":\"").append(term).append("\", \"score\": 1.0").append("}");
		}

		// create the query object
		String json = QUERY_TEMPLATE.replace("%1%",builder.toString());

		System.out.println(json);

		return nbResult;
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("Invalid arguments: [input_path_to_UAT] [result_path]");
		} else {
			long start = System.currentTimeMillis();
			UAT uat = new UAT(args[0]);
			int nbResult = uat.mapNerd();
			uat.injectResults(args[0], args[1]);
			long end = System.currentTimeMillis();

			System.out.println(nbResult + " ... produced in " + (end - start) + " ms");
		}
	}

}