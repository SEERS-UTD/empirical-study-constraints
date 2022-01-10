package sample.abs;

import java.io.File;

public abstract class Unpack extends Task {
    protected File dest;

    @Override
    public void execute() throws BuildException {
        File savedDest = dest; // may be altered in validate
        try {
            validate();
        } finally {
            dest = savedDest;
        }
    }

    private void validate() throws BuildException {
        if (dest == null) {
            throw new BuildException("dest is required when using a non-filesystem source");
        }

        if (dest.isDirectory()) {
            System.out.println("DIR");
        }
    }
}
