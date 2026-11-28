package com.jitendra.ai.sessionvault.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "ApiKeyAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Session Vault API")
                        .version("1.0.0")
                        .description("""
                                **AI Session Vault** — Local MCP memory server and conversation persistence platform.
                                
                                Stores Antigravity / GitHub Copilot chat sessions in PostgreSQL and exposes them
                                as MCP tools (`vault_search_messages`, `vault_list_sessions`, `vault_get_session`,
                                `vault_ingest_transcript`, `vault_export_session`, `vault_save_message`).
                                
                                ### Authentication
                                All endpoints (except `/actuator/health`) require the `X-API-KEY` header.
                                Configured via the `VAULT_API_KEY` environment variable.
                                """)
                        .contact(new Contact()
                                .name("Jitendra")
                                .url("https://github.com/jitendra"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(API_KEY_SCHEME,
                                new SecurityScheme()
                                        .name("X-API-KEY")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("API key configured via the `VAULT_API_KEY` environment variable")));
    }
}
