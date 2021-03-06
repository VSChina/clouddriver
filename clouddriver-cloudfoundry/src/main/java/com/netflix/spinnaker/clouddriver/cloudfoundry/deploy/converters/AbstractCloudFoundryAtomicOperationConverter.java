/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.gcs.GcsArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.http.HttpArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

abstract class AbstractCloudFoundryAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  Optional<CloudFoundrySpace> findSpace(String region, CloudFoundryClient client) {
    CloudFoundrySpace space = CloudFoundrySpace.fromRegion(region);

    // fully populates the space guid which is what Cloud Foundry's API expects as an input, not the name.
    return client.getOrganizations()
      .findByName(space.getOrganization().getName())
      .map(org -> client.getSpaces().findByName(org.getId(), space.getName()));
  }

  protected CloudFoundryClient getClient(Map<?, ?> input) {
    CloudFoundryCredentials credentials = getCredentialsObject(input.get("credentials").toString());
    return credentials.getClient();
  }

  static Artifact convertToArtifact(ArtifactCredentials artifactCredentials, String reference) {
    Artifact artifact = new Artifact();
    artifact.setReference(reference);

    if (artifactCredentials == null || artifactCredentials instanceof HttpArtifactCredentials) {
      artifact.setType("http/file");
    } else if (artifactCredentials instanceof GcsArtifactCredentials) {
      artifact.setType("gcs/file");
    }

    return artifact;
  }

  void downloadAndProcessManifest(Map manifest, ArtifactCredentialsRepository credentialsRepository, Consumer<Map> processManifest) {
    ArtifactCredentials manifestArtifactCredentials = credentialsRepository.getAllCredentials().stream()
      .filter(creds -> creds.getName().equals(manifest.get("account")))
      .findAny()
      .orElseThrow(() -> new IllegalArgumentException("Unable to find manifest credentials '" + manifest.get("account") + "'"));
    Artifact manifestArtifact = convertToArtifact(manifestArtifactCredentials, manifest.get("reference").toString());

    try {
      InputStream manifestInput = manifestArtifactCredentials.download(manifestArtifact);
      Yaml parser = new Yaml();
      processManifest.accept(parser.load(manifestInput));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
