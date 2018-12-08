package subjectreco.util;

import org.apache.commons.configuration2.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Reporter implements IConfiguration {
    private String reportTitle;
    private FileWriter reportFileWriter;
    private Boolean doReports;
    private String reportLocation;
    private Long initTime;

    public void startExperiment() {
        if (doReports) {
            initTime = System.currentTimeMillis();
            File path = new File(reportLocation);
            if (!path.exists() || !path.isDirectory())
                path.mkdir();

            String dateString = new Date(System.currentTimeMillis()).toString().replace(':', '.');

            String actualReportTitle = reportTitle + dateString;
            File reportFile = new File(path + File.separator + actualReportTitle + ".report.csv");
            try {
                reportFileWriter = new FileWriter(reportFile);
                reportFileWriter.flush();
                reportFileWriter.write(dateString + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void finishExperiment() {
        if (doReports) {
            try {
                double totalTime = (double) (System.currentTimeMillis() - initTime) / 1000.0;
                reportFileWriter.write("Total time of execution (s) , " + totalTime);
                reportFileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addLog(String log) {
        if (doReports) {
            try {
                reportFileWriter.write(log + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addResults(Map<String, Double[]> results) {
        if (doReports) {
            String res = "";
            for (String key : results.keySet())
                res = res.concat(key + " +/- , " + results.get(key)[0] + " , " + results.get(key)[1] + "\n");
            try {
                reportFileWriter.write(res);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addStats(int average, long totalMemory, long memory, AtomicInteger noEstimateCounter) {
        if (doReports) {
            String logAverage = "Average time per recommendation (ms), " + average + "\n";
            String logTotalMem = "Total memory, " + totalMemory / 1000000L + "\n";
            String logMemory = "Memory used (MB), " + memory / 1000000L + "\n";
            String logNoEst = "Unable to recommend, " + noEstimateCounter.get() + "\n";
            try {
                reportFileWriter.write(logAverage);
                reportFileWriter.write(logTotalMem);
                reportFileWriter.write(logMemory);
                reportFileWriter.write(logNoEst);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void configure(Configuration config) {
        reportLocation = config.getString("reportLocation");
        reportTitle = config.getString("reportTitle");
        doReports = config.getBoolean("doReports");
    }
}
