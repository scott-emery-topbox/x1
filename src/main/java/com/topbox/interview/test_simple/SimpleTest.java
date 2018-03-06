package com.topbox.interview.test_simple;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

public class SimpleTest {

	protected Logger log = LoggerFactory.getLogger(this.getClass().getSimpleName());

	private static final String CALLS_INPUT = "/tmp/calls-input.csv";
	private static final String CONFLICTS_OUTPUT = "/tmp/conflicts.csv";

	public static void main(String[] args) throws IOException {
		SimpleTest simpleTest = new SimpleTest();
		simpleTest.generateTestInput();
		simpleTest.findConflicts();
		simpleTest.findConflictsTree();
	}

	public boolean isOverlap(Call call1, Call call2) {
		boolean overlap = false;
		if (call2.getEnd().after(call1.getStart())) {
			if (call2.getStart().before(call1.getEnd())) {
				overlap = true;
			}
		}
		return overlap;
	}

	public void findConflictsTree() throws FileNotFoundException, IOException {
		log.info("starting findConflictsTree();");
		Comparator<Call> comparator = new Comparator<Call>() {
			@Override
			public int compare(Call o1, Call o2) {
				if (o1.getId().equals(o2.getId())) {
					return 0;
				}
				if (isOverlap(o1, o2)) {
					throw new RuntimeException(
							o1.getAgentName() + "-" + o1.getId() + ":" + o2.getAgentName() + "_" + o2.getId());
				} else if (o1.getStart().before(o2.getStart())) {
					return -1;
				} else {
					return 1;
				}
			}
		};

		MappingIterator<Call> csvIterator = readCsv(CALLS_INPUT);
		Map<String, Set<Call>> agentCallsMap = new HashMap<String, Set<Call>>();
		int conflictsCount = 0;
		while (csvIterator.hasNext()) {
			Call call = csvIterator.next();
			Set<Call> agentsCalls = agentCallsMap.get(call.getAgentName());
			if (agentsCalls == null) {
				agentsCalls = new TreeSet<Call>(comparator);
				agentCallsMap.put(call.getAgentName(), agentsCalls);
			}
			try {
				agentsCalls.add(call);
			} catch (Exception e) {
				System.out.println(e.getMessage());
				conflictsCount++;
			}
		}
		log.info("conflict count: " + conflictsCount);
	}

	public void findConflicts() throws FileNotFoundException, IOException {
		log.info("starting findConflicts();");
		MappingIterator<Call> csvIterator = readCsv(CALLS_INPUT);
		int conflictsCount = 0;
		while (csvIterator.hasNext()) {
			Call call = csvIterator.next();
			MappingIterator<Call> csvIterator2 = readCsv(CALLS_INPUT);
			while (csvIterator2.hasNext()) {
				Call call2 = csvIterator2.next();
				if (!call.getId().equals(call2.getId())) {
					if (StringUtils.equalsIgnoreCase(call.getAgentName(), call2.getAgentName())) {
						if (isOverlap(call, call2)) {
							conflictsCount++;
							log.info("{}-{}:{}-{}", call.getAgentName(), call.getId(), call2.getAgentName(),
									call2.getId());
						}
					}
				}
			}
		}
		log.info("conflict count: " + conflictsCount);
	}

	public void generateTestInput() throws IOException {
		int numberOfAgents = 75;
		int secretAgent = new Double(Math.random() * 1000 % numberOfAgents).intValue();
		double avgCallDurationSeconds = 300;
		double avgBreakDurationSeconds = 500;
		int callSequence = 1;
		int imposterCallsCount = 0;

		List<Call> calls = new ArrayList<Call>();

		Random random = new Random();
		for (int i = 0; i < numberOfAgents; i++) {
			Date date = DateUtils.truncate(new Date(), Calendar.DATE);
			String agentName = "agent_" + i;
			for (int j = 0; j < 86400; j++) {
				Date start = DateUtils.addSeconds(date, j);
				int callDuration = new Double(
						(Math.random() * BooleanUtils.toInteger(random.nextBoolean(), -1, 1) * avgCallDurationSeconds)
								+ avgCallDurationSeconds).intValue();
				Call call = new Call();
				call.setId(new Long(callSequence));
				call.setAgentName(agentName);
				if (i == secretAgent) {
					if (Math.random() < 0.1) {
						imposterCallsCount++;
						call.setAgentName("agent_" + new Double(Math.random() * 100 % numberOfAgents).intValue());
						log.info("IMPOSTER CALL: {}-{}", call.getAgentName(), call.getId());
					}
				}
				call.setStart(start);
				call.setEnd(DateUtils.addSeconds(start, callDuration));
				callSequence++;
				j += callDuration + new Double(
						(Math.random() * BooleanUtils.toInteger(random.nextBoolean(), -1, 1) * avgBreakDurationSeconds)
								+ avgBreakDurationSeconds).intValue();
				calls.add(call);
			}
			Collections.shuffle(calls);
			writeCsv(calls, CALLS_INPUT);
		}
		log.info("secretAgent = " + secretAgent + " imposterCallsCount: " + imposterCallsCount);
	}

	public MappingIterator<Call> readCsv(String path) throws FileNotFoundException, IOException {
		CsvMapper mapper = new CsvMapper();
		CsvSchema schema = mapper.schemaFor(Call.class);
		ObjectReader objectReader = mapper.readerFor(Call.class).with(schema);
		Reader reader = new FileReader(path);
		return objectReader.readValues(reader);
	}

	public void writeCsv(Collection<Call> calls, String path) throws IOException {
		CsvMapper mapper = new CsvMapper();
		CsvSchema schema = mapper.schemaFor(Call.class);
		ObjectWriter writer = mapper.writerFor(Call.class).with(schema);
		File tempFile = new File(path);
		writer.writeValues(tempFile).writeAll(calls);
	}

}
