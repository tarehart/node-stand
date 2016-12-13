package com.nodestand.service.argument;

import com.nodestand.auth.NotAuthorizedException;
import com.nodestand.controllers.ResourceNotFoundException;
import com.nodestand.controllers.serial.EditResult;
import com.nodestand.controllers.serial.QuickEdge;
import com.nodestand.controllers.serial.QuickGraphResponse;
import com.nodestand.nodes.ArgumentNode;
import com.nodestand.nodes.Author;
import com.nodestand.nodes.NodeRulesException;
import com.nodestand.nodes.User;
import com.nodestand.nodes.assertion.AssertionBody;
import com.nodestand.nodes.assertion.AssertionNode;
import com.nodestand.nodes.interpretation.InterpretationBody;
import com.nodestand.nodes.interpretation.InterpretationNode;
import com.nodestand.nodes.repository.ArgumentNodeRepository;
import com.nodestand.nodes.repository.UserRepository;
import com.nodestand.nodes.source.SourceBody;
import com.nodestand.nodes.source.SourceNode;
import com.nodestand.service.AuthorRulesUtil;
import com.nodestand.service.VersionHelper;
import com.nodestand.util.TwoWayUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.template.Neo4jOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class ArgumentServiceNeo4j implements ArgumentService {

    @Autowired
    ArgumentNodeRepository argumentRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    VersionHelper versionHelper;

    @Autowired
    Neo4jOperations operations;


    @Override
    @Transactional
    public QuickGraphResponse getGraph(String rootStableId) {
        Set<ArgumentNode> nodes = argumentRepo.getGraph(rootStableId);

        if (nodes.isEmpty()) {
            throw new RuntimeException("Node not found!");
        }

        Set<QuickEdge> edges = new HashSet<>();

        Long rootId = null;

        for (ArgumentNode n: nodes) {
            for (ArgumentNode child : n.getGraphChildren()) {
                edges.add(new QuickEdge(n.getId(), child.getId()));
            }
            if (n.getStableId().equals(rootStableId)) {
                rootId = n.getId();
            }
        }

        return new QuickGraphResponse(nodes, edges, rootId, rootStableId);
    }

    @Override
    @Transactional
    public ArgumentNode getFullDetail(String stableId) {
        return argumentRepo.getNodeRich(stableId);
    }

    @Override
    @Transactional
    public AssertionNode createAssertion(long userId, String authorStableId, String title, String qualifier, String body, Collection<Long> links) throws NodeRulesException {

        Author author = AuthorRulesUtil.loadAuthorWithSecurityCheck(userRepo, userId, authorStableId);

        AssertionBody assertionBody = new AssertionBody(title, qualifier, body, author);

        AssertionNode node = assertionBody.constructNode();

        Set<ArgumentNode> children = getAndValidateChildNodes(links);

        TwoWayUtil.updateSupportingNodes(node, children);

        operations.save(node);
        return node;
    }



    @Override
    @Transactional
    public InterpretationNode createInterpretation(long userId, String authorStableId, String title, String qualifier, String body, Long sourceId) throws NodeRulesException {

        Author author = AuthorRulesUtil.loadAuthorWithSecurityCheck(userRepo, userId, authorStableId);
        InterpretationBody interpretationBody = new InterpretationBody(title, qualifier, body, author);

        InterpretationNode node = interpretationBody.constructNode();

        if (sourceId != null) {
            SourceNode source = operations.load(SourceNode.class, sourceId);
            node.setSource(source);
        }

        operations.save(node);
        return node;
    }

    @Override
    @Transactional
    public SourceNode createSource(long userId, String authorStableId, String title, String qualifier, String url) throws NodeRulesException {

        Author author = AuthorRulesUtil.loadAuthorWithSecurityCheck(userRepo, userId, authorStableId);

        SourceBody sourceBody = new SourceBody(title, qualifier, author, url);
        SourceNode node = sourceBody.constructNode();

        operations.save(node);
        return node;
    }

    @Override
    @Transactional
    public AssertionNode editAssertion(long userId, long nodeId, String title, String qualifier, String body, Collection<Long> links) throws NodeRulesException {

        AssertionNode existingNode = operations.load(AssertionNode.class, nodeId, 2);

        AuthorRulesUtil.loadAuthorWithSecurityCheck(userRepo, userId, existingNode.getBody().author.getStableId());

        checkEditRules(existingNode);

        existingNode.getBody().setTitle(title);
        existingNode.getBody().setQualifier(qualifier);
        existingNode.getBody().setBody(body);

        Set<ArgumentNode> children = getAndValidateChildNodes(links);

        TwoWayUtil.updateSupportingNodes(existingNode, children);

        operations.save(existingNode);
        return existingNode;
    }

    private Set<ArgumentNode> getAndValidateChildNodes(Collection<Long> links) throws NodeRulesException {
        Set<ArgumentNode> children = new HashSet<>();
        for (Long id : links) {
            ArgumentNode supportingNode = argumentRepo.loadWithMajorVersion(id);
            if (supportingNode instanceof SourceNode) {
                throw new NodeRulesException("An assertion node cannot link directly to a source!");
            }
            children.add(supportingNode);
        }
        return children;
    }


    @Override
    @Transactional
    public InterpretationNode editInterpretation(long userId, long nodeId, String title, String qualifier, String body, Long sourceId) throws NodeRulesException {

        InterpretationNode existingNode = operations.load(InterpretationNode.class, nodeId, 2);

        AuthorRulesUtil.loadAuthorWithSecurityCheck(userRepo, userId, existingNode.getBody().author.getStableId());

        checkEditRules(existingNode);

        existingNode.getBody().setTitle(title);
        existingNode.getBody().setQualifier(qualifier);
        existingNode.getBody().setBody(body);

        SourceNode sourceNode = null;
        if (sourceId != null) {
            sourceNode = operations.load(SourceNode.class, sourceId);
        }

        TwoWayUtil.updateSupportingNodes(existingNode, sourceNode);

        operations.save(existingNode);
        return existingNode;
    }

    @Override
    @Transactional
    public SourceNode editSource(long userId, long nodeId, String title, String qualifier, String url) throws NodeRulesException {

        SourceNode existingNode = operations.load(SourceNode.class, nodeId, 2);

        AuthorRulesUtil.loadAuthorWithSecurityCheck(userRepo, userId, existingNode.getBody().author.getStableId());

        checkEditRules(existingNode);

        existingNode.getBody().setTitle(title);
        existingNode.getBody().setQualifier(qualifier);
        existingNode.getBody().setUrl(url);

        operations.save(existingNode);
        return existingNode;
    }

    @Override
    @Transactional
    public EditResult makeDraft(long userId, String authorStableId, long nodeId) throws NodeRulesException {

        Author author = AuthorRulesUtil.loadAuthorWithSecurityCheck(userRepo, userId, authorStableId);

        ArgumentNode existingNode = operations.load(ArgumentNode.class, nodeId, 2);

        if (!existingNode.getBody().isEditable()) {
            throw new NodeRulesException("Cannot edit this node!");
        }

        if (!existingNode.getBody().isPublic()) {
            // Already a draft.
            throw new NodeRulesException("This is already a draft, should not attempt to split off a new draft");
        }

        // Create a new draft version

        // This will set the previous version on the draft. Later, when we publish the edit,
        // this draft will copy its contents to the previous version and then be destroyed.
        ArgumentNode draftNode = existingNode.createNewDraft(author);

        operations.save(draftNode);

        EditResult result = new EditResult(draftNode);
        result.setGraph(getGraph(draftNode.getStableId()));

        return result;
    }

    @Override
    @Transactional
    public QuickGraphResponse publishNode(long userId, long nodeId) throws NotAuthorizedException, NodeRulesException {

        ArgumentNode existingNode = operations.load(ArgumentNode.class, nodeId, 2);

        if (existingNode == null) {
            throw new ResourceNotFoundException("Could not find node with id " + nodeId);
        }

        AuthorRulesUtil.loadAuthorWithSecurityCheck(userRepo, userId, existingNode.getBody().author.getStableId());

        if (existingNode.isFinalized()) {
            throw new NodeRulesException("No new changes to publish!");
        }

        ArgumentNode resultingNode = versionHelper.publish(existingNode);

        // We're pretty confident at this point that the entire tree is in memory, so don't bother hitting the db again.
        return buildGraphResponseFromNode(resultingNode);
    }

    private QuickGraphResponse buildGraphResponseFromNode(ArgumentNode node) {
        Set<ArgumentNode> nodes = new HashSet<>();
        Set<QuickEdge> edges = new HashSet<>();

        collectDescendingNodesAndEdges(node, nodes, edges);
        collectAscendingNodesAndEdges(node, nodes, edges);

        return new QuickGraphResponse(nodes, edges, node.getId(), node.getStableId());
    }

    private void collectDescendingNodesAndEdges(ArgumentNode node, Set<ArgumentNode> nodes, Set<QuickEdge> edges) {
        nodes.add(node);
        if (node.getGraphChildren() != null) {
            for (ArgumentNode child : node.getGraphChildren()) {
                edges.add(new QuickEdge(node.getId(), child.getId()));
                collectDescendingNodesAndEdges(child, nodes, edges);
            }
        }
    }

    private void collectAscendingNodesAndEdges(ArgumentNode node, Set<ArgumentNode> nodes, Set<QuickEdge> edges) {
        nodes.add(node);
        if (node.getDependentNodes() != null) {
            for (ArgumentNode parent : node.getDependentNodes()) {
                edges.add(new QuickEdge(parent.getId(), node.getId()));
                collectAscendingNodesAndEdges(parent, nodes, edges);
            }
        }
    }

    @Override
    @Transactional
    public Set<ArgumentNode> getNodesInMajorVersion(long majorVersionId) {
        return argumentRepo.getNodesInMajorVersion(majorVersionId);
    }

    @Override
    public Set<ArgumentNode> getRootNodes() {
        return argumentRepo.getRootNodesRich();
    }

    @Override
    public Set<ArgumentNode> getDraftNodes(long userId, String authorStableId) throws NodeRulesException {
        AuthorRulesUtil.loadAuthorWithSecurityCheck(userRepo, userId, authorStableId);
        return argumentRepo.getDraftNodesRich(authorStableId);
    }

    @Override
    public Set<ArgumentNode> getConsumerNodes(long nodeId) {
        return argumentRepo.getConsumerNodes(nodeId);
    }

    @Override
    public Set<ArgumentNode> getConsumerNodesIncludingDrafts(long userId, long nodeId) {
        return argumentRepo.getConsumerNodes(nodeId, userId);
    }

    @Override
    public Set<ArgumentNode> getNodesPublishedByAuthor(String authorStableId) {
        return argumentRepo.getNodesOriginallyAuthoredByUser(authorStableId);
    }

    @Override
    public ArgumentNode getEditHistory(String stableId) {
        ArgumentNode editHistory = argumentRepo.getEditHistory(stableId);
        return editHistory;
    }

    @Override
    public void discardDraft(Long userId, String stableId) throws NodeRulesException {

        ArgumentNode draftNode = argumentRepo.getNodeRich(stableId);

        AuthorRulesUtil.loadAuthorWithSecurityCheck(userRepo, userId, draftNode.getBody().author.getStableId());

        operations.delete(draftNode);
        operations.delete(draftNode.getBody());
        TwoWayUtil.forgetNode(draftNode);
    }

    private void checkEditRules(ArgumentNode existingNode) throws NodeRulesException {
        if (!existingNode.getBody().isEditable()) {
            throw new NodeRulesException("Cannot edit this node!");
        }

        if (existingNode.getBody().isPublic()) {
            throw new NodeRulesException("Must split off a private draft before editing.");
        }
    }


}
