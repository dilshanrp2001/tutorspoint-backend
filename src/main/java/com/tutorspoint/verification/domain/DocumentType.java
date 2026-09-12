package com.tutorspoint.verification.domain;

/**
 * What a tutor is submitting (FR-T7). The reviewer's queue is filtered and prioritised by
 * this, and the tutor's own list is labelled with it.
 *
 * <p>{@link #OTHER} exists because the alternative is a tutor with a genuine credential we did
 * not anticipate having nowhere to put it, and a reviewer would rather read a title than not
 * receive the document at all.
 */
public enum DocumentType {

    /** National Identity Card - the identity check every other document rests on. */
    NIC,

    DEGREE_CERTIFICATE,

    TEACHING_CERTIFICATE,

    OTHER
}
