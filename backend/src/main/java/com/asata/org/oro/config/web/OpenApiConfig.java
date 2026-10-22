package com.asata.org.oro.config.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI on /doc: the bearer token of a product, and the organization header of the Umoja requests */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI oroOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Oro Messaging API")
                        .description("Messaging shared by Oro (personal) and Umoja (organizations). Bearer: an access "
                                + "token of the product. Umoja requests name their organization in x-organization-id.")
                        .version("1"))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("integration", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("x-integration-key")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }

    /** the Actor parameter of the controllers comes from the token and this header */
    @Bean
    OperationCustomizer organizationHeader() {
        return (operation, handlerMethod) -> {
            boolean takesActor = java.util.Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(parameter -> parameter.getParameterType() == com.asata.org.oro.context.Actor.class);
            if (takesActor) {
                operation.addParametersItem(new HeaderParameter().name(ActorResolver.ORGANIZATION_HEADER)
                        .description("Umoja only: the organization of the request (one the user has access to)")
                        .required(false).schema(new StringSchema().format("uuid")));
            }
            if (handlerMethod.getBeanType().getSimpleName().startsWith("Integration")) {
                operation.setSecurity(java.util.List.of(new SecurityRequirement().addList("integration")));
            }
            return operation;
        };
    }
}
