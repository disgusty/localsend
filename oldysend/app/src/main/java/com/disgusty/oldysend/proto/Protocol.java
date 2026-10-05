package com.disgusty.oldysend.proto;

/** Constants of LocalSend protocol v2.2. */
public final class Protocol {
    public static final String VERSION = "2.2";
    public static final String API = "/api/localsend/v2";

    public static final String REGISTER = API + "/register";
    public static final String INFO = API + "/info";
    public static final String INFO_V1 = "/api/localsend/v1/info";
    public static final String PREPARE_UPLOAD = API + "/prepare-upload";
    public static final String UPLOAD = API + "/upload";
    public static final String CANCEL = API + "/cancel";
    public static final String PREPARE_DOWNLOAD = API + "/prepare-download";
    public static final String DOWNLOAD = API + "/download";
    public static final String SHOW = API + "/show";

    /** Body limit for JSON requests (file lists can be long). */
    public static final int MAX_JSON = 8 * 1024 * 1024;

    public static final int MAX_PIN_ATTEMPTS = 3;
    public static final int MAX_UPLOAD_ATTEMPTS = 3;

    private Protocol() {
    }
}
