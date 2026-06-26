package de.bsi.secvisogram.csaf_cms_backend.service;

import de.bsi.secvisogram.csaf_cms_backend.entity.AdvisoryEntity;
import de.bsi.secvisogram.csaf_cms_backend.entity.AdvisoryVersionEntity;
import de.bsi.secvisogram.csaf_cms_backend.entity.AuditTrailCommentEntity;
import de.bsi.secvisogram.csaf_cms_backend.entity.AuditTrailDocumentEntity;
import de.bsi.secvisogram.csaf_cms_backend.entity.AuditTrailWorkflowEntity;
import de.bsi.secvisogram.csaf_cms_backend.entity.CommentEntity;
import de.bsi.secvisogram.csaf_cms_backend.exception.CsafException;
import de.bsi.secvisogram.csaf_cms_backend.json.AdvisoryWrapper;
import de.bsi.secvisogram.csaf_cms_backend.json.CommentWrapper;
import de.bsi.secvisogram.csaf_cms_backend.model.ChangeType;
import de.bsi.secvisogram.csaf_cms_backend.rest.response.AdvisoryInformationResponse;
import de.bsi.secvisogram.csaf_cms_backend.rest.response.AnswerInformationResponse;
import de.bsi.secvisogram.csaf_cms_backend.rest.response.CommentInformationResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Utility class for converting between JPA entities and the domain wrapper objects
 * used by the service layer. Bridges the gap between the JPA persistence model and
 * the JSON-centric wrapper model used during the migration from CouchDB.
 *
 * <p>The entity layer stores CSAF content as {@code com.fasterxml.jackson.databind.JsonNode}
 * (Jackson 2, required by Hibernate), while the wrapper layer uses
 * {@code tools.jackson.databind.JsonNode} (Jackson 3, bundled with Spring Boot 4).
 * Conversion between these types goes through a JSON string serialisation round-trip.</p>
 */
public final class EntityConverter {

    private EntityConverter() {
        // utility class
    }

    // -------------------------------------------------------------------------
    // Advisory conversions
    // -------------------------------------------------------------------------

    /**
     * Build an {@link AdvisoryWrapper} from a persisted {@link AdvisoryEntity}.
     *
     * <p>The wrapper node is constructed to be structurally identical to what
     * {@code AdvisoryWrapper.createFromCouchDb} would produce, with synthetic
     * {@code _id} and {@code _rev} fields populated from the entity's primary key
     * and optimistic-lock version respectively.</p>
     *
     * @param entity the advisory entity to convert
     * @return an AdvisoryWrapper backed by the entity data
     * @throws IOException   if the CSAF JSON cannot be serialised/parsed
     * @throws CsafException if the resulting node is not of type Advisory
     */
    public static AdvisoryWrapper toWrapper(AdvisoryEntity entity) throws IOException, CsafException {

        tools.jackson.databind.ObjectMapper mapper = new JsonMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("_id", entity.getId().toString());
        node.put("_rev", String.valueOf(entity.getVersion()));
        node.put("type", "Advisory");
        node.put("workflowState", entity.getWorkflowState());
        node.put("owner", entity.getOwner());
        // Bridge Jackson 2 -> Jackson 3 via string round-trip
        if (entity.getCsaf() != null) {
            JsonNode csafNode = mapper.readValue(entity.getCsaf().toString(), JsonNode.class);
            node.set("csaf", csafNode);
        }
        node.put("versioningType", entity.getVersioningType());
        if (entity.getLastMajorVersion() != null) {
            node.put("lastMajorVersion", entity.getLastMajorVersion());
        }
        if (entity.getTmpTrackingId() != null) {
            node.put("tmpTrackingId", entity.getTmpTrackingId());
        }
        if (entity.getCreatedAt() != null) {
            node.put("createdAt", entity.getCreatedAt().toString());
        }

        String json = node.toString();
        return AdvisoryWrapper.createFromCouchDb(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Build an {@link AdvisoryWrapper} from a persisted {@link AdvisoryVersionEntity}.
     *
     * @param entity the advisory version entity
     * @return an AdvisoryWrapper backed by the version entity data
     * @throws IOException   if the CSAF JSON cannot be serialised/parsed
     * @throws CsafException if the resulting node is not of acceptable type
     */
    public static AdvisoryWrapper toWrapper(AdvisoryVersionEntity entity) throws IOException, CsafException {

        tools.jackson.databind.ObjectMapper mapper = new JsonMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("_id", entity.getId().toString());
        node.put("type", "AdvisoryVersion");
        node.put("workflowState", entity.getWorkflowState());
        node.put("owner", entity.getOwner());
        if (entity.getCsaf() != null) {
            JsonNode csafNode = mapper.readValue(entity.getCsaf().toString(), JsonNode.class);
            node.set("csaf", csafNode);
        }
        node.put("versioningType", entity.getVersioningType());
        if (entity.getLastMajorVersion() != null) {
            node.put("lastMajorVersion", entity.getLastMajorVersion());
        }
        if (entity.getAdvisory() != null) {
            node.put("advisoryReference", entity.getAdvisory().getId().toString());
        }
        if (entity.getCreatedAt() != null) {
            node.put("createdAt", entity.getCreatedAt().toString());
        }

        String json = node.toString();
        return AdvisoryWrapper.createFromCouchDb(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Populate an {@link AdvisoryEntity} from an {@link AdvisoryWrapper}.
     *
     * <p>When {@code existing} is non-null its identity and creation timestamp are
     * preserved; only mutable fields are overwritten. When {@code existing} is null
     * a new entity is created. The entity ID is set only when the wrapper carries one.</p>
     *
     * @param wrapper  the advisory wrapper to read from
     * @param existing the entity to update, or {@code null} to create a new one
     * @return the populated entity (never null)
     */
    public static AdvisoryEntity toEntity(AdvisoryWrapper wrapper, AdvisoryEntity existing) {

        AdvisoryEntity entity = (existing != null) ? existing : new AdvisoryEntity();
        if (wrapper.getAdvisoryId() != null) {
            entity.setId(UUID.fromString(wrapper.getAdvisoryId()));
        }
        entity.setWorkflowState(wrapper.getWorkflowStateString());
        entity.setOwner(wrapper.getOwner());
        // Bridge Jackson 3 -> Jackson 2 via string round-trip
        if (wrapper.getCsaf() != null) {
            entity.setCsaf(toFasterxmlNode(wrapper.getCsaf()));
        }
        entity.setVersioningType(wrapper.getVersioningType());
        entity.setLastMajorVersion(wrapper.getLastVersion());
        entity.setTmpTrackingId(wrapper.getTempTrackingIdInFromMeta());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        return entity;
    }

    /**
     * Build an {@link AdvisoryVersionEntity} from an advisory version snapshot wrapper.
     *
     * @param versionWrapper the advisory version wrapper
     * @param parentEntity   the parent advisory entity this version belongs to
     * @return a populated version entity
     */
    public static AdvisoryVersionEntity toVersionEntity(AdvisoryWrapper versionWrapper, AdvisoryEntity parentEntity) {

        AdvisoryVersionEntity entity = new AdvisoryVersionEntity();
        entity.setAdvisory(parentEntity);
        entity.setWorkflowState(versionWrapper.getWorkflowStateString());
        entity.setOwner(versionWrapper.getOwner());
        if (versionWrapper.getCsaf() != null) {
            entity.setCsaf(toFasterxmlNode(versionWrapper.getCsaf()));
        }
        entity.setVersioningType(versionWrapper.getVersioningType());
        entity.setLastMajorVersion(versionWrapper.getLastVersion());
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    // -------------------------------------------------------------------------
    // Comment conversions
    // -------------------------------------------------------------------------

    /**
     * Populate a {@link CommentEntity} from a {@link CommentWrapper}.
     *
     * @param wrapper        the comment wrapper
     * @param advisoryEntity the advisory this comment belongs to
     * @param existing       the entity to update, or {@code null} to create a new one
     * @return the populated entity
     */
    public static CommentEntity toEntity(CommentWrapper wrapper, AdvisoryEntity advisoryEntity,
            CommentEntity existing) {

        CommentEntity entity = (existing != null) ? existing : new CommentEntity();
        if (wrapper.getCommentId() != null) {
            entity.setId(UUID.fromString(wrapper.getCommentId()));
        }
        entity.setAdvisory(advisoryEntity);
        entity.setOwner(wrapper.getOwner());
        entity.setCommentText(wrapper.getText());
        entity.setCsafNodeId(wrapper.getCsafNodeId());
        entity.setFieldName(wrapper.getFieldName());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        return entity;
    }

    /**
     * Build an {@link AdvisoryInformationResponse} from an {@link AdvisoryEntity}
     * without loading comments. Revision is set to the entity's optimistic-lock version.
     *
     * @param entity the advisory entity
     * @return a populated response
     */
    public static AdvisoryInformationResponse toAdvisoryInfo(AdvisoryEntity entity) {

        tools.jackson.databind.ObjectMapper mapper = new JsonMapper();
        AdvisoryInformationResponse response = new AdvisoryInformationResponse(entity.getId().toString());
        response.setRevision(String.valueOf(entity.getVersion()));
        response.setWorkflowState(entity.getWorkflowState());
        response.setOwner(entity.getOwner());
        if (entity.getCsaf() != null) {
            // Extract title and trackingId from CSAF JSON
            try {
                JsonNode csafNode = mapper.readValue(entity.getCsaf().toString(), JsonNode.class);
                JsonNode titleNode = csafNode.at("/document/title");
                if (!titleNode.isMissingNode()) {
                    response.setTitle(titleNode.asString());
                }
                JsonNode trackingIdNode = csafNode.at("/document/tracking/id");
                if (!trackingIdNode.isMissingNode()) {
                    response.setDocumentTrackingId(trackingIdNode.asString());
                }
                JsonNode releaseDateNode = csafNode.at("/document/tracking/current_release_date");
                if (!releaseDateNode.isMissingNode()) {
                    response.setCurrentReleaseDate(releaseDateNode.asString());
                }
            } catch (Exception e) {
                // non-fatal: leave title and tracking id blank
            }
        }
        return response;
    }

    /**
     * Build an {@link AdvisoryInformationResponse} from an {@link AdvisoryVersionEntity}.
     * Version snapshots have no optimistic-lock version field, so revision is left null.
     *
     * @param entity the advisory version entity
     * @return a populated response
     */
    public static AdvisoryInformationResponse toAdvisoryVersionInfo(AdvisoryVersionEntity entity) {

        tools.jackson.databind.ObjectMapper mapper = new JsonMapper();
        String id = (entity.getAdvisory() != null)
                ? entity.getAdvisory().getId().toString() : entity.getId().toString();
        AdvisoryInformationResponse response = new AdvisoryInformationResponse(id);
        response.setWorkflowState(entity.getWorkflowState());
        response.setOwner(entity.getOwner());
        if (entity.getCsaf() != null) {
            try {
                JsonNode csafNode = mapper.readValue(entity.getCsaf().toString(), JsonNode.class);
                JsonNode titleNode = csafNode.at("/document/title");
                if (!titleNode.isMissingNode()) {
                    response.setTitle(titleNode.asString());
                }
                JsonNode trackingIdNode = csafNode.at("/document/tracking/id");
                if (!trackingIdNode.isMissingNode()) {
                    response.setDocumentTrackingId(trackingIdNode.asString());
                }
                JsonNode releaseDateNode = csafNode.at("/document/tracking/current_release_date");
                if (!releaseDateNode.isMissingNode()) {
                    response.setCurrentReleaseDate(releaseDateNode.asString());
                }
            } catch (Exception e) {
                // non-fatal: leave title and tracking id blank
            }
        }
        return response;
    }

    /**
     * Build a {@link CommentInformationResponse} from a {@link CommentEntity}.
     *
     * @param entity the comment entity
     * @return a populated response
     */
    public static CommentInformationResponse toCommentInfo(CommentEntity entity) {

        String advisoryId = (entity.getAdvisory() != null) ? entity.getAdvisory().getId().toString() : null;
        CommentInformationResponse response = new CommentInformationResponse(
                entity.getId().toString(),
                advisoryId,
                entity.getCsafNodeId(),
                entity.getOwner()
        );
        if (entity.getAnswerTo() != null) {
            response.setAnswerTo(entity.getAnswerTo().getId().toString());
        }
        return response;
    }

    /**
     * Build an {@link AnswerInformationResponse} from a {@link CommentEntity}
     * that represents an answer (i.e. its {@code answerTo} is set).
     *
     * @param entity the answer entity
     * @return a populated response
     */
    public static AnswerInformationResponse toAnswerInfo(CommentEntity entity) {

        String answerTo = (entity.getAnswerTo() != null) ? entity.getAnswerTo().getId().toString() : null;
        return new AnswerInformationResponse(entity.getId().toString(), answerTo, entity.getOwner());
    }

    // -------------------------------------------------------------------------
    // Audit trail conversions
    // -------------------------------------------------------------------------

    /**
     * Build an {@link AuditTrailDocumentEntity} from audit trail wrapper data.
     *
     * @param advisoryEntity the advisory this audit trail entry belongs to
     * @param user           the user who triggered the change
     * @param changeType     the type of change
     * @param diffPatch      the JSON-Patch diff as a tools.jackson JsonNode (may be null)
     * @param oldDocVersion  the document version before the change
     * @param docVersion     the document version after the change
     * @return a populated audit trail document entity
     */
    public static AuditTrailDocumentEntity toAuditTrailDocumentEntity(
            AdvisoryEntity advisoryEntity,
            String user,
            ChangeType changeType,
            JsonNode diffPatch,
            String oldDocVersion,
            String docVersion) {

        AuditTrailDocumentEntity entity = new AuditTrailDocumentEntity();
        entity.setAdvisory(advisoryEntity);
        entity.setCreatedAt(Instant.now());
        entity.setUser(user);
        entity.setChangeType(changeType.name());
        entity.setOldDocVersion(oldDocVersion);
        entity.setDocVersion(docVersion);
        if (diffPatch != null) {
            entity.setDiff(toFasterxmlNode(diffPatch));
        }
        return entity;
    }

    /**
     * Build an {@link AuditTrailWorkflowEntity} from audit trail wrapper data.
     *
     * @param advisoryEntity   the advisory this audit trail entry belongs to
     * @param user             the user who triggered the change
     * @param oldState         the workflow state before the transition
     * @param newState         the workflow state after the transition
     * @param oldDocVersion    the document version before the change
     * @param docVersion       the document version after the change
     * @return a populated audit trail workflow entity
     */
    public static AuditTrailWorkflowEntity toAuditTrailWorkflowEntity(
            AdvisoryEntity advisoryEntity,
            String user,
            String oldState,
            String newState,
            String oldDocVersion,
            String docVersion) {

        AuditTrailWorkflowEntity entity = new AuditTrailWorkflowEntity();
        entity.setAdvisory(advisoryEntity);
        entity.setCreatedAt(Instant.now());
        entity.setUser(user);
        entity.setChangeType(ChangeType.Update.name());
        entity.setOldState(oldState);
        entity.setNewState(newState);
        entity.setOldDocVersion(oldDocVersion);
        entity.setDocVersion(docVersion);
        return entity;
    }

    /**
     * Build an {@link AuditTrailCommentEntity} from comment audit trail data.
     *
     * @param commentEntity the comment this audit trail entry belongs to
     * @param user          the user who triggered the change
     * @param changeType    the type of change
     * @param commentText   the comment text at the time of the change (may be null)
     * @return a populated audit trail comment entity
     */
    public static AuditTrailCommentEntity toAuditTrailCommentEntity(
            CommentEntity commentEntity,
            String user,
            ChangeType changeType,
            String commentText) {

        AuditTrailCommentEntity entity = new AuditTrailCommentEntity();
        entity.setComment(commentEntity);
        entity.setCreatedAt(Instant.now());
        entity.setUser(user);
        entity.setChangeType(changeType.name());
        entity.setCommentText(commentText);
        return entity;
    }

    // -------------------------------------------------------------------------
    // Internal Jackson 2/3 bridge helpers
    // -------------------------------------------------------------------------

    /**
     * Convert a {@code tools.jackson} (Jackson 3) {@link JsonNode} to a
     * {@code com.fasterxml.jackson} (Jackson 2) {@link com.fasterxml.jackson.databind.JsonNode}
     * by serialising to a JSON string and re-parsing.
     *
     * @param source the Jackson 3 node
     * @return the equivalent Jackson 2 node, or {@code null} if source is null
     */
    static com.fasterxml.jackson.databind.JsonNode toFasterxmlNode(JsonNode source) {

        if (source == null) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper fasterxmlMapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return fasterxmlMapper.readTree(source.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to convert Jackson 3 node to Jackson 2 node", e);
        }
    }

    /**
     * Convert a {@code com.fasterxml.jackson} (Jackson 2) {@link com.fasterxml.jackson.databind.JsonNode}
     * to a {@code tools.jackson} (Jackson 3) {@link JsonNode} by serialising to a JSON string
     * and re-parsing.
     *
     * @param source the Jackson 2 node
     * @return the equivalent Jackson 3 node, or {@code null} if source is null
     */
    static JsonNode toToolsJacksonNode(com.fasterxml.jackson.databind.JsonNode source) {

        if (source == null) {
            return null;
        }
        try {
            tools.jackson.databind.ObjectMapper mapper = new JsonMapper();
            return mapper.readValue(source.toString(), JsonNode.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to convert Jackson 2 node to Jackson 3 node", e);
        }
    }
}
