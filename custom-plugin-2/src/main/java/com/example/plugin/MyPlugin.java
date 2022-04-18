package com.example.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class MyPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().register(
                "generateJooqClasses",
                JooqClassesGenerationTask.class
        );
    }
}
