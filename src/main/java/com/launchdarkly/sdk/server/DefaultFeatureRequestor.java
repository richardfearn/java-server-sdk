package com.launchdarkly.sdk.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.stream.JsonReader;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;
import com.launchdarkly.sdk.server.interfaces.SerializationException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.launchdarkly.sdk.server.DataModelSerialization.parseFullDataSet;
import static com.launchdarkly.sdk.server.Util.concatenateUriPath;
import static com.launchdarkly.sdk.server.Util.configureHttpClientBuilder;
import static com.launchdarkly.sdk.server.Util.getHeadersBuilderFor;
import static com.launchdarkly.sdk.server.Util.shutdownHttpClient;

import okhttp3.Cache;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Implementation of getting flag data via a polling request.
 */
final class DefaultFeatureRequestor implements FeatureRequestor {
  private static final long MAX_HTTP_CACHE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
  
  @VisibleForTesting final URI baseUri;
  private final OkHttpClient httpClient;
  private final URI pollingUri;
  private final Headers headers;
  private final Path cacheDir;
  private final LDLogger logger;

  DefaultFeatureRequestor(HttpConfiguration httpConfig, URI baseUri, LDLogger logger) {
    this.baseUri = baseUri;
    this.pollingUri = concatenateUriPath(baseUri, StandardEndpoints.POLLING_REQUEST_PATH);
    this.logger = logger;
    
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(httpConfig, httpBuilder);
    this.headers = getHeadersBuilderFor(httpConfig).build();

    try {
      cacheDir = Files.createTempDirectory("LaunchDarklySDK");
    } catch (IOException e) {
      throw new RuntimeException("unable to create cache directory for polling", e);
    }
    Cache cache = new Cache(cacheDir.toFile(), MAX_HTTP_CACHE_SIZE_BYTES);
    httpBuilder.cache(cache);

    httpClient = httpBuilder.build();
  }

  public void close() {
    shutdownHttpClient(httpClient);
    Util.deleteDirectory(cacheDir);
  }
  
  public FullDataSet<ItemDescriptor> getAllData(boolean returnDataEvenIfCached)
      throws IOException, HttpErrorException, SerializationException {
    Request request = new Request.Builder()
        .url(pollingUri.toURL())
        .headers(headers)
        .get()
        .build();

    logger.debug("Making request: " + request);

    try (Response response = httpClient.newCall(request).execute()) {
      boolean wasCached = response.networkResponse() == null || response.networkResponse().code() == 304;
      if (wasCached && !returnDataEvenIfCached) {
        logger.debug("Get flag(s) got cached response, will not parse");
        logger.debug("Cache hit count: {} Cache network count: {} ",
            httpClient.cache().hitCount(), httpClient.cache().networkCount());
        return null;
      }

      logger.debug("Get flag(s) response: " + response.toString());
      logger.debug("Network response: " + response.networkResponse());
      logger.debug("Cache hit count: " + httpClient.cache().hitCount() + " Cache network Count: " + httpClient.cache().networkCount());
      logger.debug("Cache response: " + response.cacheResponse());

      if (!response.isSuccessful()) {
        throw new HttpErrorException(response.code());
      }

      JsonReader jr = new JsonReader(response.body().charStream());
      return parseFullDataSet(jr);
    }
  }
}
