package com.eduvision.web;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvision.service.SeedSummaryService;

/**
 * TEMPORARY read-only developer endpoints used to prove the TASK 2 seed landed on a fresh
 * database. Safe to delete in TASK 3 once real controllers exist.
 */
@RestController
@RequestMapping(value = "/api/dev", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins = "*")
public class DevController {

    private final SeedSummaryService seedSummaryService;

    public DevController(SeedSummaryService seedSummaryService) {
        this.seedSummaryService = seedSummaryService;
    }

    /**
     * Row counts of every seeded table, e.g.
     * {"users":10,"classes":1,"contents":6,"parts":23,"questions":20,"assignments":2,"classMembers":8}
     */
    @GetMapping("/seed-summary")
    public Map<String, Object> seedSummary() {
        return seedSummaryService.summary();
    }
}