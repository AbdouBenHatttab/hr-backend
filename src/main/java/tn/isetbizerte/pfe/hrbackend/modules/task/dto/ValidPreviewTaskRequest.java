package tn.isetbizerte.pfe.hrbackend.modules.task.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = PreviewTaskRequestValidator.class)
@Target(ElementType.TYPE)
@Retention(RUNTIME)
public @interface ValidPreviewTaskRequest {
    String message() default "Task preview payload is invalid";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
