package com.eduvision.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvision.concurrent.ParallelGradingService;
import com.eduvision.jdbc.AttendanceJdbcDao;
import com.eduvision.jdbc.AttendanceRecord;
import com.eduvision.oops.ContentProcessor;
import com.eduvision.oops.ProcessorRegistry;

/**
 * Demonstrates the three rubric concept areas that have to be proven <em>in Java</em>:
 * explicit JDBC (DAO classes), multithreading &amp; synchronization, and OOP
 * (interfaces / inheritance / polymorphism / exception handling).
 *
 * <pre>
 * GET /api/java-concepts/report -> { jdbc: {...}, concurrency: {...}, oops: {...} }
 * </pre>
 *
 * The endpoint is permitted without a token (see SecurityConfig) so it can be demonstrated live.
 */
@RestController
@RequestMapping(value = "/api/java-concepts", produces = MediaType.APPLICATION_JSON_VALUE)
public class JavaConceptsController {

    private final AttendanceJdbcDao attendanceDao;
    private final ParallelGradingService gradingService;
    private final ProcessorRegistry processorRegistry;

    public JavaConceptsController(AttendanceJdbcDao attendanceDao,
                                  ParallelGradingService gradingService,
                                  ProcessorRegistry processorRegistry) {
        this.attendanceDao = attendanceDao;
        this.gradingService = gradingService;
        this.processorRegistry = processorRegistry;
    }

    @GetMapping("/report")
    public Map<String, Object> report() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("jdbc", jdbcDemo());
        report.put("concurrency", concurrencyDemo());
        report.put("oops", oopsDemo());
        return report;
    }

    /** Explicit-JDBC DAO demo: insert 3 rows then read them back through the generic DAO interface. */
    private Map<String, Object> jdbcDemo() {
        List<AttendanceRecord> inserted = new ArrayList<>();
        inserted.add(attendanceDao.save(new AttendanceRecord(null, "Ravi Sharma", true)));
        inserted.add(attendanceDao.save(new AttendanceRecord(null, "Priya Nair", false)));
        inserted.add(attendanceDao.save(new AttendanceRecord(null, "Aman Verma", true)));

        List<AttendanceRecord> all = attendanceDao.findAll();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("daoClass", attendanceDao.getClass().getName());
        out.put("genericInterface", "com.eduvision.jdbc.GenericDao");
        out.put("table", attendanceDao.table());
        out.put("mechanism", "java.sql.DriverManager + PreparedStatement (explicit JDBC)");
        out.put("connectionUrl", attendanceDao.connectionUrl());
        out.put("inserted", inserted.size());
        out.put("count", attendanceDao.count());
        out.put("rows", all);
        return out;
    }

    /** Multithreading demo: parallel grading with atomic + synchronized aggregation. */
    private Map<String, Object> concurrencyDemo() {
        return gradingService.runDemo(8);
    }

    /** OOP demo: polymorphic dispatch across the registered content processors. */
    private Map<String, Object> oopsDemo() {
        List<Map<String, Object>> processors = new ArrayList<>();
        for (ContentProcessor processor : processorRegistry.all()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("class", processor.getClass().getName());
            row.put("kind", processor.kind());
            row.put("describe", processor.describe(6, 20));
            processors.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("interface", ContentProcessor.class.getName());
        out.put("baseClass", "com.eduvision.oops.AbstractContentProcessor");
        out.put("processorCount", processors.size());
        out.put("kinds", processorRegistry.kinds());
        out.put("processors", processors);
        return out;
    }
}
