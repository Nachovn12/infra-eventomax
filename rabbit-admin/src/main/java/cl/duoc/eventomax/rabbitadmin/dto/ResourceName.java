package cl.duoc.eventomax.rabbitadmin.dto;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.ReportAsSingleViolation;
import java.lang.annotation.*;
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*")
@ReportAsSingleViolation
public @interface ResourceName {
    String message() default "Resource name must contain 1-128 ASCII letters, digits, dots, underscores or hyphens";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
