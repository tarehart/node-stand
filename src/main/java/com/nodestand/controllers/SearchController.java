package com.nodestand.controllers;

import com.nodestand.nodes.ArgumentBody;
import com.nodestand.nodes.assertion.AssertionBody;
import com.nodestand.nodes.interpretation.InterpretationBody;
import com.nodestand.nodes.repository.ArgumentBodyRepository;
import com.nodestand.nodes.source.SourceBody;
import com.nodestand.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class SearchController {

    private final UserService userService;

    private final ArgumentBodyRepository argumentBodyRepository;

    @Autowired
    public SearchController(UserService userService, ArgumentBodyRepository argumentBodyRepository) {
        this.userService = userService;
        this.argumentBodyRepository = argumentBodyRepository;
    }

    @PreAuthorize("hasRole('ROLE_USER')")
    @Transactional
    @RequestMapping("/search")
    public List<ArgumentBody> findByTitle(@RequestParam String query, @RequestParam List<String> types) {

        Long userId = userService.getUserNodeIdFromSecurityContext();

        Set<ArgumentBody> richHits = argumentBodyRepository.queryTitlesRich(String.format("(?i).*%s.*", query), userId);

        List<ArgumentBody> searchResults = new LinkedList<>();

        Set<String> majorVersionIds = new HashSet<>();

        List<Class> acceptableClasses = buildAcceptableClasses(types);

        // Keep the search results in order. If there are multiple bodies corresponding to
        // the same major version node, return only the first one.
        // This is NOT the version number; it's the unique node id
        richHits.stream().filter(body -> acceptableClasses.contains(body.getClass())).forEach(body -> {
            String majorVersionId = body.getMajorVersion().getStableId(); // This is NOT the version number; it's the unique node id
            if (!majorVersionIds.contains(majorVersionId)) {
                searchResults.add(body);
                majorVersionIds.add(majorVersionId);
            }
        });

        return searchResults;
    }

    private List<Class> buildAcceptableClasses(List<String> types) {
        List<Class> acceptableClasses = new ArrayList<>();
        if (types.contains("assertion")) {
            acceptableClasses.add(AssertionBody.class);
        }
        if (types.contains("interpretation")) {
            acceptableClasses.add(InterpretationBody.class);
        }
        if (types.contains("source")) {
            acceptableClasses.add(SourceBody.class);
        }
        return acceptableClasses;
    }
}
