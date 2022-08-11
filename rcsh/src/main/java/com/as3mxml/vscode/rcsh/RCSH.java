package com.as3mxml.vscode.rcsh;

import java.util.Scanner;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.royale.compiler.clients.COMPJSC;
import org.apache.royale.compiler.clients.MXMLJSC;

/**
 * Royale Compiler Shell. Derived from ASCSH by Jeff Ward.
 */
public class RCSH {
    public static void main(String[] args) {
        System.out.println("Royale Compiler Shell");

        if (args.length > 0) {
            compile(args);
        }

        // Accept commands from STDIN
        String line;
        Scanner stdin = new Scanner(System.in);

        while (true) {
            System.out.print("(fcsh) ");
            line = stdin.nextLine();
            if (line == null || line.equals("")) {
                continue;
            }

            line = line.trim();
            if (line.equals("quit") || line.equals("exit")) {
                break;
            }
            if (line.equals("help")) {
                print_help();
                continue;
            }

            // Parse String command into args array
            String[] compile_args = null;
            try {
                Matcher matcher = compilerOptionsPattern.matcher(line);
                ArrayList<String> argsCollection = new ArrayList<String>();
                while (matcher.find()) {
                    String option = matcher.group();
                    argsCollection.add(option);
                }
                compile_args = argsCollection.toArray(new String[0]);
            } catch (Exception e) {
                System.out.println("Error parsing command:\n" + line);
                e.printStackTrace(System.err);
                continue;
            }
            compile(compile_args);
        }

        stdin.close();
        System.exit(0);
    }

    private static ArrayList<ArrayList<String>> targets = new ArrayList<ArrayList<String>>();
    private static ArrayList<Object> compilers = new ArrayList<Object>();
    private static Pattern compilerOptionsPattern = Pattern.compile("[^\\s]*'([^'])*?'|[^\\s]*\"([^\"])*?\"|[^\\s]+");

    @SuppressWarnings("all")
    /**
     * Invoke MXMLC or COMPC
     */
    public static void compile(String[] args) {
        ArrayList<String> list = new ArrayList<String>(Arrays.asList(args));
        String command = list.remove(0); // Shift first element (command) from args
        int exitCode = 0;

        // Compile existing target
        if (command.equals("compile")) {
            int idx = Integer.parseInt(list.get(0)) - 1;
            if (idx >= compilers.size()) {
                System.out.println("fcsh: Target " + (idx + 1) + " not found");
                return;
            } else {
                Object compiler = compilers.get(idx);
                list = targets.get(idx);
                args = list.toArray(new String[list.size()]);
                if (compiler instanceof MXMLJSC) {
                    MXMLJSC mxmlc = (MXMLJSC) compiler;
                    startCapture();
                    // this is actually a static method, but ascsh calls it as
                    // non-static, for some reason -JT
                    exitCode = mxmlc.staticMainNoExit(args);
                    stopCapture();
                } else if (compiler instanceof COMPJSC) {
                    COMPJSC compc = (COMPJSC) compiler;
                    startCapture();
                    exitCode = compc.staticMainNoExit(args);
                    stopCapture();
                } else {
                    System.out.println("fcsh: Target " + (idx + 1) + " not found");
                    return;
                }
            }
        } else {
            args = list.toArray(new String[list.size()]);

            if (targets.size() > 0) {
                System.out.println(
                        "WARNING: rcsh currently only reliably handles one compile target via staticMainNoExit");
            }
            System.out.println("fcsh: Assigned " + (targets.size() + 1) + " as the compile target id");

            if (command.equals("mxmlc")) {
                MXMLJSC compiler = new MXMLJSC();
                compilers.add(compiler);
                targets.add(list);
                startCapture();
                exitCode = compiler.execute(args);
                stopCapture();
            } else if (command.equals("compc")) {
                COMPJSC compiler = new COMPJSC();
                compilers.add(compiler);
                targets.add(list);
                startCapture();
                exitCode = compiler.execute(args);
                stopCapture();
            } else {
                System.out.println("fcsh unknown command '" + command + "'");
                exitCode = 255;
            }
        }
        System.out.println("Compile status: " + exitCode);
    }

    static ByteArrayOutputStream buffer;
    static PrintStream oldOut;
    static Pattern errPattern = Pattern.compile(".*\\.[a-z]+.*:[0-9]+");

    static void startCapture() {
        buffer = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buffer);
        // IMPORTANT: Save the old System.out!
        oldOut = System.err;
        // Tell Java to use your special stream
        System.setErr(ps);
        // Print some output: goes to your special stream
    }

    static void stopCapture() {
        // Put things back
        System.err.flush();
        System.setErr(oldOut);

        // Reformat errors to match FCSH
        String[] lines = buffer.toString().split("\n");
        for (int i = 0, len = lines.length; i < len; i++) {
            String line = lines[i].trim();
            if (errPattern.matcher(line).matches()) {
                System.err.print(line);
                System.err.print(": ");
            } else {
                System.err.println(lines[i]);
            }
        }

        oldOut = null;
        buffer = null;
    }

    /**
     * Print available commands
     */
    public static void print_help() {
        System.out.print("List of fcsh commands:\n"
                + "mxmlc arg1 arg2 ...      full compilation and optimization; return a target id\n"
                + "compc arg1 arg2 ...      full SWC compilation\n"
                + "compile id               incremental compilation\n"
                + "clear [id]               clear target(s) (NOT SUPPORTED)\n"
                + "info [id]                display compile target info\n" + "quit                     quit\n");
    }
}
