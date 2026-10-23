package com.asata.org.oro.config.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The directory where the uploads are received (spring.servlet.multipart.location, ORO_MULTIPART_DIR), made at start:
 * the server refuses an upload into a directory that does not exist. Under the local storage, its name starts with a
 * dot: never a storage key.
 */
@Slf4j
@Component
public class MultipartLocation {

    public MultipartLocation(@Value("${spring.servlet.multipart.location:}") String location) throws IOException {
        if (location != null && !location.isBlank()) {
            Path directory = Files.createDirectories(Path.of(location));
            log.info("uploads received in {}", directory.toAbsolutePath());
        }
    }
}
