package source.config;

import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Arrays;

/**
 * Keeps the staged KFE runtime from publishing Core HTTP controllers while it
 * still reuses the root Spring Boot executable.
 */
public final class KfeProfileCoreControllerExclusionFilter implements TypeFilter, EnvironmentAware {

    private static final String KFE_PACKAGE_PREFIX = "source.kfe.";
    private static final String CONTROLLER_ANNOTATION = Controller.class.getName();
    private static final String REST_CONTROLLER_ANNOTATION = RestController.class.getName();

    private boolean kfeProfileActive;

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.kfeProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("kfe");
    }

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
        if (!kfeProfileActive) {
            return false;
        }

        String className = metadataReader.getClassMetadata().getClassName();
        if (className.startsWith(KFE_PACKAGE_PREFIX)) {
            return false;
        }

        AnnotationMetadata annotations = metadataReader.getAnnotationMetadata();
        return annotations.hasAnnotation(CONTROLLER_ANNOTATION)
                || annotations.hasAnnotation(REST_CONTROLLER_ANNOTATION)
                || annotations.hasMetaAnnotation(CONTROLLER_ANNOTATION);
    }
}
