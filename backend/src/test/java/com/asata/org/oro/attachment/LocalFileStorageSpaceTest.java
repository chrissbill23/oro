package com.asata.org.oro.attachment;

import com.asata.org.oro.attachment.storage.LocalFileStorage;
import com.asata.org.oro.exception.ServiceUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** the local storage keeps part of its disk free: an upload that would eat into it is refused (503) */
class LocalFileStorageSpaceTest {

    @Test
    void aFileIsRefusedWhenTheDiskWouldBeShort(@TempDir Path directory) throws Exception {
        assertThatCode(() -> new LocalFileStorage(directory.toString(), 1024).requireSpace(1000)).doesNotThrowAnyException();
        // more free space than any disk has
        assertThatThrownBy(() -> new LocalFileStorage(directory.toString(), Long.MAX_VALUE / 2).requireSpace(1000))
                .isInstanceOf(ServiceUnavailable.class);
    }
}
