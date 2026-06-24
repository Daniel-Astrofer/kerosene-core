package source;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import source.config.KfeProfileCoreControllerExclusionFilter;

@SpringBootApplication
@ComponentScan(
        basePackages = "source",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "source\\.kfe\\..*"),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = KfeProfileCoreControllerExclusionFilter.class)
        })
@EntityScan(basePackages = {
        "source.auth.model.entity",
        "source.notification.model.entity"
})
@EnableJpaRepositories(basePackages = {
        "source.auth.application.infra.persistence.jpa",
        "source.notification.repository"
})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
