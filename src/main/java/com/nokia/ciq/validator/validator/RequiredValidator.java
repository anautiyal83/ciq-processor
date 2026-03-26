package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.ConditionalRequired;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;

/**
 * Validates {@code required} and {@code requiredWhen} rules.
 *
 * <p>This is a <em>gatekeeper</em> validator: if it produces an error the engine
 * stops running further validators for that cell (no point checking allowed values
 * or patterns on a blank mandatory field).
 */
public class RequiredValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        boolean isBlank = value == null || value.trim().isEmpty();

        if (rule.isRequired() && isBlank) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Column '" + colName + "' is required but is empty"));
        }

        ConditionalRequired rw = rule.getRequiredWhen();
        if (rw != null && isBlank) {
            String triggerVal = row.get(rw.getColumn());
            if (rw.getValue().equalsIgnoreCase(triggerVal)) {
                return Collections.singletonList(new ValidationError(
                        row.getRowNumber(), colName, value,
                        "Column '" + colName + "' is required when "
                        + rw.getColumn() + "=" + rw.getValue() + " but is empty"));
            }
        }

        return Collections.emptyList();
    }

    @Override
    public boolean isGatekeeper() {
        return true;
    }
}
