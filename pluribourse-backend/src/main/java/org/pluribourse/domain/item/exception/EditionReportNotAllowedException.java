package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EditionReportNotAllowedException extends BusinessException {

    public EditionReportNotAllowedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "edition-report-not-allowed",
                "The edition summary report is only available once the Sale phase has ended.");
    }
}
