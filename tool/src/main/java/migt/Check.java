package migt;

import burp.IExtensionHelpers;
import burp.IRequestInfo;
import burp.IResponseInfo;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Check Object class. This object is used in Operations to check that a parameter or some text is in as specified.
 *
 * @author Matteo Bitussi
 */
public class Check {
    String what; // what to search
    Utils.CheckOps op; // the check operations
    Utils.MessageSection in; // the section over which to search
    String op_val;
    boolean isParamCheck = false; // specifies if what is declared in what is a parameter name

    Utils.ContentType contentType; // The content on which the check should work on

    public Check() {

    }

    /**
     * Instantiate a new Check object given its parsed JSONObject
     *
     * @param json_check the check as JSONObject
     * @throws ParsingException
     */
    public Check(JSONObject json_check) throws ParsingException {
        Iterator<String> keys = json_check.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            switch (key) {
                case "in":
                    if (key.equals("in")) {
                        this.in = Utils.MessageSection.fromString(json_check.getString("in"));
                    }
                case "check param":
                    if (key.equals("check param")) {
                        this.isParamCheck = true;
                        this.setWhat(json_check.getString("check param"));
                        break;
                    }
                case "check":
                    if (key.equals("check")) {
                        this.setWhat(json_check.getString("check"));
                        break;
                    }
                case "is":
                    if (key.equals("is")) {
                        this.setOp(Utils.CheckOps.IS);
                        this.op_val = json_check.getString("is");
                        break;
                    }
                case "is not":
                    if (key.equals("is not")) {
                        this.setOp(Utils.CheckOps.IS_NOT);
                        this.op_val = json_check.getString("is not");
                        break;
                    }
                case "contains":
                    if (key.equals("contains")) {
                        this.setOp(Utils.CheckOps.CONTAINS);
                        this.op_val = json_check.getString("contains");
                        break;
                    }
                case "not contains":
                    if (key.equals("not contains")) {
                        this.setOp(Utils.CheckOps.NOT_CONTAINS);
                        this.op_val = json_check.getString("not contains");
                        break;
                    }
                case "is present":
                    if (key.equals("is present")) {
                        this.op = json_check.getBoolean("is present") ? Utils.CheckOps.IS_PRESENT :
                                Utils.CheckOps.IS_NOT_PRESENT;
                        this.op_val = json_check.getBoolean("is present") ?
                                "is present" : "is not present";
                    }
            }
        }
    }

    /**
     * Execute the check if it is http
     *
     * @param message
     * @param helpers
     * @param isRequest
     * @return
     * @throws ParsingException
     */
    private boolean execute_http(HTTPReqRes message,
                                 IExtensionHelpers helpers,
                                 boolean isRequest) throws ParsingException {
        String msg_str = "";
        IRequestInfo req_info = null;
        IResponseInfo res_info = null;
        if (isRequest) req_info = helpers.analyzeRequest(message.getRequest());
        if (!isRequest) res_info = helpers.analyzeResponse(message.getResponse());
        if (this.in == null) {
            throw new ParsingException("from tag in checks is null");
        }
        switch (this.in) {
            case URL:
                if (!isRequest) {
                    throw new ParsingException("Searching URL in response");
                }
                msg_str = req_info.getHeaders().get(0);
                break;
            case BODY:
                if (isRequest) {
                    int offset = req_info.getBodyOffset();
                    byte[] body = Arrays.copyOfRange(message.getRequest(), offset, message.getRequest().length);
                    msg_str = new String(body);
                } else {
                    int offset = res_info.getBodyOffset();
                    byte[] body = Arrays.copyOfRange(message.getResponse(), offset, message.getResponse().length);
                    msg_str = new String(body);
                }
                break;
            case HEAD:
                if (isRequest) {
                    int offset = req_info.getBodyOffset();
                    byte[] head = Arrays.copyOfRange(message.getRequest(), 0, offset);
                    msg_str = new String(head);
                } else {
                    int offset = res_info.getBodyOffset();
                    byte[] head = Arrays.copyOfRange(message.getResponse(), 0, offset);
                    msg_str = new String(head);
                }
                break;
            default:
                System.err.println("no valid \"in\" specified in check");
                return false;
        }

        if (msg_str.length() == 0) {
            return false;
        }

        if (this.isParamCheck) {
            try {
                Pattern p = this.in == Utils.MessageSection.URL ?
                        Pattern.compile("(?<=[?&]" + this.what + "=)[^\\n&]*") :
                        Pattern.compile("(?<=" + this.what + ":\\s?)[^\\n]*");
                Matcher m = p.matcher(msg_str);

                String val = "";
                if (m.find()) {
                    val = m.group();
                } else {
                    return false;
                }

                if (this.op == null && val.length() != 0) {
                    // if it passed all the splits without errors, the param is present, but no checks are specified
                    // so result is true
                    return true;
                }

                switch (this.op) {
                    case IS:
                        if (!this.op_val.equals(val)) {
                            return false;
                        }
                        break;
                    case IS_NOT:
                        if (this.op_val.equals(val)) {
                            return false;
                        }
                        break;
                    case CONTAINS:
                        if (!val.contains(this.op_val)) {
                            return false;
                        }
                        break;
                    case NOT_CONTAINS:
                        if (val.contains(this.op_val)) {
                            return false;
                        }
                        break;
                    case IS_PRESENT:
                        return true; // if it gets to this, the searched param is already found
                    case IS_NOT_PRESENT:
                        return false;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                //e.printStackTrace();
                if (this.op != null) {
                    if (this.op != Utils.CheckOps.IS_NOT_PRESENT) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        } else {
            if (!msg_str.contains(this.what)) {
                if (this.op != null) {
                    return this.op == Utils.CheckOps.IS_NOT_PRESENT;
                } else {
                    return false;
                }
            } else {
                if (this.op != null) {
                    return this.op != Utils.CheckOps.IS_NOT_PRESENT;
                }
            }
        }
        return true;
    }

    /**
     * Execute the json version of the check
     * @param message the message to check
     * @param helpers the burp helper class
     * @param isRequest to select the request or the response
     * @return the result of the execution //TODO: change to API
     * @throws ParsingException
     */
    private boolean execute_json(HTTPReqRes message,
                                 IExtensionHelpers helpers,
                                 boolean isRequest) throws ParsingException {
        return true;
        // https://github.com/json-path/JsonPath
        // TODO
        //String something = JsonPath.read(json, "$.store.book[*].author");

        //JSONParser jsonParser = new JSONParser();
    }

    /**
     * Executes the given check
     *
     * @param message
     * @param helpers
     * @param isRequest
     * @return the result of the check (passed or not passed)
     */
    public boolean execute(HTTPReqRes message,
                           IExtensionHelpers helpers,
                           boolean isRequest) throws ParsingException {
        switch (contentType) {
            case HTTP:
                return execute_http(message, helpers, isRequest);
            case JSON:
                return execute_json(message, helpers, isRequest);
            default:
                throw new ParsingException("invalid content type + " + contentType);
        }
    }

    public void setWhat(String what) {
        this.what = what;
    }

    public void setOp(Utils.CheckOps op) {
        this.op = op;
    }

    @Override
    public String toString() {
        return "check: " + what + (op == null ? "" : " " + op + ": " + op_val);
    }
}