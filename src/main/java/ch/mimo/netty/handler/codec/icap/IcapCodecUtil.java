/*******************************************************************************
 * Copyright (c) 2011 Michael Mimo Moratti.
 *
 * Michael Mimo Moratti licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package ch.mimo.netty.handler.codec.icap;

import java.nio.charset.Charset;
import java.util.List;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.util.CharsetUtil;


/**
 * This class is an exact copy of @see HttpCodecUtil
 * Once the ICAP codec will be integrated into netty this has to be consolidated.
 */
final class IcapCodecUtil {
	
	// 0; ieof
	static final Byte[] IEOF_SEQUENCE = new Byte[]{48,59,32,105,101,111,102};
	
	// TODO find other solution...
	static final byte[] NATIVE_IEOF_SEQUENCE = new byte[]{48,59,32,105,101,111,102};
	
	static final String IEOF_SEQUENCE_STRING = "0; ieof";
	
    //space ' '
    static final byte SP = 32;

    //tab ' '
    static final byte HT = 9;

    /**
     * Carriage return
     */
    static final byte CR = 13;

    /**
     * Equals '='
     */
    static final byte EQUALS = 61;

    /**
     * Line feed character
     */
    static final byte LF = 10;

    /**
     * carriage return line feed
     */
    static final byte[] CRLF = new byte[] { CR, LF };

    /**
    * Colon ':'
    */
    static final byte COLON = 58;

    /**
    * Semicolon ';'
    */
    static final byte SEMICOLON = 59;

     /**
    * comma ','
    */
    static final byte COMMA = 44;

    static final byte DOUBLE_QUOTE = '"';

    static final Charset DEFAULT_CHARSET = CharsetUtil.UTF_8;
    
    static final Charset ASCII_CHARSET = Charset.forName("ASCII");
    
    static final String ENCAPSULATION_ELEMENT_REQHDR = "req-hdr";
    static final String ENCAPSULATION_ELEMENT_RESHDR = "res-hdr";
    static final String ENCAPSULATION_ELEMENT_REQBODY = "req-body";
    static final String ENCAPSULATION_ELEMENT_RESBODY = "res-body";
    static final String ENCAPSULATION_ELEMENT_OPTBODY = "opt-body";
    static final String ENCAPSULATION_ELEMENT_NULLBODY = "null-body";
    
    private IcapCodecUtil() {
    }

    static void validateHeaderName(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c > 127) {
                throw new IllegalArgumentException(
                        "name contains non-ascii character: " + name);
            }

            // Check prohibited characters.
            switch (c) {
            case '\t': case '\n': case 0x0b: case '\f': case '\r':
            case ' ':  case ',':  case ':':  case ';':  case '=':
                throw new IllegalArgumentException(
                        "name contains one of the following prohibited characters: " +
                        "=,;: \\t\\r\\n\\v\\f: " + name);
            }
        }
    }

    static void validateHeaderValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        // 0 - the previous character was neither CR nor LF
        // 1 - the previous character was CR
        // 2 - the previous character was LF
        int state = 0;

        for (int i = 0; i < value.length(); i ++) {
            char c = value.charAt(i);

            // Check the absolutely prohibited characters.
            switch (c) {
            case 0x0b: // Vertical tab
                throw new IllegalArgumentException(
                        "value contains a prohibited character '\\v': " + value);
            case '\f':
                throw new IllegalArgumentException(
                        "value contains a prohibited character '\\f': " + value);
            }

            // Check the CRLF (HT | SP) pattern
            switch (state) {
            case 0:
                switch (c) {
                case '\r':
                    state = 1;
                    break;
                case '\n':
                    state = 2;
                    break;
                }
                break;
            case 1:
                switch (c) {
                case '\n':
                    state = 2;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Only '\\n' is allowed after '\\r': " + value);
                }
                break;
            case 2:
                switch (c) {
                case '\t': case ' ':
                    state = 0;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Only ' ' and '\\t' are allowed after '\\n': " + value);
                }
            }
        }

        if (state != 0) {
            throw new IllegalArgumentException(
                    "value must not end with '\\r' or '\\n':" + value);
        }
    }

    static boolean isTransferEncodingChunked(HttpMessage m) {
        List<String> chunked = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        if (chunked.isEmpty()) {
            return false;
        }

        for (String v: chunked) {
            if (v.equalsIgnoreCase(HttpHeaders.Values.CHUNKED)) {
                return true;
            }
        }
        return false;
    }
}
