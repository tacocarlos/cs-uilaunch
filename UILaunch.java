import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

public class UILaunch {

    // Settings
    public static final boolean USE_LEVENSHTEIN_JUDGING = false;
    private static final int MAX_LEVENSHTEIN_DISTANCE = 2;
    private static final int PROBLEM_TOTAL_SCORE = 60;
    private static final int ATTEMPT_PENALTY = 5;

    // things inherent to the OS
    public static final boolean IsWindows = System.getProperty("os.name")
        .toLowerCase()
        .contains("windows");

    public static final boolean IsMac = System.getProperty("os.name")
        .toLowerCase()
        .contains("mac");

    public static final boolean IsUnix = !IsWindows && !IsMac;
    public static final String CurrentDir = System.getProperty("user.dir");
    public static final String StudentSourceDir = Path.of(
        CurrentDir,
        "src"
    ).toString();

    public static final String DownloadDir = Path.of(
        System.getProperty("user.home"),
        "Downloads"
    ).toString();
    public static final String TempDir = System.getProperty("java.io.tmpdir");

    public static String getAppDataDirectory() {
        if (IsWindows) {
            return System.getenv("APPDATA");
        } else if (IsMac) {
            return Path.of(
                System.getProperty("user.home"),
                "Library",
                "Application Support"
            ).toString();
        } else {
            return Path.of(
                System.getProperty("user.home"),
                ".local",
                "share"
            ).toString();
        }
    }

    public static final String AppDataDir = getAppDataDirectory();

    // things that only need to be determined once
    public static final String CompetitionID = String.format(
        "csuil-practice-%d",
        System.currentTimeMillis()
    );
    public static final String CompetitionDirRoot = Path.of(
        AppDataDir,
        CompetitionID
    ).toString();
    public static final String CompetitionExecutionDir = Path.of(
        CompetitionDirRoot,
        "student-runtime"
    ).toString();
    // only need to be determined once, but can't do it when the program starts
    public static String JudgeFolderPath;
    public static String CompetitionDir; // depends on the contest directory

    // currently using UploadThing
    public static final Map<String, String> UIL_FILE_URLS = new TreeMap<
        String,
        String
    >(
        Map.of(
            "2026inva",
            "https://jxjozsvaxe.ufs.sh/f/rp8xqAZwHWGMsQfev1SuUYmLZkPKCBW2jgO97yTxX3z8fG51",
            "2025state",
            "https://jxjozsvaxe.ufs.sh/f/rp8xqAZwHWGMgwpP5q7qQS3VsjYulG0hf5KBy71PCFkMpXOz",
            "2025district",
            "https://jxjozsvaxe.ufs.sh/f/rp8xqAZwHWGMpHR0juXhkE458vxVsmANXzC6oU7jiaP1Y2yq",
            "2025invb",
            "https://jxjozsvaxe.ufs.sh/f/rp8xqAZwHWGMWPQSg8taujbMxJ3Bnr7XY0vSelTwE4A25gqy",
            "2025inva",
            "https://jxjozsvaxe.ufs.sh/f/rp8xqAZwHWGMqPorvkwLlAd9j36NPomXBYGKuiSZqzkRrawt"
        )
    );

    public static String downloadContestZip(String contestName) {
        final String directUrl = UIL_FILE_URLS.getOrDefault(contestName, null);
        if (directUrl == null) {
            return null;
        }

        System.out.printf("Downloading contest data from %s\n", directUrl);
        try {
            URI url = new URI(directUrl);
            ReadableByteChannel rbc = Channels.newChannel(
                url.toURL().openStream()
            );
            Path outputPath = Path.of(
                DownloadDir,
                contestName + "Programming" + ".zip"
            );
            FileOutputStream fileOut = new FileOutputStream(
                outputPath.toString()
            );
            FileChannel fc = fileOut.getChannel();
            fc.transferFrom(rbc, 0, Long.MAX_VALUE);
            fileOut.close();
            return outputPath.toString();
        } catch (Exception e) {
            System.out.println("Fatal Error. Could not download contest data.");
        }

        return null;
    }

    // I miss js' nullish coalescense
    public static <T> T nullish(T value, T fallback) {
        return value != null ? value : fallback;
    }

    public static <T> T nullish(T value, Supplier<T> fallbackFn) {
        return value != null ? value : fallbackFn.get();
    }

    public static boolean notNull(Object... values) {
        return Arrays.stream(values).allMatch(v -> v != null);
    }

    public static void exitProgram(int statusCode, String format, Object... v) {
        IOUtils.cleanUpTemp();
        System.out.printf(format, v);
        System.exit(statusCode);
    }

    public static void exitProgram(String reason) {
        IOUtils.cleanUpTemp();
        exitProgram(-1, reason);
    }

    public static class IOUtils {

        public static Set<String> listFiles(String dir) {
            return listFiles(dir, true);
        }

        public static Set<String> listFiles(String dir, boolean ignoreDir) {
            Set<String> filePaths = new HashSet<>();
            try (Stream<Path> stream = Files.list(Paths.get(dir))) {
                if (ignoreDir) {
                    filePaths = stream
                        .filter(f -> !Files.isDirectory(f))
                        .map(Path::toString)
                        .collect(Collectors.toSet());
                } else {
                    filePaths = stream
                        .map(Path::toString)
                        .collect(Collectors.toSet());
                }
            } catch (Exception e) {
                // don't do anything, we failed so just return empty set
                System.out.println("Failed to read files from: \n\t" + dir);
            }
            return filePaths;
        }

        public static boolean containsWords(String search, String[] words) {
            return Arrays.stream(words).reduce(
                true,
                (prev, w) -> prev && search.contains(w),
                (prev, curr) -> (prev && curr)
            );
        }

        public static boolean isZipFileMIME(String path) {
            try {
                Path p = Paths.get("example.zip");
                String contentType = Files.probeContentType(p);
                // contentType can be null, so invert the comparison
                return ("application/zip".equals(contentType));
            } catch (Exception e) {
                return false;
            }
        }

        public static boolean isZipFileMAGIC(String path) {
            Path p = Paths.get(path);
            if (Files.isDirectory(p)) {
                return false;
            }

            try (
                FileChannel channel = FileChannel.open(
                    p,
                    StandardOpenOption.READ
                )
            ) {
                ByteBuffer buffer = ByteBuffer.allocate(4);
                int bytesRead = channel.read(buffer);

                if (bytesRead < 4) {
                    return false;
                }

                buffer.flip();
                int header = buffer.getInt();
                // The magic number for ZIP is 0x504B0304 (Little Endian)
                // or 0x504B0708 (for spanned archives)
                return (
                    header == 0x504B0304 ||
                    header == 0x504B0506 ||
                    header == 0x504B0708
                );
            } catch (Exception e) {
                return false; // failed somehow, just give up
            }
        }

        public static boolean isZipFile(String path) {
            return isZipFileMIME(path) || isZipFileMAGIC(path);
        }

        public static String checkDir(String dir, String... terms) {
            return checkDir(dir, true, terms);
        }

        public static String checkDir(
            String dir,
            boolean ignoreDir,
            String... terms
        ) {
            Set<String> files = listFiles(dir, ignoreDir);
            for (String f : files) {
                if (containsWords(f, terms)) {
                    return f;
                }
            }

            return null;
        }

        public static boolean createDirIfAbsent(Path dir) {
            if (Files.notExists(dir)) {
                try {
                    Path p = Files.createDirectories(dir);
                    return p != null;
                } catch (Exception e) {
                    return false;
                }
            }

            return true; // if already exists, not really a problem, right?
        }

        public static void unzip(String zipFilePath, String destDir) {
            Path zipPath = Path.of(zipFilePath);
            Path destPath = Path.of(destDir);
            if (!createDirIfAbsent(zipPath)) {
                exitProgram(
                    -1,
                    "Unable to create directories at \n\t%s\nMaybe need files permissions?",
                    destDir
                );
            }

            try (
                ZipInputStream zis = new ZipInputStream(
                    Files.newInputStream(zipPath)
                )
            ) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path filePath = destPath.resolve(entry.getName());

                    if (
                        !filePath.normalize().startsWith(destPath.normalize())
                    ) {
                        zis.closeEntry();
                        exitProgram(
                            -1,
                            "Bad zip entry. Unable to correctly extract files."
                        );
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(filePath);
                    } else {
                        Files.createDirectories(filePath.getParent());

                        try (
                            OutputStream os = Files.newOutputStream(filePath)
                        ) {
                            byte[] buffer = new byte[8192];
                            int len = 0;
                            while ((len = zis.read(buffer)) > 0) {
                                os.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            } catch (Exception e) {
                exitProgram(-1, "Failed to unzip file.");
                cleanUpTemp();
            }

            // unzipped competition data successfully, but the actual data is inside a
            // different folder, so now we can set the CompetitionDir
            Set<String> F = listFiles(destDir, false);
            if (F.size() == 1) {
                CompetitionDir = F.stream().findFirst().orElse("");
                if (CompetitionDir.equals("")) {
                    cleanUpTemp();
                    exitProgram(
                        "Could not find competition root directory within unzipped data."
                    );
                } else {
                    // System.out.println(CompetitionDir);
                }
            }

            System.out.printf("Unzipped to %s successfully.\n", destDir);
        }

        public static boolean delete(File f) {
            return delete(f.toPath());
        }

        public static boolean delete(Path p) {
            File f = p.toFile();
            boolean success = true;
            if (f.isDirectory()) {
                for (File child : f.listFiles()) {
                    success &= delete(child);
                }
            }

            return success && f.delete();
        }

        public static boolean delete(String s) {
            return delete(Path.of(s));
        }

        public static void cleanUpTemp() {
            if(CompetitionDirRoot == null) return;

            if (delete(Path.of(CompetitionDirRoot))) {
                System.out.println("Cleaned up temporary files.");
            } else {
                System.out.printf(
                    "Unable to clean up temp files at:\n\t%s\n",
                    CompetitionDirRoot
                );
            }
        }

        public static String getFileName(String filePath) {
            return filePath != null
                ? Path.of(filePath).getFileName().toString()
                : "";
        }

        public static List<String> readLines(File f) {
            ArrayList<String> lines = new ArrayList<>();
            try (Scanner s = new Scanner(f)) {
                while (s.hasNextLine()) lines.add(s.nextLine());
            } catch (FileNotFoundException e) {}
            return lines;
        }
    }

    public static class Problem {

        private final String name;
        private final String judgeDataPath, correctCodePath, judgeOutPath;
        private int numTries = 0;
        private boolean accepted = false;
        private String studentDataPath;

        @Override
        public String toString() {
            return String.format(
                "%s [Accepted=%b, Attemps=%2d, Points Awarded=%2d]\n",
                name,
                accepted,
                numTries,
                getScore(),
                IOUtils.getFileName(judgeDataPath),
                IOUtils.getFileName(judgeOutPath),
                IOUtils.getFileName(studentDataPath)
            );
        }

        public String toStringDetailed() {
            return String.format(
                "%s [Accepted=%b, Attemps=%2d, Points Awarded=%2d]\n\tJudge Data File: %s\n\tJudge Output: %s\n\tStudent Data File:%s\n",
                name,
                accepted,
                numTries,
                getScore(),
                IOUtils.getFileName(judgeDataPath),
                IOUtils.getFileName(judgeOutPath),
                IOUtils.getFileName(studentDataPath)
            );
        }

        public Problem(String n, String jdp, String sop, String ccp) {
            name = n;
            judgeDataPath = jdp;
            judgeOutPath = sop;
            correctCodePath = ccp;
        }

        public void setStudentDataPath(String sdp) {
            studentDataPath = sdp;
        }

        public String getName() {
            return name;
        }

        public int getScore() {
            return accepted
                ? PROBLEM_TOTAL_SCORE - (numTries - 1) * ATTEMPT_PENALTY
                : 0;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public int getNumberTries() {
            return numTries;
        }

        private String centerString(String s, int width) {
            return String.format(
                "%s%s%s",
                " ".repeat((width - s.length()) / 2),
                s,
                " ".repeat((width - s.length() + 1) / 2)
            );
        }

        public void run(String studentCodePath) {
            IOUtils.createDirIfAbsent(Path.of(CompetitionExecutionDir));
            System.out.printf(
                "========== Compiling %s ==========\n",
                centerString(getName(), 10)
            );
            int status = compileStudentCode(studentCodePath, false);
            if (status != 0) {
                System.out.println("Failed to compile");
                return;
            }
            System.out.printf(
                "========== Running   %s ==========\n",
                centerString(getName(), 10)
            );
            executeStudentCode(false);
            cleanUpExecution();
        }

        // the sins of the father pass down to the son
        // (IOUtils.readLines shouldn't have ever existed)
        public boolean check(String studentCodePath) {
            // run student code
            IOUtils.createDirIfAbsent(Path.of(CompetitionExecutionDir));
            System.out.printf(
                "========== Compiling %s ==========\n",
                centerString(getName(), 10)
            );
            int status = compileStudentCode(studentCodePath, false);
            if (status != 0) {
                System.out.println("Failed to compile.");
            }

            // isJudge actually just makes it write to a file, it does no judgeing at all.
            // naming it such seemed like a good idea about three hours ago
            File studentOutput = executeStudentCode(true);
            List<String> studentOutputLines = IOUtils.readLines(studentOutput);
            cleanUpExecution();

            // run correct code
            IOUtils.createDirIfAbsent(Path.of(CompetitionExecutionDir));
            compileStudentCode(correctCodePath, true);
            File correctOutput = executeStudentCode(true);
            List<String> correctOutputLines = IOUtils.readLines(correctOutput);
            try {
                Files.deleteIfExists(studentOutput.toPath());
                Files.deleteIfExists(correctOutput.toPath());
            } catch(IOException e) {
                // yeet
            }
            cleanUpExecution();

            return isCorrectOutput(correctOutputLines, studentOutputLines);
        }

        public int compileStudentCode(String studentCodePath, boolean isJudge) {
            try {
                String fileName = Path.of(studentCodePath)
                    .getFileName()
                    .toString();
                Path destination = Path.of(CompetitionExecutionDir, fileName);
                Files.deleteIfExists(destination);
                Files.copy(Path.of(studentCodePath), destination);
                ProcessBuilder pb = new ProcessBuilder("javac", fileName)
                    .directory(new File(CompetitionExecutionDir))
                    .inheritIO();
                if (!isJudge) {
                    pb.redirectOutput(Redirect.INHERIT);
                }
                return pb.start().waitFor();
            } catch (IOException | InterruptedException e) {
                return -1;
            }
        }

        public File executeStudentCode(boolean isJudge) {
            String dataPath = isJudge ? judgeDataPath : studentDataPath;
            try {
                if (dataPath != null) {
                    Files.copy(
                        Path.of(dataPath),
                        Path.of(
                            CompetitionExecutionDir,
                            String.format("%s.dat", name.toLowerCase())
                        ),
                        StandardCopyOption.REPLACE_EXISTING
                    );
                }
                ProcessBuilder pb = new ProcessBuilder("java", name).directory(
                    new File(CompetitionExecutionDir)
                );
                File studentOutputFile = null;
                if (isJudge) {
                    studentOutputFile = File.createTempFile(
                        name,
                        "student-output"
                    );
                    studentOutputFile.deleteOnExit();
                    pb.redirectOutput(studentOutputFile);
                } else {
                    pb.redirectOutput(Redirect.INHERIT);
                }
                pb.start().waitFor();
                return studentOutputFile;
            } catch (IOException | InterruptedException e) {
                return null;
            }
        }

        public boolean judge(String studentCodePath) {
            if (accepted) return true;

            numTries++;
            IOUtils.createDirIfAbsent(Path.of(CompetitionExecutionDir));
            System.out.println("Compiling...");
            int statusCode = compileStudentCode(studentCodePath, true);
            if (statusCode != 0) {
                System.out.println("Failed to compile.");
                cleanUpExecution();
                return false;
            }
            System.out.println("Running...");
            File studentOutput = executeStudentCode(true);
            boolean isCorrect = isCorrectOutput(studentOutput);
            System.out.println("Run complete.");
            try {
               Files.deleteIfExists(studentOutput.toPath());
            } catch (IOException e) {
                // should I do something?
            }
            cleanUpExecution();
            accepted = isCorrect;
            return isCorrect;
        }

        private void cleanUpExecution() {
            IOUtils.delete(CompetitionExecutionDir);
        }

        private boolean isCorrectOutput(File studentOutput) {
            File judgeOutput = new File(judgeOutPath);
            if (USE_LEVENSHTEIN_JUDGING) {
                return (
                    diffOutput(judgeOutput, studentOutput) <=
                    MAX_LEVENSHTEIN_DISTANCE
                );
            }

            // fallback to simple .equals approach
            return isSameOutput(judgeOutput, studentOutput);
        }

        private boolean isCorrectOutput(
            List<String> correctOutput,
            List<String> studentOutput
        ) {
            BufferedReader correct = new BufferedReader(
                new StringReader(String.join("\n", correctOutput))
            );
            BufferedReader student = new BufferedReader(
                new StringReader(String.join("\n", studentOutput))
            );
            if (USE_LEVENSHTEIN_JUDGING) {
                return diffOutput(correct, student) <= MAX_LEVENSHTEIN_DISTANCE;
            }

            // fallback to simple .equals approach
            return isSameOutput(correct, student);
        }

        private boolean isSameOutput(File correctOutput, File studentOutput) {
            try {
                return isSameOutput(
                    new BufferedReader(new FileReader(correctOutput)),
                    new BufferedReader(new FileReader(studentOutput))
                );
            } catch (FileNotFoundException e) {
                return false;
            }
        }

        @SuppressWarnings("ConvertToTryWithResources")
        private boolean isSameOutput(
            BufferedReader correctReader,
            BufferedReader studentReader
        ) {
            try {
                String c = correctReader.readLine();
                String s = studentReader.readLine();

                while (c != null && s != null) {
                    if (!s.trim().equals(c.trim())) {
                        // System.out.printf("Mismatch:\n\tExpected: %s\n\tGot: %s\n", c, s);
                        correctReader.close();
                        studentReader.close();
                        return false;
                    }
                    s = studentReader.readLine();
                    c = correctReader.readLine();
                }

                correctReader.close();
                studentReader.close();

                // make sure that neither the correct answer nor the student answer has an extra
                // blank line
                if (c != null && c.isBlank()) c = null;
                if (s != null && s.isBlank()) s = null;

                return ((c == null || s == null) && (s == null && c == null));
            } catch (IOException e) {
                return false;
            }
        }

        private int diffOutput(File correctOutput, File studentOutput) {
            try {
                return diffOutput(
                    new BufferedReader(new FileReader(correctOutput)),
                    new BufferedReader(new FileReader(studentOutput))
                );
            } catch (FileNotFoundException e) {
                return Integer.MAX_VALUE;
            }
        }

        @SuppressWarnings("ConvertToTryWithResources")
        private int diffOutput(
            BufferedReader correctReader,
            BufferedReader studentReader
        ) {
            try {
                int distance = 0;
                String c = correctReader.readLine();
                String s = studentReader.readLine();

                while (c != null && s != null) {
                    distance += levenshtein(c.trim(), s.trim());
                    s = studentReader.readLine();
                    c = correctReader.readLine();
                }
                correctReader.close();
                studentReader.close();

                // make sure that neither the correct answer nor the student answer has an extra
                // blank line
                if (c != null && c.isBlank()) c = null;
                if (s != null && s.isBlank()) s = null;
                // if either c or s is null and they are not both null, then one did not finish,
                // so completely wrong output
                if ((c == null || s == null) && !(s == null && c == null)) {
                    return Integer.MAX_VALUE;
                }

                return distance;
            } catch (IOException e) {
                return Integer.MAX_VALUE; // failed to read,
            }
        }

        // copied from baeldung [https://www.baeldung.com/java-levenshtein-distance]
        private static int costOfSubstitution(char a, char b) {
            return a == b ? 0 : 1;
        }

        private static int min(int... numbers) {
            return Arrays.stream(numbers).min().orElse(Integer.MAX_VALUE);
        }

        private static int levenshtein(String x, String y) {
            int[][] dp = new int[x.length() + 1][y.length() + 1];

            for (int i = 0; i <= x.length(); i++) {
                for (int j = 0; j <= y.length(); j++) {
                    if (i == 0) {
                        dp[i][j] = j;
                    } else if (j == 0) {
                        dp[i][j] = i;
                    } else {
                        dp[i][j] = min(
                            dp[i - 1][j - 1] +
                                costOfSubstitution(
                                    x.charAt(i - 1),
                                    y.charAt(j - 1)
                                ),
                            dp[i - 1][j] + 1,
                            dp[i][j - 1] + 1
                        );
                    }
                }
            }

            return dp[x.length()][y.length()];
        }
    }

    public static class Competition {

        Map<String, Problem> problems = new HashMap<>();
        public PrintStream out = System.out;

        @Override
        public String toString() {
            int totalScore = 0;
            StringBuilder sb = new StringBuilder(
                "========================= Start Problem List =========================\n"
            );
            for (Problem p : problems.values()) {
                sb.append(p.toString());
                totalScore += p.getScore();
            }
            sb.append(
                "=========================  End Problem List  =========================\n"
            );
            sb.append("Total Score: ");
            sb.append(totalScore);
            sb.append("\n");
            return sb.toString();
        }

        public Map<String, Problem> getProblems() {
            return problems;
        }

        public Problem getProblem(String name) {
            return problems.getOrDefault(name, null);
        }

        public int getContestScore() {
            return problems
                .values()
                .stream()
                .map(p -> p.getScore())
                .reduce(0, (a, b) -> a + b);
        }

        public static Competition ReadFromDir(String... judgeFolderKeywords) {
            String judgePath = IOUtils.checkDir(
                CompetitionDir,
                false,
                judgeFolderKeywords
            );
            if (judgePath == null) {
                // IOUtils.cleanUpTemp();
                exitProgram(-1, "Could Not Find Judge Output Files.");
            }
            Set<String> files = IOUtils.listFiles(judgePath);
            Set<String> problemNames = new TreeSet<>();

            files
                .stream()
                .filter(f -> !(f.endsWith(".out") || f.endsWith(".dat")))
                .forEach(f -> {
                    problemNames.add(
                        Path.of(f).getFileName().toString().split("\\.")[0]
                    );
                });

            Competition competition = new Competition();
            // surely there's a better way, but I don't really care enough to make it faster
            // (since there should only ever be 12 problems)
            problemNames
                .stream()
                .forEach(name -> {
                    String nameLC = name.toLowerCase();
                    String judgeOut = null;
                    String judgeData = null;
                    String codePath = null;
                    for (String f : files
                        .stream()
                        .filter(f ->
                            Path.of(f)
                                .getFileName()
                                .toString()
                                .toLowerCase()
                                .startsWith(nameLC)
                        )
                        .collect(Collectors.toList())) {
                        @SuppressWarnings("SingleCharRegex")
                        String[] parts = f.split("\\.");
                        String ext = parts[parts.length - 1].toLowerCase();
                        switch (ext) {
                            case "out" -> judgeOut = f;
                            case "dat" -> judgeData = f;
                            case "java" -> codePath = f;
                            default -> {
                            }
                        }
                    }

                    if (!notNull(judgeOut, codePath)) return; // couldn't find data, but not sure about judge data (since Q1 lacks a data
                    // file)

                    competition
                        .getProblems()
                        .put(
                            name.toLowerCase(),
                            new Problem(name, judgeData, judgeOut, codePath)
                        );
                });
            // now, we then try to find the "A202X_StudentData" folder
            String studentDataPath = IOUtils.checkDir(
                CompetitionDir,
                false,
                "StudentData"
            );
            if (studentDataPath == null) {
                System.out.println(
                    "[WARNING] Unable to extract student program data. Will be unable to test student code."
                );
            }
            for (String dataFile : IOUtils.listFiles(studentDataPath)) {
                String fileName = IOUtils.getFileName(dataFile);
                String[] parts = fileName.split("\\.");
                String problemName = parts[parts.length - 2];
                competition
                    .getProblem(problemName)
                    .setStudentDataPath(dataFile);
            }
            return competition;
        }

        public static Competition ReadFromDir() {
            return ReadFromDir("Solutions", "JudgeData", "OutFiles");
        }

        public void setup() {
            IOUtils.createDirIfAbsent(Path.of(StudentSourceDir));
            for (Problem p : problems.values()) {
                String name = p.getName();
                try {
                    Files.createFile(
                        Path.of(
                            StudentSourceDir,
                            String.format("%s.java", name)
                        )
                    );
                } catch (IOException e) {}
            }
        }

        public void printHelp(PrintStream out) {
            out.println(
                """
                Summary Of Commands:

                "list" -- lists problems
                "judge <problem>" -- judges the problem
                "run <problem>" -- runs the problem code with student data
                "check <problem>" -- checks if the problem code produces the same solution using student data
                "show <problem>" -- shows your current code for the problem that it would run/judge
                "data <problem>" -- outputs the student data (if exists)
                "clear" -- clears the screen
                "exit" -- stops the competition
                "here" -- opens the student code directory
                "dir" -- lists student code directory
                "restart" -- (only useful for development) effectively "replaces" the current instance of the program with a fresh one
                "help" -- shows this message again"""
            );
        }

        public void run(PrintStream out) {
            try {
                @SuppressWarnings("resource")
                Scanner input = new Scanner(System.in);
                printHelp(out);
                while (true) {
                    out.print("> ");
                    String line = input.nextLine().toLowerCase();
                    String[] parts = line.split(" ");
                    String problemName = (parts.length > 1) ? parts[1] : "";
                    switch (parts[0]) {
                        case "list" -> out.println(this);
                        case "judge" -> judge(problemName);
                        case "run" -> run(problemName);
                        case "check" -> check(problemName);
                        case "clear" -> clear(); // doesn't work
                        case "show" -> show(problemName);
                        case "here" -> explorer();
                        case "dir" -> ls_la();
                        case "data" -> showStudentData(problemName);
                        case "exec" -> {
                            if (parts.length < 2) {
                                System.out.println("Insufficient Arguments.");
                            } else {
                                exec(
                                    Arrays.copyOfRange(parts, 1, parts.length)
                                );
                            }
                        }
                        case "exit" -> {
                            return;
                        }
                        case "restart" -> restart();
                        case "help" -> printHelp(out);
                        default -> {
                            System.out.println("Unrecognized Command");
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println(e);
                System.out.println(
                    "Unknown Error Occured. See above for details."
                );
                IOUtils.cleanUpTemp();
            }
        }

        private void ls_la() {
            try {
                ProcessBuilder pb;
                if (IsWindows) {
                    pb = new ProcessBuilder("cmd", "/c", "dir");
                } else {
                    pb = new ProcessBuilder("ls", "-la");
                }
                pb.directory(new File(StudentSourceDir));
                pb.inheritIO().start().waitFor();
            } catch (IOException | InterruptedException ex) {}
        }

        private void explorer() {
            String[] cmd = null;
            if (IsWindows) {
                cmd = new String[] { "explorer.exe", StudentSourceDir };
            } else if (IsMac) {
                cmd = new String[] { "open", StudentSourceDir };
            } else {
                cmd = new String[] { "xdg-open", StudentSourceDir };
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.start().waitFor();
            } catch (IOException | InterruptedException e) {
                // do nothing, don't care
            }
        }

        private String getStudentCodePath(Problem p) {
            return Path.of(
                StudentSourceDir,
                String.format("%s.java", p.name)
            ).toString();
        }

        private Problem getUserSelectedProblem(String name) {
            Problem p = problems.getOrDefault(name.trim().toLowerCase(), null);
            if (p == null) {
                out.println(
                    "Invalid problem selected. Could not find problem: " + name
                );
            }
            return p;
        }

        private void restart() {
            try {
                System.out.println(
                    "Bootstrapping the lastest version to run on current process..."
                );
                (
                    new ProcessBuilder("java", "UILaunch.java").inheritIO()
                ).start().waitFor();
            } catch (Exception e) {}
            IOUtils.cleanUpTemp();
            System.exit(0);
        }

        private void show(String problemName) {
            Problem p = getUserSelectedProblem(problemName);
            if (p == null) return;

            try {
                List<String> lines = Files.readAllLines(
                    Path.of(
                        StudentSourceDir,
                        String.format("%s.java", p.getName())
                    )
                );
                lines.forEach(l -> out.println(l));
            } catch (IOException e) {}
        }

        private void showStudentData(String problemName) {
            Problem p = getUserSelectedProblem(problemName);
            if(p == null) return;
            try {
                if(p.studentDataPath == null || !Files.exists(Path.of(p.studentDataPath))) {
                    System.out.printf("Problem <%s> does not have a data file.\n", p.getName());
                    return;
                }

                List<String> lines = Files.readAllLines(
                    Path.of(p.studentDataPath)
                );
                lines.forEach(l -> out.println(l));
            } catch (IOException e) {}
        }

        private void ansiClear() {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        }

        private void pbClear() {
            try {
                ProcessBuilder pb;
                if (IsWindows) {
                    pb = new ProcessBuilder("cmd", "/c", "cls");
                } else {
                    pb = new ProcessBuilder("clear");
                }
                pb.inheritIO().start().waitFor();
            } catch (IOException | InterruptedException ex) {}
        }

        private void clear() {
            try {
                ansiClear();
                pbClear();
            } catch (Exception e) {
                // Fallback: print newlines
                System.out.println("Fallback: write a ton of new lines");
                for (int i = 0; i < 50; i++) {
                    System.out.println();
                }
            }
        }

        private void exec(String... command) {
            try {
                (new ProcessBuilder(command).inheritIO()).start().waitFor();
            } catch (IOException e) {
            } catch (InterruptedException e) {
            }
        }

        private void judge(String problemName) {
            Problem p = getUserSelectedProblem(problemName);
            if (p == null) return;

            if (p.accepted) {
                out.println("Problem already accepted.");
            } else {
                if (p.judge(getStudentCodePath(p))) {
                    System.out.println("Solution Accepted.");
                } else {
                    System.out.println("Solution Deined.");
                }
            }
        }

        private void run(String problemName) {
            Problem p = getUserSelectedProblem(problemName);
            if (p == null) return;
            p.run(getStudentCodePath(p));
        }

        private void check(String problemName) {
            Problem p = getUserSelectedProblem(problemName);
            if (p == null) return;
            if (p.check(getStudentCodePath(p))) {
                out.println("Correct Solution.");
            } else {
                out.println("Incorrect Solution");
            }
        }
    }

    private static String zipFileChooser() {
        System.out.println(
            "Please enter the path for the competition zip file."
        );
        final JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(Path.of(CurrentDir).toFile());
        fc.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
            "ZIP files",
            "zip"
        );
        fc.setFileFilter(filter);
        // default preferred size seems small
        Dimension d = fc.getPreferredSize();
        fc.setPreferredSize(new Dimension(d.width * 2, d.height * 2));
        int status = fc.showOpenDialog(null);
        if (status == JFileChooser.APPROVE_OPTION) {
            return fc.getSelectedFile().toPath().toString();
        }

        return "";
    }

    @SuppressWarnings("resource")
    private static String requestUserDataZip() {
        System.out.print("Would you like to pick one on your computer? (y/n) ");
        Scanner s = new Scanner(System.in);
        String input = s.nextLine();
        if (input.equalsIgnoreCase("y")) {
            return zipFileChooser();
        }

        System.out.printf(
            "Would you like to download one from the internet? (y/n) "
        );
        input = s.nextLine();
        if (input.equalsIgnoreCase("y")) {
            List<String> availCompetitions = UIL_FILE_URLS.keySet()
                .stream()
                .toList();
            System.out.println(
                "Enter the contest number from the following list:"
            );
            for (int i = 0; i < availCompetitions.size(); i++) {
                System.out.printf("\t[%3d] %s\n", i, availCompetitions.get(i));
            }

            int choice = Integer.parseUnsignedInt(s.nextLine().trim());
            if (choice >= availCompetitions.size()) {
                return null;
            }

            Path expectedPath = Path.of(
                DownloadDir,
                availCompetitions.get(choice) + "Programming.zip"
            );
            if (Files.exists(expectedPath)) {
                System.out.printf(
                    "Found previously downloaded file for %s at %s. Use this file? (y/n) ",
                    availCompetitions.get(choice),
                    expectedPath.toString()
                );
                String useExisting = s.nextLine();
                if (useExisting.equalsIgnoreCase("y")) {
                    return expectedPath.toString();
                }
            }

            return downloadContestZip(availCompetitions.get(choice));
        }

        s.close();
        return null;
    }

    private static String detectCompetitionZip(String[] args) {
        String shareDir = IOUtils.checkDir(CurrentDir, "UILCS", "Programming");
        String srcDir = IOUtils.checkDir(
            StudentSourceDir,
            "UILCS",
            "Programming",
            "DataFiles"
        );
        String argsDir = args.length > 0 ? args[0] : null;

        if (shareDir != null) {
            System.out.printf(
                "[Same Directory] Use %s as competition zip? (y/n): ",
                shareDir
            );
            @SuppressWarnings("resource")
            Scanner s = new Scanner(System.in);
            String input = s.nextLine();
            if (input.equalsIgnoreCase("y")) {
                return shareDir;
            }
        } else if (srcDir != null) {
            System.out.printf(
                "[src Directory] Use %s as competition zip? (y/n): ",
                shareDir
            );
            @SuppressWarnings("resource")
            Scanner s = new Scanner(System.in);
            String input = s.nextLine();
            if (input.equalsIgnoreCase("y")) {
                return shareDir;
            }
        } else if (argsDir != null) {
            System.out.printf(
                "[args] Use %s as competition zip? (y/n): ",
                shareDir
            );
            @SuppressWarnings("resource")
            Scanner s = new Scanner(System.in);
            String input = s.nextLine();
            if (input.equalsIgnoreCase("y")) {
                return shareDir;
            }
        }

        System.out.println(
            "Unable to automatically detect competition zip file"
        );
        return null;
    }

    @SuppressWarnings("ConvertToTryWithResources")
    public static void main(String[] args) {
        Scanner terminal = new Scanner(System.in);

        // Step 1: Need to find the zip file containing all the data
        // these terms are in the new naming format, and won't work past 2025 district

        // competitionZip can be passed as cli args, discovered in same directory, or
        // selected using a java swing FileChooser
        String competitionZip = nullish(detectCompetitionZip(args), () -> requestUserDataZip());

        if (competitionZip == null || competitionZip.isEmpty() || Files.notExists(Path.of(competitionZip))) {
            exitProgram(-1, "Invalid Path. Exiting Program");
        }

        // Step 2: Inflate the zip file to the temp directory
        System.out.printf(
            "Unzipping competition files to: \n\t%s\n",
            CompetitionDirRoot
        );
        IOUtils.unzip(competitionZip, CompetitionDirRoot);

        // Step 3: Read Competition Data and set it up
        System.out.println("Reading competition data...");
        Competition competition = Competition.ReadFromDir();
        System.out.printf(
            "Read %d problems from data.\n",
            competition.getProblems().size()
        );
        System.out.println(competition);

        // Step 4: Create blank java files for student use
        competition.setup();

        competition.run(System.out);

        System.out.println("Contest Results:");
        System.out.println(competition);

        terminal.close();
        IOUtils.cleanUpTemp();
    }
}
