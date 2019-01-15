package com.jpinpoint.perf.pinpointrules;

import org.apache.commons.fileupload.FileItem;

/**
 * Following test is to add detections for non streaming methods of the commons-fileupload methods
 * get and getString. These should be avoided for big files!
 *
 * See also JPCC-19
 */
public class AvoidApacheCommonsFileItemNonStreamingTest {

    private FileItem globalFileItem;

    private String getAllowedMimeType(final FileItem fileItem1) {
        return allowedDocumentExtensionsList.getAllowedMimeType(
                fileItem1.get(), // avoid!!!
                fileItem1.getName());
    }

    private String getString(final FileItem fileItem2) {
        FileItem fileItem3 = fileItem2;
        return fileItem2.getString() + fileItem3.getString(); // avoid!!!
    }

    private String getString(final HarmlessClass harmless) {
        globalFileItem.getString(); // avoid!!!
        return harmless.getString(); // harmless
    }

    private byte [] get(final HarmlessClass harmless) {
        globalFileItem.get(); // avoid!!!
        return harmless.get(); // harmless
    }

    private class AllowedDocumentExtensionsList {
        public String getAllowedMimeType(byte [] byteArray, String fileItemName) {
            return "pdf";
        }
    }

    private AllowedDocumentExtensionsList allowedDocumentExtensionsList = new AllowedDocumentExtensionsList();

    private class HarmlessClass {

        public byte [] get() {
            return new byte[0];
        }

        public String getString() {
            return "";
        }
    }

}
