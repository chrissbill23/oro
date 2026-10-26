package com.asata.org.oro.identity.api;

import com.asata.org.oro.identity.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.UUID;

/** an export as a download: a file, never cached */
final class ExportResponses {

    private ExportResponses() {
    }

    static void write(HttpServletResponse response, UUID identityId, ExportService exports) throws IOException {
        write(response, identityId, null, exports);
    }

    /** organizationId: only what is in its conversations; null: everything */
    static void write(HttpServletResponse response, UUID identityId, UUID organizationId, ExportService exports) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"oro-export.json\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        exports.write(identityId, organizationId, response.getOutputStream());
    }
}
