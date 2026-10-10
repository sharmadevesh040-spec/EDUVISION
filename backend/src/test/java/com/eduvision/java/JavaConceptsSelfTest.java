package com.eduvision.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.eduvision.concurrent.ParallelGradingService;
import com.eduvision.jdbc.AttendanceJdbcDao;
import com.eduvision.jdbc.AttendanceRecord;
import com.eduvision.jdbc.ConnectionFactory;
import com.eduvision.oops.ContentProcessor;
import com.eduvision.oops.MediaContentProcessor;
import com.eduvision.oops.PartContentProcessor;
import com.eduvision.oops.ProcessorRegistry;
import com.eduvision.oops.QuizContentProcessor;

/**
 * Build-time proof of the three missing rubric concepts:
 * (A) explicit JDBC DAO classes, (B) multithreading &amp; synchronization, (C) OOP polymorphism.
 *
 * <p>Run with: {@code mvn test -Dtest=JavaConceptsSelfTest}
 */
class JavaConceptsSelfTest {

    @Test
    @DisplayName("JDBC: explicit DriverManager DAO saves 3 rows and reads them back")
    void jdbcCrudWorks() {
        ConnectionFactory connectionFactory =
                new ConnectionFactory("jdbc:h2:mem:conceptstest;DB_CLOSE_DELAY=-1", "sa", "");
        AttendanceJdbcDao dao = new AttendanceJdbcDao(connectionFactory);

        // The DAO creates its own table; clear any rows left by a previous run in this JVM.
        try (Connection c = connectionFactory.open();
             Statement st = c.createStatement()) {
            st.execute("DELETE FROM jdbc_attendance");
        } catch (Exception e) {
            throw new AssertionError("table cleanup failed", e);
        }

        dao.save(new AttendanceRecord(null, "Ravi Sharma", true));
        dao.save(new AttendanceRecord(null, "Priya Nair", false));
        dao.save(new AttendanceRecord(null, "Aman Verma", true));

        List<AttendanceRecord> all = dao.findAll();
        assertEquals(3, all.size(), "findAll() must return the 3 saved rows");
        assertEquals(3L, dao.count(), "count() must report 3 rows");
        assertTrue(all.stream().allMatch(r -> r.getId() != null && r.getId() > 0),
                "every row must carry a database-generated id");
        assertTrue(dao.findById(all.get(0).getId()).isPresent(), "findById() must resolve a saved row");
    }

    @Test
    @DisplayName("Multithreading: runDemo(8) runs 8 tasks and the synchronized total matches the atomic total")
    void concurrencyAggregatesConsistently() {
        ParallelGradingService service = new ParallelGradingService();
        try {
            Map<String, Object> result = service.runDemo(8);
            assertEquals(8, result.get("tasksRun"), "all 8 submitted tasks must complete");
            long atomicTotal = ((Number) result.get("atomicTotal")).longValue();
            long syncTotal = ((Number) result.get("syncTotal")).longValue();
            assertEquals(atomicTotal, syncTotal,
                    "AtomicInteger total and synchronized total must agree");
            assertTrue(atomicTotal > 0, "the aggregate score must be non-zero");
            assertTrue((Integer) result.get("threadsUsed") > 1, "the pool must use more than one thread");
        } finally {
            service.shutdown();
        }
    }

    @Test
    @DisplayName("OOP: the registry resolves 3 distinct polymorphic processors with different descriptions")
    void oopsPolymorphism() {
        ProcessorRegistry registry = new ProcessorRegistry(List.of(
                new QuizContentProcessor(), new PartContentProcessor(), new MediaContentProcessor()));

        assertEquals(3, registry.size(), "exactly 3 content processors must be registered");
        assertTrue(registry.supports("quiz"));
        assertTrue(registry.supports("part"));
        assertTrue(registry.supports("media"));

        Set<String> descriptions = new HashSet<>();
        Set<String> classes = new HashSet<>();
        for (ContentProcessor processor : registry.all()) {
            classes.add(processor.getClass().getName());
            descriptions.add(processor.describe(6, 20));
        }
        assertEquals(3, classes.size(), "3 distinct processor classes");
        assertEquals(3, descriptions.size(), "each processor must describe itself differently");

        // polymorphic dispatch through the interface, plus exception handling for an unknown kind
        assertEquals("QuizContentProcessor", registry.resolve("quiz").getClass().getSimpleName());
        assertThrows(IllegalArgumentException.class, () -> registry.resolve("does-not-exist"));
        assertNotEquals(registry.resolve("part").describe(6, 20),
                registry.resolve("media").describe(6, 20));
    }
}
