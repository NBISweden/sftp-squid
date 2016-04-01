package se.nbis.sftp_squid;

import java.io.IOException;

import net.schmizz.sshj.common.StreamCopier;

/**
 * ProgressBarListener - report progress to the user of the program
 */
class ProgressBarListener implements StreamCopier.Listener {
    /** Total length of the current file */
    private long length = 0;
    /** When to update the progressbar next */
    private long nextPrint = 0;
    /** How often we should update the progressbar */
    private long printStepSize = 0;
    /** Width of progressbar */
    private int width = 60;
    /** File name that is currently beeing transferred */
    private String file_name;

    /**
     * Create a new listener
     *
     * @param length size of file in bytes
     */
    ProgressBarListener(long length, String file_name) {
        this.length    = length;
        this.file_name = renderFileName( file_name ); // Might as well just cache this

        printStepSize  = length/1000;
        width          = 60;
    }

    /**
     * Display the progressbar
     *
     * @param transferred The number of bytes transferred so far
     */
    @Override
    public void reportProgress(long transferred) throws IOException {
        if (length != 0 && transferred == length) {
            String prog = renderBar(transferred);
            String size = renderSize(transferred);
            System.out.printf("\r%-10.10s %s %s %5.1f%%\n", file_name, prog, size, 100.0);
        }
        else if (nextPrint < transferred) {
            nextPrint = transferred + printStepSize;

            String prog = renderBar(transferred);
            String size = renderSize(transferred);

            double percentDone = 100 * (double) transferred/ (double) length;

            System.out.printf("\r%-10.10s %s %s %5.1f%%", file_name, prog, size, percentDone);
        }
    }

    private String renderFileName(String file_name) {
        if (file_name.length() > 10) {
            return String.format("%-8.8s..", file_name);
        }
        return String.format("%-10.10s", file_name);
    }

    private String renderSize(long transferred) {
        String[] sizes = {"", "Kb", "Mb", "Gb", "Tb", "Pb", "Hb"};
        int exponent = (int) Math.floor( Math.log(transferred) / Math.log(1000) );
        double size = transferred / Math.pow(1000.0, (double) exponent);
        return String.format("%6.1f %2.2s", size, sizes[exponent]);
    }

    private String renderBar(long transferred) {
        int bar_length = (int) ((width - 2) * transferred / length);

        String prog = "|";
        for (int i=0; i<bar_length-1; i++) {
            prog += "=";
        }
        if (bar_length > 0 && bar_length < width - 2) {
            prog += ">";
        }
        for (int i=bar_length+1; i<width-2; i++) {
            prog += " ";
        }
        prog += "|";

        return prog;
    }
}
