package com.finki.vladislavangelovski.ai_service.qa;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class PromptTemplates {
    private final String claimSystem;
    private final String claimUser;
    private final String questionSystem;
    private final String questionUser;

    public PromptTemplates(ResourceLoader resourceLoader) {
        this.claimSystem = load(resourceLoader, "classpath:prompts/claim-system.txt");
        this.claimUser = load(resourceLoader, "classpath:prompts/claim-user.txt");
        this.questionSystem = load(resourceLoader, "classpath:prompts/question-system.txt");
        this.questionUser = load(resourceLoader, "classpath:prompts/question-user.txt");
    }

    public String claimSystem() {
        return claimSystem;
    }

    public String claimUser() {
        return claimUser;
    }

    public String questionSystem() {
        return questionSystem;
    }

    public String questionUser() {
        return questionUser;
    }

    private static String load(ResourceLoader resourceLoader,
                               String location) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load prompt template: " + location, ex);
        }
    }
}
