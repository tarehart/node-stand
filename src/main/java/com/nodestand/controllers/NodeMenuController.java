package com.nodestand.controllers;

import com.nodestand.nodes.ArgumentNode;
import com.nodestand.nodes.repository.ArgumentNodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.template.Neo4jOperations;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
public class NodeMenuController {

    @Autowired
    Neo4jOperations neo4jOperations;

    @Autowired
    ArgumentNodeRepository argumentNodeRepository;

    @Transactional
    @RequestMapping("/nodeMenu")
    public Set<ArgumentNode> getNodeMenu() {

        return argumentNodeRepository.getAllNodesRich();
    }

}
