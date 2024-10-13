package com.fdu.msacs.dfs.metanode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fdu.msacs.dfs.metanode.mdb.BlockNode;
import com.fdu.msacs.dfs.metanode.mdb.BlockNodeMappingRepo;

@Service
public class MetaNodeService {
    private static final Logger logger = LoggerFactory.getLogger(MetaNodeService.class);
    
    private Map<String, Set<String>> fileNodeMapping;
    private Map<String, Set<String>> nodeFileMapping;
    private Map<String, DfsNode> registeredNodes;
    private int currentNodeIndex = 0;
    private BlockNodeMappingRepo blockNodeMapping;
    
    public MetaNodeService(BlockNodeMappingRepo blockNodeMapping) {
    	this.fileNodeMapping = new HashMap<String, Set<String>>();
    	this.nodeFileMapping = new HashMap<String, Set<String>>();
    	this.registeredNodes = new HashMap<String, DfsNode>();
    	this.blockNodeMapping = blockNodeMapping;
    }
    
    public String registerNode(DfsNode node) {
    	String nodeUrl = node.getContainerUrl();
        if (registeredNodes.get(nodeUrl) == null) {
            registeredNodes.put(nodeUrl, node);            
            logger.info("A new node registered: {}", nodeUrl);
            return "Node registered: " + nodeUrl;
        } else {
            logger.warn("Node already registered: {}", nodeUrl);
            return "Node already registered: " + nodeUrl; // Conflict status
        }
    }

    public String registerFileLocation(String filename, String nodeUrl) {
        // Update or create the FileNode entry
        Set<String> nodeList = fileNodeMapping.get(filename);
        if (nodeList==null) {
        	nodeList = new HashSet<String>();
        	fileNodeMapping.put(filename, nodeList);
        }
        nodeList.add(nodeUrl);
        
        Set<String> fileList = nodeFileMapping.get(nodeUrl);
        if (fileList==null) {
        	fileList = new HashSet<String>();
        	nodeFileMapping.put(nodeUrl, fileList);
        }
        fileList.add(filename);
        
        logger.info("File {} registered to : {}", filename, nodeUrl);
        return "File location registered: " + filename + " on " + nodeUrl;
    }
    
    public String registerBlockLocation(String hash, String nodeUrl) {
		logger.debug("MetaService: registerBlockLocation: {}->{}", hash, nodeUrl);
		
    	BlockNode blockNode = blockNodeMapping.findByHash(hash);
    	if (blockNode == null) {
    		blockNode = new BlockNode();
    		blockNode.setHash(hash);
    		blockNode.getNodeUrls().add(nodeUrl);
    	}
    	blockNodeMapping.save(blockNode);
        logger.debug("Block {} registered to : {}", hash, nodeUrl);
        logger.debug("Current blockNodeMapping: {}", blockNodeMapping.findAll());
        return "Block location registered: " + hash + " on " + nodeUrl;
    }

    public List<String> getNodesForFile(String filename) {
        Set<String> nodes = fileNodeMapping.getOrDefault(filename, new HashSet<String>());
        List<String> nodeUrls = new ArrayList<>(nodes);
        logger.info("Searching the node for file {}, return {}", filename, nodeUrls);
        return nodeUrls;
    }

    
    public List<DfsNode> getReplicationNodes(String filename, String requestingNodeUrl) {
        logger.info("/metadata/get-replication-nodes called for filename: {} and requestingNodeUrl: {}", filename, requestingNodeUrl);

        // Step 1: Create a list of available nodes and filter out the requesting node
        List<DfsNode> availableNodes = registeredNodes.values().stream()
            .filter(node -> !node.getContainerUrl().equals(requestingNodeUrl))
            .toList();

        // Step 2: If more than 2 nodes remain, limit the list to 2 nodes
        if (availableNodes.size() > 2) {
            availableNodes = availableNodes.subList(0, 2);
        }

        logger.info("Available nodes after filtering and limiting: {}", availableNodes);
        return availableNodes;
    }


    public List<DfsNode> getRegisteredNodes() {
        return new ArrayList<DfsNode>(registeredNodes.values());
    }

    public List<String> getNodeFiles(String nodeUrl) {
    	Set<String> files = nodeFileMapping.getOrDefault(nodeUrl, new HashSet<String>());
        return new ArrayList<String>(files);
    }

    public void clearCache() {
        fileNodeMapping.clear();
        nodeFileMapping.clear();
        
        logger.info("Cache cleared.");
    }

    public void clearRegisteredNodes() {
        registeredNodes.clear();
        logger.info("Registered nodes cleared.");
    }

	public List<String> getFileNodes(String filename) {
		Set<String> nodes = fileNodeMapping.getOrDefault(filename, new HashSet<String>());
		return new ArrayList<String>(nodes);
	}
	
    public DfsNode selectNodeForUpload() {
        if (registeredNodes.isEmpty()) {
            return null;
        }

        List<DfsNode> nodeList = new ArrayList<>(registeredNodes.values());        
        DfsNode selectedNode = nodeList.get(currentNodeIndex);

        // Update currentNodeIndex based on the total weight
        currentNodeIndex = (currentNodeIndex + 1) % nodeList.size();

        return selectedNode;
    }

}
