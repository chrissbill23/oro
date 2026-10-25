package com.asata.org.oro.identity.service;

/** the size of a page of the export, for the tests outside its package */
public final class ExportServiceAccess {

    private ExportServiceAccess() {
    }

    public static int page() {
        return ExportService.PAGE;
    }
}
