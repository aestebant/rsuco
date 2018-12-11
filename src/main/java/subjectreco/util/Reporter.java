package subjectreco.util;

import org.apache.commons.configuration2.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Reporter implements IConfiguration {
    private Boolean reportOnConsole;
    private Boolean reportOnFile;
    private String reportTitle;
    private FileWriter reportFileWriter;
    private String reportLocation;
    private Long initTime;

    public void startExperiment() {
        initTime = System.currentTimeMillis();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        Date date = new Date();
        String dateString = dateFormat.format(date);

        if (reportOnConsole) {
            System.out.println("[REPORT]: Starting experiment");
        }
        if (reportOnFile) {
            File path = new File(reportLocation);
            if (!path.exists() || !path.isDirectory())
                path.mkdir();

            String actualReportTitle = reportTitle + "_" + dateString + ".report.txt";
            File reportFile = new File(path + File.separator + actualReportTitle);
            try {
                reportFileWriter = new FileWriter(reportFile);
                reportFileWriter.flush();
                reportFileWriter.write(dateString + System.lineSeparator());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void finishExperiment() {
        double totalTime = (double) (System.currentTimeMillis() - initTime) / 1000.0;
        String msg = String.format("Total time of executions (s): %f", totalTime);

        if (reportOnConsole) {
            System.out.println("[REPORT]: " + msg);
            System.out.println("[REPORT]: Experiment finished");
        }
        if (reportOnFile) {
            try {
                reportFileWriter.write(msg + System.lineSeparator());
                reportFileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addInfo(String msg, Object... args) {
        String fullMsg = String.format(msg, args);
        if (reportOnConsole) {
            System.out.println("[INFO]: " + fullMsg);
        }
    }

    public void addLog(String msg, Object... args) {
        String fullMsg = String.format(msg, args);

        if (reportOnConsole) {
            System.out.println("[REPORT]: " + fullMsg);
        }
        if (reportOnFile) {
            try {
                reportFileWriter.write(fullMsg + System.lineSeparator());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addResults(Map<String, Double[]> results) {
        StringBuilder msg = new StringBuilder();
        for (String key : results.keySet()) {
            msg.append(String.format("%s: %f +/- %f", key, results.get(key)[0], results.get(key)[1]));
            msg.append(System.lineSeparator());
        }

        if (reportOnConsole) {
            System.out.println(msg.toString());
        }
        if (reportOnFile) {
            try {
                reportFileWriter.write(msg.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addStats(int average, long totalMemory, long memory, AtomicInteger noEstimateCounter) {
        StringBuilder msg = new StringBuilder();
        msg.append(String.format("Average time per recommendation (ms): %d", average)).append(System.lineSeparator());
        msg.append(String.format("Total memory (MB): %d", totalMemory/1000000L)).append(System.lineSeparator());
        msg.append(String.format("Memory used (MB): %d", memory/1000000L)).append(System.lineSeparator());
        msg.append(String.format("Unable to recommend: %d", noEstimateCounter.get()));

        if (reportOnConsole) {
            System.out.println(msg.toString());
        }
        if (reportOnFile) {
            try {
                reportFileWriter.write(msg.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void configure(Configuration config) {
        reportOnConsole = config.getBoolean("reportOnConsole", true);
        reportOnFile = config.getBoolean("reportOnFile", false);
        if (reportOnFile) {
            reportLocation = config.getString("reportLocation");
            reportTitle = config.getString("reportTitle");
        }
    }
}
