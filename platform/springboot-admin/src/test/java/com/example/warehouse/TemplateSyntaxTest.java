package com.example.warehouse;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;

class TemplateSyntaxTest {
    @Test
    void parsesTaskOperationTemplates() throws Exception {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "/templates");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        configuration.getTemplate("tasks.ftl");
        configuration.getTemplate("table_ops.ftl");
        configuration.getTemplate("replay.ftl");
    }
}
