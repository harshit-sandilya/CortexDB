package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to ingest a large document into CortexDB.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestDocumentRequest {

    private String uid;
    private String documentTitle;
    private String documentText;

    public IngestDocumentRequest() {
    }

    public IngestDocumentRequest(String uid, String documentTitle, String documentText) {
        this.uid = uid;
        this.documentTitle = documentTitle;
        this.documentText = documentText;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public String getDocumentText() {
        return documentText;
    }

    public void setDocumentText(String documentText) {
        this.documentText = documentText;
    }
}
