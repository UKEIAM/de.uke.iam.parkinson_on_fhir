package de.uke.iam.parkinson_on_fhir.servlet;

import java.util.ArrayList;
import java.util.List;
import java.sql.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.narrative.INarrativeGenerator;
import ca.uhn.fhir.rest.server.FifoMemoryPagingProvider;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import ca.uhn.fhir.rest.openapi.OpenApiInterceptor;
import org.jooq.SQLDialect;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import de.uke.iam.parkinson_on_fhir.provider.DeviceResourceProvider;
import de.uke.iam.parkinson_on_fhir.provider.GroupResourceProvider;
import de.uke.iam.parkinson_on_fhir.provider.ObservationResourceProvider;
import de.uke.iam.parkinson_on_fhir.provider.PatientResourceProvider;

/**
 * This servlet is the actual FHIR server itself
 */
public class RestfulServlet extends RestfulServer {

	private static final long serialVersionUID = 1L;
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RestfulServlet.class);

	/**
	 * Constructor
	 */
	public RestfulServlet() {
		super(FhirContext.forR4());
	}

	/**
	 * This method is called automatically when the
	 * servlet is initializing.
	 */
	@Override
	public void initialize() {
		var user = System.getProperty("de.uke.iam.parkinson_on_fhir.user");
		var password = System.getProperty("de.uke.iam.parkinson_on_fhir.password");
		var url = String.format(
				"jdbc:postgresql://%s/%s",
				System.getProperty("de.uke.iam.parkinson_on_fhir.postgres_server"),
				System.getProperty("de.uke.iam.parkinson_on_fhir.database"));

		DSLContext context = null;
		try {
			logger.info("Loading the PostgreSQL driver");
			Class.forName("org.postgresql.Driver");

			logger.info("Connecting '{}' with user '{}'", url, user);
			Connection connection = DriverManager.getConnection(url, user, password);
			// We disable auto-commit as Postgres does not support fetching otherwise
			connection.setAutoCommit(false);

			logger.info("Initializing jOOQ");
			context = DSL.using(connection, SQLDialect.POSTGRES);

			logger.info("Database ready");
		} catch (Exception e) {
			logger.error("Unable to establish database connection: {}", e.toString());
			return;
		}

		/*
		 * Two resource providers are defined. Each one handles a specific
		 * type of resource.
		 */
		List<IResourceProvider> providers = new ArrayList<IResourceProvider>();
		providers.add(new GroupResourceProvider(context));
		providers.add(new PatientResourceProvider(context));
		providers.add(new ObservationResourceProvider(context));
		providers.add(new DeviceResourceProvider(context));
		setResourceProviders(providers);

		/*
		 * Use a narrative generator. This is a completely optional step,
		 * but can be useful as it causes HAPI to generate narratives for
		 * resources which don't otherwise have one.
		 */
		INarrativeGenerator narrativeGen = new DefaultThymeleafNarrativeGenerator();
		getFhirContext().setNarrativeGenerator(narrativeGen);

		/*
		 * Use nice coloured HTML when a browser is used to request the content
		 */
		registerInterceptor(new ResponseHighlighterInterceptor());

		/*
		 * Show OpenAPI documentation under "/api-docs"
		 */
		OpenApiInterceptor openApiInterceptor = new OpenApiInterceptor();
		registerInterceptor(openApiInterceptor);

		/*
		 * Support paging for long output.
		 */
		FifoMemoryPagingProvider pp = new FifoMemoryPagingProvider(1024);
		pp.setDefaultPageSize(50);
		pp.setMaximumPageSize(100);
		setPagingProvider(pp);
	}
}
