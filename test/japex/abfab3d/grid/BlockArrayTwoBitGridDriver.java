package abfab3d.grid;

import com.sun.japex.TestCase;

/**
 * TODO: Add docs
 *
 * @author Alan Hudson
 */
public class BlockArrayTwoBitGridDriver extends BaseGridDriver {
    public void allocate(TestCase testCase) {
        int w = Integer.parseInt(testCase.getParam("width"));
        int h = Integer.parseInt(testCase.getParam("height"));
        int d = Integer.parseInt(testCase.getParam("depth"));

        grid =  new BlockArrayTwoBitGrid(w,h,d,voxel_size, slice_height, new int[] {4,4,4});
    }
}
