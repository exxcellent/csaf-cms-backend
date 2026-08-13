package de.bsi.secvisogram.csaf_cms_backend.json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public class RemoveIdHelper {

    public static final String COMMNENT_NODE_ID = "nodeId";

    public static void removeCommentIds(JsonNode jsonNode) {

        removeIds(jsonNode, COMMNENT_NODE_ID);
    }

    public static void removeIds(JsonNode jsonNode, String idName) {
        if (jsonNode.isArray()) {
            for (JsonNode arrayItem : jsonNode) {
                removeIds(arrayItem, idName);
            }
        } else if (jsonNode.isObject()) {
            for (JsonNode field : jsonNode) {
                removeIds(field, idName);
            }
            ((ObjectNode) jsonNode).remove(idName);
        }
    }

    public static void removeCommentIds(com.fasterxml.jackson.databind.JsonNode csaf) {

        removeIds(csaf, COMMNENT_NODE_ID);
    }

    private static void removeIds(com.fasterxml.jackson.databind.JsonNode jsonNode, String commnentNodeId) {
        if (jsonNode.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode arrayItem : jsonNode) {
                removeIds(arrayItem, commnentNodeId);
            }
        } else if (jsonNode.isObject()) {
            for (com.fasterxml.jackson.databind.JsonNode field : jsonNode) {
                removeIds(field, commnentNodeId);
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) jsonNode).remove(commnentNodeId);
        }
    }
}
