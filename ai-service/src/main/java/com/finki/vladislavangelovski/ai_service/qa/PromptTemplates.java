package com.finki.vladislavangelovski.ai_service.qa;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class PromptTemplates {
  private final String questionSystem;
  private final String questionUser;

  public PromptTemplates(ResourceLoader resourceLoader) {
    this.questionSystem = load(resourceLoader, "classpath:prompts/question-system.txt");
    this.questionUser = load(resourceLoader, "classpath:prompts/question-user.txt");
  }

  public String questionSystem() {
    return questionSystem;
  }

  public String questionUser() {
    return questionUser;
  }

  private static String load(ResourceLoader resourceLoader, String location) {
    Resource resource = resourceLoader.getResource(location);
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load prompt template: " + location, ex);
    }
  }
}
