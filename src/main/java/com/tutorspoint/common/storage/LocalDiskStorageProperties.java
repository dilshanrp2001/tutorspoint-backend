package com.tutorspoint.common.storage;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/**
 * Where {@link LocalDiskFileStorage} keeps its files.
 *
 * <p>A path, not a secret, but still environment-specific: a developer's machine, a container
 * volume and a production host disagree about it, and the same build artifact has to run in
 * all three. The dev profile defaults it under the working directory; production supplies a
 * mounted volume and nothing else would be acceptable, because a directory inside the
 * container would lose every uploaded document on the next deployment.
 */
@Validated
@ConfigurationProperties(prefix = "tutorspoint.storage.local")
public record LocalDiskStorageProperties(

        /** The directory every storage key is resolved against. Created at startup if absent. */
        @NotNull Path root) {
}
