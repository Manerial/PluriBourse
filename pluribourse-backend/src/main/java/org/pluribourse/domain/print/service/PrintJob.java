package org.pluribourse.domain.print.service;

import org.pluribourse.domain.print.entity.Printer;

/**
 * Contract consumed by future printing stories (thermal labels, PDF deposit slip) — deliberately
 * free of any content-generation logic in this story.
 */
public interface PrintJob {

    void execute(Printer printer);
}
