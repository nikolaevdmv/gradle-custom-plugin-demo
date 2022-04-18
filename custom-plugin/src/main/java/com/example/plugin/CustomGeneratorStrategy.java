package com.example.plugin;

import org.jooq.codegen.DefaultGeneratorStrategy;
import org.jooq.meta.Definition;

public class CustomGeneratorStrategy extends DefaultGeneratorStrategy {

    @Override
    public String getJavaClassName(Definition definition, Mode mode) {

        if (mode == Mode.POJO) {
            return super.getJavaClassName(definition, mode) + "Pojo";
        } else {
            return super.getJavaClassName(definition, mode);
        }
    }
}
