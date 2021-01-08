package com.sword;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3MessageInfo;
import org.apache.commons.net.pop3.POP3SClient;
import com.buck.common.codec.QuotedPrintable;


public class Pop3Receiver {
    private static List<String> sendList;
    public static void printMessageInfo(final BufferedReader reader, final int id) throws IOException {
        String mail = "";
        String from = "";
        String subject = "";
        String line;
        String html = "";
        boolean visitUrl = true;
        boolean htmlFind = false;
        while ((line = reader.readLine()) != null) {
            final String lower = line.toLowerCase(Locale.ENGLISH);
            mail += line + "\n\r";
            if (lower.startsWith("from: ")) {
                from = line.substring(6).trim();
            } else if (lower.startsWith("subject: ")) {
                subject = line.substring(9).trim();
            }
            if (line.contains("<html>")) htmlFind = true;
            if (htmlFind) {
//                System.out.println(line);
//                html+=new String(new QuotedPrintable().newDecoder().decode(line.getBytes("utf-8")), Charset.forName("utf-8"));
                if (line.endsWith("=")) line = line.substring(0, line.length() - 1);
                html += line;
            }
            if (line.contains("</html>")) htmlFind = false;
        }

//        System.out.println(Integer.toString(id) + " From: " + from + "  Subject: " + subject);

        if (from.contains("@amazon.com")) {
//            System.out.print(mail);
//        html="=E6=AC=A2=E8=BF=8E=E4=BD=BF=E7=94=A8=E7=BD=91"; //test code
            html = new String(new QuotedPrintable().newDecoder().decode(html.getBytes("utf-8")), Charset.forName("utf-8"));
            if (html.contains("验证请求") && visitUrl) {
                html = html.substring(0, html.indexOf("验证请求"));
                html = html.substring(html.lastIndexOf("<a "));
                html = html.substring(html.indexOf("href=\"") + 6);
                html = html.substring(0, html.indexOf("\""));

                System.out.println(html);

                String confirmUrl = html;

                if(needSend(confirmUrl)) {
                    URL url = new URL(confirmUrl);

                    URLConnection URLconnection = url.openConnection();
                    HttpURLConnection httpConnection = (HttpURLConnection) URLconnection;
                    int responseCode = httpConnection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        System.err.println("成功");

//                        InputStream in = httpConnection.getInputStream();
//                        InputStreamReader isr = new InputStreamReader(in);
//                        BufferedReader bufr = new BufferedReader(isr);
//                        String str;
//                        while ((str = bufr.readLine()) != null) {
//                            System.out.println(str);
//                        }
//                        bufr.close();

                    } else {
                        System.err.println("失败");
                    }
                }
            }
        }
        mail = "";
    }

    private static boolean needSend(String url){    //判断是否曾经发送过，返回结果并添加到已发送列表
        boolean ret=true;
        for(String s : sendList){
            if(s.compareTo(url)==0)ret=false;
        }
        if(ret){
            sendList.add(url);
            //Todo:save2File
        }
        return ret;
    }

    public static void main(final String[] args) {
        if (args.length < 3) {
            System.err.println(
                    "Usage: Pop3Receiver <server[:port]> <username> <password|-|*|VARNAME> [TLS [true=implicit]]");
            System.err.println(
                    "Example: Pop3Receiver pop.163.com:995 username password TLS");
            System.exit(1);
        }

        //todo:load sendlist
        sendList=new ArrayList<String>();

        final String arg0[] = args[0].split(":");
        final String server = arg0[0];
        final String username = args[1];
        String password = args[2];
        // prompt for the password if necessary
//        try {
//            password = Utils.getPassword(username, password);
//        } catch (final IOException e1) {
//            System.err.println("Could not retrieve password: " + e1.getMessage());
//            return;
//        }

        final String proto = args.length > 3 ? args[3] : null;
        final boolean implicit = args.length > 4 ? Boolean.parseBoolean(args[4]) : false;

        POP3Client pop3;

        if (proto != null) {
            System.out.println("Using secure protocol: " + proto);
            pop3 = new POP3SClient(proto, implicit);
        } else {
            pop3 = new POP3Client();
        }

        int port;
        if (arg0.length == 2) {
            port = Integer.parseInt(arg0[1]);
        } else {
            port = pop3.getDefaultPort();
        }
        System.out.println("Connecting to server " + server + " on " + port);

        // We want to timeout if a response takes longer than 60 seconds
        pop3.setDefaultTimeout(60000);

        // suppress login details
        pop3.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out), true));

        try {
            pop3.connect(server);
        } catch (final IOException e) {
            System.err.println("Could not connect to server.");
            e.printStackTrace();
            return;
        }

        try {
            if (!pop3.login(username, password)) {
                System.err.println("Could not login to server.  Check password.");
                pop3.disconnect();
                return;
            }

            final POP3MessageInfo status = pop3.status();
            if (status == null) {
                System.err.println("Could not retrieve status.");
                pop3.logout();
                pop3.disconnect();
                return;
            }

            System.out.println("Status: " + status);

            pop3.setReceiveBufferSize(10);
            final POP3MessageInfo[] messages = pop3.listMessages();

            if (messages == null) {
                System.err.println("Could not retrieve message list.");
                pop3.logout();
                pop3.disconnect();
                return;
            } else if (messages.length == 0) {
                System.out.println("No messages");
                pop3.logout();
                pop3.disconnect();
                return;
            }

            System.out.println("Message count: " + messages.length);

            for (final POP3MessageInfo msginfo : messages) {
//                final BufferedReader reader = (BufferedReader) pop3.retrieveMessageTop(msginfo.number, 0);
                final BufferedReader reader = (BufferedReader) pop3.retrieveMessage(msginfo.number);

                if (reader == null) {
                    System.err.println("Could not retrieve message header.");
                    pop3.logout();
                    pop3.disconnect();
                    return;
                }
                printMessageInfo(reader, msginfo.number);
            }

            pop3.logout();
            pop3.disconnect();
        } catch (final IOException e) {
            e.printStackTrace();
            return;
        }finally {
            for(String s : sendList){
                System.out.println(s);
            }
        }
    }
}