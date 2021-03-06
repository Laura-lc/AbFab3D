package abfab3d.io.output;

import abfab3d.core.GridDataChannel;
import abfab3d.core.GridDataDesc;
import abfab3d.core.AttributeGrid;
import abfab3d.grid.ModelWriter;
import abfab3d.util.TriangleMesh;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Write a grid out in a voxel format. Caller is responsible for closing the provided output stream.
 *
 * @author Alan Hudson
 */
public class VoxelModelWriter implements ModelWriter {
    private OutputStream os;

    @Override
    public void setOutputFormat(String fileEnding) {
        if (!fileEnding.equals("svx")) throw new IllegalArgumentException("Unhandled voxel file format: " + fileEnding);
    }

    @Override
    public void setOutputStream(OutputStream os) {
        this.os = os;
    }

    @Override
    public void execute(AttributeGrid grid) throws IOException {
        // TODO: Not sure how these should be sourced yet
        GridDataDesc attDesc = new GridDataDesc();
        attDesc.addChannel(new GridDataChannel(GridDataChannel.DENSITY, "dens", 8, 0, 0., 1.));
        grid.setDataDesc(attDesc);

        SVXWriter writer = new SVXWriter();
        writer.write(grid, os);
    }

    @Override
    public TriangleMesh getGeneratedMesh() {
        return null;
    }

    /**
     * Get a string name for this writer.
     * @return
     */
    public String getStyleName() {
        return "voxels";
    }
}
