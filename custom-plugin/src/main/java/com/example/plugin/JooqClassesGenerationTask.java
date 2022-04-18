package com.example.plugin;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.*;
import org.postgresql.Driver;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Properties;

public class JooqClassesGenerationTask extends DefaultTask {

    @TaskAction
    void run() throws Exception {
        disableRyukContainer();
        PostgreSQLContainer postgreSQLContainer = createContainer();

        try {
            postgreSQLContainer.start();

            Properties connectionProps = new Properties();
            connectionProps.put("user", postgreSQLContainer.getUsername());
            connectionProps.put("password", postgreSQLContainer.getPassword());

            DriverManager.registerDriver(new Driver());

            try (Connection connection = DriverManager.getConnection(postgreSQLContainer.getJdbcUrl(), connectionProps)) {

                Database liquibaseDatabase = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                Liquibase liquibase = new Liquibase(
                        "db/changelog.xml",
                        new ClassLoaderResourceAccessor(),
//                        new FileSystemResourceAccessor(getProject().getProjectDir().getAbsolutePath() + "/src/main/resources"),
                        liquibaseDatabase
                );

                liquibase.update(new Contexts());

            }
            Configuration configuration = buildJooqConfiguration(
                    postgreSQLContainer.getJdbcUrl(),
                    postgreSQLContainer.getUsername(),
                    postgreSQLContainer.getPassword()
            );
            GenerationTool.generate(configuration);
        } finally {
            postgreSQLContainer.stop();
        }
    }

    private PostgreSQLContainer createContainer() {
        DockerImageName postgresImageName = DockerImageName
                .parse("postgres")
                .asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer(postgresImageName);
    }

    private void disableRyukContainer() throws Exception {
        getModifiableEnvironment().put("TESTCONTAINERS_RYUK_DISABLED", "true");
    }


    private Configuration buildJooqConfiguration(String jdbcUrl,
                                                 String username,
                                                 String password) {
        org.jooq.meta.jaxb.Database database = new org.jooq.meta.jaxb.Database()
                .withName("org.jooq.meta.postgres.PostgresDatabase")
                .withInputSchema("public")
                .withExcludes("Databasechangelog.*");
        Generate generate = new Generate()
                .withDeprecated(false)
                .withRecords(true)
                .withPojos(true)
                .withImmutablePojos(false)
                .withFluentSetters(true)
                .withRoutines(false)
                .withIndexes(false)
                .withUdts(true)
                .withQueues(false)
                .withKeys(false);
        System.out.println("Dir= " + getProject().getBuildDir().getAbsolutePath() + "/jooq");
        System.out.println("ProjectDir= " + getProject().getProjectDir().getAbsolutePath());
        System.out.println("RootDir= " + getProject().getRootDir().getAbsolutePath());
        Target target = new Target()
                .withPackageName("com.example.model")
                .withDirectory(getProject().getBuildDir().getAbsolutePath() + "/generated")
//                .withDirectory(getProject().getProjectDir().getAbsolutePath() + "/src/jooq/java")
                ;
        Strategy strategy = new Strategy()
                .withName("com.example.plugin.CustomGeneratorStrategy");
        Generator generator = new Generator()
                .withName("org.jooq.codegen.JavaGenerator")
                .withDatabase(database)
                .withGenerate(generate)
                .withTarget(target)
                .withStrategy(strategy);
        org.jooq.meta.jaxb.Property property = new org.jooq.meta.jaxb.Property();
        property.setKey("ssl");
        property.setValue("false");
        Jdbc jdbc = new Jdbc()
                .withDriver("org.postgresql.Driver")
                .withUrl(jdbcUrl)
                .withUser(username)
                .withPassword(password)
                .withProperties(property);
        return new Configuration()
                .withGenerator(generator)
                .withJdbc(jdbc);
    }

    // https://stackoverflow.com/questions/580085/is-it-possible-to-set-an-environment-variable-at-runtime-from-java
    @SuppressWarnings("unchecked")
    private static Map<String, String> getModifiableEnvironment() throws Exception {
        Class<?> pe = Class.forName("java.lang.ProcessEnvironment");
        Method getenv = pe.getDeclaredMethod("getenv", String.class);
        getenv.setAccessible(true);
        Field props = pe.getDeclaredField("theCaseInsensitiveEnvironment");
        props.setAccessible(true);
        return (Map<String, String>) props.get(null);
    }
}
