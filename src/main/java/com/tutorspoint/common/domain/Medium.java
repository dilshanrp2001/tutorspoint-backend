package com.tutorspoint.common.domain;

/**
 * The language a class is taught in (PRD: medium of instruction).
 *
 * <p>Deliberately not a reference table. The set is closed by the education system rather
 * than by us, it never grows, and the display name for each value is already a translatable
 * message key — so a table would buy nothing but a join. Contrast {@code Subject} and
 * {@code Area}, which an administrator genuinely has to extend.
 *
 * <p>Distinct from {@link Language}, which is the language of the <em>interface</em>. A tutor
 * whose app is in English may well teach in Sinhala.
 */
public enum Medium {

    SINHALA,

    TAMIL,

    ENGLISH
}
